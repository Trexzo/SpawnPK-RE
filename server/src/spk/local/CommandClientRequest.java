package spk.local;

import java.util.Objects;

/** Domain-facing command intent decoded from exact-current opcode 103. */
final class CommandClientRequest
    implements ClientRequest {

    private final String command;
    private final ClientRequestMetadata metadata;

    CommandClientRequest(
        String command,
        ClientRequestMetadata metadata
    ){
        this.command=Objects.requireNonNull(
            command,
            "command"
        );
        this.metadata=Objects.requireNonNull(
            metadata,
            "metadata"
        );
    }

    String command(){
        return command;
    }

    @Override public ClientRequestMetadata metadata(){
        return metadata;
    }

    @Override public String toString(){
        return "CommandClientRequest{command="+
            command+
            ",metadata="+metadata+
            "}";
    }
}
