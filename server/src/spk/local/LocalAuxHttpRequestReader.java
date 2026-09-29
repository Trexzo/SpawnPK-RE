package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

final class LocalAuxHttpRequestReader {
    static final int MAX_LINE_BYTES=
        8 * 1024;
    static final int MAX_TOTAL_BYTES=
        64 * 1024;
    static final int MAX_HEADER_LINES=
        64;
    static final long REQUEST_DEADLINE_NANOS=
        TimeUnit.SECONDS.toNanos(
            2
        );

    interface NanoClock {
        long nanoTime();
    }

    interface TimeoutSetter {
        void setTimeoutMillis(
            int timeoutMillis
        )throws IOException;
    }

    private final InputStream in;
    private final NanoClock clock;
    private final TimeoutSetter timeoutSetter;
    private final long deadlineNanos;

    private int totalBytes;
    private boolean requestLineRead;
    private boolean headersConsumed;

    static LocalAuxHttpRequestReader forSocket(
        Socket socket,
        InputStream in
    )throws IOException{
        Objects.requireNonNull(
            socket,
            "socket"
        );

        return new LocalAuxHttpRequestReader(
            in,
            System::nanoTime,
            socket::setSoTimeout,
            REQUEST_DEADLINE_NANOS
        );
    }

    LocalAuxHttpRequestReader(
        InputStream in,
        NanoClock clock,
        TimeoutSetter timeoutSetter,
        long deadlineBudgetNanos
    )throws IOException{
        this.in=
            Objects.requireNonNull(
                in,
                "in"
            );
        this.clock=
            Objects.requireNonNull(
                clock,
                "clock"
            );
        this.timeoutSetter=
            Objects.requireNonNull(
                timeoutSetter,
                "timeoutSetter"
            );

        if(deadlineBudgetNanos<=0)
            throw new IllegalArgumentException(
                "deadlineBudgetNanos"
            );

        long now=
            clock.nanoTime();

        // nanoTime values are allowed to wrap.  Standard deadline subtraction
        // remains correct for this small positive interval across that wrap.
        deadlineNanos=
            now+
            deadlineBudgetNanos;

        updateReadTimeout();
    }

    String readRequestLine()
        throws IOException{
        if(requestLineRead)
            throw new IllegalStateException(
                "request line already read"
            );

        requestLineRead=true;
        return readLine();
    }

    void consumeHeaders()
        throws IOException{
        if(!requestLineRead)
            throw new IllegalStateException(
                "request line not read"
            );

        if(headersConsumed)
            throw new IllegalStateException(
                "headers already consumed"
            );

        headersConsumed=true;
        int lines=0;

        for(;;){
            String line=
                readLine();

            if(line==null||
               line.isEmpty())
                return;

            lines++;

            if(lines>MAX_HEADER_LINES)
                throw new IOException(
                    "auxiliary HTTP header line limit exceeded: "+
                    lines
                );
        }
    }

    int totalBytes(){
        return totalBytes;
    }

    private String readLine()
        throws IOException{
        ByteArrayOutputStream buffer=
            new ByteArrayOutputStream(
                Math.min(
                    128,
                    MAX_LINE_BYTES
                )
            );
        boolean sawAny=false;
        int physicalBytes=0;

        for(;;){
            int value=
                readByte();

            if(value<0)
                return sawAny
                    ?buffer.toString(
                        StandardCharsets
                            .ISO_8859_1
                            .name()
                    )
                    :null;

            sawAny=true;

            if(value=='\n')
                return buffer.toString(
                    StandardCharsets
                        .ISO_8859_1
                        .name()
                );

            if(physicalBytes>=
                    MAX_LINE_BYTES)
                throw new IOException(
                    "auxiliary HTTP physical line exceeds "+
                    MAX_LINE_BYTES+
                    " bytes"
                );

            physicalBytes++;

            if(value=='\r')
                continue;

            buffer.write(
                value
            );
        }
    }

    private int readByte()
        throws IOException{
        if(totalBytes>=
                MAX_TOTAL_BYTES)
            throw new IOException(
                "auxiliary HTTP request exceeds "+
                MAX_TOTAL_BYTES+
                " bytes"
            );

        updateReadTimeout();

        int value=in.read();

        if(value>=0)
            totalBytes++;

        return value;
    }

    private void updateReadTimeout()
        throws IOException{
        long now=
            clock.nanoTime();
        long remaining=
            deadlineNanos-
            now;

        if(remaining<=0)
            throw new SocketTimeoutException(
                "auxiliary HTTP request deadline exceeded"
            );

        long millis=
            TimeUnit.NANOSECONDS
                .toMillis(
                    remaining
                );

        if(TimeUnit.MILLISECONDS
                .toNanos(
                    millis)<remaining)
            millis++;

        if(millis<=0)
            millis=1;

        timeoutSetter.setTimeoutMillis(
            (int)Math.min(
                Integer.MAX_VALUE,
                millis
            )
        );
    }

}
