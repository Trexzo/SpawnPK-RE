package spk.content.builtin;

import spk.content.api.*;

/**
 * Local runtime-proven NPC semantic bindings.
 *
 * Banker 7605 option 1 Talk-to and option 3 Bank are established LocalLab
 * runtime parity. Core installation assigns LOCAL_RUNTIME_PROVEN externally.
 */
public final class RuntimeProvenNpcInteractionModule
    implements ContentModule {

    public static final int BANKER_7605=7605;

    @Override public String id(){
        return "runtime-proven-npc-interactions";
    }

    @Override public void register(
        ContentRegistrar registrar
    ){
        registrar.npcOption(
            BANKER_7605,
            1,
            100,
            context->
                ContentNpcOptionResult.handled(
                    ContentNpcService.BANK
                )
        );

        registrar.npcOption(
            BANKER_7605,
            3,
            100,
            context->
                ContentNpcOptionResult.handled(
                    ContentNpcService.BANK
                )
        );
    }
}
