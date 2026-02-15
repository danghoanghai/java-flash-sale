package com.flashsale.auth.service;

import com.flashsale.auth.entity.User;
import com.flashsale.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailAuthStrategy implements AuthStrategy {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserRepository userRepository;

    @Override
    public boolean supports(String identifier) {
        return identifier != null && EMAIL_PATTERN.matcher(identifier).matches();
    }

    @Override
    public boolean existsByIdentifier(String identifier) {
        return userRepository.findByEmail(identifier).isPresent();
    }

    @Override
    public Optional<User> findByIdentifier(String identifier) {
        return userRepository.findByEmail(identifier);
    }

    @Override
    public User register(String identifier, String password, String nickname) {
        User user = User.builder()
                .email(identifier)
                .password(password)
                .nickname(nickname)
                .verified(false)
                .build();
        return userRepository.save(user);
    }
}
