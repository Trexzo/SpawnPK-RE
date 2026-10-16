package spk.local;

/** G19.1.6: preserve the current, open-World dead-NPC retry callback. */
public final class G1916OpenWorldDeadNpcRetryPositiveControlTest {
    public static void main(String[] args)throws Exception{
        G1915ClosedWorldDeadNpcRetryVetoTest.run(false);
        System.out.println(
            "G1916_OPEN_WORLD_DEAD_NPC_RETRY_CONTROL_PASS"+
            " realDecodedOpcode72=true"+
            " currentSessionDeadRetryOnce=true"+
            " noHitOrRewardAdded=true"+
            " originalServerRewardPolicyUnknown=true");
    }

    private G1916OpenWorldDeadNpcRetryPositiveControlTest(){}
}
