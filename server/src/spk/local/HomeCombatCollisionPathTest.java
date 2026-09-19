package spk.local;
import java.util.*;
public final class HomeCombatCollisionPathTest{
  public static void main(String[] args){
    if(!HomeCombatPathfinder.blockedTile(3084,3495))throw new AssertionError("known HOME blocked tile missing");
    List<int[]> p=HomeCombatPathfinder.route(3083,3495,3086,3495,1);
    if(p==null||p.isEmpty())throw new AssertionError("no route around collision");
    int px=3083,py=3495; boolean sawDiagonal=false;
    for(int[] s:p){
      if(HomeCombatPathfinder.blockedTile(s[0],s[1]))throw new AssertionError("route entered blocked tile "+s[0]+","+s[1]);
      if(!HomeCombatPathfinder.canStep(px,py,s[0],s[1]))throw new AssertionError("illegal/corner-cut step "+px+","+py+"->"+s[0]+","+s[1]);
      if(Math.abs(s[0]-px)==1&&Math.abs(s[1]-py)==1)sawDiagonal=true;
      px=s[0];py=s[1];
    }
    if(Math.abs(px-3086)+Math.abs(py-3495)!=1)throw new AssertionError("did not finish cardinal-adjacent: "+px+","+py);

    List<int[]> d=HomeCombatPathfinder.route(3090,3490,3095,3495,1);
    if(d==null||d.isEmpty())throw new AssertionError("open diagonal path missing");
    boolean openDiag=false; int ox=3090,oy=3490;
    for(int[] s:d){if(Math.abs(s[0]-ox)==1&&Math.abs(s[1]-oy)==1)openDiag=true;ox=s[0];oy=s[1];}
    if(!openDiag)throw new AssertionError("open route did not use diagonal progress");
    int cheb=Math.max(Math.abs(3095-3090),Math.abs(3495-3490));
    if(d.size()>cheb)throw new AssertionError("open route not shortest-depth client BFS: steps="+d.size()+" cheb="+cheb);

    System.out.println("V5131_HOME_COMBAT_CLIENT_BFS_PATH_PASS blocker=10997@3084,3495 detourSteps="+p.size()+" openSteps="+d.size()+" openDiagonal="+openDiag+" blockedTiles="+HomeCombatPathfinder.blockedTileCount()+" blockedEdges="+HomeCombatPathfinder.blockedEdgeCount());
  }
}
