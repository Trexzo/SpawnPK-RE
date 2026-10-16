package spk.local;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * G21.292: exact numeric nlink witnesses distinguish two from three
 * names across *separate* stable scans. Each multi-link observation
 * remains negative; a foreign inode's aliases cannot contaminate it.
 */
public final class G21292ForensicExactLinkCountReportIsolationTest {
    private G21292ForensicExactLinkCountReportIsolationTest(){}
    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError("G21.292 "+reason);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21292-link-report-");
        try{
            Path selected=root.resolve("selected.properties");
            Path first=root.resolve("selected-alias1.properties");
            Path second=root.resolve("selected-alias2.properties");
            Path foreign=root.resolve("other.properties");
            Path foreignAlias=root.resolve("other-alias.properties");
            byte[] selectedBytes="selected-nlink".getBytes(
                StandardCharsets.US_ASCII);
            byte[] foreignBytes="foreign-nlink".getBytes(
                StandardCharsets.US_ASCII);
            Files.write(selected,selectedBytes);
            Files.write(foreign,foreignBytes);
            boolean supported=
                G21285ForensicSelectedAccountHardlinkNegativeTest
                    .supportsNlink(selected);
            if(supported){
                Files.createLink(first,selected);
                Files.createLink(foreignAlias,foreign);
            }
            LocalAccountForensicEvidence.Evidence observedTwo=
                LocalAccountForensicEvidence.inspect(selected);
            if(supported){
                check("2".equals(observedTwo.accountObservedLinkCount)&&
                    observedTwo.report().contains(
                        "accountObservedLinkCount=2")&&
                    "MULTIPLE_ACCOUNT_LINKS_NEGATIVE".equals(
                        observedTwo.accountHardlinkState),
                    "nlink 2 was not exact, observable and negative");
                Files.createLink(second,selected);
                LocalAccountForensicEvidence.Evidence observedThree=
                    LocalAccountForensicEvidence.inspect(selected);
                check("3".equals(observedThree.accountObservedLinkCount)&&
                    "MULTIPLE_ACCOUNT_LINKS_NEGATIVE".equals(
                        observedThree.accountHardlinkState)&&
                    observedTwo.accountSha256.equals(
                        observedThree.accountSha256),
                    "nlink 3 remained indistinguishable or lost content");
                LocalAccountForensicEvidence.Evidence unrelated=
                    LocalAccountForensicEvidence.inspect(foreign);
                check("2".equals(unrelated.accountObservedLinkCount)&&
                    unrelated.observedNegativeNames==0&&
                    observedThree.observedNegativeNames==0,
                    "selected hardlink count leaked into foreign account");
            }else{
                check("UNAVAILABLE_UNPROVEN".equals(
                        observedTwo.accountObservedLinkCount)&&
                    observedTwo.report().contains("loginAuthorized=false"),
                    "unsupported nlink claimed a measured count");
            }
            check(Arrays.equals(selectedBytes,Files.readAllBytes(selected))&&
                Arrays.equals(foreignBytes,Files.readAllBytes(foreign)),
                "inspection edited selected or unrelated data");
            try(Stream<Path> paths=Files.list(root)){
                check(paths.count()==(supported?5:2),
                    "inspector created lock or cleanup");
            }
            System.out.println(
                "G21292_FORENSIC_NUMERIC_NLINK_REPORT_ISOLATION_PASS"+
                " nlinkSupported="+supported+
                " twoToThreeBetweenScansObservable="+supported+
                " multiLinkAlwaysNegative="+supported+
                " exactSelectedScope=true"+
                " finalSaveAck=false");
        }finally{
            try(Stream<Path> paths=Files.walk(root)){
                for(Path path:(Iterable<Path>)paths.sorted(
                    Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(path);
            }
        }
    }
}
