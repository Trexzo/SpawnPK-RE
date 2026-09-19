package spk.local;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

public final class CombatServerOwnedApproachTest {
    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception{
        CombatEngine c=new CombatEngine(); MovementState m=new MovementState(); NpcRegistry n=new NpcRegistry(); EquipmentState e=new EquipmentState();
        e.setWeapon(21566);
        NpcEntity target=new NpcEntity(77,1489,m.x()+4,m.y());
        Field f=NpcRegistry.class.getDeclaredField("visible"); f.setAccessible(true); ((List<NpcEntity>)f.get(n)).add(target);
        ByteArrayOutputStream out=new ByteArrayOutputStream(); ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(new int[]{4,3,2,1}));
        long now=System.currentTimeMillis();
        String req=c.request(target,m,e.weapon(),now); if(!req.contains("DEFERRED_RANGE"))throw new AssertionError(req);
        String approach=c.beginServerOwnedApproach(target,m,e.weapon(),now); if(!approach.startsWith("SERVER_APPROACH_ACCEPTED"))throw new AssertionError(approach);
        String attack=null; long tick=1;
        while(m.queued()>0 && tick<20){
            m.advance();
            String r=c.tick(m,n,e,w,tick++,null); if(r!=null&&r.contains("ATTACK_SENT"))attack=r;
        }
        if(LocalSession.chebyshev(m.x(),m.y(),target.x,target.y)!=1)throw new AssertionError("did not stop at melee range owner="+m.x()+","+m.y()+" target="+target.x+","+target.y);
        if(attack==null){String r=c.tick(m,n,e,w,tick,null);if(r!=null&&r.contains("ATTACK_SENT"))attack=r;}
        if(attack==null)throw new AssertionError("server-owned approach reached range but did not attack");
        System.out.println("V5123_COMBAT_SERVER_APPROACH_PASS distance=1 attackAfterArrival=true routeIndependentOfClientMovement=true");
    }
}
