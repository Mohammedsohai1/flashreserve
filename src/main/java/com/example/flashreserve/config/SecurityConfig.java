package com.example.flashreserve.config;

import com.example.flashreserve.dto.ErrorResponse;
import com.example.flashreserve.service.CustomUserDetailsService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
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
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class SecurityConfig {

    @Bean
    SecretKey jwtSecretKey(
            @Value("${security.jwt.secret-base64}") String secretBase64) {

        byte[] keyBytes = Base64.getDecoder().decode(secretBase64);

        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                    "JWT secret must decode to at least 32 bytes");
        }

        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();

        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            CustomUserDetailsService userDetailsService,
            ObjectMapper objectMapper) throws Exception {
        AuthenticationEntryPoint authenticationEntryPoint =
                (request, response, exception) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);

                    objectMapper.writeValue(
                            response.getOutputStream(),
                            new ErrorResponse(
                                    "UNAUTHORIZED",
                                    "Authentication is required or the access token is invalid"
                            )
                    );
                };

        AccessDeniedHandler accessDeniedHandler =
                (request, response, exception) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);

                    objectMapper.writeValue(
                            response.getOutputStream(),
                            new ErrorResponse(
                                    "ACCESS_DENIED",
                                    "You do not have permission to perform this operation"
                            )
                    );
                };

        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .userDetailsService(userDetailsService)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth

                        // Registration and login are public
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/checkout.html").permitAll()

                        // Razorpay webhook is authenticated by its signature
                        .requestMatchers(
                                org.springframework.http.HttpMethod.POST,
                                "/payments/webhook"
                        ).permitAll()

                        // Public product browsing
                        .requestMatchers(
                                org.springframework.http.HttpMethod.GET,
                                "/products",
                                "/products/**"
                        ).permitAll()

                        // Only ADMIN can create products
                        .requestMatchers(
                                org.springframework.http.HttpMethod.POST,
                                "/products"
                        ).hasRole("ADMIN")

                        // Only ADMIN can modify or delete products
                        .requestMatchers(
                                org.springframework.http.HttpMethod.PUT,
                                "/products/**"
                        ).hasRole("ADMIN")
                        .requestMatchers(
                                org.springframework.http.HttpMethod.PATCH,
                                "/products/**"
                        ).hasRole("ADMIN")
                        .requestMatchers(
                                org.springframework.http.HttpMethod.DELETE,
                                "/products/**"
                        ).hasRole("ADMIN")

                        // Inventory reservation requires authentication
                        .requestMatchers(
                                org.springframework.http.HttpMethod.POST,
                                "/products/*/reserve"
                        ).authenticated()

                        // Only ADMIN can view all orders
                        .requestMatchers(
                                org.springframework.http.HttpMethod.GET,
                                "/orders"
                        ).hasRole("ADMIN")

                        // Customer order and payment operations require authentication
                        .requestMatchers("/orders/**", "/payments/**").authenticated()

                        // Reject anything not explicitly allowed
                        .anyRequest().denyAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                jwtAuthenticationConverter)));

        return http.build();
    }
}