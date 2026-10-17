package spk.local;

/** G21.278: foreign process review marker on an unrelated account
 * must not revoke a healthy selected forced World SaveTicket. */
public final class G21278ForeignJvmUnrelatedWorldActiveMarkerControlTest {
    private G21278ForeignJvmUnrelatedWorldActiveMarkerControlTest(){}
    public static void main(String[] args)throws Exception{
        if(args.length!=0)
            throw new IllegalArgumentException("G21.278 unexpected args");
        G21277ForeignJvmWorldPostMoveActiveMarkerTest.run(true);
    }
}
