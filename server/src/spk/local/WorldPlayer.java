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
    private String username;
    private long generation;
    private boolean registered;

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

    @Override public synchronized String toString(){
        return "WorldPlayer{id="+entityId+",username="+username+",generation="+generation+",registered="+registered+"}";
    }
}
