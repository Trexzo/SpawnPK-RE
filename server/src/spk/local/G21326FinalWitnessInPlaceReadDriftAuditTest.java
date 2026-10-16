package spk.local;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/** G21.326 same inode, length and restored mtime is not a stable read. */
public final class G21326FinalWitnessInPlaceReadDriftAuditTest {
    private G21326FinalWitnessInPlaceReadDriftAuditTest(){}
    private static void req(boolean yes,String why){
        if(!yes)throw new AssertionError("G21.326 "+why);
    }
    public static void main(String[] args)throws Exception{
        req(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21326-read-drift-");
        try{
            Path account=root.resolve("selected.properties");
            Files.write(account,"stable-account".getBytes(
                StandardCharsets.US_ASCII));
            LocalDurableAccountReviewIntentPrimitive.writeActiveReview(
                account);
            Path witness=LocalFinalSaveNegativeWitness.publishOnce(
                account,"selected",2L,3L);
            BasicFileAttributes attrs=Files.readAttributes(witness,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            AtomicBoolean injected=new AtomicBoolean(false);
            boolean refused=false;
            try{
                LocalFinalSaveWitnessForensicAudit.inspect(
                    account,"selected",selected->{
                        req(selected.equals(witness),"different witness");
                        try(FileChannel file=FileChannel.open(selected,
                                StandardOpenOption.WRITE,
                                LinkOption.NOFOLLOW_LINKS)){
                            file.position(3L);
                            file.write(ByteBuffer.wrap(new byte[]{'X'}));
                            file.force(true);
                        }
                        Files.setLastModifiedTime(selected,
                            attrs.lastModifiedTime());
                        BasicFileAttributes after=Files.readAttributes(
                            selected,BasicFileAttributes.class,
                            LinkOption.NOFOLLOW_LINKS);
                        req(attrs.fileKey().equals(after.fileKey())&&
                            attrs.size()==after.size()&&
                            attrs.lastModifiedTime().equals(
                                after.lastModifiedTime()),
                            "same inode/size/mtime injection invalid");
                        injected.set(true);
                    });
            }catch(IOException expected){
                refused=String.valueOf(expected.getMessage()).contains(
                    "G21.326 FINAL_WITNESS_CHANGED_DURING_FORENSIC_READ");
            }
            req(injected.get()&&refused,
                "restored-mtime selected edit not caught");
            // A foreign file may change during inspection, but cannot
            // contaminate the exact selected witness observation.
            Path foreign=root.resolve("other-account.properties");
            Files.write(foreign,new byte[]{3});
            LocalFinalSaveWitnessForensicAudit.Evidence isolated=
                LocalFinalSaveWitnessForensicAudit.inspect(
                    account,"selected",selected->{
                        Files.write(foreign,new byte[]{4});
                    });
            req(isolated.state==
                LocalFinalSaveWitnessForensicAudit.State
                    .MALFORMED_NEGATIVE,
                "post-injection malformed selected witness not isolated");
            req(Files.readAllBytes(foreign)[0]==4,
                "foreign control did not change");
            boolean veto=false;
            try{
                LocalFinalSaveNegativeWitness.requireNoNegativeWitness(
                    account);
            }catch(IOException expected){veto=true;}
            req(veto,"audit ever removed negative admission veto");
            System.out.println(
                "G21326_FINAL_WITNESS_INPLACE_READ_DRIFT_PASS"+
                " realInodeSameLengthRestoredMtime=true"+
                " doubleReadDetectedTampering=true"+
                " foreignMutationIsolated=true"+
                " noReceiptOrRecovery=true");
        }finally{
            try(Stream<Path> paths=Files.walk(root)){
                for(Path p:paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
    }
}
