package spk.local;

/**
 * G19.2.6: a second NPC may be re-registered and re-killed at the same
 * tick with unchanged values without invalidating the selected
 * current dead NPC's retry.
 */
public final class G1926UnrelatedNpcSameTickLifecycleAbaControlTest {
    public static void main(String[] args)throws Exception{
        G1919DeadNpcRemovedDuringGenerationTest.run(
            G1919DeadNpcRemovedDuringGenerationTest.REDEAD_OTHER_SAME_TICK);
        System.out.println(
            "G1926_UNRELATED_NPC_SAME_TICK_LIFECYCLE_ABA_CONTROL_PASS"+
            " unrelatedDeathTickHpAndAuthorityUnchanged=true"+
            " unrelatedRegistrationIdentityReplaced=true"+
            " currentTargetRetryOnce=true"+
            " noNewHitReward=true");
    }
    private G1926UnrelatedNpcSameTickLifecycleAbaControlTest(){}
}
