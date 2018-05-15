package BenTrapani.CryptoArbitrage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;

import BenTrapani.CryptoArbitrage.OrderBookAnalyzer.AnalysisResult;
import BenTrapani.CryptoArbitrage.OrderGraph.GraphEdge;
import BenTrapani.CryptoArbitrage.OrderGraph.TwoSidedGraphEdge;
import BenTrapani.CryptoArbitrage.OrderGraphAnalysisHandler;

import org.knowm.xchange.currency.Currency;

public class ArbitrageExecutor implements OrderGraphAnalysisHandler {
	
	private final BigDecimal minAcceptableRatio;
	private final Currency tradePathSource;
	private ConcreteCurrencyBalanceDS currencyBalanceDS;
	
	public ArbitrageExecutor(BigDecimal minAcceptableRatio, Currency tradePathSource) {
		this.minAcceptableRatio = minAcceptableRatio;
		this.tradePathSource = tradePathSource;
	}
	
	public void setExchanges(StreamingExchangeSubset[] newExchanges) {
		synchronized(currencyBalanceDS) {
			this.currencyBalanceDS = new ConcreteCurrencyBalanceDS(newExchanges);
		}
	}
	
	public static interface CurrencyBalanceDS {
		public BigDecimal getBalance(Currency currency, String exchangeName);
	}
	
	protected static class ExecutableTrade {
		final String exchangeName;
		final Currency base;
		final Currency counter;
		final BigDecimal quantity;
		final BigDecimal price;
		final boolean isBuy;
		
		public ExecutableTrade(IntermediateTrade intTrade, BigDecimal sourceQuantity) {
			this.exchangeName = intTrade.graphEdge.exchangeName;
			if (intTrade.graphEdge.isBuy) {
				this.base = intTrade.graphEdge.destCurrency;
				this.counter = intTrade.sourceCurrency;
			}else {
				this.base = intTrade.sourceCurrency;
				this.counter = intTrade.graphEdge.destCurrency;
			}
			
			// for buy, counter -> base, dest = base
			// for sell, base -> counter, dest = counter
			// quantity is in base
			// price is in counter
			// concatenated ratio = base per unit tradePathSource
			// quantity here = quantity tradePathSource * concatenated ratio
			// If buy, use ratio here. If sell, use prev ratio
			this.quantity = sourceQuantity.multiply(intTrade.concatenatedRatioUsedForQuantAdjust);
			
			// can't modify price at all
			this.price = intTrade.graphEdge.price;
			this.isBuy = intTrade.graphEdge.isBuy;
		}
		
		@Override
		public String toString() {
			return "Exchange name: " + exchangeName + "\n" +
				   "base: " + base.toString() + "\n" + 
				   "counter: " + counter.toString() + "\n " +
				   "quantity: " + quantity.toString() + "\n" +
				   "price: " + price.toString() + "\n" +
				   "isBuy: " + isBuy + "\n\n";
		}
	}
	
	protected static class IntermediateTrade {
		final Currency sourceCurrency;
		final GraphEdge graphEdge;
		BigDecimal concatenatedRatio;
		BigDecimal prevConcatenatedRatio;
		BigDecimal concatenatedRatioUsedForQuantAdjust; //same as concatenatedRatio for buy, and is concatenatedRatio of prev ratio for sell
		BigDecimal quantityInPathSourceUnits; //source quantity in terms of path source units
		
		public IntermediateTrade(Currency sourceCurrency, GraphEdge graphEdge) {
			this.sourceCurrency = sourceCurrency;
			this.graphEdge = graphEdge;
		}
	}
	
	protected static List<IntermediateTrade> populateConcatenatedRatios(List<IntermediateTrade> intermediateTrades) {
		BigDecimal ratioHerePerUnitSource = BigDecimal.ONE;
		for (IntermediateTrade intermediateTrade : intermediateTrades) {
			BigDecimal nextRatio = ratioHerePerUnitSource.multiply(intermediateTrade.graphEdge.ratio);
			intermediateTrade.concatenatedRatio = nextRatio;
			intermediateTrade.prevConcatenatedRatio = ratioHerePerUnitSource;
			ratioHerePerUnitSource = nextRatio;
		}
		return intermediateTrades;
	}
	
