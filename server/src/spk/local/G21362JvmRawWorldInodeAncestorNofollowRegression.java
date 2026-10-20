package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * R34: the opt-in same-JVM account inode gate must reject symbolic
 * link ancestors even if cross-JVM session locking is disabled.
 * A NOFOLLOW leaf read is insufficient when parent/grandparent names
 * can redirect the account file to an unrelated directory.
 *
 * This is a filesystem preflight, not atomic ancestry fencing,
 * authenticated ownership, or a crash-durable writer epoch.
 */
public final class G21362JvmRawWorldInodeAncestorNofollowRegression {
    private static final String USER="r34-no-follow";
    private G21362JvmRawWorldInodeAncestorNofollowRegression(){}
    private static void check(boolean okay,String why){
        if(!okay)throw new AssertionError("R34_JVM_ACCOUNT_ANCESTRY "+why);
    }

    private static boolean rawDenied(Path account)throws Exception{
        try(LocalJvmAccountSessionLease.RawWriteReservation raw=
                LocalJvmAccountSessionLease.reserveRawWrite(account,USER)){
            return false;
        }catch(IOException expected){
            return expected.getMessage()!=null&&
                expected.getMessage().contains(
                    "ACCOUNT_LOCK_SYMLINK_ANCESTOR_NOFOLLOW");
        }
    }

    private static boolean worldDenied(Path account)throws Exception{
        World world=World.isolatedForTest(60_000L,
            new FilePlayerRepository(ignored->account));
        try{
            try{
                LocalJvmAccountSessionLease.Token token=
                    LocalJvmAccountSessionLease.acquire(
                        world.persistence(),USER);
                if(token!=null)
                    token.releaseAfterInitializationFailure();
                return false;
            }catch(IllegalStateException expected){
                return expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "ACCOUNT_FILE_OBJECT_PRECHECK_FAILED");
            }
        }finally{
            world.close();
        }
    }

    private static void genuineRawDenied(Path account)
        throws Exception{
        FilePlayerRepository repository=new FilePlayerRepository(
            ignored->account);
        boolean denied=false;
        try{
            repository.save(PlayerSnapshotCodec.capture(
                USER,new WorldPlayer()));
        }catch(IOException expected){
            denied=expected.getMessage()!=null&&
                expected.getMessage().contains(
                    "RAW_ACCOUNT_INODE_PRECHECK_FAILED");
        }
        check(denied,
            "real RAW repository save accepted symlink ancestor");
        check(!Files.exists(account.resolveSibling(
                account.getFileName().toString()+".tmp"),
                LinkOption.NOFOLLOW_LINKS),
            "denied RAW call created redirected temp");
        check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
            "rejected repository save leaked reservation");
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("r34-jvm-ancestry-");
        String[] props={
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalJvmAccountSessionLease.INODE_ALIAS_GATE_PROPERTY,
            LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY
        };
        String[] prior=new String[props.length];
        for(int i=0;i<props.length;i++)
            prior[i]=System.getProperty(props[i]);
        System.setProperty(props[0],"true");
        System.setProperty(props[1],"true");
        System.setProperty(props[2],"true");
        System.setProperty(props[3],"false");
        System.setProperty(props[4],"false");
        try{
            Path real=root.resolve("real");
            Path nested=real.resolve("middle").resolve("accounts");
            Files.createDirectories(nested);
            Path healthy=nested.resolve("account.properties");
            // Positive: a missing, safe bootstrap account remains
            // usable when no filename component redirects elsewhere.
            try(LocalJvmAccountSessionLease.RawWriteReservation raw=
                    LocalJvmAccountSessionLease.reserveRawWrite(
                        healthy,USER)){
                check(raw!=null,
                    "valid account RAW reservation unexpectedly denied");
            }
            check(LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0,
                "valid RAW reservation not released");
            check(!Files.exists(healthy,LinkOption.NOFOLLOW_LINKS),
                "test bootstrap reservation created account contents");

            World validWorld=World.isolatedForTest(60_000L,
                new FilePlayerRepository(ignored->healthy));
            try{
                LocalJvmAccountSessionLease.Token valid=
                    LocalJvmAccountSessionLease.acquire(
                        validWorld.persistence(),USER);
                check(valid!=null,"valid World opt-in lease missing");
                valid.releaseAfterInitializationFailure();
            }finally{
                validWorld.close();
            }

            if(!System.getProperty("os.name").toLowerCase()
                    .contains("win")){
                Path linked=root.resolve("redirect");
                Files.createSymbolicLink(linked,real);
                Path grandparent=linked.resolve("middle")
                    .resolve("accounts").resolve("grandparent.properties");
                Path parent=linked.resolve("parent.properties");
                for(Path unsafe:new Path[]{grandparent,parent}){
                    check(rawDenied(unsafe),
                        "RAW guard followed redirect "+unsafe);
                    check(worldDenied(unsafe),
                        "World guard followed redirect "+unsafe);
                    genuineRawDenied(unsafe);
                    check(!Files.exists(unsafe,LinkOption.NOFOLLOW_LINKS),
                        "denied account created file "+unsafe);
                }
                check(!Files.exists(nested.resolve(
                        "grandparent.properties.tmp"),
                        LinkOption.NOFOLLOW_LINKS),
                    "symlinked grandparent leaked RAW temp");
                check(!Files.exists(real.resolve("parent.properties.tmp"),
                        LinkOption.NOFOLLOW_LINKS),
                    "symlinked direct parent leaked RAW temp");
                System.out.println(
                    "R34_JVM_RAW_WORLD_INODE_ANCESTRY_NOFOLLOW_PASS"+
                    " crossJvmOptInDisabled=true"+
                    " validMissingAccountAllowed=true"+
                    " rawDirectParentDenied=true"+
                    " rawGrandparentDenied=true"+
                    " worldDirectParentDenied=true"+
                    " worldGrandparentDenied=true"+
                    " realRepositorySaveDeniedBeforeTemp=true"+
                    " accountAndReservationsUnchanged=true"+
                    " atomicExternalAncestorFence=false"+
                    " durableOwnerEpoch=false");
            }else{
                System.out.println(
                    "R34_JVM_RAW_WORLD_INODE_ANCESTRY_NOFOLLOW_PASS"+
                    " crossJvmOptInDisabled=true"+
                    " validMissingAccountAllowed=true"+
                    " symlinkNegative=UNSUPPORTED_WINDOWS"+
                    " durableOwnerEpoch=false");
            }
            check(LocalJvmAccountSessionLease.activeCountForTest()==0&&
                LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0&&
                LocalJvmAccountSessionLease
                    .pendingSessionAdmissionsForTest()==0,
                "opt-in ancestry fixture leaked reservation");
        }finally{
            for(int i=0;i<props.length;i++){
                if(prior[i]==null)System.clearProperty(props[i]);
                else System.setProperty(props[i],prior[i]);
            }
            try(Stream<Path> all=Files.walk(root)){
                for(Path entry:all.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(entry);
            }
        }
    }
}
