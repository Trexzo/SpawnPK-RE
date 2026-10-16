package spk.local;

/**
 * G19.2.4: restarting an unrelated NPC lifecycle does not invalidate
 * the unchanged selected dead target's current retry eligibility.
 */
public final class G1924UnrelatedNpcLifecycleRestartedRetryControlTest {
    public static void main(String[] args)throws Exception{
        G1919DeadNpcRemovedDuringGenerationTest.run(
            G1919DeadNpcRemovedDuringGenerationTest.RESTART_OTHER_LIFECYCLE);
        System.out.println(
            "G1924_UNRELATED_NPC_LIFECYCLE_RESTARTED_RETRY_CONTROL_PASS"+
            " selectedDeadLifecycleUnchanged=true"+
            " validRetryOnce=true"+
            " noNewReward=true"+
            " originalServerPolicyUnknown=true");
    }
    private G1924UnrelatedNpcLifecycleRestartedRetryControlTest(){}
}
