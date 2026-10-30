package spk.local;

import java.util.*;

final class PvmRecordService {
    static final String AUTHORITY=
        "LOCAL_LAB_POLICY_G1_PVM_RECORD_V1";
    static final String NAMESPACE=
        "pvm-record";
    static final String VERSION="1";
    // P5.36 persistent NEGATIVE-ONLY witness: this source-only
    // history cannot be reconstructed from the ordinary kill counter.
    // If a snapshot carrying this bit is restored without matching
    // in-JVM current-session provenance, require manual review.
    // It is not a durable grant, a receipt or a filesystem save ACK.
    static final String SOURCE_RESTART_REVIEW_KEY=
        "source-restart-review-required";
    static final String SOURCE_RESTART_REVIEW_VALUE="true";

    static final class Snapshot {
        final long kills;

        Snapshot(long kills){
            this.kills=kills;
        }

        @Override public String toString(){
            return "PvmRecord{kills="+kills+"}";
        }
    }

    static final class Receipt {
        final boolean applied;
        final boolean replayed;
        final String reason;
        final EntityId npcId;
        final long deathTick;
        final long kills;

        private Receipt(
            boolean applied,
            boolean replayed,
            String reason,
            EntityId npcId,
            long deathTick,
            long kills
        ){
            this.applied=applied;
            this.replayed=replayed;
            this.reason=reason;
            this.npcId=npcId;
            this.deathTick=deathTick;
            this.kills=kills;
        }

        static Receipt applied(
            EntityId npcId,
            long deathTick,
            long kills
        ){
            return new Receipt(
                true,false,"APPLIED",
                npcId,deathTick,kills
            );
        }

        static Receipt replay(
            EntityId npcId,
            long deathTick,
            long kills
        ){
            return new Receipt(
                false,true,"REPLAY",
                npcId,deathTick,kills
            );
        }

        static Receipt rejected(
            String reason,
            EntityId npcId,
            long deathTick,
            long kills
        ){
            return new Receipt(
                false,false,reason,
                npcId,deathTick,kills
            );
        }
    }

    private static final class KillIdentity {
        final EntityId npcId;
        final long deathTick;

        KillIdentity(
            EntityId npcId,
            long deathTick
        ){
            this.npcId=
                Objects.requireNonNull(
                    npcId,
                    "npcId"
                );
            this.deathTick=deathTick;
        }

        @Override public boolean equals(
            Object other
        ){
            if(!(other instanceof KillIdentity))
                return false;

            KillIdentity that=
                (KillIdentity)other;

            return npcId.equals(that.npcId)&&
                deathTick==that.deathTick;
        }

        @Override public int hashCode(){
            return 31*npcId.hashCode()+
                Long.hashCode(deathTick);
        }
    }

    private final World world;
    private final LinkedHashMap<KillIdentity,Receipt>
        receipts=new LinkedHashMap<>();

    /**
     * P5.35: source-ONLY in-memory companion proof for records that
     * passed the P5.34 Wind Strike lethal witness admission. The
     * legacy receipt key is deliberately UNCHANGED. The same NPC ID
     * and World tick may correspond to a different NPC registration
     * (ABA); in that case REJECT, never call it a valid replay or
     * award again. No persisted durable registration identity.
     */
    private static final class SourceDeathReceipt {
        final Object registrationIdentity;
        final LocalLabWindStrikeHitWitness witness;

        SourceDeathReceipt(LocalLabWindStrikeHitWitness witness){
            this.witness=Objects.requireNonNull(witness,"witness");
            this.registrationIdentity=witness.npcRegistrationIdentity;
        }
    }

    private final LinkedHashMap<KillIdentity,SourceDeathReceipt>
        sourceDeathReceipts=new LinkedHashMap<>();

    PvmRecordService(World world){
        this.world=
            Objects.requireNonNull(
                world,
                "world"
            );
    }

    synchronized Receipt recordIfCurrent(
        WorldPlayer player,
        long expectedGeneration,
        EntityId npcId,
        long deathTick
    ){
        return recordIfCurrentInternal(
            player,expectedGeneration,npcId,deathTick,false);
    }

