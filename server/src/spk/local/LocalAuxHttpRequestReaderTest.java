package spk.local;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class LocalAuxHttpRequestReaderTest {
    public static void main(String[] args)
        throws Exception{
        assertOrdinaryRequest();
        assertExactLineLimitAccepted();
        assertOverlongPhysicalLineRejected();
        assertCrPaddingCannotBypassPhysicalLineLimit();
        assertHeaderLineLimit();
        assertTotalBudget();
        assertAbsoluteDeadlineDoesNotReset();
        assertFailureIsConnectionScoped();
        assertWorkerContinuesAfterMalformedConnection();
        assertUncheckedWorkerFailureStillEscapes();

        System.out.println(
            "LOCAL_AUX_HTTP_REQUEST_READER_PASS "+
            "ordinary=true "+
            "lineLimit=8192 "+
            "exactLineLimitAccepted=true "+
            "overlongRejected=true "+
            "crPaddingRejected=true "+
            "headerLinesBounded=64 "+
            "totalBytesBounded=65536 "+
            "absoluteDeadline=true "+
            "deadlineDoesNotReset=true "+
            "connectionScoped=true "+
            "sameWorkerContinues=true "+
            "failedSocketRetired=true "+
            "nextSocketHandled=true "+
            "uncheckedStillEscapes=true"
        );
    }

    private static void assertOrdinaryRequest()
        throws Exception{
        byte[] request=
            (
                "GET /cache.zip HTTP/1.1\r\n"+
                "Host: 127.0.0.1\r\n"+
                "Connection: close\r\n"+
                "\r\n"
            ).getBytes(
                StandardCharsets
                    .ISO_8859_1
            );
        RecordingTimeout timeout=
            new RecordingTimeout();
        LocalAuxHttpRequestReader reader=
            new LocalAuxHttpRequestReader(
                new ByteArrayInputStream(
                    request
                ),
                ()->0L,
                timeout,
                LocalAuxHttpRequestReader
                    .REQUEST_DEADLINE_NANOS
            );

        String first=
            reader.readRequestLine();

        if(!"GET /cache.zip HTTP/1.1"
                .equals(first))
            throw new AssertionError(
                "ordinary request line changed: "+
                first
            );

        reader.consumeHeaders();

        if(reader.totalBytes()!=
                request.length)
            throw new AssertionError(
                "ordinary request byte accounting mismatch expected="+
                request.length+
                " actual="+
                reader.totalBytes()
            );

        if(timeout.calls.get()==0||
           timeout.minMillis<=0)
            throw new AssertionError(
                "ordinary request did not maintain positive bounded timeout"
            );
    }

    private static void assertExactLineLimitAccepted()
        throws Exception{
        byte[] line=
            (
                repeat(
                    'A',
                    LocalAuxHttpRequestReader
                        .MAX_LINE_BYTES
                )+
                "\n"
            ).getBytes(
                StandardCharsets
                    .ISO_8859_1
            );

        LocalAuxHttpRequestReader reader=
            reader(
                line
            );
        String value=
            reader.readRequestLine();

        if(value==null||
           value.length()!=
                LocalAuxHttpRequestReader
                    .MAX_LINE_BYTES)
            throw new AssertionError(
                "exact 8 KiB request line was not accepted"
            );
    }

    private static void assertOverlongPhysicalLineRejected()
        throws Exception{
        byte[] line=
            new byte[
                LocalAuxHttpRequestReader
                    .MAX_LINE_BYTES+
                2
            ];

        java.util.Arrays.fill(
            line,
            0,
            line.length-1,
            (byte)'A'
        );
        line[line.length-1]=
            (byte)'\n';

        LocalAuxHttpRequestReader reader=
            reader(
                line
            );

        boolean rejected=false;

        try{
            reader.readRequestLine();
        }catch(IOException expected){
            rejected=
                expected.getMessage()!=null&&
                expected.getMessage()
                    .contains(
                        "line exceeds"
                    );
        }

        if(!rejected)
            throw new AssertionError(
                "overlong physical request line was truncated/split instead of rejected"
            );

        if(reader.totalBytes()!=
                LocalAuxHttpRequestReader
                    .MAX_LINE_BYTES+
                1)
            throw new AssertionError(
                "overlong line consumed unexpected byte count: "+
                reader.totalBytes()
            );
    }

    private static void assertCrPaddingCannotBypassPhysicalLineLimit()
        throws Exception{
        byte[] line=
            (
                repeat(
                    '\r',
                    LocalAuxHttpRequestReader
                        .MAX_LINE_BYTES
                )+
                "A\n"
            ).getBytes(
                StandardCharsets
                    .ISO_8859_1
            );

        LocalAuxHttpRequestReader reader=
            reader(
                line
            );
        boolean rejected=false;

        try{
            reader.readRequestLine();
        }catch(IOException expected){
            rejected=
                expected.getMessage()!=null&&
                expected.getMessage()
                    .contains(
                        "physical line exceeds"
                    );
        }

        if(!rejected)
            throw new AssertionError(
                "CR padding bypassed physical request-line ceiling"
            );

        if(reader.totalBytes()!=
                LocalAuxHttpRequestReader
                    .MAX_LINE_BYTES+
                1)
            throw new AssertionError(
                "CR-padded overlong line consumed unexpected bytes: "+
                reader.totalBytes()
            );
    }

    private static void assertHeaderLineLimit()
        throws Exception{
        StringBuilder text=
            new StringBuilder(
                "GET / HTTP/1.1\r\n"
            );

        for(int i=0;
            i<
                LocalAuxHttpRequestReader
                    .MAX_HEADER_LINES+
                1;
            i++)
            text.append(
                "X-"
            ).append(i)
             .append(
                ": a\r\n"
             );

        text.append(
            "\r\n"
        );

        LocalAuxHttpRequestReader reader=
            reader(
                text.toString()
                    .getBytes(
                        StandardCharsets
                            .ISO_8859_1
                    )
            );

        reader.readRequestLine();
        boolean rejected=false;

        try{
            reader.consumeHeaders();
        }catch(IOException expected){
            rejected=
                expected.getMessage()!=null&&
                expected.getMessage()
                    .contains(
                        "header line limit"
                    );
        }

        if(!rejected)
            throw new AssertionError(
                "header line-count budget was not enforced"
            );
    }

    private static void assertTotalBudget()
        throws Exception{
        StringBuilder text=
            new StringBuilder(
                "GET / HTTP/1.1\r\n"
            );
        String payload=
            repeat(
                'x',
                LocalAuxHttpRequestReader
                    .MAX_LINE_BYTES-
                16
            );

        for(int i=0;i<16;i++)
            text.append(
                "X: "
            ).append(payload)
             .append(
                "\r\n"
             );

        LocalAuxHttpRequestReader reader=
            reader(
                text.toString()
                    .getBytes(
                        StandardCharsets
                            .ISO_8859_1
                    )
            );

        reader.readRequestLine();
        boolean rejected=false;

        try{
            reader.consumeHeaders();
        }catch(IOException expected){
            rejected=
                expected.getMessage()!=null&&
                expected.getMessage()
                    .contains(
                        "request exceeds"
                    );
        }

        if(!rejected)
            throw new AssertionError(
                "aggregate request byte budget was not enforced"
            );

        if(reader.totalBytes()!=
                LocalAuxHttpRequestReader
                    .MAX_TOTAL_BYTES)
            throw new AssertionError(
                "aggregate budget consumed beyond limit: "+
                reader.totalBytes()
            );
    }

    private static void assertAbsoluteDeadlineDoesNotReset()
        throws Exception{
        byte[] endless=
            repeat(
                'A',
                256
            ).getBytes(
                StandardCharsets
                    .ISO_8859_1
            );
        StepClock clock=
            new StepClock(
                10L
            );
        LocalAuxHttpRequestReader reader=
            new LocalAuxHttpRequestReader(
                new ByteArrayInputStream(
                    endless
                ),
                clock,
                ignored->{},
                100L
            );

        Throwable observed=null;

        try{
            reader.readRequestLine();
        }catch(Throwable failure){
            observed=failure;
        }

        if(!(observed instanceof
                SocketTimeoutException))
            throw new AssertionError(
                "continuous input reset/escaped absolute deadline",
                observed
            );

        if(reader.totalBytes()>=
                endless.length)
            throw new AssertionError(
                "deadline allowed entire continuously available input to be consumed"
            );
    }

    private static void assertFailureIsConnectionScoped()
        throws Exception{
        LocalAuxHttpRequestReader failed=
            new LocalAuxHttpRequestReader(
                new ByteArrayInputStream(
                    "GET / HTTP/1.1\r\n"
                        .getBytes(
                            StandardCharsets
                                .ISO_8859_1
                        )
                ),
                new StepClock(50L),
                ignored->{},
                100L
            );

        try{
            failed.readRequestLine();
            throw new AssertionError(
                "fixture deadline unexpectedly succeeded"
            );
        }catch(SocketTimeoutException expected){
            // connection-scoped parse failure
        }

        LocalAuxHttpRequestReader next=
            reader(
                (
                    "HEAD /cache.zip HTTP/1.1\r\n"+
                    "\r\n"
                ).getBytes(
                    StandardCharsets
                        .ISO_8859_1
                )
            );

        String line=
            next.readRequestLine();
        next.consumeHeaders();

        if(!"HEAD /cache.zip HTTP/1.1"
                .equals(line))
            throw new AssertionError(
                "next request was poisoned by prior parser failure"
            );
    }

    private static void assertWorkerContinuesAfterMalformedConnection()
        throws Exception{
        FakeSocket malformed=
            new FakeSocket(
                (
                    repeat(
                        '\r',
                        LocalAuxHttpRequestReader
                            .MAX_LINE_BYTES
                    )+
                    "A\n"
                ).getBytes(
                    StandardCharsets
                        .ISO_8859_1
                )
            );
        FakeSocket valid=
            new FakeSocket(
                (
                    "GET /Production/tradingpost HTTP/1.1\r\n"+
                    "Host: 127.0.0.1\r\n"+
                    "\r\n"
                ).getBytes(
                    StandardCharsets
                        .ISO_8859_1
                )
            );
        List<Socket> accepted=
            new ArrayList<>();
        accepted.add(
            malformed
        );
        accepted.add(
            valid
        );
        AtomicInteger index=
            new AtomicInteger();
        AtomicInteger failures=
            new AtomicInteger();
        AtomicInteger releases=
            new AtomicInteger();

        LocalAuxHttpWorker.run(
            ()->false,
            ()->{
                int current=
                    index.getAndIncrement();

                return current<
                        accepted.size()
                    ?accepted.get(
                        current
                    )
                    :null;
            },
            Main::handleAuxConnection,
            socket->{
                releases.incrementAndGet();
                socket.close();
            },
            failure->
                failures.incrementAndGet(),
            failure->{
                throw new AssertionError(
                    "worker retirement retry unexpectedly failed",
                    failure
                );
            }
        );

        if(failures.get()!=1)
            throw new AssertionError(
                "malformed connection did not remain one connection-scoped failure count="+
                failures.get()
            );

        if(releases.get()!=2||
           !malformed.isClosed()||
           !valid.isClosed())
            throw new AssertionError(
                "worker did not retire both accepted sockets releases="+
                releases.get()
            );

        String response=
            new String(
                valid.output(),
                StandardCharsets
                    .ISO_8859_1
            );

        if(!response.contains(
                "HTTP/1.1 200 OK\r\n")||
           !response.endsWith(
                "{}\n"))
            throw new AssertionError(
                "same AUX worker did not handle valid connection after malformed peer: "+
                response
            );
    }

    private static void assertUncheckedWorkerFailureStillEscapes()
        throws Exception{
        FakeSocket socket=
            new FakeSocket(
                new byte[0]
            );
        RuntimeException expected=
            new IllegalStateException(
                "fixture-worker-unchecked"
            );
        AtomicInteger releases=
            new AtomicInteger();
        Throwable observed=null;

        try{
            LocalAuxHttpWorker.run(
                ()->false,
                new LocalAuxHttpWorker.Acceptor(){
                    private boolean first=true;

                    @Override public Socket accept(){
                        if(!first)
                            return null;

                        first=false;
                        return socket;
                    }
                },
                ignored->{
                    throw expected;
                },
                owned->{
                    releases.incrementAndGet();
                    owned.close();
                },
                failure->{},
                failure->{}
            );
        }catch(Throwable failure){
            observed=failure;
        }

        if(observed!=expected||
           releases.get()!=1||
           !socket.isClosed())
            throw new AssertionError(
                "unchecked AUX worker failure was swallowed/wrapped or socket not retired",
                observed
            );
    }

    private static LocalAuxHttpRequestReader reader(
        byte[] bytes
    )throws Exception{
        return new LocalAuxHttpRequestReader(
            new ByteArrayInputStream(
                bytes
            ),
            ()->0L,
            ignored->{},
            LocalAuxHttpRequestReader
                .REQUEST_DEADLINE_NANOS
        );
    }

    private static String repeat(
        char value,
        int count
    ){
        char[] chars=
            new char[count];
        java.util.Arrays.fill(
            chars,
            value
        );
        return new String(
            chars
        );
    }

    private static final class FakeSocket
        extends Socket {
        private final ByteArrayInputStream input;
        private final java.io.ByteArrayOutputStream output=
            new java.io.ByteArrayOutputStream();
        private boolean closed;
        private int timeoutMillis;

        FakeSocket(
            byte[] input
        ){
            this.input=
                new ByteArrayInputStream(
                    input
                );
        }

        @Override public InputStream getInputStream(){
            return input;
        }

        @Override public OutputStream getOutputStream(){
            return output;
        }

        @Override public void setSoTimeout(
            int timeout
        ){
            timeoutMillis=timeout;
        }

        @Override public int getSoTimeout(){
            return timeoutMillis;
        }

        @Override public synchronized void close(){
            closed=true;
        }

        @Override public synchronized boolean isClosed(){
            return closed;
        }

        byte[] output(){
            return output.toByteArray();
        }
    }

    private static final class
        RecordingTimeout
        implements LocalAuxHttpRequestReader
            .TimeoutSetter {
        final AtomicInteger calls=
            new AtomicInteger();
        volatile int minMillis=
            Integer.MAX_VALUE;

        @Override public void setTimeoutMillis(
            int timeoutMillis
        ){
            calls.incrementAndGet();
            minMillis=
                Math.min(
                    minMillis,
                    timeoutMillis
                );
        }
    }

    private static final class
        StepClock
        implements LocalAuxHttpRequestReader
            .NanoClock {
        private final long step;
        private long value;

        StepClock(
            long step
        ){
            this.step=step;
        }

        @Override public long nanoTime(){
            long current=value;
            value+=step;
            return current;
        }
    }

    private LocalAuxHttpRequestReaderTest(){}
}
