package BenTrapani.CryptoArbitrage;

import java.util.Scanner;

import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;

import info.bitrich.xchangestream.bitfinex.BitfinexStreamingExchange;
import info.bitrich.xchangestream.gdax.GDAXStreamingExchange;
import info.bitrich.xchangestream.gemini.GeminiStreamingExchange;
import info.bitrich.xchangestream.binance.BinanceStreamingExchange;
import info.bitrich.xchangestream.kraken.KrakenStreamingExchange;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
    	// Binance (done)
    	// Kraken
    	// HitBTC
    	// Bitmex
    	// Bitfinex (done)
    	// GDAX (done)
    	// Gemini (done)
    	StreamingExchange binanceExch = StreamingExchangeFactory.INSTANCE.createExchange(BinanceStreamingExchange.class.getName());
    	StreamingExchange bitfinexExch = StreamingExchangeFactory.INSTANCE.createExchange(BitfinexStreamingExchange.class.getName());
    	StreamingExchange xchangeGdx = StreamingExchangeFactory.INSTANCE.createExchange(GDAXStreamingExchange.class.getName());
    	StreamingExchange geminiExch = StreamingExchangeFactory.INSTANCE.createExchange(GeminiStreamingExchange.class.getName());
    	StreamingExchange krakenExch = StreamingExchangeFactory.INSTANCE.createExchange(KrakenStreamingExchange.class.getName());
    	
        CryptoArbitrageManager manager = new CryptoArbitrageManager(new StreamingExchange[]{
        		binanceExch, 
        		bitfinexExch, 
        		xchangeGdx, 
        		geminiExch,
        		krakenExch});
        manager.startArbitrage();
        
        boolean shouldExit = false;
        Scanner s = new Scanner(System.in);
        while(!shouldExit) {
        	String curCommand = s.nextLine();
        	switch(curCommand) {
        	case "QUIT":{
        		shouldExit = true;
        		break;
        	}
        	default: {
        		System.out.println("Unknown command " + curCommand + ", continuing");
        	}
        	}
        }
        manager.stopArbitrage();
        s.close();
    }
}
