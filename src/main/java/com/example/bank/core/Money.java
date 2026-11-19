package com.example.bank.core;

import java.math.BigDecimal;
import java.util.Objects;

/** An exact, signed amount in cents. Transaction-specific positivity is validated by callers. */
public record Money(long cents) implements Comparable<Money> {
    private static final BigDecimal MIN_AMOUNT = BigDecimal.valueOf(Long.MIN_VALUE, 2);
    private static final BigDecimal MAX_AMOUNT = BigDecimal.valueOf(Long.MAX_VALUE, 2);

    public static Money fromBigDecimal(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("Amount must have at most two decimal places");
        }
        if (amount.signum() == 0) {
            return new Money(0);
        }
        // Check before shifting: movePointRight can expand a huge negative scale into a BigInteger.
        if (amount.compareTo(MIN_AMOUNT) < 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new ArithmeticException("Amount is outside the range of long cents");
        }
        return new Money(amount.movePointRight(2).longValueExact());
    }

    public BigDecimal toBigDecimal() {
        return BigDecimal.valueOf(cents, 2);
    }

    public Money add(Money other) {
        return new Money(Math.addExact(cents, other.cents));
    }

    public Money subtract(Money other) {
        return new Money(Math.subtractExact(cents, other.cents));
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(cents, other.cents);
    }
}
