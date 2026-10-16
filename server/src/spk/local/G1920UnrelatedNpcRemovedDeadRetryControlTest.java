package spk.local;

/** G19.2.0: removing a different WorldNpc cannot veto current dead retry. */
public final class G1920UnrelatedNpcRemovedDeadRetryControlTest {
    public static void main(String[] args)throws Exception{
        G1919DeadNpcRemovedDuringGenerationTest.run(
            G1919DeadNpcRemovedDuringGenerationTest.REMOVE_OTHER_NPC);
        System.out.println(
            "G1920_UNRELATED_NPC_REMOVED_DEAD_RETRY_CONTROL_PASS"+
            " selectedCanonicalStillCurrent=true"+
            " legitimateRetryOnce=true"+
            " noNewReward=true");
    }
    private G1920UnrelatedNpcRemovedDeadRetryControlTest(){}
}
