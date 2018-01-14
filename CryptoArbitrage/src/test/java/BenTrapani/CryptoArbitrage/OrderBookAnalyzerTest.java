package BenTrapani.CryptoArbitrage;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;

import BenTrapani.CryptoArbitrage.OrderBookAnalyzer;
import BenTrapani.CryptoArbitrage.OrderBookAnalyzer.AnalysisResult;
import BenTrapani.CryptoArbitrage.OrderGraph.TwoSidedGraphEdge;
import BenTrapani.CryptoArbitrage.OrderGraph.GraphEdge;

import org.junit.Test;
import org.knowm.xchange.currency.Currency;

public class OrderBookAnalyzerTest {
	
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
		orderGraph.addEdge(Currency.BTC, Currency.USD, "coinbase", true, new BigDecimal(1000.0), new BigDecimal(1.0));
		orderGraph.addEdge(Currency.USD, Currency.BTC, "coinbase", true, new BigDecimal(0.001), new BigDecimal(1.0));
		return orderGraph;
	}
	
	// Tests cached partial solutions if implemented (ETH) and multiple equivalence classes otherwise
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
		// Max loop to USD: USD -> DGC -> ETH -> BTC -> XRP -> ETH -> XPM -> ETH -> USD
		orderGraph.addEdge(Currency.USD, Currency.LTC, "testExch", true, new BigDecimal(0.01), new BigDecimal(1.0));
		orderGraph.addEdge(Currency.USD, Currency.DGC, "testExch", true, new BigDecimal(0.5), new BigDecimal(1.0));
		orderGraph.addEdge(Currency.LTC, Currency.ETH, "testExch", true, new BigDecimal(0.5), new BigDecimal(1.0));
		orderGraph.addEdge(Currency.DGC, Currency.ETH, "testExch", true, new BigDecimal(0.7), new BigDecimal(1.0));
		orderGraph.addEdge(Currency.ETH, Currency.USD, "testExch", true, new BigDecimal(1.0), new BigDecimal(1.0));
		orderGraph.addEdge(Currency.ETH, Currency.BTC, "testExch", true, new BigDecimal(2.0), new BigDecimal(1.0));
		orderGraph.addEdge(Currency.ETH, Currency.XPM, "testExch", true, new BigDecimal(100.0), new BigDecimal(1.0));
		orderGraph.addEdge(Currency.XPM, Currency.ETH, "testExch", true, new BigDecimal(0.03), new BigDecimal(1.0));
		orderGraph.addEdge(Currency.XPM, Currency.XRP, "testExch", true, new BigDecimal(4.0), new BigDecimal(1.0));
		orderGraph.addEdge(Currency.BTC, Currency.XRP, "testExch", true, new BigDecimal(100.0), new BigDecimal(1.0));
		orderGraph.addEdge(Currency.XRP, Currency.ETH, "testExch", true, new BigDecimal(1.0), new BigDecimal(1.0));
		return orderGraph;
	}
	
	@Test
	public void testSearchForArbitrageSimple() {
		OrderGraph sharedOrderGraph = buildTestOrderGraph1();
		OrderBookAnalyzer analyzer = new OrderBookAnalyzer(sharedOrderGraph, Currency.USD);
		AnalysisResult analysisResult = analyzer.searchForArbitrage();
		assertTrue(bigDecimalsEqualWithTolerance(new BigDecimal(0.1 * 0.05 * 1000), 
				analysisResult.maxRatio, 
				new BigDecimal(0.0001)));
		TwoSidedGraphEdge e1 = new TwoSidedGraphEdge(Currency.USD, new GraphEdge("poloniex", Currency.DGC, true, new BigDecimal(0.1), new BigDecimal(1.0)));
		TwoSidedGraphEdge e2 = new TwoSidedGraphEdge(Currency.DGC, new GraphEdge("gdax", Currency.BTC, true, new BigDecimal(0.05), new BigDecimal(1.0)));
		TwoSidedGraphEdge e3 = new TwoSidedGraphEdge(Currency.BTC, new GraphEdge("coinbase", Currency.USD, true, new BigDecimal(1000.0), new BigDecimal(1.0)));
		HashSet<TwoSidedGraphEdge> expectedTradesOnBestPath = new HashSet<TwoSidedGraphEdge>(Arrays.asList(new TwoSidedGraphEdge[]{e1, e2, e3}));
		assertEquals(expectedTradesOnBestPath, analysisResult.tradesToExecute);
	}
	
	@Test
	public void testSearchForArbitrageMultiEquivalenceClassesPerCurrency(){
		OrderGraph sharedOrderGraph = buildTestOrderGraph2();
		OrderBookAnalyzer analyzer = new OrderBookAnalyzer(sharedOrderGraph, Currency.USD);
		AnalysisResult analysisResult = analyzer.searchForArbitrage();
		BigDecimal expectedMaxRatio = new BigDecimal(0.5 * 0.7 * 2 * 100 * 1 * 100 * 0.03);
		assertTrue(bigDecimalsEqualWithTolerance(expectedMaxRatio, analysisResult.maxRatio, new BigDecimal(0.0001)));
		// Resulting trades should be as follows
		String testExch = "testExch";
		TwoSidedGraphEdge e1 = new TwoSidedGraphEdge(Currency.USD, new GraphEdge(testExch, Currency.DGC, true, 
				new BigDecimal(0.5), new BigDecimal(1.0)));
		TwoSidedGraphEdge e2 = new TwoSidedGraphEdge(Currency.DGC, new GraphEdge(testExch, Currency.ETH, true, 
				new BigDecimal(0.7), new BigDecimal(1.0)));
		TwoSidedGraphEdge e3 = new TwoSidedGraphEdge(Currency.ETH, new GraphEdge(testExch, Currency.BTC, true, 
				new BigDecimal(2.0), new BigDecimal(1.0)));
		TwoSidedGraphEdge e4 = new TwoSidedGraphEdge(Currency.BTC, new GraphEdge(testExch, Currency.XRP, true, 
				new BigDecimal(100.0), new BigDecimal(1.0)));
		TwoSidedGraphEdge e5 = new TwoSidedGraphEdge(Currency.XRP, new GraphEdge(testExch, Currency.ETH, true, 
				new BigDecimal(1.0), new BigDecimal(1.0)));
		TwoSidedGraphEdge e6 = new TwoSidedGraphEdge(Currency.ETH, new GraphEdge(testExch, Currency.XPM, true, 
				new BigDecimal(100.0), new BigDecimal(1.0)));
		TwoSidedGraphEdge e7 = new TwoSidedGraphEdge(Currency.XPM, new GraphEdge(testExch, Currency.ETH, true, 
				new BigDecimal(0.03), new BigDecimal(1.0)));
		TwoSidedGraphEdge e8 = new TwoSidedGraphEdge(Currency.ETH, new GraphEdge(testExch, Currency.USD, true, 
				new BigDecimal(1.0), new BigDecimal(1.0)));
		HashSet<TwoSidedGraphEdge> expectedTradesOnBestPath = new HashSet<TwoSidedGraphEdge>(Arrays.asList(new TwoSidedGraphEdge[]{e1, e2, e3, e4, e5, e6, e7, e8}));
		assertEquals(expectedTradesOnBestPath, analysisResult.tradesToExecute);
	}
}
