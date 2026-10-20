package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * R35: standalone opt-in NOFOLLOW account nlink admission must not
 * follow symbolic-link ancestors when the independent JVM inode-alias
 * and cross-process OS lock features are switched OFF.
 *
 * Observational pathname preflight only. No atomic path fencing,
 * authenticated cross-JVM epoch or crash-durable release guarantee.
 */
public final class G21363HardlinkCountStandaloneAncestorNofollowRegression {
    private static final String USER="r35-standalone-nlink";
    private G21363HardlinkCountStandaloneAncestorNofollowRegression(){}
    private static void check(boolean okay,String reason){
        if(!okay)throw new AssertionError("R35_STANDALONE_NLINK "+reason);
    }
    private static boolean rawDenied(Path file,String expected){
        try(LocalJvmAccountSessionLease.RawWriteReservation raw=
                LocalJvmAccountSessionLease.reserveRawWrite(file,USER)){
            return false;
        }catch(IOException refusal){
            return String.valueOf(refusal.getMessage())
                .contains(expected);
        }
    }
    private static boolean worldDenied(Path file){
        World world=World.isolatedForTest(60_000L,
            new FilePlayerRepository(ignored->file));
        try{
            try{
                LocalJvmAccountSessionLease.Token unexpected=
                    LocalJvmAccountSessionLease.acquire(
                        world.persistence(),USER);
                if(unexpected!=null)
                    unexpected.releaseAfterInitializationFailure();
                return false;
            }catch(IllegalStateException denial){
                return String.valueOf(denial.getMessage()).contains(
                    "HARDLINK_ACCOUNT_ADMISSION_DENIED");
            }
        }finally{
            world.close();
        }
    }
    private static boolean actualRawDenied(Path file,String marker)
        throws IOException{
        FilePlayerRepository repo=new FilePlayerRepository(
            ignored->file);
        try{
            repo.save(PlayerSnapshotCodec.capture(
                USER,new WorldPlayer()));
            return false;
        }catch(IOException failure){
            return String.valueOf(failure.getMessage())
                .contains(marker);
        }
    }
    private static void noTemp(Path file){
        check(!Files.exists(file.resolveSibling(
            file.getFileName().toString()+".tmp"),
            LinkOption.NOFOLLOW_LINKS),
            "refused account left RAW .tmp");
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        if(System.getProperty("os.name").toLowerCase().contains("win")){
            System.out.println(
                "R35_STANDALONE_NLINK_ANCESTRY_NOT_EXERCISED_WINDOWS"+
                " unsupportedUnixNlinkAndSymlinkPrivileges=true");
            return;
        }
        Path root=Files.createTempDirectory("r35-nlink-ancestry-");
        String[] keys={
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalJvmAccountSessionLease.INODE_ALIAS_GATE_PROPERTY,
            LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY
        };
        String[] previous=new String[keys.length];
        for(int i=0;i<keys.length;i++)
            previous[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],"true");
        System.setProperty(keys[1],"true");
        System.setProperty(keys[2],"false");
        System.setProperty(keys[3],"true");
        System.setProperty(keys[4],"false");
        try{
            Path real=root.resolve("real");
            Path dir=real.resolve("middle").resolve("accounts");
            Files.createDirectories(dir);
            Path healthy=dir.resolve("account.properties");
            check(!Files.exists(healthy,LinkOption.NOFOLLOW_LINKS),
                "positive bootstrap not missing");

            try(LocalJvmAccountSessionLease.RawWriteReservation raw=
                    LocalJvmAccountSessionLease.reserveRawWrite(
                        healthy,USER)){
                check(raw!=null,"valid absent RAW account denied");
            }
            World world=World.isolatedForTest(60_000L,
                new FilePlayerRepository(ignored->healthy));
            try{
                LocalJvmAccountSessionLease.Token token=
                    LocalJvmAccountSessionLease.acquire(
                        world.persistence(),USER);
                check(token!=null,"valid missing World account denied");
                token.releaseAfterInitializationFailure();
            }finally{
                world.close();
            }

            // The independent hardlink gate must veto both missing
            // and preexisting final leafs reached through symlinks.
            Path redirected=root.resolve("redirect");
            Files.createSymbolicLink(redirected,real);
            Path[] unsafe={
                redirected.resolve("direct.properties"),
                redirected.resolve("middle").resolve("accounts")
                    .resolve("grandparent.properties")
            };
            for(Path file:unsafe){
                check(rawDenied(file,
                        "ACCOUNT_LOCK_SYMLINK_ANCESTOR_NOFOLLOW"),
                    "standalone RAW hardlink gate followed ancestor");
                check(worldDenied(file),
                    "standalone World hardlink gate followed ancestor");
                check(actualRawDenied(file,
                        "ACCOUNT_LOCK_SYMLINK_ANCESTOR_NOFOLLOW"),
                    "real standalone repository RAW followed ancestor");
                noTemp(file);
                check(!Files.exists(file,LinkOption.NOFOLLOW_LINKS),
                    "denied account file was created");
            }

            FilePlayerRepository normal=new FilePlayerRepository(
                ignored->healthy);
            normal.save(PlayerSnapshotCodec.capture(
                USER,new WorldPlayer()));
            byte[] baseline=Files.readAllBytes(healthy);
            Path alias=dir.resolve("alias.properties");
            Files.createLink(alias,healthy);
            check(Files.isSameFile(healthy,alias),
                "real hardlink nlink fixture invalid");
            check(rawDenied(healthy,
                    "MULTIPLE_ACCOUNT_HARDLINKS_DENIED"),
                "multi-hardlink RAW account admitted");
            check(worldDenied(healthy),
                "multi-hardlink World account admitted");
            check(actualRawDenied(healthy,
                    "MULTIPLE_ACCOUNT_HARDLINKS_DENIED"),
                "actual RAW save permitted multiply-linked account");
            check(Arrays.equals(baseline,Files.readAllBytes(alias))&&
                  Arrays.equals(baseline,Files.readAllBytes(healthy)),
                "denied hardlink path changed account postimage");
            Files.delete(alias);

            try(LocalJvmAccountSessionLease.RawWriteReservation raw=
                    LocalJvmAccountSessionLease.reserveRawWrite(
                        healthy,USER)){
                check(raw!=null,
                    "single-link RAW was not usable after alias removal");
            }
            normal.save(PlayerSnapshotCodec.capture(
                USER,new WorldPlayer()));
            check(LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0&&
                LocalJvmAccountSessionLease.activeCountForTest()==0&&
                LocalJvmAccountSessionLease
                    .pendingSessionAdmissionsForTest()==0,
                "negative gate leaked reservation state");

            System.out.println(
                "R35_STANDALONE_NLINK_ANCESTRY_PASS"+
                " inodeAliasGateDisabled=true"+
                " crossJvmLockDisabled=true"+
                " missingAccountAdmissionAllowed=true"+
                " directSymlinkParentDenied=true"+
                " grandparentSymlinkDenied=true"+
                " actualRawSaveDeniedBeforeTemp=true"+
                " existingMultipleLinkDenied=true"+
                " verifiedRetryAfterAliasRemoval=true"+
                " diskBytesProtected=true"+
                " atomicPathPin=false crashDurableEpoch=false");
        }finally{
            for(int i=0;i<keys.length;i++){
                if(previous[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],previous[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path item:walk.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(item);
            }
        }
    }
}
