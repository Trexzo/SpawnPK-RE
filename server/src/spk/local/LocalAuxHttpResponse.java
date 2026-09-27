package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

final class LocalAuxHttpResponse {
    private static final int COPY_BUFFER_SIZE=
        8 * 1024;

    static void writeBytes(
        OutputStream out,
        int status,
        String reason,
        String type,
        byte[] body,
        boolean head
    )throws IOException{
        Objects.requireNonNull(
            body,
            "body"
        );

        writeHeaders(
            out,
            status,
            reason,
            type,
            body.length
        );

        if(!head)
            out.write(
                body
            );

        out.flush();
    }

    static long writeFile(
        OutputStream out,
        int status,
        String reason,
        String type,
        Path file,
        boolean head
    )throws IOException{
        Objects.requireNonNull(
            file,
            "file"
        );

        long length=
            Files.size(
                file
            );

        if(length<0)
            throw new IOException(
                "negative auxiliary file length: "+
                length
            );

        writeHeaders(
            out,
            status,
            reason,
            type,
            length
        );

        if(!head)
            try(InputStream in=
                    Files.newInputStream(
                        file
                    )){
                copy(
                    in,
                    out
                );
            }

        out.flush();
        return length;
    }

    static void copy(
        InputStream in,
        OutputStream out
    )throws IOException{
        Objects.requireNonNull(
            in,
            "in"
        );
        Objects.requireNonNull(
            out,
            "out"
        );

        byte[] buffer=
            new byte[
                COPY_BUFFER_SIZE
            ];

        for(;;){
            int count=
                in.read(
                    buffer
                );

            if(count<0)
                break;

            if(count==0)
                continue;

            out.write(
                buffer,
                0,
                count
            );
        }
    }

    private static void writeHeaders(
        OutputStream out,
        int status,
        String reason,
        String type,
        long contentLength
    )throws IOException{
        Objects.requireNonNull(
            out,
            "out"
        );
        Objects.requireNonNull(
            reason,
            "reason"
        );
        Objects.requireNonNull(
            type,
            "type"
        );

        if(contentLength<0)
            throw new IOException(
                "negative Content-Length: "+
                contentLength
            );

        String headers=
            "HTTP/1.1 " + status + " " + reason + "\r\n"+
            "Content-Type: " + type + "\r\n"+
            "Content-Length: " + contentLength + "\r\n"+
            "Connection: close\r\n"+
            "Cache-Control: no-store\r\n\r\n";

        out.write(
            headers.getBytes(
                StandardCharsets.US_ASCII
            )
        );
    }

    private LocalAuxHttpResponse(){}
}
