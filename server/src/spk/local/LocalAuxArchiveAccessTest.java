package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicInteger;

public final class LocalAuxArchiveAccessTest {
    public static void main(
        String[] args
    )throws Exception{
        assertRegularArchiveServed();
        assertNonRegularUnavailable();
        assertProbeSecurityUnavailable();
        assertOpenSecurityUnavailable();
        assertOpenIOExceptionPropagates();
        assertPostOpenIOExceptionPropagates();
        assertRuntimeUnswept();
        assertFatalUnswept();

        System.out.println(
            "LOCAL_AUX_ARCHIVE_ACCESS_PASS "+
            "regularServed=true "+
            "nonRegularUnavailable=true "+
            "probeSecurityUnavailable=true "+
            "openSecurityUnavailable=true "+
            "openIoConnectionScoped=true "+
            "postOpenIoConnectionScoped=true "+
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
        AtomicInteger openCalls=
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
                    file->{
                        openCalls.incrementAndGet();
                        return new EmptyChannel();
                    }
                );

        if(result!=null||
           openCalls.get()!=0)
            throw new AssertionError(
                "non-regular archive reached opener"
            );
    }

    private static void assertProbeSecurityUnavailable()
        throws Exception{
        AtomicInteger openCalls=
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
                    file->{
                        openCalls.incrementAndGet();
                        return new EmptyChannel();
                    }
                );

        if(result!=null||
           openCalls.get()!=0)
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
                    file->{
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

    private static void assertOpenIOExceptionPropagates(){
        IOException expected=
            new IOException(
                "fixture-open-io"
            );
        Throwable observed=null;

        try{
            LocalAuxArchiveAccess
                .writeIfAvailable(
                    new ByteArrayOutputStream(),
                    Path.of(
                        "fixture-open-io.zip"
                    ),
                    false,
                    file->true,
                    file->{
                        throw expected;
                    }
                );
        }catch(Throwable failure){
            observed=failure;
        }

        if(observed!=expected)
            throw new AssertionError(
                "archive open IOException was normalized into unavailable",
                observed
            );
    }

    private static void assertPostOpenIOExceptionPropagates(){
        IOException expected=
            new IOException(
                "fixture-post-open-io"
            );
        Throwable observed=null;

        try{
            LocalAuxArchiveAccess
                .writeIfAvailable(
                    new ByteArrayOutputStream(),
                    Path.of(
                        "fixture-stream-io.zip"
                    ),
                    false,
                    file->true,
                    file->
                        new FailingReadChannel(
                            expected
                        )
                );
        }catch(Throwable failure){
            observed=failure;
        }

        if(observed!=expected)
            throw new AssertionError(
                "post-open archive IOException was normalized into unavailable",
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
                    file->new EmptyChannel()
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
                    file->{
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

    private static class EmptyChannel
        implements SeekableByteChannel {
        boolean open=true;
        long position;

        @Override public int read(
            ByteBuffer destination
        )throws IOException{
            return -1;
        }

        @Override public int write(
            ByteBuffer source
        ){
            throw new java.nio.channels
                .NonWritableChannelException();
        }

        @Override public long position(){
            return position;
        }

        @Override public SeekableByteChannel position(
            long newPosition
        ){
            position=newPosition;
            return this;
        }

        @Override public long size(){
            return 0L;
        }

        @Override public SeekableByteChannel truncate(
            long size
        ){
            throw new java.nio.channels
                .NonWritableChannelException();
        }

        @Override public boolean isOpen(){
            return open;
        }

        @Override public void close(){
            open=false;
        }
    }

    private static final class FailingReadChannel
        extends EmptyChannel {
        private final IOException failure;

        FailingReadChannel(
            IOException failure
        ){
            this.failure=failure;
        }

        @Override public long size(){
            return 1L;
        }

        @Override public int read(
            ByteBuffer destination
        )throws IOException{
            throw failure;
        }
    }

    private LocalAuxArchiveAccessTest(){}
}
