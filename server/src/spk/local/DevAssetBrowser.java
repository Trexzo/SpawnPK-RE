package spk.local;

import java.util.*;

/** Read-only authority browser over repositories already shipped in LocalLab. */
final class DevAssetBrowser {
    private DevAssetBrowser(){}

    static String item(int id){
        ItemCatalog.Meta m=ItemCatalog.get(id);
        if(m==null) return "DEV_ASSET_ITEM_NOT_FOUND id="+id;
        PetDefinitionRepository.Def pet=PetDefinitionRepository.get(id);
        CombatWeaponProfile combat=CombatWeaponRepository.resolve(id);
        return "DEV_ASSET_ITEM id="+m.id+
            " name=\""+safe(m.name)+"\""+
            " tradeable="+m.tradeable+
            " stackable="+m.stackable+
            " actions="+Arrays.toString(m.actions)+
            " clone="+m.clone+" equipClone="+m.equipClone+" fullClone="+m.fullClone+
            " pet="+(pet==null?"none":(pet.itemId+"->npc"+pet.npcId+"/"+pet.provenance))+
            " combat="+(combat==null?"none":combat.toString())+
            " interface="+CombatInterfaceRepository.describe(CombatInterfaceRepository.forWeapon(id));
    }

    static List<String> findItems(String term,int limit){
        String q=term==null?"":term.trim().toLowerCase(Locale.ROOT);
        int max=Math.max(1,Math.min(50,limit));
        ArrayList<String> out=new ArrayList<>();
        if(q.isEmpty()){ out.add("DEV_ASSET_FIND_REJECTED empty_query"); return out; }
        for(ItemCatalog.Meta m:ItemCatalog.all()){
            if(m.name!=null && m.name.toLowerCase(Locale.ROOT).contains(q)){
                out.add(m.id+"\t"+m.name);
                if(out.size()>=max) break;
            }
        }
        if(out.isEmpty()) out.add("DEV_ASSET_FIND_NONE query=\""+safe(term)+"\"");
        return out;
    }

    static String pet(int itemId){
        PetDefinitionRepository.Def d=PetDefinitionRepository.get(itemId);
        if(d==null){
            if(PetDefinitionRepository.isAmbiguous(itemId))
                return "DEV_ASSET_PET_AMBIGUOUS item="+itemId+" evidence="+PetDefinitionRepository.ambiguousEvidence(itemId);
            return "DEV_ASSET_PET_NOT_MAPPED item="+itemId;
        }
        return "DEV_ASSET_PET item="+d.itemId+" name=\""+safe(d.itemName)+"\" npc="+d.npcId+
            " npcName=\""+safe(d.npcName)+"\" stand="+d.standAnim+" walk="+d.walkAnim+
            " turn180="+d.turn180Anim+" turnCW="+d.turn90CWAnim+" turnCCW="+d.turn90CCWAnim+
            " size="+d.size+" models="+d.models+" provenance="+d.provenance;
    }

    private static String safe(String s){ return s==null?"":s.replace('"','\''); }
}