    /**
     * Preserve the historical public source API. Only the explicitly
     * reviewed source path sets the persistent negative-only marker
     * in the SAME snapshot-extension replacement as the kill count.
     */
    private Receipt recordIfCurrentInternal(
        WorldPlayer player,
        long expectedGeneration,
        EntityId npcId,
        long deathTick,
        boolean sourceOnly
    ){
        Objects.requireNonNull(
            player,
            "player"
        );
        Objects.requireNonNull(
            npcId,
            "npcId"
        );

        if(deathTick<0L)
            throw new IllegalArgumentException(
                "deathTick="+deathTick
            );

        KillIdentity identity=
            new KillIdentity(
                npcId,
                deathTick
            );

        Receipt prior=
            receipts.get(
                identity
            );

        if(prior!=null)
            return Receipt.replay(
                npcId,
                deathTick,
                prior.kills
            );

        final Receipt[] result={null};

        try{
            boolean owned=
                world.withOpenPlayerMutationOwnershipIfCurrent(
                    player,
                    expectedGeneration,
                    ()->{
                        SortedMap<String,String> current=
                            player.snapshotExtensions()
                                .namespace(
                                    NAMESPACE
                                );

                        Long before=
                            decodeKills(
                                current
                            );

                        if(before==null){
                            result[0]=
                                Receipt.rejected(
                                    "INVALID_STATE",
                                    npcId,
                                    deathTick,
                                    0L
                                );
                            return;
                        }

                        if(before.longValue()==
                                Long.MAX_VALUE){
                            result[0]=
                                Receipt.rejected(
                                    "OVERFLOW_STATE",
                                    npcId,
                                    deathTick,
                                    before.longValue()
                                );
                            return;
                        }

                        long next=
                            before.longValue()+1L;

                        TreeMap<String,String> replacement=
                            new TreeMap<>();
                        replacement.put(
                            "version",
                            VERSION
                        );
                        replacement.put(
                            "authority",
                            AUTHORITY
                        );
                        replacement.put(
                            "kills",
                            Long.toString(next)
                        );
                        // Do not erase existing source negative
                        // evidence if a legacy NPC policy later
                        // increments the same normal kill counter.
                        // A new source grant writes marker+count in
                        // ONE namespace postimage; no separate update.
                        String retainedMarker=current.get(
                            SOURCE_RESTART_REVIEW_KEY);
                        if(sourceOnly){
                            replacement.put(
                                SOURCE_RESTART_REVIEW_KEY,
                                SOURCE_RESTART_REVIEW_VALUE);
                        }else if(retainedMarker!=null){
                            // Preserve even an INVALID marker in the
                            // legacy writer. Silently deleting corrupt
                            // negative evidence would let a later
                            // source grant bypass mandatory review.
                            replacement.put(
                                SOURCE_RESTART_REVIEW_KEY,
                                retainedMarker);
                        }

                        player.snapshotExtensions()
                            .replaceNamespace(
                                NAMESPACE,
                                replacement
                            );

                        result[0]=
                            Receipt.applied(
                                npcId,
                                deathTick,
                                next
                            );
                    }
                );

            if(!owned)
                return Receipt.rejected(
                    "STALE_PLAYER_GENERATION",
                    npcId,
                    deathTick,
                    0L
                );
        }catch(Exception failure){
            throw new IllegalStateException(
                "PvM record mutation failed player="+
                player.id()+
                " npc="+npcId,
                failure
            );
        }

        Receipt committed=
            Objects.requireNonNull(
                result[0],
                "PvM record result"
            );

        if(committed.applied)
            receipts.put(
                identity,
                committed
            );

        return committed;
    }

