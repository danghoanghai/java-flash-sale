package com.flashsale.auth.service;

import com.flashsale.auth.dto.LoginResponse;
import com.flashsale.auth.entity.User;
import com.flashsale.auth.repository.UserRepository;
import com.flashsale.common.exception.BusinessException;
import com.flashsale.wallet.service.WalletService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthStrategyFactory strategyFactory;
    private final OtpService otpService;
    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WalletService walletService;

    // ======================== REGISTER ========================

    public void register(String identifier, String password, String nickname) {
        AuthStrategy strategy = strategyFactory.resolve(identifier);

        if (strategy.existsByIdentifier(identifier)) {
            throw new BusinessException("An account with this identifier already exists.");
        }

        String hashedPassword = passwordEncoder.encode(password);
        strategy.register(identifier, hashedPassword, nickname);
        otpService.sendOtp(identifier);

        log.info("User registered (unverified) and OTP sent: {}", identifier);
    }

    public LoginResponse verifyRegistration(String identifier, String otp) {
        AuthStrategy strategy = strategyFactory.resolve(identifier);

        User user = strategy.findByIdentifier(identifier)
                .orElseThrow(() -> new BusinessException("No account found. Please register first."));

        if (user.getVerified()) {
            throw new BusinessException("Account is already verified. Please login.");
        }

        otpService.verifyOtp(identifier, otp);

        user.setVerified(true);
        userRepository.save(user);

        String token = tokenService.createToken(user.getId());
        log.info("User verified: {} (userId={})", identifier, user.getId());

        return LoginResponse.builder()
                .userId(user.getId())
                .nickname(user.getNickname())
                .token(token)
                .build();
    }

    public void resendVerificationOtp(String identifier) {
        AuthStrategy strategy = strategyFactory.resolve(identifier);

        User user = strategy.findByIdentifier(identifier)
                .orElseThrow(() -> new BusinessException("No account found. Please register first."));

        if (user.getVerified()) {
            throw new BusinessException("Account is already verified.");
        }

        otpService.sendOtp(identifier);
        log.info("Verification OTP resent to: {}", identifier);
    }

    // ======================== LOGIN ========================

    public LoginResponse login(String identifier, String password) {
        AuthStrategy strategy = strategyFactory.resolve(identifier);

        User user = strategy.findByIdentifier(identifier)
                .orElseThrow(() -> new BusinessException(401, "Invalid credentials."));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(401, "Invalid credentials.");
        }

        if (!user.getVerified()) {
            throw new BusinessException(403, "Account not verified. Please verify your OTP first.");
        }

        String token = tokenService.createToken(user.getId());
        walletService.ensureBalanceInRedis(user.getId());
        log.info("User logged in: {} (userId={})", identifier, user.getId());

        return LoginResponse.builder()
                .userId(user.getId())
                .nickname(user.getNickname())
                .token(token)
                .build();
    }

    // ======================== LOGOUT ========================

    public void logout(String token) {
        boolean removed = tokenService.invalidate(token);
        if (!removed) {
            log.warn("Logout called with unknown/expired token");
        }
    }

    // ======================== RESET PASSWORD ========================

    public void requestPasswordReset(String identifier) {
        AuthStrategy strategy = strategyFactory.resolve(identifier);

        strategy.findByIdentifier(identifier)
                .orElseThrow(() -> new BusinessException("No account found for: " + identifier));

        otpService.sendOtp(identifier);
        log.info("Password reset OTP sent to: {}", identifier);
    }

    public void resetPassword(String identifier, String otp, String newPassword) {
        AuthStrategy strategy = strategyFactory.resolve(identifier);

        User user = strategy.findByIdentifier(identifier)
                .orElseThrow(() -> new BusinessException("No account found for: " + identifier));

        otpService.verifyOtp(identifier, otp);

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setVerified(true);
        userRepository.save(user);

        log.info("Password reset (and verified) for: {} (userId={})", identifier, user.getId());
    }
}
