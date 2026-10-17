package spk.local;
public final class G21361ForcedWorldLateAccountInodeSwapTest {
    private G21361ForcedWorldLateAccountInodeSwapTest() {}
    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new AssertionError("unexpected arguments");
        G21360ForcedWorldAccountPreimageFixture.run(
            G21360ForcedWorldAccountPreimageFixture.Scenario.LATE_INODE);
    }
}
