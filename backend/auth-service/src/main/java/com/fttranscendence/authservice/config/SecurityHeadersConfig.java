package com.fttranscendence.authservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Response hardening shared by every auth-service endpoint. HSTS is emitted only for secure
 * requests by Spring Security, which keeps local HTTP development usable while allowing a TLS
 * terminating proxy in production.
 */
@Configuration
public class SecurityHeadersConfig {
  private static final long ONE_YEAR_SECONDS = 31_536_000L;
  private static final String CONTENT_SECURITY_POLICY =
      "default-src 'self'; base-uri 'self'; form-action 'self'; "
          + "frame-ancestors 'none'; object-src 'none'";
  private static final String PERMISSIONS_POLICY =
      "accelerometer=(), camera=(), geolocation=(), microphone=(), payment=(), usb=()";
  private final boolean hstsEnabled;

  public SecurityHeadersConfig(
      @Value("${security.headers.hsts-enabled:false}") boolean hstsEnabled) {
    this.hstsEnabled = hstsEnabled;
  }

  public void configure(HttpSecurity http) throws Exception {
    http.headers(
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
        });
  }
}
