package BenTrapani.CryptoArbitrage;

import java.io.IOException;
import java.util.Scanner;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.kraken.KrakenExchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.Fee;
import java.util.Map;

import BenTrapani.CryptoArbitrage.ExchangeAPIKeys.APIKeyPair;
import BenTrapani.CryptoArbitrage.ExchangeAPIKeys.ExchangeKeyPair;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;

import info.bitrich.xchangestream.bitfinex.BitfinexStreamingExchange;
import info.bitrich.xchangestream.gemini.GeminiStreamingExchange;
import info.bitrich.xchangestream.binance.BinanceStreamingExchange;
import info.bitrich.xchangestream.hitbtc.HitbtcStreamingExchange;
import info.bitrich.xchangestream.wex.WexStreamingExchange;
import info.bitrich.xchangestream.cexio.CexioStreamingExchange;

public class App 
{
    public static void main( String[] args )
    {
    	ExchangeAPIKeys exchangeAPIKeys = null;
		try {
			exchangeAPIKeys = new ExchangeAPIKeys("APIKeys.json");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.err.println("Error loading API keys from config file: " + e.toString());
			e.printStackTrace();
		}
    	// Binance (done)
    	// Kraken (done)
    	// HitBTC 
    	// Bitfinex (done)
    	// GDAX (done)
    	// Gemini (done)
    	//StreamingExchange binanceExch = StreamingExchangeFactory.INSTANCE.createExchange(BinanceStreamingExchange.class.getName());
    	StreamingExchange bitfinexExch = StreamingExchangeFactory.INSTANCE.createExchange(BitfinexStreamingExchange.class.getName());
    	//StreamingExchange geminiExch = StreamingExchangeFactory.INSTANCE.createExchange(GeminiStreamingExchange.class.getName());
    	StreamingExchange hitbtcExch = StreamingExchangeFactory.INSTANCE.createExchange(HitbtcStreamingExchange.class.getName());
    	//StreamingExchange wexExch = StreamingExchangeFactory.INSTANCE.createExchange(WexStreamingExchange.class.getName());
    	//StreamingExchange cexIOExch = StreamingExchangeFactory.INSTANCE.createExchange(CexioStreamingExchange.class.getName());
    	Exchange krakenExchange = ExchangeFactory.INSTANCE.createExchange(KrakenExchange.class.getName());
    	
    	Exchange[] exchangeCollection = new Exchange[]{bitfinexExch, hitbtcExch, krakenExchange};
		for (Exchange exch : exchangeCollection) {
			String exchangeName = exch.getExchangeSpecification().getExchangeName();
			ExchangeKeyPair keyPair = null;
			try {
				keyPair = exchangeAPIKeys.getKeyPairForExchange(exchangeName);
			} catch (IllegalStateException e) {
				System.out.println("Missing API keys for exchange " + exchangeName);
				continue;
			}
			// make a function that executes the below, and then call for each exchange
	    	ExchangeSpecification spec = exch.getExchangeSpecification();
	    	spec.setApiKey(keyPair.apiKeyPair.publicKey);
	    	spec.setSecretKey(keyPair.apiKeyPair.privateKey);
	    	spec.setExchangeSpecificParameters(keyPair.otherProperties);
	    	exch.applySpecification(spec);
		}
    	
    	StreamingExchangeSubset[] exchangeSubsets = new StreamingExchangeSubset[]{
    		//new StreamingExchangeAdapter(binanceExch),
    		new StreamingExchangeAdapter(bitfinexExch),
    		//new StreamingExchangeAdapter(xchangeGdx),
    		//new StreamingExchangeAdapter(geminiExch),
    		new StreamingExchangeAdapter(hitbtcExch),
    		//new StreamingExchangeAdapter(wexExch),
    		//new StreamingExchangeAdapter(cexIOExch)
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
