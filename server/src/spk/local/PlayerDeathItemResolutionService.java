package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure semantic carried-item disposition for one player death.
 *
 * This service snapshots canonical inventory/equipment and asks caller-owned
 * gameplay policy how much of each exact carried line is kept. It does not
 * mutate items or create loot.
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

    static final class DeathContext {
        final EntityId playerId;
        final long deathTick;
        final String deathCause;
        final List<CarriedLine> carried;

        private DeathContext(
            EntityId playerId,
            long deathTick,
            String deathCause,
            List<CarriedLine> carried
        ){
            this.playerId=playerId;
            this.deathTick=deathTick;
            this.deathCause=deathCause;
            this.carried=Collections.unmodifiableList(
                new ArrayList<>(carried)
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
        final String deathCause;
        final List<Disposition> dispositions;
        final String policyAuthority;

        private Resolution(
            EntityId playerId,
            long deathTick,
            String deathCause,
            List<Disposition> dispositions,
            String policyAuthority
        ){
            this.playerId=playerId;
            this.deathTick=deathTick;
            this.deathCause=deathCause;
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

    interface DispositionPolicy {
        List<Decision> resolve(
            DeathContext context
        );

        String authority();
    }

    private final WorldPlayer player;
    private final BankState bank;
    private final EquipmentState equipment;
    private final PlayerLifecycleState lifecycle;
    private final DispositionPolicy policy;
    private final String policyAuthority;

    private final LinkedHashMap<Long,Resolution>
        resolvedByDeathTick=
            new LinkedHashMap<>();

    PlayerDeathItemResolutionService(
        WorldPlayer player,
        DispositionPolicy policy
    ){
        this.player=
            Objects.requireNonNull(
                player,
                "player"
            );
        this.bank=
            player.bank();
        this.equipment=
            player.equipment();
        this.lifecycle=
            player.lifecycle();
        this.policy=
            Objects.requireNonNull(
                policy,
                "policy"
            );
        this.policyAuthority=
            requireGameplayAuthority(
                policy.authority()
            );
    }

    Resolution resolveCurrentDeath(){
        synchronized(player.mutationLock()){
            if(!lifecycle.dead())
                throw new IllegalStateException(
                    "player is not dead id="+
                    player.id()
                );

            long deathTick=
                lifecycle.deathTick();

            if(deathTick<0L)
                throw new IllegalStateException(
                    "dead player missing death tick id="+
                    player.id()
                );

            Resolution existing=
                resolvedByDeathTick.get(
                    deathTick
                );

            if(existing!=null)
                return existing;

            String cause=
                safeCause(
                    lifecycle.cause()
                );

            List<CarriedLine> before=
                snapshotCarried();

            DeathContext context=
                new DeathContext(
                    player.id(),
                    deathTick,
                    cause,
                    before
                );

            List<Decision> decisions=
                Objects.requireNonNull(
                    policy.resolve(
                        context
                    ),
                    "death disposition decisions"
                );

            List<Disposition> dispositions=
                validateDecisions(
                    before,
                    decisions
                );

            if(!lifecycle.dead()||
               lifecycle.deathTick()!=
                    deathTick)
                throw new IllegalStateException(
                    "player death identity changed during disposition resolution id="+
                    player.id()
                );

            List<CarriedLine> after=
                snapshotCarried();

            if(!sameCarried(
                    before,
                    after))
                throw new IllegalStateException(
                    "carried item state changed during death disposition resolution id="+
                    player.id()
                );

            Resolution result=
                new Resolution(
                    player.id(),
                    deathTick,
                    cause,
                    dispositions,
                    policyAuthority
                );

            resolvedByDeathTick.put(
                deathTick,
                result
            );

            return result;
        }
    }

    synchronized Resolution get(
        long deathTick
    ){
        return resolvedByDeathTick.get(
            deathTick
        );
    }

    synchronized int size(){
        return resolvedByDeathTick.size();
    }

    synchronized List<Resolution> snapshot(){
        return Collections.unmodifiableList(
            new ArrayList<>(
                resolvedByDeathTick.values()
            )
        );
    }

    String policyAuthority(){
        return policyAuthority;
    }

    private List<CarriedLine> snapshotCarried(){
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
            CarriedLine a=
                left.get(i);
            CarriedLine b=
                right.get(i);

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

        String clean=
            value.trim();

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
