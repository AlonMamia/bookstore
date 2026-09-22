package com.alon.bookstore.book;

import java.math.BigDecimal;

public record BookFilter(
       String query,
       String category,
       BigDecimal minPrice,
       BigDecimal maxPrice,
       boolean availableOnly
) {
}
