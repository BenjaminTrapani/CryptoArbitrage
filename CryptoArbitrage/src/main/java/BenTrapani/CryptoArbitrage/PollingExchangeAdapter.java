package BenTrapani.CryptoArbitrage;

import java.io.IOException;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.meta.RateLimit;

import info.bitrich.xchangestream.core.ProductSubscription.ProductSubscriptionBuilder;
import io.reactivex.Observable;

public class PollingExchangeAdapter extends StreamingExchangeSubset {
	private Thread pollingThread;
	private boolean shouldExitPollingThread;
	
	public PollingExchangeAdapter(Exchange exchange) {
		super(exchange);
	}
	
	/***
	 * Intentionally skip this, as it is not needed for non-streaming exchange
	 */
	@Override
	public void buildAndWait(ProductSubscriptionBuilder builder) {
	}

	@Override
	public Observable<OrderBook> getOrderBook(CurrencyPair currencyPair) {
		RateLimit[] rateLimits = exchange.getExchangeMetaData().getPublicRateLimits();
		long delayMillis = ExchangeMetaData.getPollDelayMillis(rateLimits);
		shouldExitPollingThread = false;
		return Observable.<OrderBook>create(e -> {
			pollingThread = new Thread(){
				public void run(){
					while (!shouldExitPollingThread) {
						OrderBook orderBookSnapshot = null;
						try {
							orderBookSnapshot = exchange.getMarketDataService().getOrderBook(currencyPair);
						} catch (IOException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
						long curTimeMillis = System.currentTimeMillis();
						if (orderBookSnapshot != null) {
							e.onNext(orderBookSnapshot);
						}
						long timeToSleep = delayMillis - (System.currentTimeMillis() - curTimeMillis) + 1;
						try {
							Thread.sleep(timeToSleep);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
			    }
			};
		}).doOnDispose(() -> {
			shouldExitPollingThread = true;
			pollingThread.join();
		}).share();
	}
}
