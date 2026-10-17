package spk.local;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.282: a drift on another named account during the selected
 * two-pass forensic read never becomes a global selected-account
 * rejection. The inspector neither acquires an OS lease nor writes.
 */
public final class G21282ForensicExactAccountContinuityControlTest {
    private G21282ForensicExactAccountContinuityControlTest(){}
    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G21.282 "+why);
    }
    private static String hex(byte[] bytes){
        StringBuilder b=new StringBuilder();
        for(byte v:bytes)b.append(String.format("%02x",v&255));
        return b.toString();
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21282-forensic-select-");
        try{
            Path selected=root.resolve("selected.properties");
            Path foreign=root.resolve("unrelated.properties");
            byte[] target=new byte[32768];
            Arrays.fill(target,(byte)'a');
            byte[] other="foreign=BEFORE\n".getBytes(
                StandardCharsets.US_ASCII);
            Files.write(selected,target);
            Files.write(foreign,other);
            FileTime oldMtime=Files.getLastModifiedTime(foreign,
                LinkOption.NOFOLLOW_LINKS);
            AtomicBoolean mutated=new AtomicBoolean(false);
            long initialEntries;
            try(Stream<Path> ls=Files.list(root)){
                initialEntries=ls.count();
            }
            LocalAccountForensicEvidence.Evidence output=
                LocalAccountForensicEvidence.inspect(selected,path->{
                    check(selected.toAbsolutePath().normalize()
                        .equals(path),"unexpected forensic target");
                    try(FileChannel channel=FileChannel.open(foreign,
                            StandardOpenOption.WRITE,
                            LinkOption.NOFOLLOW_LINKS)){
                        channel.position(8);
                        channel.write(ByteBuffer.wrap(new byte[]{'A'}));
                        channel.force(true);
                    }
                    Files.setLastModifiedTime(foreign,oldMtime);
                    mutated.set(true);
                });
            check(mutated.get(),"fixture unrelated mutation absent");
            check("PRESENT_REGULAR".equals(output.accountState)&&
                  output.observedNegativeNames==0,
                "unrelated file contamination in selected evidence");
            String expected=hex(MessageDigest.getInstance("SHA-256")
                .digest(target));
            check(expected.equals(output.accountSha256),
                "selected exact account SHA not retained across passes");
            check(Arrays.equals(target,Files.readAllBytes(selected)),
                "selected account changed by inspector");
            check(!Arrays.equals(other,Files.readAllBytes(foreign))&&
                  other.length==Files.size(foreign),
                "unrelated write did not occur or altered file size");
            try(Stream<Path> ls=Files.list(root)){
                check(ls.count()==initialEntries,
                    "forensic scan created a lock/temporary/marker");
            }
            check(output.report().contains("loginAuthorized=false")&&
                  output.report().contains("finalSaveAck=false")&&
                  output.report().contains("noKnownNamesIsNotSafety=true"),
                "scan report upgraded byte stability to authority");
            System.out.println(
                "G21282_FORENSIC_EXACT_ACCOUNT_DOUBLE_DIGEST_CONTROL_PASS"+
                " stableSelectedSha256Verified=true"+
                " concurrentUnrelatedAccountEditIgnored=true"+
                " noGlobalForensicVeto=true"+
                " selectedBytesPreserved=true"+
                " noLockOrMarkerCreated=true"+
                " noLoginOrFinalSaveAck=true");
        }finally{
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:(Iterable<Path>)walk.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(p);
            }
        }
    }
}
