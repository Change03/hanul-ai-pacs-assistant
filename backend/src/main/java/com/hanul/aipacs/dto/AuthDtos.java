package com.hanul.aipacs.dto;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record LoginRequest(String username, String password) {
    }

    public record MeResponse(String username, String role, boolean authenticated) {
    }
}
