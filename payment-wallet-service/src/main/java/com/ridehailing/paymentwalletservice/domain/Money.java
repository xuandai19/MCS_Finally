package com.ridehailing.paymentwalletservice.domain;
import java.math.BigDecimal; import java.util.Currency;
public record Money(BigDecimal amount, Currency currency) {
 public Money { if(amount==null||currency==null) throw new IllegalArgumentException("amount and currency are required"); amount=amount.setScale(2,java.math.RoundingMode.HALF_UP); }
}
