package BenTrapani.CryptoArbitrage;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;

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
	
	public Map<CurrencyPair, Fee> getDynamicTradingFees() {
		synchronized(exchange) {
			try {
				return exchange.getAccountService().getDynamicTradingFees();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return null;
	}
	
	Optional<String> placeLimitOrder(CurrencyPair pair, boolean isBuy, Fraction price, Fraction quantity, String id) {
		final CurrencyPairMetaData meta = exchange.getExchangeMetaData().getCurrencyPairs().get(pair);
		final Integer priceScale = meta.getPriceScale();
		final Integer quantityScale = meta.getBaseScale();
		final int roundingMode = isBuy ? BigDecimal.ROUND_DOWN : BigDecimal.ROUND_UP;
		try {
			return Optional.of(exchange.getTradeService().placeLimitOrder(new LimitOrder(isBuy ? OrderType.BID : OrderType.ASK,
					quantity.convertToBigDecimal(quantityScale, BigDecimal.ROUND_DOWN), 
					pair, id, null, price.convertToBigDecimal(priceScale, roundingMode))));
		} catch (IOException e) {
			System.out.println("Error placing order: " + e.toString());
			return Optional.empty();
		}
	}
	
	boolean cancelIfNotFilled(String exchangeOrderID) throws IOException {
		return !exchange.getTradeService().cancelOrder(exchangeOrderID);
	}
	
	public abstract Observable<OrderBook> getOrderBook(CurrencyPair currencyPair);
}
