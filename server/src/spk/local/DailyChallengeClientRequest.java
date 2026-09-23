package spk.local;

import java.util.Objects;

/**
 * Exact-current Daily Challenge compatibility intent normalized from C2S103.
 *
 * The client-visible challenge key is preserved verbatim after the proven
 * command prefix. Mapping that key into server-owned assignment identity is
 * deliberately outside this transport object.
 */
final class DailyChallengeClientRequest
    implements ClientRequest {

    enum Action {
        CLAIM,
        INFO
    }

    private final Action action;
    private final String challengeKey;
    private final ClientRequestMetadata metadata;

    DailyChallengeClientRequest(
        Action action,
        String challengeKey,
        ClientRequestMetadata metadata
    ){
        this.action=Objects.requireNonNull(
            action,
            "action"
        );

        if(challengeKey==null)
            throw new NullPointerException(
                "challengeKey"
            );

        if(challengeKey.isEmpty())
            throw new IllegalArgumentException(
                "challengeKey empty"
            );

        this.challengeKey=challengeKey;
        this.metadata=Objects.requireNonNull(
            metadata,
            "metadata"
        );
    }

    Action action(){
        return action;
    }

    String challengeKey(){
        return challengeKey;
    }

    @Override public ClientRequestMetadata metadata(){
        return metadata;
    }

    @Override public String toString(){
        return "DailyChallengeClientRequest{action="+
            action+
            ",challengeKey="+
            challengeKey+
            ",metadata="+
            metadata+
            "}";
    }
}
