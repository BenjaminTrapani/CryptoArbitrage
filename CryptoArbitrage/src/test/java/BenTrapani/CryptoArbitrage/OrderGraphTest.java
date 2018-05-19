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
		Fraction fee = new Fraction(0);
		Fraction oneFrac = new Fraction(1);
		GraphEdge graphEdge = new OrderGraph.GraphEdge("poloniex", Currency.BTC, true, oneFrac, new Fraction(1000), fee);
		
		HashSet<OrderGraph.GraphEdge> testEdgeSet = new HashSet<OrderGraph.GraphEdge>();
		testEdgeSet.add(graphEdge);
		assertTrue(testEdgeSet.contains(graphEdge));
		
		GraphEdge graphEdgeDup = new OrderGraph.GraphEdge("poloniex", Currency.BTC, true, oneFrac, new Fraction(1000), fee);
	    assertTrue(testEdgeSet.contains(graphEdgeDup));
	    
	    GraphEdge graphEdgeNot1 = new OrderGraph.GraphEdge("poloniexx", Currency.BTC, true, oneFrac, new Fraction(1000), fee);
	    GraphEdge graphEdgeNot2 = new OrderGraph.GraphEdge("poloniex", Currency.USD, true, oneFrac, new Fraction(1000), 
	    		new Fraction(25, 10000));
	    GraphEdge graphEdgeNot3 = new OrderGraph.GraphEdge("poloniex", Currency.BTC, false, oneFrac, new Fraction(1000), 
	    		new Fraction(25, 10000));
	    GraphEdge graphEdgeNot4 = new OrderGraph.GraphEdge("poloniex", Currency.BTC, true, oneFrac.add(new Fraction(1, 2)), 
	    		new Fraction(1000), fee);
	    GraphEdge graphEdgeNot5 = new OrderGraph.GraphEdge("poloniex", Currency.BTC, true, oneFrac, new Fraction(1001), fee);
	    GraphEdge graphEdgeNot6 = new OrderGraph.GraphEdge("poloniex", Currency.BTC, true, oneFrac, new Fraction(1000), 
	    		fee.add(new Fraction(1, 10)));
	    
	    assertFalse(testEdgeSet.contains(graphEdgeNot1));
	    assertFalse(testEdgeSet.contains(graphEdgeNot2));
	    assertFalse(testEdgeSet.contains(graphEdgeNot3));
	    assertFalse(testEdgeSet.contains(graphEdgeNot4));
	    assertFalse(testEdgeSet.contains(graphEdgeNot5));
	    assertFalse(testEdgeSet.contains(graphEdgeNot6));
	    
	    assertTrue(graphEdge.equals(graphEdge));
	    assertTrue(graphEdge.equals(graphEdgeDup));
	    assertFalse(graphEdge.equals(graphEdgeNot1));
	    assertFalse(graphEdge.equals(graphEdgeNot2));
	    assertFalse(graphEdge.equals(graphEdgeNot3));
	    assertFalse(graphEdge.equals(graphEdgeNot4));
	    assertFalse(graphEdge.equals(graphEdgeNot5));
	    assertFalse(graphEdge.equals(graphEdgeNot6));
	    
	    //0.0009975
	    assertEquals(new Fraction(9975, 10000000), graphEdgeNot2.ratio);
	    // 997.5
	    assertEquals(new Fraction(9975, 10), graphEdgeNot3.ratio);
	}
	
	@Test
	public void testTwoSidedGraphEdge() {
		Fraction oneFrac = new Fraction(1);
		Fraction feeFrac = new Fraction(25, 10000);
		Fraction oneHalf = new Fraction(1, 2);
		TwoSidedGraphEdge e1 = new TwoSidedGraphEdge(Currency.USD, new GraphEdge("exch1", Currency.DGC, true, 
				oneHalf, oneFrac, feeFrac));
		TwoSidedGraphEdge e1Dup = new TwoSidedGraphEdge(Currency.USD, new GraphEdge("exch1", Currency.DGC, true, 
				oneHalf, oneFrac, feeFrac));
		TwoSidedGraphEdge diffSource = new TwoSidedGraphEdge(Currency.EUR, new GraphEdge("exch1", Currency.DGC, true, 
				oneHalf, oneFrac, feeFrac));
		TwoSidedGraphEdge diffGraphEdge = new TwoSidedGraphEdge(Currency.USD, new GraphEdge("exch2", Currency.DGC, true, 
				oneHalf, oneFrac, feeFrac));
		
		assertEquals(e1, e1Dup);
		assertEquals(e1.hashCode(), e1Dup.hashCode());
		
		assertTrue(!e1.equals(diffSource));
		assertTrue(e1.hashCode() != diffSource.hashCode());
		assertTrue(!e1.equals(diffGraphEdge));
		assertTrue(e1.hashCode() != diffGraphEdge.hashCode());
	}
	
	@Test
	public void testAddAndRemoveEdge() {
		Fraction fee = new Fraction(0);
		
		OrderGraph graph = new OrderGraph();
		graph.addEdge(Currency.USD, Currency.BTC, "poloniex", 
				true, new Fraction(2), new Fraction(1500), fee);
		graph.addEdge(Currency.USD, Currency.BTC, "bitmex", true, new Fraction(4), new Fraction(750), 
				fee);
		//Duplicate edge should be ignored
		graph.addEdge(Currency.USD, Currency.BTC, "bitmex", true, new Fraction(4), new Fraction(750),
				fee);
		graph.addEdge(Currency.USD, Currency.DGC, "bitmex", false, new Fraction(4), new Fraction(750),
				fee);
		
		HashSet<TwoSidedGraphEdge> edges = graph.getEdges(Currency.BTC);
		assertNull(edges);
		
		edges = graph.getEdges(Currency.USD);
		assertNotNull(edges);
		assertEquals(2, edges.size());
		TwoSidedGraphEdge e1 = new TwoSidedGraphEdge(Currency.USD, new GraphEdge("poloniex", Currency.BTC, true, new Fraction(2), 
				new Fraction(1500), fee));
		TwoSidedGraphEdge e2 = new TwoSidedGraphEdge(Currency.USD, new GraphEdge("bitmex", Currency.BTC, true, new Fraction(4), 
				new Fraction(750), fee));
		assertTrue(edges.contains(e1));
		assertTrue(edges.contains(e2));
		
		edges = graph.getEdges(Currency.DGC);
		assertNotNull(edges);
		assertEquals(1, edges.size());
		TwoSidedGraphEdge expectedDGCEdge = new TwoSidedGraphEdge(Currency.DGC, 
				new GraphEdge("bitmex", Currency.USD, false, new Fraction(4), 
						new Fraction(750), fee));
		assertTrue(edges.contains(expectedDGCEdge));
		
		assertTrue(graph.removeEdge(Currency.USD, Currency.DGC, "bitmex", false, 
				new Fraction(4), new Fraction(750), fee));
		edges = graph.getEdges(Currency.DGC);
		assertNull(edges);
		assertFalse(graph.removeEdge(Currency.USD, Currency.DGC, "bitmex", false, 
				new Fraction(4), new Fraction(750), fee));
		
		assertTrue(graph.removeEdge(Currency.USD, Currency.BTC, "bitmex", true, 
				new Fraction(4), new Fraction(750), fee));
		edges = graph.getEdges(Currency.USD);
		assertNotNull(edges);
		assertEquals(1, edges.size());
		assertTrue(edges.contains(e1));
		assertFalse(edges.contains(e2));
	}
}
