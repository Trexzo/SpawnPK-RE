package spk.local;

/**
 * Gameplay owner independent of the connection.  R2 deliberately composes the
 * already-certified state classes so ownership can move without rewriting them.
 */
final class WorldPlayer {
    private final EntityId entityId=EntityId.next();
    private final MovementState movement=new MovementState();
    private final BankState bank=new BankState();
    private final EquipmentState equipment=new EquipmentState();
    private final PetState petState=new PetState();
    private final PlayerState playerState=new PlayerState();
    private final PrayerState prayers=new PrayerState();
    private final MagicState magic=new MagicState();
    private final CombatStyleState combatStyles=new CombatStyleState();
    private final CombatState combatState=new CombatState();
    private final PlayerLifecycleState lifecycle=new PlayerLifecycleState();
    private final PlayerStatusState statusState=new PlayerStatusState();
    private final SemanticTimedEffectService timedEffects=new SemanticTimedEffectService();
    private final PetEffectState petEffects=new PetEffectState();
    private final MiniPetService miniPets=new MiniPetService();
    private final PetAccessoryState petAccessoryState=new PetAccessoryState();
    private final PlayerSnapshotExtensionState snapshotExtensions=
        new PlayerSnapshotExtensionState();
    // Explicit LocalLab policy: account-owned messages, max 128 envelopes.
    // Server-only until a session/root/input routing authority is established.
    private final MailboxRewardDeliveryService mailbox=
        new MailboxRewardDeliveryService(
            LocalLabMailboxPersistence.MAX_MESSAGES
        );
    private boolean mailboxSnapshotKnown;
    private final Object mutationLock=new Object();
    // Exact LocalLab semantic NPC Attack cooldown is player-owned rather
    // than handler-instance-owned: multiple wrappers for this WorldPlayer
    // must share both the admission monitor and authoritative next tick.
    // Separate from legacy CombatState and original SpawnPK authority.
    private final Object canonicalNpcAttackLock=new Object();
    private long canonicalNpcAttackNextAllowedTick;
    // Java monitors are reentrant. A synchronous hook/callback may initiate
    // another opcode72 attack from the SAME thread before the first call
    // publishes its cooldown. Track the in-flight admission independently
    // of the shared thread monitor. This is not an account/session lease.
    private boolean canonicalNpcAttackInFlight;
    // P5.22 source-only Wind Strike once-per-World-tick ledger.
    // Shared by ALL isolated settlement service wrappers of this player.
    // Unlike an instance-local timestamp, a second wrapper cannot spend
    // again in the same World/session generation/tick.
    // Not a durable replay key, nor original SpawnPK spell cadence.
    private World localLabWindStrikeLastWorld;
    private long localLabWindStrikeLastGeneration=-1L;
    private long localLabWindStrikeLastTick=-1L;
    // P5.28 opt-in, source-only recovery policy. One immutable policy
    // per World+player-generation, shared by every settlement wrapper.
    // Defaults to the preexisting one-hit-per-tick behaviour unless
    // explicitly installed before the first successful local cast.
    private World localLabWindStrikeRecoveryWorld;
    private long localLabWindStrikeRecoveryGeneration=-1L;
    private int localLabWindStrikeRecoveryTicks=1;
    // P5.29: source-only attack lifecycle bound to the exact canonical
    // player, not to an individual LocalLab spell wrapper or socket.
    private final LocalLabWindStrikeAttackLifecycle
        localLabWindStrikeAttackLifecycle=
            new LocalLabWindStrikeAttackLifecycle();
    // P5.30 optional cross-style PvM admission. Independent from the
    // canonical melee and Wind Strike internal monitors: its own short
    // synchronized methods never call into World or session services.
    private final LocalLabSharedPvmActionGate
        localLabSharedPvmActionGate=
            new LocalLabSharedPvmActionGate();
    private String username;
    private long generation;
    private boolean registered;
    // Source-only recipe writer binding: once a session generation has
    // claimed its writer, a revoked witness cannot be recreated for the
    // same WorldPlayer generation. Not a durable named-account epoch.
    private long recipeWriterClaimedGeneration=-1L;

    EntityId id(){return entityId;}
    MovementState movement(){return movement;}
    BankState bank(){return bank;}
    EquipmentState equipment(){return equipment;}
    PetState petState(){return petState;}
    PlayerState playerState(){return playerState;}
    PrayerState prayers(){return prayers;}
    MagicState magic(){return magic;}
    CombatStyleState combatStyles(){return combatStyles;}
    CombatState combatState(){return combatState;}
    PlayerLifecycleState lifecycle(){return lifecycle;}
    PlayerStatusState statusState(){return statusState;}
    SemanticTimedEffectService timedEffects(){return timedEffects;}
    PetEffectState petEffects(){return petEffects;}
    MiniPetService miniPets(){return miniPets;}
    PetAccessoryState petAccessoryState(){return petAccessoryState;}
    PlayerSnapshotExtensionState snapshotExtensions(){
        return snapshotExtensions;
    }
    MailboxRewardDeliveryService mailbox(){return mailbox;}

