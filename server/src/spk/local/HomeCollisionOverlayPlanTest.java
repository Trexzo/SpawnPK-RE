package spk.local;

import java.util.*;

/** Exact-client collision-policy certification for WORLD-R4. */
public final class HomeCollisionOverlayPlanTest {
    public static void main(String[] args){
        HomeWorldManifest.requireCollisionPolicyCertification();
        eq(HomeCollisionOverlayRepository.count(),53,"plan count");
        eq(HomeObjectCollisionDefinitionRepository.count(),19,"collision def count");
        int adds=0,removes=0,rect=0,walls=0,removeOnly=0,rectCells=0,deltaRows=0;
        Set<String> occupied=new HashSet<>();
        for(HomeCollisionOverlayRepository.Entry e:HomeCollisionOverlayRepository.all()){
            if(e.isRemove()){removes++;if(e.removeOnly())removeOnly++;continue;}
            adds++; if(!Boolean.TRUE.equals(e.movementClip))fail("HOME add unexpectedly non-clipping seq="+e.seq); if(!Boolean.TRUE.equals(e.projectileClip))fail("HOME add unexpectedly projectile-passable seq="+e.seq);
            List<ClientCollisionDeltaCodec.Delta> ds=HomeCollisionOverlayRepository.addDeltas(e); deltaRows+=ds.size();
            if("RECTANGLE".equals(e.collisionClass)){rect++;rectCells+=e.effectiveWidth*e.effectiveHeight;for(ClientCollisionDeltaCodec.Delta d:ds)occupied.add(d.worldX+","+d.worldY);}
            else if("WALL".equals(e.collisionClass)){walls++; if(e.shape!=0)fail("HOME wall shape !=0");}
            else fail("unexpected add class "+e.collisionClass);
        }
        eq(adds,26,"adds");eq(removes,27,"removes");eq(rect,20,"rectangle adds");eq(walls,6,"wall adds");eq(removeOnly,1,"remove-only count");eq(rectCells,54,"rectangle cell count");eq(occupied.size(),54,"unique rectangle cells");

        // Exact remove-only doorway/scenery delta retained as a deliberate server-baseline identity gap.
        List<HomeCollisionOverlayRepository.Entry> door=HomeCollisionOverlayRepository.at(3091,3508); if(door.size()!=1||!door.get(0).removeOnly())fail("3091,3508 remove-only missing");
        if(!door.get(0).certainty.contains("SERVER_BASELINE_OBJECT_ID_NOT_CAPTURED"))fail("remove-only evidence boundary lost");

        // Portal 15477 is 5x2 unrotated => 10 clipped tiles, exact current definition.
        HomeCollisionOverlayRepository.Entry portal=findAdd(15477,3084,3483);eq(portal.effectiveWidth,5,"portal width");eq(portal.effectiveHeight,2,"portal height");eq(HomeCollisionOverlayRepository.addDeltas(portal).size(),10,"portal collision deltas");
        // Spell-book altar 3x1 rotation2 remains 3x1.
        HomeCollisionOverlayRepository.Entry altar=findAdd(6552,3095,3506);eq(altar.effectiveWidth,3,"altar width");eq(altar.effectiveHeight,1,"altar height");
        // Chaos altar 2x1 rotation2 remains 2x1.
        HomeCollisionOverlayRepository.Entry chaos=findAdd(61,3098,3506);eq(chaos.effectiveWidth,2,"chaos width");
        // Fountain 2x2 unaffected by rotation1.
        HomeCollisionOverlayRepository.Entry fountain=findAdd(153,3076,3493);eq(fountain.effectiveWidth,2,"fountain width");eq(fountain.effectiveHeight,2,"fountain height");

        // Exact straight-wall mask proof for Energy Barrier rot1 from current rs.f.
        HomeCollisionOverlayRepository.Entry barrier=findAdd(4470,3092,3506);List<ClientCollisionDeltaCodec.Delta> b=HomeCollisionOverlayRepository.addDeltas(barrier);
        has(b,3092,3506,2);has(b,3092,3507,32);has(b,3092,3506,1024);has(b,3092,3507,16384);

        // Add/remove sparse collision codec round-trip masks for representative rectangle and wall.
        roundTripRect(100,200,3,1,1,true); roundTripWall(100,200,0,true);roundTripWall(100,200,1,true);roundTripWall(100,200,2,true);roundTripWall(100,200,3,true);

        System.out.println("WORLD_R4_EXACT_COLLISION_OVERLAY_PASS mutations=53 remove=27 add=26 rectAdds=20 wallAdds=6 rectCells=54 uniqueRectCells=54 removeOnly=1 objectDefs=19 exactClientPolicy=true");
    }
    private static HomeCollisionOverlayRepository.Entry findAdd(int scene,int x,int y){for(HomeCollisionOverlayRepository.Entry e:HomeCollisionOverlayRepository.at(x,y))if(e.isAdd()&&e.sceneObjectId==scene)return e;throw new AssertionError("missing add "+scene+" @"+x+","+y);}
    private static void roundTripRect(int x,int y,int sx,int sy,int r,boolean p){List<ClientCollisionDeltaCodec.Delta>a=ClientCollisionDeltaCodec.rectangle(ClientCollisionDeltaCodec.Kind.ADD,x,y,sx,sy,r,p),b=ClientCollisionDeltaCodec.rectangle(ClientCollisionDeltaCodec.Kind.REMOVE,x,y,sx,sy,r,p);eq(a.size(),b.size(),"rect rt size");for(int i=0;i<a.size();i++){ClientCollisionDeltaCodec.Delta x1=a.get(i),x2=b.get(i);if(x1.worldX!=x2.worldX||x1.worldY!=x2.worldY||x1.mask!=x2.mask)fail("rect roundtrip mismatch");}}
    private static void roundTripWall(int x,int y,int r,boolean p){List<ClientCollisionDeltaCodec.Delta>a=ClientCollisionDeltaCodec.straightWall(ClientCollisionDeltaCodec.Kind.ADD,x,y,r,p),b=ClientCollisionDeltaCodec.straightWall(ClientCollisionDeltaCodec.Kind.REMOVE,x,y,r,p);eq(a.size(),b.size(),"wall rt size");for(int i=0;i<a.size();i++){ClientCollisionDeltaCodec.Delta x1=a.get(i),x2=b.get(i);if(x1.worldX!=x2.worldX||x1.worldY!=x2.worldY||x1.mask!=x2.mask)fail("wall roundtrip mismatch");}}
    private static void has(List<ClientCollisionDeltaCodec.Delta> d,int x,int y,int mask){for(ClientCollisionDeltaCodec.Delta q:d)if(q.worldX==x&&q.worldY==y&&q.mask==mask)return;fail("missing mask 0x"+Integer.toHexString(mask)+" @"+x+","+y);}
    private static void eq(int a,int b,String w){if(a!=b)fail(w+" "+a+" != "+b);} private static void fail(String s){throw new AssertionError(s);}
}
