package BenTrapani.CryptoArbitrage;

import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;

import info.bitrich.xchangestream.core.ProductSubscription.ProductSubscriptionBuilder;
import info.bitrich.xchangestream.core.StreamingExchange;
import io.reactivex.Observable;

/***
 * 
 * @author benjamintrapani
 *
 * Implements a limited subset of the functionality of a streaming exchange. It implements
 * only the functionality required by the OrderBookAnalyzer and Aggregator.
 *  
 */
public class StreamingExchangeAdapter extends StreamingExchangeSubset {
	private StreamingExchange streamingExchange;
	
	public StreamingExchangeAdapter(StreamingExchange exchange) {
		super(exchange);
		this.streamingExchange = exchange;
	}
	
	@Override
	public void buildAndWait(ProductSubscriptionBuilder builder) {
		streamingExchange.connect(builder.build()).blockingAwait();
	}
	
	@Override
	public Observable<OrderBook> getOrderBook(CurrencyPair currencyPair) {
		return streamingExchange.getStreamingMarketDataService().getOrderBook(currencyPair);
	}
}
