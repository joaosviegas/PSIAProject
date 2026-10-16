package pt.psia.auth.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pt.psia.auth.domain.User;
import pt.psia.auth.repository.UserRepository;
import pt.psia.auth.web.dto.TokenResponse;
import pt.psia.auth.web.dto.UserResponse;

import java.util.UUID;

/**
 * Service for handling authentication-related operations.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final Counter registrationsCounter;
    private final Counter loginSuccessCounter;
    private final Counter loginFailureCounter;

    /**
     * Constructs a new AuthService with the specified dependencies.
     *
     * @param userRepository the user repository
     * @param passwordEncoder the password encoder
     * @param tokenService the token service
     * @param registry the meter registry for metrics
     */
    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       TokenService tokenService, MeterRegistry registry) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.registrationsCounter = Counter.builder("auth_registrations_total").register(registry);
        this.loginSuccessCounter = Counter.builder("auth_logins_total").tag("result", "success").register(registry);
        this.loginFailureCounter = Counter.builder("auth_logins_total").tag("result", "failure").register(registry);
    }

    /** Registers a new user with the specified username and password. */
    public UserResponse register(String username, String password) {
        if (userRepository.findByUsernameIgnoreCase(username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken. Please choose a different one.");
        }
        var user = new User(UUID.randomUUID(), username, passwordEncoder.encode(password));
        userRepository.save(user);
        registrationsCounter.increment();
        return new UserResponse(user.getId().toString(), user.getUsername());
    }

    /** Logs in a user with the specified username and password, returning a token if successful. */
    public TokenResponse login(String username, String password) {
        var user = userRepository.findByUsernameIgnoreCase(username)
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> {
                    loginFailureCounter.increment();
                    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong credentials. Please try again or register first.");
                });
        loginSuccessCounter.increment();
        var token = tokenService.generateToken(user);
        return new TokenResponse(token, "Bearer", tokenService.getTtlSeconds(),
                user.getId().toString(), user.getUsername());
    }
}
