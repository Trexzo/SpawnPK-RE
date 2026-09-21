package spk.local;

import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;

public final class ContentItemOnPlayerRegistrationTest {
    private static final int ITEM_ID=900401;

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(20L);
        WorldPlayer actor=new WorldPlayer();
        WorldPlayer target=new WorldPlayer();

        try{
            actor.movement().setRunEnergy(91);
            target.movement().setRunEnergy(73);

            ContentRegistry registry=world.content();
            AtomicReference<ContentRegistration> low=
                new AtomicReference<>();
            AtomicReference<ContentRegistration> high=
                new AtomicReference<>();

            registry.installCustom(
                module(
                    "item-player-low",
                    registrar->
                        low.set(
                            registrar.itemOnPlayer(
                                ITEM_ID,
                                10,
                                context->{
                                    if(context.itemId()!=ITEM_ID)
                                        throw new AssertionError(
                                            "low item-on-player item mismatch"
                                        );

                                    if(context.target()==null||
                                       context.target().runEnergy()!=73)
                                        throw new AssertionError(
                                            "resolved target facade mismatch"
                                        );

                                    return ContentInteractionResult.handled(
                                        "ITEM_PLAYER_LOW"
                                    );
                                }
                            )
                        )
                )
            );

            registry.installCustom(
                module(
                    "item-player-high",
                    registrar->
                        high.set(
                            registrar.itemOnPlayer(
                                ITEM_ID,
                                20,
                                context->{
                                    if(context.target()==null||
                                       context.target().runEnergy()!=73)
                                        throw new AssertionError(
                                            "high resolved target facade mismatch"
                                        );

                                    return ContentInteractionResult.handled(
                                        "ITEM_PLAYER_HIGH"
                                    );
                                }
                            )
                        )
                )
            );

            if(low.get()==null||
               high.get()==null||
               !low.get().active()||
               !high.get().active())
                throw new AssertionError(
                    "committed item-on-player registrations not active"
                );

            assertWinner(
                registry.itemOnPlayerBinding(
                    ITEM_ID
                ),
                "item-player-high",
                20
            );

            boolean offWorldRejected=false;
            try{
                registry.dispatchItemOnPlayer(
                    ITEM_ID,
                    target
                );
            }catch(IllegalStateException expected){
                offWorldRejected=
                    expected.getMessage().contains(
                        "World execution context"
                    );
            }

            if(!offWorldRejected)
                throw new AssertionError(
                    "off-world item-on-player dispatch accepted"
                );

            world.registerPlayer(
                actor,
                "item-on-player-actor"
            );
            world.registerPlayer(
                target,
                "item-on-player-target"
            );
            world.start();

            ContentInteractionResult first=
                dispatch(
                    world,
                    actor,
                    target,
                    registry,
                    ITEM_ID
                );

            if(first==null||
               !"ITEM_PLAYER_HIGH".equals(
                   first.outcome()))
                throw new AssertionError(
                    "high-priority item-on-player dispatch="+
                    first
                );

            if(actor.movement().runEnergy()!=91||
               target.movement().runEnergy()!=73)
                throw new AssertionError(
                    "target resolution mutated player state"
                );

            if(!high.get().unregister()||
               high.get().active()||
               high.get().unregister())
                throw new AssertionError(
                    "high item-on-player handle idempotence"
                );

            assertWinner(
                registry.itemOnPlayerBinding(
                    ITEM_ID
                ),
                "item-player-low",
                10
            );

            ContentInteractionResult fallback=
                dispatch(
                    world,
                    actor,
                    target,
                    registry,
                    ITEM_ID
                );

            if(fallback==null||
               !"ITEM_PLAYER_LOW".equals(
                   fallback.outcome()))
                throw new AssertionError(
                    "item-on-player fallback="+fallback
                );

            AtomicReference<ContentRegistration>
                conflictHandle=
                    new AtomicReference<>();

            boolean conflict=false;
            try{
                registry.installCustom(
                    module(
                        "item-player-conflict",
                        registrar->
                            conflictHandle.set(
                                registrar.itemOnPlayer(
                                    ITEM_ID,
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
                    "equal-priority item-on-player conflict not fail-closed"
                );

            AtomicReference<ContentRegistration>
                cancelled=
                    new AtomicReference<>();

            registry.installCustom(
                module(
                    "item-player-cancel-pending",
                    registrar->{
                        ContentRegistration handle=
                            registrar.itemOnPlayer(
                                ITEM_ID+1,
                                1,
                                context->
                                    ContentInteractionResult.handled(
                                        "SHOULD_NOT_RUN"
                                    )
                            );
                        cancelled.set(handle);

                        if(!handle.unregister())
                            throw new AssertionError(
                                "pending item-on-player unregister rejected"
                            );
                    }
                )
            );

            if(cancelled.get()==null||
               cancelled.get().active()||
               cancelled.get().unregister()||
               registry.itemOnPlayerBinding(
                   ITEM_ID+1
               )!=null)
                throw new AssertionError(
                    "pending item-on-player cancellation committed"
                );

            boolean invalid=false;
            try{
                registry.installCustom(
                    module(
                        "item-player-negative-item",
                        registrar->
                            registrar.itemOnPlayer(
                                -1,
                                1,
                                context->
                                    ContentInteractionResult.handled(
                                        "INVALID"
                                    )
                            )
                    )
                );
            }catch(IllegalArgumentException expected){
                invalid=true;
            }

            if(!invalid)
                throw new AssertionError(
                    "negative item id accepted"
                );

            ContentInteractionResult unknown=
                dispatch(
                    world,
                    actor,
                    target,
                    registry,
                    ITEM_ID+99
                );

            if(unknown!=null)
                throw new AssertionError(
                    "unknown item-on-player resolved"
                );

            System.out.println(
                "CONTENT_ITEM_ON_PLAYER_REGISTRATION_PASS "+
                "priority=true "+
                "fallback=true "+
                "equalPriorityFailClosed=true "+
                "pendingCancellation=true "+
                "worldThreadGuard=true "+
                "resolvedTargetFacade=true "+
                "playerIndex=false "+
                "widgetIdentity=false "+
                "inventorySlot=false "+
                "packetIdentity=false"
            );
        }finally{
            if(target.registered())
                world.unregisterPlayer(target);
            if(actor.registered())
                world.unregisterPlayer(actor);
            world.close();
        }
    }

    private static ContentInteractionResult dispatch(
        World world,
        WorldPlayer actor,
        WorldPlayer target,
        ContentRegistry registry,
        int itemId
    )throws Exception{
        AtomicReference<ContentInteractionResult>
            result=
                new AtomicReference<>();

        world.submitAndWait(
            actor,
            ()->
                result.set(
                    registry.dispatchItemOnPlayer(
                        itemId,
                        target
                    )
                ),
            5_000L
        );

        return result.get();
    }

    private static void assertWinner(
        ContentRegistry.BindingInfo info,
        String moduleId,
        int priority
    ){
        if(info==null||
           !moduleId.equals(info.moduleId)||
           info.priority!=priority||
           !"ITEM_ON_PLAYER".equals(info.kind))
            throw new AssertionError(
                "unexpected item-on-player binding winner "+
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

    private ContentItemOnPlayerRegistrationTest(){}
}
