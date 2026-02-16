package com.flashsale.auth.controller;

import com.flashsale.auth.dto.*;
import com.flashsale.auth.service.AuthService;
import com.flashsale.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Registration, Login, Logout, Password Reset")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // ======================== REGISTER ========================
    @Operation(summary = "Testing")
    @GetMapping("/test")
    public ApiResponse<Void> ping() {
        return ApiResponse.success("Pong", null);
    }

    @Operation(summary = "Register a new account (saves to DB, sends OTP)")
    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request.getIdentifier(), request.getPassword(), request.getNickname());
        return ApiResponse.success("Registration successful. OTP sent — please verify.", null);
    }

    @Operation(summary = "Verify registration OTP (activates account, returns token)")
    @PostMapping("/register/verify")
    public ApiResponse<LoginResponse> verifyRegistration(@Valid @RequestBody VerifyOtpRequest request) {
        LoginResponse response = authService.verifyRegistration(request.getIdentifier(), request.getOtp());
        return ApiResponse.success("Account verified", response);
    }

    @Operation(summary = "Resend verification OTP for an unverified account")
    @PostMapping("/register/resend-otp")
    public ApiResponse<Void> resendVerificationOtp(@Valid @RequestBody SendOtpRequest request) {
        authService.resendVerificationOtp(request.getIdentifier());
        return ApiResponse.success("OTP resent.", null);
    }

    // ======================== LOGIN / LOGOUT ========================

    @Operation(summary = "Login with identifier + password (rejects unverified users)")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request.getIdentifier(), request.getPassword());
        return ApiResponse.success("Login successful", response);
    }

    @Operation(summary = "Logout — invalidate token")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.getToken());
        return ApiResponse.success("Logged out successfully", null);
    }

    // ======================== RESET PASSWORD ========================

    @Operation(summary = "Request password reset OTP (works for verified and unverified users)")
    @PostMapping("/password/reset-request")
    public ApiResponse<Void> requestPasswordReset(@Valid @RequestBody SendOtpRequest request) {
        authService.requestPasswordReset(request.getIdentifier());
        return ApiResponse.success("Password reset OTP sent.", null);
    }

    @Operation(summary = "Reset password with OTP (also verifies unverified accounts)")
    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getIdentifier(), request.getOtp(), request.getNewPassword());
        return ApiResponse.success("Password reset successful. You can now login.", null);
    }
}
