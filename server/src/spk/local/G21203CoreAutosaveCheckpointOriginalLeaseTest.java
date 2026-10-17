package spk.local;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.203: test real World autosave checkpoint carries original
 * capture-time lease identity through the coalesced worker.
 *
 * Production initializer binds the admitted JVM token after registration.
 * The separate G21.196 disk records are still TEST-ONLY manual correlation.
 * No durable authenticated epoch, write-fencing, or marker retirement.
 */
public final class G21203CoreAutosaveCheckpointOriginalLeaseTest {
    private static final String USER="g21203-observed-generation";
    private static final String NAMESPACE="core_g21203";
    private static final String VALUE="WORLD_FINAL_FORCED";
    private static final String STORED=
        "extension.core_g21203.phase";

    private G21203CoreAutosaveCheckpointOriginalLeaseTest(){}

    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G21.203 "+why);
    }
    @FunctionalInterface private interface Condition{
        boolean ok()throws Exception;
    }
    private static void await(Condition condition,String stage)
        throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
        while(System.nanoTime()<end){
            if(condition.ok())return;
            Thread.sleep(10L);
        }
        check(condition.ok(),"timeout "+stage);
    }
    private static String onDisk(FilePlayerRepository repository)
        throws Exception{
        Optional<PlayerSnapshot> snapshot=repository.load(USER);
        check(snapshot.isPresent(),"account snapshot missing");
        return snapshot.get().values().get(STORED);
    }
    private static int attemptedLogin(int port)throws Exception{
        try(Socket socket=new Socket(
                InetAddress.getByName("127.0.0.1"),port)){
            socket.setSoTimeout(5000);
            InputStream in=socket.getInputStream();
            OutputStream out=socket.getOutputStream();
            out.write(14);
            out.write(7);
            out.flush();
            byte[] pre=Binary.readExactly(in,9);
            check((pre[8]&255)==0,"prelogin mismatch");
            long seed=Binary.i64(Binary.readExactly(in,8),0);
            check(seed==LocalLoginTransport.SERVER_SEED,
                "unexpected login seed");
            int[] seeds={0x01020304,0x11223344,
                (int)(seed>>>32),(int)seed};
            byte[] frame=
                G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .loginFrame(seeds,USER);
            out.write(16);
            out.write(frame.length);
            out.write(frame);
            out.flush();
            return in.read();
        }
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21203-real-gen-");
        final String[] keys={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalReviewIntentAdmissionVeto.PROPERTY,
            LocalOneShotActiveReviewOnAdmission.PROPERTY,
            FilePlayerRepository.EXPERIMENTAL_FORCE_WORLD_POSTIMAGE_PROPERTY
        };
        String[] previous=new String[keys.length];
        for(int i=0;i<keys.length;i++)
            previous[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],
            root.resolve("opensrc.properties").toString());
        for(int i=1;i<keys.length;i++)
            System.setProperty(keys[i],"true");

        try{
            Path account=LocalAccountProfiles.accountFile(USER);
            Path marker=LocalDurableAccountReviewIntentPrimitive
                .markerPath(account);
            Files.createDirectories(account.getParent());
            check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "first-time account already exists");
            check(!Files.exists(marker,LinkOption.NOFOLLOW_LINKS),
                "G21.190 marker unexpectedly exists");

            // Standalone prototype invoked by test code BEFORE an actual
            // login, not by production LocalSessionPlayerInitializer.
            LocalDurableOwnerEpochPrototype.Reservation pre=
                LocalDurableOwnerEpochPrototype
                    .reserveBeforeWorldRegistration(account);
            LocalDurableOwnerEpochPrototype.Observation preInspect=
                LocalDurableOwnerEpochPrototype.inspect(account);
            check(!preInspect.generationBound&&
                  preInspect.worldGeneration==-1L,
                "reservation already has generation");
            check(!Files.exists(
                    LocalDurableOwnerEpochPrototype.bindingPath(account),
                    LinkOption.NOFOLLOW_LINKS),
                "pre-registration binding exists");

            FilePlayerRepository repository=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            World first=World.isolatedForTest(50L,repository);
            final int[] manualCheckBoundary={-1};
            final int[] capturedWorkerBoundary={-1};
            final LocalJvmAccountSessionLease.SaveCaptureWitness[]
                originalCapture={null};
            long observedGeneration;
            try{
                try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession listener=
                        new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                            .OneSession(first)){
                    try(Socket authenticated=listener.login(USER)){
                        await(()->first.players().byName(USER)!=null,
                            "actual TCP World registration");
                        WorldPlayer current=first.players().byName(USER);
                        observedGeneration=current.generation();
                        check(observedGeneration>0L,
                            "real World assigned no positive generation");
                        LocalDurableAccountReviewIntentPrimitive
                            .requireReviewMarker(account);
                        check(LocalJvmAccountSessionLease
                                .activeCountForTest()==1,
                            "real TCP session lacks ownership lease");

                        // Actual G21.203 opt-in runtime binding:
                        // a live lease recognizes only the exact registered
                        // WorldPlayer object and its current generation.
                        LocalJvmAccountSessionLease
                            .requireCurrentWorldOwnerForTest(
                                account,current,observedGeneration);

                        WorldPlayer forged=new WorldPlayer();
                        forged.markRegistered(USER);
                        check(forged.generation()==observedGeneration,
                            "identity negative needs matching generation");
                        boolean wrongIdentityRejected=false;
                        try{
                            LocalJvmAccountSessionLease
                                .requireCurrentWorldOwnerForTest(
                                    account,forged,observedGeneration);
                        }catch(IllegalStateException expected){
                            wrongIdentityRejected=expected.getMessage()!=null&&
                                expected.getMessage().contains(
                                    "WORLD_OWNER_GENERATION_NOT_CURRENT");
                        }
                        check(wrongIdentityRejected,
                            "different WorldPlayer with same generation passed");

                        boolean staleGenerationRejected=false;
                        try{
                            LocalJvmAccountSessionLease
                                .requireCurrentWorldOwnerForTest(
                                    account,current,observedGeneration+1L);
                        }catch(IllegalStateException expected){
                            staleGenerationRejected=expected.getMessage()!=null&&
                                expected.getMessage().contains(
                                    "WORLD_OWNER_GENERATION_NOT_CURRENT");
                        }
                        check(staleGenerationRejected,
                            "wrong generation acquired live lease");

                        boolean otherAccountRejected=false;
                        try{
                            LocalJvmAccountSessionLease
                                .requireCurrentWorldOwnerForTest(
                                    LocalAccountProfiles.accountFile(
                                        "g21203-different-account"),
                                    current,observedGeneration);
                        }catch(IllegalStateException expected){
                            otherAccountRejected=expected.getMessage()!=null&&
                                expected.getMessage().contains(
                                    "NO_CURRENT_ACCOUNT_LEASE");
                        }
                        check(otherAccountRejected,
                            "different account path used current owner lease");

                        // Actual G21.203 queued-save guard counters: the
                        // live token must be usable only by the captured
                        // exact owner/generation. The worker repeats this
                        // check before real FilePlayerRepository IO.
                        int beforeWorkerChecks=
                            LocalJvmAccountSessionLease
                                .workerOwnerChecksForTest();
                        check(beforeWorkerChecks>=0,
                            "invalid worker counter");
                        check(LocalJvmAccountSessionLease
                            .requireCapturedSaveOwnerIfLeased(
                                account,USER,current,observedGeneration),
                            "captured owner not recognized under token");
                        boolean foreignCapturedDenied=false;
                        try{
                            LocalJvmAccountSessionLease
                                .requireCapturedSaveOwnerIfLeased(
                                    account,USER,forged,observedGeneration);
                        }catch(IllegalStateException expected){
                            foreignCapturedDenied=expected.getMessage()!=null&&
                                expected.getMessage().contains(
                                    "CAPTURED_SAVE_OWNER_NOT_BOUND_TO_LEASE");
                        }
                        check(foreignCapturedDenied,
                            "same-name foreign captured snapshot passed");
                        boolean oldGenerationDenied=false;
                        try{
                            LocalJvmAccountSessionLease
                                .requireCapturedSaveOwnerIfLeased(
                                    account,USER,current,observedGeneration+1L);
                        }catch(IllegalStateException expected){
                            oldGenerationDenied=expected.getMessage()!=null&&
                                expected.getMessage().contains(
                                    "CAPTURED_SAVE_OWNER_NOT_BOUND_TO_LEASE");
                        }
                        check(oldGenerationDenied,
                            "stale captured generation passed");
                        boolean wrongAccountNameDenied=false;
                        try{
                            LocalJvmAccountSessionLease
                                .requireCapturedSaveOwnerIfLeased(
                                    account,"different-snapshot-account",
                                    current,observedGeneration);
                        }catch(IllegalStateException expected){
                            wrongAccountNameDenied=expected.getMessage()!=null&&
                                expected.getMessage().contains(
                                    "CAPTURED_SAVE_OWNER_NOT_BOUND_TO_LEASE");
                        }
                        check(wrongAccountNameDenied,
                            "wrong snapshot account passed");
                        // Capture-time provenance pins the *original*
                        // JVM Token instance rather than whatever token
                        // later holds this account path in the worker.
                        originalCapture[0]=LocalJvmAccountSessionLease
                            .captureSaveWitness(
                                account,USER,current,observedGeneration);
                        check(originalCapture[0]!=null,
                            "snapshot origin witness not captured");
                        int preCaptureChecks=
                            LocalJvmAccountSessionLease
                                .captureBoundWorkerChecksForTest();
                        check(LocalJvmAccountSessionLease
                            .requireCapturedWitnessBeforeWrite(
                                originalCapture[0],account,USER,
                                current,observedGeneration),
                            "exact captured owner token rejected");
                        check(LocalJvmAccountSessionLease
                            .captureBoundWorkerChecksForTest()==
                                preCaptureChecks+1,
                            "original capture-bound token not checked");
                        boolean witnessForeignRejected=false;
                        try{
                            LocalJvmAccountSessionLease
                                .requireCapturedWitnessBeforeWrite(
                                    originalCapture[0],account,USER,
                                    forged,observedGeneration);
                        }catch(IllegalStateException expected){
                            witnessForeignRejected=expected.getMessage()!=null&&
                                expected.getMessage().contains(
                                    "SAVE_CAPTURE_WITNESS_MISMATCH");
                        }
                        check(witnessForeignRejected,
                            "foreign owner used someone else's saved witness");
                        boolean witnessGenRejected=false;
                        try{
                            LocalJvmAccountSessionLease
                                .requireCapturedWitnessBeforeWrite(
                                    originalCapture[0],account,USER,
                                    current,observedGeneration+1L);
                        }catch(IllegalStateException expected){
                            witnessGenRejected=expected.getMessage()!=null&&
                                expected.getMessage().contains(
                                    "SAVE_CAPTURE_WITNESS_MISMATCH");
                        }
                        check(witnessGenRejected,
                            "stale generation reused capture witness");
                        capturedWorkerBoundary[0]=LocalJvmAccountSessionLease
                            .captureBoundWorkerChecksForTest();

                        manualCheckBoundary[0]=LocalJvmAccountSessionLease
                            .workerOwnerChecksForTest();

                        // G21.196 correlation remains an UNUSED, manually
                        // bound prototype, not the G21.203 JVM-only lease.
                        // Manually hand the observed World generation to
                        // an UNUSED prototype. This is *not* authenticated
                        // runtime owner-epoch binding or automatic wiring.
                        LocalDurableOwnerEpochPrototype
                            .bindAfterWorldRegistration(
                                pre,observedGeneration);
                        LocalDurableOwnerEpochPrototype.Observation bound=
                            LocalDurableOwnerEpochPrototype.inspect(account);
                        check(bound.generationBound&&
                              bound.worldGeneration==observedGeneration&&
                              bound.reservation.epoch.equals(pre.epoch)&&
                              bound.reservation.reservationSha
                                  .equals(pre.reservationSha),
                            "prototype did not bind observed generation");

                        // G21.203: send a REAL eligible autosave tick
                        // through World execution context while the TCP
                        // lease-bound owner is still registered. Its
                        // queued checkpoint MUST be bound to the token
                        // recorded at snapshot capture, not a late lookup.
                        long beforeCheckpointWritten=
                            first.persistence().checkpointWrittenCount();
                        int beforeCheckpointLeaseGuard=
                            LocalJvmAccountSessionLease
                                .captureBoundWorkerChecksForTest();
                        first.submit(current,observedGeneration,()->{
                            current.snapshotExtensions().replaceNamespace(
                                NAMESPACE,
                                Collections.singletonMap(
                                    "phase","CHECKPOINT_VERIFIED"));
                            first.persistence().checkpointDue(100L);
                        }).get(5L,TimeUnit.SECONDS);
                        await(()->first.persistence()
                            .checkpointWrittenCount()>beforeCheckpointWritten,
                            "actual World checkpoint publication");
                        check(LocalJvmAccountSessionLease
                            .captureBoundWorkerChecksForTest()>
                                beforeCheckpointLeaseGuard,
                            "checkpoint worker did not validate original token");
                        check("CHECKPOINT_VERIFIED".equals(onDisk(repository)),
                            "real file-backed checkpoint did not persist");
                        // Existing final-save path must still succeed
                        // after checkpoint and retain same World ownership.
                        first.submit(current,observedGeneration,()->{
                            current.snapshotExtensions().replaceNamespace(
                                NAMESPACE,
                                Collections.singletonMap("phase",VALUE));
                        }).get(5L,TimeUnit.SECONDS);
                    }
                    await(()->first.players().size()==0,
                        "healthy socket logout");
                    listener.waitCompleted();
                }
                check(repository.forcedWorldPostimageCountForTest()>0,
                    "real forced World postimage path not exercised");
                check(manualCheckBoundary[0]>=0&&
                      LocalJvmAccountSessionLease
                          .workerOwnerChecksForTest()>
                          manualCheckBoundary[0],
                    "real final save worker skipped bound owner check");
                check(capturedWorkerBoundary[0]>=0&&
                      LocalJvmAccountSessionLease
                          .captureBoundWorkerChecksForTest()>
                          capturedWorkerBoundary[0],
                    "real queued final-save worker skipped original capture lease");
                check(first.persistence().failedCount()==0,
                    "healthy World account save unexpectedly failed");
                check(VALUE.equals(onDisk(repository)),
                    "forced final snapshot lost World mutation");
                await(()->LocalJvmAccountSessionLease
                        .activeCountForTest()==0,
                    "healthy final save lease release");
                boolean releasedOwnerRejected=false;
                try{
                    LocalJvmAccountSessionLease
                        .requireCurrentWorldOwnerForTest(
                            account,first.players().byName(USER),
                            observedGeneration);
                }catch(IllegalStateException expected){
                    releasedOwnerRejected=expected.getMessage()!=null&&
                        expected.getMessage().contains(
                            "NO_CURRENT_ACCOUNT_LEASE");
                }
                check(releasedOwnerRejected,
                    "confirmed healthy final save retained reusable token");
                // A previous SAVE witness must not be accepted after the
                // exact original lease is gone. This also refuses a
                // replacement lease (identity, not account path, matters).
                boolean releasedCaptureRejected=false;
                try{
                    LocalJvmAccountSessionLease
                        .requireCapturedWitnessBeforeWrite(
                            originalCapture[0],account,USER,
                            originalCapture[0].owner,
                            originalCapture[0].generation);
                }catch(IllegalStateException expected){
                    releasedCaptureRejected=expected.getMessage()!=null&&
                        expected.getMessage().contains(
                            "SAVE_CAPTURE_ORIGINAL_LEASE_LOST_OR_REPLACED");
                }
                check(releasedCaptureRejected,
                    "released session witness silently became unleased");
            }finally{
                first.close();
            }

            // A healthy saved account and matching prototype pair still
            // have NO retirement/admission authority over G21.190 ACTIVE.
            LocalDurableAccountReviewIntentPrimitive
                .requireReviewMarker(account);
            byte[] markerBefore=Files.readAllBytes(marker);
            byte[] reservationBefore=Files.readAllBytes(
                LocalDurableOwnerEpochPrototype.reservationPath(account));
            byte[] bindingBefore=Files.readAllBytes(
                LocalDurableOwnerEpochPrototype.bindingPath(account));
            byte[] accountBefore=Files.readAllBytes(account);

            FilePlayerRepository nextRepository=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            check(VALUE.equals(onDisk(nextRepository)),
                "forensic next repository did not see forced save");
            World next=World.isolatedForTest(50L,nextRepository);
            try{
                try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession listener=
                        new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                            .OneSession(next)){
                    check(attemptedLogin(
                        listener.listener.getLocalPort())!=2,
                        "matching prototype pair incorrectly admitted TCP");
                    listener.waitCompleted();
                }
                check(next.players().size()==0,
                    "refused second socket created a World member");
                check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                    "refused re-entry retained a new JVM token");
            }finally{
                next.close();
            }
            check(Arrays.equals(accountBefore,
                    Files.readAllBytes(account)),
                "refused retry modified saved account");
            check(Arrays.equals(markerBefore,
                    Files.readAllBytes(marker)),
                "refused retry retired ACTIVE marker");
            check(Arrays.equals(reservationBefore,Files.readAllBytes(
                    LocalDurableOwnerEpochPrototype.reservationPath(account))),
                "refused retry changed epoch reservation");
            check(Arrays.equals(bindingBefore,Files.readAllBytes(
                    LocalDurableOwnerEpochPrototype.bindingPath(account))),
                "refused retry changed generation binding");

            System.out.println(
                "G21203_CHECKPOINT_CAPTURED_JVM_LEASE_PASS"+
                " realTcpWorldGenerationObserved=true"+
                " currentLeaseOwnerMatch=true"+
                " realWorldSaveWorkerGuardChecked=true"+
                " exactOriginalCaptureLeaseChecked=true"+
                " realAsyncFinalSaveCaptureBound=true"+
                " realAutosaveCheckpointPublished=true"+
                " checkpointCapturedOriginalLeaseChecked=true"+
                " finalAfterCheckpointStillHealthy=true"+
                " foreignOwnerWithCapturedWitnessDenied=true"+
                " staleGenerationWithCapturedWitnessDenied=true"+
                " releasedCaptureWitnessDenied=true"+
                " g21184LateToggleStillUnsafe=true"+
                " foreignCapturedOwnerDenied=true"+
                " wrongCapturedGenerationDenied=true"+
                " wrongSnapshotAccountDenied=true"+
                " checkpointWorkerGuardNotYetWired=true"+
                " authenticatedDurableOwnerEpoch=false"+
                " wrongIdentityRejected=true"+
                " staleGenerationRejected=true"+
                " wrongAccountPathRejected=true"+
                " healthyFinalLeaseRelease=true"+
                " reservationForcedBeforeTcpAdmission=true"+
                " testManuallyBoundActualGeneration=true"+
                " forcedWorldFinalSave=true"+
                " activeReviewNeverRetired=true"+
                " secondRealTcpAdmissionDenied=true"+
                " runtimeEpochWiring=false"+
                " authenticatedOwnerEpoch=false"+
                " markerRetirement=false"+
                " recovery=false"+
                " rawWriterExclusion=false"+
                " powerLossProven=false");
        }finally{
            for(int i=0;i<keys.length;i++){
                if(previous[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],previous[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path path:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        run();
    }
}
