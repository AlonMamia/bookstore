package com.alon.bookstore.book;

import com.alon.bookstore.shared.model.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;
    private final BookMapper bookMapper;

    public PageResponse<BookDtoOut> filterBooks(
            BookFilter bookFilter,
            Pageable pageable
    ) {

        Specification<Book> bookSpecification = BookSpecification.withFilters(bookFilter);

        Page<Book> booksPage = bookRepository.findAll(bookSpecification, pageable);

        Page<BookDtoOut> bookDtoOutPage = booksPage.map(bookMapper::toDto);

        return PageResponse.from(bookDtoOutPage);

    }


}
