package com.alon.bookstore.book;

import com.alon.bookstore.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link BookSpecification} against the real, Flyway-seeded catalog
 * (V1.5__add_50_books.sql: 10 categories x 5 books, exactly one out-of-stock
 * book per category) on a real PostgreSQL instance, rather than mocking the
 * JPA Criteria API.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BookSpecificationIT {

    @Autowired
    private BookRepository bookRepository;

    private final List<Long> isolatedFixtureIds = new ArrayList<>();

    @AfterEach
    void cleanUpIsolatedFixtures() {
        if (!isolatedFixtureIds.isEmpty()) {
            bookRepository.deleteAllByIdInBatch(isolatedFixtureIds);
            isolatedFixtureIds.clear();
        }
    }

    @Test
    void noFilters_returnsFullSeededCatalog() {
        Page<Book> page = search(new BookFilter(null, null, null, null, false), PageRequest.of(0, 100));

        assertThat(page.getTotalElements()).isEqualTo(50);
    }

    @Test
    void query_matchesTitleCaseInsensitively() {
        // Genuinely different capitalization from the stored title ("The Dragon's
        // Heir"): a case-sensitive LIKE would not match this, so a match here proves
        // the comparison folds case rather than happening to agree by coincidence.
        Page<Book> page = search(new BookFilter("DRAGON", null, null, null, false), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Book::getTitle)
                .containsExactly("The Dragon's Heir");
    }

    @Test
    void query_matchesAuthorAcrossMultipleBooks() {
        Page<Book> page = search(new BookFilter("Maya Stone", null, null, null, false), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Book::getTitle)
                .containsExactlyInAnyOrder("The Silent Harbor", "Echoes at Midnight");
    }

    @Test
    void query_withNoMatches_returnsEmptyPage() {
        Page<Book> page = search(new BookFilter("no-such-book-exists", null, null, null, false), PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        assertThat(page.isFirst()).isTrue();
        assertThat(page.isLast()).isTrue();
    }

    @Test
    void category_filtersToExactCategoryOnly() {
        Page<Book> page = search(new BookFilter(null, "MYSTERY", null, null, false), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getContent()).allSatisfy(book -> assertThat(book.getCategory()).isEqualTo("MYSTERY"));
    }

    @Test
    void category_isCaseInsensitive() {
        Page<Book> page = search(new BookFilter(null, "mystery", null, null, false), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(5);
    }

    @Test
    void category_withUnknownValue_returnsEmptyPage() {
        Page<Book> page = search(new BookFilter(null, "NOT_A_REAL_CATEGORY", null, null, false), PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void priceRange_includesBoundariesAndExcludesOutsideValues() {
        BookFilter filter = new BookFilter(null, null, new BigDecimal("100.00"), new BigDecimal("120.00"), false);
        Page<Book> page = search(filter, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Book::getTitle)
                .containsExactlyInAnyOrder(
                        "Forest of Forgotten Gods",
                        "Children of the Last Sun",
                        "The Social Brain",
                        "Empires of the Ancient Sea",
                        "Europe Between Wars",
                        "Strategy Without Noise"
                );
    }

    @Test
    void priceRange_atExactBoundaries_isInclusive() {
        // The seeded catalog has no book priced at exactly 100.00 or 120.00 (see
        // V1.5__add_50_books.sql), so priceRange_includesBoundariesAndExcludesOutsideValues
        // above never actually exercises the >=/<= boundary itself, only values well
        // inside/outside the range. Isolated fixtures here pin down that boundary
        // behavior precisely, and are cleaned up afterward (see cleanUpIsolatedFixtures)
        // so they don't change the seeded-catalog counts other tests rely on.
        String category = "BOUNDARY_TEST_" + java.util.UUID.randomUUID();
        trackNew(category, "At min boundary", new BigDecimal("100.00"));
        trackNew(category, "At max boundary", new BigDecimal("120.00"));
        trackNew(category, "Just below min", new BigDecimal("99.99"));
        trackNew(category, "Just above max", new BigDecimal("120.01"));

        BookFilter filter = new BookFilter(
                null, category, new BigDecimal("100.00"), new BigDecimal("120.00"), false);
        Page<Book> page = search(filter, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Book::getTitle)
                .containsExactlyInAnyOrder("At min boundary", "At max boundary");
    }

    private void trackNew(String category, String title, BigDecimal price) {
        Book book = new Book();
        book.setTitle(title);
        book.setIsbn(java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        book.setPrice(price);
        book.setStockQuantity(1);
        book.setAuthor("Boundary Test Author");
        book.setCategory(category);
        isolatedFixtureIds.add(bookRepository.saveAndFlush(book).getId());
    }

    @Test
    void maxPriceOfZero_isTreatedAsUnbounded() {
        // BookFilter.maxPrice() <= 0 is treated as "no upper bound" by BookSpecification.
        BookFilter filter = new BookFilter(null, "MYSTERY", null, BigDecimal.ZERO, false);
        Page<Book> page = search(filter, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(5);
    }

    @Test
    void availableOnly_excludesOutOfStockBooks() {
        Page<Book> page = search(new BookFilter(null, null, null, null, true), PageRequest.of(0, 100));

        assertThat(page.getTotalElements()).isEqualTo(40);
        assertThat(page.getContent()).allSatisfy(book -> assertThat(book.getStockQuantity()).isGreaterThan(0));
    }

    @Test
    void combinedFilters_applyEveryPredicateTogether() {
        BookFilter filter = new BookFilter(
                null, null, new BigDecimal("100.00"), new BigDecimal("120.00"), true);
        Page<Book> page = search(filter, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Book::getTitle)
                .containsExactlyInAnyOrder(
                        "Forest of Forgotten Gods",
                        "Children of the Last Sun",
                        "Empires of the Ancient Sea",
                        "Europe Between Wars"
                );
    }

    @Test
    void categoryAndPriceRange_narrowToTheIntersection() {
        BookFilter filter = new BookFilter(
                null, "HISTORY", new BigDecimal("100.00"), new BigDecimal("120.00"), false);
        Page<Book> page = search(filter, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Book::getTitle)
                .containsExactlyInAnyOrder("Empires of the Ancient Sea", "Europe Between Wars");
    }

    @Test
    void sorting_byPriceAscending_ordersWithinCategory() {
        Page<Book> page = search(
                new BookFilter(null, "MYSTERY", null, null, false),
                PageRequest.of(0, 10, Sort.by("price").ascending()));

        assertThat(page.getContent()).extracting(Book::getTitle)
                .containsExactly(
                        "A Case of Shadows",
                        "Echoes at Midnight",
                        "The Silent Harbor",
                        "The Last Witness",
                        "Murder on Cedar Street"
                );
    }

    @Test
    void pagination_splitsResultsAcrossPagesConsistently() {
        BookFilter filter = new BookFilter(null, "MYSTERY", null, null, false);
        Sort sort = Sort.by("price").ascending();

        Page<Book> firstPage = search(filter, PageRequest.of(0, 2, sort));
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
        assertThat(firstPage.isFirst()).isTrue();
        assertThat(firstPage.isLast()).isFalse();

        Page<Book> middlePage = search(filter, PageRequest.of(1, 2, sort));
        assertThat(middlePage.getContent()).hasSize(2);
        assertThat(middlePage.isFirst()).isFalse();
        assertThat(middlePage.isLast()).isFalse();

        Page<Book> lastPage = search(filter, PageRequest.of(2, 2, sort));
        assertThat(lastPage.getContent()).hasSize(1);
        assertThat(lastPage.isLast()).isTrue();

        List<String> allTitlesInOrder = List.of(
                firstPage.getContent().get(0).getTitle(),
                firstPage.getContent().get(1).getTitle(),
                middlePage.getContent().get(0).getTitle(),
                middlePage.getContent().get(1).getTitle(),
                lastPage.getContent().get(0).getTitle()
        );
        assertThat(allTitlesInOrder).containsExactly(
                "A Case of Shadows",
                "Echoes at Midnight",
                "The Silent Harbor",
                "The Last Witness",
                "Murder on Cedar Street"
        );
    }

    @Test
    void pagination_pageBeyondLastPage_returnsEmptyContentWithValidMetadata() {
        BookFilter filter = new BookFilter(null, "MYSTERY", null, null, false);

        Page<Book> page = search(filter, PageRequest.of(10, 5));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.isLast()).isTrue();
    }

    private Page<Book> search(BookFilter filter, PageRequest pageRequest) {
        return bookRepository.findAll(BookSpecification.withFilters(filter), pageRequest);
    }
}
