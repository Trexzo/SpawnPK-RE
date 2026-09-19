package spk.local;

import java.lang.reflect.*;

/**
 * Runs only when the original client jar is also on the classpath.  It feeds the
 * v0.3 minimal appearance block directly into the pinned client's real parser.
 */
public final class AppearanceClientParityTest {
    public static void main(String[] args) throws Exception {
        byte[] data = BootstrapPackets.appearanceBlock("localtest");
        Class<?> playerClass = Class.forName("rs.a.k");
        Class<?> bufferClass = Class.forName("rs.x.e");
        Object player = playerClass.getConstructor().newInstance();
        Object buffer = bufferClass.getConstructor(byte[].class).newInstance((Object)data);
        Method parse = playerClass.getMethod("a", bufferClass);
        parse.invoke(player, buffer);
        Field pos = bufferClass.getField("h");
        int consumed = pos.getInt(buffer);
        if (consumed != data.length) throw new AssertionError("client parser consumed=" + consumed + " len=" + data.length);
        Method displayName = playerClass.getMethod("o");
        String name = String.valueOf(displayName.invoke(player));
        if (!"localtest".equals(name)) throw new AssertionError("client display name=" + name);
        System.out.println("APPEARANCE_CLIENT_PARSER_PARITY_PASS bytes=" + data.length + " consumed=" + consumed + " displayName=" + name);
    }
}
