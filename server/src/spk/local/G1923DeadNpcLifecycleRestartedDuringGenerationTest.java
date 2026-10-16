package spk.local;

/**
 * G19.2.3: old dead-lifecycle observation must not authorize retry
 * after the same canonical WorldNpc is unregistered and registered
 * anew with an ALIVE lifecycle during generation sampling.
 */
public final class G1923DeadNpcLifecycleRestartedDuringGenerationTest {
    public static void main(String[] args)throws Exception{
        G1919DeadNpcRemovedDuringGenerationTest.run(
            G1919DeadNpcRemovedDuringGenerationTest.RESTART_SELECTED_LIFECYCLE);
        System.out.println(
            "G1923_DEAD_NPC_LIFECYCLE_RESTARTED_DURING_GENERATION_PASS"+
            " targetCanonicalIdentityUnchanged=true"+
            " newLifecycleAlive=true"+
            " staleDeathRetryVeto=true"+
            " noTerminalReward=true"+
            " originalServerPolicyUnknown=true");
    }
    private G1923DeadNpcLifecycleRestartedDuringGenerationTest(){}
}
