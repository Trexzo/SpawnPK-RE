package spk.local;

public final class CombatTargetRepositoryTest {
    public static void main(String[] args){
        CombatTargetRepository.Target p=CombatTargetRepository.forDefinition(1488);
        CombatTargetRepository.Target m=CombatTargetRepository.forDefinition(1489);
        if(p==null || p.context!=CombatContext.PLAYER_PVP) throw new AssertionError("1488 context");
        if(m==null || m.context!=CombatContext.NPC_PVM) throw new AssertionError("1489 context");
        if(CombatTargetRepository.forDefinition(1799)!=null) throw new AssertionError("non-dummy enabled");
        System.out.println("V56_COMBAT_TARGET_PASS 1488=PLAYER_PVP 1489=NPC_PVM othersFailClosed=true");
    }
}
