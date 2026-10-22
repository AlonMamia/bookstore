package com.alon.bookstore.auth;

import com.alon.bookstore.user.User;
import com.alon.bookstore.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    private CustomUserDetailsService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new CustomUserDetailsService(userRepository);
    }

    @Test
    void loadUserByUsername_returnsUserDetails_whenUserExists() {
        User user = new User();
        user.setEmail("user@example.test");
        user.setPasswordHash("hashed-password");
        given(userRepository.findByEmail("user@example.test")).willReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("user@example.test");

        assertThat(details.getUsername()).isEqualTo("user@example.test");
        assertThat(details.getPassword()).isEqualTo("hashed-password");
        assertThat(((CustomUserDetails) details).getUser()).isSameAs(user);
    }

    @Test
    void loadUserByUsername_throwsUsernameNotFound_whenUserDoesNotExist() {
        given(userRepository.findByEmail("missing@example.test")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("missing@example.test"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
