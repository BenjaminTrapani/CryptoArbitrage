package BenTrapani.CryptoArbitrage;

import io.reactivex.disposables.Disposable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.dto.account.Fee;

public class OrderBookAggregator {
	
	protected static class KBestOrders {
		public final List<LimitOrder> kBestBids;
		public final List<LimitOrder> kBestAsks;
		
		private List<LimitOrder> sortAndTakeFirstK(List<LimitOrder> orders, int k) {
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
		public KBestOrders() {
			this.kBestBids = new ArrayList<LimitOrder>();
			this.kBestAsks = new ArrayList<LimitOrder>();
		}
	}
	
	private OrderGraph sharedOrderGraph;
	private OrderGraphChangeHandler orderGraphChangeHandler;
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
		private final OneSidedOrderBookDiff buyDiffs;
		private final OneSidedOrderBookDiff sellDiffs;
		
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
	
	protected static class OrderBookConsumer implements io.reactivex.functions.Consumer<OrderBook> {
		
		public OrderBookConsumer(int numBestBids, int numBestAsks, 
				OrderGraph sharedOrderGraph, String exchangeName, Fraction feeToTrade,
				CurrencyPair currencyPair, OrderGraphChangeHandler orderGraphChangeHandler) {
			this.numBestBids = numBestBids;
			this.numBestAsks = numBestAsks;
			this.sharedOrderGraph = sharedOrderGraph;
			this.exchangeName = exchangeName;
			this.feeToTrade = feeToTrade;
			this.currencyPair = currencyPair;
			this.orderGraphChangeHandler = orderGraphChangeHandler;
		}
		
		@Override
		public void accept(OrderBook orderBook) throws Exception {
			synchronized (lockObj) {
			KBestOrders newKBest = new KBestOrders(new ArrayList<LimitOrder>(orderBook.getBids()), 
					new ArrayList<LimitOrder>(orderBook.getAsks()), 
					numBestBids,
					numBestAsks);
			OrderBookDiff diff;
				diff = new OrderBookDiff(prevOrderBook, newKBest);
				//System.out.println("Got order book update from exchange " + exchangeName + " on thread " + Thread.currentThread().getId());

				List<LimitOrder> deletions = diff.getDeletions();
				List<LimitOrder> additions = diff.getAdditions();
				for (LimitOrder deletion : deletions) {
					if (!sharedOrderGraph.removeEdge(deletion.getCurrencyPair().counter,
							deletion.getCurrencyPair().base, exchangeName, getIsLimitOrderBuyForUs(deletion),
							new Fraction(deletion.getRemainingAmount()), 
							new Fraction(deletion.getLimitPrice()), 
							feeToTrade)) {
						throw new IllegalStateException(
								"Failed to remove edge that should have existed according to diff: \n"
										+ " updating currency " + currencyPair.toString() + " for exchange "
										+ exchangeName + " on thread " + Thread.currentThread().getId());
					}
				}
				for (LimitOrder addition : additions) {
					sharedOrderGraph.addEdge(addition.getCurrencyPair().counter, addition.getCurrencyPair().base,
							exchangeName, getIsLimitOrderBuyForUs(addition), 
							new Fraction(addition.getRemainingAmount()),
							new Fraction(addition.getLimitPrice()), 
							feeToTrade);
				}

				// Returns immediately and analysis starts running in another
				// thread
				orderGraphChangeHandler.onOrderGraphChanged();

				prevOrderBook = newKBest;
				//System.out.println("Finished order book update from exchange " + exchangeName + " on thread " + Thread.currentThread().getId());
			}
		}
		
		private KBestOrders prevOrderBook = new KBestOrders();
		private Object lockObj = new Object();
		
		private final int numBestBids;
		private final int numBestAsks;
		private final Fraction feeToTrade;
		private final String exchangeName;
		private final CurrencyPair currencyPair;
		
		private OrderGraphChangeHandler orderGraphChangeHandler;
		private OrderGraph sharedOrderGraph;
	}
	
	private static boolean getIsLimitOrderBuyForUs(LimitOrder order){
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
		int idx = 0;
		
		Map<CurrencyPair, Fee> feeMap = null;
		try {
			feeMap = exchange.getDynamicTradingFees();
		} catch (NotAvailableFromExchangeException e) {
			System.out.println("Dynamic trading fees unsupported for exchange " + exchangeName);
		} catch (NotYetImplementedForExchangeException e) {
			System.out.println("Dynamic trading fees unsupported for exchange " + exchangeName);
		}
		
		for (CurrencyPair currencyPair : currenciesForExchange) {
			// feeToTrade is a fraction by volume taker fee (fraction expressed in dest currency)
			CurrencyPairMetaData metadataForPair = currencyPairToMeta.get(currencyPair);
			if (metadataForPair == null ){
				throw new IllegalStateException("Could not find metadata for currency pair " + currencyPair.toString());
			}
			
			// TODO also account for min and max trade amounts
			
			BigDecimal currentTradingFee = null;
			if (feeMap != null) {
				Fee feeForPair = feeMap.get(currencyPair);
				if (feeForPair == null) {
					System.out.println("Missing maker taker fees for currency " + currencyPair.toString() + 
							" on exchange " + exchangeName);
				} else {
					currentTradingFee = feeForPair.getTakerFee();
				}
			}
			if (currentTradingFee == null)
			{
				currentTradingFee = metadataForPair.getTradingFee();
				if (currentTradingFee == null) {
					throw new IllegalStateException("Could not get fee to trade for exchange " +
													exchangeName + 
													" and currency pair " + currencyPair.toString());
				}
			}
			
			Fraction feeToTrade = new Fraction(currentTradingFee);
			OrderBookConsumer orderBookConsumer = new OrderBookConsumer(numBestBids, 
					numBestAsks, sharedOrderGraph, exchangeName, feeToTrade, currencyPair, orderGraphChangeHandler);
			
			//System.out.println("Exchange " + exchangeName + " subscribing to " + currencyPair);
			disposablesPerCurrency[idx] = exchange.getOrderBook(currencyPair)
												  .subscribe(orderBookConsumer);
			idx++;
		}
		return disposablesPerCurrency;
	}
}
