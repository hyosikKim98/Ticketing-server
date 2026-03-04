package com.example.ticketing.application.auth;

import com.example.ticketing.api.dto.AuthResponse;
import com.example.ticketing.api.dto.LoginRequest;
import com.example.ticketing.api.dto.SignupRequest;
import com.example.ticketing.domain.entity.User;
import com.example.ticketing.domain.entity.UserRole;
import com.example.ticketing.domain.repository.UserRepository;
import com.example.ticketing.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public void signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }
        User user = new User(
            request.email(),
            passwordEncoder.encode(request.password()),
            UserRole.USER
        );
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String token = jwtTokenProvider.createToken(user.getId(), user.getRole().name());
        return new AuthResponse(token);
    }
}
