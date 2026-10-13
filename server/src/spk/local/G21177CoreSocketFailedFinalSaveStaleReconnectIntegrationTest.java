package spk.local;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.177: a characterization of the REAL missing durability
 * boundary after a failed final World save. This test documents the
 * old state that a fresh TCP session can still load; it does NOT
 * create or claim a persistent quarantine/replay/recovery system.
 */
public final class G21177CoreSocketFailedFinalSaveStaleReconnectIntegrationTest {
    private static final String USER="g21177-final-failure";
    private static final String NAMESPACE="core_final_failure";
    private static final String KEY="phase";
    private static final String PROPERTY="extension.core_final_failure.phase";
    private static final String OLD="PREVIOUS_DURABLE";
    private static final String NEW="NEW_UNSAVED";

    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError("G21.177 "+message);
    }

    @FunctionalInterface private interface Condition{
        boolean ready()throws Exception;
    }

    private static void await(Condition c,String label)throws Exception{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(6L);
        while(System.nanoTime()<until){
            if(c.ready())return;
            Thread.sleep(10L);
        }
        check(c.ready(),"timed out waiting for "+label);
    }

    private static String phase(WorldPlayer player){
        return player.snapshotExtensions().namespace(NAMESPACE).get(KEY);
    }

    private static void setPhase(World world,WorldPlayer player,String value)
        throws Exception{
        world.submit(player,player.generation(),()->{
            player.snapshotExtensions().replaceNamespace(
                NAMESPACE,Collections.singletonMap(KEY,value));
        }).get(4L,TimeUnit.SECONDS);
        check(value.equals(phase(player)),
            "World-owned mutation lost value="+value);
    }

    private static String diskValue(FilePlayerRepository repository)
        throws Exception{
        Optional<PlayerSnapshot> stored=repository.load(USER);
        check(stored.isPresent(),"real disk snapshot missing");
        return stored.get().values().get(PROPERTY);
    }

    /**
     * Bootstrap writes are queued independently of the post-login
     * socket success response. Wait until that actual repository write
     * completes before arming a FINAL-ONLY failure.
     * This reads the existing diagnostic metric; no production API change.
     */
    private static long completedWrites(World world){
        String metrics=world.persistence().metrics();
        String key=",completed=";
        int at=metrics.indexOf(key);
        check(at>=0,"persistence metrics lack completed counter");
        int start=at+key.length();
        int end=metrics.indexOf(',',start);
        check(end>start,"persistence metrics completed invalid");
        return Long.parseLong(metrics.substring(start,end));
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21177-final-save-failure-");
        String previous=System.getProperty("spk.local.accountFile");
        System.setProperty("spk.local.accountFile",
            root.resolve("opensrc.properties").toString());
        World firstWorld=null;
        World failingWorld=null;
        World freshWorld=null;
        try{
            Path file=LocalAccountProfiles.accountFile(USER);
            Path secondary=LocalAccountProfiles.accountFile("src");
            FilePlayerRepository normalRepository=
                new FilePlayerRepository(LocalAccountProfiles::accountFile);
            check(!Files.exists(file,LinkOption.NOFOLLOW_LINKS),
                "initial named profile unexpectedly exists");

            // Session 1 publishes a real legitimate account file,
            // rather than manually fabricating the prior snapshot.
            firstWorld=World.isolatedForTest(50L,normalRepository);
            final World first=firstWorld;
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession listener=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(first)){
                try(Socket client=listener.login(USER)){
                    await(()->first.players().byName(USER)!=null,
                        "first genuine login");
                    WorldPlayer player=first.players().byName(USER);
                    setPhase(first,player,OLD);
                }
                await(()->first.players().size()==0,
                    "first final logout");
                listener.waitCompleted();
            }
            check(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS),
                "first final account save not written");
            check(OLD.equals(diskValue(normalRepository)),
                "first successful save did not persist OLD phase");
            byte[] oldBytes=Files.readAllBytes(file);
            check(oldBytes.length>0,"old durable account is empty");
            first.close();
            check(first.closed(),"first World not closed");

            // The identical REAL file-backed World save code runs,
            // but its pre-publication package-scoped test hook throws
            // before an account replacement could happen.
            AtomicBoolean armFailure=new AtomicBoolean(false);
            AtomicInteger refusals=new AtomicInteger();
            AtomicReference<byte[]> preFinalBytes=new AtomicReference<>();
            FilePlayerRepository faultRepository=new FilePlayerRepository(
                LocalAccountProfiles::accountFile,account->{
                    if(armFailure.get()&&USER.equals(account)){
                        refusals.incrementAndGet();
                        throw new IOException(
                            "G21.177 INJECTED_FINAL_SAVE_IO_FAILURE_NO_AUTHORITY");
                    }
                });
            failingWorld=World.isolatedForTest(50L,faultRepository);
            final World failing=failingWorld;
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession listener=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(failing)){
                try(Socket client=listener.login(USER)){
                    await(()->failing.players().byName(USER)!=null,
                        "second login of durable OLD account");
                    WorldPlayer player=failing.players().byName(USER);
                    check(OLD.equals(phase(player)),
                        "second World did not actually hydrate old bytes");
                    // The bootstrap publisher also queues an account
                    // save. If fault injection starts immediately after
                    // socket success, BOTH BOOTSTRAP and SESSION_END may
                    // fail, incorrectly invalidating a final-only test.
                    await(()->completedWrites(failing)>=1L,
                        "bootstrap account write completed");
                    check(OLD.equals(diskValue(normalRepository)),
                        "bootstrap changed semantic durable preimage");
                    preFinalBytes.set(Files.readAllBytes(file));
                    check(preFinalBytes.get().length>0,
                        "no durable post-bootstrap preimage");
                    setPhase(failing,player,NEW);
                    // Arm only AFTER the genuine bootstrap write has
                    // fully completed and BEFORE socket disconnect.
                    armFailure.set(true);
                }
                await(()->failing.players().size()==0,
                    "failed-final-save unregister");
                listener.waitCompleted();
            }
            check(refusals.get()==1,
                "expected exactly one genuine final-save IO refusal");
            check(failing.persistence().failedCount()>=1L,
                "persistence worker failed counter not incremented");
            check(preFinalBytes.get()!=null&&
                Arrays.equals(preFinalBytes.get(),Files.readAllBytes(file)),
                "failed final-save rewrote post-bootstrap durable account");
            check(OLD.equals(diskValue(normalRepository)),
                "uncommitted NEW was reported durable after failure");
            check(!Files.exists(secondary,LinkOption.NOFOLLOW_LINKS),
                "failed final save created secondary profile");
            failing.close();
            check(failing.closed(),"failing World did not close");

            // The documented unresolved safety problem: because no
            // durable failed-final-save quarantine is published, an
            // independent new World can authenticate the older
            // committed file. This must never be mislabeled NEW.
            freshWorld=World.isolatedForTest(50L,normalRepository);
            final World fresh=freshWorld;
            AtomicBoolean staleReconnectSeen=new AtomicBoolean();
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession listener=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(fresh)){
                try(Socket client=listener.login(USER)){
                    await(()->fresh.players().byName(USER)!=null,
                        "post-failure reconnect");
                    WorldPlayer reloaded=fresh.players().byName(USER);
                    staleReconnectSeen.set(OLD.equals(phase(reloaded)));
                    check(staleReconnectSeen.get(),
                        "reconnect did not report actual prior durable state");
                    check(!NEW.equals(phase(reloaded)),
                        "failed-final-save falsely recovered NEW state");
                }
                await(()->fresh.players().size()==0,
                    "post-failure reconnect logout");
                listener.waitCompleted();
            }
            check(OLD.equals(diskValue(normalRepository)),
                "post-failure reconnect silently rewrote old snapshot");
            check(!Files.exists(secondary,LinkOption.NOFOLLOW_LINKS),
                "post-failure session used legacy secondary slot");
            fresh.close();
            check(fresh.closed(),"fresh World did not close");

            boolean noLeaks=
                MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1.activeLocalWatchCount()==0;
            check(noLeaks,"JVM publication lease/watch leaked");
            System.out.println(
                "G21177_FINAL_FAILURE_DIAGNOSTICS"+
                " realTcpSuccessfulLogins=3"+
                " worldsCreatedAndClosed=3"+
                " firstDurableSnapshot=true"+
                " bootstrapCompletedBeforeFault=true"+
                " prePublicationIoFailures="+refusals.get()+
                " persistenceFailedCounterObserved=true"+
                " previousAccountBytesUnchanged=true"+
                " failedSessionUnregistered=true"+
                " staleReconnectCurrentlyAllowed="+staleReconnectSeen.get()+
                " unsavedMutationNotRestored=true"+
                " operatorRecoveryPerformed=false"+
                " zeroJvmLeases="+noLeaks
            );
            System.out.println(
                "G21177_FINAL_FAILURE_PASS"+
                " realFileBackedWorldSaveFault=true"+
                " noSilentFalseSuccessfulSave=true"+
                " previouslyPersistedFilePreserved=true"+
                " durableFailureQuarantineImplemented=false"+
                " staleReconnectStillPossible=true"+
                " originalGraphicalClientProven=false"+
                " rawWriterExclusion=false"+
                " independentProvenance=false atomicCrashEpoch=false"+
                " cleanup=false rollover=false pruning=false"+
                " unlink=false grant=false replay=false"+
                " sessionRelease=false ack=false"
            );
        }finally{
            if(freshWorld!=null&&!freshWorld.closed())
                freshWorld.close();
            if(failingWorld!=null&&!failingWorld.closed())
                failingWorld.close();
            if(firstWorld!=null&&!firstWorld.closed())
                firstWorld.close();
            if(previous==null)
                System.clearProperty("spk.local.accountFile");
            else
                System.setProperty("spk.local.accountFile",previous);
            try(Stream<Path> paths=Files.walk(root)){
                for(Path p:paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        run();
    }
}
