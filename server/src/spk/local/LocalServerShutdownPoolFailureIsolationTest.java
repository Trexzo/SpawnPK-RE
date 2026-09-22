package spk.local;

import java.net.ServerSocket;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

public final class LocalServerShutdownPoolFailureIsolationTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(
                GameClock.TICK_MILLIS
            );

        SecurityException poolFailure=
            new SecurityException(
                "pool-shutdown-denied"
            );

        FailingShutdownExecutor pool=
            new FailingShutdownExecutor(
                poolFailure
            );

        try(
            ServerSocket game=new ServerSocket();
            ServerSocket aux=new ServerSocket()
        ){
            LocalServerShutdownCoordinator shutdown=
                new LocalServerShutdownCoordinator(
                    world,
                    pool,
                    game,
                    aux
                );

            Throwable first=null;

            try{
                shutdown.close();
            }catch(Throwable failure){
                first=failure;
            }

            if(first!=poolFailure)
                throw new AssertionError(
                    "pool failure did not remain primary"
                );

            if(!world.closed())
                throw new AssertionError(
                    "World close was skipped after pool teardown failure"
                );

            Throwable repeated=null;

            try{
                shutdown.close();
            }catch(Throwable failure){
                repeated=failure;
            }

            if(repeated!=poolFailure)
                throw new AssertionError(
                    "repeated shutdown caller did not observe exact terminal failure"
                );
        }finally{
            if(!world.closed())
                world.close();
        }

        System.out.println(
            "SERVER_SHUTDOWN_POOL_FAILURE_WORLD_CLOSE_PASS "+
            "worldCloseAttempted=true "+
            "primaryPoolFailurePreserved=true "+
            "repeatedCallerSameFailure=true"
        );
    }

    private static final class FailingShutdownExecutor
        extends AbstractExecutorService {

        private final RuntimeException failure;
        private boolean shutdown;

        private FailingShutdownExecutor(
            RuntimeException failure
        ){
            this.failure=failure;
        }

        @Override public void shutdown(){
            shutdown=true;
            throw failure;
        }

        @Override public List<Runnable> shutdownNow(){
            shutdown=true;
            return Collections.emptyList();
        }

        @Override public boolean isShutdown(){
            return shutdown;
        }

        @Override public boolean isTerminated(){
            return false;
        }

        @Override public boolean awaitTermination(
            long timeout,
            TimeUnit unit
        ){
            return false;
        }

        @Override public void execute(
            Runnable command
        ){
            throw new UnsupportedOperationException();
        }
    }

    private LocalServerShutdownPoolFailureIsolationTest(){}
}
