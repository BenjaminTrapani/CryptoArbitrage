package BenTrapani.CryptoArbitrage;

import io.reactivex.disposables.Disposable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;

import info.bitrich.xchangestream.core.StreamingExchange;

public class OrderBookAggregator {
	
	protected static class KBestOrders {
		public final List<LimitOrder> kBestBids;
		public final List<LimitOrder> kBestAsks;
		
		private static List<LimitOrder> sortAndTakeFirstK(List<LimitOrder> orders, int k) {
			Collections.sort(orders);
			if (k < orders.size()) {
				orders = orders.subList(0, k);
			}
			return orders;
		}
		
		public KBestOrders(List<LimitOrder> allBids, 
				List<LimitOrder> allAsks,
				final int maxBids,
				final int maxAsks) {
			this.kBestBids = sortAndTakeFirstK(allBids, maxBids);
			this.kBestAsks = sortAndTakeFirstK(allAsks, maxAsks);
		}
	}
	
	private OrderGraph sharedOrderGraph;
	private OrderGraphChangeHandler orderGraphChangeHandler;
	private Hashtable<StreamingExchangeSubset, ArrayList<KBestOrders>> prevOrderBooksMap = 
			new Hashtable<StreamingExchangeSubset, ArrayList<KBestOrders>>();
	private final int numBestBids;
	private final int numBestAsks;
	
	public OrderBookAggregator(OrderGraph orderGraph, 
			OrderGraphChangeHandler orderGraphChangeHandler,
			int numBestBids,
			int numBestAsks) {
		this.sharedOrderGraph = orderGraph;
		this.orderGraphChangeHandler = orderGraphChangeHandler;
		this.numBestBids = numBestBids;
		this.numBestAsks = numBestAsks;
	}

	protected static class OneSidedOrderBookDiff {
		private List<LimitOrder> additions = new ArrayList<LimitOrder>();
		private List<LimitOrder> deletions = new ArrayList<LimitOrder>();

		public OneSidedOrderBookDiff(List<LimitOrder> source, List<LimitOrder> dest) {
			HashSet<LimitOrder> sourceSet = new HashSet<LimitOrder>(source);
			// Remove all elements in dest from source.
			// If element is in dest and not in source set, it is an addition.
			// Anything left in sourceSet is a deletion
			for (LimitOrder destOrder : dest) {
				if (!sourceSet.contains(destOrder)) {
					additions.add(destOrder);
				} else {
					sourceSet.remove(destOrder);
				}
			}

			for (LimitOrder remainingOrder : sourceSet) {
				deletions.add(remainingOrder);
			}
		}

		List<LimitOrder> getAdditions() {
			return additions;
		}

		List<LimitOrder> getDeletions() {
			return deletions;
		}
	}

	protected static class OrderBookDiff {
		private OneSidedOrderBookDiff buyDiffs;
		private OneSidedOrderBookDiff sellDiffs;
		
		// Source expected to already be sorted
		public OrderBookDiff(KBestOrders source, KBestOrders dest) {
			buyDiffs = new OneSidedOrderBookDiff(source.kBestBids, dest.kBestBids);
			sellDiffs = new OneSidedOrderBookDiff(source.kBestAsks, dest.kBestAsks);
		}

		public List<LimitOrder> getAdditions() {
			List<LimitOrder> allAdditions = new ArrayList<LimitOrder>(buyDiffs.getAdditions());
			allAdditions.addAll(sellDiffs.getAdditions());
			return allAdditions;
		}

		public List<LimitOrder> getDeletions() {
			List<LimitOrder> allDeletions = new ArrayList<LimitOrder>(buyDiffs.getDeletions());
			allDeletions.addAll(sellDiffs.getDeletions());
			return allDeletions;
		}
	}
	
	private boolean getIsLimitOrderBuyForUs(LimitOrder order){
		return order.getType() == OrderType.ASK;
	}
	
