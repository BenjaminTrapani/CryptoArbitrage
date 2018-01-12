package BenTrapani.CryptoArbitrage;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Hashtable;

import org.knowm.xchange.currency.Currency;

public class OrderGraph {
	
	public static class GraphEdge {
		public final String exchangeName;
		public final Currency destCurrency;
		public final boolean isBuy;
		public final BigDecimal quantity;
		public final BigDecimal price;
		public final BigDecimal ratio;
		
		public GraphEdge(String exchangeName, 
				Currency destCurrency, 
				boolean isBuy,
				BigDecimal quantity,
				BigDecimal price) {
			this.exchangeName = exchangeName;
			this.destCurrency = destCurrency;
			this.isBuy = isBuy;
			this.quantity = quantity;
			this.price = price;
			this.ratio = isBuy ? quantity.divide(price, CryptoConfigs.decimalScale, BigDecimal.ROUND_HALF_UP) : 
				price.divide(quantity, CryptoConfigs.decimalScale, BigDecimal.ROUND_HALF_UP);
		}
		
		@Override
		public int hashCode() {
			final int exchangeHashCode = exchangeName.hashCode();
			final int currencyHashCode = destCurrency.hashCode();
			final int quantityHashCode = quantity.hashCode();
			final int priceHashCode = price.hashCode();
			final int ratioHashCode = ratio.hashCode();
			final int hash = 59 * exchangeHashCode + 
					47 * currencyHashCode +
					197 * quantityHashCode + 
					163 * priceHashCode + 
					179 * ratioHashCode +
					(this.isBuy ? 797 : 0);
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
			final GraphEdge other = (GraphEdge) obj;
			return exchangeName.compareTo(other.exchangeName) == 0 
					&& destCurrency.equals(other.destCurrency) 
					&& isBuy == other.isBuy 
					&& quantity.equals(other.quantity)
					&& price.equals(other.price)
					&& ratio.equals(other.ratio);
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
	private Hashtable<Currency, HashSet<GraphEdge>> graphSet = new Hashtable<Currency, HashSet<GraphEdge>>();
	
	//Call this after clearing all edges for an exchange that received an update. Add edges for all orders.
	public void addEdge(Currency counter, 
			Currency base, 
			String exchangeName, 
			boolean isBuyOrder,
			BigDecimal quantity, 
			BigDecimal price) {
		
		MutableCurrencyPair currencyPair = new MutableCurrencyPair(counter, base, isBuyOrder);
		GraphEdge newEdge = new GraphEdge(exchangeName, currencyPair.dest, isBuyOrder, quantity, price);
		
		synchronized(graphSet) {
			if (!graphSet.containsKey(currencyPair.source)) {
				graphSet.put(currencyPair.source, new HashSet<GraphEdge>());
			}
			HashSet<GraphEdge> edgesHere = graphSet.get(currencyPair.source);
			edgesHere.add(newEdge);
		}
	}
	
	public boolean removeEdge(Currency counter, 
			Currency base,
			String exchangeName,
			boolean isBuy, 
			BigDecimal quantity,
			BigDecimal price) {
		MutableCurrencyPair mutablePair = new MutableCurrencyPair(counter, base, isBuy);
		synchronized(graphSet) { 
			if (graphSet.containsKey(mutablePair.source)) {
				HashSet<GraphEdge> edgesHere = graphSet.get(mutablePair.source);
				GraphEdge edgeToRemove = new GraphEdge(exchangeName, mutablePair.dest, isBuy, quantity, price);
				return edgesHere.remove(edgeToRemove);
			}
		}
		return false;
	}
	
	@SuppressWarnings("unchecked")
	public HashSet<GraphEdge> getEdges(Currency source) {
		synchronized(graphSet){
			HashSet<GraphEdge> edgesForCurrency = graphSet.get(source);
			if (edgesForCurrency == null || edgesForCurrency.size() == 0) {
				return null;
			}
			return (HashSet<GraphEdge>) edgesForCurrency.clone();
		}
	}
}
