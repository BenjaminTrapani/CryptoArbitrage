package BenTrapani.CryptoArbitrage;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;

import org.junit.Test;
import org.knowm.xchange.currency.Currency;

import BenTrapani.CryptoArbitrage.OrderGraph.GraphEdge;
import BenTrapani.CryptoArbitrage.OrderGraph.TwoSidedGraphEdge;
import BenTrapani.CryptoArbitrage.ArbitrageExecutor.ExecutableTrade;
import BenTrapani.CryptoArbitrage.ArbitrageExecutor.IntermediateTrade;

public class ArbitrageExecutorTest {
	
	@Test
	public void testRatioConcatenationAndExecutableTradeConstruction() {
		String testExch1 = "testExch1";
		String testExch2 = "testExch2";
		String testExch3 = "testExch3";
		
		BigDecimal feeFraction = BigDecimal.ZERO;
		
		BigDecimal pointNineQuantity = BigDecimal.ONE.divide(BigDecimal.TEN).multiply(new BigDecimal(9));
		// Buy BTC with USD
		GraphEdge someGraphEdge = new GraphEdge(testExch1, Currency.BTC, true, 
				pointNineQuantity.add(BigDecimal.ZERO), 
				new BigDecimal(8330), feeFraction);
		
		// Sell BTC for DGC
		GraphEdge secondEdge = new GraphEdge(testExch2, Currency.DGC, false, 
				pointNineQuantity.add(BigDecimal.ZERO),
				new BigDecimal(1021), feeFraction);
		
		// Buy LTC with DGC
		GraphEdge thirdEdge = new GraphEdge(testExch3, Currency.LTC, true, BigDecimal.ONE,
				new BigDecimal(1021), feeFraction);
		
		IntermediateTrade intTrade1 = new IntermediateTrade(Currency.USD, someGraphEdge);
		assertEquals(someGraphEdge, intTrade1.graphEdge);
		assertEquals(Currency.USD, intTrade1.sourceCurrency);
		IntermediateTrade intTrade2 = new IntermediateTrade(Currency.BTC, secondEdge);
		IntermediateTrade intTrade3 = new IntermediateTrade(Currency.DGC, thirdEdge);
		
		List<IntermediateTrade> listOfTrades = new ArrayList<IntermediateTrade>(Arrays.asList(new IntermediateTrade[]{intTrade1, intTrade2, intTrade3}));
		listOfTrades = ArbitrageExecutor.populateConcatenatedRatios(listOfTrades);
		
		BigDecimal firstRatio = BigDecimal.ONE.divide(new BigDecimal(8330), CryptoConfigs.decimalScale, BigDecimal.ROUND_DOWN); // BTC per USD
		BigDecimal secondRatio = firstRatio.multiply(new BigDecimal(1021)); // DGC per USD
		BigDecimal thirdRatio = BigDecimal.ONE.divide(new BigDecimal(1021), CryptoConfigs.decimalScale, BigDecimal.ROUND_DOWN).multiply(secondRatio); // LTC per USD
		
		assertEquals(firstRatio, listOfTrades.get(0).concatenatedRatio);
		assertEquals(secondRatio, listOfTrades.get(1).concatenatedRatio);
		assertEquals(thirdRatio, listOfTrades.get(2).concatenatedRatio);
		assertEquals(BigDecimal.ONE, listOfTrades.get(0).prevConcatenatedRatio);
		assertEquals(firstRatio, listOfTrades.get(1).prevConcatenatedRatio);
		assertEquals(secondRatio, listOfTrades.get(2).prevConcatenatedRatio);
		
		listOfTrades = ArbitrageExecutor.computeQuantitiesInSourceUnits(listOfTrades);
		assertEquals(firstRatio, listOfTrades.get(0).concatenatedRatioUsedForQuantAdjust);
		assertEquals(firstRatio, listOfTrades.get(1).concatenatedRatioUsedForQuantAdjust);
		assertEquals(thirdRatio, listOfTrades.get(2).concatenatedRatioUsedForQuantAdjust);
		assertEquals(new BigDecimal(7497), listOfTrades.get(0).quantityInPathSourceUnits.setScale(0, BigDecimal.ROUND_HALF_UP));
		assertTrue(new BigDecimal(7497).compareTo(listOfTrades.get(0).quantityInPathSourceUnits.setScale(0, BigDecimal.ROUND_HALF_UP)) >= 0);
		assertEquals(new BigDecimal(7497), listOfTrades.get(1).quantityInPathSourceUnits.setScale(0, BigDecimal.ROUND_HALF_UP));
		assertTrue(new BigDecimal(7497).compareTo(listOfTrades.get(1).quantityInPathSourceUnits.setScale(0, BigDecimal.ROUND_HALF_UP)) >= 0);
		assertEquals(new BigDecimal(8330), listOfTrades.get(2).quantityInPathSourceUnits.setScale(0, BigDecimal.ROUND_HALF_UP));
		assertTrue(new BigDecimal(8330).compareTo(listOfTrades.get(2).quantityInPathSourceUnits.setScale(0, BigDecimal.ROUND_HALF_UP)) >= 0);
	}
	
