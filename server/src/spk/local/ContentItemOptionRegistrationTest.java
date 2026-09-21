package spk.local;

import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;

public final class ContentItemOptionRegistrationTest {
    private static final int ITEM_ID=900101;
    private static final int OPTION=1;

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(20L);
        WorldPlayer player=new WorldPlayer();

        try{
            ContentRegistry registry=world.content();
            AtomicReference<ContentRegistration> low=
                new AtomicReference<>();
            AtomicReference<ContentRegistration> high=
                new AtomicReference<>();

            registry.installCustom(
                module(
                    "item-low",
                    registrar->
                        low.set(
                            registrar.itemOption(
                                ITEM_ID,
                                OPTION,
                                10,
                                context->{
                                    if(context.itemId()!=ITEM_ID||
                                       context.option()!=OPTION)
                                        throw new AssertionError(
                                            "low context mismatch"
                                        );

                                    return ContentInteractionResult.handled(
                                        "ITEM_LOW"
                                    );
                                }
                            )
                        )
                )
            );

            registry.installCustom(
                module(
                    "item-high",
                    registrar->
                        high.set(
                            registrar.itemOption(
                                ITEM_ID,
                                OPTION,
                                20,
                                context->
                                    ContentInteractionResult.handled(
                                        "ITEM_HIGH"
                                    )
                            )
                        )
                )
            );

            if(low.get()==null||
               high.get()==null||
               !low.get().active()||
               !high.get().active())
                throw new AssertionError(
                    "committed item registrations not active"
                );

            assertWinner(
                registry.itemOptionBinding(
                    ITEM_ID,
                    OPTION
                ),
                "item-high",
                20
            );

            boolean offWorldRejected=false;
            try{
                registry.dispatchItemOption(
                    ITEM_ID,
                    OPTION
                );
            }catch(IllegalStateException expected){
                offWorldRejected=
                    expected.getMessage().contains(
                        "World execution context"
                    );
            }

            if(!offWorldRejected)
                throw new AssertionError(
                    "off-world item dispatch accepted"
                );

            world.registerPlayer(
                player,
                "item-option-registration"
            );
            world.start();

            ContentInteractionResult first=
                dispatch(
                    world,
                    player,
                    registry
                );

            if(first==null||
               !"ITEM_HIGH".equals(
                   first.outcome()))
                throw new AssertionError(
                    "high-priority item dispatch="+
                    first
                );

            if(!high.get().unregister()||
               high.get().active()||
               high.get().unregister())
                throw new AssertionError(
                    "high item handle idempotence"
                );

            assertWinner(
                registry.itemOptionBinding(
                    ITEM_ID,
                    OPTION
                ),
                "item-low",
                10
            );

            ContentInteractionResult fallback=
                dispatch(
                    world,
                    player,
                    registry
                );

            if(fallback==null||
               !"ITEM_LOW".equals(
                   fallback.outcome()))
                throw new AssertionError(
                    "item fallback="+fallback
                );

            AtomicReference<ContentRegistration>
                conflictHandle=
                    new AtomicReference<>();

            boolean conflict=false;
            try{
                registry.installCustom(
                    module(
                        "item-conflict",
                        registrar->
                            conflictHandle.set(
                                registrar.itemOption(
                                    ITEM_ID,
                                    OPTION,
                                    10,
                                    context->
                                        ContentInteractionResult.handled(
                                            "CONFLICT"
                                        )
                                )
                            )
                    )
                );
            }catch(IllegalStateException expected){
                conflict=
                    expected.getMessage().contains(
                        "content binding conflict"
                    );
            }

            if(!conflict||
               conflictHandle.get()==null||
               conflictHandle.get().active()||
               conflictHandle.get().unregister())
                throw new AssertionError(
                    "equal-priority item conflict not fail-closed"
                );

            AtomicReference<ContentRegistration>
                cancelled=
                    new AtomicReference<>();

            registry.installCustom(
                module(
                    "item-cancel-pending",
                    registrar->{
                        ContentRegistration handle=
                            registrar.itemOption(
                                ITEM_ID+1,
                                2,
                                1,
                                context->
                                    ContentInteractionResult.handled(
                                        "SHOULD_NOT_RUN"
                                    )
                            );
                        cancelled.set(handle);

                        if(!handle.unregister())
                            throw new AssertionError(
                                "pending item unregister rejected"
                            );
                    }
                )
            );

            if(cancelled.get()==null||
               cancelled.get().active()||
               cancelled.get().unregister()||
               registry.itemOptionBinding(
                   ITEM_ID+1,
                   2
               )!=null)
                throw new AssertionError(
                    "pending item cancellation committed"
                );

            assertInvalidRegistration(
                registry,
                "item-negative-id",
                registrar->
                    registrar.itemOption(
                        -1,
                        1,
                        1,
                        context->
                            ContentInteractionResult.handled(
                                "INVALID"
                            )
                    )
            );

            assertInvalidRegistration(
                registry,
                "item-zero-option",
                registrar->
                    registrar.itemOption(
                        ITEM_ID,
                        0,
                        1,
                        context->
                            ContentInteractionResult.handled(
                                "INVALID"
                            )
                    )
            );

            AtomicReference<ContentInteractionResult>
                unknown=
                    new AtomicReference<>();

            world.submitAndWait(
                player,
                ()->
                    unknown.set(
                        registry.dispatchItemOption(
                            ITEM_ID+99,
                            1
                        )
                    ),
                5_000L
            );

            if(unknown.get()!=null)
                throw new AssertionError(
                    "unknown item option resolved"
                );

            System.out.println(
                "CONTENT_ITEM_OPTION_REGISTRATION_PASS "+
                "priority=true "+
                "fallback=true "+
                "equalPriorityFailClosed=true "+
                "pendingCancellation=true "+
                "worldThreadGuard=true "+
                "protocolIdentity=false"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(player);
            world.close();
        }
    }

    private static ContentInteractionResult dispatch(
        World world,
        WorldPlayer player,
        ContentRegistry registry
    )throws Exception{
        AtomicReference<ContentInteractionResult>
            result=
                new AtomicReference<>();

        world.submitAndWait(
            player,
            ()->
                result.set(
                    registry.dispatchItemOption(
                        ITEM_ID,
                        OPTION
                    )
                ),
            5_000L
        );

        return result.get();
    }

    private static void assertInvalidRegistration(
        ContentRegistry registry,
        String moduleId,
        ModuleBody body
    ){
        boolean rejected=false;

        try{
            registry.installCustom(
                module(
                    moduleId,
                    body
                )
            );
        }catch(IllegalArgumentException expected){
            rejected=true;
        }

        if(!rejected)
            throw new AssertionError(
                "invalid item registration accepted: "+
                moduleId
            );
    }

    private static void assertWinner(
        ContentRegistry.BindingInfo info,
        String moduleId,
        int priority
    ){
        if(info==null||
           !moduleId.equals(info.moduleId)||
           info.priority!=priority||
           !"ITEM_OPTION".equals(info.kind))
            throw new AssertionError(
                "unexpected item binding winner "+
                info
            );
    }

    private interface ModuleBody{
        void register(
            ContentRegistrar registrar
        );
    }

    private static ContentModule module(
        String id,
        ModuleBody body
    ){
        return new ContentModule(){
            @Override public String id(){
                return id;
            }

            @Override public void register(
                ContentRegistrar registrar
            ){
                body.register(registrar);
            }
        };
    }

    private ContentItemOptionRegistrationTest(){}
}
