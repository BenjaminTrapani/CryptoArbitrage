package BenTrapani.CryptoArbitrage;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.HashSet;

import BenTrapani.CryptoArbitrage.OrderBookAnalyzer.SearchState;
import BenTrapani.CryptoArbitrage.OrderGraph.GraphEdge;
import BenTrapani.CryptoArbitrage.OrderBookAnalyzer;

import org.junit.Test;
import org.knowm.xchange.currency.Currency;

public class OrderBookAnalyzerTest {

	@Test
	public void testSearchState() {
		SearchState parent = new SearchState(Currency.USD, null, true, new BigDecimal(1.0), new HashSet<GraphEdge>());
		SearchState parentDup = new SearchState(Currency.USD, null, true, new BigDecimal(1.0), new HashSet<GraphEdge>());
		
		SearchState s1 = new SearchState(Currency.BTC, parent, false, new BigDecimal(0.8), new HashSet<GraphEdge>());
		SearchState s2 = new SearchState(Currency.LTC, parent, true, new BigDecimal(0.6), new HashSet<GraphEdge>());
		SearchState s2Dup = new SearchState(Currency.LTC, parent, true, new BigDecimal(0.6), new HashSet<GraphEdge>());
		SearchState s2DiffParent = new SearchState(Currency.LTC, parentDup, true, new BigDecimal(0.6), new HashSet<GraphEdge>());
		SearchState s2DiffCurrency = new SearchState(Currency.AFN, parent, true, new BigDecimal(0.6), new HashSet<GraphEdge>());
		SearchState s2DiffLastChild = new SearchState(Currency.AFN, parent, false, new BigDecimal(0.6), new HashSet<GraphEdge>());
		SearchState s2DiffRatio = new SearchState(Currency.AFN, parent, true, new BigDecimal(0.7), new HashSet<GraphEdge>());
		
		assertEquals(s2, s2Dup);
		assertEquals(s2.hashCode(), s2Dup.hashCode());
		assertFalse(s2.equals(s1));
		assertFalse(s2.hashCode() == s1.hashCode());
		assertEquals(s2DiffParent, s2);
		assertEquals(s2DiffParent.hashCode(), s2.hashCode());
		assertFalse(s2DiffCurrency.equals(s2));
		assertFalse(s2DiffCurrency.hashCode() == s2.hashCode());
		assertFalse(s2DiffLastChild.equals(s2));
		assertFalse(s2DiffLastChild.hashCode() == s2.hashCode());
		assertFalse(s2DiffRatio.equals(s2));
		assertFalse(s2DiffRatio.hashCode() == s2.hashCode());
	}
	
	private static boolean bigDecimalsEqualWithTolerance(BigDecimal a, BigDecimal b, BigDecimal tolerance) {
		return a.subtract(b).abs().compareTo(tolerance) < 0;
	}
	
	//Basic test structure to make sure max works when cache is never reused
	private OrderGraph buildTestOrderGraph1() {
		OrderGraph orderGraph = new OrderGraph();
		/*
		 * USD ---0.001---->   BTC
		 * \  <---1000----- /> 
		 *  \			   /
		 * 	 \			  0.05
		 * 	 0.1		 /
		 * 		\> DGC  /
		 */
		orderGraph.addEdge(Currency.USD, Currency.DGC, "poloniex", true, new BigDecimal(0.1), new BigDecimal(1.0));
		orderGraph.addEdge(Currency.DGC, Currency.BTC, "gdax", true, new BigDecimal(0.05), new BigDecimal(1.0));
		orderGraph.addEdge(Currency.BTC, Currency.USD, "coinbase", true, new BigDecimal(1000), new BigDecimal(1.0));
		orderGraph.addEdge(Currency.USD, Currency.BTC, "coinbase", true, new BigDecimal(0.001), new BigDecimal(1.0));
		return orderGraph;
	}
	
	// Tests cached partial solutions (ETH)
	private OrderGraph buildTestOrderGraph2() {
		OrderGraph orderGraph = new OrderGraph();
		/*
		 *     > LTC \         > BTC 
		 *    /       \      /      \
		 *   0.01      0.5  2        100
		 *  /           \> /          \>
		 * USD<---1----ETH < \----1----XRP
		 *   \         /> \   \          />
		 *    0.5    0.7   100 0.03     4
		 *     \>    /      \    \     /
		 *      DGC /          > XPM /
		 */
		orderGraph.addEdge(Currency.USD, Currency.DGC, "poloniex", true, new BigDecimal(0.1), new BigDecimal(1.0));
		orderGraph.addEdge(Currency.DGC, Currency.BTC, "gdax", true, new BigDecimal(0.05), new BigDecimal(1.0));
		orderGraph.addEdge(Currency.BTC, Currency.USD, "coinbase", true, new BigDecimal(1000), new BigDecimal(1.0));
		orderGraph.addEdge(Currency.USD, Currency.BTC, "coinbase", true, new BigDecimal(0.001), new BigDecimal(1.0));
		return orderGraph;
	}
	
	@Test
	public void testSearchForArbitrage() {
		OrderGraph sharedOrderGraph = buildTestOrderGraph1();
		OrderBookAnalyzer analyzer = new OrderBookAnalyzer(sharedOrderGraph, Currency.USD);
		BigDecimal maxRatio = analyzer.searchForArbitrage();
		assertTrue(bigDecimalsEqualWithTolerance(new BigDecimal(0.1 * 0.05 * 1000), 
				maxRatio, 
				new BigDecimal(0.01)));
	}
}
