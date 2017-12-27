package BenTrapani.CryptoArbitrage;

import org.knowm.xchange.currency.CurrencyPair;

import info.bitrich.xchangestream.bitstamp.BitstampStreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;
import io.reactivex.disposables.Disposable;

public class CryptoArbitrageManager {
	private StreamingExchange exchange = StreamingExchangeFactory.INSTANCE.createExchange(BitstampStreamingExchange.class.getName());
	private Disposable subscription = null; 
	
	public CryptoArbitrageManager() {
		exchange.connect().blockingAwait();
	}
	
	public void startArbitrage() {
		subscription = exchange.getStreamingMarketDataService()
                .getOrderBook(CurrencyPair.BTC_USD)
                .subscribe(orderBook -> {
                     System.out.println(orderBook.getBids());
                     System.out.println(orderBook.getAsks());
                });
	}
	
	public void stopArbitrage() {
		subscription.dispose();
	}
}
