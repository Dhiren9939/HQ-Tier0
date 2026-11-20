package me.dhiren9939.backend.access.infrastructure.keyformat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SortableEncoder {

    private static final String ALPHABET = "-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz";

    /** Characters {@link #encode} produces for {@code longCount} longs: 6 bits per character, rounded up. */
    public static int encodedLength(int longCount) {
        return (longCount * 64 + 5) / 6;
    }

    /** True when every character of {@code value} is in the encoding alphabet. */
    public static boolean isEncoded(String value) {
        for (int i = 0; i < value.length(); i++)
            if (ALPHABET.indexOf(value.charAt(i)) < 0)
                return false;
        return true;
    }

    public String encode(long[] array) {
        if (array == null || array.length == 0) return "";

        char[] out = new char[encodedLength(array.length)];

        long buffer = 0;
        int bitCount = 0;
        int outIdx = 0;

        for (long val : array) {
            for (int shift = 32; shift >= 0; shift -= 32) {
                long chunk = (val >>> shift) & 0xFFFFFFFFL;
                buffer = (buffer << 32) | chunk;
                bitCount += 32;

                while (bitCount >= 6) {
                    bitCount -= 6;
                    int index = (int) ((buffer >>> bitCount) & 0x3F);
                    out[outIdx++] = ALPHABET.charAt(index);
                }
            }
        }

        if (bitCount > 0) {
            int index = (int) ((buffer << (6 - bitCount)) & 0x3F);
            out[outIdx] = ALPHABET.charAt(index);
        }

        return new String(out);
    }
}
