// src/main/java/com/fttranscendence/authservice/security/JwtAuthenticationFilter.java
package com.fttranscendence.authservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_TOKEN_PREFIX = "Bearer ";
  private static final String REGISTER_PATH = "/api/auth/register";
  private static final String LOGIN_PATH = "/api/auth/login";

  private final JwtService jwtService;
  private final UserDetailsService userDetailsService;

  @Override
  protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
    String path = request.getServletPath();
    return REGISTER_PATH.equals(path) || LOGIN_PATH.equals(path);
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);

    boolean hasBearerToken =
        StringUtils.hasText(authorizationHeader)
            && authorizationHeader.startsWith(BEARER_TOKEN_PREFIX);
    if (!hasBearerToken) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      String jwt = authorizationHeader.substring(BEARER_TOKEN_PREFIX.length());
      String userEmail = jwtService.extractEmail(jwt);

      boolean hasUserEmail = userEmail != null;
      if (hasUserEmail) {
        boolean hasNoExistingAuthentication =
            SecurityContextHolder.getContext().getAuthentication() == null;
        if (hasNoExistingAuthentication) {
          UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);
          if (jwtService.isTokenValid(jwt, userDetails)) {
            UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
          }
        }
      }
    } catch (RuntimeException ex) {
      SecurityContextHolder.clearContext();
    }

    filterChain.doFilter(request, response);
  }
}
