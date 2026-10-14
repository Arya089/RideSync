package com.ridesync.payment.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignaturesTest {

    @Test
    void hmacMatchesKnownTestVector() {
        assertEquals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
                Signatures.hmacSha256Hex("key", "The quick brown fox jumps over the lazy dog"));
    }

    @Test
    void safeEqualsAcceptsIdenticalValues() {
        assertTrue(Signatures.safeEquals("abc123", "abc123"));
    }

    @Test
    void safeEqualsRejectsDifferentOrNullValues() {
        assertFalse(Signatures.safeEquals("abc123", "abc124"));
        assertFalse(Signatures.safeEquals(null, "abc123"));
        assertFalse(Signatures.safeEquals("abc123", null));
    }
}