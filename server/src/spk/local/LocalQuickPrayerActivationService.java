package spk.local;

import java.io.IOException;
import java.util.*;

/**
 * CUSTOM_LOCALLAB Quick Prayer ON for pinned 5000 widget.
 *
 * Source-authoritative selected keys are mapped to existing visible
 * prayer definitions, preflighted under WorldPlayer mutation ownership,
 * and emitted as one recoverable queue-backed packet batch. Only after
 * queue admission are activation booleans changed. No prayer drain,
 * conflict resolution, damage protection, or durable selection policy
 * is asserted. Queue admission is not a client ACK.
 */
final class LocalQuickPrayerActivationService {
    static final String AUTHORITY=
        "LOCAL_LAB_POLICY_G123_QUICK_ON_ACTIVATION";

    private final WorldPlayer player;
    private final QuickPrayerSelectionService selection;

    LocalQuickPrayerActivationService(
        WorldPlayer player,
        QuickPrayerSelectionService selection
    ){
        this.player=Objects.requireNonNull(player,"player");
        this.selection=Objects.requireNonNull(selection,"selection");
    }

    String activate(ServerPacketWriter writer)throws IOException{
        Objects.requireNonNull(writer,"writer");
        synchronized(player.mutationLock()){
            QuickPrayerSelectionService.Snapshot snapshot=
                selection.snapshot();
            if(snapshot.editing)
                return "REJECTED_SELECTION_DRAFT_OPEN";
            if(player.lifecycle().dead())
                return "REJECTED_PLAYER_DEAD";
            List<String> keys=snapshot.confirmed(snapshot.currentBook);
            if(keys.isEmpty())
                return "REJECTED_NO_CONFIRMED_PRAYERS";
            int points=player.playerState().currentLevel(
                PlayerState.PRAYER);
            if(points<=0)
                return "REJECTED_NO_PRAYER_POINTS";

            PrayerDefinitionRepository.Book book=
                snapshot.currentBook==QuickPrayerSelectionService.Book.NORMAL
                    ?PrayerDefinitionRepository.Book.NORMAL
                    :PrayerDefinitionRepository.Book.CURSES;
            if(player.prayers().book()!=book)
                return "REJECTED_BOOK_CHANGED";

            ArrayList<PrayerDefinitionRepository.Def> resolved=
                new ArrayList<>();
            HashSet<Integer> uniqueWidgets=new HashSet<>();
            for(String key:keys){
                QuickPrayerSelectionService.Option selected=null;
                for(QuickPrayerSelectionService.Option option:
                        QuickPrayerSelectionService.available(
                            snapshot.currentBook)){
                    if(option.prayerKey.equals(key)){
                        selected=option;
                        break;
                    }
                }
                if(selected==null)
                    return "REJECTED_UNRECOGNIZED_SELECTION";
                PrayerDefinitionRepository.Def match=null;
                for(PrayerDefinitionRepository.Def def:
                        PrayerDefinitionRepository.all()){
                    if(def.book==book&&
                       def.name.equals(selected.displayName)){
                        match=def;
                        break;
                    }
                }
                if(match==null||!uniqueWidgets.add(match.widget))
                    return "REJECTED_UNRESOLVED_PRAYER";
                if(points<match.level)
                    return "REJECTED_PRAYER_LEVEL name="+match.name+
                        " required="+match.level+" current="+points;
                resolved.add(match);
            }

            ServerPacketWriter.RecoverablePacketResult queued=
                writer.publishQueueOnlyRecoverablePacketIfIdle(()->{
                    for(PrayerDefinitionRepository.Def def:resolved){
                        if(!player.prayers().activeWidget(def.widget))
                            writer.fixed(36,
                                BootstrapPackets.config36(def.varp,1));
                    }
                    QuickPrayerSelectionPresentation.publishActive(
                        writer,true);
                });
            if(queued!=ServerPacketWriter.RecoverablePacketResult.COMMITTED)
                return "REJECTED_WRITER_BACKPRESSURE_RETRYABLE";

            // No source-state change until the exact whole S2C batch
            // has been atomically admitted. Retrying is idempotent.
            player.prayers().recordPublishedQuickActivation(resolved);
            return "QUICK_PRAYERS_ON book="+snapshot.currentBook+
                " selected="+resolved.size()+
                " authority="+AUTHORITY+
                " queueAdmittedNotClientAck=true"+
                " drainAndCombatEffectsUnverified=true";
        }
    }
}
