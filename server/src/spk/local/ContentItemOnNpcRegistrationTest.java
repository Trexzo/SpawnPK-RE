package spk.local;

import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;

public final class ContentItemOnNpcRegistrationTest {
    private static final int ITEM_ID=900201;
    private static final int NPC_ID=900202;
    private static final int WORLD_X=3200;
    private static final int WORLD_Y=3201;

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
                    "item-npc-low",
                    registrar->
                        low.set(
                            registrar.itemOnNpc(
                                ITEM_ID,
                                NPC_ID,
                                10,
                                context->{
                                    if(context.itemId()!=ITEM_ID||
                                       context.npcDefinitionId()!=NPC_ID||
                                       context.worldX()!=WORLD_X||
                                       context.worldY()!=WORLD_Y)
                                        throw new AssertionError(
                                            "low item-on-npc context mismatch"
                                        );

                                    return ContentInteractionResult.handled(
                                        "ITEM_NPC_LOW"
                                    );
                                }
                            )
                        )
                )
            );

            registry.installCustom(
                module(
                    "item-npc-high",
                    registrar->
                        high.set(
                            registrar.itemOnNpc(
                                ITEM_ID,
                                NPC_ID,
                                20,
                                context->
                                    ContentInteractionResult.handled(
                                        "ITEM_NPC_HIGH"
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
                    "committed item-on-npc registrations not active"
                );

            assertWinner(
                registry.itemOnNpcBinding(
                    ITEM_ID,
                    NPC_ID
                ),
                "item-npc-high",
                20
            );

            boolean offWorldRejected=false;
            try{
                registry.dispatchItemOnNpc(
                    ITEM_ID,
                    NPC_ID,
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
                    "off-world item-on-npc dispatch accepted"
                );

            world.registerPlayer(
                player,
                "item-on-npc-registration"
            );
            world.start();

            ContentInteractionResult first=
                dispatch(
                    world,
                    player,
                    registry,
                    ITEM_ID,
                    NPC_ID
                );

            if(first==null||
               !"ITEM_NPC_HIGH".equals(
                   first.outcome()))
                throw new AssertionError(
                    "high-priority item-on-npc dispatch="+
                    first
                );

            if(!high.get().unregister()||
               high.get().active()||
               high.get().unregister())
                throw new AssertionError(
                    "high item-on-npc handle idempotence"
                );

            assertWinner(
                registry.itemOnNpcBinding(
                    ITEM_ID,
                    NPC_ID
                ),
                "item-npc-low",
                10
            );

            ContentInteractionResult fallback=
                dispatch(
                    world,
                    player,
                    registry,
                    ITEM_ID,
                    NPC_ID
                );

            if(fallback==null||
               !"ITEM_NPC_LOW".equals(
                   fallback.outcome()))
                throw new AssertionError(
                    "item-on-npc fallback="+fallback
                );

            AtomicReference<ContentRegistration>
                conflictHandle=
                    new AtomicReference<>();

            boolean conflict=false;
            try{
                registry.installCustom(
                    module(
                        "item-npc-conflict",
                        registrar->
                            conflictHandle.set(
                                registrar.itemOnNpc(
                                    ITEM_ID,
                                    NPC_ID,
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
                    "equal-priority item-on-npc conflict not fail-closed"
                );

            AtomicReference<ContentRegistration>
                cancelled=
                    new AtomicReference<>();

            registry.installCustom(
                module(
                    "item-npc-cancel-pending",
                    registrar->{
                        ContentRegistration handle=
                            registrar.itemOnNpc(
                                ITEM_ID+1,
                                NPC_ID+1,
                                1,
                                context->
                                    ContentInteractionResult.handled(
                                        "SHOULD_NOT_RUN"
                                    )
                            );
                        cancelled.set(handle);

                        if(!handle.unregister())
                            throw new AssertionError(
                                "pending item-on-npc unregister rejected"
                            );
                    }
                )
            );

            if(cancelled.get()==null||
               cancelled.get().active()||
               cancelled.get().unregister()||
               registry.itemOnNpcBinding(
                   ITEM_ID+1,
                   NPC_ID+1
               )!=null)
                throw new AssertionError(
                    "pending item-on-npc cancellation committed"
                );

            assertInvalidRegistration(
                registry,
                "item-npc-negative-item",
                registrar->
                    registrar.itemOnNpc(
                        -1,
                        NPC_ID,
                        1,
                        context->
                            ContentInteractionResult.handled(
                                "INVALID"
                            )
                    )
            );

            assertInvalidRegistration(
                registry,
                "item-npc-negative-npc",
                registrar->
                    registrar.itemOnNpc(
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
                    NPC_ID+99
                );

            if(unknown!=null)
                throw new AssertionError(
                    "unknown item-on-npc resolved"
                );

            System.out.println(
                "CONTENT_ITEM_ON_NPC_REGISTRATION_PASS "+
                "priority=true "+
                "fallback=true "+
                "equalPriorityFailClosed=true "+
                "pendingCancellation=true "+
                "worldThreadGuard=true "+
                "resolvedNpcIdentity=true "+
                "clientSceneIndex=false "+
                "inventorySlot=false "+
                "widgetIdentity=false "+
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
        int npcDefinitionId
    )throws Exception{
        AtomicReference<ContentInteractionResult>
            result=
                new AtomicReference<>();

        world.submitAndWait(
            player,
            ()->
                result.set(
                    registry.dispatchItemOnNpc(
                        itemId,
                        npcDefinitionId,
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
                "invalid item-on-npc registration accepted: "+
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
           !"ITEM_ON_NPC".equals(info.kind))
            throw new AssertionError(
                "unexpected item-on-npc binding winner "+
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

    private ContentItemOnNpcRegistrationTest(){}
}
