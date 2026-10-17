package spk.local;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

final class LocalServerShutdownCoordinator
    implements AutoCloseable {

    static final int GAME_ACCEPT_POLL_TIMEOUT_MILLIS=250;
    static final int AUXILIARY_ACCEPT_POLL_TIMEOUT_MILLIS=250;

    interface SessionFactory {
        Runnable create()
            throws Exception;
    }

    interface GameSocketAcceptor {
        Socket accept()
            throws IOException;
    }

    interface AuxiliarySocketAcceptor {
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
    private final Set<Socket> activeAuxiliarySockets=
        new HashSet<>();
    private final TerminalCloseState terminal=
        new TerminalCloseState();

    private boolean closing;
    private int gameAcceptHandoffs;
    private int auxiliaryAcceptHandoffs;
    private int sessionFactoryHandoffs;
    private Throwable terminalHandoffFailure;
    private Throwable terminalAuxiliarySocketFailure;
    private Throwable auxiliaryWorkerFailure;
    private Throwable terminalAuxiliaryWorkerFailure;
    private boolean auxiliaryWorkerFailureSurfaced;
    private boolean terminalCompletionPublished;

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

        Throwable pendingWorkerFailure;

        synchronized(lifecycleLock){
            if(closing)
                return null;

            pendingWorkerFailure=
                auxiliaryWorkerFailure;

            if(pendingWorkerFailure==null)
                gameAcceptHandoffs++;
            else
                auxiliaryWorkerFailureSurfaced=true;
        }

        if(pendingWorkerFailure!=null){
            rethrowAcceptFailure(
                pendingWorkerFailure
            );
            throw new AssertionError(
                "unreachable"
            );
        }

        Socket accepted=null;
        Throwable failure=null;

        for(;;){
            try{
                accepted=
                    Objects.requireNonNull(
                        acceptor.accept(),
                        "accepted socket"
                    );
            }catch(Throwable error){
                failure=error;
            }

            if(!(failure instanceof SocketTimeoutException))
                break;

            boolean terminalPoll;
            Throwable pollWorkerFailure;

            synchronized(lifecycleLock){
                terminalPoll=closing;
                pollWorkerFailure=
                    terminalPoll
                        ?null
                        :auxiliaryWorkerFailure;

                if(pollWorkerFailure!=null)
                    auxiliaryWorkerFailureSurfaced=true;

                if(terminalPoll||
                   pollWorkerFailure!=null){
                    gameAcceptHandoffs--;
                    lifecycleLock.notifyAll();
                }
            }

            if(pollWorkerFailure!=null){
                rethrowAcceptFailure(
                    pollWorkerFailure
                );
                throw new AssertionError(
                    "unreachable"
                );
            }

            if(terminalPoll)
                return null;

            accepted=null;
            failure=null;
        }

        boolean terminal;
        Throwable workerFailure;

        synchronized(lifecycleLock){
            terminal=closing;
            workerFailure=
                terminal
                    ?null
                    :auxiliaryWorkerFailure;

            if(workerFailure!=null)
                auxiliaryWorkerFailureSurfaced=true;

            if(failure==null&&
               !terminal&&
               accepted!=null)
                activeGameSockets.add(
                    accepted
                );

            if(failure!=null||
               !terminal||
               workerFailure!=null){
                gameAcceptHandoffs--;
                lifecycleLock.notifyAll();
            }
        }

        if(workerFailure!=null){
            Throwable closeFailure=null;

            if(accepted!=null)
                closeFailure=
                    retireOwnedSocket(
                        accepted
                    );

            if(failure!=null&&
               failure!=workerFailure)
                workerFailure.addSuppressed(
                    failure
                );

            if(closeFailure!=null&&
               closeFailure!=workerFailure)
                workerFailure.addSuppressed(
                    closeFailure
                );

            rethrowAcceptFailure(
                workerFailure
            );
            throw new AssertionError(
                "unreachable"
            );
        }

        if(failure==null&&
           terminal){
            // A pre-fence accept that completes after terminal publication is
            // still owned by this handoff until its socket-close attempt
            // finishes. Do not inject it into activeGameSockets: terminal
            // completion may already be in progress if the listener itself
            // failed to close and could not unblock this accept.
            Throwable closeFailure=null;

            try{
                closeFailure=
                    closeUnownedSocket(
                        accepted
                    );
            }finally{
                synchronized(lifecycleLock){
                    if(closeFailure!=null){
                        recordTerminalHandoffFailureLocked(
                            closeFailure
                        );

                        if(!accepted.isClosed())
                            activeGameSockets.add(
                                accepted
                            );
                    }

                    gameAcceptHandoffs--;
                    lifecycleLock.notifyAll();
                }
            }

            if(closeFailure!=null)
                rethrowAcceptFailure(
                    closeFailure
                );

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

    private void recordTerminalHandoffFailureLocked(
        Throwable failure
    ){
        if(failure==null)
            return;

        if(terminalHandoffFailure==null)
            terminalHandoffFailure=
                failure;
        else if(terminalHandoffFailure!=
                failure)
            terminalHandoffFailure
                .addSuppressed(
                    failure
                );
    }

    void rejectSessionSocket(
        Socket socket
    )throws IOException{
        Objects.requireNonNull(
            socket,
            "socket"
        );

        Throwable closeFailure=
            retireOwnedSocket(
                socket
            );

        if(closeFailure!=null)
            rethrowSocketCloseFailure(
                closeFailure
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
            Throwable closeFailure=
                closeUnownedSocket(
                    socket
                );

            if(closeFailure!=null)
                rethrowFactoryFailure(
                    closeFailure
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
            Throwable closeFailure=
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
                                Throwable closeFailure=
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
            Throwable closeFailure=
                retireOwnedSocket(
                    socket
                );

            if(closeFailure!=null)
                rethrowFactoryFailure(
                    closeFailure
                );

            return false;
        }

        if(submissionFailure!=null){
            Throwable closeFailure=
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

    private Throwable retireOwnedSocket(
        Socket socket
    ){
        // Ownership is retired only after the Socket itself reports closed.
        // A checked close failure that leaves it open therefore remains
        // coordinator-owned for a later terminal retry.
        Throwable failure=
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

    private static Throwable closeUnownedSocket(
        Socket socket
    ){
        Throwable failure=
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

    private Throwable retryOwnedSocketsForTerminal(){
        ArrayList<Socket> remaining;

        synchronized(lifecycleLock){
            remaining=
                new ArrayList<>(
                    activeGameSockets
                );
        }

        Throwable primary=null;

        for(Socket socket:remaining){
            Throwable failure=
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

    private static Throwable combineFailure(
        Throwable primary,
        Throwable next
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

    private static void rethrowSocketCloseFailure(
        Throwable failure
    )throws IOException{
        if(failure instanceof IOException)
            throw (IOException)failure;
        if(failure instanceof RuntimeException)
            throw (RuntimeException)failure;
        if(failure instanceof Error)
            throw (Error)failure;

        throw new IOException(
            "session socket close failed",
            failure
        );
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

    Socket acceptAuxiliarySocket()
        throws IOException{
        return acceptAuxiliarySocket(
            aux::accept
        );
    }

    Socket acceptAuxiliarySocket(
        AuxiliarySocketAcceptor acceptor
    )throws IOException{
        Objects.requireNonNull(
            acceptor,
            "acceptor"
        );

        synchronized(lifecycleLock){
            if(closing)
                return null;

            auxiliaryAcceptHandoffs++;
        }

        Socket accepted=null;
        Throwable failure=null;

        for(;;){
            try{
                accepted=
                    Objects.requireNonNull(
                        acceptor.accept(),
                        "accepted auxiliary socket"
                    );
            }catch(Throwable error){
                failure=error;
            }

            if(!(failure instanceof SocketTimeoutException))
                break;

            boolean terminalPoll;

            synchronized(lifecycleLock){
                terminalPoll=closing;

                if(terminalPoll){
                    auxiliaryAcceptHandoffs--;
                    lifecycleLock.notifyAll();
                }
            }

            if(terminalPoll)
                return null;

            accepted=null;
            failure=null;
        }

        boolean terminal;

        synchronized(lifecycleLock){
            terminal=closing;

            if(failure==null&&
               !terminal)
                activeAuxiliarySockets.add(
                    accepted
                );

            if(failure!=null||
               !terminal){
                auxiliaryAcceptHandoffs--;
                lifecycleLock.notifyAll();
            }
        }

        if(failure==null&&
           terminal){
            Throwable closeFailure=null;

            try{
                closeFailure=
                    retireAuxiliaryHandoffSocket(
                        accepted
                    );
            }finally{
                synchronized(lifecycleLock){
                    if(closeFailure!=null){
                        recordTerminalAuxiliarySocketFailureLocked(
                            closeFailure
                        );

                        if(!accepted.isClosed())
                            activeAuxiliarySockets.add(
                                accepted
                            );
                    }

                    auxiliaryAcceptHandoffs--;
                    lifecycleLock.notifyAll();
                }
            }

            if(closeFailure!=null)
                rethrowAcceptFailure(
                    closeFailure
                );

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

    int pendingAuxiliaryAcceptHandoffs(){
        synchronized(lifecycleLock){
            return auxiliaryAcceptHandoffs;
        }
    }

    void releaseAuxiliarySocket(
        Socket socket
    )throws IOException{
        Objects.requireNonNull(
            socket,
            "socket"
        );

        Throwable failure=
            retireOwnedAuxiliarySocket(
                socket
            );

        if(failure==null)
            return;

        synchronized(lifecycleLock){
            if(closing)
                recordTerminalAuxiliarySocketFailureLocked(
                    failure
                );
        }

        rethrowSocketCloseFailure(
            failure
        );
    }

    int activeAuxiliarySocketCount(){
        synchronized(lifecycleLock){
            return activeAuxiliarySockets.size();
        }
    }

    private static Throwable retireAuxiliaryHandoffSocket(
        Socket socket
    ){
        Throwable failure=
            closeSocket(
                socket
            );

        if(!socket.isClosed()&&
           failure==null)
            failure=
                new IOException(
                    "terminal auxiliary handoff socket close returned without closing socket"
                );

        return failure;
    }

    private Throwable retireOwnedAuxiliarySocket(
        Socket socket
    ){
        Throwable failure=
            closeSocket(
                socket
            );

        if(socket.isClosed()){
            synchronized(lifecycleLock){
                activeAuxiliarySockets.remove(
                    socket
                );
            }
        }else if(failure==null)
            failure=
                new IOException(
                    "auxiliary socket close returned without closing socket"
                );

        return failure;
    }

    private Throwable retryOwnedAuxiliarySocketsForTerminal(){
        ArrayList<Socket> remaining;

        synchronized(lifecycleLock){
            remaining=
                new ArrayList<>(
                    activeAuxiliarySockets
                );
        }

        Throwable primary=null;

        for(Socket socket:remaining)
            primary=
                combineFailure(
                    primary,
                    retireOwnedAuxiliarySocket(
                        socket
                    )
                );

        synchronized(lifecycleLock){
            if(!activeAuxiliarySockets.isEmpty()&&
               primary==null)
                primary=
                    new IOException(
                        "auxiliary socket retirement incomplete count="+
                        activeAuxiliarySockets.size()
                    );
        }

        return primary;
    }

    private void recordTerminalAuxiliarySocketFailureLocked(
        Throwable failure
    ){
        if(failure==null)
            return;

        if(terminalAuxiliarySocketFailure==null)
            terminalAuxiliarySocketFailure=
                failure;
        else if(terminalAuxiliarySocketFailure!=
                failure)
            terminalAuxiliarySocketFailure
                .addSuppressed(
                    failure
                );
    }

    private Throwable drainTerminalAuxiliarySocketFailure(){
        synchronized(lifecycleLock){
            Throwable failure=
                terminalAuxiliarySocketFailure;
            terminalAuxiliarySocketFailure=null;
            return failure;
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

            pool.execute(
                ()->{
                    try{
                        task.run();
                    }catch(Throwable failure){
                        publishAuxiliaryWorkerFailure(
                            failure
                        );

                        WorldCloseSequence.rethrow(
                            failure
                        );
                    }
                }
            );
            return true;
        }
    }

    void publishAuxiliaryWorkerFailure(
        Throwable failure
    ){
        Objects.requireNonNull(
            failure,
            "failure"
        );

        boolean wakeGame=false;

        synchronized(lifecycleLock){
            if(closing){
                if(terminalCompletionPublished){
                    Throwable published=
                        terminal.failure();

                    if(published!=null&&
                       published!=failure)
                        published.addSuppressed(
                            failure
                        );
                    else
                        recordTerminalAuxiliaryWorkerFailureLocked(
                            failure
                        );
                }else
                    recordTerminalAuxiliaryWorkerFailureLocked(
                        failure
                    );
            }else if(auxiliaryWorkerFailure==null){
                auxiliaryWorkerFailure=failure;
                wakeGame=true;
            }else if(auxiliaryWorkerFailure!=failure)
                auxiliaryWorkerFailure.addSuppressed(
                    failure
                );
        }

        if(!wakeGame)
            return;

        Throwable wakeFailure=
            closeServerSocket(
                game,
                "game"
            );

        if(!game.isClosed())
            wakeFailure=
                combineFailure(
                    wakeFailure,
                    closeServerSocket(
                        game,
                        "game"
                    )
                );

        if(wakeFailure!=null&&
           wakeFailure!=failure)
            failure.addSuppressed(
                wakeFailure
            );
    }

    private void recordTerminalAuxiliaryWorkerFailureLocked(
        Throwable failure
    ){
        if(failure==null)
            return;

        if(terminalAuxiliaryWorkerFailure==null)
            terminalAuxiliaryWorkerFailure=failure;
        else if(terminalAuxiliaryWorkerFailure!=failure)
            terminalAuxiliaryWorkerFailure.addSuppressed(
                failure
            );
    }

    private Throwable drainTerminalAuxiliaryWorkerFailure(){
        synchronized(lifecycleLock){
            Throwable failure=
                terminalAuxiliaryWorkerFailure;
            terminalAuxiliaryWorkerFailure=null;
            return failure;
        }
    }

    private Throwable reconcileAuxiliaryWorkerAfterPool(
        Throwable initialFailure
    ){
        return combineFailure(
            initialFailure,
            drainTerminalAuxiliaryWorkerFailure()
        );
    }

    boolean claimAuxiliaryResponseTimeout(
        BooleanSupplier commit
    ){
        Objects.requireNonNull(
            commit,
            "commit"
        );

        synchronized(lifecycleLock){
            if(closing)
                return false;

            return commit.getAsBoolean();
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
        ArrayList<Socket> auxiliarySockets=
            null;
        Throwable initialAuxiliaryWorkerFailure=
            null;

        synchronized(lifecycleLock){
            if(!closing){
                closing=true;
                owner=true;
                sockets=
                    new ArrayList<>(
                        activeGameSockets
                    );
                auxiliarySockets=
                    new ArrayList<>(
                        activeAuxiliarySockets
                    );

                if(!auxiliaryWorkerFailureSurfaced)
                    initialAuxiliaryWorkerFailure=
                        auxiliaryWorkerFailure;
            }
        }

        if(!owner){
            terminal.await();

            Throwable listenerRetryFailure=
                retryOpenListeners();

            // A listener that recovered only on this repeated close can now
            // safely unblock and retire the pre-fence accept handoff. Rejoin
            // that barrier before draining its late failure/socket ownership.
            awaitPreTerminalHandoffs(
                game.isClosed()
            );

            Throwable lateHandoffFailure;

            synchronized(lifecycleLock){
                lateHandoffFailure=
                    terminalHandoffFailure;
                terminalHandoffFailure=null;
            }

            Throwable residualFailure=
                combineFailure(
                    listenerRetryFailure,
                    lateHandoffFailure
                );
            residualFailure=
                combineFailure(
                    residualFailure,
                    retryOwnedSocketsForTerminal()
                );
            residualFailure=
                combineFailure(
                    residualFailure,
                    drainTerminalAuxiliarySocketFailure()
                );
            residualFailure=
                combineFailure(
                    residualFailure,
                    retryOwnedAuxiliarySocketsForTerminal()
                );
            residualFailure=
                combineFailure(
                    residualFailure,
                    drainTerminalAuxiliaryWorkerFailure()
                );
            Throwable terminalFailure=
                terminal.failure();

            if(residualFailure!=null&&
               terminalFailure!=null&&
               residualFailure!=terminalFailure)
                terminalFailure.addSuppressed(
                    residualFailure
                );

            if(terminalFailure!=null)
                WorldCloseSequence.rethrow(
                    terminalFailure
                );

            if(residualFailure!=null)
                WorldCloseSequence.rethrow(
                    new IllegalStateException(
                        "residual session socket retirement failed",
                        residualFailure
                    )
                );

            return;
        }

        // Publish the terminal fence first, then perform potentially blocking
        // listener/socket closes without lifecycleLock.
        Throwable gameListenerFailure=
            closeServerSocket(
                game,
                "game"
            );
        Throwable auxListenerFailure=
            closeServerSocket(
                aux,
                "aux"
            );

        // Retry physical listener retirement once without erasing the first
        // failure. Resource recovery and diagnostic truth are separate.
        if(!game.isClosed())
            gameListenerFailure=
                combineFailure(
                    gameListenerFailure,
                    closeServerSocket(
                        game,
                        "game"
                    )
                );

        if(!aux.isClosed())
            auxListenerFailure=
                combineFailure(
                    auxListenerFailure,
                    closeServerSocket(
                        aux,
                        "aux"
                    )
                );

        final Throwable terminalGameListenerFailure=
            gameListenerFailure;
        final Throwable terminalAuxListenerFailure=
            auxListenerFailure;

        // If the game listener is physically closed after the bounded retry,
        // its blocked accept is expected to retire and success semantics still
        // wait for it. If it remains failed-open, waiting here could deadlock
        // the terminal owner forever; terminal failure is published instead.
        boolean waitGameAcceptHandoffs=
            game.isClosed();

        // First close pass covers active ownership present at terminal
        // publication. Failed-open sockets remain in activeGameSockets. A
        // later successful retry may retire the resource, but it must not
        // erase the original terminal close failure from terminal history.
        Throwable firstSocketCloseFailure=null;

        for(Socket socket:sockets)
            firstSocketCloseFailure=
                combineFailure(
                    firstSocketCloseFailure,
                    retireOwnedSocket(
                        socket
                    )
                );

        Throwable firstAuxiliarySocketCloseFailure=null;

        for(Socket socket:auxiliarySockets)
            firstAuxiliarySocketCloseFailure=
                combineFailure(
                    firstAuxiliarySocketCloseFailure,
                    retireOwnedAuxiliarySocket(
                        socket
                    )
                );

        awaitPreTerminalHandoffs(
            waitGameAcceptHandoffs
        );

        Throwable handoffFailure;

        synchronized(lifecycleLock){
            handoffFailure=
                terminalHandoffFailure;
            terminalHandoffFailure=null;
        }

        // A session-factory handoff may have completed after the first
        // snapshot, and a first active-socket close may have failed. Retry
        // every still-owned active socket once after the awaited handoffs.
        Throwable socketRetirementFailure=
            combineFailure(
                firstSocketCloseFailure,
                retryOwnedSocketsForTerminal()
            );

        final Throwable initialAuxiliarySocketFailure=
            firstAuxiliarySocketCloseFailure;
        final Throwable initialWorkerFailure=
            initialAuxiliaryWorkerFailure;

        Throwable failure=null;

        try{
            failure=
                WorldCloseSequence.run(
                    ()->throwIfCloseFailed(
                        "game listener close failed",
                        terminalGameListenerFailure
                    ),
                    ()->throwIfCloseFailed(
                        "aux listener close failed",
                        terminalAuxListenerFailure
                    ),
                    ()->throwIfCloseFailed(
                        "terminal accept handoff close failed",
                        handoffFailure
                    ),
                    ()->throwIfCloseFailed(
                        "session socket retirement failed",
                        socketRetirementFailure
                    ),
                    this::closePool,
                    ()->WorldCloseSequence.rethrow(
                        reconcileAuxiliaryWorkerAfterPool(
                            initialWorkerFailure
                        )
                    ),
                    ()->throwIfCloseFailed(
                        "auxiliary socket retirement failed",
                        reconcileAuxiliaryAfterPool(
                            initialAuxiliarySocketFailure
                        )
                    ),
                    world::close
                );
        }finally{
            synchronized(lifecycleLock){
                Throwable lateWorkerFailure=
                    terminalAuxiliaryWorkerFailure;
                terminalAuxiliaryWorkerFailure=null;

                failure=
                    combineFailure(
                        failure,
                        lateWorkerFailure
                    );

                terminal.complete(
                    failure
                );
                terminalCompletionPublished=true;
            }
        }

        WorldCloseSequence.rethrow(
            failure
        );
    }

    private Throwable reconcileAuxiliaryAfterPool(
        Throwable initialFailure
    ){
        Throwable failure=
            combineFailure(
                initialFailure,
                drainTerminalAuxiliarySocketFailure()
            );

        failure=
            combineFailure(
                failure,
                retryOwnedAuxiliarySocketsForTerminal()
            );

        synchronized(lifecycleLock){
            if(auxiliaryAcceptHandoffs!=0)
                failure=
                    combineFailure(
                        failure,
                        new IOException(
                            "auxiliary accept handoff unresolved count="+
                            auxiliaryAcceptHandoffs
                        )
                    );
        }

        return failure;
    }

    private void awaitPreTerminalHandoffs(
        boolean waitGameAcceptHandoffs
    ){
        boolean interrupted=false;

        synchronized(lifecycleLock){
            while((waitGameAcceptHandoffs&&
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

    private static void throwIfCloseFailed(
        String message,
        Throwable failure
    ){
        if(failure==null)
            return;

        throw new IllegalStateException(
            message,
            failure
        );
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

    private Throwable retryOpenListeners(){
        Throwable failure=null;

        if(!game.isClosed())
            failure=
                combineFailure(
                    failure,
                    closeServerSocket(
                        game,
                        "game"
                    )
                );

        if(!aux.isClosed())
            failure=
                combineFailure(
                    failure,
                    closeServerSocket(
                        aux,
                        "aux"
                    )
                );

        return failure;
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
            terminated=
                awaitPool(
                    1,
                    TimeUnit.SECONDS
                );
        }

        if(!terminated&&
           !pool.isTerminated())
            throw new IllegalStateException(
                "session executor did not terminate after forced shutdown"
            );
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

    private static Throwable closeSocket(
        Socket socket
    ){
        try{
            socket.close();
            return null;
        }catch(Throwable failure){
            return failure;
        }
    }

    private static Throwable closeServerSocket(
        ServerSocket socket,
        String label
    ){
        Throwable failure=null;

        try{
            socket.close();
        }catch(Throwable error){
            failure=error;
        }

        if(!socket.isClosed()&&
           failure==null)
            failure=
                new IOException(
                    label+
                    " listener close returned without closing listener"
                );

        return failure;
    }
}
