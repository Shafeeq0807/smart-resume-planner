package com.smartresume.planner.user.web;

import com.smartresume.planner.security.CurrentUser;
import com.smartresume.planner.user.dto.AuthDtos.ChangePasswordRequest;
import com.smartresume.planner.user.dto.AuthDtos.UpdateProfileRequest;
import com.smartresume.planner.user.dto.AuthDtos.UserResponse;
import com.smartresume.planner.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
@Tag(name = "Profile")
public class UserController {

    private final UserService userService;

    @GetMapping
    public UserResponse get() {
        return userService.get(CurrentUser.id());
    }

    @PatchMapping
    public UserResponse update(@Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(CurrentUser.id(), request);
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(CurrentUser.id(), request);
        return ResponseEntity.noContent().build();
    }
}
