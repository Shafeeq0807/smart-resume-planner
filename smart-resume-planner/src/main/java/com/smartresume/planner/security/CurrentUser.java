package com.smartresume.planner.security;

import com.smartresume.planner.common.exception.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reads the authenticated user's id from the validated JWT; never trusts a client-supplied id. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return Long.valueOf(jwt.getSubject());
        }
        throw ApiException.unauthorized("UNAUTHENTICATED", "Authentication required");
    }
}
