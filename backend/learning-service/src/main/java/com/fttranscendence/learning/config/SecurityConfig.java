package com.fttranscendence.learning.config;

import com.fttranscendence.learning.security.JwtAuthenticationFilter;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

  private static final long ONE_YEAR_SECONDS = 31_536_000L;
  private static final String CONTENT_SECURITY_POLICY =
      "default-src 'self'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'; object-src 'none'";
  private static final String PERMISSIONS_POLICY =
      "accelerometer=(), camera=(), geolocation=(), microphone=(), payment=(), usb=()";

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtAuthenticationFilter jwtAuthenticationFilter,
      @Value("${security.headers.hsts-enabled:false}") boolean hstsEnabled)
      throws Exception {
    http.cors(cors -> {})
        .csrf(AbstractHttpConfigurer::disable)
        .headers(
            headers -> {
              headers
                  .contentTypeOptions(Customizer.withDefaults())
                  .frameOptions(frame -> frame.deny())
                  .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                  .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                  .permissionsPolicy(policy -> policy.policy(PERMISSIONS_POLICY));
              if (hstsEnabled) {
                headers.httpStrictTransportSecurity(
                    hsts -> hsts.maxAgeInSeconds(ONE_YEAR_SECONDS).includeSubDomains(true));
              } else {
                headers.httpStrictTransportSecurity(hsts -> hsts.disable());
              }
            })
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/health")
                    .permitAll()
                    // Internal writes are authenticated by a backend-only integration key in their
                    // controllers.
                    .requestMatchers("/api/learning/internal/**")
                    .permitAll()
                    .requestMatchers("/api/learning/tutor/**")
                    .hasRole("TUTOR")
                    .requestMatchers("/api/learning/student/**")
                    .hasRole("STUDENT")
                    .requestMatchers("/api/learning/shared/**")
                    .hasAnyRole("TUTOR", "STUDENT")
                    // Every public domain route must be explicitly listed above.
                    .anyRequest()
                    .denyAll())
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${security.cors.allowed-origins:http://localhost:3000}") String configuredOrigins) {
    CorsConfiguration configuration = new CorsConfiguration();

    List<String> allowedOrigins =
        Arrays.stream(configuredOrigins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .distinct()
            .toList();
    if (allowedOrigins.isEmpty()) {
      throw new IllegalStateException(
          "security.cors.allowed-origins must contain at least one origin");
    }

    configuration.setAllowedOrigins(allowedOrigins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of(
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.ACCEPT,
            "X-Requested-With",
            "Idempotency-Key"));
    configuration.setAllowCredentials(false);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
