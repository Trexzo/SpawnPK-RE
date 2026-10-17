package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Objects;

/**
 * G21.51: read untrusted NEGATIVE marker files without first allocating
 * their entire advertised or changed contents. Not an account decoder,
 * authorization gate, durability proof, or atomic multi-file snapshot.
 *
 * A metadata check limits ordinary oversized files; the actual opened
 * descriptor is then independently capped at max+1 bytes. NOFOLLOW is
 * required on both operations to reject even a substituted symlink.
 */
final class MailboxNegativeMarkerBoundedRead {
    static byte[] read(Path file,int minimum,int maximum)
        throws IOException{
        Objects.requireNonNull(file,"marker file");
        if(minimum<0||maximum<minimum||maximum>4096)
            throw new IllegalArgumentException(
                "G21.51 invalid bounded marker read limits"
            );
        BasicFileAttributes metadata=Files.readAttributes(
            file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
        );
        if(!metadata.isRegularFile()||
           metadata.size()<minimum||metadata.size()>maximum)
            throw new IOException(
                "G21.51 negative marker not regular or out of bounds"
            );
        try(FileChannel channel=FileChannel.open(
                file,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            ByteBuffer bytes=ByteBuffer.allocate(maximum+1);
            while(bytes.hasRemaining()){
                int n=channel.read(bytes);
                if(n<0)break;
                if(n==0)
                    throw new IOException(
                        "G21.51 marker returned empty nonterminal read"
                    );
            }
            int used=bytes.position();
            if(used<minimum||used>maximum)
                throw new IOException(
                    "G21.51 negative marker size changed during read"
                );
            return Arrays.copyOf(bytes.array(),used);
        }
    }

    private MailboxNegativeMarkerBoundedRead(){}
}
