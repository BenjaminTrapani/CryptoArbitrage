package BenTrapani.CryptoArbitrage;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.AccountInfo;
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
		synchronized(exchange) {
			return exchange.getExchangeMetaData().getCurrencyPairs().keySet();
		}
	}
	
	public Map<CurrencyPair, CurrencyPairMetaData> getCurrencyPairMetadata() {
		synchronized(exchange) {
			return exchange.getExchangeMetaData().getCurrencyPairs();
		}
	}
	
	public abstract void buildAndWait(ProductSubscriptionBuilder builder);
	
	public String getExchangeName() {
		synchronized(exchange) {
			return exchange.getExchangeSpecification().getExchangeName();
		}
	}
	
	public BigDecimal getBalanceForCurrencyAvailableToTrade(Currency currency) {
		AccountInfo accountInfo = null;
		try {
			synchronized(exchange) {
				accountInfo = exchange.getAccountService().getAccountInfo();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		synchronized(accountInfo) {
			return accountInfo.getWallet().getBalance(currency).getAvailable();
		}
	}
	
	public abstract Observable<OrderBook> getOrderBook(CurrencyPair currencyPair);
}
