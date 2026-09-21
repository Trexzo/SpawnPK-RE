package spk.local;

import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;

public final class ContentItemOnItemRegistrationTest {
    private static final int SELECTED_ITEM_ID=900601;
    private static final int TARGET_ITEM_ID=900602;

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(20L);
        WorldPlayer actor=new WorldPlayer();

        try{
            ContentRegistry registry=world.content();
            AtomicReference<ContentRegistration> low=
                new AtomicReference<>();
            AtomicReference<ContentRegistration> high=
                new AtomicReference<>();
            AtomicReference<ContentRegistration> reverse=
                new AtomicReference<>();

            registry.installCustom(
                module(
                    "item-item-low",
                    registrar->
                        low.set(
                            registrar.itemOnItem(
                                SELECTED_ITEM_ID,
                                TARGET_ITEM_ID,
                                10,
                                context->{
                                    if(context.selectedItemId()!=
                                            SELECTED_ITEM_ID||
                                       context.targetItemId()!=
                                            TARGET_ITEM_ID)
                                        throw new AssertionError(
                                            "low item-on-item context mismatch"
                                        );

                                    return ContentInteractionResult.handled(
                                        "ITEM_ITEM_LOW"
                                    );
                                }
                            )
                        )
                )
            );

            registry.installCustom(
                module(
                    "item-item-high",
                    registrar->
                        high.set(
                            registrar.itemOnItem(
                                SELECTED_ITEM_ID,
                                TARGET_ITEM_ID,
                                20,
                                context->
                                    ContentInteractionResult.handled(
                                        "ITEM_ITEM_HIGH"
                                    )
                            )
                        )
                )
            );

            registry.installCustom(
                module(
                    "item-item-reverse",
                    registrar->
                        reverse.set(
                            registrar.itemOnItem(
                                TARGET_ITEM_ID,
                                SELECTED_ITEM_ID,
                                30,
                                context->{
                                    if(context.selectedItemId()!=
                                            TARGET_ITEM_ID||
                                       context.targetItemId()!=
                                            SELECTED_ITEM_ID)
                                        throw new AssertionError(
                                            "reverse item-on-item context mismatch"
                                        );

                                    return ContentInteractionResult.handled(
                                        "ITEM_ITEM_REVERSE"
                                    );
                                }
                            )
                        )
                )
            );

            if(low.get()==null||
               high.get()==null||
               reverse.get()==null||
               !low.get().active()||
               !high.get().active()||
               !reverse.get().active())
                throw new AssertionError(
                    "committed item-on-item registrations not active"
                );

            assertWinner(
                registry.itemOnItemBinding(
                    SELECTED_ITEM_ID,
                    TARGET_ITEM_ID
                ),
                "item-item-high",
                20
            );

            assertWinner(
                registry.itemOnItemBinding(
                    TARGET_ITEM_ID,
                    SELECTED_ITEM_ID
                ),
                "item-item-reverse",
                30
            );

            boolean offWorldRejected=false;
            try{
                registry.dispatchItemOnItem(
                    SELECTED_ITEM_ID,
                    TARGET_ITEM_ID
                );
            }catch(IllegalStateException expected){
                offWorldRejected=
                    expected.getMessage().contains(
                        "World execution context"
                    );
            }

            if(!offWorldRejected)
                throw new AssertionError(
                    "off-world item-on-item dispatch accepted"
                );

            world.registerPlayer(
                actor,
                "item-on-item-registration"
            );
            world.start();

            ContentInteractionResult first=
                dispatch(
                    world,
                    actor,
                    registry,
                    SELECTED_ITEM_ID,
                    TARGET_ITEM_ID
                );

            if(first==null||
               !"ITEM_ITEM_HIGH".equals(
                   first.outcome()))
                throw new AssertionError(
                    "high-priority item-on-item dispatch="+
                    first
                );

            ContentInteractionResult reversed=
                dispatch(
                    world,
                    actor,
                    registry,
                    TARGET_ITEM_ID,
                    SELECTED_ITEM_ID
                );

            if(reversed==null||
               !"ITEM_ITEM_REVERSE".equals(
                   reversed.outcome()))
                throw new AssertionError(
                    "ordered reverse item-on-item dispatch="+
                    reversed
                );

            if(!high.get().unregister()||
               high.get().active()||
               high.get().unregister())
                throw new AssertionError(
                    "high item-on-item handle idempotence"
                );

            assertWinner(
                registry.itemOnItemBinding(
                    SELECTED_ITEM_ID,
                    TARGET_ITEM_ID
                ),
                "item-item-low",
                10
            );

            ContentInteractionResult fallback=
                dispatch(
                    world,
                    actor,
                    registry,
                    SELECTED_ITEM_ID,
                    TARGET_ITEM_ID
                );

            if(fallback==null||
               !"ITEM_ITEM_LOW".equals(
                   fallback.outcome()))
                throw new AssertionError(
                    "item-on-item fallback="+fallback
                );

            AtomicReference<ContentRegistration>
                conflictHandle=
                    new AtomicReference<>();

            boolean conflict=false;
            try{
                registry.installCustom(
                    module(
                        "item-item-conflict",
                        registrar->
                            conflictHandle.set(
                                registrar.itemOnItem(
                                    SELECTED_ITEM_ID,
                                    TARGET_ITEM_ID,
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
                    "equal-priority item-on-item conflict not fail-closed"
                );

            AtomicReference<ContentRegistration>
                cancelled=
                    new AtomicReference<>();

            registry.installCustom(
                module(
                    "item-item-cancel-pending",
                    registrar->{
                        ContentRegistration handle=
                            registrar.itemOnItem(
                                SELECTED_ITEM_ID+1,
                                TARGET_ITEM_ID+1,
                                1,
                                context->
                                    ContentInteractionResult.handled(
                                        "SHOULD_NOT_RUN"
                                    )
                            );
                        cancelled.set(handle);

                        if(!handle.unregister())
                            throw new AssertionError(
                                "pending item-on-item unregister rejected"
                            );
                    }
                )
            );

            if(cancelled.get()==null||
               cancelled.get().active()||
               cancelled.get().unregister()||
               registry.itemOnItemBinding(
                   SELECTED_ITEM_ID+1,
                   TARGET_ITEM_ID+1
               )!=null)
                throw new AssertionError(
                    "pending item-on-item cancellation committed"
                );

            assertInvalidRegistration(
                registry,
                "item-item-negative-selected",
                registrar->
                    registrar.itemOnItem(
                        -1,
                        TARGET_ITEM_ID,
                        1,
                        context->
                            ContentInteractionResult.handled(
                                "INVALID"
                            )
                    )
            );

            assertInvalidRegistration(
                registry,
                "item-item-negative-target",
                registrar->
                    registrar.itemOnItem(
                        SELECTED_ITEM_ID,
                        -1,
                        1,
                        context->
                            ContentInteractionResult.handled(
                                "INVALID"
                            )
                    )
            );

            ContentInteractionResult unknown=
                dispatch(
                    world,
                    actor,
                    registry,
                    SELECTED_ITEM_ID+99,
                    TARGET_ITEM_ID+99
                );

            if(unknown!=null)
                throw new AssertionError(
                    "unknown item-on-item resolved"
                );

            System.out.println(
                "CONTENT_ITEM_ON_ITEM_REGISTRATION_PASS "+
                "orderedPair=true "+
                "priority=true "+
                "fallback=true "+
                "equalPriorityFailClosed=true "+
                "pendingCancellation=true "+
                "worldThreadGuard=true "+
                "widgetIdentity=false "+
                "inventorySlot=false "+
                "packetIdentity=false"
            );
        }finally{
            if(actor.registered())
                world.unregisterPlayer(actor);
            world.close();
        }
    }

    private static ContentInteractionResult dispatch(
        World world,
        WorldPlayer actor,
        ContentRegistry registry,
        int selectedItemId,
        int targetItemId
    )throws Exception{
        AtomicReference<ContentInteractionResult>
            result=
                new AtomicReference<>();

        world.submitAndWait(
            actor,
            ()->
                result.set(
                    registry.dispatchItemOnItem(
                        selectedItemId,
                        targetItemId
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
                "invalid item-on-item registration accepted: "+
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
           !"ITEM_ON_ITEM".equals(info.kind))
            throw new AssertionError(
                "unexpected item-on-item binding winner "+
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

    private ContentItemOnItemRegistrationTest(){}
}
