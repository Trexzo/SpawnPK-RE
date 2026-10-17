package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/** G21.244 deterministic real file-object substitution at RAW/strict pre-move. */
public final class G21244RawAndStrictLockObjectContinuityVetoTest {
    private static final String RAW="g21244-raw-lock-object";
    private static final String STRICT="g21244-strict-lock-object";
    private static final String NS="core_g21244";
    private static final String KEY="extension.core_g21244.phase";
    private G21244RawAndStrictLockObjectContinuityVetoTest(){}

    private static void check(boolean yes,String stage){
        if(!yes)throw new AssertionError("G21.244 "+stage);
    }

    private static void substituteLock(Path account)throws IOException{
        Path lock=account.resolveSibling(
            account.getFileName().toString()+".session.lck");
        BasicFileAttributes before=Files.readAttributes(
            lock,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        check(before.fileKey()!=null,"original lock object unknown");
        Files.delete(lock);
        Files.createFile(lock);
        BasicFileAttributes after=Files.readAttributes(
            lock,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        check(after.fileKey()!=null&&
            !after.fileKey().equals(before.fileKey()),
            "real lock object substitution did not occur");
    }

    private static PlayerSnapshot snapshot(String username,String phase){
        WorldPlayer player=new WorldPlayer();
        player.snapshotExtensions().replaceNamespace(
            NS,Collections.singletonMap("phase",phase));
        return PlayerSnapshotCodec.capture(username,player);
    }

    private static String disk(FilePlayerRepository repo,String username)
        throws IOException{
        Optional<PlayerSnapshot> found=repo.load(username);
        check(found.isPresent(),"missing "+username+" disk postimage");
        return found.get().values().get(KEY);
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21244-raw-lock-");
        String[] flags={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY
        };
        String[] original=new String[flags.length];
        for(int i=0;i<flags.length;i++)original[i]=System.getProperty(flags[i]);
        System.setProperty(flags[0],root.resolve("opensrc.properties").toString());
        for(int i=1;i<flags.length;i++)System.setProperty(flags[i],"true");
        try{
            FilePlayerRepository baseline=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            baseline.save(snapshot(RAW,"RAW_BASE"));
            check("RAW_BASE".equals(disk(baseline,RAW)),
                "RAW baseline did not persist");
            AtomicBoolean rawHook=new AtomicBoolean();
            FilePlayerRepository rawSwapped=new FilePlayerRepository(
                LocalAccountProfiles::accountFile,
                ignored->{},ignored->{},ignored->{},ignored->{},
                ignored->{
                    rawHook.set(true);
                    substituteLock(LocalAccountProfiles.accountFile(RAW));
                });
            boolean vetoed=false;
            try{rawSwapped.save(snapshot(RAW,"RAW_UNSAFE"));}
            catch(IOException expected){
                vetoed=String.valueOf(expected.getMessage())
                    .contains("SESSION_LOCK_FILE_OBJECT_CHANGED");
            }
            check(rawHook.get()&&vetoed,
                "repository RAW publication did not reject lock substitution");
            check("RAW_BASE".equals(disk(baseline,RAW)),
                "repository RAW replaced old postimage despite veto");
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "repository RAW reservation leaked");

            StrictDurablePlayerSnapshotWriter strictBaseline=
                new StrictDurablePlayerSnapshotWriter(
                    LocalAccountProfiles::accountFile);
            StrictDurablePlayerSnapshotWriter.Receipt initial=
                strictBaseline.saveStrict(snapshot(STRICT,"STRICT_BASE"));
            check(initial!=null&&
                "STRICT_BASE".equals(disk(baseline,STRICT)),
                "strict baseline not persisted");

            AtomicBoolean strictHook=new AtomicBoolean();
            StrictDurablePlayerSnapshotWriter strictSwapped=
                new StrictDurablePlayerSnapshotWriter(
                    LocalAccountProfiles::accountFile,phase->{
                        if(phase==StrictDurablePlayerSnapshotWriter.Phase
                                .BEFORE_ATOMIC_REPLACE){
                            strictHook.set(true);
                            substituteLock(LocalAccountProfiles.accountFile(STRICT));
                        }
                    });
            vetoed=false;
            try{strictSwapped.saveStrict(snapshot(STRICT,"STRICT_UNSAFE"));}
            catch(IOException expected){
                vetoed=String.valueOf(expected.getMessage())
                    .contains("SESSION_LOCK_FILE_OBJECT_CHANGED");
            }
            check(strictHook.get()&&vetoed,
                "standalone strict publication did not reject lock substitution");
            check("STRICT_BASE".equals(disk(baseline,STRICT)),
                "standalone strict overwrote prior postimage despite veto");
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "strict writer RAW reservation leaked");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "test leaked active JVM account owner");
            System.out.println(
                "G21244_RAW_AND_STRICT_PREMOVE_LOCK_OBJECT_VETO_PASS"+
                " repositoryRawVeto=true standaloneStrictVeto=true"+
                " originalPostimagesPreserved=true"+
                " noReservationLeak=true"+
                " postCheckHostileSwapRaceStillOpen=true"+
                " durableEpoch=false");
        }finally{
            for(int i=0;i<flags.length;i++){
                if(original[i]==null)System.clearProperty(flags[i]);
                else System.setProperty(flags[i],original[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:(Iterable<Path>)walk.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(p);
            }
        }
    }
}
