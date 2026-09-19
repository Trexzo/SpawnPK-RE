package spk.local;

import java.util.Properties;

/** Persisted main-pet and configured mini-pet state. Mini selection survives main-pet pickup. */
final class PetState {
    private int itemId=-1;
    private int npcId=-1;
    private int miniItemId=-1;

    boolean active(){return itemId>=0&&npcId>=0;}
    int itemId(){return itemId;}
    int npcId(){return npcId;}
    boolean miniConfigured(){return miniItemId>=0;}
    int miniItemId(){return miniItemId;}

    void activate(PetDefinitionRepository.Def d){
        if(d==null)throw new IllegalArgumentException("pet definition");
        itemId=d.itemId; npcId=d.npcId;
    }
    /** Clear only the active main pet. Configured mini-pet is independent and persists. */
    void clear(){itemId=-1;npcId=-1;}
    void configureMini(int item){
        if(MiniPetDefinitionRepository.get(item)==null)throw new IllegalArgumentException("unknown mini pet "+item);
        miniItemId=item;
    }
    void clearMini(){miniItemId=-1;}

    void saveAccountProperties(Properties p){
        p.setProperty("pet.activeItemId",Integer.toString(itemId));
        p.setProperty("pet.activeNpcId",Integer.toString(npcId));
        p.setProperty("pet.miniItemId",Integer.toString(miniItemId));
    }
    void loadAccountProperties(Properties p){
        itemId=parse(p.getProperty("pet.activeItemId"),-1);
        npcId=parse(p.getProperty("pet.activeNpcId"),-1);
        if((itemId<0)!=(npcId<0)){itemId=-1;npcId=-1;}
        if(active()){
            PetDefinitionRepository.Def d=PetDefinitionRepository.get(itemId);
            if(d==null||d.npcId!=npcId){itemId=-1;npcId=-1;}
        }
        miniItemId=parse(p.getProperty("pet.miniItemId"),-1);
        if(miniItemId>=0&&MiniPetDefinitionRepository.get(miniItemId)==null) miniItemId=-1;
    }
    private static int parse(String s,int fallback){try{return s==null?fallback:Integer.parseInt(s);}catch(Exception e){return fallback;}}
}