	protected static List<IntermediateTrade> computeQuantitiesInSourceUnits(List<IntermediateTrade> intermediateTrades) {
		for (int i = 0; i < intermediateTrades.size(); ++i) {
			IntermediateTrade intTrade = intermediateTrades.get(i);
			intTrade.concatenatedRatioUsedForQuantAdjust = intTrade.graphEdge.isBuy ? intTrade.concatenatedRatio : intTrade.prevConcatenatedRatio;
			BigDecimal adjustedPrevConcatRat = intTrade.prevConcatenatedRatio.add(CryptoConfigs.decimalRoundAdjustDigit);
			if (intTrade.graphEdge.isBuy) {
				BigDecimal sourceQuantity = intTrade.graphEdge.price.multiply(intTrade.graphEdge.quantity);
				BigDecimal sourceQuantityInPathSource = sourceQuantity.divide(adjustedPrevConcatRat, CryptoConfigs.decimalScale, BigDecimal.ROUND_DOWN);
				intTrade.quantityInPathSourceUnits = sourceQuantityInPathSource;
			} else {
				intTrade.quantityInPathSourceUnits = intTrade.graphEdge.quantity.divide(adjustedPrevConcatRat, CryptoConfigs.decimalScale, BigDecimal.ROUND_DOWN);
			}
		}
		return intermediateTrades;
	}
	
	protected static BigDecimal computeLoopMaxflowInSource(List<IntermediateTrade> intTrades, 
			CurrencyBalanceDS ds) {
		BigDecimal minEdgeCapacityInPathSourceUnits = null;
		for (IntermediateTrade intTrade : intTrades) {
			BigDecimal sourceQuantityAvailableForTrade = ds.getBalance(intTrade.sourceCurrency, intTrade.graphEdge.exchangeName);
			BigDecimal sourceQuantityAvailableForTradeInPathSourceUnits = sourceQuantityAvailableForTrade.divide(intTrade.prevConcatenatedRatio.add(CryptoConfigs.decimalRoundAdjustDigit), 
					CryptoConfigs.decimalScale, BigDecimal.ROUND_DOWN);
			BigDecimal minSourceQuantityInPathSourceUnits = intTrade.quantityInPathSourceUnits.min(sourceQuantityAvailableForTradeInPathSourceUnits);
			if (minEdgeCapacityInPathSourceUnits == null || minEdgeCapacityInPathSourceUnits.compareTo(minSourceQuantityInPathSourceUnits) > 0) {
				minEdgeCapacityInPathSourceUnits = minSourceQuantityInPathSourceUnits;
			}
		}
		if (minEdgeCapacityInPathSourceUnits != null){
			// bad way of copying it
			minEdgeCapacityInPathSourceUnits = minEdgeCapacityInPathSourceUnits.add(BigDecimal.ZERO);
		}
		return minEdgeCapacityInPathSourceUnits;
	}
	
	static List<ExecutableTrade> convertToExecutableTrades(List<IntermediateTrade> intTrades,
			BigDecimal sourceQuantity) {
		
		List<ExecutableTrade> executableTrades = new ArrayList<ExecutableTrade>(intTrades.size());
		
		for (int i = 0; i < intTrades.size(); i++) {
			ExecutableTrade executableTrade = new ExecutableTrade(intTrades.get(i), sourceQuantity);
			executableTrades.add(executableTrade);
		}
		
		return executableTrades;
	}
	
	List<ExecutableTrade> buildExecutableTrades(HashSet<TwoSidedGraphEdge> graphEdges, CurrencyBalanceDS ds) {
		Hashtable<Currency, GraphEdge> sourceCurrencyToGraphEdge = new Hashtable<Currency, GraphEdge>();
		for (TwoSidedGraphEdge graphEdge : graphEdges) {
			sourceCurrencyToGraphEdge.put(graphEdge.sourceCurrency, graphEdge.graphEdge);
		}
		
		List<IntermediateTrade> orderedEdges = new ArrayList<IntermediateTrade>(graphEdges.size());
		Currency prevCurrency = tradePathSource;
		for (int i = 0; i < graphEdges.size(); ++i) {
			GraphEdge nextEdge = sourceCurrencyToGraphEdge.get(prevCurrency);
			orderedEdges.add(new IntermediateTrade(prevCurrency, nextEdge));
			prevCurrency = nextEdge.destCurrency;
		}
		
		populateConcatenatedRatios(orderedEdges);
		computeQuantitiesInSourceUnits(orderedEdges);
		BigDecimal loopCapacity = computeLoopMaxflowInSource(orderedEdges, ds);
		return convertToExecutableTrades(orderedEdges, loopCapacity);
	}
	
	@Override
	public void onOrderBookAnalysisComplete(AnalysisResult analysisResult) {
		if (analysisResult.maxRatio.compareTo(minAcceptableRatio) > 0) {
			String allTrades = "Trades: \n";
			for (TwoSidedGraphEdge trade: analysisResult.tradesToExecute) {
				allTrades += trade.toString();
			}
			System.out.println("Profitable trade found with ratio " + analysisResult.maxRatio.toString() + "\n" + allTrades);
			
			List<ExecutableTrade> executableTrades = null;
			synchronized(currencyBalanceDS) {
				executableTrades = buildExecutableTrades(analysisResult.tradesToExecute, currencyBalanceDS);
			}
			System.out.println(executableTrades.toString());
			
		}else {
			System.out.println("No profitable trade found. Best ratio = " + analysisResult.maxRatio);
		}
	}
}
