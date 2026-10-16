package spk.local;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

/** G21.279 real disk evidence, no mutating audit or recovery. */
public final class G21279ReadOnlyAccountForensicEvidenceTest {
    private G21279ReadOnlyAccountForensicEvidenceTest(){}
    private static void check(boolean value,String explanation){
        if(!value)throw new AssertionError("G21.279 "+explanation);
    }
    private static long count(Path root)throws Exception{
        try(Stream<Path> paths=Files.list(root)){
            return paths.count();
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21279-forensic-readonly-");
        Path account=root.resolve("selected.properties");
        byte[] original="account-owned-test-data\n".getBytes(
            StandardCharsets.UTF_8);
        try{
            Files.write(account,original);
            long baseline=count(root);
            LocalAccountForensicEvidence.Evidence clean=
                LocalAccountForensicEvidence.inspect(account);
            check("PRESENT_REGULAR".equals(clean.accountState)&&
                  clean.accountSha256.length()==64,
                "regular account digest not reported");
            check(clean.observedNegativeNames==0,
                "no-marker case falsely reported negative");
            check(clean.report().contains("loginAuthorized=false")&&
                  clean.report().contains("noKnownNamesIsNotSafety=true"),
                "absence was described as login/recovery authority");
            check(count(root)==baseline,
                "clean inspection created a lock or sidecar");

            Path active=LocalDurableAccountReviewIntentPrimitive
                .writeActiveReview(account);
            Path uncertain=new MailboxStrictUncertainFence(
                requested->account).fencePath("forensic");
            Files.createFile(uncertain);
            byte[] activeBefore=Files.readAllBytes(active);
            long withMarkers=count(root);
            LocalAccountForensicEvidence.Evidence observed=
                LocalAccountForensicEvidence.inspect(account);
            check(observed.observedNegativeNames==2,
                "ACTIVE and uncertain markers not both counted");
            check("PRESENT_REGULAR_NEGATIVE".equals(
                  observed.negativeSidecars.get("activeReview"))&&
                  "PRESENT_REGULAR_NEGATIVE".equals(
                  observed.negativeSidecars.get("strictUncertain")),
                "negative markers incorrectly classified");
            check("ABSENT".equals(
                  observed.negativeSidecars.get("mailboxReview"))&&
                  "ABSENT".equals(
                  observed.negativeSidecars.get("strictWriteIntent"))&&
                  "ABSENT".equals(
                  observed.negativeSidecars.get("diskCommit")),
                "unrelated marker name contaminated scan");
            check(observed.accountSha256.equals(clean.accountSha256),
                "forensic observation changed account digest");
            check(Arrays.equals(original,Files.readAllBytes(account))&&
                  Arrays.equals(activeBefore,Files.readAllBytes(active))&&
                  Files.isRegularFile(uncertain,LinkOption.NOFOLLOW_LINKS),
                "read-only inspection mutated account or sidecars");
            check(count(root)==withMarkers,
                "forensic scan created or removed a filesystem entry");
            check(observed.report().contains("recoveryAuthorized=false")&&
                  observed.report().contains("grantAuthorized=false")&&
                  observed.report().contains("ownerEpochProven=false"),
                "report invented positive settlement/owner authority");
            System.out.println(
                "G21279_READ_ONLY_ACCOUNT_FORENSIC_EVIDENCE_PASS"+
                " actualAccountSha256=true"+
                " activeReviewAndStrictUncertainNegative=true"+
                " absentNamesNotPositive=true"+
                " markerAndAccountBytesPreserved=true"+
                " noLockOrSidecarCreated=true"+
                " noLoginOrGrantAuthority=true");
        }finally{
            try(Stream<Path> walk=Files.walk(root)){
                for(Path path:(Iterable<Path>)walk.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(path);
            }
        }
    }
}
