package com.ptutor.backend.config;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.ptutor.backend.response.ApiResponseFactory;

import tools.jackson.databind.ObjectMapper;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import static com.ptutor.backend.config.SecurityConstants.API_PUBLIC;
import static com.ptutor.backend.config.SecurityConstants.API_DOCUMENTATION;
import static com.ptutor.backend.config.SecurityConstants.STUDENT_SELF_SERVICE_API;
import static com.ptutor.backend.config.SecurityConstants.TUTOR_CERTIFICATE_READ_API;
import static com.ptutor.backend.config.SecurityConstants.TUTOR_PROFILE_READ_API;
import static com.ptutor.backend.config.SecurityConstants.TUTOR_SELF_SERVICE_API;


@Configuration
public class SecurityConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public Key jwtSecretKey(@Value("${app.security.jwt.secret}") String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT secret must contain at least 32 bytes");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(Key jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey.getEncoded()));
    }

    @Bean
    public JwtDecoder jwtDecoder(Key jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey((javax.crypto.SecretKey) jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            if (role == null || role.isBlank()) {
                return List.<GrantedAuthority>of();
            }
            return List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });
        return converter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            CorsConfigurationSource corsConfigurationSource,
            ObjectMapper objectMapper,
            ApiResponseFactory responseFactory,
            Environment environment) throws Exception {
        boolean productionProfile = environment.acceptsProfiles(Profiles.of("prod"));
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeSecurityError(
                                response, objectMapper, responseFactory, HttpStatus.UNAUTHORIZED,
                                "UNAUTHORIZED", "Authentication is required", request.getRequestURI()))
                        .accessDeniedHandler((request, response, exception) -> writeSecurityError(
                                response, objectMapper, responseFactory, HttpStatus.FORBIDDEN,
                                "FORBIDDEN", "You do not have permission to access this resource", request.getRequestURI())))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(API_PUBLIC).permitAll()
                        .requestMatchers(API_DOCUMENTATION).access((authentication, context) ->
                                new org.springframework.security.authorization.AuthorizationDecision(!productionProfile))
                        .requestMatchers(STUDENT_SELF_SERVICE_API).hasRole("STUDENT")
                        .requestMatchers(TUTOR_SELF_SERVICE_API).hasRole("TUTOR")
                        .requestMatchers(TUTOR_CERTIFICATE_READ_API).hasRole("STUDENT")
                        .requestMatchers(TUTOR_PROFILE_READ_API).authenticated()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }

    private void writeSecurityError(
            jakarta.servlet.http.HttpServletResponse response,
            ObjectMapper objectMapper,
            ApiResponseFactory responseFactory,
            HttpStatus status,
            String code,
            String message,
            String path) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), responseFactory.error(code, message, java.util.Map.of(), path));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.security.cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        return buildCorsConfigurationSource(allowedOrigins);
    }

    private CorsConfigurationSource buildCorsConfigurationSource(String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
