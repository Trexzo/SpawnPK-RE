package spk.local;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mandatory PvP death-settlement runtime.
 *
 * A lethal player attack registers one exact death obligation. The victim's
 * world tick consumes that obligation before respawn preparation. Settlement
 * failures propagate and leave the obligation pending for retry.
 */
final class PvpDeathSettlementRuntime {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB";

    static final class Pending {
        final WorldPlayer victim;
        final long victimGeneration;
        final long deathSequence;
        final long deathTick;
        final String lootOwner;

        Pending(
            WorldPlayer victim,
            long victimGeneration,
            long deathSequence,
            long deathTick,
            String lootOwner
        ){
            this.victim=
                Objects.requireNonNull(
                    victim,
                    "victim"
                );
            this.victimGeneration=
                victimGeneration;
            this.deathSequence=
                deathSequence;
            this.deathTick=
                deathTick;
            this.lootOwner=
                requireOwner(
                    lootOwner
                );
        }

        boolean same(
            WorldPlayer otherVictim,
            long otherGeneration,
            long otherSequence,
            long otherTick,
            String otherOwner
        ){
            return victim==otherVictim&&
                victimGeneration==
                    otherGeneration&&
                deathSequence==
                    otherSequence&&
                deathTick==
                    otherTick&&
                lootOwner.equals(
                    requireOwner(
                        otherOwner
                    )
                );
        }
    }

    static final class PresentationDebt {
        final WorldPlayer victim;
        final long victimGeneration;
        final long deathSequence;

        PresentationDebt(
            WorldPlayer victim,
            long victimGeneration,
            long deathSequence
        ){
            this.victim=
                Objects.requireNonNull(
                    victim,
                    "victim"
                );
            this.victimGeneration=
                victimGeneration;
            this.deathSequence=
                deathSequence;
        }

        boolean same(
            WorldPlayer otherVictim,
            long otherGeneration,
            long otherSequence
        ){
            return victim==otherVictim&&
                victimGeneration==otherGeneration&&
                deathSequence==otherSequence;
        }
    }

    static final class SettlementResult {
        final Pending pending;
        final LocalLabPvpDeathPolicy.Result policy;
        final PlayerDeathItemSettlementService.Receipt receipt;

        SettlementResult(
            Pending pending,
            LocalLabPvpDeathPolicy.Result policy,
            PlayerDeathItemSettlementService.Receipt receipt
        ){
            this.pending=
                Objects.requireNonNull(
                    pending,
                    "pending"
                );
            this.policy=
                Objects.requireNonNull(
                    policy,
                    "policy"
                );
            this.receipt=
                Objects.requireNonNull(
                    receipt,
                    "receipt"
                );
        }
    }

    static final class RespawnGateResult {
        final SettlementResult settlement;
        final PlayerLifecycleService.PreparedRespawn
            preparedRespawn;

        RespawnGateResult(
            SettlementResult settlement,
            PlayerLifecycleService.PreparedRespawn
                preparedRespawn
        ){
            this.settlement=settlement;
            this.preparedRespawn=preparedRespawn;
        }
    }

    private final World world;
    private final LocalLabPvpDeathPolicy policy=
        new LocalLabPvpDeathPolicy();
    private final Map<EntityId,Pending> pendingByVictim=
        new HashMap<>();
    private final Map<EntityId,PlayerDeathItemSettlementService>
        settlementByVictim=
            new HashMap<>();
    private final Map<EntityId,PresentationDebt>
        presentationDebtByVictim=
            new HashMap<>();

    PvpDeathSettlementRuntime(
        World world
    ){
        this.world=
            Objects.requireNonNull(
                world,
                "world"
            );
    }

