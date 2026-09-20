package spk.local;

/**
 * Central per-step collision authority adapter.
 *
 * Policies are explicit so recovered compatibility behavior cannot be confused
 * with stronger cache/static collision authority.
 */
final class CollisionStepAuthority {
    enum Policy {
        /**
         * Historical R8.5 HOME ordinary movement compatibility retained only
         * for explicit regression/research comparison.
         */
        HOME_CLIENT_SUBMITTED_COMPATIBILITY,

        /** Recovered HOME object/edge collision used by interaction routing. */
        HOME_RECOVERED_STATIC,

        /** Exact-current cache/static world collision authority. */
        WORLD_STATIC
    }

    static Policy movementPolicy(MovementState movement){
        if(movement==null)
            throw new NullPointerException("movement");

        return movement.transientRegion()
            ?Policy.WORLD_STATIC
            :Policy.HOME_RECOVERED_STATIC;
    }

    static boolean canStep(
        Policy policy,
        int x0,
        int y0,
        int plane,
        int x1,
        int y1
    ){
        if(policy==null)
            throw new NullPointerException("policy");

        int dx=x1-x0;
        int dy=y1-y0;

        if(Math.abs(dx)>1||
           Math.abs(dy)>1||
           (dx==0&&dy==0))
            return false;

        switch(policy){
            case HOME_CLIENT_SUBMITTED_COMPATIBILITY:
                return true;

            case HOME_RECOVERED_STATIC:
                if(plane!=0)return false;
                return HomeCombatPathfinder.canStep(
                    x0,y0,x1,y1
                );

            case WORLD_STATIC:
                return WorldCollisionAuthority.canStep(
                    x0,y0,plane,x1,y1
                );

            default:
                throw new IllegalStateException(
                    "unsupported collision policy "+
                    policy
                );
        }
    }

    static String authority(Policy policy){
        switch(policy){
            case HOME_CLIENT_SUBMITTED_COMPATIBILITY:
                return "CLIENT_SUBMITTED_HOME_COMPATIBILITY";
            case HOME_RECOVERED_STATIC:
                return "HOME_RECOVERED_STATIC_COLLISION";
            case WORLD_STATIC:
                return "WORLD_CACHE_STATIC_COLLISION";
            default:
                throw new IllegalStateException(
                    "unsupported collision policy "+
                    policy
                );
        }
    }

    private CollisionStepAuthority(){}
}
