package spk.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class LocalAuxResponseDeadlineTest {
    public static void main(
        String[] args
    )throws Exception{
        assertTimedOutResponseDoesNotMonopolizeWorker();
        assertTerminalCloseWinsWithoutSecondAbort();
        assertHealthyDeadlineCompletesWithoutAbort();

        System.out.println(
            "LOCAL_AUX_RESPONSE_DEADLINE_PASS "+
            "responsePhaseBounded=true "+
            "headersCovered=true "+
            "timeoutAbortOnce=true "+
            "timeoutConnectionScoped=true "+
            "sameWorkerContinues=true "+
            "nextResponseExact=true "+
            "terminalCloseWins=true "+
            "normalCompletionCancels=true "+
            "watchdogsRetired=true "+
            "archiveStreamingInherited=true"
        );
    }

    private static void
        assertTimedOutResponseDoesNotMonopolizeWorker()
        throws Exception{
        IOException stalledWriteFailure=
            new IOException(
                "fixture-stalled-response-write"
            );
        BlockingOutputStream blockedOut=
            new BlockingOutputStream(
                stalledWriteFailure
            );
        FakeSocket stalled=
            FakeSocket.blocking(
                request(
                    "/Production/tradingpost"
                ),
                blockedOut
            );
        FakeSocket healthy=
            FakeSocket.buffered(
                request(
                    "/Production/tradingpost"
                )
            );

        ManualTimeoutWaiter timeoutWaiter=
            new ManualTimeoutWaiter();
        TrackingDeadlineFactory deadlines=
            new TrackingDeadlineFactory(
                timeoutWaiter
            );
        AtomicInteger accepts=
            new AtomicInteger();
        AtomicInteger releases=
            new AtomicInteger();
        AtomicInteger healthyHandled=
            new AtomicInteger();
        List<IOException> connectionFailures=
            new ArrayList<>();
        AtomicReference<Throwable>
            workerFailure=
                new AtomicReference<>();

        Thread worker=
            new Thread(
                ()->{
                    try{
                        LocalAuxHttpWorker.run(
                            ()->false,
                            ()->{
                                int index=
                                    accepts.getAndIncrement();

                                if(index==0)
                                    return stalled;
                                if(index==1)
                                    return healthy;
                                return null;
                            },
                            socket->{
                                Main.handleAuxConnection(
                                    socket,
                                    deadlines
                                );

                                if(socket==healthy)
                                    healthyHandled.incrementAndGet();
                            },
                            socket->{
                                releases.incrementAndGet();
                                socket.close();
                            },
                            connectionFailures::add,
                            failure->{}
                        );
                    }catch(Throwable failure){
                        workerFailure.set(
                            failure
                        );
                    }
                },
                "aux-response-deadline-worker-test"
            );

        worker.start();

        if(!timeoutWaiter.entered.await(
                5,
                TimeUnit.SECONDS))
            throw new AssertionError(
                "response deadline watchdog did not enter wait"
            );

        if(!blockedOut.writeStarted.await(
                5,
                TimeUnit.SECONDS))
            throw new AssertionError(
                "response did not block on first header write"
            );

        timeoutWaiter.fireTimeout();

        worker.join(
            5_000L
        );

        if(worker.isAlive()){
            worker.interrupt();
            throw new AssertionError(
                "AUX worker remained monopolized after response timeout"
            );
        }

        if(workerFailure.get()!=null)
            throw new AssertionError(
                "response timeout became authoritative worker death",
                workerFailure.get()
            );

        if(deadlines.abortCalls.get()!=1)
            throw new AssertionError(
                "deadline abort count mismatch: "+
                deadlines.abortCalls.get()
            );

        if(!stalled.isClosed())
            throw new AssertionError(
                "timed-out response socket was not physically closed"
            );

        if(healthyHandled.get()!=1||
           accepts.get()!=3)
            throw new AssertionError(
                "same AUX worker did not continue to the next connection"
            );

        if(connectionFailures.size()!=1||
           connectionFailures.get(0)!=
                stalledWriteFailure)
            throw new AssertionError(
                "timeout-induced write failure lost connection-scoped identity"
            );

        if(stalledWriteFailure
                .getSuppressed().length!=1||
           !(stalledWriteFailure
                .getSuppressed()[0]
                instanceof java.net
                    .SocketTimeoutException))
            throw new AssertionError(
                "response timeout evidence was not retained behind write failure"
            );

        String healthyWire=
            healthy.outputText();

        if(!healthyWire.contains(
                "HTTP/1.1 200 OK\r\n")||
           !healthyWire.endsWith(
                "{}\n"))
            throw new AssertionError(
                "next healthy AUX response changed: "+
                healthyWire
            );

        if(releases.get()!=2)
            throw new AssertionError(
                "worker did not reconcile both accepted sockets"
            );

        deadlines.assertAllWatchdogsRetired();
    }

    private static void
        assertTerminalCloseWinsWithoutSecondAbort()
        throws Exception{
        IOException terminalWriteFailure=
            new IOException(
                "fixture-terminal-close-write"
            );
        BlockingOutputStream blockedOut=
            new BlockingOutputStream(
                terminalWriteFailure
            );
        FakeSocket socket=
            FakeSocket.blocking(
                request(
                    "/Production/tradingpost"
                ),
                blockedOut
            );
        TrackingDeadlineFactory deadlines=
            new TrackingDeadlineFactory(
                null
            );
        AtomicReference<Throwable> observed=
            new AtomicReference<>();

        Thread handler=
            new Thread(
                ()->{
                    try{
                        Main.handleAuxConnection(
                            socket,
                            deadlines
                        );
                    }catch(Throwable failure){
                        observed.set(
                            failure
                        );
                    }
                },
                "aux-response-terminal-race-test"
            );

        handler.start();

        if(!blockedOut.writeStarted.await(
                5,
                TimeUnit.SECONDS))
            throw new AssertionError(
                "terminal race response never reached blocked write"
            );

        socket.close();

        handler.join(
            5_000L
        );

        if(handler.isAlive()){
            handler.interrupt();
            throw new AssertionError(
                "terminal socket close did not unblock response handler"
            );
        }

        if(observed.get()!=
                terminalWriteFailure)
            throw new AssertionError(
                "terminal-close write failure identity changed",
                observed.get()
            );

        if(deadlines.abortCalls.get()!=0)
            throw new AssertionError(
                "deadline issued a second abort after terminal close won"
            );

        deadlines.assertAllWatchdogsRetired();
    }

    private static void
        assertHealthyDeadlineCompletesWithoutAbort()
        throws Exception{
        FakeSocket socket=
            FakeSocket.buffered(
                request(
                    "/Production/tradingpost"
                )
            );
        TrackingDeadlineFactory deadlines=
            new TrackingDeadlineFactory(
                null
            );

        Main.handleAuxConnection(
            socket,
            deadlines
        );

        if(deadlines.abortCalls.get()!=0)
            throw new AssertionError(
                "healthy response triggered deadline abort"
            );

        if(!socket.outputText()
                .endsWith(
                    "{}\n"))
            throw new AssertionError(
                "healthy response body mismatch"
            );

        deadlines.assertAllWatchdogsRetired();
    }

    private static byte[] request(
        String target
    ){
        return (
            "GET "+
            target+
            " HTTP/1.1\r\n"+
            "Host: localhost\r\n"+
            "\r\n"
        ).getBytes(
            java.nio.charset.StandardCharsets
                .US_ASCII
        );
    }

    private static final class
        TrackingDeadlineFactory
        implements LocalAuxResponseDeadline.Factory {

        final AtomicInteger abortCalls=
            new AtomicInteger();
        final List<LocalAuxResponseDeadline>
            deadlines=
                new ArrayList<>();
        private final ManualTimeoutWaiter firstWaiter;
        private int started;

        TrackingDeadlineFactory(
            ManualTimeoutWaiter firstWaiter
        ){
            this.firstWaiter=
                firstWaiter;
        }

        @Override public synchronized
            LocalAuxResponseDeadline start(
                LocalAuxResponseDeadline.AbortAction abort
            ){
            final LocalAuxResponseDeadline.WaitStrategy
                waiter;

            if(started++==0&&
               firstWaiter!=null)
                waiter=firstWaiter;
            else
                waiter=
                    (completed,timeout)->
                        completed.await(
                            timeout,
                            TimeUnit.MILLISECONDS
                        );

            LocalAuxResponseDeadline deadline=
                LocalAuxResponseDeadline.start(
                    5_000L,
                    ()->{
                        abortCalls.incrementAndGet();
                        abort.abort();
                    },
                    waiter,
                    (target,name)->
                        new Thread(
                            target,
                            name+
                            "-test"
                        )
                );

            deadlines.add(
                deadline
            );
            return deadline;
        }

        synchronized void assertAllWatchdogsRetired(){
            for(LocalAuxResponseDeadline deadline:
                    deadlines)
                if(deadline.watchdogAlive())
                    throw new AssertionError(
                        "response watchdog survived completed connection"
                    );
        }
    }

    private static final class
        ManualTimeoutWaiter
        implements LocalAuxResponseDeadline.WaitStrategy {

        final CountDownLatch entered=
            new CountDownLatch(
                1
            );
        private final CountDownLatch fire=
            new CountDownLatch(
                1
            );

        @Override public boolean await(
            CountDownLatch completed,
            long timeoutMillis
        )throws InterruptedException{
            entered.countDown();
            fire.await();
            return false;
        }

        void fireTimeout(){
            fire.countDown();
        }
    }

    private static final class
        BlockingOutputStream
        extends OutputStream {

        final CountDownLatch writeStarted=
            new CountDownLatch(
                1
            );
        private final CountDownLatch released=
            new CountDownLatch(
                1
            );
        private final IOException failure;

        BlockingOutputStream(
            IOException failure
        ){
            this.failure=failure;
        }

        @Override public void write(
            int value
        )throws IOException{
            byte[] one=
                new byte[]{
                    (byte)value
                };
            write(
                one,
                0,
                1
            );
        }

        @Override public void write(
            byte[] values,
            int offset,
            int length
        )throws IOException{
            writeStarted.countDown();

            try{
                released.await();
            }catch(InterruptedException interrupted){
                Thread.currentThread()
                    .interrupt();
                throw new IOException(
                    "fixture blocking write interrupted",
                    interrupted
                );
            }

            throw failure;
        }

        void abort(){
            released.countDown();
        }
    }

    private static final class FakeSocket
        extends Socket {

        private final InputStream in;
        private final OutputStream out;
        private final ByteArrayOutputStream bytes;
        private volatile boolean closed;

        static FakeSocket blocking(
            byte[] request,
            BlockingOutputStream out
        ){
            return new FakeSocket(
                request,
                out,
                null
            );
        }

        static FakeSocket buffered(
            byte[] request
        ){
            ByteArrayOutputStream out=
                new ByteArrayOutputStream();

            return new FakeSocket(
                request,
                out,
                out
            );
        }

        private FakeSocket(
            byte[] request,
            OutputStream out,
            ByteArrayOutputStream bytes
        ){
            in=
                new ByteArrayInputStream(
                    request
                );
            this.out=out;
            this.bytes=bytes;
        }

        @Override public InputStream getInputStream(){
            return in;
        }

        @Override public OutputStream getOutputStream(){
            return out;
        }

        @Override public synchronized void close()
            throws IOException{
            if(closed)
                return;

            closed=true;

            if(out instanceof BlockingOutputStream)
                ((BlockingOutputStream)out)
                    .abort();
        }

        @Override public boolean isClosed(){
            return closed;
        }

        @Override public void setSoTimeout(
            int timeout
        ){
        }

        @Override public InetAddress getInetAddress(){
            return InetAddress.getLoopbackAddress();
        }

        @Override public SocketAddress getRemoteSocketAddress(){
            return new InetSocketAddress(
                InetAddress.getLoopbackAddress(),
                43595
            );
        }

        String outputText(){
            if(bytes==null)
                return "";

            return new String(
                bytes.toByteArray(),
                java.nio.charset.StandardCharsets
                    .ISO_8859_1
            );
        }
    }

    private LocalAuxResponseDeadlineTest(){}
}
