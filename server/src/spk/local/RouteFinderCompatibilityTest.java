package spk.local;

import java.util.*;

public final class RouteFinderCompatibilityTest {
    public static void main(String[] args){
        assertParity(
            3083,3495,
            3086,3495,
            1
        );

        assertParity(
            3090,3490,
            3095,3495,
            1
        );

        RouteFinder.Result result=
            RouteFinder.find(
                RouteRequest.combatCompatibility(
                    3083,
                    3495,
                    0,
                    3086,
                    3495,
                    1
                )
            );

        if(!"HOME_COMBAT_CLIENT_BFS_COMPATIBILITY".equals(
                result.authority))
            throw new AssertionError(
                "compatibility authority label changed: "+
                result.authority
            );

        if(result.blockedTileCount!=
                HomeCombatPathfinder.blockedTileCount()||
           result.blockedEdgeCount!=
                HomeCombatPathfinder.blockedEdgeCount())
            throw new AssertionError(
                "compatibility collision diagnostics changed"
            );

        System.out.println(
            "ROUTE_FINDER_COMPATIBILITY_PASS "+
            "authority="+result.authority+
            " blockedTiles="+result.blockedTileCount+
            " blockedEdges="+result.blockedEdgeCount
        );
    }

    private static void assertParity(
        int sx,
        int sy,
        int tx,
        int ty,
        int range
    ){
        List<int[]> expected=
            HomeCombatPathfinder.route(
                sx,
                sy,
                tx,
                ty,
                range
            );

        RouteFinder.Result actual=
            RouteFinder.find(
                RouteRequest.combatCompatibility(
                    sx,
                    sy,
                    0,
                    tx,
                    ty,
                    range
                )
            );

        if(!same(expected,actual.path))
            throw new AssertionError(
                "route parity changed start="+
                sx+","+sy+
                " target="+tx+","+ty+
                " expected="+describe(expected)+
                " actual="+describe(actual.path)
            );
    }

    private static boolean same(
        List<int[]> a,
        List<int[]> b
    ){
        if(a==null||b==null)return a==b;
        if(a.size()!=b.size())return false;

        for(int i=0;i<a.size();i++)
            if(a.get(i)[0]!=b.get(i)[0]||
               a.get(i)[1]!=b.get(i)[1])
                return false;

        return true;
    }

    private static String describe(
        List<int[]> path
    ){
        if(path==null)return "null";
        StringBuilder out=new StringBuilder("[");
        for(int i=0;i<path.size();i++){
            if(i>0)out.append(',');
            out.append(path.get(i)[0])
               .append(':')
               .append(path.get(i)[1]);
        }
        return out.append(']').toString();
    }
}
