package pt.psia.auth;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import pt.psia.auth.domain.User;
import pt.psia.auth.repository.UserRepository;
import pt.psia.auth.service.AuthService;
import pt.psia.auth.service.TokenService;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private UserRepository userRepository;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        var encoder = new BCryptPasswordEncoder();
        var tokenService = mock(TokenService.class);
        when(tokenService.generateToken(any())).thenReturn("mock-token");
        when(tokenService.getTtlSeconds()).thenReturn(7200L);
        authService = new AuthService(userRepository, encoder, tokenService, new SimpleMeterRegistry());
    }

    @Test
    void registerOk() {
        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var resp = authService.register("alice", "password123");
        assertEquals("alice", resp.username());
        assertNotNull(resp.userId());
    }

    @Test
    void registerDuplicate() {
        when(userRepository.findByUsernameIgnoreCase("alice"))
                .thenReturn(Optional.of(new User(UUID.randomUUID(), "alice", "hash")));
        var ex = assertThrows(ResponseStatusException.class, () -> authService.register("alice", "password123"));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void registerCaseInsensitiveDuplicate() {
        when(userRepository.findByUsernameIgnoreCase("Alice"))
                .thenReturn(Optional.of(new User(UUID.randomUUID(), "alice", "hash")));
        var ex = assertThrows(ResponseStatusException.class, () -> authService.register("Alice", "password123"));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void loginOk() {
        var encoder = new BCryptPasswordEncoder();
        var user = new User(UUID.randomUUID(), "alice", encoder.encode("password123"));
        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(user));
        var resp = authService.login("alice", "password123");
        assertEquals("mock-token", resp.token());
        assertEquals("Bearer", resp.tokenType());
        assertEquals("alice", resp.username());
    }

    @Test
    void loginWrongPassword() {
        var encoder = new BCryptPasswordEncoder();
        var user = new User(UUID.randomUUID(), "alice", encoder.encode("password123"));
        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(user));
        var ex = assertThrows(ResponseStatusException.class, () -> authService.login("alice", "wrong"));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void loginUnknownUser() {
        when(userRepository.findByUsernameIgnoreCase("ghost")).thenReturn(Optional.empty());
        var ex = assertThrows(ResponseStatusException.class, () -> authService.login("ghost", "password123"));
        assertEquals(401, ex.getStatusCode().value());
    }
}
