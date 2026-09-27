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
            // This socket completed a pre-fence accept after closing was
            // published. Keep explicit ownership until physical close really
            // succeeds so terminal close can retry a checked close failure.
            synchronized(lifecycleLock){
                activeGameSockets.add(
                    accepted
                );
            }

            IOException closeFailure;

            try{
                closeFailure=
                    retireOwnedSocket(
                        accepted
                    );
            }finally{
                synchronized(lifecycleLock){
                    gameAcceptHandoffs--;
                    lifecycleLock.notifyAll();
                }
            }

            if(closeFailure!=null)
                throw closeFailure;

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
    )throws IOException{
        Objects.requireNonNull(
            socket,
            "socket"
        );

        IOException closeFailure=
            retireOwnedSocket(
                socket
            );

        if(closeFailure!=null)
            throw closeFailure;
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
            IOException closeFailure=
                closeUnownedSocket(
                    socket
                );

            if(closeFailure!=null)
                throw closeFailure;

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
            IOException closeFailure=
                retireOwnedSocket(
                    socket
                );

            if(closeFailure!=null&&
               closeFailure!=failure)
                failure.addSuppressed(
                    closeFailure
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
                                IOException closeFailure=
                                    retireOwnedSocket(
                                        socket
                                    );

                                if(closeFailure!=null)
                                    System.err.println(
                                        "[local] session socket close failed; ownership retained="+
                                        !socket.isClosed()+
                                        " error="+
                                        closeFailure
                                    );
                            }
                        }
                    );
                }catch(RuntimeException error){
                    submissionFailure=error;
                }
        }

        if(rejectedAfterFactory){
            IOException closeFailure=
                retireOwnedSocket(
                    socket
                );

            if(closeFailure!=null)
                throw closeFailure;

            return false;
        }

        if(submissionFailure!=null){
            IOException closeFailure=
                retireOwnedSocket(
                    socket
                );

            if(closeFailure!=null&&
               closeFailure!=submissionFailure)
                submissionFailure.addSuppressed(
                    closeFailure
                );

            throw submissionFailure;
        }

        return true;
    }

    private IOException retireOwnedSocket(
        Socket socket
    ){
        // Ownership is retired only after the Socket itself reports closed.
        // A checked close failure that leaves it open therefore remains
        // coordinator-owned for a later terminal retry.
        IOException failure=
            closeSocket(
                socket
            );

        if(socket.isClosed()){
            synchronized(lifecycleLock){
                activeGameSockets.remove(
                    socket
                );
            }
        }else if(failure==null)
            failure=
                new IOException(
                    "session socket close returned without closing socket"
                );

        return failure;
    }

    private static IOException closeUnownedSocket(
        Socket socket
    ){
        IOException failure=
            closeSocket(
                socket
            );

        if(!socket.isClosed()&&
           failure==null)
            failure=
                new IOException(
                    "rejected session socket close returned without closing socket"
                );

        return failure;
    }

    private IOException retryOwnedSocketsForTerminal(){
        ArrayList<Socket> remaining;

        synchronized(lifecycleLock){
            remaining=
                new ArrayList<>(
                    activeGameSockets
                );
        }

        IOException primary=null;

        for(Socket socket:remaining){
            IOException failure=
                retireOwnedSocket(
                    socket
                );

            if(failure==null)
                continue;

            if(primary==null)
                primary=failure;
            else if(failure!=primary)
                primary.addSuppressed(
                    failure
                );
        }

        synchronized(lifecycleLock){
            if(!activeGameSockets.isEmpty()&&
               primary==null)
                primary=
                    new IOException(
                        "session socket retirement incomplete count="+
                        activeGameSockets.size()
                    );
        }

        return primary;
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
        // listener/socket closes without lifecycleLock. Listener close failure
        // is terminal evidence: swallowing it can strand a blocked accept
        // handoff forever.
        IOException listenerCloseFailure=
            closeServerSocket(
                game
            );
        listenerCloseFailure=
            combineIOException(
                listenerCloseFailure,
                closeServerSocket(
                    aux
                )
            );

        // Retry a failed-open listener once. If the game listener still is
        // not closed after this bounded retry, terminal failure semantics must
        // not wait forever for accept() to unblock.
        if(!game.isClosed())
            listenerCloseFailure=
                combineIOException(
                    listenerCloseFailure,
                    closeServerSocket(
                        game
                    )
                );
        if(!aux.isClosed())
            listenerCloseFailure=
                combineIOException(
                    listenerCloseFailure,
                    closeServerSocket(
                        aux
                    )
                );

        // First close pass covers ownership present at terminal publication.
        // Failed-open sockets remain in activeGameSockets.
        for(Socket socket:sockets)
            retireOwnedSocket(
                socket
            );

        awaitPreTerminalHandoffs(
            game.isClosed()
        );

        // A handoff may have completed after the first snapshot, and a first
        // close may have failed. Retry every still-owned socket once after the
        // safely-waitable pre-fence handoffs have retired.
        IOException socketRetirementFailure=
            retryOwnedSocketsForTerminal();

        final IOException terminalListenerFailure=
            listenerCloseFailure;
        Throwable failure=null;

        try{
            failure=
                WorldCloseSequence.run(
                    ()->{
                        if(terminalListenerFailure!=null)
                            throw new IllegalStateException(
                                "server listener close failed",
                                terminalListenerFailure
                            );
                    },
                    ()->{
                        if(socketRetirementFailure!=null)
                            throw new IllegalStateException(
                                "session socket retirement failed",
                                socketRetirementFailure
                            );
                    },
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

    private void awaitPreTerminalHandoffs(
        boolean waitForGameAccepts
    ){
        boolean interrupted=false;

        synchronized(lifecycleLock){
            while((waitForGameAccepts&&
                   gameAcceptHandoffs!=0)||
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

    private static IOException closeSocket(
        Socket socket
    ){
        try{
            socket.close();
            return null;
        }catch(IOException failure){
            return failure;
        }
    }

    private static IOException closeServerSocket(
        ServerSocket socket
    ){
        try{
            socket.close();
            return null;
        }catch(IOException failure){
            return failure;
        }
    }

    private static IOException combineIOException(
        IOException primary,
        IOException next
    ){
        if(next==null)
            return primary;

        if(primary==null)
            return next;

        if(primary!=next)
            primary.addSuppressed(
                next
            );

        return primary;
    }
}
