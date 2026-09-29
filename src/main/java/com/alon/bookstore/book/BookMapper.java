package com.alon.bookstore.book;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BookMapper {

    BookDtoOut toDto(Book book);


}
