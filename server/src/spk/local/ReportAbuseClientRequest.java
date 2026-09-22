package spk.local;

import java.util.Objects;

/**
 * Exact-current report submission transport.
 *
 * The raw target name key, rule index and mute toggle are preserved. Report
 * storage, moderation and sanction policy remain server-authority unknown.
 */
final class ReportAbuseClientRequest
    implements ClientRequest {

    private final long nameKey;
    private final int ruleIndex;
    private final int muteToggle;
    private final ClientRequestMetadata metadata;

    ReportAbuseClientRequest(
        long nameKey,
        int ruleIndex,
        int muteToggle,
        ClientRequestMetadata metadata
    ){
        this.nameKey=nameKey;
        this.ruleIndex=
            requireU8(
                ruleIndex,
                "ruleIndex"
            );
        this.muteToggle=
            requireU8(
                muteToggle,
                "muteToggle"
            );
        this.metadata=Objects.requireNonNull(
            metadata,
            "metadata"
        );
    }

    long nameKey(){return nameKey;}
    int ruleIndex(){return ruleIndex;}
    int muteToggle(){return muteToggle;}

    @Override public ClientRequestMetadata metadata(){
        return metadata;
    }

    @Override public String toString(){
        return "ReportAbuseClientRequest{nameKey="+
            Long.toUnsignedString(nameKey)+
            ",ruleIndex="+
            ruleIndex+
            ",muteToggle="+
            muteToggle+
            ",metadata="+
            metadata+
            "}";
    }

    private static int requireU8(
        int value,
        String label
    ){
        if(value<0||value>255)
            throw new IllegalArgumentException(
                label+"="+value
            );
        return value;
    }
}
