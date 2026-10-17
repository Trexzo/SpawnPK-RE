package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * G21.280: an unrelated negative marker never contaminates a
 * selected file's forensic report. Malformed/nonregular sidecars
 * remain NEGATIVE; account symlinks must never be followed.
 */
public final class G21280AccountForensicIsolationAndUnsafeLeafTest {
    private G21280AccountForensicIsolationAndUnsafeLeafTest(){}
    private static void check(boolean yes,String message){
        if(!yes)throw new AssertionError("G21.280 "+message);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21280-forensic-isolation-");
        try{
            Path selected=root.resolve("first.properties");
            Path unrelated=root.resolve("second.properties");
            byte[] firstBytes="first-account".getBytes(
                StandardCharsets.UTF_8);
            byte[] otherBytes="other-account".getBytes(
                StandardCharsets.UTF_8);
            Files.write(selected,firstBytes);
            Files.write(unrelated,otherBytes);
            Path foreignCommit=unrelated.resolveSibling(
                unrelated.getFileName().toString()+
                MailboxGuardedDiskCommitRecord.SUFFIX);
            // A directory or malicious nonregular negative sidecar is
            // NOT a valid marker, but it MUST count as negative.
            Files.createDirectory(foreignCommit);
            int countBefore;
            try(Stream<Path> ls=Files.list(root)){
                countBefore=(int)ls.count();
            }

            LocalAccountForensicEvidence.Evidence selectedAudit=
                LocalAccountForensicEvidence.inspect(selected);
            LocalAccountForensicEvidence.Evidence foreignAudit=
                LocalAccountForensicEvidence.inspect(unrelated);
            check(selectedAudit.observedNegativeNames==0,
                "foreign marker affected the selected exact account");
            check(foreignAudit.observedNegativeNames==1&&
                "PRESENT_NONREGULAR_NEGATIVE".equals(
                    foreignAudit.negativeSidecars.get("diskCommit")),
                "nonregular disk COMMIT was ignored/treated safe");
            check(!selectedAudit.accountSha256.equals(
                    foreignAudit.accountSha256),
                "different account data produced same digest");
            check(Arrays.equals(firstBytes,Files.readAllBytes(selected))&&
                  Arrays.equals(otherBytes,Files.readAllBytes(unrelated))&&
                  Files.isDirectory(foreignCommit,
                      LinkOption.NOFOLLOW_LINKS),
                "forensic scan mutated foreign account/commit directory");
            try(Stream<Path> ls=Files.list(root)){
                check(ls.count()==countBefore,
                    "forensic scan created lock/marker/temp");
            }

            // The selected account leaf itself must never be
            // dereferenced through a symlink. Skip this fixture only
            // on hosts that explicitly prohibit creating symlinks.
            boolean testedSymlink=false;
            Path alias=root.resolve("linked-account.properties");
            try{
                Files.createSymbolicLink(alias,unrelated.getFileName());
                testedSymlink=true;
            }catch(UnsupportedOperationException|
                   java.nio.file.FileSystemException unsupported){
                check(!Files.exists(alias,LinkOption.NOFOLLOW_LINKS),
                    "failed symlink creation left unexpected alias");
            }
            if(testedSymlink){
                boolean denied=false;
                try{
                    LocalAccountForensicEvidence.inspect(alias);
                }catch(IOException expected){
                    denied=String.valueOf(expected.getMessage())
                        .contains("ACCOUNT_LEAF_UNSAFE");
                }
                check(denied,"account symlink was hashed/followed");
                check(Arrays.equals(otherBytes,Files.readAllBytes(unrelated)),
                    "account symlink target was modified");
            }
            System.out.println(
                "G21280_FORENSIC_NEGATIVE_SCOPE_AND_UNSAFE_LEAF_PASS"+
                " exactSelectedAccount=true"+
                " foreignDiskCommitDirectoryRemainsNegative=true"+
                " noCrossAccountReviewPromotion=true"+
                " noLockFileCreated=true"+
                " accountSymlinkNeverFollowed=true"+
                " symlinkFixtureSupported="+testedSymlink+
                " noRecoveryOrAdmission=true");
        }finally{
            try(Stream<Path> walk=Files.walk(root)){
                for(Path path:(Iterable<Path>)walk.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(path);
            }
        }
    }
}
