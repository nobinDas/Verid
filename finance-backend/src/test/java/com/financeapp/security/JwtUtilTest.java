package com.financeapp.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    // Same secret as application.properties
    private static final String SECRET =
            "dGhpcy1pcy1hLXZlcnktc2VjdXJlLWp3dC1zZWNyZXQta2V5LWZvci1maW5hbmNlYXBwLTI1NmJpdA==";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 86_400_000L);

    @Test
    void generateToken_then_extractEmail_roundtrip() {
        String email = "user@example.com";

        String token = jwtUtil.generateToken(email);

        assertThat(token).isNotBlank();
        assertThat(jwtUtil.extractEmail(token)).isEqualTo(email);
        assertThat(jwtUtil.isValid(token)).isTrue();
    }

    @Test
    void isValid_expiredToken_returnsFalse() throws InterruptedException {
        JwtUtil shortLived = new JwtUtil(SECRET, 1L); // 1 ms TTL
        String token = shortLived.generateToken("expire@example.com");

        Thread.sleep(20); // ensure it expires

        assertThat(shortLived.isValid(token)).isFalse();
    }

    @Test
    void isValid_tamperedSignature_returnsFalse() {
        String token = jwtUtil.generateToken("user@example.com");
        // Replace last 5 chars to tamper the signature
        String tampered = token.substring(0, token.length() - 5) + "AAAAA";

        assertThat(jwtUtil.isValid(tampered)).isFalse();
    }

    @Test
    void isValid_garbageToken_returnsFalse() {
        assertThat(jwtUtil.isValid("not.a.jwt")).isFalse();
    }

    @Test
    void extractEmail_afterGenerate_matchesInput() {
        String email = "another@test.com";
        String token = jwtUtil.generateToken(email);

        assertThat(jwtUtil.extractEmail(token)).isEqualTo(email);
    }
}
