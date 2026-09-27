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

    interface SessionFactory {
        Runnable create()
            throws Exception;
    }

    interface GameSocketAcceptor {
        Socket accept()
            throws IOException;
    }

    private final Object lifecycleLock=
        new Object();
    private final World world;
    private final ExecutorService pool;
    private final ServerSocket game;
    private final ServerSocket aux;
    private final Set<Socket> activeGameSockets=
        new HashSet<>();
    private final TerminalCloseState terminal=
        new TerminalCloseState();

    private boolean closing;
    private int gameAcceptHandoffs;

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

    Socket acceptGameSocket()
        throws IOException{
        return acceptGameSocket(
            game::accept
        );
    }

    Socket acceptGameSocket(
        GameSocketAcceptor acceptor
    )throws IOException{
        Objects.requireNonNull(
            acceptor,
            "acceptor"
        );

        synchronized(lifecycleLock){
            if(closing)
                return null;

            gameAcceptHandoffs++;
        }

        Socket accepted=null;
        Throwable failure=null;

        try{
            accepted=
                Objects.requireNonNull(
                    acceptor.accept(),
                    "accepted socket"
                );
        }catch(Throwable error){
            failure=error;
        }

        boolean terminal;

        synchronized(lifecycleLock){
            gameAcceptHandoffs--;
            terminal=closing;

            if(failure==null){
                if(terminal)
                    closeQuietly(
                        accepted
                    );
                else
                    activeGameSockets.add(
                        accepted
                    );
            }

            lifecycleLock.notifyAll();
        }

        if(failure!=null){
            if(terminal&&
               failure instanceof IOException)
                return null;

            rethrowAcceptFailure(
                failure
            );
        }

        return terminal
            ?null
            :accepted;
    }

    void rejectSessionSocket(
        Socket socket
    ){
        Objects.requireNonNull(
            socket,
            "socket"
        );

        synchronized(lifecycleLock){
            activeGameSockets.remove(
                socket
            );
        }

        closeQuietly(
            socket
        );
    }

    int pendingGameAcceptHandoffs(){
        synchronized(lifecycleLock){
            return gameAcceptHandoffs;
        }
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

        try{
            return submitSession(
                socket,
                (SessionFactory)
                    ()->session
            );
        }catch(RuntimeException error){
            throw error;
        }catch(Error error){
            throw error;
        }catch(Exception error){
            throw new RuntimeException(
                error
            );
        }
    }

    boolean submitSession(
        Socket socket,
        SessionFactory factory
    )throws Exception{
        Objects.requireNonNull(
            socket,
            "socket"
        );
        Objects.requireNonNull(
            factory,
            "factory"
        );

        synchronized(lifecycleLock){
            if(closing){
                activeGameSockets.remove(
                    socket
                );
                closeQuietly(socket);
                return false;
            }

            activeGameSockets.add(
                socket
            );
        }

        final Runnable session;

        try{
            session=
                Objects.requireNonNull(
                    factory.create(),
                    "session"
                );
        }catch(Throwable failure){
            synchronized(lifecycleLock){
                activeGameSockets.remove(
                    socket
                );
            }

            closeQuietly(socket);
            rethrowFactoryFailure(
                failure
            );
            throw new AssertionError(
                "unreachable"
            );
        }

        synchronized(lifecycleLock){
            if(closing){
                activeGameSockets.remove(
                    socket
                );
                closeQuietly(socket);
                return false;
            }

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

    private static void rethrowFactoryFailure(
        Throwable failure
    )throws Exception{
        if(failure instanceof Exception)
            throw (Exception)failure;
        if(failure instanceof Error)
            throw (Error)failure;

        throw new RuntimeException(
            failure
        );
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

                activeGameSockets.clear();
            }
        }

        if(!owner){
            terminal.awaitAndRethrow();
            return;
        }

        awaitGameAcceptHandoffs();

        Throwable failure=null;

        try{
            failure=
                WorldCloseSequence.run(
                    this::closePool,
                    world::close
                );
        }finally{
            terminal.complete(
                failure
            );
        }

        WorldCloseSequence.rethrow(
            failure
        );
    }

    private void awaitGameAcceptHandoffs(){
        boolean interrupted=false;

        synchronized(lifecycleLock){
            while(gameAcceptHandoffs!=0)
                try{
                    lifecycleLock.wait();
                }catch(InterruptedException error){
                    interrupted=true;
                }
        }

        if(interrupted)
            Thread.currentThread()
                .interrupt();
    }

    private static void rethrowAcceptFailure(
        Throwable failure
    )throws IOException{
        if(failure instanceof IOException)
            throw (IOException)failure;
        if(failure instanceof RuntimeException)
            throw (RuntimeException)failure;
        if(failure instanceof Error)
            throw (Error)failure;

        throw new IOException(
            "game socket accept failed",
            failure
        );
    }

    private void closePool(){
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
