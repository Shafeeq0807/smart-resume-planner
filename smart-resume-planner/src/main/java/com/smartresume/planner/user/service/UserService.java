package com.smartresume.planner.user.service;

import com.smartresume.planner.common.exception.ApiException;
import com.smartresume.planner.user.domain.User;
import com.smartresume.planner.user.dto.AuthDtos.ChangePasswordRequest;
import com.smartresume.planner.user.dto.AuthDtos.UpdateProfileRequest;
import com.smartresume.planner.user.dto.AuthDtos.UserResponse;
import com.smartresume.planner.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    @Transactional(readOnly = true)
    public UserResponse get(Long userId) {
        return UserResponse.from(load(userId));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = load(userId);
        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.headline() != null) {
            user.setHeadline(request.headline().isBlank() ? null : request.headline().trim());
        }
        if (request.githubUsername() != null) {
            user.setGithubUsername(request.githubUsername().isBlank() ? null : request.githubUsername().trim());
        }
        return UserResponse.from(user);
    }

    /** Changing the password signs the user out everywhere by revoking every refresh token. */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = load(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("BAD_CURRENT_PASSWORD", "Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        authService.revokeAllSessions(userId);
    }

    public User load(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
    }
}
