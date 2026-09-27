package spk.local;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

final class LocalAuxArchiveAccess {
    interface RegularFileProbe {
        boolean isRegularFile(
            Path file
        );
    }

    interface ArchiveWriter {
        long write(
            OutputStream out,
            Path file,
            boolean head
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
            (targetOut,targetFile,targetHead)->
                LocalAuxHttpResponse.writeFile(
                    targetOut,
                    200,
                    "OK",
                    "application/zip",
                    targetFile,
                    targetHead
                )
        );
    }

    static Long writeIfAvailable(
        OutputStream out,
        Path file,
        boolean head,
        RegularFileProbe probe,
        ArchiveWriter writer
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
            writer,
            "writer"
        );

        try{
            if(!probe.isRegularFile(
                    file))
                return null;

            return Long.valueOf(
                writer.write(
                    out,
                    file,
                    head
                )
            );
        }catch(SecurityException expected){
            return null;
        }
    }

    private LocalAuxArchiveAccess(){}
}
