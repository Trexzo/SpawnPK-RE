package spk.local;

/** A foreign JVM hardlink to another account is not a global veto. */
public final class G21269ForeignJvmUnrelatedPostMoveHardlinkControlTest {
    private G21269ForeignJvmUnrelatedPostMoveHardlinkControlTest(){}
    public static void main(String[] args)throws Exception{
        if(args.length!=0)
            throw new IllegalArgumentException("G21.269 unexpected args");
        G21268ForeignJvmPostMoveHardlinkUncertainTest.run(true);
    }
}
