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
 * G21.286: an unrelated account hardlink does not globally poison
 * a read-only report for the exact selected, unaliased account.
 */
public final class G21286ForensicUnrelatedAccountHardlinkControlTest {
    private G21286ForensicUnrelatedAccountHardlinkControlTest(){}
    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G21.286 "+reason);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21286-other-nlink-");
        try{
            Path selected=root.resolve("selected.properties");
            Path other=root.resolve("unrelated.properties");
            Path alias=root.resolve("unrelated-external-link.properties");
            byte[] selectedBytes="selected-single-name".getBytes(
                StandardCharsets.US_ASCII);
            byte[] otherBytes="other-multi-name".getBytes(
                StandardCharsets.US_ASCII);
            Files.write(selected,selectedBytes);
            Files.write(other,otherBytes);
            boolean supported=G21285ForensicSelectedAccountHardlinkNegativeTest
                .supportsNlink(selected);
            if(supported)
                Files.createLink(alias,other);
            LocalAccountForensicEvidence.Evidence selectedAudit=
                LocalAccountForensicEvidence.inspect(selected);
            LocalAccountForensicEvidence.Evidence otherAudit=
                LocalAccountForensicEvidence.inspect(other);
            check(selectedAudit.observedNegativeNames==0,
                "unrelated alias changed selected review-sidecar inventory");
            check(selectedAudit.report().contains(
                "loginAuthorized=false")&&
                selectedAudit.report().contains(
                    "noKnownNamesIsNotSafety=true"),
                "single-link forensic state was promoted to admission");
            if(supported){
                check("ONE_OBSERVED_NOT_AUTHORITY".equals(
                    selectedAudit.accountHardlinkState),
                    "unaliased selected account got global alias negative");
                check("MULTIPLE_ACCOUNT_LINKS_NEGATIVE".equals(
                    otherAudit.accountHardlinkState),
                    "foreign aliased account not classified negative");
                check(Arrays.equals(otherBytes,Files.readAllBytes(alias)),
                    "foreign hardlink mutated by inspection");
            }else{
                check("UNAVAILABLE_UNPROVEN".equals(
                    selectedAudit.accountHardlinkState)&&
                    "UNAVAILABLE_UNPROVEN".equals(
                    otherAudit.accountHardlinkState),
                    "unsupported nlink was treated as single name");
            }
            check(Arrays.equals(selectedBytes,Files.readAllBytes(selected))&&
                Arrays.equals(otherBytes,Files.readAllBytes(other)),
                "forensic scanner modified either selected or foreign file");
            check(!Files.exists(
                LocalDurableAccountReviewIntentPrimitive.markerPath(selected),
                LinkOption.NOFOLLOW_LINKS),
                "scanner created selected review marker");
            try(Stream<Path> contents=Files.list(root)){
                check(contents.count()==(supported?3:2),
                    "read-only scanning created extra files/locks");
            }
            System.out.println(
                "G21286_FORENSIC_UNRELATED_ACCOUNT_NLINK_CONTROL_PASS"+
                " nlinkSupported="+supported+
                " exactSelectedScope=true"+
                " unrelatedMultipleLinksNegative="+supported+
                " noGlobalForensicVeto=true"+
                " bothAccountBytesPreserved=true"+
                " unsupportedDoesNotClaimOne=true"+
                " finalSaveAck=false");
        }finally{
            try(Stream<Path> paths=Files.walk(root)){
                for(Path p:(Iterable<Path>)paths.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(p);
            }
        }
    }
}
