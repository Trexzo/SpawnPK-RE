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

/** G21.287: a real selected inode's nlink 2->3 is a scan-time change. */
public final class G21287ForensicMultipleToMultipleAliasDriftTest {
    private G21287ForensicMultipleToMultipleAliasDriftTest(){}
    private static void check(boolean value,String reason){
        if(!value)throw new AssertionError("G21.287 "+reason);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21287-multi-alias-");
        try{
            Path account=root.resolve("selected.properties");
            Path alias1=root.resolve("selected-alias-1.properties");
            Path alias2=root.resolve("selected-alias-2.properties");
            byte[] bytes="selected-multi-alias-account\n".getBytes(
                StandardCharsets.US_ASCII);
            Files.write(account,bytes);
            boolean supported=
                G21285ForensicSelectedAccountHardlinkNegativeTest
                    .supportsNlink(account);
            if(supported){
                Files.createLink(alias1,account);
                LocalAccountForensicEvidence.Evidence initial=
                    LocalAccountForensicEvidence.inspect(account);
                check("MULTIPLE_ACCOUNT_LINKS_NEGATIVE".equals(
                    initial.accountHardlinkState),"baseline nlink=2 not negative");
                AtomicBoolean injected=new AtomicBoolean(false);
                boolean rejected=false;
                try{
                    LocalAccountForensicEvidence.inspect(account,path->{
                        check(account.toAbsolutePath().normalize().equals(path),
                            "probe received unrelated account");
                        Files.createLink(alias2,path);
                        injected.set(true);
                    });
                }catch(IOException expected){
                    rejected=String.valueOf(expected.getMessage()).contains(
                        "G21.287 ACCOUNT_HARDLINK_EXACT_COUNT_CHANGED_DURING_READ");
                }
                check(injected.get()&&rejected,
                    "nlink=2 to nlink=3 was not invalidated exactly");
                check(Files.exists(alias1,LinkOption.NOFOLLOW_LINKS)&&
                    Files.exists(alias2,LinkOption.NOFOLLOW_LINKS),
                    "scanner silently unlinked selected aliases");
                LocalAccountForensicEvidence.Evidence stable=
                    LocalAccountForensicEvidence.inspect(account);
                check("MULTIPLE_ACCOUNT_LINKS_NEGATIVE".equals(
                    stable.accountHardlinkState),
                    "stable observed nlink=3 was promoted to authority");
                check(stable.report().contains("loginAuthorized=false")&&
                    stable.report().contains("finalSaveAck=false"),
                    "negative evidence claimed admission or final-save receipt");
                check(Arrays.equals(bytes,Files.readAllBytes(alias2)),
                    "scanner changed aliased account bytes");
            }else{
                LocalAccountForensicEvidence.Evidence unknown=
                    LocalAccountForensicEvidence.inspect(account);
                check("UNAVAILABLE_UNPROVEN".equals(unknown.accountHardlinkState),
                    "unsupported exact nlink falsely classified clean");
            }
            check(Arrays.equals(bytes,Files.readAllBytes(account)),
                "forensic scan mutated selected account");
            try(Stream<Path> listing=Files.list(root)){
                check(listing.count()==(supported?3:1),
                    "scanner created unexpected lock or negative sidecar");
            }
            System.out.println("G21287_FORENSIC_EXACT_SELECTED_NLINK_DRIFT_PASS"+
                " nlinkSupported="+supported+
                " multipleToMultipleDriftRejected="+supported+
                " reportStillNegative=true"+
                " readOnly=true"+
                " noAdmissionAuthority=true");
        }finally{
            try(Stream<Path> paths=Files.walk(root)){
                for(Path path:(Iterable<Path>)paths.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(path);
            }
        }
    }
}
