package spk.local;

/**
 * Typed server-side routing intent.
 *
 * Policy is explicit so early roadmap slices can centralize routing ownership
 * without silently changing the recovered R8.5 traversal semantics.
 */
final class RouteRequest {
    enum Purpose {
        PLAYER_MOVEMENT,
        INTERACTION_APPROACH,
        COMBAT_APPROACH,
        FOLLOWER
    }

    enum Policy {
        HOME_COMBAT_COMPATIBILITY,
        WORLD_STATIC_AUTHORITY
    }

    final int startX,startY,plane;
    final int targetX,targetY;
    final int stopRange;
    final Purpose purpose;
    final Policy policy;

    RouteRequest(
        int startX,
        int startY,
        int plane,
        int targetX,
        int targetY,
        int stopRange,
        Purpose purpose,
        Policy policy
    ){
        if(plane<0||plane>3)
            throw new IllegalArgumentException("plane="+plane);
        if(stopRange<0)
            throw new IllegalArgumentException("stopRange="+stopRange);
        if(purpose==null)
            throw new NullPointerException("purpose");
        if(policy==null)
            throw new NullPointerException("policy");

        this.startX=startX;
        this.startY=startY;
        this.plane=plane;
        this.targetX=targetX;
        this.targetY=targetY;
        this.stopRange=stopRange;
        this.purpose=purpose;
        this.policy=policy;
    }

    static RouteRequest combatCompatibility(
        int startX,
        int startY,
        int plane,
        int targetX,
        int targetY,
        int stopRange
    ){
        return new RouteRequest(
            startX,
            startY,
            plane,
            targetX,
            targetY,
            stopRange,
            Purpose.COMBAT_APPROACH,
            Policy.HOME_COMBAT_COMPATIBILITY
        );
    }

    static RouteRequest worldStatic(
        int startX,
        int startY,
        int plane,
        int targetX,
        int targetY,
        int stopRange,
        Purpose purpose
    ){
        return new RouteRequest(
            startX,
            startY,
            plane,
            targetX,
            targetY,
            stopRange,
            purpose,
            Policy.WORLD_STATIC_AUTHORITY
        );
    }
}
