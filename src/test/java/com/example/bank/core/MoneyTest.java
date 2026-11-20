package com.example.bank.core;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyTest {
    @Test
    void parsesAndFormatsExactDecimalValues() {
        assertEquals(new Money(0), Money.fromBigDecimal(new BigDecimal("0")));
        assertEquals(new Money(1230), Money.fromBigDecimal(new BigDecimal("12.30")));
        assertEquals(new Money(-1), Money.fromBigDecimal(new BigDecimal("-0.01")));
        assertEquals(new Money(100000), Money.fromBigDecimal(new BigDecimal("1E+3")));
        assertEquals(new BigDecimal("-12.30"), new Money(-1230).toBigDecimal());
        assertEquals(new BigDecimal("0.00"), new Money(0).toBigDecimal());
        assertEquals(new Money(Long.MAX_VALUE), Money.fromBigDecimal(new BigDecimal("92233720368547758.07")));
        assertEquals(new Money(Long.MIN_VALUE), Money.fromBigDecimal(new BigDecimal("-92233720368547758.08")));
    }

    @Test
    void rejectsExcessScaleEvenWhenTrailingZerosAndOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> Money.fromBigDecimal(new BigDecimal("1.000")));
        assertThrows(IllegalArgumentException.class, () -> Money.fromBigDecimal(new BigDecimal("0.001")));
        assertThrows(ArithmeticException.class, () -> Money.fromBigDecimal(new BigDecimal("92233720368547758.08")));
        assertThrows(ArithmeticException.class, () -> Money.fromBigDecimal(new BigDecimal("-92233720368547758.09")));
        assertThrows(ArithmeticException.class, () -> Money.fromBigDecimal(new BigDecimal("1E+100")));
        assertThrows(NullPointerException.class, () -> Money.fromBigDecimal(null));
    }

    @Test
    void checksExtremeExponentsBeforeExpandingAndAcceptsExtremeScaleZero() {
        for (String value : new String[]{"1E+1000000000", "-1E+1000000000",
                "1E+100000000", "-1E+100000000"}) {
            ArithmeticException exception = assertThrows(ArithmeticException.class,
                    () -> Money.fromBigDecimal(new BigDecimal(value)));
            assertEquals("Amount is outside the range of long cents", exception.getMessage());
        }
        assertEquals(new Money(0), Money.fromBigDecimal(new BigDecimal(BigInteger.ZERO, Integer.MIN_VALUE)));
        assertEquals(new Money(0), Money.fromBigDecimal(new BigDecimal("0E+1000000000")));
        assertThrows(IllegalArgumentException.class, () -> Money.fromBigDecimal(new BigDecimal("0.000")));
    }

    @Test
    void arithmeticAndComparisonAreExactAndOverflowSafe() {
        assertEquals(new Money(50), new Money(100).add(new Money(-50)));
        assertEquals(new Money(-150), new Money(-100).subtract(new Money(50)));
        assertEquals(new Money(42), new Money(42).add(new Money(0)));
        assertTrue(new Money(Long.MIN_VALUE).compareTo(new Money(Long.MAX_VALUE)) < 0);
        assertEquals(0, new Money(42).compareTo(new Money(42)));
        assertThrows(ArithmeticException.class, () -> new Money(Long.MAX_VALUE).add(new Money(1)));
        assertThrows(ArithmeticException.class, () -> new Money(Long.MIN_VALUE).add(new Money(-1)));
        assertThrows(ArithmeticException.class, () -> new Money(Long.MIN_VALUE).subtract(new Money(1)));
        assertThrows(ArithmeticException.class, () -> new Money(Long.MAX_VALUE).subtract(new Money(-1)));
    }

    @Test
    void roundTripsFullRangeOfCents() {
        for (long cents : new long[]{Long.MIN_VALUE, -10001, -1, 0, 1, 10001, Long.MAX_VALUE}) {
            assertEquals(new Money(cents), Money.fromBigDecimal(new Money(cents).toBigDecimal()));
        }
    }

    @Test
    void arithmeticAndDecimalRoundTripsPreserveRandomCentsExactly() {
        Random random = new Random(0xB4A7L);
        for (int i = 0; i < 1_000; i++) {
            long first = random.nextLong(-1_000_000_000_000L, 1_000_000_000_000L);
            long second = random.nextLong(-1_000_000_000_000L, 1_000_000_000_000L);
            Money amount = new Money(first);
            Money adjustment = new Money(second);

            assertEquals(amount, amount.add(adjustment).subtract(adjustment));
            assertEquals(amount, Money.fromBigDecimal(amount.toBigDecimal()));
        }
    }
}
