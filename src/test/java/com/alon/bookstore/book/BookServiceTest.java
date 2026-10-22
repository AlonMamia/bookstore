package com.alon.bookstore.book;

import com.alon.bookstore.shared.model.PageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;
    @Mock
    private BookMapper bookMapper;

    private BookService bookService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        bookService = new BookService(bookRepository, bookMapper);
    }

    @Test
    void filterBooks_mapsEveryEntityToDto_andPreservesFirstPageMetadata() {
        BookFilter filter = new BookFilter("dragon", null, null, null, false);
        Pageable pageable = PageRequest.of(0, 10);

        Book firstBook = new Book();
        firstBook.setId(1L);
        firstBook.setTitle("The Dragon's Heir");

        Book secondBook = new Book();
        secondBook.setId(2L);
        secondBook.setTitle("Dragon's Descent");

        Page<Book> bookPage = new PageImpl<>(List.of(firstBook, secondBook), pageable, 2);

        BookDtoOut firstDto = new BookDtoOut();
        firstDto.setId(1L);
        firstDto.setTitle("The Dragon's Heir");

        BookDtoOut secondDto = new BookDtoOut();
        secondDto.setId(2L);
        secondDto.setTitle("Dragon's Descent");

        given(bookRepository.findAll(any(Specification.class), eq(pageable))).willReturn(bookPage);
        given(bookMapper.toDto(firstBook)).willReturn(firstDto);
        given(bookMapper.toDto(secondBook)).willReturn(secondDto);

        PageResponse<BookDtoOut> result = bookService.filterBooks(filter, pageable);

        // containsExactly (not just hasSize/contains) proves both entities were mapped,
        // in order - not just the first one, with the second silently dropped or mapped
        // to the same DTO.
        assertThat(result.content()).containsExactly(firstDto, secondDto);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.first()).isTrue();
        assertThat(result.last()).isTrue();
    }

    @Test
    void filterBooks_onANonFirstPage_reportsAccuratePageMetadata() {
        BookFilter filter = new BookFilter(null, "MYSTERY", null, null, false);
        Pageable pageable = PageRequest.of(1, 2);

        Book book = new Book();
        book.setId(3L);
        book.setTitle("The Last Witness");
        // Page 1 (zero-indexed) of 2-sized pages out of 5 total elements: 3 pages
        // overall, this one neither first nor last.
        Page<Book> bookPage = new PageImpl<>(List.of(book), pageable, 5);

        BookDtoOut dto = new BookDtoOut();
        dto.setId(3L);
        dto.setTitle("The Last Witness");

        given(bookRepository.findAll(any(Specification.class), eq(pageable))).willReturn(bookPage);
        given(bookMapper.toDto(book)).willReturn(dto);

        PageResponse<BookDtoOut> result = bookService.filterBooks(filter, pageable);

        assertThat(result.content()).containsExactly(dto);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(5);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.first()).isFalse();
        assertThat(result.last()).isFalse();
    }

    @Test
    void filterBooks_withNoMatches_returnsEmptyPageResponse() {
        BookFilter filter = new BookFilter("no-match", null, null, null, false);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Book> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        given(bookRepository.findAll(any(Specification.class), eq(pageable))).willReturn(emptyPage);

        PageResponse<BookDtoOut> result = bookService.filterBooks(filter, pageable);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }
}