    /**
     * Tracks a namespace that was restored or first captured, so a later
     * delete-all saves an explicit empty mailbox rather than replaying
     * an older nonempty account snapshot.
     */
    boolean mailboxSnapshotKnown(){
        synchronized(mutationLock){
            return mailboxSnapshotKnown;
        }
    }

    void markMailboxSnapshotKnown(){
        synchronized(mutationLock){
            mailboxSnapshotKnown=true;
        }
    }

    Object mutationLock(){return mutationLock;}

    // Calls require the existing World ownership + mutation locks; the
    // state machine itself separately guards same-thread reentrant calls.
    LocalLabWindStrikeAttackLifecycle
        localLabWindStrikeAttackLifecycle(){
        return localLabWindStrikeAttackLifecycle;
    }

    // Callers hold the current World lifecycle lease and mutationLock.
    // Synchronization is reentrant and also protects direct test callers.
    boolean localLabWindStrikeSettledThisTick(
        World expectedWorld,long generation,long tick
    ){
        if(expectedWorld==null||generation<0L||tick<0L)
            throw new IllegalArgumentException(
                "invalid source-only spell tick identity");
        synchronized(mutationLock){
            return localLabWindStrikeLastWorld==expectedWorld&&
                localLabWindStrikeLastGeneration==generation&&
                localLabWindStrikeLastTick==tick;
        }
    }

    /**
     * Explicit LocalLab-only trial policy. Must be installed while the
     * current World-player lease is held, before the first committed cast
     * for this exact generation. There is deliberately no downgrade or
     * reconfiguration pathway in the same owner generation.
     *
     * No real SpawnPK magic cadence is inferred by this operation.
     */
    boolean installLocalLabWindStrikeRecovery(
        World expectedWorld,long expectedGeneration,int recoveryTicks
    )throws java.io.IOException{
        if(expectedWorld==null||expectedGeneration<0L||
           recoveryTicks<2||recoveryTicks>8)
            throw new IllegalArgumentException(
                "P528_INVALID_LOCAL_LAB_RECOVERY_POLICY");
        return expectedWorld.withOpenPlayerOwnershipIfCurrent(
            this,expectedGeneration,()->{
                synchronized(mutationLock){
                    if(localLabWindStrikeRecoveryWorld==expectedWorld&&
                       localLabWindStrikeRecoveryGeneration==
                           expectedGeneration)
                        throw new IllegalStateException(
                            "P528_RECOVERY_POLICY_ALREADY_INSTALLED");
                    if(localLabWindStrikeLastWorld==expectedWorld&&
                       localLabWindStrikeLastGeneration==
                           expectedGeneration)
                        throw new IllegalStateException(
                            "P528_RECOVERY_POLICY_AFTER_FIRST_CAST");
                    localLabWindStrikeRecoveryWorld=expectedWorld;
                    localLabWindStrikeRecoveryGeneration=
                        expectedGeneration;
                    localLabWindStrikeRecoveryTicks=recoveryTicks;
                }
            }
        );
    }

    /**
     * The shared player-owned state, not a handler-instance timer,
     * determines admission. Null is READY; denials are stable and do
     * not mutate anything, including the cooldown or MagicState.last.
     */
    String localLabWindStrikeRecoveryRejection(
        World expectedWorld,long expectedGeneration,long tick
    ){
        if(expectedWorld==null||expectedGeneration<0L||tick<0L)
            throw new IllegalArgumentException(
                "invalid source-only recovery tick identity");
        synchronized(mutationLock){
            if(localLabWindStrikeLastWorld!=expectedWorld||
               localLabWindStrikeLastGeneration!=expectedGeneration)
                return null;
            if(tick<localLabWindStrikeLastTick)
                return "REJECTED_WORLD_TICK_REGRESSION";
            if(tick==localLabWindStrikeLastTick)
                return "REJECTED_SAME_TICK_DUPLICATE";
            int interval=localLabWindStrikeRecoveryWorld==expectedWorld&&
                localLabWindStrikeRecoveryGeneration==
                    expectedGeneration
                ?localLabWindStrikeRecoveryTicks:1;
            // Compare differences without adding to lastSettledTick,
            // avoiding overflow near Long.MAX_VALUE.
            if(tick-localLabWindStrikeLastTick<interval)
                return "REJECTED_LOCAL_LAB_SPELL_RECOVERY";
            return null;
        }
    }

    /**
     * Source-only P5.32 action interval, including the P5.28 optional
     * custom spell policy. No original SpawnPK magic cadence inferred.
     * Caller retains the exact World + player generation ownership.
     */
    int localLabWindStrikeEffectiveRecoveryTicks(
        World expectedWorld,long expectedGeneration
    ){
        synchronized(mutationLock){
            return localLabWindStrikeRecoveryWorld==expectedWorld&&
                localLabWindStrikeRecoveryGeneration==
                    expectedGeneration
                ?localLabWindStrikeRecoveryTicks:1;
        }
    }

