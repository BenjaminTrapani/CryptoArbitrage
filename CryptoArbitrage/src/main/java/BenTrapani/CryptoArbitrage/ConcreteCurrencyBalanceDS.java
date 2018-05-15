package BenTrapani.CryptoArbitrage;

import java.math.BigDecimal;
import java.util.Hashtable;

import org.knowm.xchange.currency.Currency;

public class ConcreteCurrencyBalanceDS implements ArbitrageExecutor.CurrencyBalanceDS {
	
	private Hashtable<String, StreamingExchangeSubset> streamingExchangeSubsetByName = new Hashtable<String, StreamingExchangeSubset>();
	
	public ConcreteCurrencyBalanceDS(StreamingExchangeSubset[] exchanges) {
		for (StreamingExchangeSubset exchange : exchanges) {
			streamingExchangeSubsetByName.put(exchange.getExchangeName(), exchange);
		}
	}
	
	@Override
	public BigDecimal getBalance(Currency currency, String exchangeName) {
		StreamingExchangeSubset keyedExchange = streamingExchangeSubsetByName.get(exchangeName);
		if (keyedExchange == null) {
			throw new IllegalStateException("Exchange not found for name " + exchangeName);
		}
		return keyedExchange.getBalanceForCurrencyAvailableToTrade(currency);
	}
}
