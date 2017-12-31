package BenTrapani.CryptoArbitrage;

import io.reactivex.disposables.Disposable;
import java.util.Hashtable;

import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;

import info.bitrich.xchangestream.core.StreamingExchange;

public class OrderBookAggregator {
	public class AggregateOrderBookKey {
		private String exchangeName;
		private CurrencyPair currencyPair;
		
		public AggregateOrderBookKey(String exchangeName, CurrencyPair currencyPair) {
			this.exchangeName = exchangeName;
			this.currencyPair = currencyPair;
		}
		
		public String getExchangeName() {
			return exchangeName;
		}
		
		public CurrencyPair getCurrencyPair() {
			return currencyPair;
		}
		
		@Override
		public int hashCode() {
			int nameHash = exchangeName.hashCode();
			int curPairHash = currencyPair.hashCode();
			int combinedHash = nameHash * 31 + curPairHash;
			return combinedHash;
		}
	}
	
	Hashtable<AggregateOrderBookKey, OrderBook> orderBookPerExchange;
	
	public OrderBookAggregator() {
	}
	
	public Disposable createConsumerForExchange(StreamingExchange exchange) {
		String exchangeName = exchange.getExchangeSpecification().getExchangeName();
		CurrencyPair pairToSubscribe = CurrencyPair.BTC_USD;
		AggregateOrderBookKey obKey = new AggregateOrderBookKey(exchangeName, pairToSubscribe);
		return exchange.getStreamingMarketDataService()
        .getOrderBook(pairToSubscribe)
        .subscribe(orderBook -> {
        	// Edges are built from orders in the order book. Each edge contains an action (buy/sell), 
        	// dest currency, price and quantity. Ratio can be derived by quantity / price. Multiply ratios along
        	// a loop. If final ratio is > 1, execute all trades along path. 
        	// Edge construction:
        	// 	If order book entry is buy, add edge with action sell from base to counter with given quantity and price.
        	// 	If order book entry is sell, add edge with action buy from counter to base with given quantity and price.
        	// Graph structure: Hashtable of currency pair to a list of edges as described above. 
        	// Traverse graph on change to find profitable loops (ratio > 1). Restriction: cannot traverse the same edge more than once.
        	// Compute quantity of all trades with respect to the chosen source currency and computed ratio at each point.
        	// Execute all trades along path with adjusted quantity equal to minimum adjusted quantity along the path.
        	// If all trades are successful, the only change in currency allocation will be an increase in the source currency by the given ratio.
        	// If one or more trades fail, the allocation among currencies will become lopsided but an increase in source currency will still be observed.
        	orderBookPerExchange.put(obKey, orderBook);
        });
	}
}
