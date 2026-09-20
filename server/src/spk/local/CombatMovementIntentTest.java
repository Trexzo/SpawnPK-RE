package spk.local;

public final class CombatMovementIntentTest {
    @SuppressWarnings("unchecked")
    public static void main(String[] args){
        CombatEngine c=new CombatEngine(); MovementState m=new MovementState(); NpcRegistry n=new NpcRegistry();
        NpcEntity target=new NpcEntity(77,1489,m.x()+4,m.y());
        try { java.lang.reflect.Field f=NpcRegistry.class.getDeclaredField("visible"); f.setAccessible(true); ((java.util.List<NpcEntity>)f.get(n)).add(target); } catch(Exception e){ throw new RuntimeException(e); }
        long now=System.currentTimeMillis();
        String r=c.request(target,m,21566,now); if(!r.contains("DEFERRED_RANGE"))throw new AssertionError(r);
        String route=c.beginServerOwnedApproach(target,m,21566,now); if(!route.startsWith("SERVER_APPROACH_ACCEPTED"))throw new AssertionError(route);
        if(m.queued()!=3)throw new AssertionError("expected 3 server-owned steps got "+m.queued()+" "+route);

        // One immediate client movement packet is the interaction-routing echo.
        MovementRequest echo=new MovementRequest(164,false,new int[]{target.x-2},new int[]{target.y},new byte[0]);
        if(!c.consumeImmediateApproachEcho(echo,now+10))throw new AssertionError("first approach echo not consumed");
        if(c.consumeImmediateApproachEcho(echo,now+20))throw new AssertionError("approach echo consumed twice");
        if(m.queued()!=3)throw new AssertionError("client echo replaced server route");

        // The next movement is unambiguously manual and must clear combat.
        // Issue #13 made recovered HOME collision authoritative, so choose an
        // actually open adjacent step instead of the old collision-blind x-3 fixture.
        int[][] directions={{-1,0},{1,0},{0,-1},{0,1}};
        int manualX=Integer.MIN_VALUE,manualY=Integer.MIN_VALUE;
        for(int[] d:directions){
            int x=m.x()+d[0],y=m.y()+d[1];
            if(HomeCombatPathfinder.canStep(m.x(),m.y(),x,y)){manualX=x;manualY=y;break;}
        }
        if(manualX==Integer.MIN_VALUE)throw new AssertionError("no collision-open manual movement fixture");
        MovementRequest manual=new MovementRequest(164,false,new int[]{manualX},new int[]{manualY},new byte[0]);
        if(!c.cancelForManualMovement())throw new AssertionError("manual cancel did not report active combat");
        if(c.state().active())throw new AssertionError("target remains active");
        if(!m.accept(manual).startsWith("ACCEPTED"))throw new AssertionError("manual route not accepted");
        System.out.println("V5123_COMBAT_MOVEMENT_INTENT_PASS serverOwnedApproach=true firstClientEchoIgnored=true laterMovementCancels=true collisionAwareManualFixture=true");
    }
}