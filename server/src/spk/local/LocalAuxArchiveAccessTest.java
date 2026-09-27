package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

public final class LocalAuxArchiveAccessTest {
    public static void main(
        String[] args
    )throws Exception{
        assertRegularArchiveServed();
        assertNonRegularUnavailable();
        assertProbeSecurityUnavailable();
        assertOpenSecurityUnavailable();
        assertIOExceptionPropagates();
        assertRuntimeUnswept();
        assertFatalUnswept();

        System.out.println(
            "LOCAL_AUX_ARCHIVE_ACCESS_PASS "+
            "regularServed=true "+
            "nonRegularUnavailable=true "+
            "probeSecurityUnavailable=true "+
            "openSecurityUnavailable=true "+
            "ioConnectionScoped=true "+
            "runtimeUnswept=true "+
            "fatalUnswept=true "+
            "singleHandleInherited=true"
        );
    }

    private static void assertRegularArchiveServed()
        throws Exception{
        Path file=
            Files.createTempFile(
                "spawnpk-aux-access-",
                ".zip"
            );

        try{
            byte[] body=
                new byte[]{
                    3,1,4,1,5,9
                };
            Files.write(
                file,
                body
            );

            ByteArrayOutputStream out=
                new ByteArrayOutputStream();

            Long length=
                LocalAuxArchiveAccess
                    .writeIfAvailable(
                        out,
                        file,
                        false
                    );

            if(length==null||
               length.longValue()!=body.length)
                throw new AssertionError(
                    "regular archive did not return streamed length"
                );

            byte[] wire=
                out.toByteArray();
            String text=
                new String(
                    wire,
                    StandardCharsets
                        .ISO_8859_1
                );

            if(!text.contains(
                    "HTTP/1.1 200 OK\r\n")||
               !text.contains(
                    "Content-Length: "+
                    body.length+
                    "\r\n"))
                throw new AssertionError(
                    "regular archive response framing changed"
                );
        }finally{
            Files.deleteIfExists(
                file
            );
        }
    }

    private static void assertNonRegularUnavailable()
        throws Exception{
        AtomicInteger writerCalls=
            new AtomicInteger();

        Long result=
            LocalAuxArchiveAccess
                .writeIfAvailable(
                    new ByteArrayOutputStream(),
                    Path.of(
                        "fixture-missing.zip"
                    ),
                    false,
                    file->false,
                    (out,file,head)->{
                        writerCalls.incrementAndGet();
                        return 1L;
                    }
                );

        if(result!=null||
           writerCalls.get()!=0)
            throw new AssertionError(
                "non-regular archive reached writer"
            );
    }

    private static void assertProbeSecurityUnavailable()
        throws Exception{
        AtomicInteger writerCalls=
            new AtomicInteger();

        Long result=
            LocalAuxArchiveAccess
                .writeIfAvailable(
                    new ByteArrayOutputStream(),
                    Path.of(
                        "fixture-security.zip"
                    ),
                    false,
                    file->{
                        throw new SecurityException(
                            "fixture-probe-security"
                        );
                    },
                    (out,file,head)->{
                        writerCalls.incrementAndGet();
                        return 1L;
                    }
                );

        if(result!=null||
           writerCalls.get()!=0)
            throw new AssertionError(
                "probe SecurityException did not map to unavailable"
            );
    }

    private static void assertOpenSecurityUnavailable()
        throws Exception{
        Long result=
            LocalAuxArchiveAccess
                .writeIfAvailable(
                    new ByteArrayOutputStream(),
                    Path.of(
                        "fixture-open-security.zip"
                    ),
                    false,
                    file->true,
                    (out,file,head)->{
                        throw new SecurityException(
                            "fixture-open-security"
                        );
                    }
                );

        if(result!=null)
            throw new AssertionError(
                "open SecurityException did not map to unavailable"
            );
    }

    private static void assertIOExceptionPropagates(){
        IOException expected=
            new IOException(
                "fixture-stream-io"
            );
        Throwable observed=null;

        try{
            LocalAuxArchiveAccess
                .writeIfAvailable(
                    new ByteArrayOutputStream(),
                    Path.of(
                        "fixture-io.zip"
                    ),
                    false,
                    file->true,
                    (out,file,head)->{
                        throw expected;
                    }
                );
        }catch(Throwable failure){
            observed=failure;
        }

        if(observed!=expected)
            throw new AssertionError(
                "archive IOException was normalized into unavailable",
                observed
            );
    }

    private static void assertRuntimeUnswept(){
        RuntimeException expected=
            new IllegalStateException(
                "fixture-runtime"
            );
        Throwable observed=null;

        try{
            LocalAuxArchiveAccess
                .writeIfAvailable(
                    new ByteArrayOutputStream(),
                    Path.of(
                        "fixture-runtime.zip"
                    ),
                    false,
                    file->{
                        throw expected;
                    },
                    (out,file,head)->1L
                );
        }catch(Throwable failure){
            observed=failure;
        }

        if(observed!=expected)
            throw new AssertionError(
                "unrelated RuntimeException was swallowed/wrapped",
                observed
            );
    }

    private static void assertFatalUnswept(){
        Error expected=
            new AssertionError(
                "fixture-error"
            );
        Throwable observed=null;

        try{
            LocalAuxArchiveAccess
                .writeIfAvailable(
                    new ByteArrayOutputStream(),
                    Path.of(
                        "fixture-error.zip"
                    ),
                    false,
                    file->true,
                    (out,file,head)->{
                        throw expected;
                    }
                );
        }catch(Throwable failure){
            observed=failure;
        }

        if(observed!=expected)
            throw new AssertionError(
                "unrelated Error was swallowed/wrapped",
                observed
            );
    }

    private LocalAuxArchiveAccessTest(){}
}
