package com.alon.bookstore.book;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BookMapperTest {

    private final BookMapper mapper = new BookMapperImpl();

    @Test
    void toDto_copiesEveryFieldWithoutLoss() {
        Book book = new Book();
        book.setId(1L);
        book.setTitle("Clean Code");
        book.setDescription("A handbook of agile craftsmanship");
        book.setIsbn("9780132350884");
        book.setPrice(new BigDecimal("45.99"));
        book.setStockQuantity(7);
        book.setAuthor("Robert C. Martin");
        book.setCategory("TECHNOLOGY");

        BookDtoOut dto = mapper.toDto(book);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getTitle()).isEqualTo("Clean Code");
        assertThat(dto.getDescription()).isEqualTo("A handbook of agile craftsmanship");
        assertThat(dto.getIsbn()).isEqualTo("9780132350884");
        assertThat(dto.getPrice()).isEqualTo(new BigDecimal("45.99"));
        assertThat(dto.getStockQuantity()).isEqualTo(7);
        assertThat(dto.getAuthor()).isEqualTo("Robert C. Martin");
        assertThat(dto.getCategory()).isEqualTo("TECHNOLOGY");
    }
}
