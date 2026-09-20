package spk.local;

import java.util.*;

public final class CacheCrossRegionRoutingAcceptanceTest {
    private static final class Boundary {
        final int sourceRegion;
        final int destinationRegion;
        final int sx,sy,tx,ty;

        Boundary(
            int sourceRegion,
            int destinationRegion,
            int sx,
            int sy,
            int tx,
            int ty
        ){
            this.sourceRegion=sourceRegion;
            this.destinationRegion=destinationRegion;
            this.sx=sx;
            this.sy=sy;
            this.tx=tx;
            this.ty=ty;
        }
    }

    public static void main(String[] args){
        Boundary boundary=findBoundary();

        if(boundary==null)
            throw new AssertionError(
                "no legal fully-decoded cache region boundary found"
            );

        if(((boundary.sx>>6)<<8|(boundary.sy>>6))==
           ((boundary.tx>>6)<<8|(boundary.ty>>6)))
            throw new AssertionError(
                "fixture did not cross a region boundary"
            );

        if(!WorldCollisionAuthority.canStep(
                boundary.sx,
                boundary.sy,
                0,
                boundary.tx,
                boundary.ty))
            throw new AssertionError(
                "discovered boundary is not traversable"
            );

        RouteFinder.Result route=
            RouteFinder.find(
                RouteRequest.worldStatic(
                    boundary.sx,
                    boundary.sy,
                    0,
                    boundary.tx,
                    boundary.ty,
                    0,
                    RouteRequest.Purpose.PLAYER_MOVEMENT
                )
            );

        if(!"WORLD_STATIC_COLLISION".equals(
                route.authority))
            throw new AssertionError(
                "route authority changed: "+
                route.authority
            );

        if(route.path==null||
           route.path.size()!=1)
            throw new AssertionError(
                "expected one legal cross-region step path="+
                describe(route.path)
            );

        int[] routed=route.path.get(0);

        if(routed[0]!=boundary.tx||
           routed[1]!=boundary.ty)
            throw new AssertionError(
                "route ended on wrong tile "+
                routed[0]+","+routed[1]
            );

        MovementState movement=
            new MovementState();

        int baseX=boundary.sx-52;
        int baseY=boundary.sy-52;

        movement.enterTransientRegion(
            boundary.sx,
            boundary.sy,
            0,
            baseX,
            baseY
        );

        String accepted=
            movement.accept(
                new MovementRequest(
                    164,
                    false,
                    new int[]{boundary.tx},
                    new int[]{boundary.ty},
                    new byte[0]
                )
            );

        if(!accepted.startsWith("ACCEPTED"))
            throw new AssertionError(
                "MovementState rejected legal cross-region step: "+
                accepted
            );

        MovementState.Tick tick=
            movement.advance();

        if(tick==null||
           movement.x()!=boundary.tx||
           movement.y()!=boundary.ty)
            throw new AssertionError(
                "MovementState did not cross region boundary"
            );

        if(!movement.transientRegion())
            throw new AssertionError(
                "cache-backed movement lost transient authority"
            );

        System.out.println(
            "CACHE_CROSS_REGION_ROUTING_ACCEPTANCE_PASS "+
            "sourceRegion="+boundary.sourceRegion+
            " destinationRegion="+boundary.destinationRegion+
            " step="+boundary.sx+","+boundary.sy+
            "->"+boundary.tx+","+boundary.ty+
            " routeAuthority="+route.authority+
            " movementAuthority=WORLD_STATIC"
        );
    }

    private static Boundary findBoundary(){
        for(int regionId=0;regionId<=65535;regionId++){
            if(!fullyDecoded(regionId))
                continue;

            int regionX=regionId>>8;
            int regionY=regionId&255;

            int east=((regionX+1)<<8)|regionY;
            if(regionX<255&&fullyDecoded(east)){
                int sourceX=regionX*64+63;
                int destinationX=sourceX+1;

                for(int localY=0;localY<64;localY++){
                    int y=regionY*64+localY;

                    if(WorldCollisionAuthority.canStep(
                            sourceX,y,0,
                            destinationX,y))
                        return new Boundary(
                            regionId,
                            east,
                            sourceX,y,
                            destinationX,y
                        );
                }
            }

            int north=(regionX<<8)|(regionY+1);
            if(regionY<255&&fullyDecoded(north)){
                int sourceY=regionY*64+63;
                int destinationY=sourceY+1;

                for(int localX=0;localX<64;localX++){
                    int x=regionX*64+localX;

                    if(WorldCollisionAuthority.canStep(
                            x,sourceY,0,
                            x,destinationY))
                        return new Boundary(
                            regionId,
                            north,
                            x,sourceY,
                            x,destinationY
                        );
                }
            }
        }

        return null;
    }

    private static boolean fullyDecoded(int regionId){
        if(!WorldCollisionAuthority.hasRegion(regionId))
            return false;

        WorldRegionAuthorityRepository.Region region=
            WorldRegionAuthorityRepository.get(regionId);

        return region!=null&&
            region.mapPresent&&
            region.landPresent&&
            region.terrainParseOk&&
            region.objectParseOk;
    }

    private static String describe(
        List<int[]> path
    ){
        if(path==null)return "null";

        StringBuilder out=
            new StringBuilder("[");

        for(int i=0;i<path.size();i++){
            if(i>0)out.append(',');
            out.append(path.get(i)[0])
               .append(':')
               .append(path.get(i)[1]);
        }

        return out.append(']').toString();
    }
}
