package BenTrapani.CryptoArbitrage;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.knowm.xchange.currency.Currency;

import BenTrapani.CryptoArbitrage.OrderGraph.GraphEdge;
import BenTrapani.CryptoArbitrage.ArbitrageExecutor.ExecutableTrade;
import BenTrapani.CryptoArbitrage.ArbitrageExecutor.IntermediateTrade;

public class ArbitrageExecutorTest {
	
	@Test
	public void testRatioConcatenationAndExecutableTradeConstruction() {
		String testExch1 = "testExch1";
		String testExch2 = "testExch2";
		String testExch3 = "testExch3";
		
		BigDecimal feeFraction = new BigDecimal(0.0);
		
		// Buy BTC with USD
		GraphEdge someGraphEdge = new GraphEdge(testExch1, Currency.BTC, true, new BigDecimal(0.9), 
				new BigDecimal(8330), feeFraction);
		
		// Sell BTC for DGC
		GraphEdge secondEdge = new GraphEdge(testExch2, Currency.DGC, false, new BigDecimal(0.9),
				new BigDecimal(1021), feeFraction);
		
		// Buy LTC with DGC
		GraphEdge thirdEdge = new GraphEdge(testExch3, Currency.LTC, true, new BigDecimal(1),
				new BigDecimal(1021), feeFraction);
		
		IntermediateTrade intTrade1 = new IntermediateTrade(Currency.USD, someGraphEdge);
		assertEquals(someGraphEdge, intTrade1.graphEdge);
		assertEquals(Currency.USD, intTrade1.sourceCurrency);
		IntermediateTrade intTrade2 = new IntermediateTrade(Currency.BTC, secondEdge);
		IntermediateTrade intTrade3 = new IntermediateTrade(Currency.DGC, thirdEdge);
		
		List<IntermediateTrade> listOfTrades = new ArrayList<IntermediateTrade>(Arrays.asList(new IntermediateTrade[]{intTrade1, intTrade2, intTrade3}));
		listOfTrades = ArbitrageExecutor.populateConcatenatedRatios(listOfTrades);
		
		BigDecimal firstRatio = BigDecimal.ONE.divide(new BigDecimal(8330), CryptoConfigs.decimalScale, BigDecimal.ROUND_DOWN);
		BigDecimal secondRatio = firstRatio.multiply(new BigDecimal(1021));
		BigDecimal thirdRatio = BigDecimal.ONE.divide(new BigDecimal(1021), CryptoConfigs.decimalScale, BigDecimal.ROUND_DOWN).multiply(secondRatio);
		
		assertEquals(firstRatio, listOfTrades.get(0).concatenatedRatio);
		assertEquals(secondRatio, listOfTrades.get(1).concatenatedRatio);
		assertEquals(thirdRatio, listOfTrades.get(2).concatenatedRatio);
		
		List<ExecutableTrade> executableTrades = ArbitrageExecutor.convertToExecutableTrades(listOfTrades, new BigDecimal(104));
		assertEquals(3, executableTrades.size());
		
		assertEquals(Currency.BTC, executableTrades.get(0).base);
		assertEquals(Currency.USD, executableTrades.get(0).counter);
		assertEquals(testExch1, executableTrades.get(0).exchangeName);
		assertEquals(true, executableTrades.get(0).isBuy);
		assertEquals(new BigDecimal(8330), executableTrades.get(0).price);
		assertEquals(new BigDecimal(104).multiply(listOfTrades.get(0).concatenatedRatio), 
				executableTrades.get(0).quantity);
		
		assertEquals(Currency.BTC, executableTrades.get(1).base);
		assertEquals(Currency.DGC, executableTrades.get(1).counter);
		assertEquals(testExch2, executableTrades.get(1).exchangeName);
		assertEquals(false, executableTrades.get(1).isBuy);
		assertEquals(new BigDecimal(1021), executableTrades.get(1).price);
		assertEquals(new BigDecimal(104).multiply(listOfTrades.get(0).concatenatedRatio), executableTrades.get(1).quantity);
		
		assertEquals(Currency.LTC, executableTrades.get(2).base);
		assertEquals(Currency.DGC, executableTrades.get(2).counter);
		assertEquals(testExch3, executableTrades.get(2).exchangeName);
		assertEquals(true, executableTrades.get(2).isBuy);
		assertEquals(new BigDecimal(1021), executableTrades.get(2).price);
		assertEquals(new BigDecimal(104).multiply(listOfTrades.get(2).concatenatedRatio), executableTrades.get(2).quantity);
	}
}
