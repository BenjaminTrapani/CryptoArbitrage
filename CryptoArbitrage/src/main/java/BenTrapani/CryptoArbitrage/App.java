package BenTrapani.CryptoArbitrage;

import java.util.Scanner;

import info.bitrich.xchangestream.bitstamp.BitstampStreamingExchange;

import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;
import info.bitrich.xchangestream.gdax.GDAXStreamingExchange;
import info.bitrich.xchangestream.poloniex.PoloniexStreamingExchange;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
    	StreamingExchange bitstampExch = StreamingExchangeFactory.INSTANCE.createExchange(BitstampStreamingExchange.class.getName());
        StreamingExchange poloniexExch = StreamingExchangeFactory.INSTANCE.createExchange(PoloniexStreamingExchange.class.getName());
        StreamingExchange xchangeGdx = StreamingExchangeFactory.INSTANCE.createExchange(GDAXStreamingExchange.class.getName());
        CryptoArbitrageManager manager = new CryptoArbitrageManager(new StreamingExchange[]{xchangeGdx, bitstampExch});
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
        s.close();
    }
}
