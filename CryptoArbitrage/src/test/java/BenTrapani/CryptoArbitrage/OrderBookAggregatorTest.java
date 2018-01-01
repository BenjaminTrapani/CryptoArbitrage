package BenTrapani.CryptoArbitrage;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;

import BenTrapani.CryptoArbitrage.OrderBookAggregator.OrderBookDiff;

public class OrderBookAggregatorTest {
	
	@Test
	public void testOneSidedOrderBookDiff() {
		Date sharedTimestamp = new Date();
		//OrderType type, BigDecimal originalAmount, CurrencyPair currencyPair, String id, Date timestamp, BigDecimal limitPrice
		LimitOrder[] prevOrders = new LimitOrder[]{new LimitOrder(OrderType.BID, new BigDecimal(0.1), CurrencyPair.BTC_USD, 
					"id1", sharedTimestamp, new BigDecimal(1500.5)), 
				new LimitOrder(OrderType.ASK, new BigDecimal(0.5), CurrencyPair.BTC_USD, 
						"id2", sharedTimestamp, new BigDecimal(1700.25))};
		LimitOrder[] newOrders = new LimitOrder[]{new LimitOrder(OrderType.ASK, new BigDecimal(0.5), CurrencyPair.BTC_USD, 
				"id2", sharedTimestamp, new BigDecimal(1700.25)), 
				new LimitOrder(OrderType.ASK, new BigDecimal(100), CurrencyPair.LTC_USD,
				"id3", sharedTimestamp, new BigDecimal(256))};
		List<LimitOrder> prevOrderList = new ArrayList<LimitOrder>(Arrays.asList(prevOrders));
		List<LimitOrder> newOrderList = new ArrayList<LimitOrder>(Arrays.asList(newOrders));
		OrderBookAggregator.OneSidedOrderBookDiff diff = new OrderBookAggregator.OneSidedOrderBookDiff(prevOrderList, newOrderList);
		List<LimitOrder> additions = diff.getAdditions();
		List<LimitOrder> deletions = diff.getDeletions();
		assertNotNull(additions);
		assertNotNull(deletions);
		assertEquals(1, additions.size());
		assertEquals(1, deletions.size());
		assertEquals(prevOrders[0], deletions.get(0));
		assertEquals(newOrders[1], additions.get(0));
		
		diff = new OrderBookAggregator.OneSidedOrderBookDiff(prevOrderList, new ArrayList<LimitOrder>());
		additions = diff.getAdditions();
		deletions = diff.getDeletions();
		assertNotNull(additions);
		assertNotNull(deletions);
		assertEquals(0, additions.size());
		assertEquals(2, deletions.size());
		
		diff = new OrderBookAggregator.OneSidedOrderBookDiff(new ArrayList<LimitOrder>(), newOrderList);
		additions = diff.getAdditions();
		deletions = diff.getDeletions();
		assertNotNull(additions);
		assertNotNull(deletions);
		assertEquals(2, additions.size());
		assertEquals(0, deletions.size());
	}
	
	@Test
	public void testOrderBookDiff() {
		Date sharedTimestamp = new Date();
		
		LimitOrder[] prevBids = new LimitOrder[]{
				new LimitOrder(OrderType.BID, new BigDecimal(0.1), CurrencyPair.BTC_USD, 
						"id1", sharedTimestamp, new BigDecimal(1500.5)), 
				new LimitOrder(OrderType.BID, new BigDecimal(30), CurrencyPair.LTC_USD, 
						"id3", sharedTimestamp, new BigDecimal(6000))
		};
		
		LimitOrder[] prevAsks = new LimitOrder[]{
				new LimitOrder(OrderType.ASK, new BigDecimal(0.5), CurrencyPair.BTC_USD, 
						"id2", sharedTimestamp, new BigDecimal(1700.25)),
				new LimitOrder(OrderType.ASK, new BigDecimal(40), CurrencyPair.LTC_USD, 
						"id4", sharedTimestamp, new BigDecimal(8000))
		};
		
		//First bid and last ask changed
		LimitOrder[] newBids = new LimitOrder[]{
				new LimitOrder(OrderType.BID, new BigDecimal(0.2), CurrencyPair.BTC_USD, 
						"id1", sharedTimestamp, new BigDecimal(1500.5)),
				new LimitOrder(OrderType.BID, new BigDecimal(30), CurrencyPair.LTC_USD, 
						"id3", sharedTimestamp, new BigDecimal(6000))
		};
		LimitOrder[] newAsks = new LimitOrder[]{
				new LimitOrder(OrderType.ASK, new BigDecimal(0.5), CurrencyPair.BTC_USD, 
						"id2", sharedTimestamp, new BigDecimal(1700.25)),
				new LimitOrder(OrderType.ASK, new BigDecimal(38), CurrencyPair.LTC_USD, 
						"id4", sharedTimestamp, new BigDecimal(8000))
		};
		
		LimitOrder[] expectedAdditions = new LimitOrder[]{
				new LimitOrder(OrderType.BID, new BigDecimal(0.2), CurrencyPair.BTC_USD, 
						"id1", sharedTimestamp, new BigDecimal(1500.5)),
				new LimitOrder(OrderType.ASK, new BigDecimal(38), CurrencyPair.LTC_USD, 
						"id4", sharedTimestamp, new BigDecimal(8000))
		};
		LimitOrder[] expectedDeletions = new LimitOrder[]{
				new LimitOrder(OrderType.BID, new BigDecimal(0.1), CurrencyPair.BTC_USD, 
						"id1", sharedTimestamp, new BigDecimal(1500.5)), 
				new LimitOrder(OrderType.ASK, new BigDecimal(40), CurrencyPair.LTC_USD, 
						"id4", sharedTimestamp, new BigDecimal(8000))
		};
		
		List<LimitOrder> expectedAdditionsList = new ArrayList<LimitOrder>(Arrays.asList(expectedAdditions));
		List<LimitOrder> expectedDeletionsList = new ArrayList<LimitOrder>(Arrays.asList(expectedDeletions));
		
		List<LimitOrder> prevBidsList = new ArrayList<LimitOrder>(Arrays.asList(prevBids));
		List<LimitOrder> prevAsksList = new ArrayList<LimitOrder>(Arrays.asList(prevAsks));
		List<LimitOrder> newBidsList = new ArrayList<LimitOrder>(Arrays.asList(newBids));
		List<LimitOrder> newAsksList = new ArrayList<LimitOrder>(Arrays.asList(newAsks));
		
		OrderBook prevOrderBook = new OrderBook(sharedTimestamp, prevBidsList, prevAsksList);
		OrderBook newOrderBook = new OrderBook(sharedTimestamp, newBidsList, newAsksList);
		
		OrderBookDiff fullOrderBookDiff = new OrderBookDiff(prevOrderBook, newOrderBook);
		List<LimitOrder> additions = fullOrderBookDiff.getAdditions();
		List<LimitOrder> deletions = fullOrderBookDiff.getDeletions();
		assertNotNull(additions);
		assertNotNull(deletions);
		assertEquals(2, additions.size());
		assertEquals(2, deletions.size());
		Set<LimitOrder> additionsSet = new HashSet<LimitOrder>(additions);
		Set<LimitOrder> deletionsSet = new HashSet<LimitOrder>(deletions);
		Set<LimitOrder> expectedAdditionSet = new HashSet<LimitOrder>(expectedAdditionsList);
		Set<LimitOrder> expectedDeletionSet = new HashSet<LimitOrder>(expectedDeletionsList);
		assertEquals(expectedAdditionSet, additionsSet);
		assertEquals(expectedDeletionSet, deletionsSet);
	}
}
