package BenTrapani.CryptoArbitrage;

import static org.junit.Assert.*;

import java.math.BigInteger;

import org.junit.Test;

public class FractionTest {
	
	@Test
	public void testInitAndReduce() {
		Fraction longInit = new Fraction(1025);
		assertEquals(BigInteger.valueOf(1025), longInit.numerator);
		assertEquals(BigInteger.ONE, longInit.denominator);
		
		Fraction bigIntInit = new Fraction(BigInteger.valueOf(20145));
		assertEquals(BigInteger.valueOf(20145), bigIntInit.numerator);
		assertEquals(BigInteger.ONE, bigIntInit.denominator);
		
		Fraction unreduceableInit = new Fraction(BigInteger.valueOf(1284018), BigInteger.valueOf(12839));
		assertEquals(BigInteger.valueOf(1284018), unreduceableInit.numerator);
		assertEquals(BigInteger.valueOf(12839), unreduceableInit.denominator);
		
		Fraction reduceableInit = new Fraction(BigInteger.valueOf(123821120), BigInteger.valueOf(12845));
		assertEquals(BigInteger.valueOf(123821120).divide(BigInteger.valueOf(5)), reduceableInit.numerator);
		assertEquals(BigInteger.valueOf(12845).divide(BigInteger.valueOf(5)), reduceableInit.denominator);
		
		Fraction reduceableLargeDenom = new Fraction(BigInteger.valueOf(-5), BigInteger.valueOf(25));
		assertEquals(BigInteger.valueOf(-1), reduceableLargeDenom.numerator);
		assertEquals(BigInteger.valueOf(5), reduceableLargeDenom.denominator);
	}
	
	@Test
	public void testMultiplicationAndDivision() {
		Fraction arg1 = new Fraction(BigInteger.valueOf(1024), BigInteger.valueOf(1280));
		Fraction arg2 = new Fraction(BigInteger.valueOf(51), BigInteger.valueOf(101));
		Fraction product1 = arg1.multiply(arg2);
		Fraction product2 = arg2.multiply(arg1);
		assertEquals(product1, product2);
		// Result is 1024 * 51 / 1280 * 101 = 52,224 / 129,280
		// GCD = 256, so result is 204 / 505
		assertEquals(BigInteger.valueOf(204), product1.numerator);
		assertEquals(BigInteger.valueOf(505), product1.denominator);
		
		Fraction div1 = product1.divide(arg2);
		Fraction div2 = product2.divide(arg1);
		assertEquals(arg1, div1);
		assertEquals(arg2, div2);
	}
	
	@Test
	public void testAdditionAndSubtraction() {
		Fraction arg1 = new Fraction(BigInteger.valueOf(15), BigInteger.valueOf(23));
		Fraction arg2 = new Fraction(BigInteger.valueOf(23), BigInteger.valueOf(37));
		// arg1 = 15 * 37 / 851
		// arg2 = 23 * 23 / 851
		
		Fraction arg1MArg2 = arg1.subtract(arg2);
		assertEquals(new Fraction(BigInteger.valueOf(26), BigInteger.valueOf(851)), arg1MArg2);
		
		Fraction shouldBeArg1 = arg2.add(arg1MArg2);
		assertEquals(shouldBeArg1, arg1);
		
		Fraction sum1 = arg1.add(arg2);
		Fraction sum2 = arg2.add(arg1);
		assertEquals(sum1, sum2);
		assertEquals(new Fraction(BigInteger.valueOf(15 * 37 + 23 * 23), BigInteger.valueOf(851)), sum2);
	}
	
	@Test
	public void testEquals() {
		Fraction a = new Fraction(BigInteger.valueOf(6), BigInteger.valueOf(42));
		Fraction b = new Fraction(BigInteger.ONE, BigInteger.valueOf(7));
		assertEquals(a, b);
		Fraction addB = b.add(new Fraction(1));
		assertEquals(false, a.equals(addB));
		Fraction negB = b.multiply(new Fraction(-1));
		assertEquals(false, a.equals(negB));
		Fraction negA = b.multiply(new Fraction(-1));
		assertEquals(negB, negA);
	}
	
	@Test
	public void testCompareTo() {
		Fraction a = new Fraction(BigInteger.valueOf(7), BigInteger.valueOf(25));
		Fraction b = new Fraction(BigInteger.valueOf(28), BigInteger.valueOf(32));
		Fraction b2 = new Fraction(BigInteger.valueOf(7), BigInteger.valueOf(8));
		assertEquals(1, b.compareTo(a));
		assertEquals(-1, a.compareTo(b));
		assertEquals(0, b.compareTo(b2));
		assertEquals(0, b2.compareTo(b));
		
		Fraction negb = b.multiply(new Fraction(-1));
		assertEquals(-1, negb.compareTo(a));
		assertEquals(1, a.compareTo(negb));
		Fraction negA = a.multiply(new Fraction(-1));
		assertEquals(-1, negb.compareTo(negA));
		assertEquals(1, negA.compareTo(negb));
	}
}
