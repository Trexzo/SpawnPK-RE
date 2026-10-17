package spk.local;

import java.net.ServerSocket;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

public final class LocalServerShutdownPoolFailureIsolationTest {
    public static void main(String[] args)throws Exception{
        assertThrownPoolFailureIsolation();
        assertResidualPoolNonterminationPublished();

        System.out.println(
            "SERVER_SHUTDOWN_POOL_FAILURE_WORLD_CLOSE_PASS "+
            "worldCloseAttempted=true "+
            "primaryPoolFailurePreserved=true "+
            "repeatedCallerSameFailure=true "+
            "forcedShutdownAttempted=true "+
            "forcedAwaitObserved=true "+
            "residualNonterminationPublished=true"
        );
    }

    private static void assertThrownPoolFailureIsolation()
        throws Exception{
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
    }

    private static void assertResidualPoolNonterminationPublished()
        throws Exception{
        World world=
            World.isolatedForTest(
                GameClock.TICK_MILLIS
            );
        NeverTerminatesExecutor pool=
            new NeverTerminatesExecutor();

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

            if(!(first instanceof
                    IllegalStateException)||
               first.getMessage()==null||
               !first.getMessage()
                    .contains(
                        "did not terminate"
                    ))
                throw new AssertionError(
                    "residual pool nontermination was not terminally published",
                    first
                );

            if(pool.shutdownCalls!=1||
               pool.shutdownNowCalls!=1||
               pool.awaitCalls!=2)
                throw new AssertionError(
                    "pool shutdown sequence mismatch shutdown="+
                    pool.shutdownCalls+
                    " shutdownNow="+
                    pool.shutdownNowCalls+
                    " awaits="+
                    pool.awaitCalls
                );

            if(!world.closed())
                throw new AssertionError(
                    "World close was skipped after residual pool nontermination"
                );

            Throwable repeated=null;

            try{
                shutdown.close();
            }catch(Throwable failure){
                repeated=failure;
            }

            if(repeated!=first)
                throw new AssertionError(
                    "repeated shutdown did not observe same residual pool failure"
                );
        }finally{
            if(!world.closed())
                world.close();
        }
    }

    private static final class NeverTerminatesExecutor
        extends AbstractExecutorService {

        private int shutdownCalls;
        private int shutdownNowCalls;
        private int awaitCalls;

        @Override public void shutdown(){
            shutdownCalls++;
        }

        @Override public List<Runnable> shutdownNow(){
            shutdownNowCalls++;
            return Collections.emptyList();
        }

        @Override public boolean isShutdown(){
            return shutdownCalls>0||
                shutdownNowCalls>0;
        }

        @Override public boolean isTerminated(){
            return false;
        }

        @Override public boolean awaitTermination(
            long timeout,
            TimeUnit unit
        ){
            awaitCalls++;
            return false;
        }

        @Override public void execute(
            Runnable command
        ){
            throw new UnsupportedOperationException();
        }
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
