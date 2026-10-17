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

/**
 * G21.283: an attacker replaces the selected account PARENT with
 * a symlink to the original directory after the first digest. The
 * account leaf itself remains a regular same-inode file, so final-
 * component NOFOLLOW and metadata-only rechecks are insufficient.
 */
public final class G21283ForensicAncestorSymlinkSwapTest {
    private G21283ForensicAncestorSymlinkSwapTest(){}
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G21.283 "+why);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21283-parent-swap-");
        Path directory=root.resolve("original-directory");
        Path parked=root.resolve("parked-original-directory");
        Path account=directory.resolve("named.properties");
        AtomicBoolean inserted=new AtomicBoolean();
        try{
            Files.createDirectory(directory);
            byte[] initial=("forensic=selected\n"+
                "review-authority=none\n").getBytes(StandardCharsets.US_ASCII);
            Files.write(account,initial);
            Path symlinkProbe=root.resolve("symbolic-link-capability");
            boolean supported;
            try{
                Files.createSymbolicLink(symlinkProbe,
                    directory.getFileName());
                supported=true;
                Files.delete(symlinkProbe);
            }catch(UnsupportedOperationException|
                   java.nio.file.FileSystemException notSupported){
                supported=false;
                Files.deleteIfExists(symlinkProbe);
            }
            if(supported){
                boolean rejected=false;
                try{
                    LocalAccountForensicEvidence.inspect(account,selected->{
                        check(account.toAbsolutePath().normalize()
                            .equals(selected),"wrong forensic target");
                        Files.move(directory,parked);
                        Files.createSymbolicLink(directory,
                            parked.getFileName());
                        check(Files.isSymbolicLink(directory)&&
                              Files.isRegularFile(account,
                                  LinkOption.NOFOLLOW_LINKS),
                            "fixture does not expose parent-only symlink");
                        inserted.set(true);
                    });
                }catch(IOException expected){
                    rejected=String.valueOf(expected.getMessage())
                        .contains("G21.283 "+
                            "FORENSIC_ACCOUNT_ANCESTRY_CHANGED_NO_AUTHORITY");
                }
                check(inserted.get()&&rejected,
                    "parent swap was not rejected before report");
                check(Arrays.equals(initial,Files.readAllBytes(account)),
                    "account target was modified through parent alias");
                check(Files.isSymbolicLink(directory)&&
                      Files.isRegularFile(parked.resolve("named.properties"),
                          LinkOption.NOFOLLOW_LINKS),
                    "original account inode was not retained");
                try(Stream<Path> entries=Files.list(parked)){
                    check(entries.count()==1,
                        "inspector created lock/marker beside account");
                }
            }
            System.out.println(
                "G21283_FORENSIC_PARENT_SYMLINK_SWAP_REJECTED_PASS"+
                " fixtureSupported="+supported+
                " selectedAccountBytesUnchanged=true"+
                " noWriterOrMarkerCreated=true"+
                " parentNoFollowRevalidated="+supported+
                " noAtomicDirectorySnapshotClaim=true");
        }finally{
            // Restore the TEST's deliberate parent substitution before
            // deleting the owned temporary tree.
            if(Files.isSymbolicLink(directory))
                Files.delete(directory);
            if(Files.exists(parked,LinkOption.NOFOLLOW_LINKS)&&
               !Files.exists(directory,LinkOption.NOFOLLOW_LINKS))
                Files.move(parked,directory);
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:(Iterable<Path>)walk.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(p);
            }
        }
    }
}
