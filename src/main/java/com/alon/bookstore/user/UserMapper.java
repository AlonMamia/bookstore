package com.alon.bookstore.user;

import com.alon.bookstore.auth.RegisterRequest;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    // Entity → DTO
    UserDtoOut toDto(User user);

    default String map(Role role) {
        return role == null ? null : role.getTitle();
    }

    default Role map(String title) {
        if (title == null) {
            return null;
        }

        Role role = new Role();
        role.setTitle(title);
        return role;
    }
}
