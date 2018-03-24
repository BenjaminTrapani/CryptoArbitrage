package BenTrapani.CryptoArbitrage;

import java.util.Map;
import java.util.Set;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;

import info.bitrich.xchangestream.core.ProductSubscription.ProductSubscriptionBuilder;
import io.reactivex.Observable;

public abstract class StreamingExchangeSubset {
	protected Exchange exchange;
	
	protected StreamingExchangeSubset(Exchange exchange) {
		this.exchange = exchange;
	}
	
	public Set<CurrencyPair> getCurrencyPairs() {
		return exchange.getExchangeMetaData().getCurrencyPairs().keySet();
	}
	
	public Map<CurrencyPair, CurrencyPairMetaData> getCurrencyPairMetadata() {
		return exchange.getExchangeMetaData().getCurrencyPairs();
	}
	
	public abstract void buildAndWait(ProductSubscriptionBuilder builder);
	
	public String getExchangeName() {
		return exchange.getExchangeSpecification().getExchangeName();
	}
	
	public abstract Observable<OrderBook> getOrderBook(CurrencyPair currencyPair);
}
