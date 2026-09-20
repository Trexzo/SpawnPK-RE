package spk.local;

/**
 * Explicit combat-target eligibility/cancellation authority.
 *
 * This class does not calculate damage, timing or presentation. It answers only
 * whether the selected PvM/PvP target is still a legal combat target.
 */
final class CombatTargetValidator {
    enum Reason {
        VALID,
        NULL_TARGET,
        SELF_TARGET,
        TARGET_UNREGISTERED,
        OWNER_DEAD,
        TARGET_DEAD,
        PLANE_MISMATCH,
        TARGET_NOT_VISIBLE,
        NPC_DEFINITION_CHANGED,
        NPC_CONTEXT_CHANGED,
        NPC_NOT_COMBAT_TARGET
    }

    static final class Result {
        final boolean valid;
        final Reason reason;
        final String detail;

        Result(boolean valid,Reason reason,String detail){
            this.valid=valid;
            this.reason=reason;
            this.detail=detail==null?"":detail;
        }

        static Result valid(){
            return new Result(true,Reason.VALID,"");
        }

        static Result reject(Reason reason,String detail){
            return new Result(false,reason,detail);
        }

        @Override public String toString(){
            return valid
                ?"CombatTargetValidity{VALID}"
                :"CombatTargetValidity{"+reason+
                    (detail.isEmpty()?"":","+detail)+"}";
        }
    }

    static Result acquireNpc(NpcEntity npc){
        if(npc==null)
            return Result.reject(
                Reason.NULL_TARGET,
                "npc=null"
            );

        CombatTargetRepository.Target target=
            CombatTargetRepository.forDefinition(
                npc.definitionId
            );

        if(target==null)
            return Result.reject(
                Reason.NPC_NOT_COMBAT_TARGET,
                "scene="+npc.sceneIndex+
                " def="+npc.definitionId
            );

        return Result.valid();
    }

    static Result activeNpc(
        NpcEntity npc,
        CombatState state
    ){
        if(npc==null)
            return Result.reject(
                Reason.NULL_TARGET,
                "npc=null"
            );

        if(state==null||!state.active())
            return Result.reject(
                Reason.NPC_CONTEXT_CHANGED,
                "combatStateInactive=true"
            );

        if(npc.definitionId!=state.targetDefinitionId)
            return Result.reject(
                Reason.NPC_DEFINITION_CHANGED,
                "expected="+state.targetDefinitionId+
                " actual="+npc.definitionId
            );

        CombatTargetRepository.Target target=
            CombatTargetRepository.forDefinition(
                npc.definitionId
            );

        if(target==null)
            return Result.reject(
                Reason.NPC_NOT_COMBAT_TARGET,
                "scene="+npc.sceneIndex+
                " def="+npc.definitionId
            );

        if(target.context!=state.context)
            return Result.reject(
                Reason.NPC_CONTEXT_CHANGED,
                "expected="+state.context+
                " actual="+target.context
            );

        return Result.valid();
    }

    static Result player(
        WorldPlayer owner,
        WorldPlayer target,
        Player81WorldSync.Context sync
    ){
        if(target==null)
            return Result.reject(
                Reason.NULL_TARGET,
                "player=null"
            );

        if(owner==target||
           (owner!=null&&owner.id().equals(target.id())))
            return Result.reject(
                Reason.SELF_TARGET,
                "target="+target.id()
            );

        if(!target.registered())
            return Result.reject(
                Reason.TARGET_UNREGISTERED,
                "target="+target.id()
            );

        if(owner!=null&&owner.lifecycle().dead())
            return Result.reject(
                Reason.OWNER_DEAD,
                "owner="+owner.id()
            );

        if(target.lifecycle().dead())
            return Result.reject(
                Reason.TARGET_DEAD,
                "target="+target.id()
            );

        if(owner!=null&&
           owner.movement().plane()!=
                target.movement().plane())
            return Result.reject(
                Reason.PLANE_MISMATCH,
                "ownerPlane="+owner.movement().plane()+
                " targetPlane="+target.movement().plane()
            );

        if(sync!=null&&
           sync.clientIndexFor(target)<0)
            return Result.reject(
                Reason.TARGET_NOT_VISIBLE,
                "target="+target.id()
            );

        return Result.valid();
    }

    private CombatTargetValidator(){}
}
