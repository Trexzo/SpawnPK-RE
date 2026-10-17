package spk.local;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.attribute.*;
import java.util.concurrent.atomic.AtomicBoolean;
public final class G21333FinalReviewForensicByteDriftTest{
    private static void ok(boolean v){if(!v)throw new AssertionError("G21.333");}
    static void cleanup(Path root)throws Exception{
        try(Stream<Path> walk=Files.walk(root)){
            for(Path p:walk.sorted(Comparator.reverseOrder())
                .toArray(Path[]::new))Files.deleteIfExists(p);
        }
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g21333-");
        try{
            Path account=root.resolve("selected.properties");
            Files.write(account,"account".getBytes(StandardCharsets.US_ASCII));
            LocalDurableAccountReviewIntentPrimitive.writeActiveReview(account);
            Path witness=LocalFinalSaveNegativeWitness.publishOnce(account,"selected",1L,1L);
            AtomicBoolean mutated=new AtomicBoolean();
            boolean refused=false;
            try{
                LocalAccountForensicEvidence.inspect(account,p->{
                    BasicFileAttributes before=Files.readAttributes(witness,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                    FileTime mtime=before.lastModifiedTime();
                    try(FileChannel f=FileChannel.open(witness,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
                        f.position(2L);f.write(ByteBuffer.wrap(new byte[]{'X'}));f.force(true);
                    }
                    Files.setLastModifiedTime(witness,mtime);
                    BasicFileAttributes after=Files.readAttributes(witness,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                    ok(before.fileKey().equals(after.fileKey())&&before.size()==after.size()&&mtime.equals(after.lastModifiedTime()));
                    mutated.set(true);
                });
            }catch(IOException e){refused=String.valueOf(e.getMessage()).contains("G21.333 FINAL_REVIEW_WITNESS_BYTES_CHANGED_DURING_READ");}
            ok(mutated.get()&&refused);
            System.out.println("G21333_FINAL_REVIEW_FORENSIC_BYTE_DRIFT_PASS restoredMetadata=true negativeReportRejected=true");
        }finally{cleanup(root);}
    }
}
