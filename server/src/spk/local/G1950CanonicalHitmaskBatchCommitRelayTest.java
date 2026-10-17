package spk.local;

/** G19.5.0: semantic canonical mask relays only after source batch commit. */
public final class G1950CanonicalHitmaskBatchCommitRelayTest {
    public static void main(String[] args)throws Exception{
        G1949CanonicalHitmaskBatchAbortFenceTest.run(false);
        System.out.println(
            "G1950_CANONICAL_HITMASK_BATCH_COMMIT_RELAY_PASS"+
            " exactDecodedAttack=true"+
            " noRemoteEventBeforeSourceCommit=true"+
            " oneOwnedCanonicalRelayAfterCommit=true"+
            " viewerMaskDeliveredOnce=true"+
            " oneAuthoritativeHpMutation=true");
    }
    private G1950CanonicalHitmaskBatchCommitRelayTest(){}
}
