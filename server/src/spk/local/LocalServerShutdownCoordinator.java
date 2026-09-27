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
    private int sessionFactoryHandoffs;

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
            terminal=closing;

            if(failure==null&&
               !terminal)
                activeGameSockets.add(
                    accepted
                );

            if(failure!=null||
               !terminal){
                gameAcceptHandoffs--;
                lifecycleLock.notifyAll();
            }
        }

        if(failure==null&&
           terminal){
            // Keep the handoff published while physical close runs, but do
            // not hold lifecycleLock across potentially blocking socket I/O.
            closeQuietly(
                accepted
            );

            synchronized(lifecycleLock){
                gameAcceptHandoffs--;
                lifecycleLock.notifyAll();
            }

            return null;
        }

        if(failure!=null){
            if(terminal&&
               failure instanceof IOException)
                return null;

            rethrowAcceptFailure(
                failure
            );
        }

        return accepted;
    }

    void rejectSessionSocket(
        Socket socket
    ){
        Objects.requireNonNull(
            socket,
            "socket"
        );

        retireOwnedSocket(
            socket
        );
    }

    int pendingGameAcceptHandoffs(){
        synchronized(lifecycleLock){
            return gameAcceptHandoffs;
        }
    }

    int pendingSessionFactoryHandoffs(){
        synchronized(lifecycleLock){
            return sessionFactoryHandoffs;
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

        boolean rejectedBeforeFactory=false;

        synchronized(lifecycleLock){
            if(closing)
                rejectedBeforeFactory=true;
            else{
                activeGameSockets.add(
                    socket
                );
                sessionFactoryHandoffs++;
            }
        }

        if(rejectedBeforeFactory){
            retireOwnedSocket(
                socket
            );
            return false;
        }

        final Runnable session;

        try{
            session=
                Objects.requireNonNull(
                    factory.create(),
                    "session"
                );
        }catch(Throwable failure){
            retireOwnedSocket(
                socket
            );

            synchronized(lifecycleLock){
                sessionFactoryHandoffs--;
                lifecycleLock.notifyAll();
            }

            rethrowFactoryFailure(
                failure
            );
            throw new AssertionError(
                "unreachable"
            );
        }

        boolean rejectedAfterFactory=false;
        RuntimeException submissionFailure=null;

        synchronized(lifecycleLock){
            sessionFactoryHandoffs--;
            lifecycleLock.notifyAll();

            if(closing)
                rejectedAfterFactory=true;
            else
                try{
                    pool.execute(
                        ()->{
                            try{
                                session.run();
                            }finally{
                                retireOwnedSocket(
                                    socket
                                );
                            }
                        }
                    );
                }catch(RuntimeException error){
                    submissionFailure=error;
                }
        }

        if(rejectedAfterFactory){
            retireOwnedSocket(
                socket
            );
            return false;
        }

        if(submissionFailure!=null){
            retireOwnedSocket(
                socket
            );
            throw submissionFailure;
        }

        return true;
    }

    private void retireOwnedSocket(
        Socket socket
    ){
        // Keep ownership published until close has completed. A concurrent
        // terminal owner may close the same socket redundantly, but it can
        // never observe the socket as retired while it is still open.
        closeQuietly(
            socket
        );

        synchronized(lifecycleLock){
            activeGameSockets.remove(
                socket
            );
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
        ArrayList<Socket> sockets=
            null;

        synchronized(lifecycleLock){
            if(!closing){
                closing=true;
                owner=true;
                sockets=
                    new ArrayList<>(
                        activeGameSockets
                    );
            }
        }

        if(!owner){
            terminal.awaitAndRethrow();
            return;
        }

        // Publish the terminal fence first, then perform potentially blocking
        // socket closes without lifecycleLock. No new ownership may enter
        // after closing=true, and existing ownership stays published until
        // each physical close has returned.
        closeQuietly(game);
        closeQuietly(aux);

        for(Socket socket:sockets)
            closeQuietly(socket);

        synchronized(lifecycleLock){
            activeGameSockets.removeAll(
                sockets
            );
        }

        awaitPreTerminalHandoffs();

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

    private void awaitPreTerminalHandoffs(){
        boolean interrupted=false;

        synchronized(lifecycleLock){
            while(gameAcceptHandoffs!=0||
                  sessionFactoryHandoffs!=0)
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
