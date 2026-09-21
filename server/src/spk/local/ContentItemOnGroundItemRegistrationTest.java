package spk.local;

import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;

public final class ContentItemOnGroundItemRegistrationTest {
    private static final int ITEM_ID=900501;
    private static final int GROUND_ITEM_ID=900502;
    private static final int WORLD_X=3220;
    private static final int WORLD_Y=3221;

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(20L);
        WorldPlayer actor=new WorldPlayer();

        try{
            ContentRegistry registry=world.content();
            AtomicReference<ContentRegistration> low=
                new AtomicReference<>();
            AtomicReference<ContentRegistration> high=
                new AtomicReference<>();

            registry.installCustom(
                module(
                    "item-ground-low",
                    registrar->
                        low.set(
                            registrar.itemOnGroundItem(
                                ITEM_ID,
                                GROUND_ITEM_ID,
                                10,
                                context->{
                                    if(context.itemId()!=ITEM_ID||
                                       context.groundItemId()!=GROUND_ITEM_ID||
                                       context.worldX()!=WORLD_X||
                                       context.worldY()!=WORLD_Y)
                                        throw new AssertionError(
                                            "low item-on-ground context mismatch"
                                        );

                                    return ContentInteractionResult.handled(
                                        "ITEM_GROUND_LOW"
                                    );
                                }
                            )
                        )
                )
            );

            registry.installCustom(
                module(
                    "item-ground-high",
                    registrar->
                        high.set(
                            registrar.itemOnGroundItem(
                                ITEM_ID,
                                GROUND_ITEM_ID,
                                20,
                                context->
                                    ContentInteractionResult.handled(
                                        "ITEM_GROUND_HIGH"
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
                    "committed item-on-ground registrations not active"
                );

            assertWinner(
                registry.itemOnGroundItemBinding(
                    ITEM_ID,
                    GROUND_ITEM_ID
                ),
                "item-ground-high",
                20
            );

            boolean offWorldRejected=false;
            try{
                registry.dispatchItemOnGroundItem(
                    ITEM_ID,
                    GROUND_ITEM_ID,
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
                    "off-world item-on-ground dispatch accepted"
                );

            world.registerPlayer(
                actor,
                "item-on-ground-registration"
            );
            world.start();

            ContentInteractionResult first=
                dispatch(
                    world,
                    actor,
                    registry,
                    ITEM_ID,
                    GROUND_ITEM_ID
                );

            if(first==null||
               !"ITEM_GROUND_HIGH".equals(
                   first.outcome()))
                throw new AssertionError(
                    "high-priority item-on-ground dispatch="+
                    first
                );

            if(!high.get().unregister()||
               high.get().active()||
               high.get().unregister())
                throw new AssertionError(
                    "high item-on-ground handle idempotence"
                );

            assertWinner(
                registry.itemOnGroundItemBinding(
                    ITEM_ID,
                    GROUND_ITEM_ID
                ),
                "item-ground-low",
                10
            );

            ContentInteractionResult fallback=
                dispatch(
                    world,
                    actor,
                    registry,
                    ITEM_ID,
                    GROUND_ITEM_ID
                );

            if(fallback==null||
               !"ITEM_GROUND_LOW".equals(
                   fallback.outcome()))
                throw new AssertionError(
                    "item-on-ground fallback="+fallback
                );

            AtomicReference<ContentRegistration>
                conflictHandle=
                    new AtomicReference<>();

            boolean conflict=false;
            try{
                registry.installCustom(
                    module(
                        "item-ground-conflict",
                        registrar->
                            conflictHandle.set(
                                registrar.itemOnGroundItem(
                                    ITEM_ID,
                                    GROUND_ITEM_ID,
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
                    "equal-priority item-on-ground conflict not fail-closed"
                );

            AtomicReference<ContentRegistration>
                cancelled=
                    new AtomicReference<>();

            registry.installCustom(
                module(
                    "item-ground-cancel-pending",
                    registrar->{
                        ContentRegistration handle=
                            registrar.itemOnGroundItem(
                                ITEM_ID+1,
                                GROUND_ITEM_ID+1,
                                1,
                                context->
                                    ContentInteractionResult.handled(
                                        "SHOULD_NOT_RUN"
                                    )
                            );
                        cancelled.set(handle);

                        if(!handle.unregister())
                            throw new AssertionError(
                                "pending item-on-ground unregister rejected"
                            );
                    }
                )
            );

            if(cancelled.get()==null||
               cancelled.get().active()||
               cancelled.get().unregister()||
               registry.itemOnGroundItemBinding(
                   ITEM_ID+1,
                   GROUND_ITEM_ID+1
               )!=null)
                throw new AssertionError(
                    "pending item-on-ground cancellation committed"
                );

            assertInvalidRegistration(
                registry,
                "item-ground-negative-selected",
                registrar->
                    registrar.itemOnGroundItem(
                        -1,
                        GROUND_ITEM_ID,
                        1,
                        context->
                            ContentInteractionResult.handled(
                                "INVALID"
                            )
                    )
            );

            assertInvalidRegistration(
                registry,
                "item-ground-negative-target",
                registrar->
                    registrar.itemOnGroundItem(
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
                    actor,
                    registry,
                    ITEM_ID+99,
                    GROUND_ITEM_ID+99
                );

            if(unknown!=null)
                throw new AssertionError(
                    "unknown item-on-ground resolved"
                );

            System.out.println(
                "CONTENT_ITEM_ON_GROUND_ITEM_REGISTRATION_PASS "+
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
        int itemId,
        int groundItemId
    )throws Exception{
        AtomicReference<ContentInteractionResult>
            result=
                new AtomicReference<>();

        world.submitAndWait(
            actor,
            ()->
                result.set(
                    registry.dispatchItemOnGroundItem(
                        itemId,
                        groundItemId,
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
                "invalid item-on-ground registration accepted: "+
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
           !"ITEM_ON_GROUND_ITEM".equals(info.kind))
            throw new AssertionError(
                "unexpected item-on-ground binding winner "+
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

    private ContentItemOnGroundItemRegistrationTest(){}
}
