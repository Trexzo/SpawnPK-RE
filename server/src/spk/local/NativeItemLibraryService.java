package spk.local;

import java.io.*;
import java.util.*;

/**
 * Server half of the exact-current native Item Library (root 47500).
 * The current client already owns the interface, preview renderer and request
 * routes. LocalLab supplies only evidence-backed item semantics. The search
 * button's production prompt flow is intentionally not fabricated; exact
 * igsearch <current item name> C2S103 requests are supported.
 */
final class NativeItemLibraryService {
    static final int ROOT=47500,SEARCH_BUTTON=47809,BONUS_BUTTON=47816,BONUS_CLOSE=47836;
    private boolean open;
    private int selectedItem=-1;

    boolean isOpen(){return open;}
    int selectedItem(){return selectedItem;}

    String open(ServerPacketWriter w,int itemId)throws IOException{
        ItemAuthorityRepository.Entry e=ItemAuthorityRepository.get(itemId);
        if(e==null)return "ITEM_LIBRARY_REJECTED_UNKNOWN_ITEM id="+itemId;
        w.beginBatch();
        try{
            w.fixed(97,BootstrapPackets.interface97(ROOT));
            control(w,"ITEM_GUIDE_RESET_PREVIEW");
            control(w,"ITEM_GUIDE_RESET_CATEGORY_ATTRIBUTES");
            control(w,"ITEM_GUIDE_FLUSH_CATEGORIES");
            control(w,"ITEM_GUIDE_RESET_CAT_SCROLL");
            control(w,"ITEM_GUIDE_RESET_DESCRIPTION_ATTRIBUTES");
            control(w,"ITEM_GUIDE_FLUSH_DESCRIPTION");
            control(w,"ITEM_GUIDE_BONUS_WIDGET OFF");
            text(w,47502,"Recovered current item authority");
            text(w,47503,clean(e.name));
            text(w,47505,"{T}Selected item");
            text(w,47506,clean(e.name)+" (#"+e.itemId+")");
            text(w,47507,"{T}Evidence");
            text(w,47508,"Exact current client/cache + passive runtime where available");
            control(w,"ITEM_GUIDE_SELECTED_47506");
            // Use the native preview field channel. Generic SLOT_1 is exact and
            // does not require us to guess the item's equipment slot.
            control(w,"ITEM_GUIDE_SET_ITEM_SPRITE");
            text(w,53,"SLOT_1 "+e.itemId);
            control(w,"ITEM_GUIDE_REFRESH_PREVIEW");
            populateDescription(w,e);
        } finally { w.endBatch(); }
        open=true;
        selectedItem=itemId;
        return "ITEM_LIBRARY_OPEN root=47500 item="+itemId+" name="+clean(e.name)+" authority=EXACT_CURRENT_NATIVE_UI";
    }

    String searchExact(ServerPacketWriter w,String currentName)throws IOException{
        ItemAuthorityRepository.Entry e=ItemAuthorityRepository.byExactName(currentName);
        if(e==null)return "ITEM_LIBRARY_IGSEARCH_NOT_FOUND name="+currentName;
        return open(w,e.itemId)+" request=igsearch_exact_name";
    }

    String handleWidget(ServerPacketWriter w,int widget)throws IOException{
        if(!open)return null;
        if(widget==BONUS_BUTTON){
            control(w,"ITEM_GUIDE_BONUS_WIDGET ON");
            int[] ids={47821,47822,47823,47824,47825,47827,47828,47829,47830,47831,47833,47834,47835};
            for(int id:ids)text(w,id,"N/A");
            text(w,47502,"Base equipment bonuses are server-fed; unrecovered values stay N/A");
            return "ITEM_LIBRARY_BONUS_ON item="+selectedItem+" values=FAIL_CLOSED_NA";
        }
        if(widget==BONUS_CLOSE){control(w,"ITEM_GUIDE_BONUS_WIDGET OFF");return "ITEM_LIBRARY_BONUS_OFF item="+selectedItem;}
        if(widget==SEARCH_BUTTON){
            text(w,47502,"Native search prompt response is still server-authority; use View guide / igsearch");
            return "ITEM_LIBRARY_SEARCH_BUTTON prompt=UNRESOLVED_SERVER_FLOW no_fake_prompt=true";
        }
        if(widget==47506 && selectedItem>=0)return "ITEM_LIBRARY_SELECTED_ROW item="+selectedItem;
        return null;
    }

    void close(){open=false;selectedItem=-1;}

