package com.openclassrooms.etudiant.service;

import com.openclassrooms.etudiant.entities.User;
import com.openclassrooms.etudiant.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UNIT TEST: only UserService is real. Its three dependencies are fake (mocks),
 * so there is no Spring, no database and no Docker. The test runs in milliseconds.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;      // fake database access
    @Mock
    private PasswordEncoder passwordEncoder;    // fake password hasher
    @Mock
    private JwtService jwtService;              // fake token generator

    @InjectMocks
    private UserService userService;            // the real class under test, built with the 3 mocks

    private User createUser() {
        User user = new User();
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setLogin("john");
        user.setPassword("password");
        return user;
    }

    // ---------- register ----------

    @Test
    void register_newUser_savesUserWithEncodedPassword() {
        // GIVEN: no user with this login exists, and the encoder turns "password" into "ENCODED"
        User user = createUser();
        when(userRepository.findByLogin("john")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password")).thenReturn("ENCODED");

        // WHEN
        userService.register(user);

        // THEN: the user was saved, and its password is the encoded one
        verify(userRepository).save(user);
        assertEquals("ENCODED", user.getPassword());
    }

    @Test
    void register_existingLogin_throwsException() {
        // GIVEN: a user with this login already exists
        User user = createUser();
        when(userRepository.findByLogin("john")).thenReturn(Optional.of(user));

        // WHEN + THEN: registration is refused and nothing is saved
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> userService.register(user));

        assertEquals("User with login john already exists", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    // ---------- login ----------

    @Test
    void login_validCredentials_returnsToken() {
        // GIVEN: the user exists, the password matches, and the JWT service returns a token
        User storedUser = createUser();
        storedUser.setPassword("HASH");
        when(userRepository.findByLogin("john")).thenReturn(Optional.of(storedUser));
        when(passwordEncoder.matches("password", "HASH")).thenReturn(true);
        when(jwtService.generateToken(any())).thenReturn("fake-token");

        // WHEN
        String token = userService.login("john", "password");

        // THEN
        assertEquals("fake-token", token);
    }

    @Test
    void login_wrongPassword_throwsException() {
        // GIVEN: the user exists but the password does not match
        User storedUser = createUser();
        storedUser.setPassword("HASH");
        when(userRepository.findByLogin("john")).thenReturn(Optional.of(storedUser));
        when(passwordEncoder.matches("wrong", "HASH")).thenReturn(false);

        // WHEN + THEN
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> userService.login("john", "wrong"));

        assertEquals("Invalid credentials", exception.getMessage());
    }

    @Test
    void login_unknownUser_throwsException() {
        // GIVEN: no user with this login
        when(userRepository.findByLogin("ghost")).thenReturn(Optional.empty());

        // WHEN + THEN
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> userService.login("ghost", "password"));

        assertEquals("Invalid credentials", exception.getMessage());
    }
}