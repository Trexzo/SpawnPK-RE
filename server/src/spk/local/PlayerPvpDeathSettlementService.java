package spk.local;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Failure-atomic player-vs-player death settlement.
 *
 * Lock order is deliberately:
 *   victim mutationLock -> GroundItemRegistry
 *
 * Presentation occurs only after both canonical owners have committed and both
 * locks have been released.
 */
final class PlayerPvpDeathSettlementService {
    static final String ITEM_SETTLEMENT_AUTHORITY=
        "CUSTOM_LOCALLAB_PVP_DEATH_ITEM_SETTLEMENT_V1";
    static final String GROUND_SETTLEMENT_AUTHORITY=
        "CUSTOM_LOCALLAB_PVP_DEATH_GROUND_SETTLEMENT_V1";

    static final class Result {
        final EntityId victimId;
        final long victimGeneration;
        final long deathTick;
        final long deathSequence;
        final Tile deathTile;
        final String recipientRef;
        final String policyAuthority;
        final PlayerDeathItemSettlementService.Settlement
            items;
        final PlayerDeathGroundLootSettlementService.Receipt
            ground;

        private Result(
            WorldPlayer victim,
            long victimGeneration,
            Tile deathTile,
            String recipientRef,
            String policyAuthority,
            PlayerDeathItemSettlementService.Settlement items,
            PlayerDeathGroundLootSettlementService.Receipt ground
        ){
            this.victimId=victim.id();
            this.victimGeneration=
                victimGeneration;
            this.deathTick=
                items.deathTick;
            this.deathSequence=
                items.deathSequence;
            this.deathTile=
                Objects.requireNonNull(
                    deathTile,
                    "deathTile"
                );
            this.recipientRef=
                requireText(
                    recipientRef,
                    "recipientRef"
                );
            this.policyAuthority=
                requireGameplayAuthority(
                    policyAuthority
                );
            this.items=
                Objects.requireNonNull(
                    items,
                    "items"
                );
            this.ground=
                Objects.requireNonNull(
                    ground,
                    "ground"
                );
        }
    }

    private static final class DeathKey {
        final EntityId victimId;
        final long deathSequence;

        DeathKey(
            EntityId victimId,
            long deathSequence
        ){
            this.victimId=
                Objects.requireNonNull(
                    victimId,
                    "victimId"
                );
            this.deathSequence=
                deathSequence;
        }

        @Override public boolean equals(
            Object other
        ){
            if(this==other)
                return true;
            if(!(other instanceof DeathKey))
                return false;

            DeathKey key=(DeathKey)other;

            return deathSequence==
                    key.deathSequence&&
                victimId.equals(
                    key.victimId
                );
        }

        @Override public int hashCode(){
            int result=victimId.hashCode();
            result=31*result+
                Long.valueOf(
                    deathSequence
                ).hashCode();
            return result;
        }
    }

    private final World world;
    private final PlayerDeathItemDecisionPolicy policy;
    private final PlayerDeathGroundLootSettlementService groundLoot;
    private final Object resultLock=
        new Object();
    private final LinkedHashMap<DeathKey,Result>
        results=
            new LinkedHashMap<>();

    PlayerPvpDeathSettlementService(
        World world
    ){
        this(
            world,
            new LocalLabPvpDeathItemPolicy()
        );
    }

    PlayerPvpDeathSettlementService(
        World world,
        PlayerDeathItemDecisionPolicy policy
    ){
        this.world=
            Objects.requireNonNull(
                world,
                "world"
            );
        this.policy=
            Objects.requireNonNull(
                policy,
                "policy"
            );

        requireGameplayAuthority(
            policy.authority()
        );

        this.groundLoot=
            new PlayerDeathGroundLootSettlementService(
                world,
                GROUND_SETTLEMENT_AUTHORITY,
                PlayerDeathGroundLootSettlementService
                    .OWNER_SCOPED_DEATH_TILE
            );
    }