    private static void populateDescription(ServerPacketWriter w,ItemAuthorityRepository.Entry e)throws IOException{
        ArrayList<String> lines=new ArrayList<>();
        lines.add("{C}<col=C0981F>"+clean(e.name)+"</col>");
        lines.add("{C}Item ID: "+e.itemId);
        if(!blank(e.inventoryActions))lines.add("Actions: "+e.inventoryActions);
        if(e.equippable())lines.add("Equipment capability: "+(e.wield?"Wield":"")+(e.wield&&e.wear?" / ":"")+(e.wear?"Wear":""));
        if(!blank(e.effectText)){lines.add("{B}Current item description/effect text");addWrapped(lines,e.effectText,72);}
        if(!blank(e.mechanicsSummary)){lines.add("{B}Recovered static combat/mechanics evidence");addWrapped(lines,e.mechanicsSummary,72);}
        V913WeaponRuntimeAuthority.Profile rp=V913WeaponRuntimeAuthority.resolve(e.itemId);
        if(rp!=null){
            lines.add("{B}Production runtime presentation (V9.13)");
            String seq=(rp.preAnimation>=0?rp.preAnimation+" -> ":"")+"anim "+rp.attackAnimation;
            if(rp.actorGfx>=0)seq+=" | actor gfx "+rp.actorGfx;
            if(rp.projectileId>=0)seq+=" | projectile "+rp.projectileId;
            if(rp.targetGfx>=0)seq+=" | target gfx "+rp.targetGfx;
            if(rp.speedTicks>0)seq+=" | ~"+rp.speedTicks+" ticks";
            addWrapped(lines,seq,72);
            addWrapped(lines,"Evidence: "+rp.evidence,72);
        }
        AmmoAuthorityRepository.Entry ammo=AmmoAuthorityRepository.get(e.itemId);
        if(ammo!=null){
            lines.add("{B}Ammo / projectile-item authority");
            addWrapped(lines,ammo.classification+" | stackability authority: "+ammo.effectiveAuthority,72);
            if(!blank(ammo.note))addWrapped(lines,ammo.note,72);
        }
        SpecialAttackAuthorityRepository.Entry spec=SpecialAttackAuthorityRepository.get(e.itemId);
        if(spec!=null){
            lines.add("{B}Special-attack metadata");
            if(!blank(spec.effectText))addWrapped(lines,spec.effectText,72);
            String meta="metadata="+spec.metadataAuthority+" | presentation="+spec.presentationBinding+" | formula="+(spec.formulaResolved()?"resolved":"server authority / unresolved");
            addWrapped(lines,meta,72);
        }
        PetResearchAuthorityRepository.Entry pet=PetResearchAuthorityRepository.get(e.itemId);
        if(pet!=null){
            lines.add("{B}Pet research authority");
            addWrapped(lines,"mapping="+pet.mappingCertainty+" | authoritative NPC mapping="+pet.authoritativeNpcMapping+" | NPC candidates="+java.util.Arrays.toString(pet.candidateNpcIds),72);
            if(!blank(pet.effectText))addWrapped(lines,pet.effectText,72);
        }
        if(!blank(e.relationSummary)){lines.add("{B}Stat inheritance evidence");addWrapped(lines,e.relationSummary,72);}
        if(!blank(e.policySummary)){lines.add("{B}Explicit current policy metadata");addWrapped(lines,e.policySummary,72);}
        lines.add("{B}Authority boundary");
        lines.add("Complete 14-field equipment bonuses are server-fed through equipstr/key24.");
        lines.add("Unknown numeric bonuses are deliberately not inferred from generic RSPS/OSRS data.");
        int key=47709;
        for(String line:lines){if(key>47808)break;text(w,key++,line);}
    }
    private static void addWrapped(List<String> out,String text,int max){
        if(text==null)return;
        String x=text.replace('\r',' ').replace('\n',' ').replaceAll("\\s+"," ").trim();
        while(x.length()>max){int cut=x.lastIndexOf(' ',max);if(cut<20)cut=max;out.add(x.substring(0,cut).trim());x=x.substring(cut).trim();}
        if(!x.isEmpty())out.add(x);
    }
    private static String clean(String s){return ItemAuthorityRepository.stripTags(s);}
    private static boolean blank(String s){return s==null||s.trim().isEmpty();}
    private static void control(ServerPacketWriter w,String s)throws IOException{w.varShort(126,new PacketPayloadWriter().putStringNl(s==null?"":s).putU16BELowAdd128(0).toByteArray());}
    private static void text(ServerPacketWriter w,int key,String s)throws IOException{w.varShort(126,BootstrapPackets.widgetText126(key,s==null?"":s));}
}
