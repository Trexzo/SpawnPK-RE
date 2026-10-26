package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * R42: each live opt-out guard must retain optional same-inode alias
 * exclusion. A second pathname (hardlink) for one existing selected
 * account cannot bypass a pending/live World owner or reserved RAW
 * writer simply by toggling RAW / World participation off.
 *
 * Observational, same-JVM, pre-existing account fileKey only.
 * No atomic external hardlink fence, cross-JVM epoch, or crash ACK.
 */
public final class G21370LiveFlagOptOutHardlinkAliasRegression {
    private static final String USER="r42-hardlink";
    private G21370LiveFlagOptOutHardlinkAliasRegression(){}

    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("R42_LIVE_ALIAS "+why);
    }

    private static PlayerSnapshot snapshot(String username){
        return PlayerSnapshotCodec.capture(username,new WorldPlayer());
    }

    private static boolean rawDenied(
        FilePlayerRepository aliasRepository
    ){
        try{
            aliasRepository.save(snapshot(USER));
            return false;
        }catch(IOException expected){
            return String.valueOf(expected.getMessage()).contains(
                "R32 SAME_FILE_OBJECT_ALIAS_RAW_WRITE_DENIED");
        }
    }

    private static boolean sessionDenied(World aliasWorld){
        try{
            LocalJvmAccountSessionLease.Token wrong=
                LocalJvmAccountSessionLease.acquire(
                    aliasWorld.persistence(),USER);
            if(wrong!=null)
                wrong.releaseAfterInitializationFailure();
            return false;
        }catch(IllegalStateException expected){
            return String.valueOf(expected.getMessage()).contains(
                "R32 SAME_FILE_OBJECT_ALIAS_SESSION_DENIED_RAW_WRITER");
        }
    }

    private static void preserved(Path account,Path alias,byte[] before)
        throws IOException{
        check(Files.isSameFile(account,alias),
            "negative attempt changed hardlink relationship");
        check(Arrays.equals(before,Files.readAllBytes(account))&&
            Arrays.equals(before,Files.readAllBytes(alias)),
            "negative attempt changed account or alias bytes");
        check(!Files.exists(alias.resolveSibling(
                alias.getFileName().toString()+".tmp"),
                LinkOption.NOFOLLOW_LINKS),
            "denied RAW wrote fixed temp via alias");
    }

    private static void pendingClaim(
        World sourceWorld,FilePlayerRepository aliasRepository,
        Path source,Path alias,byte[] before
    )throws Exception{
        CountDownLatch pending=new CountDownLatch(1);
        CountDownLatch proceed=new CountDownLatch(1);
        AtomicReference<Throwable> failure=new AtomicReference<>();
        AtomicReference<LocalJvmAccountSessionLease.Token>
            original=new AtomicReference<>();
        LocalJvmAccountSessionLease.pendingSessionAdmissionProbeForTest(
            (file,user)->{
                if(source.equals(file)&&USER.equals(user)){
                    pending.countDown();
                    try{
                        if(!proceed.await(10L,TimeUnit.SECONDS))
                            throw new IllegalStateException(
                                "R42_PENDING_PROBE_TIMEOUT");
                    }catch(InterruptedException unexpected){
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(
                            "R42_PENDING_PROBE_INTERRUPTED",unexpected);
                    }
                }
            });
        Thread worker=new Thread(()->{
            try{
                original.set(LocalJvmAccountSessionLease.acquire(
                    sourceWorld.persistence(),USER));
            }catch(Throwable failed){
                failure.set(failed);
            }
        },"r42-pending-original-owner");
        try{
            worker.start();
            check(pending.await(10L,TimeUnit.SECONDS),
                "World did not reach pending claim");
            check(LocalJvmAccountSessionLease
                    .pendingSessionAdmissionsForTest()==1,
                "pending inode claim not established");
            System.setProperty(
                LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
                "false");
            check(rawDenied(aliasRepository),
                "flag-off RAW bypassed pending hardlinked World owner");
            preserved(source,alias,before);
            check(LocalJvmAccountSessionLease
                    .pendingSessionAdmissionsForTest()==1,
                "denied RAW displaced original pending owner");
        }finally{
            System.setProperty(
                LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
                "true");
            proceed.countDown();
            worker.join(12_000L);
            LocalJvmAccountSessionLease
                .pendingSessionAdmissionProbeForTest(null);
        }
        check(!worker.isAlive()&&failure.get()==null&&
            original.get()!=null,
            "pending original did not finalize "+failure.get());
        original.get().releaseAfterInitializationFailure();
        check(LocalJvmAccountSessionLease
                .pendingSessionAdmissionsForTest()==0&&
            LocalJvmAccountSessionLease.activeCountForTest()==0,
            "pending-original fixture leaked reservation");
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        if(System.getProperty("os.name").toLowerCase().contains("win")){
            System.out.println(
                "R42_LIVE_FLAG_OPT_OUT_ALIAS_NOT_EXERCISED_WINDOWS"+
                " hardlinkInodeProviderUnproven=true");
            return;
        }
        Path root=Files.createTempDirectory("r42-live-alias-");
        String[] flags={
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalJvmAccountSessionLease.INODE_ALIAS_GATE_PROPERTY,
            LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY
        };
        String[] beforeFlag=new String[flags.length];
        for(int i=0;i<flags.length;i++)
            beforeFlag[i]=System.getProperty(flags[i]);
        System.setProperty(flags[0],"true");
        System.setProperty(flags[1],"true");
        System.setProperty(flags[2],"true");
        System.setProperty(flags[3],"false");
        System.setProperty(flags[4],"false");
        World firstWorld=null,aliasWorld=null;
        try{
            Path source=root.resolve("source.properties");
            Path alias=root.resolve("alias.properties");
            Path other=root.resolve("other.properties");
            FilePlayerRepository originalRepository=
                new FilePlayerRepository(ignored->source);
            FilePlayerRepository aliasRepository=
                new FilePlayerRepository(ignored->alias);
            FilePlayerRepository otherRepository=
                new FilePlayerRepository(ignored->other);
            originalRepository.save(snapshot(USER));
            Files.createLink(alias,source);
            byte[] baseline=Files.readAllBytes(source);
            check(Files.isSameFile(source,alias),
                "fixture does not share existing inode");

            firstWorld=World.isolatedForTest(60_000L,originalRepository);
            aliasWorld=World.isolatedForTest(60_000L,aliasRepository);
            LocalJvmAccountSessionLease.Token worldToken=
                LocalJvmAccountSessionLease.acquire(
                    firstWorld.persistence(),USER);
            check(worldToken!=null,
                "original world did not reserve existing account");
            try{
                System.setProperty(
                    LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
                    "false");
                check(rawDenied(aliasRepository),
                    "flag-off RAW bypassed committed World inode owner");
                preserved(source,alias,baseline);
                check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                    "RAW denial released rightful World token");
                // Independent file objects retain historical RAW
                // behavior, even while other username's token lives.
                otherRepository.save(snapshot("r42-unrelated"));
                check(Files.isRegularFile(other,
                    LinkOption.NOFOLLOW_LINKS),
                    "unrelated RAW was unnecessarily blocked");
            }finally{
                System.setProperty(
                    LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
                    "true");
                worldToken.releaseAfterInitializationFailure();
            }

            try(LocalJvmAccountSessionLease.RawWriteReservation raw=
                    LocalJvmAccountSessionLease.reserveRawWrite(
                        source,USER)){
                check(raw!=null,"original RAW reservation missing");
                System.setProperty(
                    LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
                    "false");
                check(rawDenied(aliasRepository),
                    "flag-off RAW bypassed RAW inode owner");
                preserved(source,alias,baseline);
                System.setProperty(
                    LocalJvmAccountSessionLease.PROPERTY,"false");
                check(sessionDenied(aliasWorld),
                    "flag-off World lease bypassed held RAW inode");
                check(LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==1&&
                    LocalJvmAccountSessionLease.activeCountForTest()==0,
                    "negative alias admission corrupted rightful RAW owner");
            }finally{
                System.setProperty(LocalJvmAccountSessionLease.PROPERTY,"true");
                System.setProperty(
                    LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
                    "true");
            }
            pendingClaim(firstWorld,aliasRepository,
                source,alias,baseline);
            check(LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0&&
                LocalJvmAccountSessionLease.activeCountForTest()==0&&
                LocalJvmAccountSessionLease
                    .pendingSessionAdmissionsForTest()==0,
                "final opt-out accounting leaked");

            // Once all owners close, exact hardlink alias remains a
            // legitimate independent legacy RAW target in default mode.
            System.setProperty(
                LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
                "false");
            aliasRepository.save(snapshot(USER));
            check(Files.isRegularFile(alias,
                LinkOption.NOFOLLOW_LINKS),
                "healthy post-release RAW retry denied");
            check(LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0,
                "healthy legacy path unexpectedly reserved RAW");

            System.out.println(
                "R42_LIVE_FLAG_OPT_OUT_HARDLINK_ALIAS_PASS"+
                " realSameInodeHardlink=true"+
                " liveWorldAliasRawOptOutDenied=true"+
                " pendingWorldAliasRawOptOutDenied=true"+
                " rawOwnerAliasRawOptOutDenied=true"+
                " rawOwnerAliasWorldOptOutDenied=true"+
                " exactBytesPreservedOnDenial=true"+
                " rightfulOwnersAndPendingUnchanged=true"+
                " unrelatedAccountRawAllowed=true"+
                " unownedDefaultOffRetryAllowed=true"+
                " globalAuthenticatedEpoch=false"+
                " postMoveExternalRaceUnfenced=true"+
                " originalV308ClientNotRun=true");
        }finally{
            LocalJvmAccountSessionLease
                .pendingSessionAdmissionProbeForTest(null);
            if(firstWorld!=null)firstWorld.close();
            if(aliasWorld!=null)aliasWorld.close();
            for(int i=0;i<flags.length;i++){
                if(beforeFlag[i]==null)
                    System.clearProperty(flags[i]);
                else System.setProperty(flags[i],beforeFlag[i]);
            }
            try(Stream<Path> walked=Files.walk(root)){
                for(Path file:walked.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(file);
            }
        }
    }
}
