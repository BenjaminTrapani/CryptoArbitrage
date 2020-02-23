package BenTrapani.CryptoArbitrage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.Semaphore;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.knowm.xchange.currency.Currency;

import BenTrapani.CryptoArbitrage.OrderGraph.TwoSidedGraphEdge;

public class OrderBookAnalyzer implements OrderGraphChangeHandler {
	private OrderGraph sharedOrderGraph;
	private Thread analyzerThread;
	private Semaphore semaphore = new Semaphore(0);
	// Currency to accumulate if using brute force algorithm, root of bellman
	// ford search when using that.
	// When using bellman ford, best to pick a currency that is exchangeable to
	// almost all other currencies (USD or BTC) to
	// increase likelihood of finding arbitrage path.
	private Currency currencyToAccumulate;
	private MutableBoolean shouldExit = new MutableBoolean(false);
	private int maxTrades;
	private OrderGraphAnalysisHandler analysisHandler;

	public OrderBookAnalyzer(OrderGraph sharedOrderGraph, Currency currencyToAccumulate, int maxTrades,
			OrderGraphAnalysisHandler analysisHandler) {
		this.sharedOrderGraph = sharedOrderGraph;
		this.currencyToAccumulate = currencyToAccumulate;
		if (maxTrades <= 0) {
			throw new IllegalArgumentException("Cannot arbitrage with less than or equal to 0 allowed trades");
		}
		this.maxTrades = maxTrades;
		this.analysisHandler = analysisHandler;
	}

