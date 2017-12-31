package BenTrapani.CryptoArbitrage;

import java.util.Hashtable;

import org.knowm.xchange.currency.Currency;

public class OrderGraph {
	
	public class GraphEdgeKey {
		public final String exchangeName;
		public final Currency destCurrency;
		public final boolean isBuy;
		public GraphEdgeKey(String exchangeName, Currency destCurrency, boolean isBuy) {
			this.exchangeName = exchangeName;
			this.destCurrency = destCurrency;
			this.isBuy = isBuy;
		}
	}
	public class GraphEdgeValue {
		public double quantity;
		public double price;
		public GraphEdgeValue(double quantity, double price) {
			this.quantity = quantity;
			this.price = price;
		}
	}
	
	//Coarse locking on graphSet is used to avoid data races when updating graph
	private Hashtable<Currency, Hashtable<GraphEdgeKey, GraphEdgeValue>> graphSet;
	
	//Call this after clearing all edges for an exchange that received an update. Add edges for all orders.
	public void addOrUpdateEdge(Currency source, 
			Currency dest, 
			String exchangeName, 
			boolean isBuyOrder,
			double quantity, 
			double price) {
		GraphEdgeKey newKey = new GraphEdgeKey(exchangeName, dest, isBuyOrder);
		GraphEdgeValue newValue = new GraphEdgeValue(quantity, price);
		synchronized(graphSet) {
			if (graphSet.contains(source)) {
				Hashtable<GraphEdgeKey, GraphEdgeValue> edgesHere = graphSet.get(source);
				if (edgesHere.contains(newKey)) {
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
				graphSet.put(source, newEdges);
			}
		}
	}
	
	public boolean removeEdge(Currency source, 
			Currency dest,
			String exchangeName,
			boolean isBuy) {
		synchronized(graphSet) { 
			if (graphSet.containsKey(source)) {
				GraphEdgeKey curKey = new GraphEdgeKey(exchangeName, dest, isBuy);
				Hashtable<GraphEdgeKey, GraphEdgeValue> edgesHere = graphSet.get(source);
				if (edgesHere.contains(curKey)){
					edgesHere.remove(curKey);
					return true;
				}
			}
		}
		return false;
	}
}