    synchronized Pending registerPending(
        WorldPlayer killer,
        WorldPlayer victim,
        long expectedVictimGeneration,
        long worldTick
    ){
        WorldPlayer checkedKiller=
            Objects.requireNonNull(
                killer,
                "killer"
            );
        WorldPlayer checkedVictim=
            Objects.requireNonNull(
                victim,
                "victim"
            );

        if(checkedKiller==checkedVictim)
            throw new IllegalArgumentException(
                "PvP death killer and victim must differ"
            );

        long killerGeneration=
            checkedKiller.generation();

        if(!world.players().owns(
                checkedKiller,
                killerGeneration
            ))
            throw new IllegalStateException(
                "PvP death killer ownership changed"
            );

        if(!world.players().owns(
                checkedVictim,
                expectedVictimGeneration
            ))
            throw new IllegalStateException(
                "PvP death victim ownership changed"
            );

        PlayerLifecycleState lifecycle=
            checkedVictim.lifecycle();

        if(!lifecycle.dead()||
           lifecycle.deathSequence()<=0L||
           lifecycle.deathTick()!=worldTick)
            throw new IllegalStateException(
                "PvP death obligation requires exact lethal lifecycle"
            );

        String owner=
            requireOwner(
                checkedKiller.username()
            );

        Pending existing=
            pendingByVictim.get(
                checkedVictim.id()
            );

        if(existing!=null){
            if(!existing.same(
                    checkedVictim,
                    expectedVictimGeneration,
                    lifecycle.deathSequence(),
                    lifecycle.deathTick(),
                    owner))
                throw new IllegalStateException(
                    "conflicting pending PvP death victim="+
                    checkedVictim.id()
                );

            return existing;
        }

        Pending pending=
            new Pending(
                checkedVictim,
                expectedVictimGeneration,
                lifecycle.deathSequence(),
                lifecycle.deathTick(),
                owner
            );

        /*
         * Publish the obligation before returning. From this point a later
         * victim tick can never prepare respawn without first attempting it.
         */
        pendingByVictim.put(
            checkedVictim.id(),
            pending
        );

        return pending;
    }

    RespawnGateResult settleAndPrepareRespawn(
        WorldPlayer victim,
        PlayerLifecycleService lifecycle,
        long worldTick
    )throws Exception{
        Objects.requireNonNull(
            victim,
            "victim"
        );
        Objects.requireNonNull(
            lifecycle,
            "lifecycle"
        );

        SettlementResult settlement=
            settlePendingBeforeRespawn(
                victim
            );

        if(hasPresentationDebt(victim))
            return new RespawnGateResult(
                settlement,
                null
            );

        PlayerLifecycleService.PreparedRespawn
            prepared=
                lifecycle.prepareRespawn(
                    worldTick
                );

        return new RespawnGateResult(
            settlement,
            prepared
        );
    }

