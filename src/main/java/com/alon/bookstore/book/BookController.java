package com.alon.bookstore.book;

import com.alon.bookstore.shared.model.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    @GetMapping("")
    public PageResponse<BookDtoOut> searchBooks(
            @ModelAttribute BookFilter bookFilter,
            Pageable pageable) {

        return bookService.filterBooks(bookFilter, pageable);
    }





}
