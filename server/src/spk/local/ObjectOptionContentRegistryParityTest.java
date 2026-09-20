package spk.local;

import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.ContentProvenance;
import spk.content.builtin.UnknownServerInteractionModule;

public final class ObjectOptionContentRegistryParityTest {
    public static void main(String[] args)throws Exception{
        GenericInteractionEvent fixture=
            GenericInteractionEvent.objectOption(
                70,
                UnknownServerInteractionModule.FIXTURE_OPTION,
                UnknownServerInteractionModule.FIXTURE_OBJECT_ID,
                3087,
                3495
            );

        LocalGenericInteractionHandler legacy=
            new LocalGenericInteractionHandler();

        String expected=
            legacy.handle(fixture);

        World world=
            World.isolatedForTest(20L);
        WorldPlayer player=
            new WorldPlayer();

        try{
            LocalGenericInteractionHandler routed=
                new LocalGenericInteractionHandler(
                    world.content()
                );

            boolean offThreadRejected=false;
            try{
                routed.handle(fixture);
            }catch(IllegalStateException expectedFailure){
                offThreadRejected=
                    expectedFailure.getMessage()
                        .contains(
                            "World execution context"
                        );
            }

            if(!offThreadRejected)
                throw new AssertionError(
                    "object content dispatch allowed off World thread"
                );

            world.registerPlayer(
                player,
                "testprofile"
            );
            world.start();

            AtomicReference<String> actual=
                new AtomicReference<>();

            world.submitAndWait(
                player,
                ()->actual.set(
                    routed.handle(fixture)
                ),
                5_000L
            );

            if(!expected.equals(
                    actual.get()))
                throw new AssertionError(
                    "object option behavior changed expected="+
                    expected+
                    " actual="+actual.get()
                );

            ContentRegistry.BindingInfo binding=
                world.content()
                    .objectOptionBinding(
                        UnknownServerInteractionModule
                            .FIXTURE_OBJECT_ID,
                        UnknownServerInteractionModule
                            .FIXTURE_OPTION
                    );

            if(binding==null||
               !"unknown-server-interactions".equals(
                    binding.moduleId
               )||
               binding.provenance!=
                    ContentProvenance
                        .UNKNOWN_SERVER_AUTHORITY)
                throw new AssertionError(
                    "object option provenance "+
                    binding
                );

            GenericInteractionEvent unrelated=
                GenericInteractionEvent.objectOption(
                    70,
                    3,
                    12345,
                    3088,
                    3496
                );

            String unrelatedExpected=
                legacy.handle(unrelated);

            AtomicReference<String> unrelatedActual=
                new AtomicReference<>();

            world.submitAndWait(
                player,
                ()->unrelatedActual.set(
                    routed.handle(unrelated)
                ),
                5_000L
            );

            if(!unrelatedExpected.equals(
                    unrelatedActual.get()))
                throw new AssertionError(
                    "unregistered object fallback changed"
                );

            System.out.println(
                "OBJECT_OPTION_CONTENT_REGISTRY_PARITY_PASS "+
                "exactLegacyText=true "+
                "wireBehavior=NONE_UNCHANGED "+
                "offThreadRejected=true "+
                "fallback=true "+
                "provenance=UNKNOWN_SERVER_AUTHORITY"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(player);
            world.close();
        }
    }
}
