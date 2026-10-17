package spk.local;

/**
 * G19.2.5: a selected NPC can be killed twice at the same tick after
 * lifecycle unregister/re-register, leaving deathTick, max/current HP,
 * source authority, canonical id, scene and definition unchanged.
 * Its original terminal retry must nevertheless be rejected.
 */
public final class G1925DeadNpcSameTickLifecycleAbaRejectedTest {
    public static void main(String[] args)throws Exception{
        G1919DeadNpcRemovedDuringGenerationTest.run(
            G1919DeadNpcRemovedDuringGenerationTest.REDEAD_SELECTED_SAME_TICK);
        System.out.println(
            "G1925_DEAD_NPC_SAME_TICK_LIFECYCLE_ABA_REJECTED_PASS"+
            " identicalDeathTickHpAndAuthority=true"+
            " exactCanonicalAndSceneCurrent=true"+
            " newRegistrationIdentityDetected=true"+
            " originalRetryCallbackVetoed=true"+
            " originalServerPolicyUnknown=true");
    }
    private G1925DeadNpcSameTickLifecycleAbaRejectedTest(){}
}
