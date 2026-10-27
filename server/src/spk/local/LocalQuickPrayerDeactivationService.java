package spk.local;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * P5.9 LOCAL_LAB_POLICY: current-book Quick Prayer OFF widget 4999.
 *
 * A single queue-only recoverable batch publishes all active config36=0
 * values and exact OFF control token. Semantic prayer deactivation is
 * applied only after that entire batch is admitted.
 *
 * This is not a client ACK, original-game death auto-clear policy, or
 * proof that other prayer mutations serialize on WorldPlayer.mutationLock.
 */
final class LocalQuickPrayerDeactivationService {
    static final String AUTHORITY=
        "LOCAL_LAB_POLICY_P59_QUICK_OFF_ATOMIC";

    private final WorldPlayer player;
    private final World canonicalWorld;
    private final java.util.function.LongSupplier worldGeneration;

    // Isolated preexisting smoke callers retain their historical mode.
    LocalQuickPrayerDeactivationService(WorldPlayer player){
        this(null,player,null);
    }

    // Production LocalSession is locked to this World generation.
    LocalQuickPrayerDeactivationService(
        World world,
        WorldPlayer player,
        java.util.function.LongSupplier generation
    ){
        if((world==null)!=(generation==null))
            throw new IllegalArgumentException("World and generation must match");
        this.canonicalWorld=world;
        this.worldGeneration=generation;
        this.player=Objects.requireNonNull(player,"player");
    }

    String deactivate(ServerPacketWriter writer)throws IOException{
        Objects.requireNonNull(writer,"writer");
        if(canonicalWorld!=null){
            final String[] response=new String[1];
            boolean admitted=canonicalWorld.withOpenPlayerOwnershipIfCurrent(
                player,worldGeneration.getAsLong(),
                ()->response[0]=deactivateCurrent(writer)
            );
            return admitted?response[0]:"REJECTED_STALE_WORLD_OWNER";
        }
        return deactivateCurrent(writer);
    }

    private String deactivateCurrent(ServerPacketWriter writer)throws IOException{
        synchronized(player.mutationLock()){
            if(player.lifecycle().dead()||
               player.playerState().currentLevel(
                   PlayerState.HITPOINTS)<=0)
                return "REJECTED_PLAYER_DEAD";

            PrayerDefinitionRepository.Book book=
                player.prayers().book();

            List<PrayerDefinitionRepository.Def> active=
                new ArrayList<>();
            for(PrayerDefinitionRepository.Def def:
                    PrayerDefinitionRepository.all()){
                if(def.book==book&&
                   player.prayers().activeWidget(def.widget))
                    active.add(def);
            }

            ServerPacketWriter.RecoverablePacketResult accepted=
                writer.publishQueueOnlyRecoverablePacketIfIdle(()->{
                    for(PrayerDefinitionRepository.Def def:active)
                        writer.fixed(36,
                            BootstrapPackets.config36(def.varp,0));

                    QuickPrayerSelectionPresentation.publishActive(
                        writer,false);
                });

            if(accepted!=
               ServerPacketWriter.RecoverablePacketResult.COMMITTED)
                return "REJECTED_WRITER_BACKPRESSURE_RETRYABLE";

            player.prayers().recordPublishedQuickDeactivation(
                book,active);

            return "QUICK_PRAYERS_OFF book="+book+
                " deactivated="+active.size()+
                " authority="+AUTHORITY+
                " queueAdmittedNotClientAck=true"+
                " currentBookOnly=true"+
                " drainCombatDeathClearUnverified=true";
        }
    }
}
