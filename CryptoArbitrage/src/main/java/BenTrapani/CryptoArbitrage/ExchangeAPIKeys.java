package BenTrapani.CryptoArbitrage;

import java.net.URL;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ExchangeAPIKeys {
	public static class APIKeyPair {
		public final String publicKey;
		public final String privateKey;
		
		public APIKeyPair(@JsonProperty("public") String publicKey, 
				@JsonProperty("secret") String privateKey) {
			this.publicKey = publicKey;
			this.privateKey = privateKey;
		}	
	}
	
	public static class ExchangeKeyPair {
		public final String exchangeName;
		public final APIKeyPair apiKeyPair;
		public final Map<String, Object> otherProperties;
		public ExchangeKeyPair(@JsonProperty("exchange") String exchangeName, 
				@JsonProperty("keys") APIKeyPair apiKeyPair,
				@JsonProperty("other_properties") Map<String, Object> otherProperties) {
			this.exchangeName = exchangeName;
			this.apiKeyPair = apiKeyPair;
			this.otherProperties = otherProperties;
		}
	}
	
	private Map<String, ExchangeKeyPair> exchangeNameToKeyPairMap = new HashMap<String, ExchangeKeyPair>();
	
	@SuppressWarnings("unchecked")
	public ExchangeAPIKeys(String apiKeyFileName) throws JsonParseException, JsonMappingException, IOException {
		ObjectMapper mapper = new ObjectMapper();
		InputStream is = ExchangeKeyPair.class.getResourceAsStream(apiKeyFileName);
		ExchangeKeyPair[] exchangeKeyPairs = mapper.readValue(is, ExchangeKeyPair[].class);
		for (ExchangeKeyPair keyPair : exchangeKeyPairs) {
			exchangeNameToKeyPairMap.put(keyPair.exchangeName, keyPair);
		}
	}
	
	public ExchangeKeyPair getKeyPairForExchange(String exchangeName) {
		ExchangeKeyPair keyPair = exchangeNameToKeyPairMap.get(exchangeName);
		if (keyPair == null) {
			throw new IllegalStateException("Cannot find API keys for exchange " + exchangeName);
		}
		return keyPair;
	}
}
