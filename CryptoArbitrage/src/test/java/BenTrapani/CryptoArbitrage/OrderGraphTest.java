package BenTrapani.CryptoArbitrage;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.HashSet;

import org.junit.Test;
import org.knowm.xchange.currency.Currency;

import BenTrapani.CryptoArbitrage.OrderGraph.GraphEdge;
import BenTrapani.CryptoArbitrage.OrderGraph.TwoSidedGraphEdge;

public class OrderGraphTest {
	
	@Test
	public void testGraphEdge() {
		GraphEdge graphEdge = new OrderGraph.GraphEdge("poloniex", Currency.BTC, true, new BigDecimal(1.0), new BigDecimal(1000.0));
		
		HashSet<OrderGraph.GraphEdge> testEdgeSet = new HashSet<OrderGraph.GraphEdge>();
		testEdgeSet.add(graphEdge);
		assertTrue(testEdgeSet.contains(graphEdge));
		
		GraphEdge graphEdgeDup = new OrderGraph.GraphEdge("poloniex", Currency.BTC, true, new BigDecimal(1.0), new BigDecimal(1000.0));
	    assertTrue(testEdgeSet.contains(graphEdgeDup));
	    
	    GraphEdge graphEdgeNot1 = new OrderGraph.GraphEdge("poloniexx", Currency.BTC, true, new BigDecimal(1.0), new BigDecimal(1000.0));
	    GraphEdge graphEdgeNot2 = new OrderGraph.GraphEdge("poloniex", Currency.USD, true, new BigDecimal(1.0), new BigDecimal(1000.0));
	    GraphEdge graphEdgeNot3 = new OrderGraph.GraphEdge("poloniex", Currency.BTC, false, new BigDecimal(1.0), new BigDecimal(1000.0));
	    GraphEdge graphEdgeNot4 = new OrderGraph.GraphEdge("poloniex", Currency.BTC, true, new BigDecimal(2.0), new BigDecimal(1000.0));
	    GraphEdge graphEdgeNot5 = new OrderGraph.GraphEdge("poloniex", Currency.BTC, true, new BigDecimal(1.0), new BigDecimal(1001.0));
	    
	    assertFalse(testEdgeSet.contains(graphEdgeNot1));
	    assertFalse(testEdgeSet.contains(graphEdgeNot2));
	    assertFalse(testEdgeSet.contains(graphEdgeNot3));
	    assertFalse(testEdgeSet.contains(graphEdgeNot4));
	    assertFalse(testEdgeSet.contains(graphEdgeNot5));
	    
	    assertTrue(graphEdge.equals(graphEdge));
	    assertTrue(graphEdge.equals(graphEdgeDup));
	    assertFalse(graphEdge.equals(graphEdgeNot1));
	    assertFalse(graphEdge.equals(graphEdgeNot2));
	    assertFalse(graphEdge.equals(graphEdgeNot3));
	    assertFalse(graphEdge.equals(graphEdgeNot4));
	    assertFalse(graphEdge.equals(graphEdgeNot5));
	    
	    assertEquals(BigDecimal.ONE.divide(graphEdgeNot2.price).setScale(CryptoConfigs.decimalScale), 
	    		graphEdgeNot2.ratio);
	    assertEquals(graphEdgeNot3.price, graphEdgeNot3.ratio);
	}
	
	@Test
	public void testTwoSidedGraphEdge() {
		TwoSidedGraphEdge e1 = new TwoSidedGraphEdge(Currency.USD, new GraphEdge("exch1", Currency.DGC, true, 
				new BigDecimal(0.5), new BigDecimal(1.0)));
		TwoSidedGraphEdge e1Dup = new TwoSidedGraphEdge(Currency.USD, new GraphEdge("exch1", Currency.DGC, true, 
				new BigDecimal(0.5), new BigDecimal(1.0)));
		TwoSidedGraphEdge diffSource = new TwoSidedGraphEdge(Currency.EUR, new GraphEdge("exch1", Currency.DGC, true, 
				new BigDecimal(0.5), new BigDecimal(1.0)));
		TwoSidedGraphEdge diffGraphEdge = new TwoSidedGraphEdge(Currency.USD, new GraphEdge("exch2", Currency.DGC, true, 
				new BigDecimal(0.5), new BigDecimal(1.0)));
		
		assertEquals(e1, e1Dup);
		assertEquals(e1.hashCode(), e1Dup.hashCode());
		
		assertTrue(!e1.equals(diffSource));
		assertTrue(e1.hashCode() != diffSource.hashCode());
		assertTrue(!e1.equals(diffGraphEdge));
		assertTrue(e1.hashCode() != diffGraphEdge.hashCode());
	}
	
	@Test
	public void testAddAndRemoveEdge() {
		OrderGraph graph = new OrderGraph();
		graph.addEdge(Currency.USD, Currency.BTC, "poloniex", 
				true, new BigDecimal(2), new BigDecimal(1500));
		graph.addEdge(Currency.USD, Currency.BTC, "bitmex", true, new BigDecimal(4), new BigDecimal(750));
		//Duplicate edge should be ignored
		graph.addEdge(Currency.USD, Currency.BTC, "bitmex", true, new BigDecimal(4), new BigDecimal(750));
		graph.addEdge(Currency.USD, Currency.DGC, "bitmex", false, new BigDecimal(4), new BigDecimal(750));
		
		HashSet<TwoSidedGraphEdge> edges = graph.getEdges(Currency.BTC);
		assertNull(edges);
		
		edges = graph.getEdges(Currency.USD);
		assertNotNull(edges);
		assertEquals(2, edges.size());
		TwoSidedGraphEdge e1 = new TwoSidedGraphEdge(Currency.USD, new GraphEdge("poloniex", Currency.BTC, true, new BigDecimal(2), new BigDecimal(1500)));
		TwoSidedGraphEdge e2 = new TwoSidedGraphEdge(Currency.USD, new GraphEdge("bitmex", Currency.BTC, true, new BigDecimal(4), new BigDecimal(750)));
		assertTrue(edges.contains(e1));
		assertTrue(edges.contains(e2));
		
		edges = graph.getEdges(Currency.DGC);
		assertNotNull(edges);
		assertEquals(1, edges.size());
		TwoSidedGraphEdge expectedDGCEdge = new TwoSidedGraphEdge(Currency.DGC, 
				new GraphEdge("bitmex", Currency.USD, false, new BigDecimal(4), new BigDecimal(750)));
		assertTrue(edges.contains(expectedDGCEdge));
		
		assertTrue(graph.removeEdge(Currency.USD, Currency.DGC, "bitmex", false, new BigDecimal(4), new BigDecimal(750)));
		edges = graph.getEdges(Currency.DGC);
		assertNull(edges);
		assertFalse(graph.removeEdge(Currency.USD, Currency.DGC, "bitmex", false, new BigDecimal(4), new BigDecimal(750)));
		
		assertTrue(graph.removeEdge(Currency.USD, Currency.BTC, "bitmex", true, new BigDecimal(4), new BigDecimal(750)));
		edges = graph.getEdges(Currency.USD);
		assertNotNull(edges);
		assertEquals(1, edges.size());
		assertTrue(edges.contains(e1));
		assertFalse(edges.contains(e2));
	}
}
