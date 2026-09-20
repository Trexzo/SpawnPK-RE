package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Exact S2C126 newline-string + short-A target encoder.
 *
 * Callers publish typed ApplicationControl126Command values; raw target ids and
 * control strings stay inside the protocol layer.
 */
final class ApplicationBus126Publisher {
    /**
     * Low-level generic packet-126 publisher retained for existing internal
     * widget-text/dev-fixture callsites. Application control/state code should
     * use the typed command overload below.
     */
    static void send(
        ServerPacketWriter writer,
        int targetKey,
        String payload
    )throws IOException{
        Objects.requireNonNull(
            writer,
            "writer"
        );

        if(targetKey<=0||
           targetKey>0xffff)
            throw new IllegalArgumentException(
                "targetKey="+
                targetKey
            );

        writer.varShort(
            126,
            BootstrapPackets.widgetText126(
                targetKey,
                payload==null
                    ?""
                    :payload
            )
        );
    }

    static void send(
        ServerPacketWriter writer,
        ApplicationControl126Command command
    )throws IOException{
        Objects.requireNonNull(
            writer,
            "writer"
        );
        Objects.requireNonNull(
            command,
            "command"
        );

        send(
            writer,
            command.target().key(),
            command.payload()
        );
    }

    private ApplicationBus126Publisher(){}
}
