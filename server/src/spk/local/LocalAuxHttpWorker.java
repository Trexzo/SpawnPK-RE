package spk.local;

import java.io.IOException;
import java.net.Socket;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class LocalAuxHttpWorker {
    interface Acceptor {
        Socket accept()
            throws IOException;
    }

    interface Handler {
        void handle(
            Socket socket
        )throws IOException;
    }

    interface Releaser {
        void release(
            Socket socket
        )throws IOException;
    }

    static void run(
        BooleanSupplier serverClosed,
        Acceptor acceptor,
        Handler handler,
        Releaser releaser,
        Consumer<IOException> connectionFailure,
        Consumer<IOException> retirementFailure
    ){
        Objects.requireNonNull(
            serverClosed,
            "serverClosed"
        );
        Objects.requireNonNull(
            acceptor,
            "acceptor"
        );
        Objects.requireNonNull(
            handler,
            "handler"
        );
        Objects.requireNonNull(
            releaser,
            "releaser"
        );
        Objects.requireNonNull(
            connectionFailure,
            "connectionFailure"
        );
        Objects.requireNonNull(
            retirementFailure,
            "retirementFailure"
        );

        while(!serverClosed.getAsBoolean()){
            Socket socket=null;
            Throwable primary=null;

            try{
                socket=
                    acceptor.accept();

                if(socket==null)
                    break;

                try{
                    handler.handle(
                        socket
                    );
                }catch(IOException|
                       RuntimeException|
                       Error failure){
                    primary=failure;
                }

                try{
                    releaser.release(
                        socket
                    );
                    socket=null;
                }catch(Throwable releaseFailure){
                    primary=
                        preserveFailureOrder(
                            primary,
                            releaseFailure
                        );
                }
            }catch(IOException acceptFailure){
                primary=
                    preserveFailureOrder(
                        primary,
                        acceptFailure
                    );
            }finally{
                if(socket!=null)
                    try{
                        releaser.release(
                            socket
                        );
                        socket=null;
                    }catch(Throwable releaseFailure){
                        primary=
                            preserveFailureOrder(
                                primary,
                                releaseFailure
                            );

                        if(releaseFailure
                                instanceof IOException&&
                           !serverClosed.getAsBoolean())
                            retirementFailure.accept(
                                (IOException)
                                    releaseFailure
                            );
                    }
            }

            if(primary==null)
                continue;

            if(primary instanceof IOException){
                if(!serverClosed.getAsBoolean())
                    connectionFailure.accept(
                        (IOException)primary
                    );
                continue;
            }

            if(primary instanceof RuntimeException)
                throw (RuntimeException)primary;

            throw (Error)primary;
        }
    }

    static Throwable preserveFailureOrder(
        Throwable current,
        Throwable next
    ){
        Objects.requireNonNull(
            next,
            "next"
        );

        if(current==null)
            return next;

        if(current==next)
            return current;

        if(current instanceof RuntimeException||
           current instanceof Error){
            current.addSuppressed(
                next
            );
            return current;
        }

        if(current instanceof IOException&&
           next instanceof IOException){
            current.addSuppressed(
                next
            );
            return current;
        }

        next.addSuppressed(
            current
        );
        return next;
    }

    private LocalAuxHttpWorker(){}
}
