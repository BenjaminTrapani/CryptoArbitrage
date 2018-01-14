package BenTrapani.CryptoArbitrage;

import java.math.BigDecimal;

import BenTrapani.CryptoArbitrage.OrderBookAnalyzer.AnalysisResult;
import BenTrapani.CryptoArbitrage.OrderGraph.TwoSidedGraphEdge;
import BenTrapani.CryptoArbitrage.OrderGraphAnalysisHandler;

public class ArbitrageExecutor implements OrderGraphAnalysisHandler {
	
	private BigDecimal minAcceptableRatio = new BigDecimal(1.0);
	
	@Override
	public void onOrderBookAnalysisComplete(AnalysisResult analysisResult) {
		if (analysisResult.maxRatio.compareTo(minAcceptableRatio) > 0) {
			String allTrades = "Trades: \n";
			for (TwoSidedGraphEdge trade: analysisResult.tradesToExecute) {
				allTrades += trade.toString();
			}
			System.out.println("Profitable trade found with ratio " + analysisResult.maxRatio.toString() + "\n" + allTrades);
		}else {
			System.out.println("No profitable trade found");
		}
	}
}
