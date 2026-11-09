package spk.local;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class LoginFrame {
    final int loginType;
    final int revision;
    final int memoryFlag;
    final int[] checksums;
    final int[] isaacSeeds;
    final int configValue1;
    final int configValue2;
    final String username;
    final String password;
    final String deviceId;
    final String trailingClientId;

    private LoginFrame(int loginType, int revision, int memoryFlag, int[] checksums, int[] isaacSeeds,
                       int configValue1, int configValue2, String username, String password,
                       String deviceId, String trailingClientId) {
        this.loginType = loginType; this.revision = revision; this.memoryFlag = memoryFlag;
        this.checksums = checksums; this.isaacSeeds = isaacSeeds;
        this.configValue1 = configValue1; this.configValue2 = configValue2;
        this.username = username; this.password = password; this.deviceId = deviceId;
        this.trailingClientId = trailingClientId;
    }

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private static final String WINDOWS_1252_OPT_IN =
        "spk.local.login.decodeWindows1252";

    static LoginFrame parse(int loginType, byte[] payload) throws IOException {
        // Genuine v308 rs/x/e.a(String) uses String.getBytes() with the
        // CLIENT JVM default charset. On the verified private Windows JDK11
        // this is Cp1252. Keep ISO-8859-1 as the legacy LocalLab default
        // until actual original v308 GUI login acceptance; opt-in only.
        Charset wire = Boolean.getBoolean(WINDOWS_1252_OPT_IN)
            ? WINDOWS_1252 : StandardCharsets.ISO_8859_1;
        return parse(loginType, payload, wire);
    }

    static LoginFrame parse(int loginType, byte[] payload, Charset wireCharset) throws IOException {
        // Arbitrary charset names could silently replacement-decode broken
        // identifiers. This LocalLab wire mode intentionally supports only
        // the original legacy byte mapping and pinned Windows Cp1252.
        if (wireCharset == null ||
            !(StandardCharsets.ISO_8859_1.equals(wireCharset) ||
              WINDOWS_1252.equals(wireCharset)))
            throw new IOException("unsupported v308 login string wire charset");
        if (loginType != 16 && loginType != 18) throw new IOException("unexpected login type " + loginType);
        // v308 login = 4-byte header + 9 CRC32s + one length byte,
        // then marker 10, 4 ISAAC seeds, 2 config ints and 4 NL strings.
        // An empty NL string still requires its terminating byte.
        if (payload.length < 70) throw new IOException("login payload too short: " + payload.length);
        if (Binary.u8(payload, 0) != 255) throw new IOException("missing 255 login marker");
        int revision = Binary.u16(payload, 1);
        int memoryFlag = Binary.u8(payload, 3);
        int[] checksums = new int[9];
        int p = 4;
        for (int i = 0; i < 9; i++, p += 4) checksums[i] = Binary.i32(payload, p);

        int blockLen = Binary.u8(payload, p++);
        if (blockLen < 29) throw new IOException("inner login block too short");
        if (blockLen != payload.length - p)
            throw new IOException("inner login block length differs from outer payload");
        int innerEnd = p + blockLen;
        if (Binary.u8(payload, p++) != 10) throw new IOException("missing inner login marker 10");
        int[] seeds = new int[4];
        for (int i = 0; i < 4; i++, p += 4) seeds[i] = Binary.i32(payload, p);

        // Exact current v308 client path (rs/f/a.c=308, rs/f/a.h=false): outer protocol remains 317; two ints, then 4 newline strings.
        int cfg1 = Binary.i32(payload, p); p += 4;
        int cfg2 = Binary.i32(payload, p); p += 4;
        Binary.Cursor c = new Binary.Cursor(p);
        String username = readNlWithin(payload, c, innerEnd, wireCharset);
        String password = readNlWithin(payload, c, innerEnd, wireCharset);
        String device = readNlWithin(payload, c, innerEnd, wireCharset);
        String trailing = readNlWithin(payload, c, innerEnd, wireCharset);
        if (c.pos != innerEnd) throw new IOException("extra bytes after fourth login string");

        return new LoginFrame(loginType, revision, memoryFlag, checksums, seeds, cfg1, cfg2,
                              username, password, device, trailing);
    }

    /**
     * Read one v308 newline-terminated field strictly within the declared
     * inner block. The earlier Binary.readNlString(payload,cursor) could
     * accidentally consume terminators in bytes beyond that boundary.
     */
    private static String readNlWithin(byte[] payload, Binary.Cursor c, int limit, Charset wireCharset) throws IOException {
        int start = c.pos;
        if (start >= limit) throw new IOException("missing newline login field");
        while (c.pos < limit && payload[c.pos] != 10) c.pos++;
        if (c.pos >= limit) throw new IOException("unterminated newline login field");
        String value = new String(payload, start, c.pos - start, wireCharset);
        c.pos++;
        return value;
    }

    @Override public String toString() {
        // Never put attacker-supplied credentials, identifying strings or
        // ISAAC session seeds in stdout/stderr and CI log artifacts.
        return "LoginFrame{type=" + loginType + ", revision=" + revision + ", memoryFlag=" + memoryFlag
             + ", checksums=" + Arrays.toString(checksums) + ", isaacSeeds=<redacted>"
             + ", config1=" + configValue1 + ", config2=" + configValue2
             + ", username=<redacted>, password=<redacted>, deviceId=<redacted>"
             + ", trailingClientId=<redacted>}";
    }
}
