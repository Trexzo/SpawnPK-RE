package spk.local;

import java.util.*;

/**
 * Generic server-owned interaction approach coordinator.
 *
 * This class separates interaction reach from route finding and movement queue
 * application. Packet/UI presentation remains with the calling handler.
 */
final class InteractionApproachResolver {
    enum Status {
        ALREADY_REACHABLE,
        ROUTE_QUEUED,
        NO_ROUTE,
        MOVEMENT_REJECTED
    }

    static final class Result {
        final Status status;
        final String authority;
        final int routeSteps;
        final int destinationX;
        final int destinationY;
        final String movementResult;

        Result(
            Status status,
            String authority,
            int routeSteps,
            int destinationX,
            int destinationY,
            String movementResult
        ){
            this.status=status;
            this.authority=authority;
            this.routeSteps=routeSteps;
            this.destinationX=destinationX;
            this.destinationY=destinationY;
            this.movementResult=movementResult;
        }

        boolean queued(){
            return status==Status.ROUTE_QUEUED;
        }

        @Override public String toString(){
            return "InteractionApproach{status="+status+
                ",authority="+authority+
                ",steps="+routeSteps+
                ",dest="+destinationX+","+destinationY+
                ",movement="+movementResult+"}";
        }
    }

    private final MovementState movement;

    InteractionApproachResolver(MovementState movement){
        this.movement=Objects.requireNonNull(
            movement,
            "movement"
        );
    }

    boolean adjacent(int targetX,int targetY){
        return Math.max(
            Math.abs(targetX-movement.x()),
            Math.abs(targetY-movement.y())
        )<=1;
    }

    Result queueAdjacent(int targetX,int targetY){
        if(adjacent(targetX,targetY))
            return new Result(
                Status.ALREADY_REACHABLE,
                "CURRENT_POSITION",
                0,
                movement.x(),
                movement.y(),
                "ALREADY_REACHABLE"
            );

        RouteRequest request=
            movement.transientRegion()
                ?RouteRequest.worldStatic(
                    movement.x(),
                    movement.y(),
                    movement.plane(),
                    targetX,
                    targetY,
                    1,
                    RouteRequest.Purpose.INTERACTION_APPROACH
                )
                :RouteRequest.interactionHomeRecovered(
                    movement.x(),
                    movement.y(),
                    movement.plane(),
                    targetX,
                    targetY,
                    1
                );

        RouteFinder.Result route=
            RouteFinder.find(request);

        if(route.path==null||route.path.isEmpty())
            return new Result(
                Status.NO_ROUTE,
                route.authority,
                0,
                movement.x(),
                movement.y(),
                "NO_ROUTE"
            );

        int[] xs=new int[route.path.size()];
        int[] ys=new int[route.path.size()];

        for(int i=0;i<route.path.size();i++){
            xs[i]=route.path.get(i)[0];
            ys[i]=route.path.get(i)[1];
        }

        int destinationX=xs[xs.length-1];
        int destinationY=ys[ys.length-1];

        String accepted=
            movement.accept(
                new MovementRequest(
                    164,
                    false,
                    xs,
                    ys,
                    new byte[0]
                )
            );

        if(!accepted.startsWith("ACCEPTED"))
            return new Result(
                Status.MOVEMENT_REJECTED,
                route.authority,
                route.path.size(),
                destinationX,
                destinationY,
                accepted
            );

        return new Result(
            Status.ROUTE_QUEUED,
            route.authority,
            route.path.size(),
            destinationX,
            destinationY,
            accepted
        );
    }
}
