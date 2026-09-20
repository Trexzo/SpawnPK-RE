package spk.local;

import java.util.*;

/**
 * LOCAL DEVELOPMENT CONTENT ONLY.
 *
 * R3 is now driven by server/data/custom_asset_manifest.tsv and
 * server/data/custom_pet_mappings.tsv. The manifest describes LocalLab-owned
 * item/NPC/model/animation/GFX identities expected from an external exact-current
 * client/cache packer. It is not recovered SpawnPK production content.
 */
final class VoidglassR3CustomContent {
    static final String NAME="Voidglass Nistirio";
    static final int LEGACY_R2_ITEM_ID=32760;
    static final int ITEM_ID=29999;
    static final int DEFAULT_NPC_ID=12000;
    private static final String CONTENT_KEY="voidglass_nistirio";
    private static final List<CustomAssetManifestRepository.Asset> MANIFEST=manifest();
    static final int PROC_GFX=MANIFEST.get(0).gfxId;
    static final String PROC_TEXT=MANIFEST.get(0).text;
    static final String PROVENANCE=MANIFEST.get(0).provenance;

    static final class Candidate {
        final int index,npcId,stand,walk,scale;
        final String name,models,description;
        Candidate(int index,int npcId,String name,String models,int stand,int walk,int scale,String description){
            this.index=index;this.npcId=npcId;this.name=name;this.models=models;this.stand=stand;this.walk=walk;this.scale=scale;this.description=description;
        }
        String summary(){return index+":"+name+" npc="+npcId+" models="+models+" anim="+stand+"/"+walk+" scale="+scale;}
    }

    static final Candidate[] CANDIDATES=loadCandidates();

    static Candidate candidate(int index){for(Candidate c:CANDIDATES)if(c.index==index)return c;return null;}
    static Candidate candidateByNpc(int npcId){for(Candidate c:CANDIDATES)if(c.npcId==npcId)return c;return null;}
    static Candidate defaultCandidate(){return CANDIDATES[0];}

    /**
     * Compatibility entry point retained for callers that previously installed the
     * mapping reflectively. It now performs validation only; repository state is
     * loaded declaratively during PetDefinitionRepository initialization.
     */
    static void ensureRuntimePetMapping(){
        PetDefinitionRepository.Def d=PetDefinitionRepository.get(ITEM_ID);
        Candidate c=defaultCandidate();
        if(d==null) throw new IllegalStateException("Voidglass R3 mapping missing from custom_pet_mappings.tsv");
        if(d.npcId!=c.npcId || d.standAnim!=c.stand || d.walkAnim!=c.walk)
            throw new IllegalStateException("Voidglass R3 manifest/mapping mismatch: "+d);
        if(d.provenance==null || !d.provenance.startsWith("CUSTOM_LOCALLAB"))
            throw new IllegalStateException("Voidglass R3 mapping provenance escaped CUSTOM_LOCALLAB: "+d.provenance);
        if(PetDefinitionRepository.get(LEGACY_R2_ITEM_ID)!=null)
            throw new IllegalStateException("legacy invalid Voidglass R2 item mapping retained");
    }

    static boolean active(PetState state,NpcEntity pet){
        return state!=null && state.active() && state.itemId()==ITEM_ID &&
            pet!=null && pet.pet && pet.petItemId==ITEM_ID && candidateByNpc(pet.definitionId)!=null;
    }

    static String profile(){
        StringBuilder b=new StringBuilder(NAME+" item="+ITEM_ID+" clientRange=0..29999 procGfx="+PROC_GFX+" candidates=");
        for(Candidate c:CANDIDATES){if(c.index>1)b.append(" ; ");b.append(c.summary());}
        return b+" authority=LOCAL_DEV_MANIFEST customProvenance="+PROVENANCE;
    }

    static String boundary(){
        return "Voidglass R3 is LocalLab developer content. Candidate identities are read from the structured custom asset manifest; no candidate is production recovery and no proprietary cache mutation is performed by the server.";
    }

    private static List<CustomAssetManifestRepository.Asset> manifest(){
        List<CustomAssetManifestRepository.Asset> rows=CustomAssetManifestRepository.byContentKey(CONTENT_KEY);
        if(rows.size()!=4) throw new ExceptionInInitializerError("Voidglass R3 expected 4 manifest variants, got "+rows.size());
        String text=rows.get(0).text;
        int gfx=rows.get(0).gfxId;
        for(int i=0;i<rows.size();i++){
            CustomAssetManifestRepository.Asset a=rows.get(i);
            if(a.kind!=CustomAssetManifestRepository.Kind.PET_VARIANT)
                throw new ExceptionInInitializerError("Voidglass R3 kind="+a.kind);
            if(a.variant!=i+1) throw new ExceptionInInitializerError("Voidglass R3 variant gap at "+a.variant);
            if(a.itemId!=ITEM_ID) throw new ExceptionInInitializerError("Voidglass R3 item mismatch "+a.itemId);
            if(a.npcId!=DEFAULT_NPC_ID+i) throw new ExceptionInInitializerError("Voidglass R3 npc mismatch "+a.npcId);
            if(a.gfxId!=gfx || !a.text.equals(text))
                throw new ExceptionInInitializerError("Voidglass R3 proc presentation must be consistent across variants");
        }
        return rows;
    }

    private static Candidate[] loadCandidates(){
        Candidate[] out=new Candidate[MANIFEST.size()];
        for(int i=0;i<MANIFEST.size();i++){
            CustomAssetManifestRepository.Asset a=MANIFEST.get(i);
            out[i]=new Candidate(a.variant,a.npcId,a.name,a.models.replace(',', '|'),
                a.standAnim,a.walkAnim,a.scale,a.description);
        }
        return out;
    }

    private VoidglassR3CustomContent(){}
}
