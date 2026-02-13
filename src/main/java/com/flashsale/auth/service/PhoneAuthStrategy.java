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
public class PhoneAuthStrategy implements AuthStrategy {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{7,15}$");

    private final UserRepository userRepository;

    @Override
    public boolean supports(String identifier) {
        return identifier != null && PHONE_PATTERN.matcher(identifier).matches();
    }

    @Override
    public boolean existsByIdentifier(String identifier) {
        return userRepository.findByPhone(identifier).isPresent();
    }

    @Override
    public Optional<User> findByIdentifier(String identifier) {
        return userRepository.findByPhone(identifier);
    }

    @Override
    public User register(String identifier, String password, String nickname) {
        User user = User.builder()
                .phone(identifier)
                .password(password)
                .nickname(nickname)
                .verified(false)
                .build();
        return userRepository.save(user);
    }
}
