package BenTrapani.CryptoArbitrage;

import java.math.BigDecimal;
import java.util.Hashtable;

import org.knowm.xchange.currency.Currency;

public class OrderGraph {
	
	public static class GraphEdgeKey {
		public final String exchangeName;
		public final Currency destCurrency;
		public final boolean isBuy;
		
		public GraphEdgeKey(String exchangeName, Currency destCurrency, boolean isBuy) {
			this.exchangeName = exchangeName;
			this.destCurrency = destCurrency;
			this.isBuy = isBuy;
		}
		
		@Override
		public int hashCode() {
			int exchangeHashCode = exchangeName.hashCode();
			int currencyHashCode = destCurrency.hashCode();
			int hash = 59 * exchangeHashCode + 29 * currencyHashCode + (this.isBuy ? 7 : 0);
			return hash;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final GraphEdgeKey other = (GraphEdgeKey) obj;
			return exchangeName.compareTo(other.exchangeName) == 0 
					&& destCurrency.equals(other.destCurrency) 
					&& isBuy == other.isBuy;
		}
		
	}
	public static class GraphEdgeValue {
		public BigDecimal quantity;
		public BigDecimal price;
		public GraphEdgeValue(BigDecimal quantity, BigDecimal price) {
			this.quantity = quantity;
			this.price = price;
		}
		@Override
		public int hashCode() {
			int quantityHashCode = quantity.hashCode();
			int priceHashCode = price.hashCode();
			int hash = 59 * quantityHashCode + priceHashCode;
			return hash;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final GraphEdgeValue other = (GraphEdgeValue) obj;
			return other.quantity.equals(quantity) && other.price.equals(price);
		}
	}
	
	private static class MutableCurrencyPair {
		public Currency source;
		public Currency dest;
		public MutableCurrencyPair(Currency counter, Currency base, boolean isBuy) {
			if (isBuy) {
				source = counter;
				dest = base;
			}else {
				source = base;
				dest = counter;
			}
		}
	}
	
	//Coarse locking on graphSet is used to avoid data races when updating graph
	private Hashtable<Currency, Hashtable<GraphEdgeKey, GraphEdgeValue>> graphSet = new Hashtable<Currency, Hashtable<GraphEdgeKey, GraphEdgeValue>>();
	
	//Call this after clearing all edges for an exchange that received an update. Add edges for all orders.
	public void addOrUpdateEdge(Currency counter, 
			Currency base, 
			String exchangeName, 
			boolean isBuyOrder,
			BigDecimal quantity, 
			BigDecimal price) {
		
		MutableCurrencyPair currencyPair = new MutableCurrencyPair(counter, base, isBuyOrder);
		GraphEdgeKey newKey = new GraphEdgeKey(exchangeName, currencyPair.dest, isBuyOrder);
		GraphEdgeValue newValue = new GraphEdgeValue(quantity, price);
		
		synchronized(graphSet) {
			if (graphSet.containsKey(currencyPair.source)) {
				Hashtable<GraphEdgeKey, GraphEdgeValue> edgesHere = graphSet.get(currencyPair.source);
				if (edgesHere.containsKey(newKey)) {
					// Edge already exists, just update it.
					GraphEdgeValue valueForEdge = edgesHere.get(newKey);
					valueForEdge.price = price;
					valueForEdge.quantity = quantity;
				}else {
					edgesHere.put(newKey, newValue);
				}
			}else {
				Hashtable<GraphEdgeKey, GraphEdgeValue> newEdges = new Hashtable<GraphEdgeKey, GraphEdgeValue>();
				newEdges.put(newKey, newValue);
				graphSet.put(currencyPair.source, newEdges);
			}
		}
	}
	
	public boolean removeEdge(Currency counter, 
			Currency base,
			String exchangeName,
			boolean isBuy) {
		MutableCurrencyPair mutablePair = new MutableCurrencyPair(counter, base, isBuy);
		synchronized(graphSet) { 
			if (graphSet.containsKey(mutablePair.source)) {
				GraphEdgeKey curKey = new GraphEdgeKey(exchangeName, mutablePair.dest, isBuy);
				Hashtable<GraphEdgeKey, GraphEdgeValue> edgesHere = graphSet.get(mutablePair.source);
				if (edgesHere.containsKey(curKey)){
					edgesHere.remove(curKey);
					return true;
				}
			}
		}
		return false;
	}
	
	@SuppressWarnings("unchecked")
	public Hashtable<GraphEdgeKey, GraphEdgeValue> getEdges(Currency source) {
		synchronized(graphSet){
			Hashtable<GraphEdgeKey, GraphEdgeValue> edgesForCurrency = graphSet.get(source);
			if (edgesForCurrency == null || edgesForCurrency.size() == 0) {
				return null;
			}
			return (Hashtable<GraphEdgeKey, GraphEdgeValue>) edgesForCurrency.clone();
		}
	}
}
