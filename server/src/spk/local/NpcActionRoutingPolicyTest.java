package spk.local;

/** v5.7 regression: opcode72 is Attack only; opcode155 is first-option/Pick-up only. */
public final class NpcActionRoutingPolicyTest {
    public static void main(String[] args){
        PetDefinitionRepository.Def d=PetDefinitionRepository.get(22519);
        req(d!=null,"missing pet def");
        PetState ps=new PetState(); ps.activate(d);
        NpcEntity pet=new NpcEntity(4,d.npcId,3086,3495,true,d.itemId,1);
        NpcEntity pvp=new NpcEntity(131,1488,3095,3513);
        NpcEntity pvm=new NpcEntity(129,1489,3107,3504);

        req(LocalSession.isPetPickupAction(new NpcAction(155,4),pet,ps),"155 should pickup active pet");
        req(!LocalSession.isPetPickupAction(new NpcAction(72,4),pet,ps),"72 must not pickup pet");
        req(LocalSession.isCombatAttackAction(new NpcAction(72,131),pvp),"72 should attack pvp dummy");
        req(LocalSession.isCombatAttackAction(new NpcAction(72,129),pvm),"72 should attack pvm dummy");
        req(!LocalSession.isCombatAttackAction(new NpcAction(155,131),pvp),"155 must not start combat");
        req(!LocalSession.isCombatAttackAction(new NpcAction(72,4),pet),"pet must not be combat dummy");
        System.out.println("V57_NPC_ACTION_ROUTING_PASS opcode72AttackOnly=true opcode155PetPickupOnly=true crossRouteBlocked=true");
    }
    static void req(boolean b,String m){if(!b)throw new AssertionError(m);}
}
