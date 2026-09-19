package spk.local;

import java.io.ByteArrayOutputStream;

final class BitWriter {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private int current;
    private int used;

    void write(int value, int bits) {
        if (bits < 0 || bits > 31) throw new IllegalArgumentException("bits=" + bits);
        for (int i = bits - 1; i >= 0; i--) {
            current = (current << 1) | ((value >>> i) & 1);
            used++;
            if (used == 8) { out.write(current); current = 0; used = 0; }
        }
    }

    byte[] finish() {
        if (used != 0) { current <<= (8 - used); out.write(current); current = 0; used = 0; }
        return out.toByteArray();
    }
}
