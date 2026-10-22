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
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            Pageable pageable) {

        // Spring Data's Pageable resolver swallows a non-numeric page/size and
        // silently falls back to its default instead of failing the request, so an
        // invalid value has to be caught here before it's used.
        requireNumericIfPresent("page", page);
        requireNumericIfPresent("size", size);

        return bookService.filterBooks(bookFilter, pageable);
    }

    private void requireNumericIfPresent(String paramName, String value) {
        if (value == null) {
            return;
        }
        try {
            Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid " + paramName + " parameter");
        }
    }

}
