package BenTrapani.CryptoArbitrage;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.LimitOrder;

import BenTrapani.CryptoArbitrage.OrderBookAggregator.OrderBookDiff;
import BenTrapani.CryptoArbitrage.OrderBookAggregator.KBestOrders;

public class OrderBookAggregatorTest {
	@Test
	public void testKBestOrders() {
		Date sharedTimestamp = new Date();
		List<LimitOrder> unorderedAsks = new ArrayList<LimitOrder>(Arrays.asList(new LimitOrder[]{
				new LimitOrder(OrderType.ASK, new BigDecimal(2.0), CurrencyPair.BTC_USD, "id1", sharedTimestamp, new BigDecimal(2000.0)),
				new LimitOrder(OrderType.ASK, new BigDecimal(1.0), CurrencyPair.BTC_USD, "id2", sharedTimestamp, new BigDecimal(2100.0)),
				new LimitOrder(OrderType.ASK, new BigDecimal(4.0), CurrencyPair.BTC_USD, "id3", sharedTimestamp, new BigDecimal(1900.0)),
				new LimitOrder(OrderType.ASK, new BigDecimal(8.0), CurrencyPair.BTC_USD, "id4", sharedTimestamp, new BigDecimal(1950.0)),
		}));
		List<LimitOrder> unorderedBids = new ArrayList<LimitOrder>(Arrays.asList(new LimitOrder[]{
				new LimitOrder(OrderType.BID, new BigDecimal(3.0), CurrencyPair.BTC_USD, "id5", sharedTimestamp, new BigDecimal(2000.0)),
				new LimitOrder(OrderType.BID, new BigDecimal(7.0), CurrencyPair.BTC_USD, "id6", sharedTimestamp, new BigDecimal(2100.0)),
				new LimitOrder(OrderType.BID, new BigDecimal(11.0), CurrencyPair.BTC_USD, "id7", sharedTimestamp, new BigDecimal(1900.0)),
				new LimitOrder(OrderType.BID, new BigDecimal(9.0), CurrencyPair.BTC_USD, "id8", sharedTimestamp, new BigDecimal(1950.0)),
		}));
		List<LimitOrder> expectedBestAsks = new ArrayList<LimitOrder>(Arrays.asList(new LimitOrder[]{
				unorderedAsks.get(2),
				unorderedAsks.get(3)
		}));
		List<LimitOrder> expectedBestBids = new ArrayList<LimitOrder>(Arrays.asList(new LimitOrder[]{
				unorderedBids.get(1),
				unorderedBids.get(0)
		}));
		
		KBestOrders bestTwo = new KBestOrders(new ArrayList<LimitOrder>(unorderedBids), 
				new ArrayList<LimitOrder>(unorderedAsks), 2, 2);
		assertEquals(expectedBestAsks, bestTwo.kBestAsks);
		assertEquals(expectedBestBids, bestTwo.kBestBids);
		
		KBestOrders twoBestAskOneBestBid = new KBestOrders(new ArrayList<LimitOrder>(unorderedBids),
				new ArrayList<LimitOrder>(unorderedAsks), 1, 2);
		assertEquals(expectedBestAsks, twoBestAskOneBestBid.kBestAsks);
		assertEquals(expectedBestBids.subList(0, 1), twoBestAskOneBestBid.kBestBids);
		
		KBestOrders oneBestAskTwoBestBids = new KBestOrders(new ArrayList<LimitOrder>(unorderedBids),
				new ArrayList<LimitOrder>(unorderedAsks), 2, 1);
		assertEquals(expectedBestAsks.subList(0, 1), oneBestAskTwoBestBids.kBestAsks);
		assertEquals(expectedBestBids, oneBestAskTwoBestBids.kBestBids);
		
		List<LimitOrder> sortedBids = new ArrayList<LimitOrder>(unorderedBids);
		List<LimitOrder> sortedAsks = new ArrayList<LimitOrder>(unorderedAsks);
		Collections.sort(sortedBids);
		Collections.sort(sortedAsks);
		KBestOrders allOrders = new KBestOrders(unorderedBids, unorderedAsks, 10, 10);
		assertEquals(sortedBids, allOrders.kBestBids);
		assertEquals(sortedAsks, allOrders.kBestAsks);
		// Make sure that input lists to KBestOrders are sorted in place
		assertEquals(sortedBids, unorderedBids);
		assertEquals(sortedAsks, unorderedAsks);
	}
	
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
		
		KBestOrders prevOrderBook = new KBestOrders(prevBidsList, prevAsksList, 2, 2);
		KBestOrders newOrderBook = new KBestOrders(newBidsList, newAsksList, 2, 2);
		
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
