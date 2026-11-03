package com.orabank.backend.security;

import com.orabank.backend.config.AppProperties;
import com.orabank.backend.entity.Employee;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String TYPE = "typ";
    private static final String ACCESS = "access";
    private static final String PASSWORD_SETUP = "password-setup";
    private static final String PASSWORD_VERSION = "ver";

    private final SecretKey key;
    private final Duration expiration;
    private final String issuer;
    private final Clock clock;

    public JwtService(AppProperties properties, Clock clock) {
        this.key = buildKey(properties.jwtSecret());
        this.expiration = properties.jwtExpiration();
        this.issuer = properties.jwtIssuer();
        this.clock = clock;
    }

    public String generateToken(Employee employee) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(issuer)
                .subject(employee.getMatricule())
                .claim("uid", employee.getId())
                .claim("role", employee.getRole().name())
                .claim(TYPE, ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public String generatePasswordSetupToken(Employee employee, String passwordVersion, Instant expiresAt) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(employee.getMatricule())
                .claim(TYPE, PASSWORD_SETUP)
                .claim(PASSWORD_VERSION, passwordVersion)
                .issuedAt(Date.from(clock.instant()))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parseAccessToken(String token) {
        Claims claims = parseClaims(token);
        Object type = claims.get(TYPE);
        if (type != null && !ACCESS.equals(type)) {
            throw new MalformedJwtException("Ce jeton ne permet pas de se connecter.");
        }
        return claims;
    }

    public Claims parsePasswordSetupToken(String token) {
        Claims claims = parseClaims(token);
        if (!PASSWORD_SETUP.equals(claims.get(TYPE))) {
            throw new MalformedJwtException("Ce jeton n'est pas un lien de mot de passe.");
        }
        return claims;
    }

    public String passwordVersionOf(Claims claims) {
        return claims.get(PASSWORD_VERSION, String.class);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .clockSkewSeconds(30)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private static SecretKey buildKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("APP_JWT_SECRET est obligatoire.");
        }
        byte[] bytes = decode(secret);
        if (bytes.length < 32) {
            throw new IllegalStateException("APP_JWT_SECRET doit faire au moins 32 octets (256 bits).");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    private static byte[] decode(String secret) {
        try {
            return Decoders.BASE64.decode(secret);
        } catch (IllegalArgumentException notBase64) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }
}
