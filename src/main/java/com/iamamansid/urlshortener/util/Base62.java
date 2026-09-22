package com.iamamansid.urlshortener.util;

/**
 * Base62 ([0-9a-zA-Z]) encoding used to turn the monotonically increasing
 * database id into a short, URL-safe code. Encoding ids (rather than random
 * strings) guarantees uniqueness without a retry loop, keeps codes short, and
 * makes them roughly time-ordered.
 */
public final class Base62 {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = ALPHABET.length();

    private Base62() {
    }

    public static String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be non-negative, got " + value);
        }
        if (value == 0) {
            return "0";
        }
        StringBuilder sb = new StringBuilder();
        while (value > 0) {
            sb.append(ALPHABET.charAt((int) (value % BASE)));
            value /= BASE;
        }
        return sb.reverse().toString();
    }

    /**
     * Encodes {@code value}, left-padding with {@code '0'} up to {@code minLength}
     * so early codes still look like real short links (e.g. {@code 0003d7}).
     */
    public static String encode(long value, int minLength) {
        String encoded = encode(value);
        if (encoded.length() >= minLength) {
            return encoded;
        }
        return "0".repeat(minLength - encoded.length()) + encoded;
    }

    public static long decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            throw new IllegalArgumentException("encoded value must not be null or empty");
        }
        long result = 0L;
        for (int i = 0; i < encoded.length(); i++) {
            int digit = ALPHABET.indexOf(encoded.charAt(i));
            if (digit < 0) {
                throw new IllegalArgumentException("Invalid Base62 character: '" + encoded.charAt(i) + "'");
            }
            result = result * BASE + digit;
        }
        return result;
    }
}
