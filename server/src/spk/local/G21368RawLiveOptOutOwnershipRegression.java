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
 * R40: RAW opt-in cannot be toggled OFF during a held same-JVM World
 * lease, pending World admission or already reserved RAW writer and
 * thereby bypass existing same-account ownership. The wholly idle
 * historical default-off writer remains unchanged. No cross-JVM
 * fencing, account epoch, or durable negative-failure intent.
 */
public final class G21368RawLiveOptOutOwnershipRegression {
    private static final String NAMED="r40-named";
    private static final String REPO="r40-repository";
    private G21368RawLiveOptOutOwnershipRegression(){}
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("R40_RAW_OPT_OUT "+why);
    }

    @FunctionalInterface interface Writer {
        void save()throws IOException;
    }

    private static boolean vetoed(Writer writer){
        try{
            writer.save();
            return false;
        }catch(IOException expected){
            return String.valueOf(expected.getMessage()).contains(
                "R40_RAW_OPT_OUT_DURING_ACCOUNT_OWNERSHIP_DENIED");
        }
    }

    private static void unchanged(Path account,byte[] bytes)
        throws IOException{
        check(Arrays.equals(bytes,Files.readAllBytes(account)),
            "denied writer modified existing account");
        check(!Files.exists(account.resolveSibling(
                account.getFileName().toString()+".tmp"),
                LinkOption.NOFOLLOW_LINKS),
            "denied writer left legacy fixed RAW temp");
    }

    private static void liveOwner(
        World world,String username,Path path,Writer writer
    )throws Exception{
        byte[] initial=Files.readAllBytes(path);
        LocalJvmAccountSessionLease.Token token=
            LocalJvmAccountSessionLease.acquire(
                world.persistence(),username);
        check(token!=null,"opt-in World reservation missing");
        try{
            System.setProperty(
                LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
                "false");
            check(vetoed(writer),
                "RAW escaped through disabled participation under World");
            unchanged(path,initial);
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "veto removed rightful World token");
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "veto created additional RAW reservation");
        }finally{
            System.setProperty(
                LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
                "true");
            token.releaseAfterInitializationFailure();
        }
        check(LocalJvmAccountSessionLease.activeCountForTest()==0,
            "world token not released at fixture cleanup");
    }

    private static void rawOwner(String name,Path path,Writer writer)
        throws Exception{
        byte[] initial=Files.readAllBytes(path);
        try(LocalJvmAccountSessionLease.RawWriteReservation held=
                LocalJvmAccountSessionLease.reserveRawWrite(
                    path,name)){
            check(held!=null,"RAW holder missing");
            System.setProperty(
                LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
                "false");
            check(vetoed(writer),
                "late flag toggle evaded held RAW reservation");
            unchanged(path,initial);
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==1,
                "rejected RAW attempted to replace holder reservation");
        }finally{
            System.setProperty(
                LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
                "true");
        }
        check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
            "RAW holder leaked reservation");
    }

    private static void pendingOwner(
        World world,String name,Path account,Writer writer
    )throws Exception{
        final CountDownLatch entered=new CountDownLatch(1);
        final CountDownLatch continueAdmission=new CountDownLatch(1);
        final AtomicReference<Throwable> failure=new AtomicReference<>();
        byte[] initial=Files.readAllBytes(account);
        Thread session=new Thread(()->{
            LocalJvmAccountSessionLease.Token token=null;
            try{
                token=LocalJvmAccountSessionLease.acquire(
                    world.persistence(),name);
            }catch(Throwable problem){
                failure.set(problem);
            }finally{
                if(token!=null){
                    try{
                        token.releaseAfterInitializationFailure();
                    }catch(Throwable problem){
                        failure.set(problem);
                    }
                }
            }
        },"r40-pending-session-owner");
        LocalJvmAccountSessionLease.pendingSessionAdmissionProbeForTest(
            (path,username)->{
                if(account.equals(path)&&name.equals(username)){
                    entered.countDown();
                    try{
                        if(!continueAdmission.await(
                                10L,TimeUnit.SECONDS))
                            throw new AssertionError(
                                "pending test failed to resume");
                    }catch(InterruptedException interrupted){
                        Thread.currentThread().interrupt();
                        throw new AssertionError(
                            "pending test interrupted",interrupted);
                    }
                }
            });
        try{
            session.start();
            check(entered.await(10L,TimeUnit.SECONDS),
                "pending admission probe never entered");
            check(LocalJvmAccountSessionLease
                    .pendingSessionAdmissionsForTest()==1,
                "World pending identity absent");
            System.setProperty(
                LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
                "false");
            check(vetoed(writer),
                "RAW bypassed already pending World admission");
            unchanged(account,initial);
            check(LocalJvmAccountSessionLease
                    .pendingSessionAdmissionsForTest()==1,
                "denied RAW displaced pending World claim");
        }finally{
            System.setProperty(
                LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
                "true");
            continueAdmission.countDown();
            session.join(12_000L);
            LocalJvmAccountSessionLease
                .pendingSessionAdmissionProbeForTest(null);
        }
        check(!session.isAlive()&&failure.get()==null,
            "pending World worker did not finish "+failure.get());
        check(LocalJvmAccountSessionLease
                .pendingSessionAdmissionsForTest()==0&&
              LocalJvmAccountSessionLease.activeCountForTest()==0,
            "pending World admission leaked reservation");
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("r40-raw-opt-out-");
        String[] flags={"spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY};
        String[] previous=new String[flags.length];
        for(int i=0;i<flags.length;i++)
            previous[i]=System.getProperty(flags[i]);
        System.setProperty(flags[0],
            root.resolve("opensrc.properties").toString());
        System.setProperty(flags[1],"true");
        System.setProperty(flags[2],"true");
        System.setProperty(flags[3],"false");
        World world=null;
        try{
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            WorldPlayer state=new WorldPlayer();
            Writer primary=()->AccountStore.save(
                state.bank(),state.equipment(),state.movement(),
                state.petState(),state.playerState());
            Writer named=()->LocalAccountProfiles.save(NAMED,
                state.bank(),state.equipment(),state.movement(),
                state.petState(),state.playerState());
            Writer repository=()->repo.save(PlayerSnapshotCodec.capture(
                REPO,new WorldPlayer()));
            Writer[] saves={primary,named,repository};
            String[] names={AccountStore.CANONICAL_USERNAME,NAMED,REPO};
            Path[] files={AccountStore.accountFile(),
                LocalAccountProfiles.accountFile(NAMED),
                LocalAccountProfiles.accountFile(REPO)};
            for(Writer save:saves)save.save();
            world=World.isolatedForTest(60_000L,repo);
            for(int i=0;i<saves.length;i++){
                liveOwner(world,names[i],files[i],saves[i]);
                rawOwner(names[i],files[i],saves[i]);
                // After the two owners release, the historical
                // unguarded RAW writer still works if the flag is OFF.
                System.setProperty(
                    LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
                    "false");
                saves[i].save();
                System.setProperty(
                    LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
                    "true");
                check(Files.isRegularFile(files[i],
                    LinkOption.NOFOLLOW_LINKS),
                    "healthy legacy save denied after owner release");
            }
            pendingOwner(world,REPO,files[2],repository);

            System.setProperty(
                LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
                "false");
            repo.save(PlayerSnapshotCodec.capture(
                "r40-unrelated",new WorldPlayer()));
            check(repo.load("r40-unrelated").isPresent(),
                "unrelated default-off account RAW denied");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0&&
                LocalJvmAccountSessionLease
                    .pendingSessionAdmissionsForTest()==0&&
                LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0,
                "test left account ownership reservation");
            System.out.println(
                "R40_RAW_OPT_OUT_LIVE_OWNERSHIP_PASS"+
                " worldOwnedPrimaryNamedRepositoryDenied=true"+
                " rawOwnedPrimaryNamedRepositoryDenied=true"+
                " pendingWorldAdmissionDenied=true"+
                " deniedPreTempAndPriorBytesPreserved=true"+
                " retainedExistingOwnerAccounting=true"+
                " historicalUnleasedRawRemainsAllowed=true"+
                " unrelatedAccountStillAllowed=true"+
                " trueLateToggleIsInvalid=true"+
                " sameJvmOnly=true crossProcessEpoch=false"+
                " crashDurableFinalSave=false");
        }finally{
            LocalJvmAccountSessionLease
                .pendingSessionAdmissionProbeForTest(null);
            if(world!=null)world.close();
            for(int i=0;i<flags.length;i++){
                if(previous[i]==null)System.clearProperty(flags[i]);
                else System.setProperty(flags[i],previous[i]);
            }
            try(Stream<Path> paths=Files.walk(root)){
                for(Path file:paths.sorted(
                        Comparator.reverseOrder()).toArray(Path[]::new))
                    Files.deleteIfExists(file);
            }
        }
    }
}
