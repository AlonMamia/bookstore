package com.alon.bookstore.auth;

import com.alon.bookstore.user.Role;
import com.alon.bookstore.user.RoleRepository;
import com.alon.bookstore.user.User;
import com.alon.bookstore.user.UserDtoOut;
import com.alon.bookstore.user.UserMapper;
import com.alon.bookstore.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private SecurityContextRepository securityContextRepository;
    @Mock
    private SessionAuthenticationStrategy sessionAuthenticationStrategy;
    @Mock
    private UserMapper userMapper;

    private AuthService authService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, roleRepository, passwordEncoder,
                authenticationManager, securityContextRepository,
                sessionAuthenticationStrategy, userMapper
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private RegisterRequest registerRequest(String email, String password, String role) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setPassword(password);
        request.setRole(role);
        request.setFirstName("Test");
        request.setLastName("User");
        request.setDateOfBirth(LocalDate.of(1990, 1, 1));
        return request;
    }

    @Test
    void register_savesUserWithHashedPasswordAndRoleFromRequest_whenEmailIsNew() {
        RegisterRequest request = registerRequest("new@example.test", "password123", "USER");
        Role userRole = new Role();
        userRole.setTitle("USER");

        given(userRepository.existsByEmail("new@example.test")).willReturn(false);
        given(roleRepository.findByTitle("USER")).willReturn(Optional.of(userRole));
        given(passwordEncoder.encode("password123")).willReturn("hashed-password");

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("new@example.test");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(saved.getRole()).isEqualTo(userRole);
        assertThat(saved.getFirstName()).isEqualTo("Test");
        assertThat(saved.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 1, 1));
    }

    @Test
    void register_throwsEmailAlreadyExists_whenEmailAlreadyExists() {
        RegisterRequest request = registerRequest("existing@example.test", "password123", "USER");
        given(userRepository.existsByEmail("existing@example.test")).willReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessage("Email already exists");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_throwsIllegalArgument_whenRoleDoesNotExist() {
        RegisterRequest request = registerRequest("new@example.test", "password123", "NOT_A_ROLE");
        given(userRepository.existsByEmail("new@example.test")).willReturn(false);
        given(roleRepository.findByTitle("NOT_A_ROLE")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid role: NOT_A_ROLE");

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_authenticatesWithSubmittedCredentials_andPersistsTheAuthenticatedContext() {
        LoginRequest request = new LoginRequest("user@example.test", "password123");
        User user = new User();
        user.setEmail("user@example.test");
        CustomUserDetails principal = new CustomUserDetails(user);
        Authentication authenticated =
                new UsernamePasswordAuthenticationToken(principal, "password123", List.of());

        ArgumentCaptor<Authentication> authRequestCaptor = ArgumentCaptor.forClass(Authentication.class);
        given(authenticationManager.authenticate(authRequestCaptor.capture())).willReturn(authenticated);
        UserDtoOut expectedDto = new UserDtoOut();
        expectedDto.setEmail("user@example.test");
        given(userMapper.toDto(user)).willReturn(expectedDto);

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        UserDtoOut result = authService.login(request, httpRequest, httpResponse);

        assertThat(result).isEqualTo(expectedDto);

        Authentication authenticationRequest = authRequestCaptor.getValue();
        assertThat(authenticationRequest.getName()).isEqualTo("user@example.test");
        assertThat(authenticationRequest.getCredentials()).isEqualTo("password123");
        assertThat(authenticationRequest.isAuthenticated()).isFalse();

        verify(sessionAuthenticationStrategy).onAuthentication(authenticated, httpRequest, httpResponse);

        ArgumentCaptor<SecurityContext> contextCaptor = ArgumentCaptor.forClass(SecurityContext.class);
        verify(securityContextRepository).saveContext(contextCaptor.capture(), eq(httpRequest), eq(httpResponse));
        assertThat(contextCaptor.getValue().getAuthentication()).isEqualTo(authenticated);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isEqualTo(authenticated);
    }

    @Test
    void login_throwsIllegalState_whenAuthenticatedPrincipalIsNotCustomUserDetails() {
        LoginRequest request = new LoginRequest("user@example.test", "password123");
        Authentication authenticated =
                new UsernamePasswordAuthenticationToken("not-a-custom-user-details", "password123", List.of());
        given(authenticationManager.authenticate(any())).willReturn(authenticated);

        assertThatThrownBy(() -> authService.login(
                request, new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void login_doesNotPersistAnyContext_whenAuthenticationFails() {
        LoginRequest request = new LoginRequest("user@example.test", "wrong-password");
        given(authenticationManager.authenticate(any()))
                .willThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(
                request, new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(sessionAuthenticationStrategy, securityContextRepository);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
