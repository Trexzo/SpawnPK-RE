package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;

final class Binary {
    private Binary() {}

    static byte[] readExactly(InputStream in, int n) throws IOException {
        byte[] out = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(out, off, n - off);
            if (r < 0) throw new EOFException("needed " + n + " bytes, got " + off);
            off += r;
        }
        return out;
    }

    static int u8(byte[] b, int off) { return b[off] & 0xff; }
    static int u16(byte[] b, int off) { return ((b[off] & 0xff) << 8) | (b[off+1] & 0xff); }
    static int i32(byte[] b, int off) {
        return (b[off] << 24) | ((b[off+1] & 0xff) << 16) | ((b[off+2] & 0xff) << 8) | (b[off+3] & 0xff);
    }
    static long i64(byte[] b, int off) {
        return ((long)(b[off] & 0xff) << 56) | ((long)(b[off+1] & 0xff) << 48)
             | ((long)(b[off+2] & 0xff) << 40) | ((long)(b[off+3] & 0xff) << 32)
             | ((long)(b[off+4] & 0xff) << 24) | ((long)(b[off+5] & 0xff) << 16)
             | ((long)(b[off+6] & 0xff) << 8) | (long)(b[off+7] & 0xff);
    }
    static void put64(OutputStream out, long v) throws IOException {
        for (int shift = 56; shift >= 0; shift -= 8) out.write((int)(v >>> shift) & 0xff);
    }

    static String readNlString(byte[] b, Cursor c) throws IOException {
        int start = c.pos;
        while (c.pos < b.length && b[c.pos] != 10) c.pos++;
        if (c.pos >= b.length) throw new EOFException("unterminated newline string at " + start);
        String s = new String(b, start, c.pos - start, StandardCharsets.ISO_8859_1);
        c.pos++;
        return s;
    }

    static final class Cursor { int pos; Cursor(int p) { pos = p; } }
}
