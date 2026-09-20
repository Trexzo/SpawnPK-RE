package spk.local;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Temporary ::devnpc entity/presentation command family.
 *
 * Dev-owned NPC actors remain nonpersistent LocalLab research entities.
 */
final class LocalDevNpcCommandHandler {
    private final NpcRegistry npcs;
    private final MovementState movement;

    LocalDevNpcCommandHandler(
        NpcRegistry npcs,
        MovementState movement
    ){
        this.npcs=java.util.Objects.requireNonNull(npcs,"npcs");
        this.movement=java.util.Objects.requireNonNull(
            movement,"movement");
    }

    List<String> handle(
        String[] p,
        ServerPacketWriter serverPackets
    )throws IOException{
        if(p==null||
           p.length<1||
           !p[0].equalsIgnoreCase("devnpc")){
            return null;
        }

        String sub=
            p.length>=2
                ?p[1].toLowerCase(Locale.ROOT)
                :"help";

        if(sub.equals("list")){
            int limit=p.length>=3?parseInt(p[2],20):20;
            return one(
                "V592_"+npcs.devNpcList(limit));
        }

        if(sub.equals("info")){
            int scene=p.length>=3?parseInt(p[2],-1):-1;
            return one(
                "V592_"+npcs.devNpcInfo(scene,movement));
        }

        if(sub.equals("spawn")){
            int npc=p.length>=3?parseInt(p[2],-1):-1;
            int dx=p.length>=4?parseInt(p[3],1):1;
            int dy=p.length>=5?parseInt(p[4],0):0;

            return one(
                "V592_"+
                npcs.devSpawnNpc(
                    npc,dx,dy,movement,serverPackets));
        }

        if(sub.equals("remove")){
            int scene=p.length>=3?parseInt(p[2],-1):-1;
            return one(
                "V592_"+
                npcs.devRemoveNpc(
                    scene,serverPackets));
        }

        if(sub.equals("clear")){
            return one(
                "V592_"+
                npcs.devRemoveAllNpcs(serverPackets));
        }

        if(sub.equals("anim")){
            int scene=p.length>=3?parseInt(p[2],-1):-1;
            int anim=p.length>=4?parseInt(p[3],-999):-999;
            int delay=p.length>=5?parseInt(p[4],0):0;

            return one(
                "V592_"+
                npcs.devNpcAnimation(
                    scene,anim,delay,serverPackets));
        }

        if(sub.equals("gfx")){
            int scene=p.length>=3?parseInt(p[2],-1):-1;
            int gfx=p.length>=4?parseInt(p[3],-999):-999;
            int height=p.length>=5?parseInt(p[4],0):0;
            int delay=p.length>=6?parseInt(p[5],0):0;

            return one(
                "V592_"+
                npcs.devNpcGfx(
                    scene,gfx,height,delay,serverPackets));
        }

        if(sub.equals("text")){
            int scene=p.length>=3?parseInt(p[2],-1):-1;
            String text=
                p.length>=4
                    ?joinTokens(p,3)
                    :"";

            return one(
                "V592_"+
                npcs.devNpcText(
                    scene,text,serverPackets));
        }

        if(sub.equals("target")){
            int scene=p.length>=3?parseInt(p[2],-1):-1;
            String value=
                p.length>=4
                    ?p[3].toLowerCase(Locale.ROOT)
                    :"";

            int target=
                value.equals("player")
                    ?32768+NpcRegistry.LOCAL_PLAYER_INDEX
                    :parseInt(value,-1);

            return one(
                "V592_"+
                npcs.devNpcTarget(
                    scene,target,serverPackets));
        }

        if(sub.equals("hit")){
            int scene=p.length>=3?parseInt(p[2],-1):-1;
            int damage=p.length>=4?parseInt(p[3],0):0;
            int max=
                p.length>=6
                    ?parseInt(p[5],100)
                    :100;
            int current=
                p.length>=5
                    ?parseInt(p[4],Math.max(0,max-damage))
                    :Math.max(0,max-damage);

            return one(
                "V592_"+
                npcs.devNpcHit(
                    scene,
                    damage,
                    current,
                    max,
                    serverPackets));
        }

        return one(
            "V592_DEV_NPC_HELP commands=list [limit] | info <scene> | spawn <npcId> [dx] [dy] | remove <scene> | clear | anim <scene> <anim> [delay] | gfx <scene> <gfx> [height] [delay] | text <scene> <text...> | target <scene> player|<raw0..65535> | hit <scene> <damage> [currentHp] [maxHp]");
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
