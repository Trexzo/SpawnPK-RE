package spk.local;

import java.io.IOException;

/**
 * LOCAL_DEV world mutation command family.
 *
 * The caller invokes this only from the authoritative WorldPulse path. The
 * handler owns command/domain routing, while exact scene packet publication
 * stays delegated to SceneUpdatePublisher and existing registries/codecs.
 */
final class LocalDevWorldCommandHandler {
    private final World world;
    private final MovementState movement;

    LocalDevWorldCommandHandler(World world,MovementState movement){
        this.world=java.util.Objects.requireNonNull(world,"world");
        this.movement=java.util.Objects.requireNonNull(movement,"movement");
    }

    boolean handle(
        String[] p,
        SceneUpdatePublisher scenePublisher,
        String username,
        long sessionWorldTick,
        String tag
    )throws IOException{
        if(p==null||p.length==0||!p[0].equalsIgnoreCase("devworld"))return false;

        String sub=p.length>=2?p[1].toLowerCase(java.util.Locale.ROOT):"info";

        if(sub.equals("info")){
            System.out.println(tag+"V5121_DEV_WORLD "+world.summary()+" metrics="+world.metrics()+
                " sceneChunk="+scenePublisher.context().currentChunkX()+","+
                scenePublisher.context().currentChunkY());
            return true;
        }

        if(sub.equals("ground")&&p.length>=4){
            int item=parseInt(p[2],-1);
            int amount=parseInt(p[3],-1);
            int dx=p.length>=5?parseInt(p[4],0):0;
            int dy=p.length>=6?parseInt(p[5],0):0;
            if(!ItemCatalog.exists(item)||amount<=0||amount>65535||dx<-16||dx>16||dy<-16||dy>16){
                System.out.println(tag+"V511_DEV_WORLD_GROUND result=REJECTED syntax=::devworld ground <item> <1..65535> [dx] [dy]");
                return true;
            }
            Tile t=new Tile(movement.x()+dx,movement.y()+dy,0);
            GroundItem before=world.groundItems().findOwned(item,t.x,t.y,0,username);
            int old=before==null?0:before.amount;
            if((long)old+amount>65535){
                System.out.println(tag+"V511_DEV_WORLD_GROUND result=REJECTED_AMOUNT_OVERFLOW");
                return true;
            }
            GroundItem g=world.groundItems().add(item,amount,t,username,sessionWorldTick,true);
            if(old>0)scenePublisher.groundAmount(g,old);
            else scenePublisher.groundSpawn(g);
            System.out.println(tag+"V511_DEV_WORLD_GROUND result=OK "+g);
            return true;
        }

        if(sub.equals("groundclear")){
            int n=0;
            for(GroundItem g:world.groundItems().removeDevOwned()){
                try{
                    scenePublisher.groundRemove(g);
                    n++;
                }catch(IllegalArgumentException ignored){}
            }
            System.out.println(tag+"V511_DEV_WORLD_GROUNDCLEAR removed="+n);
            return true;
        }

        if(sub.equals("object")&&p.length>=7){
            int id=parseInt(p[2],-1);
            int dx=parseInt(p[3],0);
            int dy=parseInt(p[4],0);
            int shape=parseInt(p[5],-1);
            int rot=parseInt(p[6],-1);
            Tile t=new Tile(movement.x()+dx,movement.y()+dy,0);
            try{
                WorldObject o=world.objects().put(id,t,shape,rot,true);
                scenePublisher.objectAdd(id,t,shape,rot);
                System.out.println(tag+"V511_DEV_WORLD_OBJECT result=OK "+o);
            }catch(Exception e){
                System.out.println(tag+"V511_DEV_WORLD_OBJECT result=REJECTED "+e.getMessage());
            }
            return true;
        }

        if(sub.equals("objremove")&&p.length>=6){
            int dx=parseInt(p[2],0);
            int dy=parseInt(p[3],0);
            int shape=parseInt(p[4],-1);
            int rot=parseInt(p[5],-1);
            Tile t=new Tile(movement.x()+dx,movement.y()+dy,0);
            try{
                WorldObject old=world.objects().removeAt(t,shape);
                scenePublisher.objectRemove(t,shape,rot);
                System.out.println(tag+"V511_DEV_WORLD_OBJREMOVE result=OK old="+old);
            }catch(Exception e){
                System.out.println(tag+"V511_DEV_WORLD_OBJREMOVE result=REJECTED "+e.getMessage());
            }
            return true;
        }

        if(sub.equals("objanim")&&p.length>=7){
            int anim=parseInt(p[2],-1);
            int dx=parseInt(p[3],0);
            int dy=parseInt(p[4],0);
            int shape=parseInt(p[5],-1);
            int rot=parseInt(p[6],-1);
            try{
                scenePublisher.objectAnimation(
                    anim,new Tile(movement.x()+dx,movement.y()+dy,0),shape,rot);
                System.out.println(tag+"V511_DEV_WORLD_OBJANIM result=OK anim="+anim);
            }catch(Exception e){
                System.out.println(tag+"V511_DEV_WORLD_OBJANIM result=REJECTED "+e.getMessage());
            }
            return true;
        }

        if(sub.equals("gfx")&&p.length>=5){
            int gfx=parseInt(p[2],-1);
            int dx=parseInt(p[3],0);
            int dy=parseInt(p[4],0);
            int h=p.length>=6?parseInt(p[5],0):0;
            int d=p.length>=7?parseInt(p[6],0):0;
            try{
                scenePublisher.spotGraphic(gfx,new Tile(movement.x()+dx,movement.y()+dy,0),h,d);
                System.out.println(tag+"V511_DEV_WORLD_GFX result=OK gfx="+gfx);
            }catch(Exception e){
                System.out.println(tag+"V511_DEV_WORLD_GFX result=REJECTED "+e.getMessage());
            }
            return true;
        }

        if(sub.equals("sound")&&p.length>=3){
            int id=parseInt(p[2],-1);
            int delay=p.length>=4?parseInt(p[3],0):0;
            int loops=p.length>=5?parseInt(p[4],0):0;
            try{
                scenePublisher.soundEffect(id,delay,loops);
                System.out.println(tag+"V511_DEV_WORLD_SOUND result=OK id="+id);
            }catch(Exception e){
                System.out.println(tag+"V511_DEV_WORLD_SOUND result=REJECTED "+e.getMessage());
            }
            return true;
        }

        System.out.println(tag+
            "V511_DEV_WORLD_HELP ground <item> <amount> [dx] [dy] | groundclear | object <id> <dx> <dy> <shape> <rot> | objremove <dx> <dy> <shape> <rot> | objanim <anim> <dx> <dy> <shape> <rot> | gfx <gfx> <dx> <dy> [height] [delay] | sound <id> [delay] [loops] | info");
        return true;
    }

    private static int parseInt(String s,int fallback){
        try{return Integer.parseInt(s);}catch(Exception e){return fallback;}
    }
}
