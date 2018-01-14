package BenTrapani.CryptoArbitrage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.Semaphore;

import org.knowm.xchange.currency.Currency;

import BenTrapani.CryptoArbitrage.OrderGraph.TwoSidedGraphEdge;

public class OrderBookAnalyzer implements OrderGraphChangeHandler {
	private OrderGraph sharedOrderGraph;
	private Thread analyzerThread;
	private Semaphore semaphore = new Semaphore(1);
	private Currency currencyToAccumulate;
	private boolean shouldExit = false;
	private OrderGraphAnalysisHandler analysisHandler;
	
	public OrderBookAnalyzer(OrderGraph sharedOrderGraph, Currency currencyToAccumulate, OrderGraphAnalysisHandler analysisHandler) {
		this.sharedOrderGraph = sharedOrderGraph;
		this.currencyToAccumulate = currencyToAccumulate;
		this.analysisHandler = analysisHandler;
	}
	
	protected static class SearchCacheKey {
		// Instead of number of edges to here, the key should have the set of edges remaining that are children of currency
		// Computing this is likely slower than the brute force approach, so probably use that instead of the DP solution.
		// Brute force solution key will replace numberOfEdgesToHere with a hashset of visited edges so far.
		public final HashSet<TwoSidedGraphEdge> edgesVisited;
		public final Currency currency;
		
		@SuppressWarnings("unchecked")
		public SearchCacheKey(SearchState searchState) {
			this.edgesVisited = (HashSet<TwoSidedGraphEdge>) searchState.visitedEdges.clone();
			this.currency = searchState.currency;
		}
		
		@Override
		public int hashCode() {
			return edgesVisited.hashCode() * 19203 + currency.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final SearchCacheKey other = (SearchCacheKey) obj;
			return edgesVisited.equals(other.edgesVisited) &&
					currency.equals(other.currency);
		}
	}
	
	protected static class SearchState {
		public final Currency currency;
		public final SearchState parent;
		public final BigDecimal ratio;
		public final HashSet<TwoSidedGraphEdge> visitedEdges;
		
		public SearchState(Currency currency, 
				SearchState parent, 
				BigDecimal ratio,
				HashSet<TwoSidedGraphEdge> visitedEdges) {
			this.currency = currency;
			this.parent = parent;
			this.ratio = ratio;
			this.visitedEdges = visitedEdges;
		}
	}
	
	public static class AnalysisResult implements Comparable<AnalysisResult> {
		public final BigDecimal maxRatio;
		public final HashSet<TwoSidedGraphEdge> tradesToExecute;
		AnalysisResult(BigDecimal maxRatio, HashSet<TwoSidedGraphEdge> tradesToExecute) {
			this.maxRatio = maxRatio;
			this.tradesToExecute = tradesToExecute;
		}
		@Override
		public int compareTo(AnalysisResult other) {
			if (other == null) {
				throw new IllegalArgumentException("Cannot compare to null object");
			}
			return maxRatio.compareTo(other.maxRatio);
		}	
	}
	
	protected class SearchContext {
		private Stack<SearchState> searchStack = new Stack<SearchState>();
		private Hashtable<SearchCacheKey, AnalysisResult> maxRatioPerState = new Hashtable<SearchCacheKey, AnalysisResult>();
		private final Currency destNode;
		private final SearchState sourceState;
		private final AnalysisResult sentinelRatio = new AnalysisResult(new BigDecimal(-1.0), null);
		
		public SearchContext(Currency destNode, SearchState sourceState){
			this.destNode = destNode;
			this.sourceState = sourceState;
			expandSearchState(sourceState);
		}
		
