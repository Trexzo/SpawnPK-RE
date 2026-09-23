package spk.local;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Temporary ::devplayer presentation command family.
 *
 * These commands remain localhost developer probes and do not mutate recovered
 * production gameplay authority.
 */
final class LocalDevPlayerCommandHandler {
    private final PlayerPresentationService playerPresentation;
    private final EquipmentState equipment;
    private final PlayerState playerState;

    LocalDevPlayerCommandHandler(
        PlayerPresentationService playerPresentation,
        EquipmentState equipment,
        PlayerState playerState
    ){
        this.playerPresentation=java.util.Objects.requireNonNull(
            playerPresentation,"playerPresentation");
        this.equipment=java.util.Objects.requireNonNull(
            equipment,"equipment");
        this.playerState=java.util.Objects.requireNonNull(
            playerState,"playerState");
    }

    List<String> handle(
        String[] p,
        String username,
        ServerPacketWriter serverPackets
    )throws IOException{
        if(p==null||
           p.length<1||
           !p[0].equalsIgnoreCase("devplayer")){
            return null;
        }

        String sub=
            p.length>=2
                ?p[1].toLowerCase(Locale.ROOT)
                :"help";

        if(sub.equals("anim")){
            int anim=p.length>=3?parseInt(p[2],-999):-999;
            if(anim<-1||anim>65535){
                return one(
                    "V591_DEV_PLAYER_ANIM result=REJECTED_RANGE");
            }

            serverPackets.varShort(
                81,
                CombatSync.player81AnimationOnly(anim));

            return one(
                "V591_DEV_PLAYER_ANIM anim="+anim+
                " gfx=NONE authority=TEMPORARY_VISUAL_PROBE");
        }

        if(sub.equals("gfx")){
            int gfx=p.length>=3?parseInt(p[2],-999):-999;
            int height=p.length>=4?parseInt(p[3],0):0;
            int delay=p.length>=5?parseInt(p[4],0):0;

            try{
                serverPackets.varShort(
                    81,
                    CombatSync.player81GfxOnly(
                        gfx,height,delay));

                return one(
                    "V591_DEV_PLAYER_GFX gfx="+gfx+
                    " height="+height+
                    " delay="+delay+
                    " anim=NONE authority=TEMPORARY_VISUAL_PROBE");
            }catch(IllegalArgumentException e){
                return one(
                    "V591_DEV_PLAYER_GFX result=REJECTED "+
                    e.getMessage());
            }
        }

        if(sub.equals("animfx")){
            int anim=p.length>=3?parseInt(p[2],-999):-999;
            int gfx=p.length>=4?parseInt(p[3],-999):-999;
            int height=p.length>=5?parseInt(p[4],0):0;
            int delay=p.length>=6?parseInt(p[5],0):0;

            try{
                serverPackets.varShort(
                    81,
                    CombatSync.player81AnimationAndGfx(
                        anim,gfx,height,delay));

                return one(
                    "V591_DEV_PLAYER_ANIMFX anim="+anim+
                    " gfx="+gfx+
                    " height="+height+
                    " delay="+delay+
                    " authority=TEMPORARY_VISUAL_PROBE");
            }catch(IllegalArgumentException e){
                return one(
                    "V591_DEV_PLAYER_ANIMFX result=REJECTED "+
                    e.getMessage());
            }
        }

        if(sub.equals("rank")||
           sub.equals("appearance-rank")||
           sub.equals("arank")){
            String raw=p.length>=3?p[2]:"";
            int rank;

            if(raw.equalsIgnoreCase("clear")||
               raw.equalsIgnoreCase("none")){
                rank=0;
            }else{
                try{
                    rank=Integer.parseInt(raw);
                }catch(NumberFormatException e){
                    return one(
                        "V5186_DEV_PLAYER_RANK result=REJECTED expected=signed_short_or_clear");
                }
            }

            if(rank<Short.MIN_VALUE||rank>Short.MAX_VALUE){
                return one(
                    "V5186_DEV_PLAYER_RANK result=REJECTED expected=signed_short_or_clear");
            }

            playerState.setAppearanceRank(rank);
            playerPresentation.refresh(
                username,
                equipment,
                playerState,
                serverPackets
            );

            return one(
                "V5186_DEV_PLAYER_RANK rank="+rank+
                " persisted=false"+
                " authority=EXACT_CURRENT_CLIENT_AC_CHANNEL+LOCAL_LAB_DEV_POLICY");
        }

        if(sub.equals("morph")||sub.equals("npc")){
            int npc=p.length>=3?parseInt(p[2],-1):-1;
            if(npc<0||npc>16383){
                return one(
                    "V592_DEV_PLAYER_MORPH result=REJECTED expected=npcId_0..16383");
            }

            try{
                return one(
                    "V592_"+
                    playerPresentation.morph(
                        npc,
                        username,
                        equipment,
                        playerState,
                        serverPackets));
            }catch(IllegalArgumentException e){
                return one(
                    "V592_DEV_PLAYER_MORPH result=REJECTED "+
                    e.getMessage());
            }
        }

        if(sub.equals("clear")||
           sub.equals("normal")||
           sub.equals("unmorph")){
            return one(
                "V592_"+
                playerPresentation.clear(
                    username,
                    equipment,
                    playerState,
                    serverPackets));
        }

        if(sub.equals("info")){
            return one(
                "V592_"+playerPresentation.info()+
                " appearanceRank="+
                playerState.appearanceRank());
        }

        return one(
            "V592_DEV_PLAYER_HELP commands=info | rank <signed-short|clear> | morph <npcId> | clear | anim <id> | gfx <id> [height] [delay] | animfx <anim> <gfx> [height] [delay] nurseIsolation='anim 10184' vs 'gfx 1310' vs 'animfx 10184 1310'");
    }

    private static List<String> one(String line){
        return Collections.singletonList(line);
    }

    private static int parseInt(String value,int fallback){
        try{
            return Integer.parseInt(value);
        }catch(Exception e){
            return fallback;
        }
    }
}
