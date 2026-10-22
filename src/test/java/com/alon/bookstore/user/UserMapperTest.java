package com.alon.bookstore.user;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private final UserMapper mapper = new UserMapperImpl();

    @Test
    void toDto_mapsAllFieldsIncludingRoleTitle() {
        Role role = new Role();
        role.setId(2L);
        role.setTitle("ADMIN");

        User user = new User();
        user.setId(1L);
        user.setEmail("admin@example.test");
        user.setPasswordHash("hash");
        user.setRole(role);
        user.setFirstName("Ada");
        user.setLastName("Min");
        user.setDateOfBirth(LocalDate.of(1985, 5, 20));

        UserDtoOut dto = mapper.toDto(user);

        assertThat(dto.getEmail()).isEqualTo("admin@example.test");
        assertThat(dto.getRole()).isEqualTo("ADMIN");
        assertThat(dto.getFirstName()).isEqualTo("Ada");
        assertThat(dto.getLastName()).isEqualTo("Min");
        assertThat(dto.getDateOfBirth()).isEqualTo(LocalDate.of(1985, 5, 20));
    }

    @Test
    void toDto_withNoRoleAssigned_mapsRoleAsNullInsteadOfFailing() {
        User user = new User();
        user.setEmail("user@example.test");
        user.setRole(null);

        UserDtoOut dto = mapper.toDto(user);

        assertThat(dto.getRole()).isNull();
    }
}
