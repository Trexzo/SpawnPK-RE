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
        assertRuntimePrimaryAcrossUncheckedReleaseFailure();
        assertSameRuntimeObjectCleanupPreservesPrimary();
        assertRuntimePrimaryAcrossMixedReleaseFailures();
        assertErrorPrimaryAcrossUncheckedReleaseFailure();
        assertSameIOExceptionObjectCleanupRemainsConnectionScoped();
        assertUncheckedReleaseBecomesPrimaryOverCheckedHandler();
        assertIoFailureRemainsConnectionScoped();
        assertCleanRetirementUnchanged();

        System.out.println(
            "LOCAL_AUX_HTTP_WORKER_FAILURE_PRESERVATION_PASS "+
            "runtimePrimary=true "+
            "errorPrimary=true "+
            "uncheckedReleaseSuppressed=true "+
            "selfSuppressionGuard=true "+
            "retirementSuppressed=true "+
            "residualRetry=true "+
            "secondRetirementSuppressed=true "+
            "uncheckedCleanupFatal=true "+
            "ioConnectionScoped=true "+
            "workerContinues=true "+
            "cleanRetirement=true"
        );
    }

    private static void
        assertRuntimePrimaryAcrossUncheckedReleaseFailure(){
        FakeSocket socket=
            new FakeSocket();
        RuntimeException expected=
            new IllegalStateException(
                "fixture-handler-runtime"
            );
        RuntimeException release=
            new IllegalArgumentException(
                "fixture-release-runtime"
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
                "unchecked release replaced runtime handler primary or retry was skipped",
                observed
            );
    }

    private static void
        assertSameRuntimeObjectCleanupPreservesPrimary(){
        FakeSocket socket=
            new FakeSocket();
        RuntimeException expected=
            new IllegalStateException(
                "fixture-same-runtime"
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
                        throw expected;
                },
                failure->{},
                failure->{}
            );
        }catch(Throwable failure){
            observed=failure;
        }

        if(observed!=expected||
           releases.get()!=2||
           expected.getSuppressed().length!=0)
            throw new AssertionError(
                "same runtime cleanup object triggered self-suppression or replaced primary",
                observed
            );
    }

    private static void
        assertRuntimePrimaryAcrossMixedReleaseFailures(){
        FakeSocket socket=
            new FakeSocket();
        RuntimeException expected=
            new IllegalArgumentException(
                "fixture-handler-runtime-two"
            );
        Error first=
            new AssertionError(
                "fixture-release-error"
            );
        IOException second=
            new IOException(
                "fixture-release-io"
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

                    if(attempt==1)
                        throw first;

                    throw second;
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
                "mixed cleanup failures did not remain behind runtime handler primary",
                observed
            );
    }

    private static void
        assertErrorPrimaryAcrossUncheckedReleaseFailure(){
        FakeSocket socket=
            new FakeSocket();
        Error expected=
            new AssertionError(
                "fixture-handler-error"
            );
        Error release=
            new LinkageError(
                "fixture-release-error"
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
                "unchecked release replaced Error handler primary",
                observed
            );
    }

    private static void
        assertSameIOExceptionObjectCleanupRemainsConnectionScoped()
        throws Exception{
        FakeSocket socket=
            new FakeSocket();
        IOException expected=
            new IOException(
                "fixture-same-io"
            );
        AtomicInteger releases=
            new AtomicInteger();
        AtomicReference<IOException>
            connectionObserved=
                new AtomicReference<>();

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
                    throw expected;
            },
            connectionObserved::set,
            failure->{}
        );

        if(connectionObserved.get()!=expected||
           releases.get()!=2||
           expected.getSuppressed().length!=0)
            throw new AssertionError(
                "same IOException cleanup object triggered self-suppression or changed connection classification"
            );
    }

    private static void
        assertUncheckedReleaseBecomesPrimaryOverCheckedHandler(){
        FakeSocket socket=
            new FakeSocket();
        IOException handlerFailure=
            new IOException(
                "fixture-handler-io-before-runtime-release"
            );
        RuntimeException releaseFailure=
            new IllegalStateException(
                "fixture-release-runtime-primary"
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
                    throw handlerFailure;
                },
                ignored->{
                    if(releases.incrementAndGet()==1)
                        throw releaseFailure;
                },
                failure->{},
                failure->{}
            );
        }catch(Throwable failure){
            observed=failure;
        }

        if(observed!=releaseFailure||
           releases.get()!=2||
           releaseFailure.getSuppressed().length!=1||
           releaseFailure.getSuppressed()[0]!=handlerFailure)
            throw new AssertionError(
                "unchecked cleanup failure was normalized into checked connection failure",
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
