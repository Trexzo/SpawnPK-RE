package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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

        try(SeekableByteChannel channel=
                Files.newByteChannel(
                    file,
                    StandardOpenOption.READ
                )){
            return writeOpenedFile(
                out,
                status,
                reason,
                type,
                channel,
                head
            );
        }
    }

    static long writeOpenedFile(
        OutputStream out,
        int status,
        String reason,
        String type,
        SeekableByteChannel channel,
        boolean head
    )throws IOException{
        Objects.requireNonNull(
            channel,
            "channel"
        );

        long length=
            channel.size();

        if(length<0)
            throw new IOException(
                "negative auxiliary file length: "+
                length
            );

        channel.position(
            0L
        );

        writeHeaders(
            out,
            status,
            reason,
            type,
            length
        );

        if(!head)
            copyExactly(
                Channels.newInputStream(
                    channel
                ),
                out,
                length
            );

        out.flush();
        return length;
    }

    static void copyExactly(
        InputStream in,
        OutputStream out,
        long length
    )throws IOException{
        Objects.requireNonNull(
            in,
            "in"
        );
        Objects.requireNonNull(
            out,
            "out"
        );

        if(length<0)
            throw new IOException(
                "negative stream length: "+
                length
            );

        byte[] buffer=
            new byte[
                COPY_BUFFER_SIZE
            ];
        long remaining=
            length;

        while(remaining>0){
            int requested=
                (int)Math.min(
                    (long)buffer.length,
                    remaining
                );
            int count=
                in.read(
                    buffer,
                    0,
                    requested
                );

            if(count<0)
                throw new IOException(
                    "auxiliary response source ended early remaining="+
                    remaining
                );

            if(count==0){
                int value=
                    in.read();

                if(value<0)
                    throw new IOException(
                        "auxiliary response source ended early remaining="+
                        remaining
                    );

                out.write(
                    value
                );
                remaining--;
                continue;
            }

            out.write(
                buffer,
                0,
                count
            );
            remaining-=count;
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
