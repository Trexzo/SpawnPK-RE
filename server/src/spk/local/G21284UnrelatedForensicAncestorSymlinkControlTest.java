package spk.local;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.284: symlink created outside the selected account ancestry
 * must not globally veto a stable exact account forensic scan.
 * No account is opened by following the foreign symlink.
 */
public final class G21284UnrelatedForensicAncestorSymlinkControlTest {
    private G21284UnrelatedForensicAncestorSymlinkControlTest(){}
    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G21.284 "+reason);
    }
    private static String hex(byte[] hash){
        StringBuilder result=new StringBuilder(hash.length*2);
        for(byte b:hash)
            result.append(String.format("%02x",b&255));
        return result.toString();
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21284-unrelated-link-");
        try{
            Path selectedParent=root.resolve("selected-safe");
            Path otherParent=root.resolve("other-safe");
            Files.createDirectory(selectedParent);
            Files.createDirectory(otherParent);
            Path account=selectedParent.resolve("account.properties");
            Path other=otherParent.resolve("other.properties");
            byte[] before="selected-forensic-bytes".getBytes(
                StandardCharsets.US_ASCII);
            byte[] foreign="other-forensic-bytes".getBytes(
                StandardCharsets.US_ASCII);
            Files.write(account,before);
            Files.write(other,foreign);
            Path alias=root.resolve("foreign-parent-alias");
            AtomicBoolean injected=new AtomicBoolean();
            boolean supported=true;
            try{
                LocalAccountForensicEvidence.Evidence evidence=
                    LocalAccountForensicEvidence.inspect(account,selected->{
                        check(account.toAbsolutePath().normalize().equals(
                            selected),"selected account unexpectedly changed");
                        try{
                            Files.createSymbolicLink(alias,
                                otherParent.getFileName());
                            injected.set(true);
                        }catch(UnsupportedOperationException|
                               java.nio.file.FileSystemException noSupport){
                            check(!Files.exists(alias,
                                LinkOption.NOFOLLOW_LINKS),
                                "failed foreign link created an unsafe leaf");
                        }
                    });
                supported=injected.get();
                check(evidence.observedNegativeNames==0&&
                      "PRESENT_REGULAR".equals(evidence.accountState),
                    "unrelated parent symlink contaminated selected scan");
                check(hex(MessageDigest.getInstance("SHA-256")
                    .digest(before)).equals(evidence.accountSha256),
                    "stable selected account digest did not match");
                check(evidence.report().contains("finalSaveAck=false")&&
                      evidence.report().contains("loginAuthorized=false"),
                    "forensics claimed owner or admission authority");
            }finally{
                Files.deleteIfExists(alias);
            }
            check(Arrays.equals(before,Files.readAllBytes(account))&&
                  Arrays.equals(foreign,Files.readAllBytes(other)),
                "inspection changed selected or foreign account bytes");
            try(Stream<Path> contents=Files.list(selectedParent)){
                check(contents.count()==1,
                    "forensics created owned lock or marker");
            }
            System.out.println(
                "G21284_UNRELATED_PARENT_SYMLINK_FORENSIC_CONTROL_PASS"+
                " fixtureSupported="+supported+
                " noGlobalAncestryVeto=true"+
                " selectedSha256Stable=true"+
                " foreignAccountUnchanged=true"+
                " noForensicLockOrSidecarCreated=true"+
                " noLoginOrRecoveryAuthority=true");
        }finally{
            try(Stream<Path> walk=Files.walk(root)){
                for(Path path:(Iterable<Path>)walk.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(path);
            }
        }
    }
}
