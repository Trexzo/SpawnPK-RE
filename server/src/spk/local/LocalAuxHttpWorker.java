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

            try{
                socket=
                    acceptor.accept();

                if(socket==null)
                    break;

                try{
                    handler.handle(
                        socket
                    );
                }finally{
                    releaser.release(
                        socket
                    );
                    socket=null;
                }
            }catch(IOException failure){
                if(!serverClosed.getAsBoolean())
                    connectionFailure.accept(
                        failure
                    );
            }finally{
                if(socket!=null)
                    try{
                        releaser.release(
                            socket
                        );
                    }catch(IOException releaseFailure){
                        if(!serverClosed.getAsBoolean())
                            retirementFailure.accept(
                                releaseFailure
                            );
                    }
            }
        }
    }

    private LocalAuxHttpWorker(){}
}
