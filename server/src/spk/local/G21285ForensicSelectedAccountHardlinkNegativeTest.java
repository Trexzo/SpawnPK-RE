package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/** G21.285: observed same-inode account aliases are NEGATIVE only. */
public final class G21285ForensicSelectedAccountHardlinkNegativeTest {
    private G21285ForensicSelectedAccountHardlinkNegativeTest(){}
    private static void check(boolean value,String reason){
        if(!value)throw new AssertionError("G21.285 "+reason);
    }
    static boolean supportsNlink(Path account){
        try{
            return Files.getAttribute(account,"unix:nlink",
                LinkOption.NOFOLLOW_LINKS) instanceof Number;
        }catch(UnsupportedOperationException|
               IllegalArgumentException|IOException unavailable){
            return false;
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21285-account-hardlinks-");
        try{
            Path account=root.resolve("selected.properties");
            Path alias=root.resolve("selected-external-link.properties");
            byte[] original="selected-hardlink-account\n".getBytes(
                StandardCharsets.US_ASCII);
            Files.write(account,original);
            boolean supported=supportsNlink(account);
            if(supported){
                Files.createLink(alias,account);
                LocalAccountForensicEvidence.Evidence aliased=
                    LocalAccountForensicEvidence.inspect(account);
                check("MULTIPLE_ACCOUNT_LINKS_NEGATIVE".equals(
                    aliased.accountHardlinkState),
                    "two names for same inode were not negative");
                check(aliased.report().contains(
                    "accountHardlinks=MULTIPLE_ACCOUNT_LINKS_NEGATIVE")&&
                    aliased.report().contains("loginAuthorized=false"),
                    "forensic report promoted observed alias to authority");
                check(Arrays.equals(original,Files.readAllBytes(alias)),
                    "selected and foreign alias no longer identical");
                Files.delete(alias);

                AtomicBoolean created=new AtomicBoolean();
                boolean denied=false;
                try{
                    LocalAccountForensicEvidence.inspect(account,path->{
                        check(account.toAbsolutePath().normalize().equals(
                            path),"unexpected forensic target");
                        Files.createLink(alias,account);
                        created.set(true);
                    });
                }catch(IOException expected){
                    denied=String.valueOf(expected.getMessage()).contains(
                        "G21.285 ACCOUNT_HARDLINK_COUNT_CHANGED_DURING_READ");
                }
                check(created.get()&&denied,
                    "late selected inode alias did not invalidate evidence");
                check(Files.exists(alias,LinkOption.NOFOLLOW_LINKS)&&
                    Arrays.equals(original,Files.readAllBytes(alias)),
                    "read-only scanner removed or modified external alias");
                Files.delete(alias);
                LocalAccountForensicEvidence.Evidence single=
                    LocalAccountForensicEvidence.inspect(account);
                check("ONE_OBSERVED_NOT_AUTHORITY".equals(
                    single.accountHardlinkState),
                    "single observed inode name incorrectly classified");
            }else{
                LocalAccountForensicEvidence.Evidence unknown=
                    LocalAccountForensicEvidence.inspect(account);
                check("UNAVAILABLE_UNPROVEN".equals(
                    unknown.accountHardlinkState),
                    "unsupported nlink filesystem was presented as safe");
            }
            check(Arrays.equals(original,Files.readAllBytes(account)),
                "inspector changed account bytes");
            try(Stream<Path> entries=Files.list(root)){
                check(entries.count()==1,
                    "inspector created lock, sidecar or temp");
            }
            System.out.println(
                "G21285_FORENSIC_SELECTED_ACCOUNT_HARDLINK_NEGATIVE_PASS"+
                " nlinkSupported="+supported+
                " multiLinkIsNegative="+supported+
                " lateAliasInvalidatesScan="+supported+
                " unsupportedIsUnproven=true"+
                " noLinkOrMarkerAutodeleted=true"+
                " noRecoveryOrAdmission=true");
        }finally{
            try(Stream<Path> paths=Files.walk(root)){
                for(Path p:(Iterable<Path>)paths.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(p);
            }
        }
    }
}
