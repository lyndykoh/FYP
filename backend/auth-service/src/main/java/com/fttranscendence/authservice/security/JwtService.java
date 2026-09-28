// src/main/java/com/fttranscendence/authservice/security/JwtService.java
package com.fttranscendence.authservice.security;

import com.fttranscendence.authservice.model.User;
import com.fttranscendence.authservice.model.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  @Value("${jwt.secret}")
  private String secretKey;

  @Value("${jwt.expiration}")
  private Long jwtExpiration;

  @PostConstruct
  void validateConfiguration() {
    boolean hasUsableSecret = isUsableSecret(secretKey);
    if (!hasUsableSecret) {
      throw new IllegalArgumentException(
          "JWT_SECRET must contain at least 32 non-placeholder bytes");
    }

    boolean hasPositiveExpiration = jwtExpiration != null && jwtExpiration > 0;
    if (!hasPositiveExpiration) {
      throw new IllegalArgumentException("JWT_EXPIRATION_MS must be positive");
    }
  }

  public String extractEmail(String token) {
    return extractClaim(token, Claims::getSubject);
  }

  public UserRole extractRole(String token) {
    String role = extractClaim(token, claims -> claims.get("role", String.class));
    if (role == null) {
      throw new IllegalArgumentException("Token is missing the role claim");
    }
    return UserRole.valueOf(role);
  }

  public long extractUserId(String token) {
    Number userId = extractClaim(token, claims -> claims.get("userId", Number.class));
    if (userId == null || userId.longValue() <= 0) {
      throw new IllegalArgumentException("Token is missing a valid userId claim");
    }
    return userId.longValue();
  }

  public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
    final Claims claims = extractAllClaims(token);
    return claimsResolver.apply(claims);
  }

  public String generateToken(UserDetails userDetails) {
    return generateToken(new HashMap<>(), userDetails);
  }

  public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
    if (!(userDetails instanceof User user)) {
      throw new IllegalArgumentException(
          "A persisted user identity and role are required to issue a token");
    }

    boolean hasRole = user.getRole() != null;
    if (!hasRole) {
      throw new IllegalArgumentException(
          "A persisted user identity and role are required to issue a token");
    }

    boolean hasPersistedId = user.getId() > 0;
    if (!hasPersistedId) {
      throw new IllegalArgumentException(
          "A persisted user identity and role are required to issue a token");
    }

    Map<String, Object> claims = new HashMap<>(extraClaims);
    claims.put("role", user.getRole().name());
    claims.put("userId", user.getId());
    claims.put("fullName", user.getFullName());
    return Jwts.builder()
        .setClaims(claims)
        .setSubject(userDetails.getUsername())
        .setIssuedAt(new Date(System.currentTimeMillis()))
        .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
        .signWith(getSigningKey(), SignatureAlgorithm.HS256)
        .compact();
  }

  public boolean isTokenValid(String token, UserDetails userDetails) {
    try {
      if (!(userDetails instanceof User user)) {
        return false;
      }

      boolean hasRole = user.getRole() != null;
      if (!hasRole) {
        return false;
      }

      final String email = extractEmail(token);
      final UserRole role = extractRole(token);
      final long userId = extractUserId(token);

      boolean hasMatchingEmail = email.equalsIgnoreCase(userDetails.getUsername());
      if (!hasMatchingEmail) {
        return false;
      }

      boolean hasMatchingRole = role == user.getRole();
      if (!hasMatchingRole) {
        return false;
      }

      boolean hasMatchingUserId = userId == user.getId();
      if (!hasMatchingUserId) {
        return false;
      }

      return !isTokenExpired(token);
    } catch (JwtException | IllegalArgumentException ex) {
      return false;
    }
  }

  private boolean isTokenExpired(String token) {
    return extractExpiration(token).before(new Date());
  }

  private Date extractExpiration(String token) {
    return extractClaim(token, Claims::getExpiration);
  }

  private Claims extractAllClaims(String token) {
    return Jwts.parserBuilder()
        .setSigningKey(getSigningKey())
        .build()
        .parseClaimsJws(token)
        .getBody();
  }

  private Key getSigningKey() {
    byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
    return Keys.hmacShaKeyFor(keyBytes);
  }

  static boolean isUsableSecret(String secret) {
    return secret != null
        && secret.getBytes(StandardCharsets.UTF_8).length >= 32
        && !secret.toLowerCase(Locale.ROOT).contains("change-me");
  }
}
