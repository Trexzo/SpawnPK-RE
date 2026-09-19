package spk.local;

import java.util.HashSet;
import java.util.Set;

final class ItemActionResolver {
    private ItemActionResolver(){}
    static String inventoryAction(int itemId,int zeroBasedSlot){ if(zeroBasedSlot<0||zeroBasedSlot>=5)return null; return resolve(itemId,zeroBasedSlot,new HashSet<Integer>()); }
    private static String resolve(int id,int slot,Set<Integer> seen){
        if(!seen.add(id))return null; ItemCatalog.Meta m=ItemCatalog.get(id); if(m==null)return null;
        if(m.actions!=null&&slot<m.actions.length){String a=m.actions[slot];if(a!=null&&!a.trim().isEmpty())return a.trim();}
        String a=null;
        if(m.clone>=0&&(a=resolve(m.clone,slot,seen))!=null)return a;
        if(m.equipClone>=0&&(a=resolve(m.equipClone,slot,seen))!=null)return a;
        if(m.fullClone>=0&&(a=resolve(m.fullClone,slot,seen))!=null)return a;
        return null;
    }
    static String inventoryOption5Semantic(int itemId){String exact=InventoryOption5Repository.explicitAction(itemId);if(exact!=null)return exact;String a=inventoryAction(itemId,4);return a==null?"Drop":a;}
    static String inventoryOption1Semantic(int itemId){return inventoryAction(itemId,0);}
}
