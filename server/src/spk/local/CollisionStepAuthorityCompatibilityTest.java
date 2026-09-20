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
                "HOME compatibility policy unexpectedly tightened"
            );

        if(CollisionStepAuthority.canStep(
                CollisionStepAuthority.Policy.HOME_RECOVERED_STATIC,
                3083,3495,0,
                3084,3495))
            throw new AssertionError(
                "recovered HOME collision failed to block known altar tile"
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

        MovementState movement=
            new MovementState();

        MovementRequest legacyHomePath=
            new MovementRequest(
                164,
                false,
                new int[]{3083},
                new int[]{3495},
                new byte[0]
            );

        String accepted=
            movement.accept(legacyHomePath);

        if(!accepted.startsWith("ACCEPTED"))
            throw new AssertionError(
                "HOME client-submitted compatibility changed: "+
                accepted
            );

        if(movement.queued()!=4)
            throw new AssertionError(
                "HOME compatibility queue changed expected=4 actual="+
                movement.queued()
            );

        if(CollisionStepAuthority.movementPolicy(movement)!=
                CollisionStepAuthority.Policy.HOME_CLIENT_SUBMITTED_COMPATIBILITY)
            throw new AssertionError(
                "HOME movement policy changed"
            );

        System.out.println(
            "COLLISION_STEP_AUTHORITY_COMPATIBILITY_PASS "+
            "homeCompatibilityPermissive=true "+
            "homeRecoveredBlocker=true "+
            "worldStaticParity=true "+
            "queued="+movement.queued()
        );
    }
}