    /**
     * P5.34 EXPLICIT source-only, post-settlement LocalLab transition.
     * Never called by normal LocalSession or the Wind Strike handler.
     *
     * Unlike the historical recordIfCurrent(player,generation,id,tick),
     * this requires an actual P5.33 committed lethal-hit object. The
     * *same* current World/player/NPC/lifecycle locks cover the final
     * witness check and the existing record mutation, closing the
     * read-only-check -> independent-record time-of-check gap.
     *
     * Existing in-JVM recordIfCurrent remains the only kill-counter
     * mutation primitive. No loot/XP is published; no disk transaction.
     */
    synchronized Receipt recordSourceWindStrikeLethalIfCurrent(
        LocalLabWindStrikeHitWitness witness
    ){
        Objects.requireNonNull(witness,"witness");
        EntityId id=witness.npcId;
        long deathTick=witness.deathTick;

        if(witness.world!=world)
            return Receipt.rejected(
                "FOREIGN_WORLD",id,deathTick,0L);
        if(!witness.newlyDied||
           witness.appliedDamage!=
               LocalLabWindStrikeNpcSettlement.DAMAGE||
           witness.hitpointsAfter!=0||
           deathTick<0L||
           witness.worldTick!=deathTick)
            return Receipt.rejected(
                "NOT_COMMITTED_LETHAL_SOURCE_HIT",
                id,deathTick,0L);

        final WorldPlayer owner=witness.attacker;
        final Receipt[] result={null};
        try{
            boolean accepted=
                world.withOpenPlayerMutationOwnershipIfCurrent(
                    owner,witness.generation,()->{
                        // Merely retaining a historical immutable
                        // witness is insufficient. This exact object
                        // must still be the player-owned LAST
                        // successfully committed source hit.
                        if(owner.lastLocalLabWindStrikeAcceptedHit()
                                !=witness){
                            result[0]=Receipt.rejected(
                                "NOT_CURRENT_SOURCE_HIT",
                                id,deathTick,0L);
                            return;
                        }

                        WorldNpc npc=world.npcs().byId(id);
                        if(npc==null||
                           npc.definitionId!=witness.npcDefinitionId){
                            result[0]=Receipt.rejected(
                                "STALE_CANONICAL_NPC",
                                id,deathTick,0L);
                            return;
                        }

                        boolean npcOwned=
                            world.npcs()
                                .withCurrentMutationOwnershipIfCurrent(
                                    npc,()->{
                                        synchronized(world.npcLifecycle()){
                                            NpcLifecycleService.Snapshot now=
                                                world.npcLifecycle().get(id);
                                            if(now==null||!now.dead()||
                                               now.hitpoints!=0||
                                               now.definitionId!=
                                                   witness.npcDefinitionId||
                                               now.registrationIdentity!=
                                                   witness.npcRegistrationIdentity||
                                               now.deathTick!=deathTick){
                                                result[0]=Receipt.rejected(
                                                    "STALE_NPC_DEATH_REGISTRATION",
                                                    id,deathTick,0L);
                                                return;
                                            }

                                            // The legacy map is keyed
                                            // only by NPC ID+death tick.
                                            // A genuine *new* NPC life
                                            // can die on the SAME tick,
                                            // especially after forced
                                            // re-registration. Its old
                                            // receipt is NOT a replay
                                            // of this new hit.
                                            KillIdentity key=
                                                new KillIdentity(id,deathTick);
                                            Receipt prior=receipts.get(key);
                                            SourceDeathReceipt proof=
                                                sourceDeathReceipts.get(key);

                                            if(prior!=null){
                                                if(proof==null){
                                                    result[0]=Receipt.rejected(
                                                        "SOURCE_DEATH_RECEIPT_UNATTRIBUTED",
                                                        id,deathTick,prior.kills);
                                                    return;
                                                }
                                                if(proof.registrationIdentity!=
                                                        witness.npcRegistrationIdentity){
                                                    result[0]=Receipt.rejected(
                                                        "SOURCE_DEATH_LIFETIME_COLLISION",
                                                        id,deathTick,prior.kills);
                                                    return;
                                                }
                                                if(proof.witness!=witness){
                                                    result[0]=Receipt.rejected(
                                                        "SOURCE_DEATH_WITNESS_COLLISION",
                                                        id,deathTick,prior.kills);
                                                    return;
                                                }
                                            }else if(proof!=null){
                                                throw new IllegalStateException(
                                                    "P535_SOURCE_RECEIPT_PROOF_WITHOUT_RECORD");
                                            }

                                            // A snapshot may have
                                            // been restored after a JVM
                                            // restart: the persisted
                                            // counter/negative marker is
                                            // NOT a retained, durable
                                            // identity for each NPC death.
                                            // The current World must
                                            // have its own SOURCE proof
                                            // for this exact player and
                                            // session generation before
                                            // granting an additional
                                            // source kill. Existing P5.35
                                            // same-key collision/replay
                                            // checks run FIRST above.
                                            if(prior==null){
                                                SortedMap<String,String> ext=
                                                    owner.snapshotExtensions()
                                                        .namespace(NAMESPACE);
                                                String marker=ext.get(
                                                    SOURCE_RESTART_REVIEW_KEY);
                                                if(marker!=null){
                                                    if(!SOURCE_RESTART_REVIEW_VALUE
                                                            .equals(marker)){
                                                        result[0]=Receipt.rejected(
                                                            "INVALID_SOURCE_RESTART_MARKER",
                                                            id,deathTick,0L);
                                                        return;
                                                    }
                                                    if(!sourceProofForOwnerGeneration(
                                                            owner,witness.generation)){
                                                        result[0]=Receipt.rejected(
                                                            "SOURCE_RESTART_REVIEW_REQUIRED",
                                                            id,deathTick,0L);
                                                        return;
                                                    }
                                                }
                                            }

                                            // Existing idempotent legacy
                                            // mutation remains the ONLY
                                            // counter/receipt writer.
                                            // A SOURCE mark is included
                                            // in the same snapshot write.
                                            // All owner, registry and
                                            // lifecycle locks stay held
                                            // during proof+record commit.
                                            Receipt receipt=
                                                recordIfCurrentInternal(
                                                    owner,witness.generation,
                                                    id,deathTick,true);
                                            if(prior==null){
                                                if(!receipt.applied){
                                                    if(receipt.replayed)
                                                        throw new IllegalStateException(
                                                            "P535_UNEXPECTED_SOURCE_REPLAY_WITHOUT_PROOF");
                                                }else{
                                                    sourceDeathReceipts.put(
                                                        key,new SourceDeathReceipt(witness));
                                                }
                                            }else if(!receipt.replayed){
                                                throw new IllegalStateException(
                                                    "P535_MATCHED_PROOF_BUT_NOT_REPLAY");
                                            }
                                            result[0]=receipt;
                                        }
                                    }
                                );

                        if(!npcOwned)
                            result[0]=Receipt.rejected(
                                "STALE_CANONICAL_NPC",
                                id,deathTick,0L);
                    }
                );

            if(!accepted)
                return Receipt.rejected(
                    "STALE_PLAYER_GENERATION",
                    id,deathTick,0L);
        }catch(Exception failure){
            // A failure could have followed a semantic record write.
            // Do not mislabel an UNKNOWN outcome as retryable.
            throw new IllegalStateException(
                "P534_SOURCE_KILL_RECORD_OUTCOME_UNCERTAIN",
                failure);
        }

        return Objects.requireNonNull(
            result[0],"P534_SOURCE_KILL_RECORD_MISSING_RESULT");
    }

