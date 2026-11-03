package com.orabank.backend.dto;

public record LoginResponse(String accessToken, AuthUserResponse user) {
}
