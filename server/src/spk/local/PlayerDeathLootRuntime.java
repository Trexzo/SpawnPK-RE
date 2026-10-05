package spk.local;

import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * World-owned LocalLab composition for lethal PvP death loot.
 *
 * Policy is explicit CUSTOM_LOCALLAB. Exact death identity and settlement
 * idempotence remain delegated to the existing semantic services.
 */
final class PlayerDeathLootRuntime {
    enum Status {
        SETTLED,
        PENDING
    }

    static final String SETTLEMENT_AUTHORITY =
        "CUSTOM_LOCALLAB_PVP_DEATH_GROUND_SETTLEMENT_V1";

    static final class Result {
        final Status status;
        final long deathSequence;
        final String recipientRef;
        final PlayerDeathGroundSettlementService.Receipt receipt;
        final String failure;

        private Result(
            Status status,
            long deathSequence,
            String recipientRef,
            PlayerDeathGroundSettlementService.Receipt receipt,
            String failure
        ){
            this.status =
                Objects.requireNonNull(
                    status,
                    "status"
                );
            this.deathSequence = deathSequence;
            this.recipientRef = recipientRef;
            this.receipt = receipt;
            this.failure = failure;
        }
    }

    private static final class Entry {
        final WorldPlayer victim;
        final long victimGeneration;
        final long deathSequence;
        final String recipientRef;
        final PlayerDeathItemResolutionService.Resolution resolution;

        Entry(
            WorldPlayer victim,
            long victimGeneration,
            long deathSequence,
            String recipientRef,
            PlayerDeathItemResolutionService.Resolution resolution
        ){
            this.victim = victim;
            this.victimGeneration = victimGeneration;
            this.deathSequence = deathSequence;
            this.recipientRef = recipientRef;
            this.resolution = resolution;
        }
    }

    private final World world;
    private final LocalLabPvpDeathPolicy policy =
        new LocalLabPvpDeathPolicy();
    private final LinkedHashMap<EntityId,Entry> pending =
        new LinkedHashMap<>();
    private final LinkedHashMap<EntityId,PlayerDeathGroundSettlementService>
        settlements =
            new LinkedHashMap<>();

    PlayerDeathLootRuntime(
        World world
    ){
        this.world =
            Objects.requireNonNull(
                world,
                "world"
            );
    }

    synchronized Result settlePvp(
        WorldPlayer attacker,
        WorldPlayer victim
    ){
        WorldPlayer checkedAttacker =
            Objects.requireNonNull(
                attacker,
                "attacker"
            );
        WorldPlayer checkedVictim =
            Objects.requireNonNull(
                victim,
                "victim"
            );

        if(checkedAttacker == checkedVictim)
            throw new IllegalArgumentException(
                "PvP death attacker/victim must differ"
            );

        long attackerGeneration =
            checkedAttacker.generation();
        long victimGeneration =
            checkedVictim.generation();

        if(!world.players().owns(
                checkedAttacker,
                attackerGeneration
            )||
           !world.players().owns(
                checkedVictim,
                victimGeneration
            ))
            throw new IllegalStateException(
                "PvP death participants are not current World owners"
            );

        if(!checkedVictim.lifecycle().dead())
            throw new IllegalStateException(
                "PvP death victim is not dead"
            );

        String recipient =
            requireRef(
                checkedAttacker.username(),
                "attacker username"
            );

        long deathSequence =
            checkedVictim.lifecycle()
                .deathSequence();

        Entry entry =
            pending.get(
                checkedVictim.id()
            );

        if(entry == null||
           entry.deathSequence != deathSequence){
            PlayerDeathItemResolutionService deaths =
                new PlayerDeathItemResolutionService(
                    checkedVictim,
                    LocalLabPvpDeathPolicy.AUTHORITY
                );

            PlayerDeathItemResolutionService.DeathPreview preview =
                deaths.previewCurrentDeath();

            PlayerDeathItemResolutionService.Resolution resolution =
                deaths.resolveCurrentDeath(
                    preview,
                    policy.decide(
                        preview
                    )
                );

            entry =
                new Entry(
                    checkedVictim,
                    victimGeneration,
                    deathSequence,
                    recipient,
                    resolution
                );

            pending.put(
                checkedVictim.id(),
                entry
            );
        }else{
            if(entry.victim != checkedVictim||
               entry.victimGeneration != victimGeneration||
               !entry.recipientRef.equals(
                    recipient
                ))
                throw new IllegalStateException(
                    "conflicting PvP death loot identity victim="+
                    checkedVictim.id()+
                    " deathSequence="+
                    deathSequence
                );
        }

        PlayerDeathGroundSettlementService settlement =
            settlements.get(
                checkedVictim.id()
            );

        if(settlement == null){
            settlement =
                new PlayerDeathGroundSettlementService(
                    world,
                    checkedVictim,
                    SETTLEMENT_AUTHORITY,
                    PlayerDeathGroundSettlementService
                        .OWNER_SCOPED_DEATH_TILE
                );

            settlements.put(
                checkedVictim.id(),
                settlement
            );
        }

        try{
            PlayerDeathGroundSettlementService.Receipt receipt =
                settlement.settle(
                    entry.resolution,
                    entry.recipientRef
                );

            world.playerCarriedPresentationEvents()
                .enqueueDeathPostimage(
                    System.currentTimeMillis(),
                    checkedVictim,
                    victimGeneration,
                    deathSequence
                );

            pending.remove(
                checkedVictim.id()
            );

            return new Result(
                Status.SETTLED,
                deathSequence,
                recipient,
                receipt,
                null
            );
        }catch(RuntimeException failure){
            return new Result(
                Status.PENDING,
                deathSequence,
                recipient,
                null,
                failure.getClass().getSimpleName()+
                    ":"+
                    String.valueOf(
                        failure.getMessage()
                    )
            );
        }
    }

    synchronized boolean hasPending(
        EntityId victimId
    ){
        return pending.containsKey(
            Objects.requireNonNull(
                victimId,
                "victimId"
            )
        );
    }

    synchronized int pendingCount(){
        return pending.size();
    }

    private static String requireRef(
        String value,
        String label
    ){
        if(value == null)
            throw new NullPointerException(
                label
            );

        String clean =
            value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                label
            );

        return clean;
    }
}