    /**
     * The source companion map is purely in-memory; only records
     * actually committed in THIS World and exact player generation
     * count as an active source chain. A restored counter and marker
     * without companions cannot authorize a post-restart grant.
     */
    private boolean sourceProofForOwnerGeneration(
        WorldPlayer player,long generation
    ){
        for(SourceDeathReceipt source:sourceDeathReceipts.values()){
            if(source.witness.world==world&&
               source.witness.attacker==player&&
               source.witness.generation==generation)
                return true;
        }
        return false;
    }

    Snapshot snapshot(
        WorldPlayer player
    ){
        Objects.requireNonNull(
            player,
            "player"
        );

        synchronized(player.mutationLock()){
            Long kills=
                decodeKills(
                    player.snapshotExtensions()
                        .namespace(
                            NAMESPACE
                        )
                );

            return new Snapshot(
                kills==null
                    ?0L
                    :kills.longValue()
            );
        }
    }

    synchronized int receiptCount(){
        return receipts.size();
    }

    private static Long decodeKills(
        SortedMap<String,String> values
    ){
        if(values==null||
           values.isEmpty())
            return Long.valueOf(0L);

        if(!VERSION.equals(
                trim(
                    values.get(
                        "version"
                    )
                )
            ))
            return null;

        if(!AUTHORITY.equals(
                trim(
                    values.get(
                        "authority"
                    )
                )
            ))
            return null;

        String raw=
            trim(
                values.get(
                    "kills"
                )
            );

        if(raw==null)
            return null;

        try{
            long parsed=
                Long.parseLong(raw);

            return parsed<0L
                ?null
                :Long.valueOf(parsed);
        }catch(NumberFormatException failure){
            return null;
        }
    }

    private static String trim(
        String value
    ){
        if(value==null)
            return null;

        String clean=value.trim();
        return clean.isEmpty()
            ?null
            :clean;
    }
}
