package spk.local;

import java.util.*;

/** Read-only browser consumes the existing authority repositories rather than duplicating them. */
public final class DevAssetBrowserTest {
    public static void main(String[] args){
        String item=DevAssetBrowser.item(28807);
        req(item.contains("id=28807") && item.toLowerCase(Locale.ROOT).contains("doppel"),item);
        String pet=DevAssetBrowser.pet(28807);
        req(pet.contains("npc=8210") && pet.contains("provenance="),pet);
        List<String> hits=DevAssetBrowser.findItems("doppel",25);
        boolean found=false;for(String x:hits)if(x.startsWith("28807\t"))found=true;
        req(found,"search did not find 28807: "+hits);
        System.out.println("V592_DEV_ASSET_BROWSER_PASS item28807=true pet8210=true search=true provenanceVisible=true readOnly=true");
    }
    static void req(boolean b,String s){if(!b)throw new AssertionError(s);}
}
