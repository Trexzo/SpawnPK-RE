package spk.local;

/** Definition-driven NPC action semantics; opcodes identify option slots, not services. */
final class NpcInteractionRouter {
    enum Service { NONE, BANK, ATTACK, TALK, TRADE, UNIMPLEMENTED }
    static final class Route { final int option; final String action; final Service service; final String authority;
        Route(int o,String a,Service s,String au){option=o;action=a;service=s;authority=au;}
        public String toString(){return "Route{option="+option+",action="+action+",service="+service+",authority="+authority+"}";}}
    static int optionForOpcode(int opcode){switch(opcode){case 155:return 1;case 72:return 2;case 17:return 3;case 21:return 4;case 18:return 5;default:return -1;}}
    static Route resolve(NpcAction req,NpcEntity npc){
        if(req==null||npc==null)return new Route(-1,null,Service.NONE,"NO_TARGET");
        int opt=optionForOpcode(req.opcode); if(opt<1)return new Route(opt,null,Service.NONE,"UNKNOWN_OPTION_OPCODE");
        EffectiveNpcDefinitionRepository.Def d=EffectiveNpcDefinitionRepository.get(npc.definitionId);
        if(d==null)return new Route(opt,null,Service.UNIMPLEMENTED,"NO_EXACT_DEFINITION");
        String action=d.action(opt), norm=d.normalized(opt); String key=(norm!=null?norm:action);
        if(key==null)return new Route(opt,null,Service.NONE,"EXACT_DEFINITION_EMPTY_SLOT");
        String k=key.toLowerCase(java.util.Locale.ROOT).replaceAll("<[^>]+>","").trim();
        Service s; String authority="EXACT_CLIENT_ACTION_SLOT";
        if(k.equals("bank"))s=Service.BANK; else if(k.equals("attack"))s=Service.ATTACK; else if(k.equals("talk-to")||k.equals("talk to"))s=Service.TALK; else if(k.equals("trade")||k.startsWith("trade"))s=Service.TRADE; else s=Service.UNIMPLEMENTED;
        return new Route(opt,action,s,authority);
    }
}
