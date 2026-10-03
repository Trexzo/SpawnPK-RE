package spk.local;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Owns LocalLab's sub-tick pet presentation scheduling state.
 *
 * The shared WorldRealtimeQueue remains the execution primitive; this class
 * owns only pet follow/test scheduling policy and transient scheduler state.
 */
final class LocalPetRealtimeScheduler {
    interface SessionBridge {
        ServerPacketWriter sessionPackets();
        String sessionTag();

        default boolean runIfSessionWorldCallbackActive(
            Runnable action
        ){
            Objects.requireNonNull(
                action,
                "session world callback"
            ).run();
            return true;
        }
    }

    private final boolean bootstrap;
    private final World world;
    private final WorldPlayer worldPlayer;
    private final MovementState movement;
    private final NpcRegistry npcs;
    private final LocalPetDropPickupHandler petDropPickup;
    private final LocalPetRuntimeCommandHandler petRuntimeCommands;
    private final LongSupplier ownerGeneration;
    private final SessionBridge bridge;

    private long nextPetFollowAt=Long.MAX_VALUE;
    private boolean petFollowRealtimeScheduled;
    private boolean petTestRealtimeScheduled;

    LocalPetRealtimeScheduler(
        boolean bootstrap,
        World world,
        WorldPlayer worldPlayer,
        MovementState movement,
        NpcRegistry npcs,
        LocalPetDropPickupHandler petDropPickup,
        LocalPetRuntimeCommandHandler petRuntimeCommands,
        SessionBridge bridge
    ){
        this(
            bootstrap,
            world,
            worldPlayer,
            movement,
            npcs,
            petDropPickup,
            petRuntimeCommands,
            worldPlayer::generation,
            bridge
        );
    }

    LocalPetRealtimeScheduler(
        boolean bootstrap,
        World world,
        WorldPlayer worldPlayer,
        MovementState movement,
        NpcRegistry npcs,
        LocalPetDropPickupHandler petDropPickup,
        LocalPetRuntimeCommandHandler petRuntimeCommands,
        LongSupplier ownerGeneration,
        SessionBridge bridge
    ){
        this.bootstrap=bootstrap;
        this.world=Objects.requireNonNull(world,"world");
        this.worldPlayer=Objects.requireNonNull(worldPlayer,"worldPlayer");
        this.movement=Objects.requireNonNull(movement,"movement");
        this.npcs=Objects.requireNonNull(npcs,"npcs");
        this.petDropPickup=Objects.requireNonNull(
            petDropPickup,"petDropPickup");
        this.petRuntimeCommands=Objects.requireNonNull(
            petRuntimeCommands,"petRuntimeCommands");
        this.ownerGeneration=Objects.requireNonNull(
            ownerGeneration,"ownerGeneration");
        this.bridge=Objects.requireNonNull(bridge,"bridge");
    }

    void resetFollowRuntime(){
        nextPetFollowAt=Long.MAX_VALUE;
        petFollowRealtimeScheduled=false;
    }

    void resetFollowDeadline(){
        nextPetFollowAt=Long.MAX_VALUE;
    }

    long followDeadline(){
        return nextPetFollowAt;
    }

    void setFollowDeadline(long value){
        nextPetFollowAt=value;
    }

    boolean followScheduled(){
        return petFollowRealtimeScheduled;
    }

    boolean testSequenceScheduled(){
        return petTestRealtimeScheduled;
    }

    void ensureFollowScheduled(long now){
        if(!bootstrap||
           petDropPickup.pendingPickupBlocksPetFollow()||
           petFollowRealtimeScheduled||
           npcs.followFrozen()||
           !npcs.needsFollow(movement)){
            return;
        }

        if(nextPetFollowAt==Long.MAX_VALUE)
            nextPetFollowAt=now+200L;

        long at=Math.max(now,nextPetFollowAt);

        petFollowRealtimeScheduled=true;

        try{
            world.scheduleRealtime(
                at,
                worldPlayer,
                ownerGeneration.getAsLong(),
                ()->bridge
                    .runIfSessionWorldCallbackActive(
                        this::runPetFollowRealtime
                    )
            );
        }catch(RuntimeException failure){
            petFollowRealtimeScheduled=false;
            throw failure;
        }
    }

    void ensureTestSequenceScheduled(long now){
        ServerPacketWriter writer=bridge.sessionPackets();

        if(!bootstrap||
           petTestRealtimeScheduled||
           !petRuntimeCommands.sequenceActive()||
           writer==null){
            return;
        }

        long due=petRuntimeCommands.sequenceAt();
        long at=Math.max(
            now,
            due==Long.MAX_VALUE
                ?now
                :due
        );

        petTestRealtimeScheduled=true;

        try{
            world.scheduleRealtime(
                at,
                worldPlayer,
                ownerGeneration.getAsLong(),
                ()->bridge
                    .runIfSessionWorldCallbackActive(
                        this::runPetTestSequenceRealtime
                    )
            );
        }catch(RuntimeException failure){
            petTestRealtimeScheduled=false;
            throw failure;
        }
    }

    private void runPetFollowRealtime(){
        petFollowRealtimeScheduled=false;

        ServerPacketWriter writer=bridge.sessionPackets();

        if(!bootstrap||
           petDropPickup.pendingPickupBlocksPetFollow()||
           writer==null||
           npcs.followFrozen()||
           !npcs.needsFollow(movement)){
            nextPetFollowAt=Long.MAX_VALUE;
            return;
        }

        long now=System.currentTimeMillis();

        try{
            String tag=bridge.sessionTag();

            String petFollow=
                npcs.tickFollow(
                    movement,
                    writer
                );

            long nextDelay=
                npcs.needsFollow(movement)
                    ?npcs.followDelayMs(movement)
                    :Long.MAX_VALUE;

            if(petFollow!=null){
                System.out.println(
                    tag+
                    "V512_"+petFollow+
                    " execution=SHARED_WORLD_THREAD initialReactionMs=200 nextDelayMs="+
                    (nextDelay==Long.MAX_VALUE
                        ?"idle"
                        :nextDelay)+
                    " ownerRunning="+
                    npcs.recentOwnerRunning()
                );
            }

            nextPetFollowAt=
                nextDelay==Long.MAX_VALUE
                    ?Long.MAX_VALUE
                    :now+nextDelay;

            if(nextDelay!=Long.MAX_VALUE)
                ensureFollowScheduled(now);
        }catch(Throwable t){
            System.err.println(
                "[world player="+worldPlayer.id()+
                "] pet-follow presentation failed: "+t
            );
        }
    }

    private void runPetTestSequenceRealtime(){
        petTestRealtimeScheduled=false;

        if(!petRuntimeCommands.sequenceActive())
            return;

        long when=System.currentTimeMillis();
        ServerPacketWriter writer=bridge.sessionPackets();

        try{
            String line=
                petRuntimeCommands.tickSequence(
                    when,
                    writer
                );

            if(line!=null)
                System.out.println(
                    bridge.sessionTag()+line
                );
        }catch(Throwable t){
            petRuntimeCommands.failSequence();

            System.err.println(
                "[world player="+worldPlayer.id()+
                "] pet-test sequence failed: "+t
            );
        }

        if(petRuntimeCommands.sequenceActive())
            ensureTestSequenceScheduled(when);
    }
}
