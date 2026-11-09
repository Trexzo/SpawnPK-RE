package spk.local;

import java.io.IOException;

/**
 * Only a LOCAL LAB transport admission rule for usernames supplied by a
 * client login frame. This is not the complete production SpawnPK name
 * policy and does not authenticate a player or check a password.
 *
 * The v308 wire parser decodes ISO-8859-1 newline fields. ASCII control
 * characters, embedded terminal escape codes and line endings must not
 * reach World registration, command-routing aliases or log messages.
 */
final class LocalLoginAliasPolicy {
    static final int MAX_ALIAS_LENGTH = 24;

    static String requireValid(String raw) throws IOException {
        if(raw == null || raw.isEmpty() || raw.length() > MAX_ALIAS_LENGTH)
            throw new IOException("invalid local login alias length");
        if(raw.charAt(0) == ' ' || raw.charAt(raw.length() - 1) == ' ')
            throw new IOException("invalid local login alias whitespace");
        for(int i = 0; i < raw.length(); i++){
            char c = raw.charAt(i);
            boolean letter = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
            boolean digit = c >= '0' && c <= '9';
            if(!(letter || digit || c == ' ' || c == '_' || c == '-'))
                throw new IOException("invalid local login alias character");
        }
        return raw;
    }

    static String redactForLog(String ignored) {
        // Do not accidentally reintroduce client-chosen identifiers into
        // structured stdout, exception strings, or CI artifacts.
        return "<redacted>";
    }

    private LocalLoginAliasPolicy(){}
}
