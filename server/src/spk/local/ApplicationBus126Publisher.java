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

        int targetKey=
            command.target().key();

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
                command.payload()
            )
        );
    }

    private ApplicationBus126Publisher(){}
}