    Result settle(
        WorldPlayer victim,
        long expectedVictimGeneration,
        Tile deathTile,
        String recipientRef
    ){
        WorldPlayer checkedVictim=
            Objects.requireNonNull(
                victim,
                "victim"
            );
        Tile checkedTile=
            Objects.requireNonNull(
                deathTile,
                "deathTile"
            );
        String checkedRecipient=
            requireText(
                recipientRef,
                "recipientRef"
            );

        if(!world.players().owns(
                checkedVictim,
                expectedVictimGeneration))
            throw new IllegalStateException(
                "PvP death victim ownership changed id="+
                checkedVictim.id()+
                " expectedGeneration="+
                expectedVictimGeneration+
                " actualGeneration="+
                checkedVictim.generation()
            );

        PlayerDeathGroundLootSettlementService.CanonicalCommit
            canonicalCommit;
        Result result;

        synchronized(checkedVictim.mutationLock()){
            if(!world.players().owns(
                    checkedVictim,
                    expectedVictimGeneration))
                throw new IllegalStateException(
                    "PvP death victim ownership changed under mutation lock id="+
                    checkedVictim.id()
                );

            long deathSequence=
                checkedVictim.lifecycle()
                    .deathSequence();

            if(deathSequence<=0L)
                throw new IllegalStateException(
                    "PvP death victim missing death sequence id="+
                    checkedVictim.id()
                );

            DeathKey key=
                new DeathKey(
                    checkedVictim.id(),
                    deathSequence
                );

            Result existing=
                existingResult(
                    key,
                    expectedVictimGeneration,
                    checkedTile,
                    checkedRecipient
                );

            if(existing!=null)
                return existing;

            if(!checkedVictim.lifecycle()
                    .dead())
                throw new IllegalStateException(
                    "PvP death settlement requires dead victim id="+
                    checkedVictim.id()
                );

            PlayerDeathItemResolutionService resolver=
                new PlayerDeathItemResolutionService(
                    checkedVictim,
                    policy.authority()
                );

            PlayerDeathItemResolutionService.DeathPreview
                preview=
                    resolver.previewCurrentDeath();

            List<PlayerDeathItemResolutionService.Decision>
                decisions=
                    Objects.requireNonNull(
                        policy.decide(
                            preview
                        ),
                        "policy decisions"
                    );

            PlayerDeathItemResolutionService.Resolution
                resolution=
                    resolver.resolveCurrentDeath(
                        preview,
                        decisions
                    );

            PlayerDeathItemSettlementService
                itemSettlement=
                    new PlayerDeathItemSettlementService(
                        checkedVictim,
                        ITEM_SETTLEMENT_AUTHORITY
                    );

            PlayerDeathItemSettlementService.Settlement
                settledItems;

            synchronized(world.groundItems()){
                /*
                 * The ground preimage is proven before item mutation. Holding
                 * the registry monitor across both commits makes that prepared
                 * preimage immutable until canonical ground commit.
                 */
                PlayerDeathGroundLootSettlementService.PreparedLoot
                    preparedGround=
                        groundLoot.prepare(
                            resolution,
                            checkedTile,
                            checkedRecipient
                        );

                settledItems=
                    itemSettlement.settle(
                        resolution
                    );

                canonicalCommit=
                    groundLoot
                        .commitPreparedCanonical(
                            preparedGround,
                            settledItems
                        );
            }

            result=
                new Result(
                    checkedVictim,
                    expectedVictimGeneration,
                    checkedTile,
                    checkedRecipient,
                    policy.authority(),
                    settledItems,
                    canonicalCommit.receipt
                );

            recordResult(
                key,
                result
            );
        }

        /*
         * Presentation lookup touches PlayerRegistry, so it is deliberately
         * outside both victim mutation and GroundItemRegistry monitors.
         */
        groundLoot.publishCommitted(
            canonicalCommit
        );

        return result;
    }

    Result get(
        EntityId victimId,
        long deathSequence
    ){
        synchronized(resultLock){
            return results.get(
                new DeathKey(
                    victimId,
                    deathSequence
                )
            );
        }
    }

    int size(){
        synchronized(resultLock){
            return results.size();
        }
    }

    String policyAuthority(){
        return policy.authority();
    }

    private Result existingResult(
        DeathKey key,
        long expectedVictimGeneration,
        Tile deathTile,
        String recipientRef
    ){
        synchronized(resultLock){
            Result existing=
                results.get(
                    key
                );

            if(existing==null)
                return null;

            if(existing.victimGeneration!=
                    expectedVictimGeneration||
               !existing.deathTile.equals(
                    deathTile
                )||
               !existing.recipientRef.equals(
                    recipientRef
                ))
                throw new IllegalStateException(
                    "conflicting PvP death settlement replay id="+
                    key.victimId+
                    " deathSequence="+
                    key.deathSequence
                );

            return existing;
        }
    }

    private void recordResult(
        DeathKey key,
        Result result
    ){
        synchronized(resultLock){
            if(results.containsKey(
                    key))
                throw new IllegalStateException(
                    "PvP death settlement result raced id="+
                    key.victimId+
                    " deathSequence="+
                    key.deathSequence
                );

            results.put(
                key,
                result
            );
        }
    }

    private static String requireGameplayAuthority(
        String value
    ){
        String clean=
            requireText(
                value,
                "policyAuthority"
            );

        if("EXACT_CURRENT_CLIENT".equals(clean)||
           "UNKNOWN_SERVER_AUTHORITY".equals(clean))
            throw new IllegalArgumentException(
                "client/unknown authority cannot define PvP death policy actual="+
                clean
            );

        return clean;
    }

    private static String requireText(
        String value,
        String name
    ){
        if(value==null)
            throw new NullPointerException(name);

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(name);

        return clean;
    }
}
