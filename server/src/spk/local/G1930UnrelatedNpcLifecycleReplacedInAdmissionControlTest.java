package spk.local;

/**
 * G19.3.0: unrelated lifecycle replace after exact World/player/target
 * admission does not invalidate the selected target's HP mutation.
 */
public final class G1930UnrelatedNpcLifecycleReplacedInAdmissionControlTest {
    public static void main(String[] args)throws Exception{
        G1929NpcLifecycleReplacedInOwnedDamageAdmissionTest.run(false);
        System.out.println(
            "G1930_UNRELATED_NPC_LIFECYCLE_REPLACED_IN_ADMISSION_CONTROL_PASS"+
            " unrelatedRegistrationReplacedInLastSeam=true"+
            " selectedRegistrationUnchanged=true"+
            " currentTargetHitTen=true"+
            " noUnexpectedReward=true");
    }

    private G1930UnrelatedNpcLifecycleReplacedInAdmissionControlTest(){}
}
