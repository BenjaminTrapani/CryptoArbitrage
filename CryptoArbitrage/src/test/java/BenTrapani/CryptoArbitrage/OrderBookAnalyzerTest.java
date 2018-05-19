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
		Fraction fee = new Fraction(0);
		Fraction oneFrac = new Fraction(1);
		orderGraph.addEdge(Currency.USD, Currency.DGC, "poloniex", true, oneFrac, new Fraction(10), fee);
		orderGraph.addEdge(Currency.DGC, Currency.BTC, "gdax", true, oneFrac, new Fraction(100, 5), fee);
		orderGraph.addEdge(Currency.BTC, Currency.USD, "coinbase", true, oneFrac, new Fraction(1, 1000), fee);
		orderGraph.addEdge(Currency.USD, Currency.BTC, "coinbase", true, oneFrac, new Fraction(1000), fee);
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
		Fraction fee = new Fraction(0);
		Fraction oneFrac = new Fraction(1);
		orderGraph.addEdge(Currency.USD, Currency.LTC, "testExch", true, oneFrac, new Fraction(100), fee);
		orderGraph.addEdge(Currency.USD, Currency.DGC, "testExch", true, oneFrac, new Fraction(2), fee);
		orderGraph.addEdge(Currency.LTC, Currency.ETH, "testExch", true, oneFrac, new Fraction(2), fee);
		orderGraph.addEdge(Currency.DGC, Currency.ETH, "testExch", true, oneFrac, new Fraction(10, 7), fee);
		orderGraph.addEdge(Currency.ETH, Currency.USD, "testExch", true, oneFrac, new Fraction(1), fee);
		orderGraph.addEdge(Currency.ETH, Currency.BTC, "testExch", true, oneFrac, new Fraction(1, 2), fee);
		orderGraph.addEdge(Currency.ETH, Currency.XPM, "testExch", true, oneFrac, new Fraction(1, 100), fee);
		orderGraph.addEdge(Currency.XPM, Currency.ETH, "testExch", true, oneFrac, new Fraction(100, 3), fee);
		orderGraph.addEdge(Currency.XPM, Currency.XRP, "testExch", true, oneFrac, new Fraction(1, 4), fee);
		orderGraph.addEdge(Currency.BTC, Currency.XRP, "testExch", true, oneFrac, new Fraction(1, 100), fee);
		orderGraph.addEdge(Currency.XRP, Currency.ETH, "testExch", true, oneFrac, new Fraction(1), fee);
		return orderGraph;
	}
	
	private OrderGraph buildLeafyTestGraph() {
		/*
		 * 
		 *     />DGC-----0.01\
		 *    /				 |
		 *   0.5	          |		
		 *  /                  |
		 * USD                  |
		 *  \                    |
		 *   0.001               >
		 *     \>BTC----20----->ETH--50-->XPM 
		 */
		OrderGraph orderGraph = new OrderGraph();
		Fraction fee = new Fraction(0);
		Fraction oneFrac = new Fraction(1);
		orderGraph.addEdge(Currency.USD, Currency.DGC, "testExch", true, oneFrac, new Fraction(10, 5), fee);
		orderGraph.addEdge(Currency.USD, Currency.BTC, "testExch", true, oneFrac, new Fraction(1000), fee);
		orderGraph.addEdge(Currency.DGC, Currency.ETH, "testExch", true, oneFrac, new Fraction(100), fee);
		orderGraph.addEdge(Currency.BTC, Currency.ETH, "testExch", true, oneFrac, new Fraction(1, 20), fee);
		orderGraph.addEdge(Currency.ETH, Currency.XPM, "testExch", true, oneFrac, new Fraction(1, 50), fee);
		return orderGraph;
	}
	
	private class MockAnalysisHandler implements OrderGraphAnalysisHandler {
		@Override
		public void onOrderBookAnalysisComplete(AnalysisResult analysisResult) {
		}
	}
	
	@Test
	public void testSearchForArbitrageSimple() {
		OrderGraph sharedOrderGraph = buildTestOrderGraph1();
		OrderBookAnalyzer analyzer = new OrderBookAnalyzer(sharedOrderGraph, Currency.USD, 100, new MockAnalysisHandler());
		AnalysisResult analysisResult = analyzer.searchForArbitrage();
		assertEquals(new Fraction(1000).multiply(new Fraction(1, 10)).multiply(new Fraction(1, 20)),
				analysisResult.maxRatio);
		Fraction fee = new Fraction(0);
		Fraction oneFrac = new Fraction(1);
		TwoSidedGraphEdge e1 = new TwoSidedGraphEdge(Currency.USD, new GraphEdge("poloniex", Currency.DGC, true, oneFrac, new Fraction(10), fee));
		TwoSidedGraphEdge e2 = new TwoSidedGraphEdge(Currency.DGC, new GraphEdge("gdax", Currency.BTC, true, oneFrac, new Fraction(100, 5), fee));
		TwoSidedGraphEdge e3 = new TwoSidedGraphEdge(Currency.BTC, new GraphEdge("coinbase", Currency.USD, true, oneFrac, new Fraction(1, 1000), fee));
		HashSet<TwoSidedGraphEdge> expectedTradesOnBestPath = new HashSet<TwoSidedGraphEdge>(Arrays.asList(new TwoSidedGraphEdge[]{e1, e2, e3}));
		assertEquals(expectedTradesOnBestPath, analysisResult.tradesToExecute);
		
		OrderBookAnalyzer shortPathAnalyzer = new OrderBookAnalyzer(sharedOrderGraph, Currency.USD, 2, new MockAnalysisHandler());
		analysisResult = shortPathAnalyzer.searchForArbitrage();
		assertEquals(new Fraction(1), analysisResult.maxRatio);
		
		TwoSidedGraphEdge e4 = new TwoSidedGraphEdge(Currency.USD, new GraphEdge("coinbase", Currency.BTC, true, oneFrac, new Fraction(1000), fee));
		expectedTradesOnBestPath = new HashSet<TwoSidedGraphEdge>(Arrays.asList(new TwoSidedGraphEdge[]{e3, e4}));
		assertEquals(expectedTradesOnBestPath, analysisResult.tradesToExecute);
	}
	
	@Test
	public void testSearchForArbitrageMultiEquivalenceClassesPerCurrency(){
		OrderGraph sharedOrderGraph = buildTestOrderGraph2();
		OrderBookAnalyzer analyzer = new OrderBookAnalyzer(sharedOrderGraph, Currency.USD, 100, new MockAnalysisHandler());
		AnalysisResult analysisResult = analyzer.searchForArbitrage();
		Fraction expectedMaxRatio = new Fraction(1, 2).multiply(new Fraction(7, 10)).multiply(new Fraction(2))
				.multiply(new Fraction(100)).multiply(new Fraction(1)).multiply(new Fraction(100)).multiply(new Fraction(3, 100));
		assertEquals(expectedMaxRatio, analysisResult.maxRatio);
		// Resulting trades should be as follows
		String testExch = "testExch";
		Fraction fee = new Fraction(0);
		Fraction oneFrac = new Fraction(1);
		TwoSidedGraphEdge e1 = new TwoSidedGraphEdge(Currency.USD, new GraphEdge(testExch, Currency.DGC, true, 
				oneFrac, new Fraction(10, 5), fee));
		TwoSidedGraphEdge e2 = new TwoSidedGraphEdge(Currency.DGC, new GraphEdge(testExch, Currency.ETH, true, 
				oneFrac, new Fraction(10, 7), fee));
		TwoSidedGraphEdge e3 = new TwoSidedGraphEdge(Currency.ETH, new GraphEdge(testExch, Currency.BTC, true, 
				oneFrac, new Fraction(1, 2), fee));
		TwoSidedGraphEdge e4 = new TwoSidedGraphEdge(Currency.BTC, new GraphEdge(testExch, Currency.XRP, true, 
				oneFrac, new Fraction(1, 100), fee));
		TwoSidedGraphEdge e5 = new TwoSidedGraphEdge(Currency.XRP, new GraphEdge(testExch, Currency.ETH, true, 
				oneFrac, new Fraction(1), fee));
		TwoSidedGraphEdge e6 = new TwoSidedGraphEdge(Currency.ETH, new GraphEdge(testExch, Currency.XPM, true, 
				oneFrac, new Fraction(1, 100), fee));
		TwoSidedGraphEdge e7 = new TwoSidedGraphEdge(Currency.XPM, new GraphEdge(testExch, Currency.ETH, true, 
				oneFrac, new Fraction(100, 3), fee));
		TwoSidedGraphEdge e8 = new TwoSidedGraphEdge(Currency.ETH, new GraphEdge(testExch, Currency.USD, true, 
				oneFrac, new Fraction(1), fee));
		HashSet<TwoSidedGraphEdge> expectedTradesOnBestPath = new HashSet<TwoSidedGraphEdge>(Arrays.asList(new TwoSidedGraphEdge[]{e1, e2, e3, e4, e5, e6, e7, e8}));
		assertEquals(expectedTradesOnBestPath, analysisResult.tradesToExecute);
	}
	
	@Test
	public void testNoLoopSearch() {
		Fraction zeroFrac = new Fraction(0);
		OrderGraph sharedOrderGraph = buildLeafyTestGraph();
		OrderBookAnalyzer analyzer = new OrderBookAnalyzer(sharedOrderGraph, Currency.USD, 100, new MockAnalysisHandler());
		AnalysisResult analysisResult = analyzer.searchForArbitrage();
		assertTrue(analysisResult.maxRatio.compareTo(zeroFrac) < 0);
		assertNull(analysisResult.tradesToExecute);
		
		OrderBookAnalyzer noInitialCurrencyAnalyzer = new OrderBookAnalyzer(sharedOrderGraph, Currency.EUR, 100, new MockAnalysisHandler());
		analysisResult = noInitialCurrencyAnalyzer.searchForArbitrage();
		assertTrue(analysisResult.maxRatio.compareTo(zeroFrac) < 0);
		assertNull(analysisResult.tradesToExecute);
	}
}
