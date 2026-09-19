package spk.local;

import java.util.*;

final class MiniPetDefinitionRepository {
  static final class Def { final int itemId,npcId; final String name,authority; Def(int i,int n,String nm,String a){itemId=i;npcId=n;name=nm;authority=a;} public String toString(){return "MiniPetDef{"+itemId+"->"+npcId+","+name+","+authority+"}";} }
  private static final Map<Integer,Def> BY_ITEM=new LinkedHashMap<>();
  static {
    add(22088,4020,"Lucky fairy","STRONG_STATIC_CACHE");
    add(22089,4021,"Gilded fairy","STRONG_STATIC_CACHE");
    add(23627,1935,"Treasure beast","STRONG_STATIC_CACHE");
    add(23628,1936,"Bounty beast","STRONG_STATIC_CACHE");
    add(23629,1937,"Soul beast","STRONG_RUNTIME_CACHE");
    add(23988,1938,"Gilded beast","STRONG_STATIC_CACHE");
    add(22869,4018,"Blood dragon","STRONG_STATIC_CACHE");
    add(22870,4017,"King blood dragon","STRONG_STATIC_CACHE");
    add(24248,4019,"King soul dragon","STRONG_STATIC_CACHE");
    add(22965,419,"Fortune genie","STRONG_STATIC_CACHE");
    add(22175,3283,"Squirrel","CANDIDATE_STRONGEST");
    add(22176,148,"Pelican","STRONG_STATIC_CACHE");
    add(22177,5429,"Penguin","CANDIDATE_STRONGEST");
    add(22178,7233,"Monkey","CANDIDATE_STRONGEST");
    add(24262,7455,"Gilded falcon","CANDIDATE");
    add(22954,6958,"Terrier puppy","V9_LOCAL_MAPPING");
    add(22955,6960,"Greyhound puppy","V9_LOCAL_MAPPING");
    add(22956,6962,"Labrador puppy","V9_LOCAL_MAPPING");
    add(22957,6964,"Dalmatian puppy","V9_LOCAL_MAPPING");
    add(22958,6966,"Sheepdog puppy","V9_LOCAL_MAPPING");
    add(22959,6969,"Bulldog puppy","V9_LOCAL_MAPPING");
  }
  private static void add(int item,int npc,String name,String authority){BY_ITEM.put(item,new Def(item,npc,name,authority));}
  static Def get(int item){return BY_ITEM.get(item);}
  static boolean isMiniPetItem(int item){return BY_ITEM.containsKey(item);}
  static Collection<Def> all(){return Collections.unmodifiableCollection(BY_ITEM.values());}
  static int count(){return BY_ITEM.size();}
}
