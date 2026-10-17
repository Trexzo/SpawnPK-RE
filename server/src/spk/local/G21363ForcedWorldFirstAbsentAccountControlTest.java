package spk.local;
public final class G21363ForcedWorldFirstAbsentAccountControlTest {
    private G21363ForcedWorldFirstAbsentAccountControlTest() {}
    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new AssertionError("unexpected arguments");
        G21360ForcedWorldAccountPreimageFixture.run(
            G21360ForcedWorldAccountPreimageFixture.Scenario.FIRST_ABSENT);
    }
}
