package spk.local;

/**
 * Static v308 client prelogin username hash, recovered by read-only bytecode
 * analysis of rs.O.a(String) and rs.Client's (hash >> 16) & 31.
 *
 * The original algorithm encodes at most 12 UTF-16 characters in base 37,
 * case-folding ASCII A-Z/a-z and encoding ASCII digits as 27..36.
 * Unsupported characters contribute a zero digit; trailing zero
 * digits are removed. This is a routing/hash value, not authentication.
 */
final class V308PreloginNameHash {
    static int hash5(String username) {
        if (username == null) throw new IllegalArgumentException("null login name");
        long value = 0;
        for (int i = 0; i < username.length() && i < 12; i++) {
            char c = username.charAt(i);
            value *= 37L;
            if (c >= 'A' && c <= 'Z') value += c - 'A' + 1;
            else if (c >= 'a' && c <= 'z') value += c - 'a' + 1;
            else if (c >= '0' && c <= '9') value += c - '0' + 27;
        }
        while (value != 0 && value % 37L == 0) value /= 37L;
        return (int) ((value >> 16) & 31L);
    }

    private V308PreloginNameHash() {}
}
