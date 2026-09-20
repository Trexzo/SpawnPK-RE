package spk.local;

public final class CollisionStepAuthorityCompatibilityTest {
    public static void main(String[] args){
        if(!HomeCombatPathfinder.blockedTile(
                3084,
                3495))
            throw new AssertionError(
                "known HOME blocker missing"
            );

        if(!CollisionStepAuthority.canStep(
                CollisionStepAuthority.Policy.HOME_CLIENT_SUBMITTED_COMPATIBILITY,
                3083,3495,0,
                3084,3495))
            throw new AssertionError(
                "historical HOME compatibility policy changed"
            );

        if(CollisionStepAuthority.canStep(
                CollisionStepAuthority.Policy.HOME_RECOVERED_STATIC,
                3083,3495,0,
                3084,3495))
            throw new AssertionError(
                "recovered HOME collision failed to block known altar tile"
            );

        MovementState movement=
            new MovementState();

        if(CollisionStepAuthority.movementPolicy(movement)!=
                CollisionStepAuthority.Policy.HOME_RECOVERED_STATIC)
            throw new AssertionError(
                "HOME movement is not using recovered static authority"
            );

        int[][] samples={
            {3087,3495,0,3088,3495},
            {3087,3495,0,3087,3496},
            {3200,3200,0,3201,3200},
            {3200,3200,0,3201,3201}
        };

        for(int[] s:samples){
            boolean expected=
                WorldCollisionAuthority.canStep(
                    s[0],s[1],s[2],s[3],s[4]
                );
            boolean actual=
                CollisionStepAuthority.canStep(
                    CollisionStepAuthority.Policy.WORLD_STATIC,
                    s[0],s[1],s[2],s[3],s[4]
                );

            if(expected!=actual)
                throw new AssertionError(
                    "WORLD_STATIC parity changed at "+
                    s[0]+","+s[1]+"->"+
                    s[3]+","+s[4]+
                    " expected="+expected+
                    " actual="+actual
                );
        }

        System.out.println(
            "COLLISION_STEP_AUTHORITY_COMPATIBILITY_PASS "+
            "legacyPolicyRetained=true "+
            "homeMovementPolicy=HOME_RECOVERED_STATIC "+
            "knownBlocker=true "+
            "worldStaticParity=true"
        );
    }
}
