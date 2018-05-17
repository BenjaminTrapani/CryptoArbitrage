package BenTrapani.CryptoArbitrage;

import java.math.BigDecimal;
import java.math.BigInteger;

/***
 * 
 * @author benjamintrapani
 *
 * Fraction class because standard library doesn't have one it seems...
 *
 */
public class Fraction implements Comparable<Fraction> {
	public final BigInteger numerator;
	public final BigInteger denominator;
	
	public Fraction(int value) {
		numerator = BigInteger.valueOf(value);
		denominator = BigInteger.ONE;
	}
	
	public Fraction(BigInteger val) {
		numerator = val;
		denominator = BigInteger.ONE;
	}
	
	public Fraction(BigInteger num, BigInteger denom) {
		BigIntPair reducedPair = computeReduced(num, denom);
		numerator = reducedPair.n1;
		denominator = reducedPair.n2;
	}
	
	public Fraction(BigDecimal val) {
		BigInteger unscaledValue = val.unscaledValue();
		BigInteger tempDenom = BigInteger.TEN.pow(val.scale());
		BigIntPair reducedPair = computeReduced(unscaledValue, tempDenom);
		numerator = reducedPair.n1;
		denominator = reducedPair.n2;
	}
	
	public BigDecimal convertToBigDecimal(int scale, int roundingMode) {
		return BigDecimal.valueOf(numerator.longValueExact())
				         .divide(BigDecimal.valueOf(denominator.longValueExact()), scale, roundingMode);
	}
	
	public Fraction multiply(Fraction other) {
		return new Fraction(other.numerator.multiply(numerator), other.denominator.multiply(denominator));
	}
	
	public Fraction divide(Fraction other) {
		return new Fraction(other.denominator.multiply(numerator), other.numerator.multiply(denominator));
	}
	
	private static class FractionPairWithCommonDenominator {
		public final BigInteger numerator1;
		public final BigInteger numerator2;
		public final BigInteger commonDenominator;
		
		public FractionPairWithCommonDenominator(final Fraction f1, final Fraction f2) {
			commonDenominator = f1.denominator.multiply(f2.denominator);
			BigInteger commonDenomFac1 = f2.denominator;
			BigInteger commonDenomFac2 = f1.denominator;
			numerator1 = f1.numerator.multiply(commonDenomFac1);
			numerator2 = f2.numerator.multiply(commonDenomFac2);
		}
	}
	public Fraction add(Fraction other) {
		FractionPairWithCommonDenominator fracPair = new FractionPairWithCommonDenominator(this, other);
		return new Fraction(fracPair.numerator1.add(fracPair.numerator2), fracPair.commonDenominator);
	}
	
	public Fraction subtract(Fraction other) {
		FractionPairWithCommonDenominator fracPair = new FractionPairWithCommonDenominator(this, other);
		return new Fraction(fracPair.numerator1.subtract(fracPair.numerator2), fracPair.commonDenominator);
	}
	
	private static BigInteger computeGCD(BigInteger arg1, BigInteger arg2) {
		BigInteger a = null;
		BigInteger b = null;
		if (arg1.compareTo(arg2) > 0) {
			a = arg1;
			b = arg2;
		} else {
			a = arg2;
			b = arg1;
		}
		
		while(b.compareTo(BigInteger.ZERO) != 0) {
			BigInteger temp = b;
			b = a.mod(b);
			a = temp;
		}
		return a;
	}
	
	private static class BigIntPair {
		public final BigInteger n1;
		public final BigInteger n2;
		public BigIntPair(BigInteger n1, BigInteger n2) {
			this.n1 = n1;
			this.n2 = n2;
		}
	}
	
	private static BigIntPair computeReduced(BigInteger tempNum, BigInteger tempDenom) {
		BigInteger gcd = computeGCD(tempNum.abs(), tempDenom.abs());
		BigInteger reducedNum = tempNum.divide(gcd);
		BigInteger reducedDenom = tempDenom.divide(gcd);
		return new BigIntPair(reducedNum, reducedDenom);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final Fraction other = (Fraction) obj;
		return numerator.equals(other.numerator) &&
				denominator.equals(other.denominator);
	}
	
	@Override
	public int hashCode() {
		return numerator.hashCode() + 
				denominator.hashCode() * 51;
	}
	
	@Override
	public int compareTo(Fraction o) {
		FractionPairWithCommonDenominator pair = new FractionPairWithCommonDenominator(this, o);
		return pair.numerator1.compareTo(pair.numerator2);
	}
}
