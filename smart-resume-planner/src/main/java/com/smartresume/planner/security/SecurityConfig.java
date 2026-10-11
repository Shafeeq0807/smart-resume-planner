package com.smartresume.planner.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartresume.planner.common.config.AppProperties;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
            "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**",
            "/actuator/health", "/actuator/health/**", "/actuator/info", "/error"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AppProperties props, JwtService jwtService,
                                            ObjectMapper objectMapper) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)           // stateless bearer-token API, no cookies
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.decoder(jwtService.decoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(entryPoint(objectMapper))
                        .accessDeniedHandler(accessDeniedHandler(objectMapper)))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint(objectMapper))
                        .accessDeniedHandler(accessDeniedHandler(objectMapper)))
                .addFilterBefore(new AuthRateLimitFilter(props.rateLimit().authAttemptsPerMinute(), objectMapper),
                        BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    JwtDecoder jwtDecoder(JwtService jwtService) {
        return jwtService.decoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(props.security().cors().allowedOrigins());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        cfg.setExposedHeaders(List.of("Content-Disposition"));
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            return role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });
        return converter;
    }

    private AuthenticationEntryPoint entryPoint(ObjectMapper mapper) {
        return (request, response, ex) -> writeProblem(mapper, response, HttpStatus.UNAUTHORIZED,
                "UNAUTHENTICATED", "Missing, invalid or expired access token");
    }

    private AccessDeniedHandler accessDeniedHandler(ObjectMapper mapper) {
        return (request, response, ex) -> writeProblem(mapper, response, HttpStatus.FORBIDDEN,
                "FORBIDDEN", "You do not have access to this resource");
    }

    private static void writeProblem(ObjectMapper mapper, jakarta.servlet.http.HttpServletResponse response,
                                     HttpStatus status, String code, String detail) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), Map.of(
                "status", status.value(),
                "title", status.getReasonPhrase(),
                "code", code,
                "detail", detail));
    }
}