    // Invoked only AFTER successful queue admission, canonical NPC HP
    // change and exact inventory semantic postimage commit.
    void markLocalLabWindStrikeSettled(
        World expectedWorld,long generation,long tick
    ){
        if(expectedWorld==null||generation<0L||tick<0L)
            throw new IllegalArgumentException(
                "invalid source-only spell tick identity");
        synchronized(mutationLock){
            String blocked=localLabWindStrikeRecoveryRejection(
                expectedWorld,generation,tick);
            if(blocked!=null)
                throw new IllegalStateException(
                    "LocalLab Wind Strike commit after denial "+blocked);
            localLabWindStrikeLastWorld=expectedWorld;
            localLabWindStrikeLastGeneration=generation;
            localLabWindStrikeLastTick=tick;
        }
    }

    /** Opt-in LocalLab-only shared source action arbiter. */
    LocalLabSharedPvmActionGate localLabSharedPvmActionGate(){
        return localLabSharedPvmActionGate;
    }

    boolean installLocalLabSharedPvmAdmission(
        World expectedWorld,long expectedGeneration
    )throws java.io.IOException{
        if(expectedWorld==null||expectedGeneration<0L)
            throw new IllegalArgumentException(
                "invalid P530 owner/generation");
        return expectedWorld.withOpenPlayerOwnershipIfCurrent(
            this,expectedGeneration,()->{
                synchronized(mutationLock){
                    localLabSharedPvmActionGate.install(
                        expectedWorld,expectedGeneration);
                }
            }
        );
    }

    Object canonicalNpcAttackLock(){return canonicalNpcAttackLock;}

    // Must be called while holding canonicalNpcAttackLock, as handle does.
    // The inner synchronization is intentionally reentrant for this guard.
    boolean tryEnterCanonicalNpcAttack(){
        synchronized(canonicalNpcAttackLock){
            if(canonicalNpcAttackInFlight)return false;
            canonicalNpcAttackInFlight=true;
            return true;
        }
    }

    void leaveCanonicalNpcAttack(){
        synchronized(canonicalNpcAttackLock){
            if(!canonicalNpcAttackInFlight)
                throw new IllegalStateException(
                    "canonical NPC admission was not active");
            canonicalNpcAttackInFlight=false;
        }
    }

    long canonicalNpcAttackNextAllowedTick(){
        synchronized(canonicalNpcAttackLock){
            return canonicalNpcAttackNextAllowedTick;
        }
    }

    void canonicalNpcAttackNextAllowedTick(long nextTick){
        if(nextTick<0L)
            throw new IllegalArgumentException("nextTick="+nextTick);
        synchronized(canonicalNpcAttackLock){
            if(nextTick<canonicalNpcAttackNextAllowedTick)
                throw new IllegalArgumentException(
                    "canonical NPC next tick regressed old="+
                    canonicalNpcAttackNextAllowedTick+" new="+nextTick
                );
            canonicalNpcAttackNextAllowedTick=nextTick;
        }
    }

    synchronized String username(){return username;}
    synchronized long generation(){return generation;}
    synchronized boolean registered(){return registered;}

    synchronized long markRegistered(String username){
        if(registered)throw new IllegalStateException("already registered");
        this.username=username;
        this.registered=true;
        return ++generation;
    }

    synchronized long markUnregistered(){
        if(!registered)return generation;
        registered=false;
        return ++generation;
    }

    synchronized boolean accepts(long expectedGeneration){
        return registered && generation==expectedGeneration;
    }

    /**
     * Source-only legacy recipe adapters may run only before a session has
     * claimed the current player generation. Once a live session writer is
     * installed, weaker World-only publication authority is permanently
     * disabled for this generation, including after witness revocation.
     */
    synchronized void requireUnclaimedRecipeGeneration(long expectedGeneration){
        if(recipeWriterClaimedGeneration==expectedGeneration)
            throw new IllegalStateException(
                "RECIPE_WORLD_ONLY_PATH_BLOCKED_BY_SESSION_WRITER");
    }

    /** One recipe writer witness per registered player generation, ever. */
    synchronized void claimRecipeWriterGeneration(long expectedGeneration){
        if(!accepts(expectedGeneration))
            throw new IllegalStateException(
                "RECIPE_SESSION_WRITER_OWNER_NOT_REGISTERED");
        if(recipeWriterClaimedGeneration==expectedGeneration)
            throw new IllegalStateException(
                "RECIPE_SESSION_WRITER_GENERATION_ALREADY_CLAIMED");
        recipeWriterClaimedGeneration=expectedGeneration;
    }

    @Override public synchronized String toString(){
        return "WorldPlayer{id="+entityId+",username="+username+",generation="+generation+",registered="+registered+"}";
    }
}
