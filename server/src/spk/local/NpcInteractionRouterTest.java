package spk.local;

public final class NpcInteractionRouterTest {
    public static void main(String[] args){
        if(EffectiveNpcDefinitionRepository.count()!=8086)throw new AssertionError("npc defs="+EffectiveNpcDefinitionRepository.count());
        NpcEntity banker=new NpcEntity(204,7605,3090,3490);
        NpcInteractionRouter.Route bank=NpcInteractionRouter.resolve(new NpcAction(17,204),banker);
        if(bank.option!=3||bank.service!=NpcInteractionRouter.Service.BANK)throw new AssertionError("bank route "+bank);
        NpcInteractionRouter.Route talk=NpcInteractionRouter.resolve(new NpcAction(155,204),banker);
        if(talk.option!=1||talk.service!=NpcInteractionRouter.Service.BANK)throw new AssertionError("banker talk-to route "+talk);
        System.out.println("V5122_NPC_INTERACTION_ROUTER_PASS definitions=8086 banker7605_option3=BANK banker7605_option1TalkTo=BANK definitionDriven=true");
    }
}
