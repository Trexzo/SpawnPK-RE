package spk.local;

/** Canonical player-owned combat scheduler state, independent of the connection wrapper. */
final class CombatState {
    int targetSceneIndex=-1;
    int targetDefinitionId=-1;
    EntityId targetPlayerId;
    CombatContext context;
    long requestedAtMs;
    boolean pendingRange;
    boolean readyEmitted;
    long nextAttackTick;
    long lastAttackTick=-1;
    int attackCount;
    CombatHitScheduler.ScheduledHit pendingHit;

    void target(NpcEntity npc, CombatTargetRepository.Target target, long now, boolean pendingRange){
        targetSceneIndex=npc.sceneIndex; targetDefinitionId=npc.definitionId; targetPlayerId=null; context=target.context;
        requestedAtMs=now; this.pendingRange=pendingRange; this.readyEmitted=false;
        nextAttackTick=0; lastAttackTick=-1; attackCount=0; pendingHit=null;
    }

    void targetPlayer(WorldPlayer target,long now){
        if(target==null)throw new NullPointerException("target");
        targetSceneIndex=-1; targetDefinitionId=-1; targetPlayerId=target.id(); context=CombatContext.PLAYER_PVP;
        requestedAtMs=now; pendingRange=false; readyEmitted=false;
        nextAttackTick=0; lastAttackTick=-1; attackCount=0; pendingHit=null;
    }

    void clear(){
        targetSceneIndex=-1; targetDefinitionId=-1; targetPlayerId=null; context=null; requestedAtMs=0L;
        pendingRange=false; readyEmitted=false; nextAttackTick=0; lastAttackTick=-1; attackCount=0; pendingHit=null;
    }

    void clearPlayerTarget(){
        if(targetPlayerId==null)return;
        clear();
    }

    boolean active(){ return targetSceneIndex>=0 && context!=null; }
    boolean activePlayer(){ return targetPlayerId!=null && context==CombatContext.PLAYER_PVP; }
}