	protected static class SearchCacheKey {
		// Instead of number of edges to here, the key should have the set of
		// edges remaining that are children of currency
		// Computing this is likely slower than the brute force approach, so
		// probably use that instead of the DP solution.
		// Brute force solution key will replace numberOfEdgesToHere with a
		// hashset of visited edges so far. This approach
		// will catch a few repeated subproblems where same set of remaining
		// edges needs to be evaluated and the order of
		// previously visited edges is the same.
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
			return edgesVisited.equals(other.edgesVisited) && currency.equals(other.currency);
		}
	}

	protected static class SearchState {
		public final Currency currency;
		public final SearchState parent;
		public final Fraction ratio;
		public final HashSet<TwoSidedGraphEdge> visitedEdges;

		public SearchState(Currency currency, SearchState parent, Fraction ratio,
				HashSet<TwoSidedGraphEdge> visitedEdges) {
			this.currency = currency;
			this.parent = parent;
			this.ratio = ratio;
			this.visitedEdges = visitedEdges;
		}
	}

	public static class AnalysisResult implements Comparable<AnalysisResult> {
		public final Fraction maxRatio;
		public final HashSet<TwoSidedGraphEdge> tradesToExecute;

		AnalysisResult(Fraction maxRatio, HashSet<TwoSidedGraphEdge> tradesToExecute) {
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

	protected static class SearchContext {
		private Stack<SearchState> searchStack = new Stack<SearchState>();
		private Hashtable<SearchCacheKey, AnalysisResult> maxRatioPerState = new Hashtable<SearchCacheKey, AnalysisResult>();
		private final Currency destNode;
		private final SearchState sourceState;
		private final AnalysisResult sentinelRatio = new AnalysisResult(new Fraction(-1), null);
		private final OrderGraph orderGraphSnapshot;
		private final int contextMaxTrades;

		public SearchContext(Currency destNode, SearchState sourceState, OrderGraph orderGraphSnapshot, int maxTrades) {
			this.destNode = destNode;
			this.sourceState = sourceState;
			this.orderGraphSnapshot = orderGraphSnapshot;
			this.contextMaxTrades = maxTrades;
			expandSearchState(sourceState);
		}

		public void expandSearchState(SearchState searchState) {
			final boolean isDestNode = searchState.currency.equals(destNode) && searchState.parent != null;
			final SearchCacheKey searchStateKey = new SearchCacheKey(searchState);
			if (maxRatioPerState.containsKey(searchStateKey) || isDestNode) {
				if (searchState != sourceState) {
					updateMaxForParent(searchState, isDestNode);
				}
			} else if (searchState.visitedEdges.size() < contextMaxTrades) {
				maxRatioPerState.put(searchStateKey, sentinelRatio);
				final HashSet<TwoSidedGraphEdge> edgesFromSource = orderGraphSnapshot.getEdges(searchState.currency);
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
			if (!maxRatioPerState.containsKey(parentKey)) {
				throw new IllegalStateException(
						"Parent must have max ratio cache initialized before children are processed");
			}
			if (!maxRatioPerState.containsKey(childKey) && !isDestNode) {
				throw new IllegalStateException(
						"Child must have max ratio cache initialized before updating parent if not dest node");
			}

			final AnalysisResult prevMaxRatio = maxRatioPerState.get(parentKey);
			final AnalysisResult childMaxRatio = isDestNode ? new AnalysisResult(child.ratio, child.visitedEdges)
					: maxRatioPerState.get(childKey);

			if (childMaxRatio.compareTo(prevMaxRatio) > 0) {
				maxRatioPerState.put(parentKey, childMaxRatio);
			}
		}

		private SearchState getNextSearchStateIfNotEdgeExists(SearchState parent, TwoSidedGraphEdge edge) {
			// Cannot use the same edge twice because each edge represents a
			// possible trade at the current point in time
			if (!parent.visitedEdges.contains(edge)) {
				@SuppressWarnings("unchecked")
				HashSet<TwoSidedGraphEdge> newVisitedEdges = (HashSet<TwoSidedGraphEdge>) parent.visitedEdges.clone();
				newVisitedEdges.add(edge);
				return new SearchState(edge.graphEdge.destCurrency, parent, edge.graphEdge.ratio.multiply(parent.ratio),
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
		SearchContext searchCtx = new SearchContext(currencyToAccumulate,
				new SearchState(currencyToAccumulate, null, new Fraction(1), new HashSet<TwoSidedGraphEdge>()),
				(OrderGraph) sharedOrderGraph.clone(), maxTrades);

		while (searchCtx.getStackSize() > 0) {
			SearchState curState = searchCtx.popStack();
			searchCtx.expandSearchState(curState);
		}

		return searchCtx.getAnalysisResult();
	}

	protected AnalysisResult searchForArbitrageBellmanFord() {
		OrderGraph orderGraphSnapshot = (OrderGraph) sharedOrderGraph.clone();

		HashMap<Currency, Fraction> distanceToVertex = new HashMap<Currency, Fraction>();
		HashMap<Currency, TwoSidedGraphEdge> predecessor = new HashMap<Currency, TwoSidedGraphEdge>();

		List<Currency> vertices = orderGraphSnapshot.getVertices();
		for (Currency vertex : vertices) {
			distanceToVertex.put(vertex, null);
			predecessor.put(vertex, null);
		}

		distanceToVertex.put(currencyToAccumulate, new Fraction(0, 1));

		Fraction neg1 = new Fraction(-1);
		for (int i = 0; i < vertices.size() - 1; ++i) {
			for (Currency vertex : vertices) {
				final HashSet<TwoSidedGraphEdge> edgesFromVert = orderGraphSnapshot.getEdgesWithNonzeroQuantity(vertex);
        if (edgesFromVert == null) {
            continue;
        }
				for (TwoSidedGraphEdge graphEdge : edgesFromVert) {
					Fraction distanceToU = distanceToVertex.get(vertex);
					Fraction distanceToV = distanceToVertex.get(graphEdge.graphEdge.destCurrency);
					Fraction weight = graphEdge.graphEdge.ratio.logLossy().multiply(neg1);
					if (distanceToU != null) {
						if (distanceToV == null || distanceToU.add(weight).compareTo(distanceToV) < 0) {
							distanceToVertex.put(graphEdge.graphEdge.destCurrency, distanceToU.add(weight));
							predecessor.put(graphEdge.graphEdge.destCurrency, graphEdge);
						}
					}
				}
			}
		}

		AnalysisResult analysisResult = null;
		for (Currency vertex : vertices) {
			final HashSet<TwoSidedGraphEdge> edgesFromVert = orderGraphSnapshot.getEdgesWithNonzeroQuantity(vertex);
      if (edgesFromVert == null) {
          continue;
      }
			for (TwoSidedGraphEdge graphEdge : edgesFromVert) {
				Fraction distanceToU = distanceToVertex.get(vertex);
				Fraction distanceToV = distanceToVertex.get(graphEdge.graphEdge.destCurrency);
				Fraction weight = graphEdge.graphEdge.ratio.logLossy().multiply(neg1);
				if (distanceToU != null && distanceToV != null && distanceToU.add(weight).compareTo(distanceToV) < 0) {
					// This is a negative weight cycle, profit opportunity.
					// Start at V and work backwards until we get back to a
					// vertex we've seen before.
					// Then start here and work around the loop to build a list
					// of trades to execute.
					Currency currentVertex = vertex;
					HashSet<Currency> vertsVisited = new HashSet<Currency>();
					while (!vertsVisited.contains(currentVertex)) {
						vertsVisited.add(currentVertex);
						currentVertex = predecessor.get(currentVertex).sourceCurrency;
					}
					HashSet<TwoSidedGraphEdge> tradesToExecute = new HashSet<TwoSidedGraphEdge>();
					while (true) {
						TwoSidedGraphEdge tradeToExecute = predecessor.get(currentVertex);
						if (tradesToExecute.contains(tradeToExecute)) {
							break;
						}
						tradesToExecute.add(tradeToExecute);
						currentVertex = tradeToExecute.sourceCurrency;
					}

					if (tradesToExecute.size() <= maxTrades) {
						Fraction pathProd = new Fraction(1);
						for (TwoSidedGraphEdge graphEdgeToExecute : tradesToExecute) {
							pathProd = pathProd.multiply(graphEdgeToExecute.graphEdge.ratio);
						}
						if (pathProd.compareTo(new Fraction(1)) <= 0) {
							throw new IllegalStateException("We goofed. The above code should never give "
									+ "us a path with a final ratio that is less than or equal to 1");
						}

						final AnalysisResult analysisResultForThisPath = new AnalysisResult(pathProd, tradesToExecute);
						if (analysisResult == null || analysisResult.compareTo(analysisResultForThisPath) < 0) {
							analysisResult = analysisResultForThisPath;
						}
					}
				}
			}
		}
		if (analysisResult == null) {
			analysisResult = new AnalysisResult(new Fraction(0), null);
		}
		return analysisResult;
	}

	public void onOrderGraphChanged() {
		semaphore.release();
	}

	public void startAnalyzingOrderBook() {
		if (analyzerThread == null) {
			analyzerThread = new Thread() {
				public void run() {
					shouldExit.setFalse();
					while (!shouldExit.booleanValue()) {
						try {
							semaphore.acquire();
							analysisHandler.onOrderBookAnalysisComplete(searchForArbitrageBellmanFord());
						} catch (InterruptedException e) {
							shouldExit.setTrue();
							;
						}
					}
				}
			};
			analyzerThread.start();
		}
	}

	public void stopAnalyzingOrderBook() throws InterruptedException {
		if (analyzerThread != null) {
			shouldExit.setTrue();
			semaphore.release();
			analyzerThread.join();
		}
	}
}