	// Edges are built from orders in the order book. Each edge contains an
	// action (buy/sell),
	// dest currency, price and quantity. Ratio can be derived by quantity /
	// price for buy, and price / quantity for sell. Multiply ratios along
	// a loop. If final ratio is > 1, execute all trades along path.
	// Edge construction:
	// If order book entry is buy, add edge with action sell from base to
	// counter with given quantity and price.
	// If order book entry is sell, add edge with action buy from counter to
	// base with given quantity and price.
	// Graph structure: Hashtable of currency pair to a list of edges as
	// described above.
	// Traverse graph on change to find profitable loops (ratio > 1).
	// Restriction: cannot traverse the same edge more than once.
	// Compute quantity of all trades with respect to the chosen source currency
	// and computed ratio at each point.
	// Execute all trades along path with adjusted quantity equal to minimum
	// adjusted quantity along the path.
	// If all trades are successful, the only change in currency allocation will
	// be an increase in the source currency by the given ratio.
	// If one or more trades fail, the allocation among currencies will become
	// lopsided but an increase in source currency will still be observed (assuming that trades
	// connected to and from source currency all succeed)
	public Disposable[] createConsumerForExchange(StreamingExchangeSubset exchange) {
		String exchangeName = exchange.getExchangeName();
		Set<CurrencyPair> currenciesForExchange = exchange.getCurrencyPairs();
		Disposable[] disposablesPerCurrency = new Disposable[currenciesForExchange.size()];
		Map<CurrencyPair, CurrencyPairMetaData> currencyPairToMeta = exchange.getCurrencyPairMetadata();
		// Don't need to synchronize access to prevOrderBooksMap because each
		// exchange only reads from its own key and Hashtable is thread-safe internally by default.
		prevOrderBooksMap.put(exchange, new ArrayList<KBestOrders>());
		int idx = 0;
		
		for (CurrencyPair currencyPair : currenciesForExchange) {
			// feeToTrade is a fraction by volume taker fee (fraction expressed in dest currency)
			BigDecimal feeToTrade = currencyPairToMeta.get(currencyPair).getTradingFee();
			if (feeToTrade == null) {
				throw new IllegalStateException("Could not get fee to trade for exchange " +
												exchange.toString() + 
												" and currency pair " + currencyPair.toString());
			}
			// TODO also account for min and max trade amounts
			
			KBestOrders initialOrderBook = new KBestOrders(new ArrayList<LimitOrder>(), new ArrayList<LimitOrder>(),
					numBestBids, numBestAsks);
			ArrayList<KBestOrders> prevOrderBooks = prevOrderBooksMap.get(exchange);
			synchronized (prevOrderBooks) {
				prevOrderBooks.add(initialOrderBook);
			}
			final int prevOrderBookIdx = idx;
			//System.out.println("Exchange " + exchangeName + " subscribing to " + currencyPair);
			disposablesPerCurrency[idx] = exchange.getOrderBook(currencyPair)
					.subscribe(orderBook -> {
						KBestOrders newKBest = new KBestOrders(orderBook.getBids(), 
								orderBook.getAsks(), 
								numBestBids,
								numBestAsks);
						OrderBookDiff diff;
						synchronized (prevOrderBooks) {
							diff = new OrderBookDiff(prevOrderBooks.get(prevOrderBookIdx), newKBest);
						}
						
						//System.out.println("Got order book update from exchange " + exchangeName);
						
						List<LimitOrder> deletions = diff.getDeletions();
						List<LimitOrder> additions = diff.getAdditions();
						for (LimitOrder deletion : deletions) {
							if (!sharedOrderGraph.removeEdge(deletion.getCurrencyPair().counter,
									deletion.getCurrencyPair().base, exchangeName, getIsLimitOrderBuyForUs(deletion),
									deletion.getRemainingAmount(), deletion.getLimitPrice(), feeToTrade)) {
								throw new IllegalStateException(
										"Failed to remove edge that should have existed according to diff: \n" +
												" updating currency " + currencyPair.toString() + 
												" for exchange " + exchangeName + 
												" on thread " + Thread.currentThread().getId() + 
												" with order book idx " + prevOrderBookIdx);
							}
						}
						for (LimitOrder addition : additions) {
							sharedOrderGraph.addEdge(addition.getCurrencyPair().counter,
									addition.getCurrencyPair().base, exchangeName, getIsLimitOrderBuyForUs(addition),
									addition.getRemainingAmount(), addition.getLimitPrice(), feeToTrade);
						}
						
						// Returns immediately and analysis starts running in another thread
						orderGraphChangeHandler.onOrderGraphChanged();
						
						synchronized (prevOrderBooks) {
							prevOrderBooks.set(prevOrderBookIdx, newKBest);
						}
					});
			idx++;
		}
		return disposablesPerCurrency;
	}
}
