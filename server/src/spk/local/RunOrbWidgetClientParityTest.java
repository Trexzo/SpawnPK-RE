package spk.local;

import java.lang.reflect.*;

/** Verifies the exact packet-126 payload using the pinned client's real F()/T() readers. */
public final class RunOrbWidgetClientParityTest {
    public static void main(String[] args) throws Exception {
        byte[] payload = BootstrapPackets.widgetText126(149, "100%");
        Class<?> bufferClass = Class.forName("rs.x.e");
        Object buffer = bufferClass.getConstructor(byte[].class).newInstance((Object)payload);
        Method readString = bufferClass.getMethod("F");
        Method readWidget = bufferClass.getMethod("T");
        String text = String.valueOf(readString.invoke(buffer));
        int widget = ((Number)readWidget.invoke(buffer)).intValue();
        Field pos = bufferClass.getField("h");
        int consumed = pos.getInt(buffer);
        if (!"100%".equals(text)) throw new AssertionError("text=" + text);
        if (widget != 149) throw new AssertionError("widget=" + widget);
        if (consumed != payload.length) throw new AssertionError("consumed=" + consumed + " len=" + payload.length);
        System.out.println("RUN_ORB_WIDGET126_CLIENT_PARITY_PASS widget=149 text=100% bytes="+payload.length+" consumed="+consumed);
    }
}
