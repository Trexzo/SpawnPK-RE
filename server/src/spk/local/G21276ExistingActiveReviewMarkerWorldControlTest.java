package spk.local;

/**
 * G21.276: previously retained valid ACTIVE intent stays negative;
 * a healthy World save need not retire or rewrite that marker.
 */
public final class G21276ExistingActiveReviewMarkerWorldControlTest {
    private G21276ExistingActiveReviewMarkerWorldControlTest(){}
    public static void main(String[] args)throws Exception{
        if(args.length!=0)
            throw new IllegalArgumentException("G21.276 unexpected args");
        G21275ActiveReviewMarkerPostMoveByteDriftTest.run(false);
    }
}
