package BenTrapani.CryptoArbitrage;
import BenTrapani.CryptoArbitrage.OrderBookAnalyzer.AnalysisResult;

public interface OrderGraphAnalysisHandler {
	public void onOrderBookAnalysisComplete(final AnalysisResult analysisResult);
}
