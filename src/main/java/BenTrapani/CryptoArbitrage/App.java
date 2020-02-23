package BenTrapani.CryptoArbitrage;

import java.io.IOException;
import java.util.Scanner;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;

import BenTrapani.CryptoArbitrage.ExchangeAPIKeys.ExchangeKeyPair;
import info.bitrich.xchangestream.bitfinex.BitfinexStreamingExchange;
import info.bitrich.xchangestream.bitflyer.BitflyerStreamingExchange;
import info.bitrich.xchangestream.kraken.KrakenStreamingExchange;
import info.bitrich.xchangestream.bitstamp.v2.BitstampStreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;
import info.bitrich.xchangestream.hitbtc.HitbtcStreamingExchange;

public class App {
	public static void main(String[] args) {
		ExchangeAPIKeys exchangeAPIKeys = null;
		try {
			exchangeAPIKeys = new ExchangeAPIKeys("APIKeys.json");
		} catch (IOException e) {
			System.err.println("Error loading API keys from config file: " + e.toString());
			e.printStackTrace();
		}
		// Notes on APIs tried
		// Hitbtc works well
		// Bitfinex works well
		// Binance not supported in US yet
		// Wex API works, but the exchange has weird things going on (don't use)
		// Bitflyer API does not work (no market data coming through). This is
		// due to no metadata for currencies. TODO add currency metadata and
		// dynamic trading fees API.
		// Update on bitflyer: should work and has dynamic fees (requires
		// creating API key and test)
		// Cexio: Working
		// Gemini: Buggy stream handling code as of 9/19
		// StreamingExchange binanceExch =
		// StreamingExchangeFactory.INSTANCE.createExchange(BinanceStreamingExchange.class.getName());
		/*StreamingExchange bitfinexExch = StreamingExchangeFactory.INSTANCE
				.createExchange(BitfinexStreamingExchange.class.getName());*/
		// StreamingExchange geminiExch =
		// StreamingExchangeFactory.INSTANCE.createExchange(GeminiStreamingExchange.class.getName());
		StreamingExchange hitbtcExch = StreamingExchangeFactory.INSTANCE
				.createExchange(HitbtcStreamingExchange.class.getName());
		// StreamingExchange wexExch =
		// StreamingExchangeFactory.INSTANCE.createExchange(WexStreamingExchange.class.getName());
		// StreamingExchange cexIOExch =
		// StreamingExchangeFactory.INSTANCE.createExchange(CexioStreamingExchange.class.getName());
		StreamingExchange bitFlyer = StreamingExchangeFactory.INSTANCE
				.createExchange(BitflyerStreamingExchange.class.getName());
		StreamingExchange krakenExchange = StreamingExchangeFactory.INSTANCE.createExchange(KrakenStreamingExchange.class.getName());
		StreamingExchange bitstampExch = StreamingExchangeFactory.INSTANCE
				.createExchange(BitstampStreamingExchange.class.getName());

		Exchange[] exchangeCollection = new Exchange[] { bitstampExch, bitFlyer, /*bitfinexExch,*/ hitbtcExch,
				krakenExchange/* , cexIOExch */ };
		for (Exchange exch : exchangeCollection) {
			String exchangeName = exch.getExchangeSpecification().getExchangeName();
			ExchangeKeyPair keyPair = null;
			try {
				keyPair = exchangeAPIKeys.getKeyPairForExchange(exchangeName);
			} catch (IllegalStateException e) {
				System.out.println("Missing API keys for exchange " + exchangeName);
				continue;
			}
			ExchangeSpecification spec = exch.getExchangeSpecification();
			spec.setApiKey(keyPair.apiKeyPair.publicKey);
			spec.setSecretKey(keyPair.apiKeyPair.privateKey);
			if (keyPair.userName != null) {
				spec.setUserName(keyPair.userName);
			}
			spec.setExchangeSpecificParameters(keyPair.otherProperties);
			exch.applySpecification(spec);
		}

		StreamingExchangeSubset[] exchangeSubsets = new StreamingExchangeSubset[] {
				// new StreamingExchangeAdapter(binanceExch),
				//new StreamingExchangeAdapter(bitfinexExch),
				// new StreamingExchangeAdapter(xchangeGdx),
				// new StreamingExchangeAdapter(geminiExch),
				new StreamingExchangeAdapter(hitbtcExch), new StreamingExchangeAdapter(bitFlyer),
				// new StreamingExchangeAdapter(wexExch),
				// new StreamingExchangeAdapter(cexIOExch),
				new StreamingExchangeAdapter(krakenExchange), new StreamingExchangeAdapter(bitstampExch) };

		CryptoArbitrageManager manager = new CryptoArbitrageManager(exchangeSubsets);
		manager.startArbitrage();

		boolean shouldExit = false;
		Scanner s = new Scanner(System.in);
		while (!shouldExit) {
			String curCommand = s.nextLine();
			switch (curCommand) {
			case "QUIT": {
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
