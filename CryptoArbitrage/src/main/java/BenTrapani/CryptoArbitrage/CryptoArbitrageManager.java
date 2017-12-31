package BenTrapani.CryptoArbitrage;

import org.knowm.xchange.currency.CurrencyPair;

import info.bitrich.xchangestream.core.StreamingExchange;
import io.reactivex.disposables.Disposable;

public class CryptoArbitrageManager {
	private Disposable[] subscriptions; 
	private StreamingExchange[] exchanges;
	private OrderBookAggregator orderBookAggregator = new OrderBookAggregator();
	
	public CryptoArbitrageManager(StreamingExchange[] exchanges) {
		subscriptions = new Disposable[exchanges.length];
		for (StreamingExchange exchange: exchanges) {
			exchange.connect().blockingAwait();
		}
		this.exchanges = exchanges.clone();
	}
	
	public void startArbitrage() {
		for (int i = 0; i < exchanges.length; i++) {
			if (subscriptions[i] != null) {
				subscriptions[i].dispose();
			}
			subscriptions[i] = orderBookAggregator.createConsumerForExchange(exchanges[i]);
		}
	}
	
	public void stopArbitrage() {
		for (int i = 0; i < subscriptions.length; i++) {
			subscriptions[i].dispose();
		}
	}
}
