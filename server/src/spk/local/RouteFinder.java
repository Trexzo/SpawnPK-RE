package spk.local;

import java.util.*;

/**
 * Single routing entry point.
 *
 * The first roadmap slice deliberately preserves the current HOME combat BFS
 * through an explicit compatibility policy. Later slices can migrate callers
 * to stronger collision authority without changing call-site ownership again.
 */
final class RouteFinder {
    static final class Result {
        final RouteRequest request;
        final List<int[]> path;
        final String authority;
        final int blockedTileCount;
        final int blockedEdgeCount;

        Result(
            RouteRequest request,
            List<int[]> path,
            String authority,
            int blockedTileCount,
            int blockedEdgeCount
        ){
            this.request=request;
            this.path=copy(path);
            this.authority=authority;
            this.blockedTileCount=blockedTileCount;
            this.blockedEdgeCount=blockedEdgeCount;
        }

        private static List<int[]> copy(List<int[]> path){
            if(path==null)return null;
            ArrayList<int[]> out=new ArrayList<>();
            for(int[] step:path)
                out.add(new int[]{step[0],step[1]});
            return Collections.unmodifiableList(out);
        }
    }

    static Result find(RouteRequest request){
        if(request==null)
            throw new NullPointerException("request");

        switch(request.policy){
            case HOME_COMBAT_COMPATIBILITY:
                return new Result(
                    request,
                    HomeCombatPathfinder.route(
                        request.startX,
                        request.startY,
                        request.targetX,
                        request.targetY,
                        request.stopRange
                    ),
                    "HOME_COMBAT_CLIENT_BFS_COMPATIBILITY",
                    HomeCombatPathfinder.blockedTileCount(),
                    HomeCombatPathfinder.blockedEdgeCount()
                );

            case HOME_RECOVERED_STATIC_AUTHORITY:
                return new Result(
                    request,
                    HomeCombatPathfinder.route(
                        request.startX,
                        request.startY,
                        request.targetX,
                        request.targetY,
                        request.stopRange
                    ),
                    "HOME_RECOVERED_STATIC_COLLISION",
                    HomeCombatPathfinder.blockedTileCount(),
                    HomeCombatPathfinder.blockedEdgeCount()
                );

            case WORLD_STATIC_AUTHORITY:
                return new Result(
                    request,
                    WorldPathfinder.route(
                        request.startX,
                        request.startY,
                        request.plane,
                        request.targetX,
                        request.targetY,
                        request.stopRange
                    ),
                    "WORLD_STATIC_COLLISION",
                    -1,
                    -1
                );

            default:
                throw new IllegalStateException(
                    "unsupported route policy "+
                    request.policy
                );
        }
    }

    private RouteFinder(){}
}
