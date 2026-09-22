package com.iamamansid.urlshortener.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Base62Test {

    @Test
    void encodeZero() {
        assertEquals("0", Base62.encode(0));
    }

    @Test
    void encodeKnownValues() {
        assertEquals("9", Base62.encode(9));
        assertEquals("a", Base62.encode(10));
        assertEquals("z", Base62.encode(35));
        assertEquals("A", Base62.encode(36));
        assertEquals("Z", Base62.encode(61));
        assertEquals("10", Base62.encode(62));   // first two-char code
        assertEquals("ZZ", Base62.encode(3843)); // 62^2 - 1
        assertEquals("3d7", Base62.encode(12345));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 61, 62, 12345, 999_999_999L, Long.MAX_VALUE})
    void roundTrip(long value) {
        assertEquals(value, Base62.decode(Base62.encode(value)));
    }

    @Test
    void encodeWithMinLengthPadsWithZeros() {
        assertEquals("0003d7", Base62.encode(12345, 6));
        assertEquals("000000", Base62.encode(0, 6));
    }

    @Test
    void encodeWithMinLengthNeverTruncates() {
        // 62^6 needs 7 chars; minLength must not cut it down
        assertEquals(7, Base62.encode(62L * 62 * 62 * 62 * 62 * 62, 6).length());
    }

    @Test
    void paddedCodesStillDecode() {
        assertEquals(12345L, Base62.decode(Base62.encode(12345, 6)));
    }

    @Test
    void encodeNegativeThrows() {
        assertThrows(IllegalArgumentException.class, () -> Base62.encode(-1));
    }

    @Test
    void decodeInvalidCharacterThrows() {
        assertThrows(IllegalArgumentException.class, () -> Base62.decode("ab!"));
    }

    @Test
    void decodeEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> Base62.decode(""));
        assertThrows(IllegalArgumentException.class, () -> Base62.decode(null));
    }
}
