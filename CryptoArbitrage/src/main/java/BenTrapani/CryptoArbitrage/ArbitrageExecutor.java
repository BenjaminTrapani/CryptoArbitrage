package BenTrapani.CryptoArbitrage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import BenTrapani.CryptoArbitrage.OrderBookAnalyzer.AnalysisResult;
import BenTrapani.CryptoArbitrage.OrderGraph.GraphEdge;
import BenTrapani.CryptoArbitrage.OrderGraph.TwoSidedGraphEdge;
import BenTrapani.CryptoArbitrage.OrderGraphAnalysisHandler;

import org.knowm.xchange.currency.Currency;

public class ArbitrageExecutor implements OrderGraphAnalysisHandler {
	
	private final Fraction minAcceptableRatio;
	private final Currency tradePathSource;
	private ConcreteCurrencyBalanceDS currencyBalanceDS;
	private ReentrantLock currencyBalanceDSLock = new ReentrantLock();
	
	public ArbitrageExecutor(Fraction minAcceptableRatio, Currency tradePathSource) {
		this.minAcceptableRatio = minAcceptableRatio;
		this.tradePathSource = tradePathSource;
	}
	
	public void setExchanges(StreamingExchangeSubset[] newExchanges) {
		currencyBalanceDSLock.lock();
		this.currencyBalanceDS = new ConcreteCurrencyBalanceDS(newExchanges);
		currencyBalanceDSLock.unlock();
	}
	
	public static interface CurrencyBalanceDS {
		public Fraction getBalance(Currency currency, String exchangeName);
	}
	
	protected static class ExecutableTrade {
		final String exchangeName;
		final Currency base;
		final Currency counter;
		final Fraction quantity;
		final Fraction price;
		final boolean isBuy;
		
		public ExecutableTrade(IntermediateTrade intTrade, Fraction sourceQuantity) {
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
			
			// TODO add a check here that ensures that this.quantity is also less than or equal
			// to available balance on the exchange. Cache this value in IntermediateTrade or something
			if (this.quantity.compareTo(intTrade.graphEdge.quantity) > 0) {
				throw new IllegalStateException("Executable quantity computed from source cannot exceed initial quantity available for trade");
			}
			
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
		Fraction concatenatedRatio;
		Fraction prevConcatenatedRatio;
		Fraction concatenatedRatioUsedForQuantAdjust; //same as concatenatedRatio for buy, and is concatenatedRatio of prev ratio for sell
		Fraction quantityInPathSourceUnits; //source quantity in terms of path source units
		
		public IntermediateTrade(Currency sourceCurrency, GraphEdge graphEdge) {
			this.sourceCurrency = sourceCurrency;
			this.graphEdge = graphEdge;
		}
	}
	
	protected static List<IntermediateTrade> populateConcatenatedRatios(List<IntermediateTrade> intermediateTrades) {
		Fraction ratioHerePerUnitSource = new Fraction(1);
		for (IntermediateTrade intermediateTrade : intermediateTrades) {
			Fraction nextRatio = ratioHerePerUnitSource.multiply(intermediateTrade.graphEdge.ratio);
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
			Fraction prevConcatRat = intTrade.prevConcatenatedRatio;
			if (intTrade.graphEdge.isBuy) {
				Fraction sourceQuantity = intTrade.graphEdge.price.multiply(intTrade.graphEdge.quantity);
				Fraction sourceQuantityInPathSource = sourceQuantity.divide(prevConcatRat);
				intTrade.quantityInPathSourceUnits = sourceQuantityInPathSource;
			} else {
				intTrade.quantityInPathSourceUnits = intTrade.graphEdge.quantity.divide(prevConcatRat);
			}
		}
		return intermediateTrades;
	}
	
	protected static Fraction computeLoopMaxflowInSource(List<IntermediateTrade> intTrades, 
			CurrencyBalanceDS ds) {
		Fraction minEdgeCapacityInPathSourceUnits = null;
		for (IntermediateTrade intTrade : intTrades) {
			Fraction sourceQuantityAvailableForTrade = ds.getBalance(intTrade.sourceCurrency, intTrade.graphEdge.exchangeName);
			Fraction sourceQuantityAvailableForTradeInPathSourceUnits = sourceQuantityAvailableForTrade.divide(intTrade.prevConcatenatedRatio);
			Fraction minSourceQuantityInPathSourceUnits = intTrade.quantityInPathSourceUnits.min(sourceQuantityAvailableForTradeInPathSourceUnits);
			if (minEdgeCapacityInPathSourceUnits == null || minEdgeCapacityInPathSourceUnits.compareTo(minSourceQuantityInPathSourceUnits) > 0) {
				minEdgeCapacityInPathSourceUnits = minSourceQuantityInPathSourceUnits;
			}
		}
		return minEdgeCapacityInPathSourceUnits;
	}
	
	static List<ExecutableTrade> convertToExecutableTrades(List<IntermediateTrade> intTrades,
			Fraction sourceQuantity) {
		
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
		Fraction loopCapacity = computeLoopMaxflowInSource(orderedEdges, ds);
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
			System.out.println("No profitable trade found. Best ratio = " + analysisResult.maxRatio.convertToBigDecimal(5, BigDecimal.ROUND_DOWN));
		}
	}
}
