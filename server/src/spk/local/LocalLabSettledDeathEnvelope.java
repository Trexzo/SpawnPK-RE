package spk.local;

import java.util.Objects;

/**
 * Immutable, invocation-time evidence that one CUSTOM_LOCALLAB Monster Spawner
 * death has ALREADY been finalized and its drop settlement receipt agrees
 * with the canonical finalization identity.
 *
 * This is a precondition/audit envelope, NOT a durable outbox event ID.
 * EntityId is process-local; neither World identity nor a save ACK is stored.
 * It cannot be used to grant, replay, or infer the original SpawnPK policy.
 */
final class LocalLabSettledDeathEnvelope {
    enum OriginStatus {
        CURRENT_SPAWN_RECIPIENT,
        SAME_NAME_REBOUND,
        NO_CURRENT_RECIPIENT,
        MISSING_SPAWN_ORIGIN,
        STALE_SPAWN_RECIPIENT
    }

    static final String AUTHORITY="CUSTOM_LOCALLAB_OBSERVATION_ONLY";

    final EntityId npcId;
    final int definitionId;
    final Tile deathTile;
    final long deathTick;
    final String recipientRef;
    final long spawnRecipientGeneration;
    final long observedCurrentGeneration;
    // In-process identity only; different players can have equal generations.
    final WorldPlayer observedCurrentPlayer;
    final OriginStatus originStatus;

    private LocalLabSettledDeathEnvelope(
        MonsterSpawnerNpcDeathFinalizationService.Result death,
        MonsterSpawnerPvmRuntime.RecipientOrigin original,
        long observedGeneration,
        WorldPlayer observedPlayer,
        OriginStatus status
    ){
        npcId=death.npcId;
        definitionId=death.definitionId;
        deathTile=death.deathTile;
        deathTick=death.deathTick;
        recipientRef=death.recipientRef;
        spawnRecipientGeneration=original==null?-1L:original.generation;
        observedCurrentGeneration=observedGeneration;
        observedCurrentPlayer=observedPlayer;
        originStatus=Objects.requireNonNull(status,"originStatus");
    }

    boolean spawnRecipientStillCurrent(){
        return originStatus==OriginStatus.CURRENT_SPAWN_RECIPIENT;
    }

    static LocalLabSettledDeathEnvelope observe(
        World expectedWorld,
        MonsterSpawnerPvmRuntime.FinalizeResult terminal
    ){
        World world=Objects.requireNonNull(expectedWorld,"expectedWorld");
        MonsterSpawnerPvmRuntime.FinalizeResult result=
            Objects.requireNonNull(terminal,"terminal");

        if(result.sourceWorld!=world)
            throw new IllegalStateException(
                "SETTLED_DEATH_ENVELOPE_SOURCE_WORLD_MISMATCH");

        if(result.status!=MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED||
           result.finalization==null||result.settlement==null)
            throw new IllegalArgumentException(
                "SETTLED_DEATH_ENVELOPE_REQUIRES_SETTLED_FINALIZED");

        MonsterSpawnerNpcDeathFinalizationService.Result death=
            result.finalization;
        NpcDropGroundSettlementService.Receipt ground=
            result.settlement;

        // A settlement whose NPC/death identity diverges must never
        // serve as the basis for consumer delivery or future outbox design.
        if(!Objects.equals(death.npcId,ground.npcId)||
           death.definitionId!=ground.definitionId||
           !Objects.equals(death.deathTile,ground.deathTile)||
           death.deathTick!=ground.deathTick||
           !Objects.equals(death.recipientRef,ground.recipientRef))
            throw new IllegalStateException(
                "SETTLED_DEATH_ENVELOPE_SETTLEMENT_IDENTITY_MISMATCH");

        WorldPlayer observed=world.players().byName(death.recipientRef);
        long currentGeneration=
            observed==null?-1L:observed.generation();
        MonsterSpawnerPvmRuntime.RecipientOrigin origin=
            result.recipientOrigin;
        OriginStatus status;

        if(origin==null)
            status=OriginStatus.MISSING_SPAWN_ORIGIN;
        else if(observed==null)
            status=OriginStatus.NO_CURRENT_RECIPIENT;
        else if(origin.player!=observed)
            status=OriginStatus.SAME_NAME_REBOUND;
        else if(!death.recipientRef.equals(origin.recipientRef)||
                origin.generation!=currentGeneration||
                !origin.stillCurrentIn(world))
            status=OriginStatus.STALE_SPAWN_RECIPIENT;
        else
            status=OriginStatus.CURRENT_SPAWN_RECIPIENT;

        return new LocalLabSettledDeathEnvelope(
            death,origin,currentGeneration,observed,status);
    }
}
