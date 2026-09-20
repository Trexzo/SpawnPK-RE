package spk.content.builtin;

import spk.content.api.*;

/**
 * Exact-current trigger whose original server outcome is still unknown.
 *
 * The module deliberately does nothing except preserve the fail-closed semantic
 * decision. Core installation assigns UNKNOWN_SERVER_AUTHORITY externally.
 */
public final class UnknownServerInteractionModule
    implements ContentModule {

    public static final int FIXTURE_OBJECT_ID=26972;
    public static final int FIXTURE_OPTION=3;

    @Override public String id(){
        return "unknown-server-interactions";
    }

    @Override public void register(
        ContentRegistrar registrar
    ){
        registrar.objectOption(
            FIXTURE_OBJECT_ID,
            FIXTURE_OPTION,
            100,
            context->
                ContentInteractionResult.handled(
                    "DECODED_FAIL_CLOSED"
                )
        );
    }
}
