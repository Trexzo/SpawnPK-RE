package spk.local;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Session-local ::devpet command family.
 *
 * These routes are developer/research presentation controls only. They do not
 * promote temporary overrides into recovered SpawnPK gameplay authority.
 */
final class LocalDevPetCommandHandler {
    private final DevAuthorityWorkbench dev;
    private final NpcRegistry npcs;
    private final MovementState movement;
    private final BankState bank;

    LocalDevPetCommandHandler(
        DevAuthorityWorkbench dev,
        NpcRegistry npcs,
        MovementState movement,
        BankState bank
    ){
        this.dev=java.util.Objects.requireNonNull(dev,"dev");
        this.npcs=java.util.Objects.requireNonNull(npcs,"npcs");
        this.movement=java.util.Objects.requireNonNull(movement,"movement");
        this.bank=java.util.Objects.requireNonNull(bank,"bank");
    }

    List<String> handle(
        String[] p,
        ServerPacketWriter serverPackets
    )throws IOException{
        if(p==null||
           p.length<1||
           !p[0].equalsIgnoreCase("devpet")){
            return null;
        }

        String sub=
            p.length>=2
                ?p[1].toLowerCase(Locale.ROOT)
                :"info";
        String result;

        if(sub.equals("info")){
            return one("V591_"+npcs.devInfo(movement));
        }

        if(sub.equals("fx")){
            String value=
                p.length>=3
                    ?p[2].toLowerCase(Locale.ROOT)
                    :"auto";

            Integer selector;
            if(value.equals("auto")||value.equals("reset")){
                selector=null;
            }else if(value.equals("next")){
                selector=
                    dev.petParticleSelector()==null
                        ?0
                        :((dev.petParticleSelector()+1)&255);
            }else if(value.equals("prev")){
                selector=
                    dev.petParticleSelector()==null
                        ?255
                        :((dev.petParticleSelector()+255)&255);
            }else{
                int parsed=parseInt(value,-1);
                if(parsed<0||parsed>255){
                    return one(
                        "V591_DEV_PET_FX result=REJECTED expected=auto|next|prev|0..255");
                }
                selector=parsed;
            }

            result=npcs.devSetParticleSelector(
                selector,movement,serverPackets);
            return one("V591_"+result);
        }

        if(sub.equals("npc")||sub.equals("preview")){
            int npc=p.length>=3?parseInt(p[2],-1):-1;
            result=npcs.previewPetDefinition(
                npc,movement,serverPackets);
            return one("V591_DEV_PET_NPC "+result);
        }

        if(sub.equals("map")){
            String kind=
                p.length>=3
                    ?p[2].toLowerCase(Locale.ROOT)
                    :"show";

            if(kind.equals("show")||kind.equals("info")){
                return one(
                    "V593_DEV_PET_MAP npc="+dev.petNpcBindings()+
                    " sprite="+dev.petSpriteBindings()+
                    " persisted=false");
            }

            if(kind.equals("clear")){
                if(p.length>=4){
                    int item=parseInt(p[3],-1);
                    dev.clearPetBinding(item);
                }else{
                    dev.clearPetBindings();
                }

                return one(
                    "V593_DEV_PET_MAP_CLEAR npc="+dev.petNpcBindings()+
                    " sprite="+dev.petSpriteBindings());
            }

            if(kind.equals("npc")){
                int item=p.length>=4?parseInt(p[3],-1):-1;
                int npc=p.length>=5?parseInt(p[4],-1):-1;

                if(item<0||npc<0||npc>16383){
                    return one(
                        "V593_DEV_PET_MAP_NPC result=REJECTED syntax=::devpet map npc <itemId> <npcId>");
                }

                dev.setPetNpcBinding(item,npc);
                String live="inactive";

                if(npcs.pet()!=null&&
                   npcs.pet().petItemId==item){
                    live=npcs.previewPetDefinition(
                        npc,movement,serverPackets);
                }

                return one(
                    "V593_DEV_PET_MAP_NPC item="+item+
                    " npc="+npc+
                    " live="+live+
                    " persisted=false");
            }

            if(kind.equals("sprite")){
                int item=p.length>=4?parseInt(p[3],-1):-1;
                int preview=p.length>=5?parseInt(p[4],-1):-1;

                if(item<0||
                   preview<0||
                   !ItemCatalog.exists(preview)){
                    return one(
                        "V593_DEV_PET_MAP_SPRITE result=REJECTED syntax=::devpet map sprite <itemId> <previewItemId>");
                }

                dev.setPetSpriteBinding(item,preview);
                return one(
                    "V593_"+
                    bank.sendDevInventoryVariantPreview(
                        dev.petSpriteBindings(),serverPackets));
            }

            if(kind.equals("applysprites")){
                return one(
                    "V593_"+
                    bank.sendDevInventoryVariantPreview(
                        dev.petSpriteBindings(),serverPackets));
            }

            return one(
                "V593_DEV_PET_MAP_HELP npc <itemId> <npcId> | sprite <itemId> <previewItemId> | applysprites | show | clear [itemId]");
        }

        if(sub.equals("visual")){
            String op=
                p.length>=3
                    ?p[2].toLowerCase(Locale.ROOT)
                    :"info";
            int active=
                npcs.pet()==null
                    ?-1
                    :npcs.pet().definitionId;

            if(op.equals("info")){
                int npc=
                    p.length>=4
                        ?parseInt(p[3],active)
                        :active;

                return one(
                    "V5124_"+
                    SpecialPetVisualLab.info(
                        npc,dev.petParticleSelector())+
                    " "+
                    LocalDevVisualOverrideStore.summary());
            }

            if(op.equals("npc")){
                int npc=p.length>=4?parseInt(p[3],-1):-1;
                return one(
                    "V593_"+
                    npcs.previewPetDefinition(
                        npc,movement,serverPackets));
            }

            if(op.equals("owner")){
                String value=
                    p.length>=4
                        ?p[3].toLowerCase(Locale.ROOT)
                        :"player";

                int raw=
                    value.equals("player")
                        ?32768+NpcRegistry.LOCAL_PLAYER_INDEX
                        :parseInt(value,-1);

                if(value.equals("raw")&&p.length>=5){
                    raw=parseInt(p[4],-1);
                }

                return one(
                    "V593_"+
                    npcs.devPetInteractionTarget(
                        raw,serverPackets));
            }

            if(op.equals("fx")){
                String value=
                    p.length>=4
                        ?p[3].toLowerCase(Locale.ROOT)
                        :"auto";

                Integer selector=
                    value.equals("auto")
                        ?null
                        :parseInt(value,-1);

                if(selector!=null&&
                   (selector<0||selector>255)){
                    return one(
                        "V593_DEV_PET_VISUAL_FX result=REJECTED");
                }

                return one(
                    "V593_"+
                    npcs.devSetParticleSelector(
                        selector,movement,serverPackets));
            }

            if(op.equals("alpha")){
                String value=
                    p.length>=4
                        ?p[3].toLowerCase(Locale.ROOT)
                        :"info";

                if(value.equals("info")){
                    return one(
                        "V5124_"+
                        SpecialPetVisualLab.inspectOnly(
                            op,active)+
                        " "+
                        LocalDevVisualOverrideStore.summary());
                }

                if(value.equals("auto")||
                   value.equals("reset")){
                    return one(
                        "V5124_"+
                        LocalDevVisualOverrideStore.set(
                            "alpha",null));
                }

                if(value.equals("off"))value="0";
                int parsed=parseInt(value,-1);

                if(parsed<0||parsed>255){
                    return one(
                        "V5124_DEV_PET_VISUAL_ALPHA result=REJECTED expected=auto|off|0..255");
                }

                return one(
                    "V5124_"+
                    LocalDevVisualOverrideStore.set(
                        "alpha",Integer.toString(parsed)));
            }

            if(op.equals("ai")){
                String value=
                    p.length>=4
                        ?p[3].toLowerCase(Locale.ROOT)
                        :"info";

                if(value.equals("info")){
                    return one(
                        "V5124_"+
                        SpecialPetVisualLab.inspectOnly(
                            op,active)+
                        " "+
                        LocalDevVisualOverrideStore.summary());
                }

                if(value.equals("auto")||
                   value.equals("reset")){
                    return one(
                        "V5124_"+
                        LocalDevVisualOverrideStore.set(
                            "ai",null));
                }

                if(value.equals("off"))value="0";

                try{
                    Integer.parseInt(value);
                }catch(Exception e){
                    return one(
                        "V5124_DEV_PET_VISUAL_AI result=REJECTED expected=auto|off|signedInt");
                }

                return one(
                    "V5124_"+
                    LocalDevVisualOverrideStore.set(
                        "ai",value));
            }

            if(op.equals("tint")){
                String value=
                    p.length>=4
                        ?p[3]
                        :"info";
                String lower=value.toLowerCase(Locale.ROOT);

                if(lower.equals("info")){
                    return one(
                        "V5124_"+
                        SpecialPetVisualLab.inspectOnly(
                            op,active)+
                        " "+
                        LocalDevVisualOverrideStore.summary());
                }

                if(lower.equals("auto")||
                   lower.equals("reset")){
                    return one(
                        "V5124_"+
                        LocalDevVisualOverrideStore.set(
                            "tint",null));
                }

                if(lower.equals("off")){
                    return one(
                        "V5124_"+
                        LocalDevVisualOverrideStore.set(
                            "tint","off"));
                }

                if(lower.equals("raw")&&p.length>=5){
                    try{
                        Integer.parseInt(p[4]);
                    }catch(Exception e){
                        return one(
                            "V5124_DEV_PET_VISUAL_TINT result=REJECTED raw_signedInt");
                    }

                    return one(
                        "V5124_"+
                        LocalDevVisualOverrideStore.set(
                            "tint","raw:"+p[4]));
                }

                String rgb=
                    lower.startsWith("#")
                        ?lower.substring(1)
                        :lower.startsWith("0x")
                            ?lower.substring(2)
                            :lower;

                try{
                    int parsed=Integer.parseInt(rgb,16);
                    if(parsed<0||parsed>0xffffff)
                        throw new Exception();

                    return one(
                        "V5124_"+
                        LocalDevVisualOverrideStore.set(
                            "tint",
                            "rgb:"+String.format("%06x",parsed)));
                }catch(Exception e){
                    return one(
                        "V5124_DEV_PET_VISUAL_TINT result=REJECTED expected=auto|off|#RRGGBB|0xRRGGBB|raw <signedInt>");
                }
            }

            if(op.equals("bodycycle")){
                String value=
                    p.length>=4
                        ?p[3].toLowerCase(Locale.ROOT)
                        :"info";

                if(value.equals("info")){
                    return one(
                        "V5124_"+
                        SpecialPetVisualLab.inspectOnly(
                            op,active)+
                        " "+
                        LocalDevVisualOverrideStore.summary());
                }

                if(value.equals("auto")||
                   value.equals("reset")){
                    return one(
                        "V5124_"+
                        LocalDevVisualOverrideStore.set(
                            "bodycycle",null));
                }

                if(value.equals("off")){
                    return one(
                        "V5124_"+
                        LocalDevVisualOverrideStore.set(
                            "bodycycle","off"));
                }

                int phase=parseInt(value,-1);
                if(phase<0||phase>66){
                    return one(
                        "V5124_DEV_PET_VISUAL_BODYCYCLE result=REJECTED expected=auto|off|0..66");
                }

                return one(
                    "V5124_"+
                    LocalDevVisualOverrideStore.set(
                        "bodycycle",
                        Integer.toString(phase)));
            }

            if(op.equals("intrinsicfx")){
                String value=
                    p.length>=4
                        ?p[3].toLowerCase(Locale.ROOT)
                        :"info";

                if(value.equals("info")){
                    return one(
                        "V5125_DEV_PET_VISUAL_INTRINSICFX npc="+
                        active+
                        " default="+
                        ((active==1334||active==8210)
                            ?"OFF_CORRECTED_PROFILE"
                            :"NATIVE_ON")+
                        " "+
                        LocalDevVisualOverrideStore.summary());
                }

                if(value.equals("auto")||
                   value.equals("reset")||
                   value.equals("default")){
                    return one(
                        "V5125_"+
                        LocalDevVisualOverrideStore.set(
                            "intrinsicfx",null));
                }

                if(value.equals("on")||
                   value.equals("native")||
                   value.equals("true")){
                    return one(
                        "V5125_"+
                        LocalDevVisualOverrideStore.set(
                            "intrinsicfx","on"));
                }

                if(value.equals("off")||
                   value.equals("false")){
                    return one(
                        "V5125_"+
                        LocalDevVisualOverrideStore.set(
                            "intrinsicfx","off"));
                }

                return one(
                    "V5125_DEV_PET_VISUAL_INTRINSICFX result=REJECTED expected=auto|on|off");
            }

            if(op.equals("state")){
                int state=
                    p.length>=4
                        ?parseInt(p[3],-1)
                        :-1;

                if(state<0||state>3){
                    return one(
                        "V5124_DEV_PET_VISUAL_STATE result=REJECTED expected=0..3");
                }

                return one(
                    "V5124_"+
                    npcs.setPetNativeState(
                        state,serverPackets));
            }

            if(op.equals("text")){
                if(p.length<4){
                    return one(
                        "V5124_DEV_PET_VISUAL_TEXT result=REJECTED expected=<text>");
                }

                return one(
                    "V5124_"+
                    npcs.forcePetText(
                        joinTokens(p,3),serverPackets));
            }

            if(op.equals("anim")){
                int anim=
                    p.length>=4
                        ?parseInt(p[3],-999)
                        :-999;
                int delay=
                    p.length>=5
                        ?parseInt(p[4],0)
                        :0;

                return one(
                    "V5124_"+
                    npcs.animatePet(
                        anim,delay,serverPackets));
            }

            if(op.equals("gfx")){
                int gfx=
                    p.length>=4
                        ?parseInt(p[3],-999)
                        :-999;
                int height=
                    p.length>=5
                        ?parseInt(p[4],0)
                        :0;
                int delay=
                    p.length>=6
                        ?parseInt(p[5],0)
                        :0;

                return one(
                    "V5124_"+
                    npcs.gfxPet(
                        gfx,height,delay,serverPackets));
            }

            if(op.equals("animfx")){
                int anim=
                    p.length>=4
                        ?parseInt(p[3],-999)
                        :-999;
                int gfx=
                    p.length>=5
                        ?parseInt(p[4],-999)
                        :-999;
                int height=
                    p.length>=6
                        ?parseInt(p[5],0)
                        :0;
                int delay=
                    p.length>=7
                        ?parseInt(p[6],0)
                        :0;

                return one(
                    "V5124_"+
                    npcs.animationAndGfxPet(
                        anim,0,gfx,height,delay,serverPackets));
            }

            if(op.equals("owneranim")){
                int anim=
                    p.length>=4
                        ?parseInt(p[3],-999)
                        :-999;

                if(anim<-1||anim>65535){
                    return one(
                        "V5124_DEV_PET_VISUAL_OWNERANIM result=REJECTED_RANGE");
                }

                serverPackets.varShort(
                    81,
                    CombatSync.player81AnimationOnly(anim));

                return one(
                    "V5124_DEV_PET_VISUAL_OWNERANIM anim="+
                    anim+
                    " authority=LOCAL_DEV_EXPERIMENT");
            }

            if(op.equals("ownergfx")){
                int gfx=
                    p.length>=4
                        ?parseInt(p[3],-999)
                        :-999;
                int height=
                    p.length>=5
                        ?parseInt(p[4],0)
                        :0;
                int delay=
                    p.length>=6
                        ?parseInt(p[5],0)
                        :0;

                try{
                    serverPackets.varShort(
                        81,
                        CombatSync.player81GfxOnly(
                            gfx,height,delay));

                    return one(
                        "V5124_DEV_PET_VISUAL_OWNERGFX gfx="+
                        gfx+
                        " height="+height+
                        " delay="+delay+
                        " authority=LOCAL_DEV_EXPERIMENT");
                }catch(IllegalArgumentException e){
                    return one(
                        "V5124_DEV_PET_VISUAL_OWNERGFX result=REJECTED "+
                        e.getMessage());
                }
            }

            if(op.equals("owneranimfx")){
                int anim=
                    p.length>=4
                        ?parseInt(p[3],-999)
                        :-999;
                int gfx=
                    p.length>=5
                        ?parseInt(p[4],-999)
                        :-999;
                int height=
                    p.length>=6
                        ?parseInt(p[5],0)
                        :0;
                int delay=
                    p.length>=7
                        ?parseInt(p[6],0)
                        :0;

                try{
                    serverPackets.varShort(
                        81,
                        CombatSync.player81AnimationAndGfx(
                            anim,gfx,height,delay));

                    return one(
                        "V5124_DEV_PET_VISUAL_OWNERANIMFX anim="+
                        anim+
                        " gfx="+gfx+
                        " height="+height+
                        " delay="+delay+
                        " authority=LOCAL_DEV_EXPERIMENT");
                }catch(IllegalArgumentException e){
                    return one(
                        "V5124_DEV_PET_VISUAL_OWNERANIMFX result=REJECTED "+
                        e.getMessage());
                }
            }

            if(op.equals("reset")||op.equals("clear")){
                ArrayList<String> lines=new ArrayList<>();
                lines.add(
                    "V5124_"+
                    LocalDevVisualOverrideStore.clear());
                lines.add(
                    "V5124_"+
                    npcs.devSetParticleSelector(
                        null,movement,serverPackets));
                return Collections.unmodifiableList(lines);
            }

            return one(
                "V5124_DEV_PET_VISUAL_HELP info [npcId] | npc <id> (model/body via definition) | owner player|raw <target> | fx auto|0..255 | intrinsicfx auto|on|off | alpha auto|off|0..255 | ai auto|off|<int> | tint auto|off|#RRGGBB|raw <int> | bodycycle auto|off|0..66 | state 0..3 | text <text> | anim <id> [delay] | gfx <id> [height] [delay] | animfx <anim> <gfx> [height] [delay] | owneranim <id> | ownergfx <id> [height] [delay] | owneranimfx <anim> <gfx> [height] [delay] | reset ; client fields=LOCAL_DEV_EXPERIMENT");
        }

        if(sub.equals("anim")){
            int anim=
                p.length>=3
                    ?parseInt(p[2],-999)
                    :-999;
            int delay=
                p.length>=4
                    ?parseInt(p[3],0)
                    :0;

            result=npcs.animatePet(
                anim,delay,serverPackets);
            return one("V591_DEV_PET_ANIM "+result);
        }

        if(sub.equals("gfx")){
            int gfx=
                p.length>=3
                    ?parseInt(p[2],-999)
                    :-999;
            int height=
                p.length>=4
                    ?parseInt(p[3],0)
                    :0;
            int delay=
                p.length>=5
                    ?parseInt(p[4],0)
                    :0;

            result=npcs.gfxPet(
                gfx,height,delay,serverPackets);
            return one("V591_DEV_PET_GFX "+result);
        }

        if(sub.equals("animfx")){
            int anim=
                p.length>=3
                    ?parseInt(p[2],-999)
                    :-999;
            int gfx=
                p.length>=4
                    ?parseInt(p[3],-999)
                    :-999;
            int height=
                p.length>=5
                    ?parseInt(p[4],0)
                    :0;
            int delay=
                p.length>=6
                    ?parseInt(p[5],0)
                    :0;

            result=npcs.animationAndGfxPet(
                anim,0,gfx,height,delay,serverPackets);
            return one("V591_DEV_PET_ANIMFX "+result);
        }

        if(sub.equals("follow")){
            String op=
                p.length>=3
                    ?p[2].toLowerCase(Locale.ROOT)
                    :"info";

            if(op.equals("freeze")){
                result=npcs.devFollowFreeze(true);
            }else if(op.equals("resume")){
                result=npcs.devFollowFreeze(false);
            }else if(op.equals("step")){
                result=npcs.devFollowStep(
                    movement,serverPackets);
            }else if(op.equals("snap")){
                result=npcs.devSnapToOwner(
                    movement,serverPackets);
            }else if(op.equals("normal")||
                     op.equals("reset")){
                result=npcs.devFollowDelay(null);
            }else if(op.equals("delay")){
                long ms=
                    p.length>=4
                        ?parseLong(p[3],-1L)
                        :-1L;

                try{
                    result=npcs.devFollowDelay(
                        ms<0?null:ms);
                }catch(IllegalArgumentException e){
                    result="REJECTED "+e.getMessage();
                }
            }else{
                result=npcs.devInfo(movement);
            }

            return one("V591_DEV_PET_FOLLOW "+result);
        }

        return one(
            "V593_DEV_PET_HELP commands=info | fx auto|next|prev|0..255 | npc <npcId> | map npc|sprite|applysprites|show|clear | visual info|npc|owner|fx|intrinsicfx|alpha|tint|ai|bodycycle|state|anim|gfx|animfx|reset | anim <id> [delay] | gfx <id> [height] [delay] | animfx <anim> <gfx> [height] [delay] | follow freeze|resume|step|snap|normal|delay <ms>");
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

    private static long parseLong(String value,long fallback){
        try{
            return Long.parseLong(value);
        }catch(Exception e){
            return fallback;
        }
    }
}
