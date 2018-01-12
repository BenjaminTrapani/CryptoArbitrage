package BenTrapani.CryptoArbitrage;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Stack;
import java.util.concurrent.Semaphore;

import org.knowm.xchange.currency.Currency;

import BenTrapani.CryptoArbitrage.OrderGraph.GraphEdge;

public class OrderBookAnalyzer {
	private OrderGraph sharedOrderGraph;
	private Thread analyzerThread;
	private Semaphore semaphore = new Semaphore(1);
	private Currency currencyToAccumulate = Currency.USD;
	private boolean shouldExit = false;
	
	public OrderBookAnalyzer(OrderGraph sharedOrderGraph, Currency currencyToAccumulate) {
		this.sharedOrderGraph = sharedOrderGraph;
		this.currencyToAccumulate = currencyToAccumulate;
	}
	
	protected static class SearchState {
		public final Currency currency;
		public final SearchState parent;
		public final BigDecimal ratio;
		public final HashSet<GraphEdge> visitedEdges;
		
		public SearchState(Currency currency, 
				SearchState parent, 
				boolean isLastChild,
				BigDecimal ratio,
				HashSet<GraphEdge> visitedEdges) {
			this.currency = currency;
			this.parent = parent;
			this.ratio = ratio;
			this.visitedEdges = visitedEdges;
		}
		
		// SearchStates are equal if their currency and ratio are deep equal
		@Override
		public int hashCode() {
			final int currencyHashCode = currency.hashCode();
			final int ratioHashCode = ratio.hashCode();
			
			final int hash = currencyHashCode + 
					163 * ratioHashCode;
			return hash;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final SearchState other = (SearchState) obj;
			return currency.equals(other.currency) &&
					ratio.equals(other.ratio);
		}
	}
	
	protected class SearchContext {
		private Stack<SearchState> searchStack = new Stack<SearchState>();
		private Hashtable<SearchState, BigDecimal> maxRatioPerState = new Hashtable<SearchState, BigDecimal>();
		private Currency destNode;
		private SearchState sourceState;
		private final BigDecimal sentinelRatio = new BigDecimal(-1.0);
		
		public SearchContext(Currency destNode, SearchState sourceState){
			this.destNode = destNode;
			this.sourceState = sourceState;
			expandSearchState(sourceState);
		}
		
		public void expandSearchState(SearchState searchState) {
			final HashSet<GraphEdge> edgesFromSource = sharedOrderGraph.getEdges(searchState.currency);
			final boolean isDestNode = searchState.currency.equals(destNode) && searchState.parent != null;
			if ((maxRatioPerState.containsKey(searchState) && 
					maxRatioPerState.get(searchState).compareTo(sentinelRatio) > 0) || 
					isDestNode) {
				if (searchState != sourceState) {
					updateMaxForParent(searchState, isDestNode);
				}
			}else {
				maxRatioPerState.put(searchState, sentinelRatio);
				//Process this state again once maxRatioPerCurrency is complete
				searchStack.push(searchState);
				
				for (Iterator<GraphEdge> iter = edgesFromSource.iterator(); iter.hasNext();) {
					GraphEdge edge = iter.next();
					addEdgeToStackIfNotExists(searchState, edge, iter.hasNext());
				}
			}
		}
		
		private void updateMaxForParent(SearchState child, boolean isDestNode) {
			SearchState parent = child.parent;
			if (parent == null) {
				throw new IllegalStateException("Parent of input child must be non-null to update its max");
			}
			if (!maxRatioPerState.containsKey(parent)){
				throw new IllegalStateException("Parent must have max ratio cache initialized before children are processed");
			}
			if (!maxRatioPerState.containsKey(child) && !isDestNode) {
				throw new IllegalStateException("Child must have max ratio cache initialed before updating parent if not dest node");
			}
			
			final BigDecimal prevMaxRatio = maxRatioPerState.get(parent);
			final BigDecimal childMaxRatio = isDestNode? child.ratio : maxRatioPerState.get(child);
			
			if (childMaxRatio.compareTo(prevMaxRatio) > 0) {
				maxRatioPerState.put(parent, childMaxRatio);
			}
		}
		
		private boolean addEdgeToStackIfNotExists(SearchState parent,
				GraphEdge edge, 
				boolean isLast) {
			// Cannot use the same edge twice because each edge represents a possible trade at the current point in time
			if (!parent.visitedEdges.contains(edge)) {
				@SuppressWarnings("unchecked")
				HashSet<GraphEdge> newVisitedEdges = (HashSet<GraphEdge>) parent.visitedEdges.clone();
				newVisitedEdges.add(edge);
				searchStack.push(new SearchState(edge.destCurrency, 
						parent, 
						isLast, 
						edge.ratio.multiply(parent.ratio),
						newVisitedEdges));
				return true;
			}
			return false;
		}
		
		int getStackSize() {
			return searchStack.size();
		}
		SearchState popStack() {
			return searchStack.pop();
		}
		
		public BigDecimal getMaxRatioForSource() {
			return maxRatioPerState.get(sourceState);
		}
	}
	
	protected BigDecimal searchForArbitrage() {
		SearchContext searchCtx = new SearchContext(currencyToAccumulate, new SearchState(currencyToAccumulate, 
				null, true, new BigDecimal(1.0), new HashSet<GraphEdge>()));
		
		while (searchCtx.getStackSize() > 0) {
			SearchState curState = searchCtx.popStack();
			searchCtx.expandSearchState(curState);
		}
		return searchCtx.getMaxRatioForSource();
	}
	
	public void startAnalyzingOrderBook() {
		if (analyzerThread == null) {
			analyzerThread = new Thread() {
				public void run() {
					shouldExit = false;
					while(!shouldExit) {
						try {
							semaphore.acquire();
							searchForArbitrage();
						} catch (InterruptedException e) {
							shouldExit = true;
						}
					}
				}
			};
			analyzerThread.start();
		}
	}
	
	public void stopAnalyzingOrderBook() throws InterruptedException {
		if (analyzerThread != null) {
			shouldExit = true;
			semaphore.release();
			analyzerThread.join();
		}
	}
}
