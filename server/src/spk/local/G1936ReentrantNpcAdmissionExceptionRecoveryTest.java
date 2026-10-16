package spk.local;

/**
 * G19.3.6: an exception during pre-resolution must release the
 * player-owned reentrant admission flag without advancing cooldown.
 */
public final class G1936ReentrantNpcAdmissionExceptionRecoveryTest {
    public static void main(String[] args)throws Exception{
        G1935ReentrantNpcAttackFenceTest.run(true);
        System.out.println(
            "G1936_REENTRANT_NPC_ADMISSION_EXCEPTION_RECOVERY_PASS"+
            " injectedFailureBeforeDamage=true"+
            " noHitOrCooldownOnFailure=true"+
            " sameTickOtherHandlerRecovers=true"+
            " noDuplicateRewards=true");
    }

    private G1936ReentrantNpcAdmissionExceptionRecoveryTest(){}
}
