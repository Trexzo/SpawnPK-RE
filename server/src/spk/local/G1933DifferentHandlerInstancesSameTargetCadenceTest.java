package spk.local;

/**
 * G19.3.3: two separate handler objects wrapping one WorldPlayer and
 * viewing one canonical NPC cannot both land at the same tick.
 */
public final class G1933DifferentHandlerInstancesSameTargetCadenceTest {
    public static void main(String[] args)throws Exception{
        G1931ConcurrentSameTickNpcAttackFenceTest.run(true,true);
        System.out.println(
            "G1933_DIFFERENT_HANDLER_INSTANCES_SAME_TARGET_CADENCE_PASS"+
            " twoRealThreadsAndTwoHandlerInstances=true"+
            " oneSharedWorldPlayer=true"+
            " sameTargetOneInitialHit=true"+
            " sharedPlayerCadenceBlocksSecond=true"+
            " laterOtherHandlerAllowed=true");
    }
    private G1933DifferentHandlerInstancesSameTargetCadenceTest(){}
}
