package spk.local;

/**
 * G19.1.0 positive control: the exact current WorldPlayer/generation
 * retains the pre-existing dead-NPC retry hook, even after same-name
 * session replacement. No dead click grants an independent hit or loot.
 */
public final class G1910CurrentDeadNpcRetryPreservedTest {
    public static void main(String[] args)throws Exception{
        G1909StaleDeadNpcRetrySessionFenceTest.run(false);
        System.out.println(
            "G1910_CURRENT_DEAD_NPC_RETRY_PRESERVED_PASS"+
            " exactReplacementSessionAdmitted=true"+
            " currentDeadRetryOnce=true"+
            " deadClickNoHitOrReward=true"+
            " priorOriginalServerPolicyUnknown=true");
    }

    private G1910CurrentDeadNpcRetryPreservedTest(){}
}
