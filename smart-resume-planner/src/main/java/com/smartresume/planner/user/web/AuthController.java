package com.smartresume.planner.user.web;

import com.smartresume.planner.security.CurrentUser;
import com.smartresume.planner.user.dto.AuthDtos.AuthResponse;
import com.smartresume.planner.user.dto.AuthDtos.LoginRequest;
import com.smartresume.planner.user.dto.AuthDtos.LogoutRequest;
import com.smartresume.planner.user.dto.AuthDtos.RefreshRequest;
import com.smartresume.planner.user.dto.AuthDtos.RegisterRequest;
import com.smartresume.planner.user.dto.AuthDtos.UserResponse;
import com.smartresume.planner.user.service.AuthService;
import com.smartresume.planner.user.service.AuthService.ClientInfo;
import com.smartresume.planner.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @Operation(summary = "Create an account and sign in")
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request, ClientInfo.of(http)));
    }

    @Operation(summary = "Sign in with email and password")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, ClientInfo.of(http));
    }

    @Operation(summary = "Exchange a refresh token for a new access + refresh token pair")
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        return authService.refresh(request.refreshToken(), ClientInfo.of(http));
    }

    @Operation(summary = "Sign out (revokes the refresh token family)")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Current user", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/me")
    public UserResponse me() {
        return userService.get(CurrentUser.id());
    }
}
