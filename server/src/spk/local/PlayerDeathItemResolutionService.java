package spk.local;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Semantic carried-item disposition for one canonical player death.
 *
 * The service never executes caller policy. Callers first obtain an immutable
 * death preview, decide kept amounts outside player ownership, then submit the
 * decisions against that exact preview. Commit revalidates death identity and
 * carried state under WorldPlayer.mutationLock().
 */
final class PlayerDeathItemResolutionService {
    enum Source {
        INVENTORY,
        EQUIPMENT
    }

    static final class CarriedLine {
        final int lineId;
        final Source source;
        final int sourceIndex;
        final EquipmentSlot equipmentSlot;
        final int itemId;
        final int quantity;

        private CarriedLine(
            int lineId,
            Source source,
            int sourceIndex,
            EquipmentSlot equipmentSlot,
            int itemId,
            int quantity
        ){
            this.lineId=lineId;
            this.source=Objects.requireNonNull(source,"source");
            this.sourceIndex=sourceIndex;
            this.equipmentSlot=equipmentSlot;
            this.itemId=itemId;
            this.quantity=quantity;
        }
    }

    static final class DeathPreview {
        final EntityId playerId;
        final long deathTick;
        final long deathSequence;
        final String deathCause;
        final EntityId responsiblePlayerId;
        final Tile deathTile;
        final boolean riskAtDeath;
        final TeleportNavigationService.EntryKind riskSourceKind;
        final long riskRevision;
        final List<CarriedLine> carried;

