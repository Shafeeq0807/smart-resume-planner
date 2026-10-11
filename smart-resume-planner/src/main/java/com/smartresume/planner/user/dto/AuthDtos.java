package com.smartresume.planner.user.dto;

import com.smartresume.planner.user.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain at least one letter and one digit")
            String password,
            @NotBlank @Size(max = 150) String fullName
    ) { }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) { }

    public record RefreshRequest(@NotBlank String refreshToken) { }

    public record LogoutRequest(@NotBlank String refreshToken) { }

    public record AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresIn,
                               UserResponse user) {
        public static AuthResponse of(String access, String refresh, long expiresIn, User user) {
            return new AuthResponse(access, refresh, "Bearer", expiresIn, UserResponse.from(user));
        }
    }

    public record UserResponse(Long id, String email, String fullName, String headline, String githubUsername,
                               String role, boolean emailVerified, Instant createdAt, Instant lastLoginAt) {
        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getHeadline(),
                    u.getGithubUsername(), u.getRole().name(), u.isEmailVerified(), u.getCreatedAt(),
                    u.getLastLoginAt());
        }
    }

    public record UpdateProfileRequest(
            @Size(max = 150) String fullName,
            @Size(max = 255) String headline,
            @Pattern(regexp = "^$|^[A-Za-z0-9](?:[A-Za-z0-9]|-(?=[A-Za-z0-9])){0,38}$",
                    message = "must be a valid GitHub username") String githubUsername
    ) { }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain at least one letter and one digit")
            String newPassword
    ) { }
}
