package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * G1 Rocktail consumable composition.
 *
 * Recovered client authority contributes only the semantic Eat action for item
 * 15272. Heal amount and cadence are explicit LocalLab gameplay policy.
 */
final class G1RocktailConsumableHandler {
    static final String AUTHORITY=
        "LOCAL_LAB_POLICY_G1_ROCKTAIL_CONSUMABLE_V1";

    static final int ITEM_ID=15272;
    static final int HEAL_AMOUNT=10;
    static final long EAT_COOLDOWN_TICKS=3L;

    enum Status {
        CONSUMED,
        DEAD,
        FULL_HITPOINTS,
        CADENCE_BLOCKED,
        STALE_SLOT,
        INVALID_STACK
    }

    static final class Result {
        final Status status;
        final int beforeHitpoints;
        final int afterHitpoints;
        final int maximumHitpoints;
        final long nextEligibleTick;
        final String saveReason;

        Result(
            Status status,
            int beforeHitpoints,
            int afterHitpoints,
            int maximumHitpoints,
            long nextEligibleTick,
            String saveReason
        ){
            this.status=status;
            this.beforeHitpoints=beforeHitpoints;
            this.afterHitpoints=afterHitpoints;
            this.maximumHitpoints=maximumHitpoints;
            this.nextEligibleTick=nextEligibleTick;
            this.saveReason=saveReason;
        }

        boolean consumed(){
            return status==Status.CONSUMED;
        }

        @Override public String toString(){
            return "Result{status="+status+
                ",hp="+beforeHitpoints+
                "->"+afterHitpoints+
                ",max="+maximumHitpoints+
                ",nextEat="+nextEligibleTick+
                "}";
        }
    }

    private final WorldPlayer player;
    private final BankState bank;
    private final HitpointsRecoveryService recovery;
    private final CombatSkillProgressionService progression;

    private long nextEligibleTick;

    G1RocktailConsumableHandler(
        WorldPlayer player,
        BankState bank
    ){
        this.player=
            Objects.requireNonNull(
                player,
                "player"
            );
        this.bank=
            Objects.requireNonNull(
                bank,
                "bank"
            );
        this.recovery=
            new HitpointsRecoveryService(
                player
            );
        this.progression=
            new CombatSkillProgressionService(
                player,
                new LocalLabCombatXpCurve()
            );

        if(ItemDefinitionRepository.isStackable(
                ITEM_ID))
            throw new IllegalStateException(
                "G1 Rocktail must remain non-stackable item="+
                ITEM_ID
            );
    }

    Result handle(
        ItemContainerAction action,
        long worldTick,
        ServerPacketWriter writer
    )throws IOException{
        Objects.requireNonNull(
            action,
            "action"
        );
        Objects.requireNonNull(
            writer,
            "writer"
        );

        InventoryActionRouter.Resolution semantic=
            InventoryActionRouter.resolve(
                action
            );

        if(action.itemId!=ITEM_ID||
           !semantic.resolved()||
           !semantic.is("Eat"))
            return null;

        if(worldTick<0L)
            throw new IllegalArgumentException(
                "worldTick="+worldTick
            );

        synchronized(player.mutationLock()){
            int before=
                player.playerState()
                    .currentLevel(
                        PlayerState.HITPOINTS
                    );

            if(player.lifecycle().dead())
                return new Result(
                    Status.DEAD,
                    before,
                    before,
                    0,
                    nextEligibleTick,
                    null
                );

            CombatSkillProgressionService.Snapshot
                hitpoints=
                    progression.snapshot(
                        CombatSkillProgressionService
                            .Skill.HITPOINTS
                    );

            int maximum=
                hitpoints.baseLevel;

            if(before>=maximum)
                return new Result(
                    Status.FULL_HITPOINTS,
                    before,
                    before,
                    maximum,
                    nextEligibleTick,
                    null
                );

            if(worldTick<nextEligibleTick)
                return new Result(
                    Status.CADENCE_BLOCKED,
                    before,
                    before,
                    maximum,
                    nextEligibleTick,
                    null
                );

            BankState.InventorySlotSnapshot slot=
                bank.inventorySlotSnapshot(
                    action.slot
                );

            if(!slot.occupied||
               slot.itemId!=ITEM_ID)
                return new Result(
                    Status.STALE_SLOT,
                    before,
                    before,
                    maximum,
                    nextEligibleTick,
                    null
                );

            if(slot.quantity!=1)
                return new Result(
                    Status.INVALID_STACK,
                    before,
                    before,
                    maximum,
                    nextEligibleTick,
                    null
                );

            BankState.PreparedInventoryMutation
                prepared=
                    bank.prepareConsumeInventoryAll(
                        action.slot,
                        ITEM_ID
                    );

            if(!prepared.accepted()||
               prepared.result!=1)
                return new Result(
                    Status.STALE_SLOT,
                    before,
                    before,
                    maximum,
                    nextEligibleTick,
                    null
                );

            int expectedAfter=
                Math.min(
                    maximum,
                    Math.addExact(
                        before,
                        HEAL_AMOUNT
                    )
                );

            long nextTick=
                Math.addExact(
                    worldTick,
                    EAT_COOLDOWN_TICKS
                );

            if(writer.batchActive())
                throw new IllegalStateException(
                    "Rocktail consumable requires caller outside packet batch"
                );

            writer.beginBatch();
            boolean ended=false;

            try{
                bank.writePreparedInventoryMutation(
                    prepared,
                    writer
                );

                writer.fixed(
                    134,
                    BootstrapPackets.skill134(
                        PlayerState.HITPOINTS,
                        player.playerState()
                            .xp(
                                PlayerState.HITPOINTS
                            ),
                        expectedAfter
                    )
                );

                writer.endBatch();
                ended=true;
            }finally{
                if(!ended)
                    try{
                        writer.abortBatch();
                    }catch(Throwable ignored){}
            }

            HitpointsRecoveryService.Result healed=
                recovery.heal(
                    HEAL_AMOUNT,
                    maximum,
                    hitpoints.curveAuthority
                );

            if(!healed.healed()||
               healed.before!=before||
               healed.after!=expectedAfter)
                throw new IllegalStateException(
                    "Rocktail heal commit drifted expected="+
                    before+"->"+expectedAfter+
                    " actual="+
                    healed.before+"->"+healed.after+
                    " status="+
                    healed.status
                );

            try{
                bank.commitPreparedInventoryMutation(
                    prepared
                );
            }catch(Throwable failure){
                player.playerState()
                    .setCurrentLevel(
                        PlayerState.HITPOINTS,
                        before
                    );
                throw failure;
            }

            nextEligibleTick=
                nextTick;

            return new Result(
                Status.CONSUMED,
                before,
                expectedAfter,
                maximum,
                nextEligibleTick,
                "G1_ROCKTAIL_CONSUMED"
            );
        }
    }

    long nextEligibleTick(){
        return nextEligibleTick;
    }
}
