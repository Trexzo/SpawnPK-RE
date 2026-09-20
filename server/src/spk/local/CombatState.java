package spk.local;

/** Canonical player-owned combat scheduler state, independent of the connection wrapper. */
final class CombatState {
    int targetSceneIndex=-1;
    int targetDefinitionId=-1;
    CombatContext context;
    long requestedAtMs;
    boolean pendingRange;
    boolean readyEmitted;
    long nextAttackTick;
    long lastAttackTick=-1;
    int attackCount;

    void target(NpcEntity npc, CombatTargetRepository.Target target, long now, boolean pendingRange){
        targetSceneIndex=npc.sceneIndex; targetDefinitionId=npc.definitionId; context=target.context;
        requestedAtMs=now; this.pendingRange=pendingRange; this.readyEmitted=false;
        nextAttackTick=0; lastAttackTick=-1; attackCount=0;
    }
    void clear(){ targetSceneIndex=-1; targetDefinitionId=-1; context=null; requestedAtMs=0L; pendingRange=false; readyEmitted=false; nextAttackTick=0; lastAttackTick=-1; attackCount=0; }
    boolean active(){ return targetSceneIndex>=0 && context!=null; }
}
