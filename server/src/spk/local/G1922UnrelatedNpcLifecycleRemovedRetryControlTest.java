package spk.local;

/** G19.2.2: unrelated lifecycle removal keeps valid dead-target retry. */
public final class G1922UnrelatedNpcLifecycleRemovedRetryControlTest {
    public static void main(String[] args)throws Exception{
        G1919DeadNpcRemovedDuringGenerationTest.run(
            G1919DeadNpcRemovedDuringGenerationTest.REMOVE_OTHER_LIFECYCLE);
        System.out.println(
            "G1922_UNRELATED_NPC_LIFECYCLE_REMOVED_RETRY_CONTROL_PASS"+
            " selectedLifecycleStillDead=true"+
            " legitimateRetryOnce=true"+
            " noExtraRewards=true");
    }
    private G1922UnrelatedNpcLifecycleRemovedRetryControlTest(){}
}
