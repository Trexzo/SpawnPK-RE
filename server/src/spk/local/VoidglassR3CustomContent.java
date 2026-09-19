package spk.local;

import java.util.*;

/**
 * LOCAL DEVELOPMENT CONTENT ONLY.
 * R3 corrects the invalid R2 item id (32760 was outside the client's 0..29999 table)
 * and exposes four non-Hydra native-asset composite candidates for visual iteration.
 * The candidates are new LocalLab combinations of existing client model assets; they are
 * NOT claimed to be recovered SpawnPK production content or newly-authored mesh bytes.
 */
final class VoidglassR3CustomContent {
    static final String NAME="Voidglass Nistirio";
    static final int LEGACY_R2_ITEM_ID=32760;
    static final int ITEM_ID=29999;
    static final int DEFAULT_NPC_ID=12000;
    static final int PROC_GFX=5042; // exact-current "Black hole opening", deliberately non-Hydra
    static final String PROC_TEXT="VOIDGLASS RIFT";
    static final String PROVENANCE="CUSTOM_LOCALLAB_VOIDGLASS_R3_NATIVE_COMPOSITOR";

    static final class Candidate {
        final int index,npcId,stand,walk,scale;
        final String name,models,description;
        Candidate(int index,int npcId,String name,String models,int stand,int walk,int scale,String description){
            this.index=index;this.npcId=npcId;this.name=name;this.models=models;this.stand=stand;this.walk=walk;this.scale=scale;this.description=description;
        }
        String summary(){return index+":"+name+" npc="+npcId+" models="+models+" anim="+stand+"/"+walk+" scale="+scale;}
    }

    // These are intentionally visually different families. Model merging is performed by the native client.
    static final Candidate[] CANDIDATES={
        new Candidate(1,12000,"Rift Reaper","32327|32325|32324|32326|32765|32530",1662,1663,58,"Blood-reaper body + abyssal portal + crystal accent"),
        new Candidate(2,12001,"Arcane Singularity","17378|17394|17387|17399|17390|34252",66,63,45,"Arcane-demon body + black-hole model"),
        new Candidate(3,12002,"Nightmare Shard","39182|32530|40177",8593,8592,45,"Nightmare body + crystal + darkness warning model"),
        new Candidate(4,12003,"Ripper Soul","44733|42282|34252",10921,10920,55,"Ripper-demon body + blue-soul + black-hole models")
    };

    static Candidate candidate(int index){for(Candidate c:CANDIDATES)if(c.index==index)return c;return null;}
    static Candidate candidateByNpc(int npcId){for(Candidate c:CANDIDATES)if(c.npcId==npcId)return c;return null;}
    static Candidate defaultCandidate(){return CANDIDATES[0];}

    @SuppressWarnings("unchecked")
    static void ensureRuntimePetMapping(){
        try{
            java.lang.reflect.Field f=PetDefinitionRepository.class.getDeclaredField("BY_ITEM");f.setAccessible(true);
            Map<Integer,PetDefinitionRepository.Def> map=(Map<Integer,PetDefinitionRepository.Def>)f.get(null);
            synchronized(map){
                PetDefinitionRepository.Def old=map.get(LEGACY_R2_ITEM_ID);
                if(old!=null && old.provenance!=null && old.provenance.contains("CUSTOM_LOCALLAB")) map.remove(LEGACY_R2_ITEM_ID);
                Candidate c=defaultCandidate();
                PetDefinitionRepository.Def d=map.get(ITEM_ID);
                if(d!=null && (d.npcId!=c.npcId || d.provenance==null || !d.provenance.contains("CUSTOM_LOCALLAB")))
                    throw new IllegalStateException("Voidglass R3 item mapping collision: "+d);
                if(d==null)map.put(ITEM_ID,new PetDefinitionRepository.Def(ITEM_ID,c.npcId,NAME,NAME,c.stand,c.walk,c.walk,c.walk,c.walk,1,c.models,PROVENANCE));
            }
        }catch(ReflectiveOperationException e){throw new ExceptionInInitializerError(e);}
    }

    static boolean active(PetState state,NpcEntity pet){
        return state!=null && state.active() && state.itemId()==ITEM_ID &&
            pet!=null && pet.pet && pet.petItemId==ITEM_ID && candidateByNpc(pet.definitionId)!=null;
    }
    static String profile(){
        StringBuilder b=new StringBuilder(NAME+" item="+ITEM_ID+" clientRange=0..29999 procGfx="+PROC_GFX+" candidates=");
        for(Candidate c:CANDIDATES){if(c.index>1)b.append(" ; ");b.append(c.summary());}
        return b+" authority=LOCAL_DEV_NATIVE_ASSET_COMPOSITOR";
    }
    static String boundary(){return "Voidglass R3 is LocalLab developer content. Candidate silhouettes are NEW COMPOSITIONS of existing current-client model bytes; no candidate is production recovery and no newly-authored raw mesh is claimed yet.";}
    private VoidglassR3CustomContent(){}
}
