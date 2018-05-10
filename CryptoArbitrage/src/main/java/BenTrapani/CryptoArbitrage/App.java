package BenTrapani.CryptoArbitrage;

import java.util.Scanner;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.kraken.KrakenExchange;

import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;

import info.bitrich.xchangestream.bitfinex.BitfinexStreamingExchange;
import info.bitrich.xchangestream.gdax.GDAXStreamingExchange;
import info.bitrich.xchangestream.gemini.GeminiStreamingExchange;
import info.bitrich.xchangestream.binance.BinanceStreamingExchange;
import info.bitrich.xchangestream.hitbtc.HitbtcStreamingExchange;

public class App 
{
    public static void main( String[] args )
    {
    	// Binance (done)
    	// Kraken (done)
    	// HitBTC 
    	// Bitmex
    	// Bitfinex (done)
    	// GDAX (done)
    	// Gemini (done)
    	StreamingExchange binanceExch = StreamingExchangeFactory.INSTANCE.createExchange(BinanceStreamingExchange.class.getName());
    	StreamingExchange bitfinexExch = StreamingExchangeFactory.INSTANCE.createExchange(BitfinexStreamingExchange.class.getName());
    	StreamingExchange xchangeGdx = StreamingExchangeFactory.INSTANCE.createExchange(GDAXStreamingExchange.class.getName());
    	StreamingExchange geminiExch = StreamingExchangeFactory.INSTANCE.createExchange(GeminiStreamingExchange.class.getName());
    	StreamingExchange hitbtcExch = StreamingExchangeFactory.INSTANCE.createExchange(HitbtcStreamingExchange.class.getName());
    	Exchange krakenExchange = ExchangeFactory.INSTANCE.createExchange(KrakenExchange.class.getName());
    	StreamingExchangeSubset[] exchangeSubsets = new StreamingExchangeSubset[]{
    		new StreamingExchangeAdapter(binanceExch),
    		new StreamingExchangeAdapter(bitfinexExch),
    		new StreamingExchangeAdapter(xchangeGdx),
    		new StreamingExchangeAdapter(geminiExch),
    		//new StreamingExchangeAdapter(hitbtcExch),
    		new PollingExchangeAdapter(krakenExchange)
    	};
        CryptoArbitrageManager manager = new CryptoArbitrageManager(exchangeSubsets);
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
