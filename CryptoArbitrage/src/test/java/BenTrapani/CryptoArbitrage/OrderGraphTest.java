package BenTrapani.CryptoArbitrage;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.Hashtable;

import org.junit.Test;
import org.knowm.xchange.currency.Currency;

import BenTrapani.CryptoArbitrage.OrderGraph.GraphEdgeKey;
import BenTrapani.CryptoArbitrage.OrderGraph.GraphEdgeValue;

public class OrderGraphTest {
	
	@Test
	public void testGraphEdgeKey() {
		OrderGraph.GraphEdgeKey graphEdgeKey = new OrderGraph.GraphEdgeKey("poloniex", Currency.BTC, true);
		OrderGraph.GraphEdgeValue value1 = new OrderGraph.GraphEdgeValue(new BigDecimal(1), new BigDecimal(1000));
		
		Hashtable<OrderGraph.GraphEdgeKey, OrderGraph.GraphEdgeValue> testTable = new Hashtable<OrderGraph.GraphEdgeKey, OrderGraph.GraphEdgeValue>();
		testTable.put(graphEdgeKey, value1);
	    
		assertTrue(testTable.containsKey(graphEdgeKey));
		OrderGraph.GraphEdgeKey graphEdgeKeyDup = new OrderGraph.GraphEdgeKey("poloniex", Currency.BTC, true);
	    assertTrue(testTable.containsKey(graphEdgeKeyDup));
	    
	    OrderGraph.GraphEdgeKey graphEdgeKeyNot1 = new OrderGraph.GraphEdgeKey("polonie", Currency.BTC, true);
	    assertFalse(testTable.containsKey(graphEdgeKeyNot1));
	    OrderGraph.GraphEdgeKey graphEdgeKeyNot2 = new OrderGraph.GraphEdgeKey("poloniex", Currency.USD, true);
	    assertFalse(testTable.containsKey(graphEdgeKeyNot2));
	    OrderGraph.GraphEdgeKey graphEdgeKeyNot3 = new OrderGraph.GraphEdgeKey("poloniex", Currency.BTC, false);
	    assertFalse(testTable.containsKey(graphEdgeKeyNot3));
	    
	    assertTrue(graphEdgeKey.equals(graphEdgeKey));
	    assertTrue(graphEdgeKey.equals(graphEdgeKeyDup));
	    assertFalse(graphEdgeKey.equals(graphEdgeKeyNot1));
	    assertFalse(graphEdgeKey.equals(graphEdgeKeyNot2));
	    assertFalse(graphEdgeKey.equals(graphEdgeKeyNot3));
	}
	
	@Test
	public void testAddOrUpdateEdge() {
		OrderGraph graph = new OrderGraph();
		graph.addOrUpdateEdge(Currency.USD, Currency.BTC, "poloniex", 
				true, new BigDecimal(2), new BigDecimal(1500));
		graph.addOrUpdateEdge(Currency.USD, Currency.LTC, "bitmex", false, new BigDecimal(4), new BigDecimal(750));
		
		Hashtable<GraphEdgeKey, GraphEdgeValue> edges = graph.getEdges(Currency.BTC);
		assertNull(edges);
		edges = graph.getEdges(Currency.LTC);
		assertNull(edges);
		
		edges = graph.getEdges(Currency.USD);
		assertNotNull(edges);
		assertEquals(2, edges.size());
		GraphEdgeKey k1 = new GraphEdgeKey("poloniex", Currency.BTC, true);
		GraphEdgeKey k2 = new GraphEdgeKey("bitmex", Currency.LTC, false);
		GraphEdgeValue v1 = new GraphEdgeValue(new BigDecimal(2), new BigDecimal(1500));
		GraphEdgeValue v2 = new GraphEdgeValue(new BigDecimal(4), new BigDecimal(750));
		GraphEdgeValue realV1 = edges.get(k1);
		GraphEdgeValue realV2 = edges.get(k2);
		assertEquals(v1, realV1);
		assertEquals(v2, realV2);
	}
}
