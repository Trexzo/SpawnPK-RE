package spk.local;

/**
 * G19.3.4: distinct wrappers for the same WorldPlayer still share
 * cooldown when their current canonical NPC targets are different.
 */
public final class G1934DifferentHandlerInstancesDifferentTargetCadenceTest {
    public static void main(String[] args)throws Exception{
        G1931ConcurrentSameTickNpcAttackFenceTest.run(false,true);
        System.out.println(
            "G1934_DIFFERENT_HANDLER_INSTANCES_DIFFERENT_TARGET_CADENCE_PASS"+
            " twoRealThreadsAndTwoHandlerInstances=true"+
            " sharedWorldPlayerAdmissionMonitor=true"+
            " unrelatedTargetUntouchedDuringCadence=true"+
            " postCooldownOtherTargetHit=true"+
            " noExtraReward=true");
    }
    private G1934DifferentHandlerInstancesDifferentTargetCadenceTest(){}
}
