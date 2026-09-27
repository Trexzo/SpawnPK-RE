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
        assertBodyIOExceptionCloseIOException();
        assertBodyIOExceptionCloseRuntime();
        assertBodyIOExceptionCloseError();
        assertCleanCloseIOException();
        assertCleanCloseRuntime();
        assertCleanCloseError();
        assertResponseRuntimeKeepsPrimary();
        assertResponseErrorKeepsPrimary();
        assertSameObjectCleanupDoesNotSelfSuppress();
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
            "bodyIoCloseIoPrimary=true "+
            "bodyIoCloseRuntimePrimary=true "+
            "bodyIoCloseErrorPrimary=true "+
            "cleanCloseIo=true "+
            "cleanCloseRuntime=true "+
            "cleanCloseError=true "+
            "responseRuntimePrimary=true "+
            "responseErrorPrimary=true "+
            "sameObjectSelfSuppressionGuard=true "+
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

    private static void assertBodyIOExceptionCloseIOException(){
        IOException body=
            new IOException(
                "fixture-body-io-close-io"
            );
        IOException close=
            new IOException(
                "fixture-close-io-after-body-io"
            );
        Throwable observed=
            invokeArchive(
                new FailingReadAndCloseChannel(
                    body,
                    close
                )
            );

        assertPrimaryWithSuppressed(
            observed,
            body,
            close,
            "body IOException + close IOException"
        );
    }

    private static void assertBodyIOExceptionCloseRuntime(){
        IOException body=
            new IOException(
                "fixture-body-io-close-runtime"
            );
        RuntimeException close=
            new IllegalStateException(
                "fixture-close-runtime-after-body-io"
            );
        Throwable observed=
            invokeArchive(
                new FailingReadAndCloseChannel(
                    body,
                    close
                )
            );

        assertPrimaryWithSuppressed(
            observed,
            close,
            body,
            "body IOException + close RuntimeException"
        );
    }

    private static void assertBodyIOExceptionCloseError(){
        IOException body=
            new IOException(
                "fixture-body-io-close-error"
            );
        Error close=
            new AssertionError(
                "fixture-close-error-after-body-io"
            );
        Throwable observed=
            invokeArchive(
                new FailingReadAndCloseChannel(
                    body,
                    close
                )
            );

        assertPrimaryWithSuppressed(
            observed,
            close,
            body,
            "body IOException + close Error"
        );
    }

    private static void assertCleanCloseIOException(){
        IOException close=
            new IOException(
                "fixture-clean-close-io"
            );
        Throwable observed=
            invokeArchive(
                new FailingCloseChannel(
                    close
                )
            );

        if(observed!=close)
            throw new AssertionError(
                "clean archive close IOException identity changed",
                observed
            );
    }

    private static void assertCleanCloseRuntime(){
        RuntimeException close=
            new IllegalArgumentException(
                "fixture-clean-close-runtime"
            );
        Throwable observed=
            invokeArchive(
                new FailingCloseChannel(
                    close
                )
            );

        if(observed!=close)
            throw new AssertionError(
                "clean archive close RuntimeException identity changed",
                observed
            );
    }

    private static void assertCleanCloseError(){
        Error close=
            new LinkageError(
                "fixture-clean-close-error"
            );
        Throwable observed=
            invokeArchive(
                new FailingCloseChannel(
                    close
                )
            );

        if(observed!=close)
            throw new AssertionError(
                "clean archive close Error identity changed",
                observed
            );
    }

    private static void assertResponseRuntimeKeepsPrimary(){
        RuntimeException response=
            new IllegalStateException(
                "fixture-response-runtime"
            );
        IOException close=
            new IOException(
                "fixture-close-io-after-response-runtime"
            );
        Throwable observed=
            invokeArchive(
                new FailingSizeAndCloseChannel(
                    response,
                    close
                )
            );

        assertPrimaryWithSuppressed(
            observed,
            response,
            close,
            "response RuntimeException + close IOException"
        );
    }

    private static void assertResponseErrorKeepsPrimary(){
        Error response=
            new AssertionError(
                "fixture-response-error"
            );
        RuntimeException close=
            new IllegalStateException(
                "fixture-close-runtime-after-response-error"
            );
        Throwable observed=
            invokeArchive(
                new FailingSizeAndCloseChannel(
                    response,
                    close
                )
            );

        assertPrimaryWithSuppressed(
            observed,
            response,
            close,
            "response Error + close RuntimeException"
        );
    }

    private static void assertSameObjectCleanupDoesNotSelfSuppress(){
        IOException same=
            new IOException(
                "fixture-same-body-close-io"
            );
        Throwable observed=
            invokeArchive(
                new FailingReadAndCloseChannel(
                    same,
                    same
                )
            );

        if(observed!=same||
           same.getSuppressed().length!=0)
            throw new AssertionError(
                "same archive response/close failure self-suppressed or changed identity",
                observed
            );
    }

    private static Throwable invokeArchive(
        SeekableByteChannel channel
    ){
        Throwable observed=null;

        try{
            LocalAuxArchiveAccess
                .writeIfAvailable(
                    new ByteArrayOutputStream(),
                    Path.of(
                        "fixture-post-open-order.zip"
                    ),
                    false,
                    file->true,
                    file->channel
                );
        }catch(Throwable failure){
            observed=failure;
        }

        if(observed==null)
            throw new AssertionError(
                "archive failure-order fixture unexpectedly completed cleanly"
            );

        return observed;
    }

    private static void assertPrimaryWithSuppressed(
        Throwable observed,
        Throwable primary,
        Throwable suppressed,
        String phase
    ){
        if(observed!=primary)
            throw new AssertionError(
                phase+
                " changed primary identity",
                observed
            );

        Throwable[] values=
            primary.getSuppressed();

        if(values.length!=1||
           values[0]!=suppressed)
            throw new AssertionError(
                phase+
                " did not retain cleanup/body evidence in order"
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

        @Override public long size()
            throws IOException{
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

        @Override public void close()
            throws IOException{
            open=false;
        }
    }

    private static final class FailingCloseChannel
        extends EmptyChannel {
        private final Throwable closeFailure;

        FailingCloseChannel(
            Throwable closeFailure
        ){
            this.closeFailure=closeFailure;
        }

        @Override public void close()
            throws IOException{
            throwFailure(
                closeFailure
            );
        }
    }

    private static final class FailingReadAndCloseChannel
        extends EmptyChannel {
        private final IOException readFailure;
        private final Throwable closeFailure;

        FailingReadAndCloseChannel(
            IOException readFailure,
            Throwable closeFailure
        ){
            this.readFailure=readFailure;
            this.closeFailure=closeFailure;
        }

        @Override public long size(){
            return 1L;
        }

        @Override public int read(
            ByteBuffer destination
        )throws IOException{
            throw readFailure;
        }

        @Override public void close()
            throws IOException{
            throwFailure(
                closeFailure
            );
        }
    }

    private static final class FailingSizeAndCloseChannel
        extends EmptyChannel {
        private final Throwable responseFailure;
        private final Throwable closeFailure;

        FailingSizeAndCloseChannel(
            Throwable responseFailure,
            Throwable closeFailure
        ){
            this.responseFailure=responseFailure;
            this.closeFailure=closeFailure;
        }

        @Override public long size()
            throws IOException{
            throwFailure(
                responseFailure
            );
            throw new AssertionError(
                "unreachable"
            );
        }

        @Override public void close()
            throws IOException{
            throwFailure(
                closeFailure
            );
        }
    }

    private static void throwFailure(
        Throwable failure
    )throws IOException{
        if(failure instanceof IOException)
            throw (IOException)failure;

        if(failure instanceof RuntimeException)
            throw (RuntimeException)failure;

        if(failure instanceof Error)
            throw (Error)failure;

        throw new IOException(
            "fixture unsupported throwable",
            failure
        );
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