		public void expandSearchState(SearchState searchState) {
			final boolean isDestNode = searchState.currency.equals(destNode) && searchState.parent != null;
			final SearchCacheKey searchStateKey = new SearchCacheKey(searchState);
			
			if (maxRatioPerState.containsKey(searchStateKey) || 
					isDestNode) {
				if (searchState != sourceState) {
					updateMaxForParent(searchState, isDestNode);
				}
			}else {
				maxRatioPerState.put(searchStateKey, sentinelRatio);
				final HashSet<TwoSidedGraphEdge> edgesFromSource = sharedOrderGraph.getEdges(searchState.currency);
				if (edgesFromSource != null) {
					List<SearchState> nextStates = new ArrayList<SearchState>(edgesFromSource.size());
					for (TwoSidedGraphEdge edge : edgesFromSource) {
						SearchState maybeNextState = getNextSearchStateIfNotEdgeExists(searchState, edge);
						if (maybeNextState != null) {
							nextStates.add(maybeNextState);
						}
					}

					if (nextStates.size() > 0) {
						// Process this state again once children are expanded
						searchStack.push(searchState);
						for (SearchState nextState : nextStates) {
							searchStack.push(nextState);
						}
					}
				}
			}
		}
		
		private void updateMaxForParent(SearchState child, boolean isDestNode) {
			final SearchState parent = child.parent;
			final SearchCacheKey parentKey = new SearchCacheKey(parent);
			final SearchCacheKey childKey = new SearchCacheKey(child);
			
			if (parent == null) {
				throw new IllegalStateException("Parent of input child must be non-null to update its max");
			}
			if (!maxRatioPerState.containsKey(parentKey)){
				throw new IllegalStateException("Parent must have max ratio cache initialized before children are processed");
			}
			if (!maxRatioPerState.containsKey(childKey) && !isDestNode) {
				throw new IllegalStateException("Child must have max ratio cache initialed before updating parent if not dest node");
			}
			
			final AnalysisResult prevMaxRatio = maxRatioPerState.get(parentKey);
			final AnalysisResult childMaxRatio = isDestNode? new AnalysisResult(child.ratio, child.visitedEdges) : maxRatioPerState.get(childKey);
			
			if (childMaxRatio.compareTo(prevMaxRatio) > 0) {
				maxRatioPerState.put(parentKey, childMaxRatio);
			}
		}
		
		private SearchState getNextSearchStateIfNotEdgeExists(SearchState parent,
				TwoSidedGraphEdge edge) {
			// Cannot use the same edge twice because each edge represents a possible trade at the current point in time
			if (!parent.visitedEdges.contains(edge)) {
				@SuppressWarnings("unchecked")
				HashSet<TwoSidedGraphEdge> newVisitedEdges = (HashSet<TwoSidedGraphEdge>) parent.visitedEdges.clone();
				newVisitedEdges.add(edge);
				return new SearchState(edge.graphEdge.destCurrency, 
						parent, 
						edge.graphEdge.ratio.multiply(parent.ratio),
						newVisitedEdges);
			}
			return null;
		}
		
		int getStackSize() {
			return searchStack.size();
		}
		SearchState popStack() {
			return searchStack.pop();
		}
		
		public AnalysisResult getAnalysisResult() {
			return maxRatioPerState.get(new SearchCacheKey(sourceState));
		}
	}
	
	protected AnalysisResult searchForArbitrage() {
		SearchContext searchCtx = new SearchContext(currencyToAccumulate, new SearchState(currencyToAccumulate, 
				null, new BigDecimal(1.0), new HashSet<TwoSidedGraphEdge>()));
		
		while (searchCtx.getStackSize() > 0) {
			SearchState curState = searchCtx.popStack();
			searchCtx.expandSearchState(curState);
		}
		
		return searchCtx.getAnalysisResult();
	}
	
	public void onOrderGraphChanged() {
		semaphore.release();
	}
	
	public void startAnalyzingOrderBook() {
		if (analyzerThread == null) {
			analyzerThread = new Thread() {
				public void run() {
					shouldExit = false;
					while(!shouldExit) {
						try {
							semaphore.acquire();
							analysisHandler.onOrderBookAnalysisComplete(searchForArbitrage());
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
