package BenTrapani.CryptoArbitrage;

import java.math.BigDecimal;

public class CryptoConfigs {
	public static final int decimalScale = 20;
	public static final BigDecimal decimalRoundAdjustDigit = BigDecimal.ONE.divide(BigDecimal.TEN.pow(decimalScale - 3),
			decimalScale, BigDecimal.ROUND_DOWN);
}
