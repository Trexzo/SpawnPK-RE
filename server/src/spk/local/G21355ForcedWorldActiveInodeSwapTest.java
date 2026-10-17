package spk.local;

public final class G21355ForcedWorldActiveInodeSwapTest {
    private G21355ForcedWorldActiveInodeSwapTest() {}
    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new AssertionError("unexpected args");
        G21355ForcedWorldActiveContinuityFixture.run(
            G21355ForcedWorldActiveContinuityFixture.Scenario.SAME_BYTES_NEW_INODE);
    }
}
