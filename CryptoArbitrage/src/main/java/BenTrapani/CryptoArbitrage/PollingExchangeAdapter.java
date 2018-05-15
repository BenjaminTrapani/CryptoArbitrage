package BenTrapani.CryptoArbitrage;

import java.io.IOException;
import java.util.ArrayList;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.meta.RateLimit;

import info.bitrich.xchangestream.core.ProductSubscription.ProductSubscriptionBuilder;
import io.reactivex.Observable;

public class PollingExchangeAdapter extends StreamingExchangeSubset {
	private ArrayList<Thread> pollingThreads = new ArrayList<Thread>();
	private boolean shouldPollingThreadsRun;

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
		shouldPollingThreadsRun = true;
		RateLimit[] rateLimits = exchange.getExchangeMetaData().getPublicRateLimits();
		int numCurrencyPairs = getCurrencyPairs().size();
		// Multiply by numCurrencyPairs here because we will hit the API numCurrencyPairs times per delay interval
		long delayMillis = ExchangeMetaData.getPollDelayMillis(rateLimits) * numCurrencyPairs;
		return Observable.<OrderBook>create(e -> {
			Thread pollingThread = new Thread() {
				public void run() {
					while (shouldPollingThreadsRun) {
						OrderBook orderBookSnapshot = null;
						try {
							synchronized(exchange) {
								orderBookSnapshot = exchange.getMarketDataService().getOrderBook(currencyPair);
							}
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
						} catch (InterruptedException exception) {
							// TODO Auto-generated catch block
							exception.printStackTrace();
						}
					}
				}
			};
			synchronized(pollingThreads) {
				pollingThreads.add(pollingThread);
			}
			pollingThread.start();
		}).doOnDispose(() -> {
			shouldPollingThreadsRun = false;
			synchronized(pollingThreads) {
				for (Thread pollingThread : pollingThreads) {
					pollingThread.join();
				}
			}
		}).doOnError((Throwable t) -> {
			System.out.println("Error in polling exchange: " + t.toString());
		}).share();
	}
}
