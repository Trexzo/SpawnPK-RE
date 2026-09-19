package spk.local;

/** Evidence-backed attack-target semantics. Only the exact production max-hit dummies are enabled in M1. */
final class CombatTargetRepository {
    static final int PLAYER_DUMMY_DEF = 1488;
    static final int PVM_DUMMY_DEF = 1489;

    static final class Target {
        final int definitionId;
        final CombatContext context;
        final String name;
        final String evidence;
        Target(int definitionId, CombatContext context, String name, String evidence) {
            this.definitionId=definitionId; this.context=context; this.name=name; this.evidence=evidence;
        }
        @Override public String toString(){
            return "Target{def="+definitionId+",context="+context+",name="+name+",evidence="+evidence+"}";
        }
    }

    private static final Target PLAYER = new Target(
        PLAYER_DUMMY_DEF, CombatContext.PLAYER_PVP, "Max hit dummy (Player)",
        "V902_CURRENT_NPC_DEFINITION+PRODUCTION_HOME_POSITION");
    private static final Target PVM = new Target(
        PVM_DUMMY_DEF, CombatContext.NPC_PVM, "Max hit dummy (PvM)",
        "V902_CURRENT_NPC_DEFINITION+PRODUCTION_HOME_POSITION");

    private CombatTargetRepository() {}

    static Target forDefinition(int definitionId) {
        if(definitionId==PLAYER_DUMMY_DEF) return PLAYER;
        if(definitionId==PVM_DUMMY_DEF) return PVM;
        return null;
    }

    static boolean isCombatDummy(int definitionId){ return forDefinition(definitionId)!=null; }
}
