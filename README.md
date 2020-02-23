# Crypto arbitrage
Builds a graph of all currency conversions possible connected to the exchanges specified in App.java. Take the following path for example:

BTC--10-->LTC--0.05-->DGC--2.01-->BTC

The combined ratio of the path is 1.005, yielding a 1.005x return if all trades are filled. The graph of possible trades is checked after each update and the best loop is found. The common source / dest currency is specified during construction of OrderBookAnalyzer (see CryptoArbitrageManager.java). The minimum ratio required to execute trades is also specified in CryptoArbitrageManager.java (ArbitrageExecutor constructor, defaults to 1). 

When a path meeting or exceeding the minimum required threshold is found, the maximum quantity that can be pushed through the path is computed such that the quantities of each intermediate currency (all but BTC in the example above) remain the same. The quantity of BTC will increase by 1.005x after all trades are filled. The maximum quantity of the path is limited by the quantity of the order used to build the corresponding graph edge. It is also limited by the quantity that the user has in the given currency on the exchange associated with the graph edge used (see ArbitrageExecutor.java for details on this computation).

## Building and running demo
The code in master does not place any trades but prints trades for profitable paths to the console. It assumes a large balance available to trade in all currencies on all exchanges. To run the demo:
```
mvn install
java -jar target/CryptoArbitrage-0.0.1-SNAPSHOT.jar
```
Note: CEXIO and some other exchanges require API keys even for market data. See below for instructions on configuring API keys.

## Configuring exchanges
The exchanges to trade on are configured in App.java. The more exchanges are added, the higher the chance of finding a profitable path but the higher the latency incurred when searching for profitable paths. A reasonable initial set of exchanges is provided with notes on others tried. To add a new exchange:
1. Instantiate the exchange using either the StreamingExchangeFactor (if supported in xchange-stream) or ExchangeFactory (if only polling interaction supported by xchange).
2. Add to exchangeCollection array to ensure that API keys are read from the json file and configured.
3. Add exchange to exchangeSubsets list. Wrap in StreamingExchangeAdapter if the exchange is created from an xchange-stream API, and in PollingExchangeAdapter if created via a normal xchange polling exchange.
4. Compile and run. There will likely be messages printed to console about unrecognized currency pairs, missing fees etc. These are bugs or missing features in xchange / xchange-stream and can be fixed or added by submitted changes to these projects.

## Trading Quick Start
To get set up for trading, perform the following steps:
1. Update APIKeys.json with your API keys for the exchanges that you plan to trade (see existing configs for example. They default API keys have been deactivated.)
2. Uncomment the commented block in ConcreteCurrencyBalanceDS.java and remove the initial return. This code will query your balance in a currency to be traded during computation of max loop quantity.
3. Uncomment call to placeOrders in AribtrageExecutor.onOrderBookAnalysisComplete. This code will place limit orders for the trades printed to stdout.

## Why open source?
I am no longer able to use this project per contract with my employer, but hope other people in the crypto trading community can make use of it.