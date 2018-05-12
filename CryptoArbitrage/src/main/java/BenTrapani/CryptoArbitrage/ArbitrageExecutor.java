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
	
	public ArbitrageExecutor(BigDecimal minAcceptableRatio, Currency tradePathSource) {
		this.minAcceptableRatio = minAcceptableRatio;
		this.tradePathSource = tradePathSource;
	}
	
	protected static class ExecutableTrade {
		final String exchangeName;
		final Currency base;
		final Currency counter;
		final BigDecimal quantity;
		final BigDecimal price;
		final boolean isBuy;
		
		public ExecutableTrade(IntermediateTrade intTrade, BigDecimal prevRatio, BigDecimal sourceQuantity) {
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
			BigDecimal ratioToAdjustQuantBy = intTrade.graphEdge.isBuy ? intTrade.concatenatedRatio : prevRatio;
			this.quantity = sourceQuantity.multiply(ratioToAdjustQuantBy);
			
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
		
		public IntermediateTrade(Currency sourceCurrency, GraphEdge graphEdge) {
			this.sourceCurrency = sourceCurrency;
			this.graphEdge = graphEdge;
		}
	}
	
	protected static List<IntermediateTrade> populateConcatenatedRatios(List<IntermediateTrade> intermediateTrades) {
		BigDecimal ratioHerePerUnitSource = new BigDecimal(1.0);
		for (IntermediateTrade intermediateTrade : intermediateTrades) {
			BigDecimal nextRatio = ratioHerePerUnitSource.multiply(intermediateTrade.graphEdge.ratio);
			intermediateTrade.concatenatedRatio = nextRatio;
			ratioHerePerUnitSource = nextRatio;
		}
		return intermediateTrades;
	}
	
	static List<ExecutableTrade> convertToExecutableTrades(List<IntermediateTrade> intTrades,
			BigDecimal sourceQuantity) {
		
		List<ExecutableTrade> executableTrades = new ArrayList<ExecutableTrade>(intTrades.size());
		
		for (int i = 0; i < intTrades.size(); i++) {
			BigDecimal prevRatio = i == 0 ? new BigDecimal(1.0) : intTrades.get(i-1).concatenatedRatio;
			ExecutableTrade executableTrade = new ExecutableTrade(intTrades.get(i), prevRatio, sourceQuantity);
			executableTrades.add(executableTrade);
		}
		
		return executableTrades;
	}
	
	private List<ExecutableTrade> buildExecutableTrades(HashSet<TwoSidedGraphEdge> graphEdges, 
			BigDecimal sourceQuantity) {
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
		// TODO add an additional pass here that computes the min of the source quantity and
		// the quantity of each currency with respect to the source
		// The quantity of each currency in terms of the source can be computed as 
		return convertToExecutableTrades(orderedEdges, sourceQuantity);
	}
	
	@Override
	public void onOrderBookAnalysisComplete(AnalysisResult analysisResult) {
		if (analysisResult.maxRatio.compareTo(minAcceptableRatio) > 0) {
			String allTrades = "Trades: \n";
			for (TwoSidedGraphEdge trade: analysisResult.tradesToExecute) {
				allTrades += trade.toString();
			}
			System.out.println("Profitable trade found with ratio " + analysisResult.maxRatio.toString() + "\n" + allTrades);
			List<ExecutableTrade> executableTrades = buildExecutableTrades(analysisResult.tradesToExecute, new BigDecimal(100));
			System.out.println(executableTrades.toString());
		}else {
			System.out.println("No profitable trade found. Best ratio = " + analysisResult.maxRatio);
		}
	}
}
