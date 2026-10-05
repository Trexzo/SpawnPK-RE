package spk.local;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public final class LocalAuxVersionsTest {
    private static final byte[] FALLBACK =
        (
            "cache_version = 67.0\r\n"+
            "sprite_version = 72.0\r\n"+
            "config_version = 110.0\r\n"
        ).getBytes(
            java.nio.charset.StandardCharsets.US_ASCII
        );

    public static void main(String[] args){
        assertFallback(
            LocalAuxVersions.readUserHome(
                FALLBACK,
                ()->null
            ),
            "missing user.home"
        );

        assertFallback(
            LocalAuxVersions.readUserHome(
                FALLBACK,
                ()->{
                    throw new SecurityException(
                        "fixture-user-home-security"
                    );
                }
            ),
            "user.home security failure"
        );

        assertFallback(
            LocalAuxVersions.readUserHome(
                FALLBACK,
                ()->
                    "bad"+
                    (char)0+
                    "home"
            ),
            "invalid user.home path"
        );

        RuntimeException homeRuntime=
            new IllegalStateException(
                "fixture-user-home-runtime"
            );
        Throwable homeRuntimeObserved=null;

        try{
            LocalAuxVersions.readUserHome(
                FALLBACK,
                ()->{ throw homeRuntime; }
            );
        }catch(Throwable failure){
            homeRuntimeObserved=failure;
        }

        if(homeRuntimeObserved!=homeRuntime)
            throw new AssertionError(
                "unrelated user.home RuntimeException was swallowed",
                homeRuntimeObserved
            );

        assertFallback(
            LocalAuxVersions.read(
                new ByteArrayInputStream(
                    new byte[0]
                ),
                FALLBACK
            ),
            "empty"
        );

        byte[] one=
            new byte[]{42};

        assertExact(
            one,
            LocalAuxVersions.read(
                new ByteArrayInputStream(
                    one
                ),
                FALLBACK
            ),
            "one byte"
        );

        byte[] maxAccepted=
            pattern(
                LocalAuxVersions
                    .MAX_ACCEPTED_LENGTH-1
            );

        assertExact(
            maxAccepted,
            LocalAuxVersions.read(
                new ByteArrayInputStream(
                    maxAccepted
                ),
                FALLBACK
            ),
            "16,383 bytes"
        );

        byte[] rejectedAtLimit=
            pattern(
                LocalAuxVersions
                    .MAX_ACCEPTED_LENGTH
            );

        assertFallback(
            LocalAuxVersions.read(
                new ByteArrayInputStream(
                    rejectedAtLimit
                ),
                FALLBACK
            ),
            "16,384 bytes"
        );

        CountingInputStream large=
            new CountingInputStream(
                10_000_000L
            );

        assertFallback(
            LocalAuxVersions.read(
                large,
                FALLBACK
            ),
            "large source"
        );

        if(large.consumed!=
                LocalAuxVersions
                    .MAX_ACCEPTED_LENGTH)
            throw new AssertionError(
                "large source consumed beyond decision bound: "+
                large.consumed
            );

        if(large.maxRequested>
                LocalAuxVersions
                    .MAX_ACCEPTED_LENGTH)
            throw new AssertionError(
                "large source requested oversized read: "+
                large.maxRequested
            );

        assertFallback(
            LocalAuxVersions.read(
                new FailingInputStream(),
                FALLBACK
            ),
            "I/O failure"
        );

        RuntimeException readRuntime=
            new IllegalStateException(
                "fixture-read-runtime"
            );
        Throwable readRuntimeObserved=null;

        try{
            LocalAuxVersions.read(
                new RuntimeFailureInputStream(
                    readRuntime
                ),
                FALLBACK
            );
        }catch(Throwable failure){
            readRuntimeObserved=failure;
        }

        if(readRuntimeObserved!=readRuntime)
            throw new AssertionError(
                "unrelated bounded-read RuntimeException was swallowed",
                readRuntimeObserved
            );

        Error expected=
            new AssertionError(
                "fixture-fatal-error"
            );
        Throwable observed=null;

        try{
            LocalAuxVersions.read(
                new ErrorInputStream(
                    expected
                ),
                FALLBACK
            );
        }catch(Throwable failure){
            observed=failure;
        }

        if(observed!=expected)
            throw new AssertionError(
                "unrelated Error was normalized into fallback",
                observed
            );

        System.out.println(
            "LOCAL_AUX_VERSIONS_PASS "+
            "missingHomeFallback=true "+
            "homeSecurityFallback=true "+
            "invalidHomeFallback=true "+
            "unrelatedRuntimeUnswept=true "+
            "emptyFallback=true "+
            "oneByteAccepted=true "+
            "maxAccepted=16383 "+
            "limitRejected=16384 "+
            "boundedConsumption=16384 "+
            "ioFailureFallback=true "+
            "fatalErrorNotSwallowed=true "+
            "fallbackUnchanged=true"
        );
    }

    private static byte[] pattern(
        int length
    ){
        byte[] data=
            new byte[length];

        for(int i=0;i<data.length;i++)
            data[i]=
                (byte)(
                    (i*31)+7
                );

        return data;
    }

    private static void assertExact(
        byte[] expected,
        byte[] actual,
        String label
    ){
        if(!Arrays.equals(
                expected,
                actual))
            throw new AssertionError(
                label+
                " did not preserve exact bytes"
            );
    }

    private static void assertFallback(
        byte[] actual,
        String label
    ){
        if(!Arrays.equals(
                FALLBACK,
                actual))
            throw new AssertionError(
                label+
                " did not use the established fallback"
            );

        if(actual==FALLBACK)
            throw new AssertionError(
                label+
                " returned mutable fallback authority directly"
            );
    }

    private static final class
        CountingInputStream
        extends InputStream {
        private final long length;
        long consumed;
        int maxRequested;

        CountingInputStream(
            long length
        ){
            this.length=length;
        }

        @Override public int read(
            byte[] buffer,
            int offset,
            int requested
        ){
            if(consumed>=length)
                return -1;

            maxRequested=
                Math.max(
                    maxRequested,
                    requested
                );

            int count=
                (int)Math.min(
                    requested,
                    length-consumed
                );

            Arrays.fill(
                buffer,
                offset,
                offset+count,
                (byte)0x5a
            );
            consumed+=count;
            return count;
        }

        @Override public int read(){
            if(consumed>=length)
                return -1;

            consumed++;
            return 0x5a;
        }
    }

    private static final class
        FailingInputStream
        extends InputStream {
        @Override public int read(
            byte[] buffer,
            int offset,
            int length
        )throws IOException{
            throw new IOException(
                "fixture-read-failure"
            );
        }

        @Override public int read()
            throws IOException{
            throw new IOException(
                "fixture-read-failure"
            );
        }
    }

    private static final class
        RuntimeFailureInputStream
        extends InputStream {
        private final RuntimeException failure;

        RuntimeFailureInputStream(
            RuntimeException failure
        ){
            this.failure=failure;
        }

        @Override public int read(
            byte[] buffer,
            int offset,
            int length
        ){
            throw failure;
        }

        @Override public int read(){
            throw failure;
        }
    }

    private static final class
        ErrorInputStream
        extends InputStream {
        private final Error failure;

        ErrorInputStream(
            Error failure
        ){
            this.failure=failure;
        }

        @Override public int read(
            byte[] buffer,
            int offset,
            int length
        ){
            throw failure;
        }

        @Override public int read(){
            throw failure;
        }
    }

    private LocalAuxVersionsTest(){}
}
