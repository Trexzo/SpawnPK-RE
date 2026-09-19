package spk.local;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Range-1 melee may touch only cardinally; diagonal adjacency must route first. */
public final class MeleeCardinalRangeTest {
    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception{
        CombatEngine c=new CombatEngine(); MovementState m=new MovementState(); NpcRegistry n=new NpcRegistry(); EquipmentState e=new EquipmentState();
        e.setWeapon(21566); // Scythe fixture, melee range 1.
        NpcEntity target=new NpcEntity(77,1489,m.x()+1,m.y()+1);
        Field f=NpcRegistry.class.getDeclaredField("visible"); f.setAccessible(true); ((List<NpcEntity>)f.get(n)).add(target);
        ByteArrayOutputStream out=new ByteArrayOutputStream(); ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(new int[]{8,6,7,5}));
        long now=System.currentTimeMillis();
        String req=c.request(target,m,e.weapon(),now);
        if(!req.contains("DEFERRED_RANGE"))throw new AssertionError("diagonal melee was accepted: "+req);
        String approach=c.beginServerOwnedApproach(target,m,e.weapon(),now);
        if(!approach.startsWith("SERVER_APPROACH_ACCEPTED"))throw new AssertionError(approach);
        long tick=1; String attack=null;
        while(m.queued()>0 && tick<20){
            m.advance(); String r=c.tick(m,n,e,w,tick++,null); if(r!=null&&r.contains("ATTACK_SENT"))attack=r;
        }
        int dx=Math.abs(m.x()-target.x),dy=Math.abs(m.y()-target.y);
        if(dx+dy!=1)throw new AssertionError("did not stop cardinally adjacent dx="+dx+" dy="+dy+" owner="+m.x()+","+m.y()+" target="+target.x+","+target.y);
        if(attack==null){String r=c.tick(m,n,e,w,tick,null);if(r!=null&&r.contains("ATTACK_SENT"))attack=r;}
        if(attack==null)throw new AssertionError("no attack after cardinal approach");
        System.out.println("V5124_MELEE_CARDINAL_RANGE_PASS diagonalRejected=true manhattanRange1=true serverApproach=true");
    }
}
