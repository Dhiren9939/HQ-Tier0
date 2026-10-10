package me.dhiren9939.api.apikey.internal.version;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SortableEncoder {

    private static final String ALPHABET = "-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz";

    public String encode(long[] array) {
        if (array == null || array.length == 0) return "";

        int totalBits = array.length * 64;
        int numChars = (totalBits + 5) / 6;
        char[] out = new char[numChars];

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
