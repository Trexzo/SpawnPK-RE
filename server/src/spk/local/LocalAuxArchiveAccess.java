package spk.local;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

final class LocalAuxArchiveAccess {
    interface RegularFileProbe {
        boolean isRegularFile(
            Path file
        );
    }

    interface ChannelOpener {
        SeekableByteChannel open(
            Path file
        )throws IOException;
    }

    static Long writeIfAvailable(
        OutputStream out,
        Path file,
        boolean head
    )throws IOException{
        return writeIfAvailable(
            out,
            file,
            head,
            Files::isRegularFile,
            target->
                Files.newByteChannel(
                    target,
                    StandardOpenOption.READ
                )
        );
    }

    static Long writeIfAvailable(
        OutputStream out,
        Path file,
        boolean head,
        RegularFileProbe probe,
        ChannelOpener opener
    )throws IOException{
        Objects.requireNonNull(
            out,
            "out"
        );
        Objects.requireNonNull(
            file,
            "file"
        );
        Objects.requireNonNull(
            probe,
            "probe"
        );
        Objects.requireNonNull(
            opener,
            "opener"
        );

        final SeekableByteChannel channel;

        try{
            if(!probe.isRegularFile(
                    file))
                return null;

            channel=
                opener.open(
                    file
                );
        }catch(SecurityException expected){
            return null;
        }

        Long result=null;
        Throwable failure=null;

        try{
            result=
                Long.valueOf(
                    LocalAuxHttpResponse
                        .writeOpenedFile(
                            out,
                            200,
                            "OK",
                            "application/zip",
                            channel,
                            head
                        )
                );
        }catch(IOException|
               RuntimeException|
               Error responseFailure){
            failure=responseFailure;
        }

        try{
            channel.close();
        }catch(Throwable closeFailure){
            failure=
                LocalAuxHttpWorker
                    .preserveFailureOrder(
                        failure,
                        closeFailure
                    );
        }

        if(failure!=null)
            rethrowPostOpenFailure(
                failure
            );

        return result;
    }

    private static void rethrowPostOpenFailure(
        Throwable failure
    )throws IOException{
        if(failure instanceof IOException)
            throw (IOException)failure;

        if(failure instanceof RuntimeException)
            throw (RuntimeException)failure;

        if(failure instanceof Error)
            throw (Error)failure;

        throw new IOException(
            "archive response/close failed",
            failure
        );
    }

    private LocalAuxArchiveAccess(){}
}
