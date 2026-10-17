package spk.local;
public final class G21360ForcedWorldLateAccountByteDriftTest {
    private G21360ForcedWorldLateAccountByteDriftTest() {}
    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new AssertionError("unexpected arguments");
        G21360ForcedWorldAccountPreimageFixture.run(
            G21360ForcedWorldAccountPreimageFixture.Scenario.LATE_BYTES);
    }
}
