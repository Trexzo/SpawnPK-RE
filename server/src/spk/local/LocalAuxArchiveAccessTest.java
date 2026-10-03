package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
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
        assertOpenIOExceptionPropagates();
        assertPostOpenIOExceptionPropagates();
        assertRuntimeUnswept();
        assertFatalUnswept();

        assertBodyIOExceptionKeepsCloseIOExceptionSubordinate();
        assertUncheckedCloseOverridesBodyIOException();
        assertFatalCloseOverridesBodyIOException();
        assertWriteIOExceptionPromotesUncheckedClose();
        assertCleanCloseFailuresEscape();
        assertUncheckedResponseFailureStaysPrimary();
        assertPostOpenCloseSecurityIsFatal();
        assertSameFailureDoesNotSelfSuppress();

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
            "singleHandleInherited=true "+
            "closeIoOrdering=true "+
            "closeRuntimeOverridesIo=true "+
            "closeErrorOverridesIo=true "+
            "writeIoOrdering=true "+
            "cleanCloseFailures=true "+
            "uncheckedResponsePrimary=true "+
            "postOpenCloseSecurityFatal=true "+
            "sameFailureIdentity=true"
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
        Throwable observed=
            invoke(
                new OrderedFailureChannel(
                    expected,
                    null
                )
            );

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

    private static void
        assertBodyIOExceptionKeepsCloseIOExceptionSubordinate(){
        IOException body=
            new IOException(
                "fixture-body-io"
            );
        IOException close=
            new IOException(
                "fixture-close-io"
            );

        Throwable observed=
            invoke(
                new OrderedFailureChannel(
                    body,
                    close
                )
            );

        assertPrimaryWithSingleSuppressed(
            "body IOException + close IOException",
            observed,
            body,
            close
        );
    }

    private static void
        assertUncheckedCloseOverridesBodyIOException(){
        IOException body=
            new IOException(
                "fixture-body-before-runtime-close"
            );
        RuntimeException close=
            new IllegalStateException(
                "fixture-runtime-close"
            );

        Throwable observed=
            invoke(
                new OrderedFailureChannel(
                    body,
                    close
                )
            );

        assertPrimaryWithSingleSuppressed(
            "body IOException + close RuntimeException",
            observed,
            close,
            body
        );
    }

    private static void
        assertFatalCloseOverridesBodyIOException(){
        IOException body=
            new IOException(
                "fixture-body-before-error-close"
            );
        Error close=
            new AssertionError(
                "fixture-error-close"
            );

        Throwable observed=
            invoke(
                new OrderedFailureChannel(
                    body,
                    close
                )
            );

        assertPrimaryWithSingleSuppressed(
            "body IOException + close Error",
            observed,
            close,
            body
        );
    }

    private static void
        assertWriteIOExceptionPromotesUncheckedClose(){
        IOException write=
            new IOException(
                "fixture-response-write-io"
            );
        RuntimeException close=
            new IllegalStateException(
                "fixture-response-write-close-runtime"
            );

        Throwable observed=
            invoke(
                new ThrowingOutputStream(
                    write
                ),
                new OrderedFailureChannel(
                    null,
                    close
                )
            );

        assertPrimaryWithSingleSuppressed(
            "response write IOException + close RuntimeException",
            observed,
            close,
            write
        );
    }

    private static void assertCleanCloseFailuresEscape(){
        IOException closeIo=
            new IOException(
                "fixture-clean-close-io"
            );
        Throwable ioObserved=
            invoke(
                new OrderedFailureChannel(
                    null,
                    closeIo
                )
            );

        if(ioObserved!=closeIo)
            throw new AssertionError(
                "clean close IOException lost identity",
                ioObserved
            );

        RuntimeException closeRuntime=
            new IllegalStateException(
                "fixture-clean-close-runtime"
            );
        Throwable runtimeObserved=
            invoke(
                new OrderedFailureChannel(
                    null,
                    closeRuntime
                )
            );

        if(runtimeObserved!=closeRuntime)
            throw new AssertionError(
                "clean close RuntimeException lost identity",
                runtimeObserved
            );

        Error closeError=
            new AssertionError(
                "fixture-clean-close-error"
            );
        Throwable errorObserved=
            invoke(
                new OrderedFailureChannel(
                    null,
                    closeError
                )
            );

        if(errorObserved!=closeError)
            throw new AssertionError(
                "clean close Error lost identity",
                errorObserved
            );
    }

    private static void
        assertUncheckedResponseFailureStaysPrimary(){
        RuntimeException bodyRuntime=
            new IllegalStateException(
                "fixture-response-runtime"
            );
        IOException closeIo=
            new IOException(
                "fixture-response-runtime-close-io"
            );

        Throwable runtimeObserved=
            invoke(
                new OrderedFailureChannel(
                    bodyRuntime,
                    closeIo
                )
            );

        assertPrimaryWithSingleSuppressed(
            "response RuntimeException + close IOException",
            runtimeObserved,
            bodyRuntime,
            closeIo
        );

        Error bodyError=
            new AssertionError(
                "fixture-response-error"
            );
        RuntimeException closeRuntime=
            new IllegalStateException(
                "fixture-response-error-close-runtime"
            );

        Throwable errorObserved=
            invoke(
                new OrderedFailureChannel(
                    bodyError,
                    closeRuntime
                )
            );

        assertPrimaryWithSingleSuppressed(
            "response Error + close RuntimeException",
            errorObserved,
            bodyError,
            closeRuntime
        );
    }

    private static void assertPostOpenCloseSecurityIsFatal(){
        IOException body=
            new IOException(
                "fixture-body-before-security-close"
            );
        SecurityException close=
            new SecurityException(
                "fixture-post-open-close-security"
            );

        Throwable observed=
            invoke(
                new OrderedFailureChannel(
                    body,
                    close
                )
            );

        assertPrimaryWithSingleSuppressed(
            "body IOException + post-open close SecurityException",
            observed,
            close,
            body
        );
    }

    private static void assertSameFailureDoesNotSelfSuppress(){
        IOException same=
            new IOException(
                "fixture-same-body-close"
            );

        Throwable observed=
            invoke(
                new OrderedFailureChannel(
                    same,
                    same
                )
            );

        if(observed!=same)
            throw new AssertionError(
                "same body/close failure lost primary identity",
                observed
            );

        if(observed.getSuppressed().length!=0)
            throw new AssertionError(
                "same body/close failure self-suppressed"
            );
    }

    private static Throwable invoke(
        SeekableByteChannel channel
    ){
        return invoke(
            new ByteArrayOutputStream(),
            channel
        );
    }

    private static Throwable invoke(
        OutputStream out,
        SeekableByteChannel channel
    ){
        try{
            LocalAuxArchiveAccess
                .writeIfAvailable(
                    out,
                    Path.of(
                        "fixture-ordering.zip"
                    ),
                    false,
                    file->true,
                    file->channel
                );
            return null;
        }catch(Throwable failure){
            return failure;
        }
    }

    private static void assertPrimaryWithSingleSuppressed(
        String label,
        Throwable observed,
        Throwable expectedPrimary,
        Throwable expectedSuppressed
    ){
        if(observed!=expectedPrimary)
            throw new AssertionError(
                label+
                " primary identity mismatch",
                observed
            );

        Throwable[] suppressed=
            observed.getSuppressed();

        if(suppressed.length!=1||
           suppressed[0]!=expectedSuppressed)
            throw new AssertionError(
                label+
                " suppression ordering mismatch"
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

        @Override public void close()
            throws IOException{
            open=false;
        }
    }

    private static final class ThrowingOutputStream
        extends OutputStream {
        private final IOException failure;

        ThrowingOutputStream(
            IOException failure
        ){
            this.failure=failure;
        }

        @Override public void write(
            int value
        )throws IOException{
            throw failure;
        }

        @Override public void write(
            byte[] source,
            int offset,
            int length
        )throws IOException{
            throw failure;
        }
    }

    private static final class OrderedFailureChannel
        extends EmptyChannel {
        private final Throwable bodyFailure;
        private final Throwable closeFailure;

        OrderedFailureChannel(
            Throwable bodyFailure,
            Throwable closeFailure
        ){
            this.bodyFailure=bodyFailure;
            this.closeFailure=closeFailure;
        }

        @Override public long size(){
            return bodyFailure==null
                ?0L
                :1L;
        }

        @Override public int read(
            ByteBuffer destination
        )throws IOException{
            if(bodyFailure==null)
                return -1;

            throwAllowed(
                bodyFailure
            );
            return -1;
        }

        @Override public void close()
            throws IOException{
            open=false;

            if(closeFailure!=null)
                throwAllowed(
                    closeFailure
                );
        }
    }

    private static void throwAllowed(
        Throwable failure
    )throws IOException{
        if(failure instanceof IOException)
            throw (IOException)failure;

        if(failure instanceof RuntimeException)
            throw (RuntimeException)failure;

        throw (Error)failure;
    }

    private LocalAuxArchiveAccessTest(){}
}