    SettlementResult settlePendingBeforeRespawn(
        WorldPlayer victim
    )throws Exception{
        WorldPlayer checked=
            Objects.requireNonNull(
                victim,
                "victim"
            );

        final Pending pending;
        final PlayerDeathItemSettlementService settlement;

        synchronized(this){
            pending=
                pendingByVictim.get(
                    checked.id()
                );

            if(pending==null)
                return null;

            if(pending.victim!=checked||
               pending.victimGeneration!=
                    checked.generation())
                throw new IllegalStateException(
                    "stale pending PvP death ownership victim="+
                    checked.id()
                );

            settlement=
                settlementByVictim.computeIfAbsent(
                    checked.id(),
                    ignored->
                        new PlayerDeathItemSettlementService(
                            world,
                            checked
                        )
                );
        }

        if(!world.players().owns(
                checked,
                pending.victimGeneration
            ))
            throw new IllegalStateException(
                "pending PvP death victim no longer owned"
            );

        PlayerLifecycleState lifecycle=
            checked.lifecycle();

        if(!lifecycle.dead()||
           lifecycle.deathSequence()!=
                pending.deathSequence||
           lifecycle.deathTick()!=
                pending.deathTick)
            throw new IllegalStateException(
                "pending PvP death identity changed victim="+
                checked.id()
            );

        PlayerDeathItemResolutionService resolver=
            new PlayerDeathItemResolutionService(
                checked,
                AUTHORITY+
                    "_"+
                    LocalLabPvpDeathPolicy.POLICY_ID
            );

        PlayerDeathItemResolutionService.DeathPreview
            preview=
                resolver.previewCurrentDeath();

        if(preview.deathSequence!=
                pending.deathSequence||
           preview.deathTick!=
                pending.deathTick)
            throw new IllegalStateException(
                "PvP death preview identity drift"
            );

        LocalLabPvpDeathPolicy.Result policyResult=
            policy.decide(
                preview
            );

        PlayerDeathItemResolutionService.Resolution
            resolution=
                resolver.resolveCurrentDeath(
                    preview,
                    policyResult.decisions
                );

        PlayerDeathItemSettlementService.Receipt receipt=
            settlement.settle(
                resolution,
                pending.lootOwner
            );

        synchronized(this){
            Pending current=
                pendingByVictim.get(
                    checked.id()
                );

            if(current!=pending)
                throw new IllegalStateException(
                    "pending PvP death changed during settlement"
                );

            if(receipt.lostTotalQuantity>0){
                PresentationDebt existingDebt=
                    presentationDebtByVictim.get(
                        checked.id()
                    );

                if(existingDebt!=null&&
                   !existingDebt.same(
                        checked,
                        pending.victimGeneration,
                        pending.deathSequence
                    ))
                    throw new IllegalStateException(
                        "conflicting PvP death presentation debt victim="+
                        checked.id()
                    );

                if(existingDebt==null)
                    presentationDebtByVictim.put(
                        checked.id(),
                        new PresentationDebt(
                            checked,
                            pending.victimGeneration,
                            pending.deathSequence
                        )
                    );
            }

            pendingByVictim.remove(
                checked.id()
            );
        }

        return new SettlementResult(
            pending,
            policyResult,
            receipt
        );
    }

    synchronized Pending pending(
        WorldPlayer victim
    ){
        return victim==null
            ?null
            :pendingByVictim.get(
                victim.id()
            );
    }

    synchronized boolean hasPending(
        WorldPlayer victim
    ){
        return pending(victim)!=null;
    }

    synchronized PresentationDebt presentationDebt(
        WorldPlayer victim
    ){
        return victim==null
            ?null
            :presentationDebtByVictim.get(
                victim.id()
            );
    }

    synchronized boolean hasPresentationDebt(
        WorldPlayer victim
    ){
        return presentationDebt(victim)!=null;
    }

    synchronized void markPresentationCommitted(
        WorldPlayer victim,
        long expectedGeneration,
        long deathSequence
    ){
        if(victim==null)
            throw new NullPointerException(
                "victim"
            );

        PresentationDebt debt=
            presentationDebtByVictim.get(
                victim.id()
            );

        if(debt==null)
            throw new IllegalStateException(
                "no PvP death presentation debt victim="+
                victim.id()
            );

        if(!debt.same(
                victim,
                expectedGeneration,
                deathSequence
            ))
            throw new IllegalStateException(
                "PvP death presentation debt identity changed victim="+
                victim.id()
            );

        presentationDebtByVictim.remove(
            victim.id()
        );
    }

    synchronized int presentationDebtCount(){
        return presentationDebtByVictim.size();
    }

    synchronized void retirePlayer(
        WorldPlayer player,
        long expectedGeneration
    ){
        if(player==null)
            return;

        Pending pending=
            pendingByVictim.get(
                player.id()
            );

        if(pending!=null&&
           pending.victim==player&&
           pending.victimGeneration==
                expectedGeneration)
            pendingByVictim.remove(
                player.id()
            );

        settlementByVictim.remove(
            player.id()
        );

        PresentationDebt debt=
            presentationDebtByVictim.get(
                player.id()
            );

        if(debt!=null&&
           debt.victim==player&&
           debt.victimGeneration==
                expectedGeneration)
            presentationDebtByVictim.remove(
                player.id()
            );
    }

    synchronized int pendingCount(){
        return pendingByVictim.size();
    }

    private static String requireOwner(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "lootOwner"
            );
        String clean=value.trim();
        if(clean.isEmpty())
            throw new IllegalArgumentException(
                "lootOwner blank"
            );
        return clean;
    }
}
