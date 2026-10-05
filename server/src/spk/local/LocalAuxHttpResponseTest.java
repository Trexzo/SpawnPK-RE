package spk.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
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

            byte[] oneHandleBytes=
                new byte[]{
                    11,22,33,44,55,66,77
                };
            ProbeSeekableByteChannel oneHandle=
                new ProbeSeekableByteChannel(
                    oneHandleBytes
                );
            oneHandle.position(
                4L
            );
            ByteArrayOutputStream oneHandleWire=
                new ByteArrayOutputStream();

            long oneHandleLength=
                LocalAuxHttpResponse.writeOpenedFile(
                    oneHandleWire,
                    200,
                    "OK",
                    "application/zip",
                    oneHandle,
                    false
                );

            byte[] oneHandleResponse=
                oneHandleWire.toByteArray();
            int oneHandleHeaderEnd=
                headerEnd(
                    oneHandleResponse
                );

            if(oneHandleLength!=
                    oneHandleBytes.length||
               oneHandle.sizeCalls!=1||
               oneHandle.positionZeroCalls!=1||
               oneHandle.readCalls==0||
               !oneHandle.isOpen()||
               !Arrays.equals(
                    oneHandleBytes,
                    Arrays.copyOfRange(
                        oneHandleResponse,
                        oneHandleHeaderEnd,
                        oneHandleResponse.length
                    )
                ))
                throw new AssertionError(
                    "opened archive handle did not own both length and body"
                );

            ProbeSeekableByteChannel openedHead=
                new ProbeSeekableByteChannel(
                    oneHandleBytes
                );
            ByteArrayOutputStream openedHeadWire=
                new ByteArrayOutputStream();

            long openedHeadLength=
                LocalAuxHttpResponse.writeOpenedFile(
                    openedHeadWire,
                    200,
                    "OK",
                    "application/zip",
                    openedHead,
                    true
                );
            byte[] openedHeadResponse=
                openedHeadWire.toByteArray();

            if(openedHeadLength!=
                    oneHandleBytes.length||
               openedHead.sizeCalls!=1||
               openedHead.readCalls!=0||
               !openedHead.isOpen()||
               openedHeadResponse.length!=
                    headerEnd(
                        openedHeadResponse
                    ))
                throw new AssertionError(
                    "opened archive HEAD read body bytes or changed handle ownership"
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

            LocalAuxHttpResponse.copyExactly(
                guarded,
                counted,
                5L * 1024L * 1024L
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

            ByteArrayOutputStream bounded=
                new ByteArrayOutputStream();

            LocalAuxHttpResponse.copyExactly(
                new ByteArrayInputStream(
                    new byte[32]
                ),
                bounded,
                7
            );

            if(bounded.size()!=7)
                throw new AssertionError(
                    "archive snapshot length was not enforced"
                );

            boolean shortSourceRejected=false;

            try{
                LocalAuxHttpResponse.copyExactly(
                    new ByteArrayInputStream(
                        new byte[3]
                    ),
                    new ByteArrayOutputStream(),
                    4
                );
            }catch(java.io.IOException expected){
                shortSourceRejected=
                    expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "ended early"
                    );
            }

            if(!shortSourceRejected)
                throw new AssertionError(
                    "short archive source did not fail closed"
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
            "exactSnapshotLength=true "+
            "singleOpenedHandle=true "+
            "openedHandleCallerOwned=true "+
            "openedHeadNoRead=true "+
            "shortSourceRejected=true "+
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

    private static final class ProbeSeekableByteChannel
        implements SeekableByteChannel {
        private final byte[] data;
        private int position;
        private boolean open=true;
        int sizeCalls;
        int readCalls;
        int positionZeroCalls;

        ProbeSeekableByteChannel(
            byte[] data
        ){
            this.data=
                data.clone();
        }

        @Override public int read(
            ByteBuffer destination
        ){
            requireOpen();
            readCalls++;

            if(position>=data.length)
                return -1;

            int count=
                Math.min(
                    destination.remaining(),
                    data.length-position
                );

            destination.put(
                data,
                position,
                count
            );
            position+=count;
            return count;
        }

        @Override public int write(
            ByteBuffer source
        ){
            throw new java.nio.channels.NonWritableChannelException();
        }

        @Override public long position(){
            requireOpen();
            return position;
        }

        @Override public SeekableByteChannel position(
            long newPosition
        ){
            requireOpen();

            if(newPosition<0||
               newPosition>Integer.MAX_VALUE)
                throw new IllegalArgumentException(
                    "position"
                );

            position=
                (int)newPosition;

            if(newPosition==0)
                positionZeroCalls++;

            return this;
        }

        @Override public long size(){
            requireOpen();
            sizeCalls++;
            return data.length;
        }

        @Override public SeekableByteChannel truncate(
            long size
        ){
            throw new java.nio.channels.NonWritableChannelException();
        }

        @Override public boolean isOpen(){
            return open;
        }

        @Override public void close(){
            open=false;
        }

        private void requireOpen(){
            if(!open)
                throw new IllegalStateException(
                    "channel closed"
                );
        }
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
