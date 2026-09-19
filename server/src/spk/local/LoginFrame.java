package spk.local;

import java.io.IOException;
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

    static LoginFrame parse(int loginType, byte[] payload) throws IOException {
        if (loginType != 16 && loginType != 18) throw new IOException("unexpected login type " + loginType);
        if (payload.length < 59) throw new IOException("login payload too short: " + payload.length);
        if (Binary.u8(payload, 0) != 255) throw new IOException("missing 255 login marker");
        int revision = Binary.u16(payload, 1);
        int memoryFlag = Binary.u8(payload, 3);
        int[] checksums = new int[9];
        int p = 4;
        for (int i = 0; i < 9; i++, p += 4) checksums[i] = Binary.i32(payload, p);

        int blockLen = Binary.u8(payload, p++);
        if (blockLen > payload.length - p) throw new IOException("inner login block length exceeds outer payload");
        int innerEnd = p + blockLen;
        if (Binary.u8(payload, p++) != 10) throw new IOException("missing inner login marker 10");
        int[] seeds = new int[4];
        for (int i = 0; i < 4; i++, p += 4) seeds[i] = Binary.i32(payload, p);

        // Exact current client(6).jar path (rs/f/a.c=307, rs/f/a.h=false): two ints, then 4 newline strings.
        int cfg1 = Binary.i32(payload, p); p += 4;
        int cfg2 = Binary.i32(payload, p); p += 4;
        Binary.Cursor c = new Binary.Cursor(p);
        String username = Binary.readNlString(payload, c);
        String password = Binary.readNlString(payload, c);
        String device = Binary.readNlString(payload, c);
        String trailing = Binary.readNlString(payload, c);
        if (c.pos > innerEnd) throw new IOException("decoded strings exceed inner block");

        return new LoginFrame(loginType, revision, memoryFlag, checksums, seeds, cfg1, cfg2,
                              username, password, device, trailing);
    }

    @Override public String toString() {
        return "LoginFrame{type=" + loginType + ", revision=" + revision + ", memoryFlag=" + memoryFlag
             + ", checksums=" + Arrays.toString(checksums) + ", seeds=" + Arrays.toString(isaacSeeds)
             + ", config1=" + configValue1 + ", config2=" + configValue2
             + ", username='" + username + "', password=<redacted>, deviceId='" + deviceId
             + "', trailingClientId='" + trailingClientId + "'}";
    }
}
