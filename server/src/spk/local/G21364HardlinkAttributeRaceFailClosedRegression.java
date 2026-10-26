package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * R36: independently opt-in account nlink checks must not confuse an
 * I/O-unknown pathname with an absent account or accept a different
 * file object after their first NOFOLLOW attribute observation.
 *
 * Deterministic identity-swap hook is test-only. This does not give
 * cross-process fencing or atomicity between final check and write.
 */
public final class G21364HardlinkAttributeRaceFailClosedRegression {
    private static final String USER="r36-nlink-race";
    private G21364HardlinkAttributeRaceFailClosedRegression(){}

    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError("R36_NLINK "+reason);
    }

    private static Object fileKey(Path file)throws IOException{
        BasicFileAttributes attrs=Files.readAttributes(
            file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        check(attrs.isRegularFile()&&attrs.fileKey()!=null,
            "invalid fixture account file");
        return attrs.fileKey();
    }

    private static void swapIdenticalAccountObject(Path path)
        throws IOException{
        byte[] bytes=Files.readAllBytes(path);
        Object original=fileKey(path);
        Path replacement=Files.createTempFile(
            path.getParent(),"r36-swap-", ".properties");
        Files.write(replacement,bytes);
        Files.move(replacement,path,StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);
        check(!original.equals(fileKey(path))&&
            Arrays.equals(bytes,Files.readAllBytes(path)),
            "fixture did not replace inode with identical bytes");
    }

    private static void assertNoTemp(Path file)throws IOException{
        check(!Files.exists(file.resolveSibling(
                file.getFileName().toString()+".tmp"),
                LinkOption.NOFOLLOW_LINKS),
            "denied raw write left fixed temporary file");
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("r36-nlink-race-");
        String[] flags={
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY,
            LocalJvmAccountSessionLease.INODE_ALIAS_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY
        };
        String[] previous=new String[flags.length];
        for(int i=0;i<flags.length;i++)
            previous[i]=System.getProperty(flags[i]);
        System.setProperty(flags[0],"true");
        System.setProperty(flags[1],"true");
        System.setProperty(flags[2],"true");
        System.setProperty(flags[3],"false");
        System.setProperty(flags[4],"false");
        try{
            Path account=root.resolve(USER+".properties");
            FilePlayerRepository repo=new FilePlayerRepository(
                ignored->account);
            World world=World.isolatedForTest(60_000L,repo);
            try{
                check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                    "missing bootstrap fixture already present");
                try(LocalJvmAccountSessionLease.RawWriteReservation raw=
                        LocalJvmAccountSessionLease.reserveRawWrite(
                            account,USER)){
                    check(raw!=null,
                        "legitimate missing RAW account was refused");
                }
                Files.writeString(account,"unchanged-account-postimage",
                    StandardCharsets.US_ASCII);
                byte[] baseline=Files.readAllBytes(account);

                AtomicBoolean armed=new AtomicBoolean(true);
                LocalJvmAccountSessionLease.hardlinkObservationProbeForTest(
                    path->{
                        if(path.equals(account)&&
                           armed.compareAndSet(true,false))
                            swapIdenticalAccountObject(path);
                    });
                boolean rawRefused=false;
                try(LocalJvmAccountSessionLease.RawWriteReservation raw=
                        LocalJvmAccountSessionLease.reserveRawWrite(
                            account,USER)){
                    check(false,"replacement inode unexpectedly reserved");
                }catch(IOException expected){
                    rawRefused=String.valueOf(expected.getMessage())
                        .contains(
                            "R36_HARDLINK_ACCOUNT_FILE_OBJECT_CHANGED_DURING_READ");
                }
                check(!armed.get()&&rawRefused,
                    "RAW admission accepted swapped exact account inode");
                assertNoTemp(account);
                check(Arrays.equals(baseline,Files.readAllBytes(account)),
                    "rejected RAW observation modified account bytes");
                check(LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0,
                    "rejected RAW observation leaked reservation");

                armed.set(true);
                boolean worldRefused=false;
                try{
                    LocalJvmAccountSessionLease.Token token=
                        LocalJvmAccountSessionLease.acquire(
                            world.persistence(),USER);
                    if(token!=null)
                        token.releaseAfterInitializationFailure();
                }catch(IllegalStateException expected){
                    worldRefused=String.valueOf(expected.getMessage())
                        .contains("HARDLINK_ACCOUNT_ADMISSION_DENIED")&&
                        expected.getCause()!=null&&
                        String.valueOf(expected.getCause().getMessage())
                            .contains(
                                "R36_HARDLINK_ACCOUNT_FILE_OBJECT_CHANGED_DURING_READ");
                }
                check(!armed.get()&&worldRefused,
                    "World admission accepted changed inode");
                check(LocalJvmAccountSessionLease.activeCountForTest()==0&&
                      LocalJvmAccountSessionLease
                          .pendingSessionAdmissionsForTest()==0,
                    "denied World admission leaked token");

                armed.set(true);
                boolean actualRawRefused=false;
                try{
                    repo.save(PlayerSnapshotCodec.capture(
                        USER,new WorldPlayer()));
                }catch(IOException expected){
                    actualRawRefused=String.valueOf(expected.getMessage())
                        .contains(
                            "R36_HARDLINK_ACCOUNT_FILE_OBJECT_CHANGED_DURING_READ");
                }
                check(!armed.get()&&actualRawRefused,
                    "actual repository RAW write accepted changed inode");
                assertNoTemp(account);
                check(Arrays.equals(baseline,Files.readAllBytes(account)),
                    "rejected real RAW changed account postimage");

                LocalJvmAccountSessionLease
                    .hardlinkObservationProbeForTest(null);
                // A still-present real inode with nlink=2 remains
                // disallowed even after the race instrumentation ends.
                Path alias=root.resolve("account-alias.properties");
                Files.createLink(alias,account);
                boolean multipleDenied=false;
                try{
                    LocalJvmAccountSessionLease.reserveRawWrite(
                        account,USER);
                }catch(IOException expected){
                    multipleDenied=String.valueOf(expected.getMessage())
                        .contains("MULTIPLE_ACCOUNT_HARDLINKS_DENIED");
                }
                check(multipleDenied,
                    "nlink >1 admission refused to fail closed");
                Files.delete(alias);

                repo.save(PlayerSnapshotCodec.capture(
                    USER,new WorldPlayer()));
                check(repo.load(USER).isPresent(),
                    "legitimate post-race RAW retry was not readable");
                LocalJvmAccountSessionLease.Token healthy=
                    LocalJvmAccountSessionLease.acquire(
                        world.persistence(),USER);
                check(healthy!=null,
                    "legitimate post-race World admission was denied");
                healthy.releaseAfterInitializationFailure();

                check(LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0&&
                    LocalJvmAccountSessionLease.activeCountForTest()==0&&
                    LocalJvmAccountSessionLease
                        .pendingSessionAdmissionsForTest()==0,
                    "final reservations not clean");

                System.out.println(
                    "R36_HARDLINK_ATTRIBUTE_RACE_FAIL_CLOSED_PASS"+
                    " sameBytesNewInodeRawReservationDenied=true"+
                    " sameBytesNewInodeWorldAdmissionDenied=true"+
                    " actualRawRepositorySaveDeniedBeforeTemp=true"+
                    " trueMissingBootstrapAllowed=true"+
                    " nlinkTwoDenied=true"+
                    " healthyRetryAllowed=true"+
                    " bytesAndReservationAccountingPreserved=true"+
                    " inodeAliasOptInDisabled=true"+
                    " crossJvmLockDisabled=true"+
                    " uncooperativeAtomicFencing=false"+
                    " crashDurableEpoch=false");
            }finally{
                world.close();
            }
        }finally{
            LocalJvmAccountSessionLease.hardlinkObservationProbeForTest(
                null);
            for(int i=0;i<flags.length;i++){
                if(previous[i]==null)System.clearProperty(flags[i]);
                else System.setProperty(flags[i],previous[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path entry:walk.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(entry);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        if(System.getProperty("os.name").toLowerCase().contains("win")){
            System.out.println(
                "R36_HARDLINK_ATTRIBUTE_RACE_NOT_EXERCISED_WINDOWS"+
                " unixNlinkAndInodeSemanticsUnproven=true");
            return;
        }
        run();
    }
}
