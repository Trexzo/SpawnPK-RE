package spk.local;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public final class LocalAuxHttpRequestReaderTest {
    public static void main(String[] args)
        throws Exception{
        assertOrdinaryRequest();
        assertOverlongPhysicalLineRejected();
        assertHeaderLineLimit();
        assertTotalBudget();
        assertAbsoluteDeadlineDoesNotReset();
        assertFailureIsConnectionScoped();

        System.out.println(
            "LOCAL_AUX_HTTP_REQUEST_READER_PASS "+
            "ordinary=true "+
            "lineLimit=8192 "+
            "overlongRejected=true "+
            "headerLinesBounded=64 "+
            "totalBytesBounded=65536 "+
            "absoluteDeadline=true "+
            "deadlineDoesNotReset=true "+
            "connectionScoped=true"
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
