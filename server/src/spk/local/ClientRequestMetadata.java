package spk.local;

import java.util.Objects;

/** Immutable transport/schema authority attached to every typed client request. */
final class ClientRequestMetadata {
    final int opcode;
    final String schema;
    final ClientRequestProvenance provenance;
    final String source;

    ClientRequestMetadata(
        int opcode,
        String schema,
        ClientRequestProvenance provenance,
        String source
    ){
        if(opcode<0||opcode>255)
            throw new IllegalArgumentException(
                "opcode="+opcode
            );

        this.opcode=opcode;
        this.schema=Objects.requireNonNull(
            schema,
            "schema"
        );
        this.provenance=Objects.requireNonNull(
            provenance,
            "provenance"
        );
        this.source=Objects.requireNonNull(
            source,
            "source"
        );
    }

    static ClientRequestMetadata exactCurrent(
        int opcode,
        String schema,
        String source
    ){
        return new ClientRequestMetadata(
            opcode,
            schema,
            ClientRequestProvenance
                .EXACT_CURRENT_CLIENT,
            source
        );
    }

    @Override public String toString(){
        return "ClientRequestMetadata{opcode="+
            opcode+
            ",schema="+schema+
            ",provenance="+provenance+
            ",source="+source+
            "}";
    }
}
