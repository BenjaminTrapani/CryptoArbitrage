package BenTrapani.CryptoArbitrage;

import java.util.HashSet;
import java.util.Hashtable;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.marketdata.OrderBook;

public class OrderBookAnalyzer {
	private OrderBookAggregator orderBookAggregator;
	private Thread analyzerThread;
	
	public OrderBookAnalyzer(OrderBookAggregator orderBookAggregator) {
		this.orderBookAggregator = orderBookAggregator;
	}
	
	class CurrencyGraphNodeEdge {
		public final Currency nextCurrency;
		public final String exchangeName;
		public final double priceRatio;
		public CurrencyGraphNodeEdge(Currency nextCur, String exchangeName, double priceRatio) {
			this.nextCurrency = nextCur;
			this.exchangeName = exchangeName;
			this.priceRatio = priceRatio;
		}
	}
	class CurrencyGraphNode {
		public final Currency curCurrency;
		public HashSet<CurrencyGraphNodeEdge> nextCurrencies = new HashSet<CurrencyGraphNodeEdge>();
		
		public CurrencyGraphNode(Currency currency) {
			this.curCurrency = currency;
		}
	}
	
	private void buildCurrencyGraphWithRoot(CurrencyGraphNode initialNode, 
			Hashtable<OrderBookAggregator.AggregateOrderBookKey, OrderBook> orderBookPerCurrencyExchange) {
		
	}
	private CurrencyGraphNode buildCurrencyGraph(Hashtable<OrderBookAggregator.AggregateOrderBookKey, 
			OrderBook> orderBookPerCurrencyExchange) {
		if (orderBookPerCurrencyExchange.size() == 0) {
			throw new IllegalArgumentException("Order book must contain at least one entry in order to build currency graph");
		}
		OrderBookAggregator.AggregateOrderBookKey randomKey = orderBookPerCurrencyExchange.keys().nextElement();
		CurrencyGraphNode initialNode = new CurrencyGraphNode(randomKey.getCurrencyPair().base);
		
		return initialNode;
	}
	// Only analyze simple one-hop arbitrage. 
	// TODO check for multi-step arbitrage opportunities (A->B + B->C != A->C)
	// TODO write a function that gets the min and max paths between currencies A and C (buy least and most of C with A)
	// Convert A -> C on larger diff path, and convert C -> A along smaller diff path. 
	// Profit is represented in increase in A. TAKE INTO ACCOUNT COMMISSIONS!
	// To move from A -> C along path, buy pairs along path. To move from C -> A, sell pairs along path.
	// Buy arbitrary initial A, and sell amount of C obtained via conversion from A -> C. Buy or sell full
	// quantity after most recent hop through all intermediate nodes.
	private void searchForArbitrage() {
		
	}
	
	//Make this event-driven: issue an event in OrderBookAggregator and perform 
	// arbitrage search in response to that event.
	public void startAnalyzingOrderBook() {
		if (analyzerThread == null) {
			analyzerThread = new Thread() {
				public void run() {
					searchForArbitrage();
				}
			};
			analyzerThread.start();
		}
	}
	
	public void stopAnalyzingOrderBook() throws InterruptedException {
		if (analyzerThread != null) {
			analyzerThread.join();
		}
	}
}
