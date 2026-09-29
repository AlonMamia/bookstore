package com.alon.bookstore.book;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BookDtoOut {

    private Long id;

    private String title;

    private String description;

    private String isbn;

    private BigDecimal price;

    private Integer stockQuantity;

    private String author;

    private String category;

}
