package BenTrapani.CryptoArbitrage;

import io.reactivex.disposables.Disposable;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;

import info.bitrich.xchangestream.core.StreamingExchange;

public class OrderBookAggregator {
	private OrderGraph sharedOrderGraph;
	private OrderGraphChangeHandler orderGraphChangeHandler;
	private ArrayList<OrderBook> prevOrderBooks = new ArrayList<OrderBook>();

	public OrderBookAggregator(OrderGraph orderGraph, OrderGraphChangeHandler orderGraphChangeHandler) {
		this.sharedOrderGraph = orderGraph;
		this.orderGraphChangeHandler = orderGraphChangeHandler;
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

		public OrderBookDiff(OrderBook source, OrderBook dest) {
			buyDiffs = new OneSidedOrderBookDiff(source.getBids(), dest.getBids());
			sellDiffs = new OneSidedOrderBookDiff(source.getAsks(), dest.getAsks());
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
	// price. Multiply ratios along
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
	// lopsided but an increase in source currency will still be observed.
	public Disposable[] createConsumerForExchange(StreamingExchange exchange) {
		String exchangeName = exchange.getExchangeSpecification().getExchangeName();
		Set<CurrencyPair> currenciesForExchange = exchange.getExchangeMetaData().getCurrencyPairs().keySet();
		Disposable[] disposablesPerCurrency = new Disposable[currenciesForExchange.size()];
		int idx = 0;
		for (CurrencyPair currencyPair : currenciesForExchange) {
			OrderBook initialOrderBook = new OrderBook(new Date(), new ArrayList<LimitOrder>(),
					new ArrayList<LimitOrder>());
			synchronized (prevOrderBooks) {
				prevOrderBooks.add(initialOrderBook);
			}
			final int prevOrderBookIdx = idx;
			System.out.println("Exchange " + exchangeName + " subscribing to " + currencyPair);
			disposablesPerCurrency[idx] = exchange.getStreamingMarketDataService().getOrderBook(currencyPair)
					.subscribe(orderBook -> {
						OrderBookDiff diff;
						synchronized (prevOrderBooks) {
							diff = new OrderBookDiff(prevOrderBooks.get(prevOrderBookIdx), orderBook);
						}
						List<LimitOrder> deletions = diff.getDeletions();
						List<LimitOrder> additions = diff.getAdditions();
						for (LimitOrder deletion : deletions) {
							if (!sharedOrderGraph.removeEdge(deletion.getCurrencyPair().counter,
									deletion.getCurrencyPair().base, exchangeName, getIsLimitOrderBuyForUs(deletion),
									deletion.getRemainingAmount(), deletion.getLimitPrice())) {
								throw new IllegalStateException(
										"Failed to remove edge that should have existed according to diff");
							}
						}
						for (LimitOrder addition : additions) {
							sharedOrderGraph.addEdge(addition.getCurrencyPair().counter,
									addition.getCurrencyPair().base, exchangeName, getIsLimitOrderBuyForUs(addition),
									addition.getRemainingAmount(), addition.getLimitPrice());
						}
						
						// Returns immediately and analysis starts running in another thread
						orderGraphChangeHandler.onOrderGraphChanged();
						
						synchronized (prevOrderBooks) {
							prevOrderBooks.set(prevOrderBookIdx, orderBook);
						}
					});
			idx++;
		}
		return disposablesPerCurrency;
	}
}
