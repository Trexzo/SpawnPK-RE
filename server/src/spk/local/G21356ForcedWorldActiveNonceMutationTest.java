package spk.local;

public final class G21356ForcedWorldActiveNonceMutationTest {
    private G21356ForcedWorldActiveNonceMutationTest() {}
    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new AssertionError("unexpected args");
        G21355ForcedWorldActiveContinuityFixture.run(
            G21355ForcedWorldActiveContinuityFixture.Scenario.SAME_INODE_CANONICAL_NONCE_DRIFT);
    }
}
