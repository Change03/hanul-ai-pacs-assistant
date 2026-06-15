package com.hanul.aipacs.controller;

import com.hanul.aipacs.dto.AuthDtos.LoginRequest;
import com.hanul.aipacs.dto.AuthDtos.MeResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;

    public AuthController(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/login")
    public MeResponse login(@RequestBody LoginRequest request, HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, servletRequest, servletResponse);
        return me(authentication);
    }

    @PostMapping("/logout")
    public MeResponse logout(HttpServletRequest request) {
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        SecurityContextHolder.clearContext();
        return new MeResponse("", "", false);
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return new MeResponse("", "", false);
        }
        String role = authentication.getAuthorities().stream()
            .findFirst()
            .map(Object::toString)
            .map(value -> value.replace("ROLE_", ""))
            .orElse("RADIOLOGIST_DEMO");
        return new MeResponse(authentication.getName(), role, true);
    }
}