	private static class MockBalanceDS implements ArbitrageExecutor.CurrencyBalanceDS {
		public static class DSKey {
			final Currency currency;
			final String exchangeName;
			
			public DSKey(Currency currency, String exchangeName) {
				this.currency = currency;
				this.exchangeName = exchangeName;
			}
			
			@Override
			public int hashCode() {
				return currency.hashCode() * 31 + exchangeName.hashCode();
			}
			
			@Override
			public boolean equals(Object obj) {
				if (obj == null) {
					return false;
				}
				if (getClass() != obj.getClass()) {
					return false;
				}
				final DSKey other = (DSKey)obj;
				return other.currency.equals(currency) && other.exchangeName.equals(exchangeName);
			}
		}
		
		public Hashtable<DSKey, BigDecimal> inMemoryDB = new Hashtable<DSKey, BigDecimal>();
		
		@Override
		public BigDecimal getBalance(Currency currency, String exchangeName) {
			DSKey keyToSearch = new DSKey(currency, exchangeName);
			BigDecimal balanceFound = inMemoryDB.get(keyToSearch);
			if (balanceFound == null) {
				throw new IllegalStateException("Could not find key in mock db table");
			}
			return balanceFound;
		}
		
	}
	
	@Test
	public void endToEndBuildExecutableTrades() {
		String testExch1 = "testExch1";
		String testExch2 = "testExch2";
		String testExch3 = "testExch3";
		String testExch4 = "testExch4";
		
		BigDecimal feeFraction = new BigDecimal(0.0);
		
		// Buy BTC with USD
		// Can push at most (8330 * 0.9) USD, or less if our balance limits us
		GraphEdge someGraphEdge = new GraphEdge(testExch1, Currency.BTC, true, new BigDecimal(0.9), 
				new BigDecimal(8330), feeFraction);
		TwoSidedGraphEdge twoSidedE1 = new TwoSidedGraphEdge(Currency.USD, someGraphEdge);
		
		// Sell BTC for DGC
		// Can push at most 0.9 BTC or less if BTC balance is not sufficient
		GraphEdge secondEdge = new GraphEdge(testExch2, Currency.DGC, false, new BigDecimal(0.9),
				new BigDecimal(1021), feeFraction);
		TwoSidedGraphEdge twoSidedE2 = new TwoSidedGraphEdge(Currency.BTC, secondEdge);
		
		// Buy LTC with DGC
		// Can push at most 1021 DGC, or less if DGC balance is insufficient
		GraphEdge thirdEdge = new GraphEdge(testExch3, Currency.LTC, true, new BigDecimal(1),
				new BigDecimal(1021), feeFraction);
		TwoSidedGraphEdge twoSidedE3 = new TwoSidedGraphEdge(Currency.DGC, thirdEdge);
		
		// Sell LTC for USD (unrealistically, LTC is more valuable than BTC)
		// Can push at most 1 LTC through this trade
		GraphEdge fourthEdge = new GraphEdge(testExch4, Currency.USD, false, new BigDecimal(1.0), 
				new BigDecimal(9000.0), feeFraction);
		TwoSidedGraphEdge twoSidedE4 = new TwoSidedGraphEdge(Currency.LTC, fourthEdge);
		
		HashSet<TwoSidedGraphEdge> graphEdges = new HashSet<TwoSidedGraphEdge>();
		graphEdges.add(twoSidedE1);
		graphEdges.add(twoSidedE2);
		graphEdges.add(twoSidedE3);
		graphEdges.add(twoSidedE4);
		
		// BTC per USD expected ratio: 1 / 8330
		// DGC per USD expected ratio: (1 / 8330) * 1021
		// LTC per USD expected ratio: ((1 / 8330) * 1021) * (1 / 1021)
		
		BigDecimal BTCPerUSD = BigDecimal.ONE.divide(new BigDecimal(8330), CryptoConfigs.decimalScale, BigDecimal.ROUND_DOWN);
		BigDecimal DGCPerUSD = BTCPerUSD.multiply(new BigDecimal(1021).setScale(CryptoConfigs.decimalScale, BigDecimal.ROUND_DOWN));
		BigDecimal LTCPerUSD = DGCPerUSD.divide(new BigDecimal(1021));
		
		// First trade USD quantity in USD: (8330 * 0.9) = 7,497
		// Second trade BTC quantity in USD: 0.9 / (1 / 8330) = 7,497
		// Third trade DGC quantity in USD: (1021 * 1) / ((1 / 8330) * 1021) = 8,330
		// Fourth trade LTC quantity in USD: 1 / (((1 / 8330) * 1021) * (1 / 1021)) = 8,330
		
		// The limiting components are the first two hops, so should be able to push 0.9 btc through minus corrections rounding errors
		// The computed quantities should always be close to the real quantities but slightly smaller
		BigDecimal expectedPathFlowInUSD = new BigDecimal(7497);
		
		MockBalanceDS balanceDS = new MockBalanceDS();
		balanceDS.inMemoryDB.put(new MockBalanceDS.DSKey(Currency.USD, testExch1), new BigDecimal(7498));
		balanceDS.inMemoryDB.put(new MockBalanceDS.DSKey(Currency.BTC, testExch2), new BigDecimal(0.91));
		balanceDS.inMemoryDB.put(new MockBalanceDS.DSKey(Currency.DGC, testExch3), new BigDecimal(1022));
		balanceDS.inMemoryDB.put(new MockBalanceDS.DSKey(Currency.LTC, testExch4), new BigDecimal(1));
		
		ArbitrageExecutor arbitrageExecutor = new ArbitrageExecutor(new BigDecimal(1.0), Currency.USD);
		List<ExecutableTrade> executableTrades = arbitrageExecutor.buildExecutableTrades(graphEdges, balanceDS);
		assertEquals(4, executableTrades.size());
		
		// This much imprecision is introduced in quantity calculations
		int testPrecision = CryptoConfigs.decimalScale - 4;
		
		// First trade to execute should be buying BTC with USD, and we have enough USD to fill the order
		assertEquals(Currency.BTC, executableTrades.get(0).base);
		assertEquals(Currency.USD, executableTrades.get(0).counter);
		assertEquals(testExch1, executableTrades.get(0).exchangeName);
		assertEquals(true, executableTrades.get(0).isBuy);
		assertEquals(someGraphEdge.price, executableTrades.get(0).price);
		BigDecimal expectedQuantity1 = expectedPathFlowInUSD.multiply(BTCPerUSD);
		assertEquals(expectedQuantity1.setScale(testPrecision, BigDecimal.ROUND_DOWN), 
				executableTrades.get(0).quantity.setScale(testPrecision, BigDecimal.ROUND_DOWN));
		assertEquals(true, executableTrades.get(0).quantity.compareTo(expectedQuantity1) <= 0);
		
		// Second trade should be selling BTC for DGC
		assertEquals(Currency.BTC, executableTrades.get(1).base);
		assertEquals(Currency.DGC, executableTrades.get(1).counter);
		assertEquals(testExch2, executableTrades.get(1).exchangeName);
		assertEquals(false, executableTrades.get(1).isBuy);
		assertEquals(secondEdge.price, executableTrades.get(1).price);
		assertEquals(expectedQuantity1.setScale(testPrecision, BigDecimal.ROUND_DOWN), 
				executableTrades.get(1).quantity.setScale(testPrecision, BigDecimal.ROUND_DOWN));
		assertEquals(true, executableTrades.get(1).quantity.compareTo(expectedQuantity1) <= 0);
		
		// Third trade should be buying LTC with DGC
		BigDecimal expectedQuantityLTC = expectedPathFlowInUSD.multiply(LTCPerUSD);
		assertEquals(Currency.LTC, executableTrades.get(2).base);
		assertEquals(Currency.DGC, executableTrades.get(2).counter);
		assertEquals(testExch3, executableTrades.get(2).exchangeName);
		assertEquals(true, executableTrades.get(2).isBuy);
		assertEquals(thirdEdge.price, executableTrades.get(2).price);
		assertEquals(expectedQuantityLTC.setScale(testPrecision, BigDecimal.ROUND_DOWN), 
				executableTrades.get(2).quantity.setScale(testPrecision, BigDecimal.ROUND_DOWN));
		assertEquals(true, expectedQuantityLTC.compareTo(executableTrades.get(2).quantity) >= 0);
		
		// Fourth trade should be selling LTC for USD
		assertEquals(Currency.LTC, executableTrades.get(3).base);
		assertEquals(Currency.USD, executableTrades.get(3).counter);
		assertEquals(testExch4, executableTrades.get(3).exchangeName);
		assertEquals(false, executableTrades.get(3).isBuy);
		assertEquals(fourthEdge.price, executableTrades.get(3).price);
		assertEquals(expectedQuantityLTC.setScale(testPrecision, BigDecimal.ROUND_DOWN), 
				executableTrades.get(3).quantity.setScale(testPrecision, BigDecimal.ROUND_DOWN));
		assertEquals(true, expectedQuantityLTC.compareTo(executableTrades.get(3).quantity) >= 0);
	}
}
