package spk.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class LocalAuxHttpResponseTest {
    public static void main(
        String[] args
    )throws Exception{
        Path file=
            Files.createTempFile(
                "spawnpk-local-aux-archive-",
                ".bin"
            );

        try{
            byte[] archive=
                new byte[
                    (2 * 1024 * 1024) + 37
                ];

            for(int i=0;i<archive.length;i++)
                archive[i]=
                    (byte)(i * 31);

            Files.write(
                file,
                archive
            );

            ByteArrayOutputStream get=
                new ByteArrayOutputStream();

            long streamed=
                LocalAuxHttpResponse.writeFile(
                    get,
                    200,
                    "OK",
                    "application/zip",
                    file,
                    false
                );

            byte[] getWire=
                get.toByteArray();
            int getHeaderEnd=
                headerEnd(
                    getWire
                );
            String getHeaders=
                new String(
                    getWire,
                    0,
                    getHeaderEnd,
                    StandardCharsets.US_ASCII
                );
            byte[] getBody=
                Arrays.copyOfRange(
                    getWire,
                    getHeaderEnd,
                    getWire.length
                );

            if(streamed!=archive.length||
               !getHeaders.contains(
                    "Content-Length: "+
                    archive.length+
                    "\r\n")||
               !Arrays.equals(
                    archive,
                    getBody
                ))
                throw new AssertionError(
                    "streamed archive GET mismatch"
                );

            ByteArrayOutputStream head=
                new ByteArrayOutputStream();

            long headLength=
                LocalAuxHttpResponse.writeFile(
                    head,
                    200,
                    "OK",
                    "application/zip",
                    file,
                    true
                );

            byte[] headWire=
                head.toByteArray();
            int headHeaderEnd=
                headerEnd(
                    headWire
                );

            if(headLength!=archive.length||
               headWire.length!=headHeaderEnd)
                throw new AssertionError(
                    "archive HEAD emitted body bytes"
                );

            byte[] placeholder=
                "LOCAL_ARCHIVE_NOT_PRESENT\n"
                    .getBytes(
                        StandardCharsets.US_ASCII
                    );
            ByteArrayOutputStream missing=
                new ByteArrayOutputStream();

            LocalAuxHttpResponse.writeBytes(
                missing,
                404,
                "Not Found",
                "text/plain; charset=us-ascii",
                placeholder,
                false
            );

            byte[] missingWire=
                missing.toByteArray();
            int missingHeaderEnd=
                headerEnd(
                    missingWire
                );

            if(!new String(
                    missingWire,
                    0,
                    missingHeaderEnd,
                    StandardCharsets.US_ASCII
                ).contains(
                    "HTTP/1.1 404 Not Found\r\n"
                )||
               !Arrays.equals(
                    placeholder,
                    Arrays.copyOfRange(
                        missingWire,
                        missingHeaderEnd,
                        missingWire.length
                    )
                ))
                throw new AssertionError(
                    "missing archive placeholder changed"
                );

            GuardedInputStream guarded=
                new GuardedInputStream(
                    5 * 1024 * 1024
                );
            CountingOutputStream counted=
                new CountingOutputStream();

            LocalAuxHttpResponse.copy(
                guarded,
                counted
            );

            if(counted.bytes!=
                    5L * 1024L * 1024L||
               guarded.maxRequested>
                    8 * 1024)
                throw new AssertionError(
                    "archive copy is not fixed-buffer bounded maxRequested="+
                    guarded.maxRequested+
                    " bytes="+
                    counted.bytes
                );
        }finally{
            Files.deleteIfExists(
                file
            );
        }

        System.out.println(
            "LOCAL_AUX_HTTP_RESPONSE_PASS "+
            "streaming=true "+
            "boundedBuffer=true "+
            "contentLength=true "+
            "getExact=true "+
            "headNoBody=true "+
            "missing404Unchanged=true"
        );
    }

    private static int headerEnd(
        byte[] wire
    ){
        for(int i=0;i+3<wire.length;i++)
            if(wire[i]=='\r'&&
               wire[i+1]=='\n'&&
               wire[i+2]=='\r'&&
               wire[i+3]=='\n')
                return i+4;

        throw new AssertionError(
            "HTTP header terminator missing"
        );
    }

    private static final class GuardedInputStream
        extends ByteArrayInputStream {

        int maxRequested;

        GuardedInputStream(
            int length
        ){
            super(
                new byte[length]
            );
        }

        @Override public synchronized int read(
            byte[] buffer,
            int offset,
            int length
        ){
            maxRequested=
                Math.max(
                    maxRequested,
                    length
                );

            return super.read(
                buffer,
                offset,
                length
            );
        }
    }

    private static final class CountingOutputStream
        extends java.io.OutputStream {

        long bytes;

        @Override public void write(
            int value
        ){
            bytes++;
        }

        @Override public void write(
            byte[] buffer,
            int offset,
            int length
        ){
            bytes+=length;
        }
    }

    private LocalAuxHttpResponseTest(){}
}
