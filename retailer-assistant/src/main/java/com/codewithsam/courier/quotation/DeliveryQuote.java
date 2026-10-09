package com.codewithsam.courier.quotation;

import java.math.BigDecimal;

/** A courier's delivery price in GBP. Not to be confused with the fee for the quote. */
public record DeliveryQuote(String courier, BigDecimal price, String currency, int days) {
}
