package com.alon.bookstore.book;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class BookSpecification {
    public static Specification<Book> withFilters(
          BookFilter bookFilter
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (bookFilter.query() != null && !bookFilter.query().isBlank()) {
                String pattern =
                        "%" + bookFilter.query().trim().toLowerCase() + "%";

                Predicate authorMatches = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("author")),
                        pattern
                );

                Predicate titleMatches = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("title")),
                        pattern
                );

                Predicate descriptionMatches = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("description")),
                        pattern
                );

                predicates.add(criteriaBuilder.or(authorMatches, titleMatches, descriptionMatches));
            }

            if (bookFilter.category() != null && !bookFilter.category().isBlank()) {
                String pattern =
                        "%" + bookFilter.category().trim().toLowerCase() + "%";

                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("category")),
                        pattern
                ));
            }

            if (bookFilter.minPrice() != null) {
                predicates.add(
                        criteriaBuilder.greaterThanOrEqualTo(root.get("price"), bookFilter.minPrice())
                );
            }

            if (bookFilter.maxPrice() != null &&  bookFilter.maxPrice().compareTo(BigDecimal.ZERO) > 0) {
                predicates.add(
                        criteriaBuilder.lessThanOrEqualTo(root.get("price"), bookFilter.maxPrice())
                );
            }
            if (bookFilter.availableOnly()) {
                predicates.add(criteriaBuilder.greaterThan(root.get("stockQuantity"), 0));
            }

            return criteriaBuilder.and(
                    predicates.toArray(Predicate[]::new)
            );
        };
    }
}
