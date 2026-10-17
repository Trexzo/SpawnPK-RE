package spk.local;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/** G21.288: foreign inode alias churn is not a global forensic veto. */
public final class G21288ForensicUnrelatedMultipleAliasChurnControlTest {
    private G21288ForensicUnrelatedMultipleAliasChurnControlTest(){}
    private static void check(boolean value,String reason){
        if(!value)throw new AssertionError("G21.288 "+reason);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21288-foreign-alias-");
        try{
            Path selected=root.resolve("selected.properties");
            Path foreign=root.resolve("foreign.properties");
            Path alias1=root.resolve("foreign-alias-1.properties");
            Path alias2=root.resolve("foreign-alias-2.properties");
            byte[] selectedBytes="selected-account-unchanged".getBytes(
                StandardCharsets.US_ASCII);
            byte[] foreignBytes="unrelated-account-unchanged".getBytes(
                StandardCharsets.US_ASCII);
            Files.write(selected,selectedBytes);
            Files.write(foreign,foreignBytes);
            boolean supported=
                G21285ForensicSelectedAccountHardlinkNegativeTest
                    .supportsNlink(selected);
            if(supported)Files.createLink(alias1,foreign);
            AtomicBoolean injected=new AtomicBoolean(false);
            LocalAccountForensicEvidence.Evidence witness=
                LocalAccountForensicEvidence.inspect(selected,path->{
                    check(selected.toAbsolutePath().normalize().equals(path),
                        "probe wrong selected account");
                    if(supported){
                        Files.createLink(alias2,foreign);
                        injected.set(true);
                    }
                });
            check(witness.observedNegativeNames==0,
                "foreign alias created selected negative marker");
            check((supported?"ONE_OBSERVED_NOT_AUTHORITY":
                    "UNAVAILABLE_UNPROVEN").equals(witness.accountHardlinkState),
                "selected unchanged inode falsely invalidated");
            check(witness.report().contains("loginAuthorized=false")&&
                witness.report().contains("ownerEpochProven=false"),
                "selected scan was promoted to ownership");
            if(supported){
                check(injected.get()&&Files.exists(alias2,
                    LinkOption.NOFOLLOW_LINKS),"foreign third alias not created");
                LocalAccountForensicEvidence.Evidence foreignWitness=
                    LocalAccountForensicEvidence.inspect(foreign);
                check("MULTIPLE_ACCOUNT_LINKS_NEGATIVE".equals(
                    foreignWitness.accountHardlinkState),
                    "foreign 3-link inode was misclassified");
            }
            check(Arrays.equals(selectedBytes,Files.readAllBytes(selected))&&
                Arrays.equals(foreignBytes,Files.readAllBytes(foreign)),
                "forensic scan edited account data");
            try(Stream<Path> listing=Files.list(root)){
                check(listing.count()==(supported?4:2),
                    "scanner created or removed filesystem objects");
            }
            System.out.println("G21288_FORENSIC_FOREIGN_ALIAS_CHURN_CONTROL_PASS"+
                " nlinkSupported="+supported+
                " selectedScopeIsolated=true"+
                " unrelatedAliasChurnAllowed="+supported+
                " bothAccountsPreserved=true"+
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
