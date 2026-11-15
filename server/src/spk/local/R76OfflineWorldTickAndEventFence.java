package spk.local;

import java.util.Objects;

/**
 * R76 OFFLINE-ONLY owner admission for WorldPulse timed effects and
 * WorldTickTarget callbacks, plus explicitly tagged WorldEventQueue events.
 *
 * R74 quiescence is shared across test-only callback surfaces, never
 * installed into production World/LocalSession by a constructor.
 * Arbitrary untagged events are NOT claimed to have a known owner.
 */
final class R76OfflineWorldTickAndEventFence {
    private final World world;
    private final R74OfflineCommandIngressFence commandFence;
    private boolean installed;

    R76OfflineWorldTickAndEventFence(
        World world,R74OfflineCommandIngressFence commandFence
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.commandFence=Objects.requireNonNull(
            commandFence,"command fence"
        );
    }

    synchronized void installForTest(){
        if(installed)
            throw new IllegalStateException(
                "R76_CROSS_TICK_GATE_DENIED already installed"
            );

        world.pulse().installOfflineTickAdmissionForTest(
            (player,generation)->
                !commandFence.isQuiesced(player,generation)
        );

        world.events().installOfflineOwnedEventAdmissionForTest(
            new WorldEventQueue.OfflineOwnedEventAdmission(){
                @Override public boolean permits(
                    EntityId ownerId,long generation
                ){
                    WorldPlayer player=world.players().byId(ownerId);
                    return player!=null &&
                        world.players().owns(player,generation) &&
                        !commandFence.isQuiesced(player,generation);
                }

                @Override public void dispatch(
                    EntityId ownerId,long generation,Runnable task
                ){
                    WorldPlayer player=world.players().byId(ownerId);
                    if(player==null)
                        return;

                    try{
                        world.withOpenPlayerMutationOwnershipIfCurrent(
                            player,generation,()->{
                                // World ownership callback holds the
                                // player's real mutation lock here.
                                if(!commandFence.isQuiesced(
                                        player,generation))
                                    task.run();
                            }
                        );
                    }catch(Exception error){
                        throw new IllegalStateException(
                            "R76_OWNER_EVENT_HANDLER_FAILED",error
                        );
                    }
                }
            }
        );

        installed=true;
    }
}
