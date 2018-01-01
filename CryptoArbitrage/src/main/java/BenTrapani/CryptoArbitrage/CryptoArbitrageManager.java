package BenTrapani.CryptoArbitrage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.knowm.xchange.currency.CurrencyPair;

import java.util.LinkedList;

import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.ProductSubscription.ProductSubscriptionBuilder;
import info.bitrich.xchangestream.core.StreamingExchange;
import io.reactivex.disposables.Disposable;

public class CryptoArbitrageManager {
	private ArrayList<Disposable> subscriptions; 
	private StreamingExchange[] exchanges;
	private OrderGraph orderGraph = new OrderGraph();
	private OrderBookAggregator orderBookAggregator = new OrderBookAggregator(orderGraph);
	
	public CryptoArbitrageManager(StreamingExchange[] exchanges) {
		subscriptions = new ArrayList<Disposable>(exchanges.length);
		for (StreamingExchange exchange: exchanges) {
			Set<CurrencyPair> currenciesForExchange = exchange.getExchangeMetaData().getCurrencyPairs().keySet();
			ProductSubscriptionBuilder builder = ProductSubscription.create();
			for (CurrencyPair currencyPair: currenciesForExchange) {
				builder = builder.addOrderbook(currencyPair);
			}
			exchange.connect(builder.build()).blockingAwait();
		}
		this.exchanges = exchanges.clone();
	}
	
	public void startArbitrage() {
		stopArbitrage();
		for (int i = 0; i < exchanges.length; i++) {
			Disposable[] tempDisposables = orderBookAggregator.createConsumerForExchange(exchanges[i]);
			List<Disposable> subsList = new LinkedList<Disposable>(Arrays.asList(tempDisposables));
			subscriptions.addAll(subsList);
		}
	}
	
	public void stopArbitrage() {
		for (Disposable disp: subscriptions) {
			disp.dispose();
		}
	}
}