        private DeathPreview(
            EntityId playerId,
            long deathTick,
            long deathSequence,
            String deathCause,
            EntityId responsiblePlayerId,
            Tile deathTile,
            boolean riskAtDeath,
            TeleportNavigationService.EntryKind riskSourceKind,
            long riskRevision,
            List<CarriedLine> carried
        ){
            this.playerId=playerId;
            this.deathTick=deathTick;
            this.deathSequence=deathSequence;
            this.deathCause=deathCause;
            this.responsiblePlayerId=responsiblePlayerId;
            this.deathTile=deathTile;
            this.riskAtDeath=riskAtDeath;
            this.riskSourceKind=riskSourceKind;
            this.riskRevision=riskRevision;
            this.carried=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        carried
                    )
                );
        }
    }

    static final class Decision {
        final int lineId;
        final int keptAmount;

        Decision(
            int lineId,
            int keptAmount
        ){
            if(lineId<=0)
                throw new IllegalArgumentException(
                    "lineId="+lineId
                );
            if(keptAmount<0)
                throw new IllegalArgumentException(
                    "keptAmount="+keptAmount
                );

            this.lineId=lineId;
            this.keptAmount=keptAmount;
        }
    }

    static final class Disposition {
        final CarriedLine line;
        final int keptAmount;
        final int lostAmount;

        private Disposition(
            CarriedLine line,
            int keptAmount
        ){
            this.line=line;
            this.keptAmount=keptAmount;
            this.lostAmount=
                line.quantity-keptAmount;
        }
    }

    static final class Resolution {
        final EntityId playerId;
        final long deathTick;
        final long deathSequence;
        final String deathCause;
        final EntityId responsiblePlayerId;
        final Tile deathTile;
        final boolean riskAtDeath;
        final TeleportNavigationService.EntryKind riskSourceKind;
        final long riskRevision;
        final List<Disposition> dispositions;
        final String policyAuthority;

        private Resolution(
            DeathPreview preview,
            List<Disposition> dispositions,
            String policyAuthority
        ){
            this.playerId=preview.playerId;
            this.deathTick=preview.deathTick;
            this.deathSequence=preview.deathSequence;
            this.deathCause=preview.deathCause;
            this.responsiblePlayerId=preview.responsiblePlayerId;
            this.deathTile=preview.deathTile;
            this.riskAtDeath=preview.riskAtDeath;
            this.riskSourceKind=preview.riskSourceKind;
            this.riskRevision=preview.riskRevision;
            this.dispositions=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        dispositions
                    )
                );
            this.policyAuthority=policyAuthority;
        }

        int keptTotalQuantity(){
            long total=0L;

            for(Disposition disposition:
                    dispositions)
                total+=
                    disposition.keptAmount;

            return total>
                    Integer.MAX_VALUE
                ?Integer.MAX_VALUE
                :(int)total;
        }

        int lostTotalQuantity(){
            long total=0L;

            for(Disposition disposition:
                    dispositions)
                total+=
                    disposition.lostAmount;

            return total>
                    Integer.MAX_VALUE
                ?Integer.MAX_VALUE
                :(int)total;
        }
    }

    private final WorldPlayer player;
    private final BankState bank;
    private final EquipmentState equipment;
    private final PlayerLifecycleState lifecycle;
    private final String policyAuthority;

    private final LinkedHashMap<Long,Resolution>
        resolvedByDeathSequence=
            new LinkedHashMap<>();

    PlayerDeathItemResolutionService(
        WorldPlayer player,
        String policyAuthority
    ){
        this.player=
            Objects.requireNonNull(
                player,
                "player"
            );
        this.bank=player.bank();
        this.equipment=player.equipment();
        this.lifecycle=player.lifecycle();
        this.policyAuthority=
            requireGameplayAuthority(
                policyAuthority
            );
    }

    DeathPreview previewCurrentDeath(){
        synchronized(player.mutationLock()){
            return previewLocked();
        }
    }

    Resolution resolveCurrentDeath(
        DeathPreview preview,
        Collection<Decision> decisions
    ){
        DeathPreview expected=
            Objects.requireNonNull(
                preview,
                "preview"
            );
        Objects.requireNonNull(
            decisions,
            "decisions"
        );

        List<Disposition> dispositions=
            validateDecisions(
                expected.carried,
                new ArrayList<>(
                    decisions
                )
            );

        synchronized(player.mutationLock()){
            requireExactCurrentDeath(
                expected
            );

            Resolution existing=
                resolvedByDeathSequence.get(
                    expected.deathSequence
                );

            if(existing!=null)
                return existing;

            List<CarriedLine> current=
                snapshotCarriedLocked();

            if(!sameCarried(
                    expected.carried,
                    current))
                throw new IllegalStateException(
                    "carried item state changed after death preview id="+
                    player.id()
                );

            Resolution result=
                new Resolution(
                    expected,
                    dispositions,
                    policyAuthority
                );

            resolvedByDeathSequence.put(
                expected.deathSequence,
                result
            );

            return result;
        }
    }

    Resolution get(
        long deathSequence
    ){
        synchronized(player.mutationLock()){
            return resolvedByDeathSequence.get(
                deathSequence
            );
        }
    }

    int size(){
        synchronized(player.mutationLock()){
            return resolvedByDeathSequence.size();
        }
    }

    List<Resolution> snapshot(){
        synchronized(player.mutationLock()){
            return Collections.unmodifiableList(
                new ArrayList<>(
                    resolvedByDeathSequence.values()
                )
            );
        }
    }

    String policyAuthority(){
        return policyAuthority;
    }

    private DeathPreview previewLocked(){
        if(!lifecycle.dead())
            throw new IllegalStateException(
                "player is not dead id="+
                player.id()
            );

        long deathTick=
            lifecycle.deathTick();
        long deathSequence=
            lifecycle.deathSequence();

        if(deathTick<0L)
            throw new IllegalStateException(
                "dead player missing death tick id="+
                player.id()
            );

        if(deathSequence<=0L)
            throw new IllegalStateException(
                "dead player missing death sequence id="+
                player.id()
            );

        LocalRiskZoneState.Snapshot risk=
            lifecycle.deathRiskSnapshot();

        return new DeathPreview(
            player.id(),
            deathTick,
            deathSequence,
            safeCause(
                lifecycle.cause()
            ),
            lifecycle.responsiblePlayerId(),
            lifecycle.deathTile(),
            risk!=null&&risk.risk(),
            risk==null
                ?null
                :risk.sourceKind,
            risk==null
                ?-1L
                :risk.revision,
            snapshotCarriedLocked()
        );
    }

    private void requireExactCurrentDeath(
        DeathPreview expected
    ){
        if(!player.id().equals(
                expected.playerId))
            throw new IllegalArgumentException(
                "death preview belongs to another player expected="+
                player.id()+
                " actual="+
                expected.playerId
            );

        if(!lifecycle.dead()||
           lifecycle.deathTick()!=
                expected.deathTick||
           lifecycle.deathSequence()!=
                expected.deathSequence||
           !safeCause(
                lifecycle.cause()
            ).equals(
                expected.deathCause)||
           !Objects.equals(
                lifecycle.responsiblePlayerId(),
                expected.responsiblePlayerId)||
           !Objects.equals(
                lifecycle.deathTile(),
                expected.deathTile)||
           !sameRiskSnapshot(
                lifecycle.deathRiskSnapshot(),
                expected))
            throw new IllegalStateException(
                "player death identity changed after preview id="+
                player.id()
            );
    }

    private static boolean sameRiskSnapshot(
        LocalRiskZoneState.Snapshot current,
        DeathPreview expected
    ){
        if(current==null)
            return !expected.riskAtDeath&&
                expected.riskSourceKind==null&&
                expected.riskRevision==-1L;

        return current.risk()==expected.riskAtDeath&&
            current.sourceKind==expected.riskSourceKind&&
            current.revision==expected.riskRevision;
    }

    private List<CarriedLine>
        snapshotCarriedLocked()
    {
        ArrayList<CarriedLine> lines=
            new ArrayList<>();
        int lineId=1;

        for(int slot=0;
            slot<BankState.INVENTORY_CAPACITY;
            slot++){
            BankState.Stack stack=
                bank.inventoryAt(
                    slot
                );

            if(stack==null||
               stack.qty<=0)
                continue;

            if(stack.itemId<0)
                throw new IllegalStateException(
                    "invalid inventory item id slot="+
                    slot+
                    " item="+
                    stack.itemId
                );

            lines.add(
                new CarriedLine(
                    lineId++,
                    Source.INVENTORY,
                    slot,
                    null,
                    stack.itemId,
                    stack.qty
                )
            );
        }

        for(int index=0;
            index<EquipmentState.EQUIPMENT_SLOTS;
            index++){
            int itemId=
                equipment.itemAt(
                    index
                );
            int quantity=
                equipment.quantityAt(
                    index
                );

            if(itemId<0)
                continue;

            if(quantity<=0)
                throw new IllegalStateException(
                    "invalid equipment quantity index="+
                    index+
                    " item="+
                    itemId+
                    " quantity="+
                    quantity
                );

            lines.add(
                new CarriedLine(
                    lineId++,
                    Source.EQUIPMENT,
                    index,
                    EquipmentSlot
                        .fromEquipmentIndex(
                            index
                        ),
                    itemId,
                    quantity
                )
            );
        }

        return Collections.unmodifiableList(
            lines
        );
    }

    private static List<Disposition>
        validateDecisions(
            List<CarriedLine> lines,
            List<Decision> decisions
        ){
        HashMap<Integer,CarriedLine> byId=
            new HashMap<>();

        for(CarriedLine line:lines)
            byId.put(
                line.lineId,
                line
            );

        if(decisions.size()!=
                lines.size())
            throw new IllegalArgumentException(
                "death disposition coverage mismatch lines="+
                lines.size()+
                " decisions="+
                decisions.size()
            );

        HashMap<Integer,Integer> kept=
            new HashMap<>();
        Set<Integer> seen=
            new HashSet<>();

        for(Decision decision:decisions){
            Decision checked=
                Objects.requireNonNull(
                    decision,
                    "decision"
                );

            CarriedLine line=
                byId.get(
                    checked.lineId
                );

            if(line==null)
                throw new IllegalArgumentException(
                    "unknown death disposition lineId="+
                    checked.lineId
                );

            if(!seen.add(
                    checked.lineId))
                throw new IllegalArgumentException(
                    "duplicate death disposition lineId="+
                    checked.lineId
                );

            if(checked.keptAmount>
                    line.quantity)
                throw new IllegalArgumentException(
                    "kept amount exceeds carried quantity lineId="+
                    line.lineId+
                    " kept="+
                    checked.keptAmount+
                    " quantity="+
                    line.quantity
                );

            kept.put(
                checked.lineId,
                checked.keptAmount
            );
        }

        ArrayList<Disposition> out=
            new ArrayList<>();

        for(CarriedLine line:lines){
            Integer amount=
                kept.get(
                    line.lineId
                );

            if(amount==null)
                throw new IllegalArgumentException(
                    "missing death disposition lineId="+
                    line.lineId
                );

            out.add(
                new Disposition(
                    line,
                    amount
                )
            );
        }

        return out;
    }

    private static boolean sameCarried(
        List<CarriedLine> left,
        List<CarriedLine> right
    ){
        if(left.size()!=right.size())
            return false;

        for(int i=0;
            i<left.size();
            i++){
            CarriedLine a=left.get(i);
            CarriedLine b=right.get(i);

            if(a.lineId!=b.lineId||
               a.source!=b.source||
               a.sourceIndex!=b.sourceIndex||
               a.equipmentSlot!=b.equipmentSlot||
               a.itemId!=b.itemId||
               a.quantity!=b.quantity)
                return false;
        }

        return true;
    }

    private static String safeCause(
        String value
    ){
        return value==null||
               value.trim().isEmpty()
            ?"UNSPECIFIED"
            :value;
    }

    private static String requireGameplayAuthority(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "policyAuthority"
            );

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                "policyAuthority blank"
            );

        if("EXACT_CURRENT_CLIENT".equals(
                clean)||
           "UNKNOWN_SERVER_AUTHORITY".equals(
                clean))
            throw new IllegalArgumentException(
                "client/unknown authority cannot define death item policy actual="+
                clean
            );

        return clean;
    }
}
