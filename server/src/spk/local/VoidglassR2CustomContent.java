package spk.local;

/**
 * LOCAL CUSTOM CONTENT ONLY. This profile is not SpawnPK production authority.
 * It forward-ports the preserved Voidglass R2 identity onto the current engine
 * while deliberately labeling the newly chosen palette as a LocalLab trial.
 */
final class VoidglassR2CustomContent {
    static final String NAME="Voidglass Nistirio";
    static final int ITEM_ID=32760;
    static final int NPC_ID=12000;
    static final int MODEL_ID=36185;
    static final int IDLE_ANIM=8233;
    static final int WALK_ANIM=8232;
    static final int PROC_ANIM=8236;
    static final int PROC_GFX=4098;
    static final String PROC_TEXT="VOID RESONANCE";
    static final String PALETTE="CUSTOM_LOCALLAB_VOIDGLASS_PALETTE_V1_NOT_PRODUCTION_RECOVERY";
    static final String PROVENANCE="CUSTOM_LOCALLAB_VOIDGLASS_R2_TRIAL";


    @SuppressWarnings("unchecked")
    static void ensureRuntimePetMapping(){
        try{
            java.lang.reflect.Field f=PetDefinitionRepository.class.getDeclaredField("BY_ITEM");
            f.setAccessible(true);
            java.util.Map<Integer,PetDefinitionRepository.Def> map=(java.util.Map<Integer,PetDefinitionRepository.Def>)f.get(null);
            synchronized(map){
                PetDefinitionRepository.Def d=map.get(ITEM_ID);
                if(d==null){
                    map.put(ITEM_ID,new PetDefinitionRepository.Def(ITEM_ID,NPC_ID,NAME,NAME,IDLE_ANIM,WALK_ANIM,WALK_ANIM,WALK_ANIM,WALK_ANIM,1,Integer.toString(MODEL_ID),PROVENANCE));
                    d=map.get(ITEM_ID);
                }
                if(d==null||d.npcId!=NPC_ID||d.standAnim!=IDLE_ANIM||d.walkAnim!=WALK_ANIM||!Integer.toString(MODEL_ID).equals(d.models)||!d.provenance.contains("CUSTOM_LOCALLAB"))
                    throw new IllegalStateException("Voidglass R2 pet mapping collision/inconsistent: "+d);
            }
        }catch(ReflectiveOperationException e){throw new ExceptionInInitializerError(e);}
    }

    static boolean active(PetState state,NpcEntity pet){
        return state!=null && state.active() && state.itemId()==ITEM_ID && state.npcId()==NPC_ID &&
               pet!=null && pet.pet && pet.petItemId==ITEM_ID && pet.definitionId==NPC_ID;
    }
    static String profile(){
        return NAME+" item="+ITEM_ID+" npc="+NPC_ID+" model="+MODEL_ID+
            " idle="+IDLE_ANIM+" walk/turn="+WALK_ANIM+" procAnim="+PROC_ANIM+" procGfx="+PROC_GFX+
            " palette="+PALETTE+" authority=CUSTOM_LOCALLAB_CONTENT gameplayModifier=NONE";
    }
    static String boundary(){
        return "Voidglass R2 is developer-created LocalLab content; model/animation/GFX IDs are current-client assets, " +
               "but the palette and pet identity are custom and MUST NOT be promoted into recovered SpawnPK production authority.";
    }
    private VoidglassR2CustomContent(){}
}
