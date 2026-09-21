package spk.local;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

final class LocalServerShutdownCoordinator
    implements AutoCloseable {

    private final Object lifecycleLock=
        new Object();
    private final World world;
    private final ExecutorService pool;
    private final ServerSocket game;
    private final ServerSocket aux;
    private final Set<Socket> activeGameSockets=
        new HashSet<>();
    private final CountDownLatch closed=
        new CountDownLatch(1);

    private boolean closing;

    LocalServerShutdownCoordinator(
        World world,
        ExecutorService pool,
        ServerSocket game,
        ServerSocket aux
    ){
        this.world=
            Objects.requireNonNull(
                world,
                "world"
            );
        this.pool=
            Objects.requireNonNull(
                pool,
                "pool"
            );
        this.game=
            Objects.requireNonNull(
                game,
                "game"
            );
        this.aux=
            Objects.requireNonNull(
                aux,
                "aux"
            );
    }

    boolean submitSession(
        Socket socket,
        Runnable session
    ){
        Objects.requireNonNull(
            socket,
            "socket"
        );
        Objects.requireNonNull(
            session,
            "session"
        );

        synchronized(lifecycleLock){
            if(closing){
                closeQuietly(socket);
                return false;
            }

            activeGameSockets.add(
                socket
            );

            try{
                pool.execute(
                    ()->{
                        try{
                            session.run();
                        }finally{
                            synchronized(lifecycleLock){
                                activeGameSockets.remove(
                                    socket
                                );
                            }

                            closeQuietly(
                                socket
                            );
                        }
                    }
                );
            }catch(RuntimeException error){
                activeGameSockets.remove(
                    socket
                );
                closeQuietly(socket);
                throw error;
            }

            return true;
        }
    }

    boolean submitAuxiliary(
        Runnable task
    ){
        Objects.requireNonNull(
            task,
            "task"
        );

        synchronized(lifecycleLock){
            if(closing)
                return false;

            pool.execute(task);
            return true;
        }
    }

    boolean closing(){
        synchronized(lifecycleLock){
            return closing;
        }
    }

    int activeSessionCount(){
        synchronized(lifecycleLock){
            return activeGameSockets.size();
        }
    }

    @Override public void close(){
        boolean owner=false;

        synchronized(lifecycleLock){
            if(!closing){
                closing=true;
                owner=true;

                closeQuietly(game);
                closeQuietly(aux);

                for(Socket socket:
                        new ArrayList<>(
                            activeGameSockets))
                    closeQuietly(socket);
            }
        }

        if(!owner){
            awaitClosed();
            return;
        }

        try{
            pool.shutdown();

            boolean terminated=
                awaitPool(
                    5,
                    TimeUnit.SECONDS
                );

            if(!terminated){
                pool.shutdownNow();
                awaitPool(
                    1,
                    TimeUnit.SECONDS
                );
            }

            world.close();
        }finally{
            closed.countDown();
        }
    }

    private boolean awaitPool(
        long timeout,
        TimeUnit unit
    ){
        try{
            return pool.awaitTermination(
                timeout,
                unit
            );
        }catch(InterruptedException error){
            Thread.currentThread()
                .interrupt();
            pool.shutdownNow();
            return false;
        }
    }

    private void awaitClosed(){
        try{
            closed.await(
                7,
                TimeUnit.SECONDS
            );
        }catch(InterruptedException error){
            Thread.currentThread()
                .interrupt();
        }
    }

    private static void closeQuietly(
        Socket socket
    ){
        try{
            socket.close();
        }catch(IOException ignored){
        }
    }

    private static void closeQuietly(
        ServerSocket socket
    ){
        try{
            socket.close();
        }catch(IOException ignored){
        }
    }
}
