package spk.local;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * LocalLab developer workbench utility commands.
 *
 * Covers trace inspection, static asset browsing, client-container item
 * previews and temporary combat-animation overrides. None of these routes
 * promote developer observations into gameplay authority.
 */
final class LocalDevToolCommandHandler {
    private final DevAuthorityWorkbench dev;
    private final BankState bank;
    private final EquipmentState equipment;

    LocalDevToolCommandHandler(
        DevAuthorityWorkbench dev,
        BankState bank,
        EquipmentState equipment
    ){
        this.dev=java.util.Objects.requireNonNull(dev,"dev");
        this.bank=java.util.Objects.requireNonNull(bank,"bank");
        this.equipment=java.util.Objects.requireNonNull(
            equipment,"equipment");
    }

    List<String> handle(
        String[] p,
        ServerPacketWriter serverPackets
    )throws IOException{
        if(p==null||p.length<1)return null;

        if(p[0].equalsIgnoreCase("devtrace")){
            String sub=
                p.length>=2
                    ?p[1].toLowerCase(Locale.ROOT)
                    :"info";

            if(sub.equals("on")){
                dev.trace().setEnabled(true);
                return one(
                    "V592_DEV_TRACE "+dev.trace().summary());
            }

            if(sub.equals("off")){
                dev.trace().setEnabled(false);
                return one(
                    "V592_DEV_TRACE "+dev.trace().summary());
            }

            if(sub.equals("clear")){
                dev.trace().clear();
                return one(
                    "V592_DEV_TRACE_CLEAR "+
                    dev.trace().summary());
            }

            if(sub.equals("show")){
                int limit=p.length>=3?parseInt(p[2],20):20;
                List<String> rows=dev.trace().snapshot(limit);
                ArrayList<String> out=new ArrayList<>();

                out.add(
                    "V592_DEV_TRACE_SHOW "+
                    dev.trace().summary());

                for(String row:rows){
                    out.add("V592_TRACE "+row);
                }

                return Collections.unmodifiableList(out);
            }

            return one(
                "V592_DEV_TRACE_INFO "+
                dev.trace().summary()+
                " commands=on|off|show_[n]|clear");
        }

        if(p[0].equalsIgnoreCase("devasset")){
            String sub=
                p.length>=2
                    ?p[1].toLowerCase(Locale.ROOT)
                    :"help";

            if(sub.equals("item")){
                int id=p.length>=3?parseInt(p[2],-1):-1;
                return one(
                    "V592_"+DevAssetBrowser.item(id));
            }

            if(sub.equals("pet")){
                int id=p.length>=3?parseInt(p[2],-1):-1;
                return one(
                    "V592_"+DevAssetBrowser.pet(id));
            }

            if(sub.equals("find")){
                if(p.length<3){
                    return one(
                        "V592_DEV_ASSET_FIND result=REJECTED_EMPTY");
                }

                String query=joinTokens(p,2);
                List<String> rows=
                    DevAssetBrowser.findItems(query,25);
                ArrayList<String> out=new ArrayList<>();

                out.add(
                    "V592_DEV_ASSET_FIND query=\""+
                    query+
                    "\" count="+rows.size());

                for(String row:rows){
                    out.add("V592_ASSET "+row);
                }

                return Collections.unmodifiableList(out);
            }

            return one(
                "V592_DEV_ASSET_HELP commands=item <id> | pet <itemId> | find <nameTerm>");
        }

        if(p[0].equalsIgnoreCase("devitem")){
            String sub=
                p.length>=2
                    ?p[1].toLowerCase(Locale.ROOT)
                    :"help";

            if(sub.equals("sprite")){
                int slot=p.length>=3?parseInt(p[2],-1):-1;
                int item=p.length>=4?parseInt(p[3],-1):-1;

                return one(
                    "V591_"+
                    bank.sendDevInventorySpritePreview(
                        slot,item,serverPackets));
            }

            if(sub.equals("gallery")){
                int start=p.length>=3?parseInt(p[2],0):0;

                if(p.length<4){
                    return one(
                        "V591_DEV_ITEM_GALLERY result=REJECTED_NEED_ITEM_IDS");
                }

                int[] ids=new int[p.length-3];
                for(int i=3;i<p.length;i++){
                    ids[i-3]=parseInt(p[i],-1);
                }

                return one(
                    "V591_"+
                    bank.sendDevInventorySpriteGallery(
                        start,ids,serverPackets));
            }

            if(sub.equals("restore")||
               sub.equals("reset")){
                return one(
                    "V591_"+
                    bank.restoreDevInventoryPreview(
                        serverPackets));
            }

            return one(
                "V591_DEV_ITEM_HELP commands=sprite <slot0..27> <itemId> | gallery <startSlot> <itemId...> | restore note=preview_is_client_container_only_do_not_click_it");
        }

        if(p[0].equalsIgnoreCase("devcombat")){
            String sub=
                p.length>=2
                    ?p[1].toLowerCase(Locale.ROOT)
                    :"info";
            int weapon=equipment.weapon();

            if(sub.equals("anim")){
                String value=
                    p.length>=3
                        ?p[2].toLowerCase(Locale.ROOT)
                        :"auto";

                try{
                    if(value.equals("auto")||
                       value.equals("reset")){
                        dev.setCombatAnimationOverride(
                            weapon,null);
                    }else if(value.equals("off")||
                             value.equals("none")){
                        dev.setCombatAnimationOverride(
                            weapon,-1);
                    }else{
                        int animation=
                            parseInt(value,-999);
                        if(animation<-1||animation>65535){
                            throw new IllegalArgumentException(
                                "animation -1..65535");
                        }

                        dev.setCombatAnimationOverride(
                            weapon,animation);
                    }

                    return one(
                        "V591_DEV_COMBAT_ANIM weapon="+weapon+
                        " override="+
                        (dev.hasCombatAnimationOverride(weapon)
                            ?dev.combatAnimationOverride(weapon)
                            :"AUTO")+
                        " authority=TEMPORARY_OVERRIDE");
                }catch(IllegalArgumentException e){
                    return one(
                        "V591_DEV_COMBAT_ANIM result=REJECTED "+
                        e.getMessage());
                }
            }

            return one(
                "V591_DEV_COMBAT_INFO weapon="+weapon+
                " profile="+
                CombatWeaponRepository.resolve(weapon)+
                " animOverride="+
                (dev.hasCombatAnimationOverride(weapon)
                    ?dev.combatAnimationOverride(weapon)
                    :"AUTO")+
                " scorchingNormalPolicy=UNBOUND_ANIMATION_SUPPRESSED_AFTER_LIVE_CRASH");
        }

        return null;
    }

    private static List<String> one(String line){
        return Collections.singletonList(line);
    }

    private static String joinTokens(String[] p,int start){
        if(p==null||start>=p.length)return "";

        StringBuilder builder=new StringBuilder();
        for(int i=start;i<p.length;i++){
            if(i>start)builder.append(' ');
            builder.append(p[i]);
        }
        return builder.toString();
    }

    private static int parseInt(String value,int fallback){
        try{
            return Integer.parseInt(value);
        }catch(Exception e){
            return fallback;
        }
    }
}
