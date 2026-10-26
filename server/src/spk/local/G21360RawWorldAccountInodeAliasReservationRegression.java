package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * R32: opt-in same-JVM account fileKey guard now includes cooperating
 * RAW writer reservations as well as pending and live World sessions.
 * This is not a cross-JVM inode lock, raw external writer exclusion,
 * post-atomic-move file identity guarantee or crash-durable owner epoch.
 */
public final class G21360RawWorldAccountInodeAliasReservationRegression {
    private static final String USER="g21360-inode-alias-owner";
    private G21360RawWorldAccountInodeAliasReservationRegression(){}

    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError("R32_RAW_ALIAS "+reason);
    }

    private static boolean rawDenied(Path path){
        try(LocalJvmAccountSessionLease.RawWriteReservation permit=
                LocalJvmAccountSessionLease.reserveRawWrite(path,USER)){
            return false;
        }catch(IOException expected){
            return expected.getMessage()!=null&&
                expected.getMessage().contains(
                    "SAME_FILE_OBJECT_ALIAS_RAW_WRITE_DENIED");
        }
    }

    private static boolean sessionDenied(World world){
        try{
            LocalJvmAccountSessionLease.Token unexpected=
                LocalJvmAccountSessionLease.acquire(
                    world.persistence(),USER);
            if(unexpected!=null)
                unexpected.releaseAfterInitializationFailure();
            return false;
        }catch(IllegalStateException expected){
            return expected.getMessage()!=null&&
                expected.getMessage().contains(
                    "SAME_FILE_OBJECT_ALIAS_SESSION_DENIED_RAW_WRITER");
        }
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("r32-raw-alias-");
        String[] keys={
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalJvmAccountSessionLease.INODE_ALIAS_GATE_PROPERTY,
            LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY
        };
        String[] old=new String[keys.length];
        for(int i=0;i<keys.length;i++)old[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],"true");
        System.setProperty(keys[1],"true");
        System.setProperty(keys[2],"true");
        System.setProperty(keys[3],"false");
        System.setProperty(keys[4],"false");

        Path first=root.resolve("account-A.properties");
        Path alias=root.resolve("account-B.properties");
        Path unrelated=root.resolve("unrelated.properties");
        World original=null,alternate=null,independent=null;
        WorldPlayer owner=null;
        LocalJvmAccountSessionLease.Token token=null;
        long generation=-1L;
        Thread pending=null;
        CountDownLatch allowPending=new CountDownLatch(1);
        try{
            Files.writeString(first,"original-preimage",StandardCharsets.UTF_8);
            Files.createLink(alias,first);
            Files.writeString(unrelated,"independent",StandardCharsets.UTF_8);
            check(!first.equals(alias)&&Files.isSameFile(first,alias)&&
                !Files.isSameFile(first,unrelated),
                "fixture lacks hardlink identity");

            FilePlayerRepository originalRepo=
                new FilePlayerRepository(ignored->first);
            FilePlayerRepository aliasRepo=
                new FilePlayerRepository(ignored->alias);
            original=World.isolatedForTest(60_000L,originalRepo);
            alternate=World.isolatedForTest(60_000L,aliasRepo);
            independent=World.isolatedForTest(60_000L,
                new FilePlayerRepository(ignored->unrelated));

            // Session-first: a RAW writer aimed at a different pathname
            // for the same inode is denied BEFORE creating a temp file.
            token=LocalJvmAccountSessionLease.acquire(
                original.persistence(),USER);
            check(token!=null,"session-first token absent");
            owner=new WorldPlayer();
            generation=original.registerPlayer(owner,USER);
            token.bindAdmittedWorldOwner(owner,generation);
            original.start();
            check(rawDenied(alias),"raw alias bypassed live World owner");
            boolean realRawDenied=false;
            try{
                aliasRepo.save(PlayerSnapshotCodec.capture(
                    USER,new WorldPlayer()));
            }catch(IOException expected){
                realRawDenied=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "SAME_FILE_OBJECT_ALIAS_RAW_WRITE_DENIED");
            }
            check(realRawDenied,
                "actual FilePlayerRepository.save bypassed World owner");
            check(!Files.exists(alias.resolveSibling(
                    alias.getFileName().toString()+".tmp"),
                    LinkOption.NOFOLLOW_LINKS),
                "rejected alias created raw temp");
            check("original-preimage".equals(
                Files.readString(first,StandardCharsets.UTF_8)),
                "denied raw alias changed original bytes");
            try(LocalJvmAccountSessionLease.RawWriteReservation control=
                    LocalJvmAccountSessionLease.reserveRawWrite(
                        unrelated,USER+"-other")){
                check(control!=null,"unrelated raw account denied");
            }
            check(original.unregisterPlayer(owner,generation),
                "could not unregister original World owner");
            token.releaseAfterInitializationFailure();
            token=null;owner=null;generation=-1L;

            // Pending-first: RAW alias cannot bypass a session that is
            // waiting to finish its admission work.
            CountDownLatch pendingReached=new CountDownLatch(1);
            AtomicReference<LocalJvmAccountSessionLease.Token>
                pendingToken=new AtomicReference<>();
            AtomicReference<Throwable> pendingError=
                new AtomicReference<>();
            LocalJvmAccountSessionLease.pendingSessionAdmissionProbeForTest(
                (path,account)->{
                    if(Thread.currentThread().getName().equals(
                            "r32-pending-session")){
                        pendingReached.countDown();
                        try{
                            check(allowPending.await(10L,TimeUnit.SECONDS),
                                "pending probe timed out");
                        }catch(InterruptedException interrupted){
                            Thread.currentThread().interrupt();
                            throw new AssertionError(interrupted);
                        }
                    }
                });
            final World sameWorld=original;
            pending=new Thread(()->{
                try{
                    pendingToken.set(
                        LocalJvmAccountSessionLease.acquire(
                            sameWorld.persistence(),USER));
                }catch(Throwable problem){pendingError.set(problem);}
            },"r32-pending-session");
            pending.start();
            check(pendingReached.await(5L,TimeUnit.SECONDS),
                "pending World reservation not reached");
            check(rawDenied(alias),
                "raw alias bypassed pending World admission");
            allowPending.countDown();
            pending.join(10_000L);
            check(!pending.isAlive()&&pendingError.get()==null&&
                pendingToken.get()!=null,
                "pending World did not complete "+pendingError.get());
            pendingToken.get().releaseAfterInitializationFailure();
            LocalJvmAccountSessionLease.pendingSessionAdmissionProbeForTest(
                null);

            // Raw-first: both a second RAW writer and a World session
            // on a different hardlink name are refused. An unrelated
            // account remains available throughout.
            try(LocalJvmAccountSessionLease.RawWriteReservation raw=
                    LocalJvmAccountSessionLease.reserveRawWrite(
                        first,USER)){
                check(raw!=null,"raw-first reservation missing");
                check(rawDenied(alias),
                    "second hardlinked raw reservation acquired");
                check(sessionDenied(alternate),
                    "World alias admitted behind pending RAW reservation");
                LocalJvmAccountSessionLease.Token control=
                    LocalJvmAccountSessionLease.acquire(
                        independent.persistence(),USER+"-other");
                check(control!=null,
                    "independent inode session denied by raw alias guard");
                control.releaseAfterInitializationFailure();
                check(LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==1,
                    "denied aliases altered RAW reservation count");
                check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                    "denied alias leaked a World session token");
            }
            check(LocalJvmAccountSessionLease
                .rawWriteReservationsForTest()==0,
                "RAW reservation remained after normal release");

            // Both paths are usable independently after the original
            // reservation ends (no actual write, so hardlink still exists).
            try(LocalJvmAccountSessionLease.RawWriteReservation raw=
                    LocalJvmAccountSessionLease.reserveRawWrite(
                        alias,USER)){
                check(raw!=null,"released RAW alias remained blocked");
            }
            check("original-preimage".equals(
                    Files.readString(alias,StandardCharsets.UTF_8))&&
                Files.isSameFile(first,alias),
                "guard changed account bytes or inode relationship");
            check(LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0&&
                LocalJvmAccountSessionLease.activeCountForTest()==0&&
                LocalJvmAccountSessionLease
                    .pendingSessionAdmissionsForTest()==0,
                "reservation accounting not clean");

            System.out.println(
                "R32_RAW_WORLD_INODE_ALIAS_RESERVATION_PASS"+
                " realHardlinks=true"+
                " liveWorldBlocksRawAlias=true"+
                " actualRawRepositoryWriteDenied=true"+
                " pendingWorldBlocksRawAlias=true"+
                " rawBlocksSecondRawAlias=true"+
                " rawBlocksWorldAlias=true"+
                " unrelatedInodeAllowed=true"+
                " releasedAliasReusable=true"+
                " rejectedNoTempAndNoMutation=true"+
                " crossJvmFileObjectEpoch=false"+
                " defaultOptInDisabled=true"+
                " crashDurable=false");
        }finally{
            allowPending.countDown();
            LocalJvmAccountSessionLease.pendingSessionAdmissionProbeForTest(
                null);
            if(pending!=null&&pending.isAlive())pending.join(10_000L);
            try{
                if(original!=null&&owner!=null&&generation>0L)
                    original.unregisterPlayer(owner,generation);
                if(token!=null)token.releaseAfterInitializationFailure();
                if(original!=null)original.close();
                if(alternate!=null)alternate.close();
                if(independent!=null)independent.close();
            }finally{
                for(int i=0;i<keys.length;i++){
                    if(old[i]==null)System.clearProperty(keys[i]);
                    else System.setProperty(keys[i],old[i]);
                }
                try(Stream<Path> entries=Files.walk(root)){
                    for(Path item:entries.sorted(
                            Comparator.reverseOrder()).toArray(Path[]::new))
                        Files.deleteIfExists(item);
                }
            }
        }
    }
}
