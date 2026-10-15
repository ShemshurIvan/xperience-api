package com.xperience.dto.auth;

public record TokenResponse(String accessToken, String tokenType, Long expiresIn) {
}
