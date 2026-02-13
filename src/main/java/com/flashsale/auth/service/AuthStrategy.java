package com.flashsale.auth.service;

import com.flashsale.auth.entity.User;

import java.util.Optional;

public interface AuthStrategy {

    boolean supports(String identifier);

    boolean existsByIdentifier(String identifier);

    Optional<User> findByIdentifier(String identifier);

    User register(String identifier, String password, String nickname);
}
