package com.orabank.backend.controller;

import com.orabank.backend.dto.AuthUserResponse;
import com.orabank.backend.dto.LoginRequest;
import com.orabank.backend.dto.LoginResponse;
import com.orabank.backend.dto.PasswordSetupInfoResponse;
import com.orabank.backend.dto.PasswordSetupRequest;
import com.orabank.backend.dto.PasswordSetupValidateRequest;
import com.orabank.backend.security.CurrentUser;
import com.orabank.backend.service.AuthService;
import com.orabank.backend.service.PasswordSetupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordSetupService passwordSetupService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Compte connecté tel qu'il est en base : le frontend s'y recale (rôle ou nom changés depuis la connexion). */
    @GetMapping("/me")
    public AuthUserResponse me() {
        return AuthUserResponse.from(CurrentUser.require());
    }

    @PostMapping("/password-setup/validate")
    public PasswordSetupInfoResponse validatePasswordSetupLink(@Valid @RequestBody PasswordSetupValidateRequest request) {
        return passwordSetupService.describe(request.token());
    }

    @PostMapping("/password-setup")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void definePassword(@Valid @RequestBody PasswordSetupRequest request) {
        passwordSetupService.definePassword(request);
    }
}
