package BenTrapani.CryptoArbitrage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;

import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.ProductSubscription.ProductSubscriptionBuilder;
import info.bitrich.xchangestream.core.StreamingExchange;
import io.reactivex.disposables.Disposable;

public class CryptoArbitrageManager {
	private ArrayList<Disposable> subscriptions; 
	private StreamingExchangeSubset[] exchanges;
	private OrderGraph orderGraph = new OrderGraph();
	private ArbitrageExecutor arbitrageExecutor = new ArbitrageExecutor(new BigDecimal(1.0), Currency.USD);
	private OrderBookAnalyzer orderBookAnalyzer = new OrderBookAnalyzer(orderGraph, Currency.USD, 4, arbitrageExecutor);
	private OrderBookAggregator orderBookAggregator = new OrderBookAggregator(orderGraph, orderBookAnalyzer, 2, 2);
	
	public CryptoArbitrageManager(StreamingExchangeSubset[] exchanges) {
		subscriptions = new ArrayList<Disposable>(exchanges.length);
		for (StreamingExchangeSubset exchange: exchanges) {
			Set<CurrencyPair> currenciesForExchange = exchange.getCurrencyPairs();
			ProductSubscriptionBuilder builder = ProductSubscription.create();
			for (CurrencyPair currencyPair: currenciesForExchange) {
				builder = builder.addOrderbook(currencyPair);
			}
			exchange.buildAndWait(builder);
		}
		this.exchanges = exchanges.clone();
		arbitrageExecutor.setExchanges(exchanges);
	}
	
	public void startArbitrage() {
		stopArbitrage();
		for (int i = 0; i < exchanges.length; i++) {
			Disposable[] tempDisposables = orderBookAggregator.createConsumerForExchange(exchanges[i]);
			List<Disposable> subsList = new ArrayList<Disposable>(Arrays.asList(tempDisposables));
			subscriptions.addAll(subsList);
		}
		orderBookAnalyzer.startAnalyzingOrderBook();
	}
	
	public void stopArbitrage() {
		for (Disposable disp: subscriptions) {
			disp.dispose();
		}
		try {
			orderBookAnalyzer.stopAnalyzingOrderBook();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
