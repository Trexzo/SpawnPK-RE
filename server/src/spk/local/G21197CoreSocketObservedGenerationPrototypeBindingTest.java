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
 * G21.197: TEST-ONLY bridge from genuine TCP World registration generation
 * to the UNUSED G21.196 owner-epoch correlation records.
 *
 * Test code, not LocalSession or World, manually reserves before login and
 * manually binds the observed generation afterward. Neither record grants
 * ownership nor retires G21.190 ACTIVE review. No production changes.
 */
public final class G21197CoreSocketObservedGenerationPrototypeBindingTest {
    private static final String USER="g21197-observed-generation";
    private static final String NAMESPACE="core_g21197";
    private static final String VALUE="WORLD_FINAL_FORCED";
    private static final String STORED=
        "extension.core_g21197.phase";

    private G21197CoreSocketObservedGenerationPrototypeBindingTest(){}

    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G21.197 "+why);
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
        Path root=Files.createTempDirectory("g21197-real-gen-");
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

                        // The prototype must not interfere with actual
                        // World-owned state/forced account publication.
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
                check(first.persistence().failedCount()==0,
                    "healthy World account save unexpectedly failed");
                check(VALUE.equals(onDisk(repository)),
                    "forced final snapshot lost World mutation");
                await(()->LocalJvmAccountSessionLease
                        .activeCountForTest()==0,
                    "healthy final save lease release");
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
                "G21197_REAL_TCP_GENERATION_PROTOTYPE_PASS"+
                " realTcpWorldGenerationObserved=true"+
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
