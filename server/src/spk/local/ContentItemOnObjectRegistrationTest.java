package spk.local;

import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;

public final class ContentItemOnObjectRegistrationTest {
    private static final int ITEM_ID=900301;
    private static final int OBJECT_ID=900302;
    private static final int WORLD_X=3210;
    private static final int WORLD_Y=3211;

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
                    "item-object-low",
                    registrar->
                        low.set(
                            registrar.itemOnObject(
                                ITEM_ID,
                                OBJECT_ID,
                                10,
                                context->{
                                    if(context.itemId()!=ITEM_ID||
                                       context.objectId()!=OBJECT_ID||
                                       context.worldX()!=WORLD_X||
                                       context.worldY()!=WORLD_Y)
                                        throw new AssertionError(
                                            "low item-on-object context mismatch"
                                        );

                                    return ContentInteractionResult.handled(
                                        "ITEM_OBJECT_LOW"
                                    );
                                }
                            )
                        )
                )
            );

            registry.installCustom(
                module(
                    "item-object-high",
                    registrar->
                        high.set(
                            registrar.itemOnObject(
                                ITEM_ID,
                                OBJECT_ID,
                                20,
                                context->
                                    ContentInteractionResult.handled(
                                        "ITEM_OBJECT_HIGH"
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
                    "committed item-on-object registrations not active"
                );

            assertWinner(
                registry.itemOnObjectBinding(
                    ITEM_ID,
                    OBJECT_ID
                ),
                "item-object-high",
                20
            );

            boolean offWorldRejected=false;
            try{
                registry.dispatchItemOnObject(
                    ITEM_ID,
                    OBJECT_ID,
                    WORLD_X,
                    WORLD_Y
                );
            }catch(IllegalStateException expected){
                offWorldRejected=
                    expected.getMessage().contains(
                        "World execution context"
                    );
            }

            if(!offWorldRejected)
                throw new AssertionError(
                    "off-world item-on-object dispatch accepted"
                );

            world.registerPlayer(
                player,
                "item-on-object-registration"
            );
            world.start();

            ContentInteractionResult first=
                dispatch(
                    world,
                    player,
                    registry,
                    ITEM_ID,
                    OBJECT_ID
                );

            if(first==null||
               !"ITEM_OBJECT_HIGH".equals(
                   first.outcome()))
                throw new AssertionError(
                    "high-priority item-on-object dispatch="+
                    first
                );

            if(!high.get().unregister()||
               high.get().active()||
               high.get().unregister())
                throw new AssertionError(
                    "high item-on-object handle idempotence"
                );

            assertWinner(
                registry.itemOnObjectBinding(
                    ITEM_ID,
                    OBJECT_ID
                ),
                "item-object-low",
                10
            );

            ContentInteractionResult fallback=
                dispatch(
                    world,
                    player,
                    registry,
                    ITEM_ID,
                    OBJECT_ID
                );

            if(fallback==null||
               !"ITEM_OBJECT_LOW".equals(
                   fallback.outcome()))
                throw new AssertionError(
                    "item-on-object fallback="+fallback
                );

            AtomicReference<ContentRegistration>
                conflictHandle=
                    new AtomicReference<>();

            boolean conflict=false;
            try{
                registry.installCustom(
                    module(
                        "item-object-conflict",
                        registrar->
                            conflictHandle.set(
                                registrar.itemOnObject(
                                    ITEM_ID,
                                    OBJECT_ID,
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
                    "equal-priority item-on-object conflict not fail-closed"
                );

            AtomicReference<ContentRegistration>
                cancelled=
                    new AtomicReference<>();

            registry.installCustom(
                module(
                    "item-object-cancel-pending",
                    registrar->{
                        ContentRegistration handle=
                            registrar.itemOnObject(
                                ITEM_ID+1,
                                OBJECT_ID+1,
                                1,
                                context->
                                    ContentInteractionResult.handled(
                                        "SHOULD_NOT_RUN"
                                    )
                            );
                        cancelled.set(handle);

                        if(!handle.unregister())
                            throw new AssertionError(
                                "pending item-on-object unregister rejected"
                            );
                    }
                )
            );

            if(cancelled.get()==null||
               cancelled.get().active()||
               cancelled.get().unregister()||
               registry.itemOnObjectBinding(
                   ITEM_ID+1,
                   OBJECT_ID+1
               )!=null)
                throw new AssertionError(
                    "pending item-on-object cancellation committed"
                );

            assertInvalidRegistration(
                registry,
                "item-object-negative-item",
                registrar->
                    registrar.itemOnObject(
                        -1,
                        OBJECT_ID,
                        1,
                        context->
                            ContentInteractionResult.handled(
                                "INVALID"
                            )
                    )
            );

            assertInvalidRegistration(
                registry,
                "item-object-negative-object",
                registrar->
                    registrar.itemOnObject(
                        ITEM_ID,
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
                    player,
                    registry,
                    ITEM_ID+99,
                    OBJECT_ID+99
                );

            if(unknown!=null)
                throw new AssertionError(
                    "unknown item-on-object resolved"
                );

            System.out.println(
                "CONTENT_ITEM_ON_OBJECT_REGISTRATION_PASS "+
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
            if(player.registered())
                world.unregisterPlayer(player);
            world.close();
        }
    }

    private static ContentInteractionResult dispatch(
        World world,
        WorldPlayer player,
        ContentRegistry registry,
        int itemId,
        int objectId
    )throws Exception{
        AtomicReference<ContentInteractionResult>
            result=
                new AtomicReference<>();

        world.submitAndWait(
            player,
            ()->
                result.set(
                    registry.dispatchItemOnObject(
                        itemId,
                        objectId,
                        WORLD_X,
                        WORLD_Y
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
                "invalid item-on-object registration accepted: "+
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
           !"ITEM_ON_OBJECT".equals(info.kind))
            throw new AssertionError(
                "unexpected item-on-object binding winner "+
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

    private ContentItemOnObjectRegistrationTest(){}
}
