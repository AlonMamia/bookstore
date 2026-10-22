package com.alon.bookstore.book;

import com.alon.bookstore.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Other suites (BookSpecificationIT, FlywayMigrationIT) assert exact counts against
 * the Flyway-seeded 50-book catalog and may share this test's Spring context/container,
 * so every book inserted here is cleaned up afterward to avoid leaking into those counts.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BookRepositoryIT {

    @Autowired
    private BookRepository bookRepository;

    private Long savedBookId;

    @AfterEach
    void cleanUp() {
        if (savedBookId != null) {
            bookRepository.deleteById(savedBookId);
        }
    }

    @Test
    void savingASecondBookWithTheSameIsbn_violatesTheUniqueConstraint() {
        // isbn is VARCHAR(20); keep well under that.
        String isbn = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        // saveAndFlush (not save) forces the INSERT - and the unique-constraint check -
        // to happen inside this call, instead of being deferred to whenever the
        // persistence context would otherwise flush.
        savedBookId = bookRepository.saveAndFlush(newBook(isbn)).getId();

        assertThatThrownBy(() -> bookRepository.saveAndFlush(newBook(isbn)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Book newBook(String isbn) {
        Book book = new Book();
        book.setTitle("Test Book");
        book.setIsbn(isbn);
        book.setPrice(new BigDecimal("10.00"));
        book.setStockQuantity(1);
        book.setAuthor("Test Author");
        book.setCategory("TEST");
        return book;
    }
}
