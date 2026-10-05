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
    private final LocalRiskZoneState riskZone=new LocalRiskZoneState();
    private final PlayerStatusState statusState=new PlayerStatusState();
    private final SemanticTimedEffectService timedEffects=new SemanticTimedEffectService();
    private final PetEffectState petEffects=new PetEffectState();
    private final MiniPetService miniPets=new MiniPetService();
    private final PetAccessoryState petAccessoryState=new PetAccessoryState();
    private final PlayerSnapshotExtensionState snapshotExtensions=
        new PlayerSnapshotExtensionState();
    private final Object mutationLock=new Object();
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
    LocalRiskZoneState riskZone(){return riskZone;}
    PlayerStatusState statusState(){return statusState;}
    SemanticTimedEffectService timedEffects(){return timedEffects;}
    PetEffectState petEffects(){return petEffects;}
    MiniPetService miniPets(){return miniPets;}
    PetAccessoryState petAccessoryState(){return petAccessoryState;}
    PlayerSnapshotExtensionState snapshotExtensions(){
        return snapshotExtensions;
    }
    Object mutationLock(){return mutationLock;}

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
