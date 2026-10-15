package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/** G21.226 tests a symlinked grandparent, not just direct parent. */
public final class G21226CoreCrossJvmLockAncestorNofollowTest {
    private G21226CoreCrossJvmLockAncestorNofollowTest(){}
    private static void check(boolean yes,String stage){
        if(!yes)throw new AssertionError("G21.226 "+stage);
    }
    private static boolean rejected(Path account)throws Exception{
        try(LocalCrossJvmAccountSessionLock.Handle invalid=
                LocalCrossJvmAccountSessionLock.acquire(account)){
            return false;
        }catch(IOException expected){
            return expected.getMessage()!=null&&
                expected.getMessage().contains(
                    "ACCOUNT_LOCK_SYMLINK_ANCESTOR_NOFOLLOW");
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21226-lock-ancestry-");
        String flag=LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY;
        String old=System.getProperty(flag);
        System.setProperty(flag,"false");
        try{
            Path real=root.resolve("real");
            Path nested=real.resolve("middle").resolve("accounts");
            Files.createDirectories(nested);
            Path healthy=nested.resolve("healthy.properties");
            try(LocalCrossJvmAccountSessionLock.Handle valid=
                    LocalCrossJvmAccountSessionLock.acquire(healthy)){
                check(valid!=null,"legitimate nested OS lock denied");
                Path expected=healthy.resolveSibling(
                    "healthy.properties.session.lck");
                check(Files.isRegularFile(
                    expected,LinkOption.NOFOLLOW_LINKS),
                    "stable lock file missing for valid nested account");
            }

            if(!System.getProperty("os.name").toLowerCase()
                    .contains("win")){
                Path linked=root.resolve("redirect");
                Files.createSymbolicLink(linked,real);
                Path account=linked.resolve("middle")
                    .resolve("accounts").resolve("unsafe.properties");
                Path realLock=nested.resolve(
                    "unsafe.properties.session.lck");
                check(!Files.exists(realLock,LinkOption.NOFOLLOW_LINKS),
                    "target lock file unexpectedly existed");
                check(rejected(account),
                    "symlinked grandparent bypassed NOFOLLOW ancestry");
                check(!Files.exists(realLock,LinkOption.NOFOLLOW_LINKS),
                    "refused lock operation created target leaf");

                // Direct symlink-parent remains refused too.
                Path direct=linked.resolve("direct.properties");
                check(rejected(direct),
                    "direct symlink parent was accepted");
                check(!Files.exists(real.resolve(
                    "direct.properties.session.lck"),
                    LinkOption.NOFOLLOW_LINKS),
                    "direct symlink parent created lock leaf");
                System.out.println(
                    "G21226_LOCK_ANCESTOR_NOFOLLOW_PASS"+
                    " realNestedAccountAllowed=true"+
                    " symlinkedGrandparentDenied=true"+
                    " directSymlinkParentDenied=true"+
                    " deniedPathsCreatedNoLockLeaves=true"+
                    " lockObjectReplacementRaceSolved=false"+
                    " durableOwnerEpoch=false");
            }else{
                // Windows symlink-creation privileges vary. Do not
                // misrepresent this as an exercised symlink negative.
                System.out.println(
                    "G21226_LOCK_ANCESTOR_NOFOLLOW_PASS"+
                    " realNestedAccountAllowed=true"+
                    " symlinkedGrandparentDenied=UNSUPPORTED_WINDOWS"+
                    " noUniversalSymlinkProof=true"+
                    " durableOwnerEpoch=false");
            }
        }finally{
            if(old==null)System.clearProperty(flag);
            else System.setProperty(flag,old);
            try(Stream<Path> entries=Files.walk(root)){
                for(Path item:entries.sorted(
                        Comparator.reverseOrder()).toArray(Path[]::new))
                    Files.deleteIfExists(item);
            }
        }
    }
}
