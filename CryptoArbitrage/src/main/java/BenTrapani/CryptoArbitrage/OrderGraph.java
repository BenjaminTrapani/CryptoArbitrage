package BenTrapani.CryptoArbitrage;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Hashtable;

import org.knowm.xchange.currency.Currency;

public class OrderGraph implements Cloneable {
	
	public static class TwoSidedGraphEdge {
		public final Currency sourceCurrency;
		public final GraphEdge graphEdge;
		public TwoSidedGraphEdge(Currency sourceCurrency, GraphEdge graphEdge) {
			this.sourceCurrency = sourceCurrency;
			this.graphEdge = graphEdge;
		}
		
		@Override
		public int hashCode() {
			final int sourceCurrencyHashCode = sourceCurrency.hashCode();
			final int graphEdgeHashCode = graphEdge.hashCode();
			final int hash = 2039 * sourceCurrencyHashCode + graphEdgeHashCode;
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
			final TwoSidedGraphEdge other = (TwoSidedGraphEdge) obj;
			return sourceCurrency.equals(other.sourceCurrency)
					&& graphEdge.equals(other.graphEdge);
		}
		
		@Override
		public String toString() {
			String result = "sourceCurrency:" + sourceCurrency.toString() + graphEdge.toString();
			return result;
		}
	}
	
	protected static class GraphEdge {
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
			this.ratio = isBuy ? BigDecimal.ONE.divide(price, CryptoConfigs.decimalScale, BigDecimal.ROUND_DOWN) : price;
		}
		
		@Override
		public String toString() {
			String result = " Exchange:" + exchangeName;
			result += " dest:" + destCurrency;
			result += " buy:" + isBuy;
			result += " quantity:" + quantity.toString();
			result += " price:" + price.toString();
			result += " ratio:" + ratio.toString();
			return result;
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
		public final Currency source;
		public final Currency dest;
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
	private Hashtable<Currency, HashSet<GraphEdge>> graphSet;
	
	public OrderGraph(){
		graphSet = new Hashtable<Currency, HashSet<GraphEdge>>();
	}
	public OrderGraph(Hashtable<Currency, HashSet<GraphEdge>> graphSet) {
		if (graphSet == null) {
			throw new IllegalArgumentException("initial graph set cannot be null in copy constructor for OrderGraph");
		}
		this.graphSet = graphSet;
	}
	
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

	public HashSet<TwoSidedGraphEdge> getEdges(Currency source) {
		synchronized(graphSet){
			HashSet<GraphEdge> edgesForCurrency = graphSet.get(source);
			if (edgesForCurrency == null || edgesForCurrency.size() == 0) {
				return null;
			}
			
			HashSet<TwoSidedGraphEdge> result = new HashSet<TwoSidedGraphEdge>();
			for (GraphEdge graphEdge: edgesForCurrency) {
				result.add(new TwoSidedGraphEdge(source, graphEdge));
			}
			return result;
		}
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public Object clone() {
		Hashtable<Currency, HashSet<GraphEdge>> graphSetDup = new Hashtable<Currency, HashSet<GraphEdge>>();
		synchronized(graphSet) {
			for (Currency k: graphSet.keySet()) {
				graphSetDup.put(k, (HashSet<GraphEdge>)graphSet.get(k).clone());
			}
		}
		return new OrderGraph(graphSetDup);
	}
}
