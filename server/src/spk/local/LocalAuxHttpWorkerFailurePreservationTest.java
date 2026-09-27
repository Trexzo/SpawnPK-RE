package spk.local;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class LocalAuxHttpWorkerFailurePreservationTest {
    public static void main(
        String[] args
    )throws Exception{
        assertRuntimePrimaryAcrossReleaseFailure();
        assertRuntimePrimaryAcrossTwoReleaseFailures();
        assertErrorPrimaryAcrossReleaseFailure();
        assertIoFailureRemainsConnectionScoped();
        assertCleanRetirementUnchanged();

        System.out.println(
            "LOCAL_AUX_HTTP_WORKER_FAILURE_PRESERVATION_PASS "+
            "runtimePrimary=true "+
            "errorPrimary=true "+
            "retirementSuppressed=true "+
            "residualRetry=true "+
            "secondRetirementSuppressed=true "+
            "ioConnectionScoped=true "+
            "workerContinues=true "+
            "cleanRetirement=true"
        );
    }

    private static void assertRuntimePrimaryAcrossReleaseFailure(){
        FakeSocket socket=
            new FakeSocket();
        RuntimeException expected=
            new IllegalStateException(
                "fixture-handler-runtime"
            );
        IOException release=
            new IOException(
                "fixture-release-first"
            );
        AtomicInteger releases=
            new AtomicInteger();
        Throwable observed=null;

        try{
            LocalAuxHttpWorker.run(
                ()->false,
                once(
                    socket
                ),
                ignored->{
                    throw expected;
                },
                ignored->{
                    if(releases.incrementAndGet()==1)
                        throw release;
                },
                failure->{},
                failure->{}
            );
        }catch(Throwable failure){
            observed=failure;
        }

        if(observed!=expected||
           releases.get()!=2||
           expected.getSuppressed().length!=1||
           expected.getSuppressed()[0]!=release)
            throw new AssertionError(
                "runtime handler failure was replaced or retirement retry missing",
                observed
            );
    }

    private static void assertRuntimePrimaryAcrossTwoReleaseFailures(){
        FakeSocket socket=
            new FakeSocket();
        RuntimeException expected=
            new IllegalArgumentException(
                "fixture-handler-runtime-two"
            );
        IOException first=
            new IOException(
                "fixture-release-first"
            );
        IOException second=
            new IOException(
                "fixture-release-second"
            );
        AtomicInteger releases=
            new AtomicInteger();
        AtomicReference<IOException>
            retirementObserved=
                new AtomicReference<>();
        Throwable observed=null;

        try{
            LocalAuxHttpWorker.run(
                ()->false,
                once(
                    socket
                ),
                ignored->{
                    throw expected;
                },
                ignored->{
                    int attempt=
                        releases.incrementAndGet();

                    throw attempt==1
                        ?first
                        :second;
                },
                failure->{},
                retirementObserved::set
            );
        }catch(Throwable failure){
            observed=failure;
        }

        Throwable[] suppressed=
            expected.getSuppressed();

        if(observed!=expected||
           releases.get()!=2||
           suppressed.length!=2||
           suppressed[0]!=first||
           suppressed[1]!=second||
           retirementObserved.get()!=second)
            throw new AssertionError(
                "second retirement failure did not remain behind runtime primary",
                observed
            );
    }

    private static void assertErrorPrimaryAcrossReleaseFailure(){
        FakeSocket socket=
            new FakeSocket();
        Error expected=
            new AssertionError(
                "fixture-handler-error"
            );
        IOException release=
            new IOException(
                "fixture-error-release"
            );
        AtomicInteger releases=
            new AtomicInteger();
        Throwable observed=null;

        try{
            LocalAuxHttpWorker.run(
                ()->false,
                once(
                    socket
                ),
                ignored->{
                    throw expected;
                },
                ignored->{
                    if(releases.incrementAndGet()==1)
                        throw release;
                },
                failure->{},
                failure->{}
            );
        }catch(Throwable failure){
            observed=failure;
        }

        if(observed!=expected||
           releases.get()!=2||
           expected.getSuppressed().length!=1||
           expected.getSuppressed()[0]!=release)
            throw new AssertionError(
                "Error handler failure was replaced by retirement failure",
                observed
            );
    }

    private static void assertIoFailureRemainsConnectionScoped()
        throws Exception{
        FakeSocket first=
            new FakeSocket();
        FakeSocket second=
            new FakeSocket();
        IOException expected=
            new IOException(
                "fixture-handler-io"
            );
        IOException release=
            new IOException(
                "fixture-handler-io-release"
            );
        List<Socket> sockets=
            new ArrayList<>();
        sockets.add(first);
        sockets.add(second);
        AtomicInteger accepts=
            new AtomicInteger();
        AtomicInteger firstReleases=
            new AtomicInteger();
        AtomicInteger secondHandled=
            new AtomicInteger();
        AtomicReference<IOException>
            connectionObserved=
                new AtomicReference<>();

        LocalAuxHttpWorker.run(
            ()->false,
            ()->{
                int index=
                    accepts.getAndIncrement();

                return index<sockets.size()
                    ?sockets.get(index)
                    :null;
            },
            socket->{
                if(socket==first)
                    throw expected;

                secondHandled.incrementAndGet();
            },
            socket->{
                if(socket==first&&
                   firstReleases.incrementAndGet()==1)
                    throw release;
            },
            connectionObserved::set,
            failure->{}
        );

        if(connectionObserved.get()!=expected||
           expected.getSuppressed().length!=1||
           expected.getSuppressed()[0]!=release||
           firstReleases.get()!=2||
           secondHandled.get()!=1)
            throw new AssertionError(
                "handler IOException became fatal or stopped worker continuation"
            );
    }

    private static void assertCleanRetirementUnchanged()
        throws Exception{
        FakeSocket socket=
            new FakeSocket();
        AtomicInteger handled=
            new AtomicInteger();
        AtomicInteger released=
            new AtomicInteger();

        LocalAuxHttpWorker.run(
            ()->false,
            once(
                socket
            ),
            ignored->
                handled.incrementAndGet(),
            ignored->
                released.incrementAndGet(),
            failure->{
                throw new AssertionError(
                    "unexpected connection failure",
                    failure
                );
            },
            failure->{
                throw new AssertionError(
                    "unexpected retirement failure",
                    failure
                );
            }
        );

        if(handled.get()!=1||
           released.get()!=1)
            throw new AssertionError(
                "clean worker handling/retirement changed"
            );
    }

    private static LocalAuxHttpWorker.Acceptor once(
        Socket socket
    ){
        return new LocalAuxHttpWorker.Acceptor(){
            private boolean first=true;

            @Override public Socket accept(){
                if(!first)
                    return null;

                first=false;
                return socket;
            }
        };
    }

    private static final class FakeSocket
        extends Socket {
    }

    private LocalAuxHttpWorkerFailurePreservationTest(){}
}
