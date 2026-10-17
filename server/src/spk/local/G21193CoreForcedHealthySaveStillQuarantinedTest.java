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
 * G21.193 COMBINED OPT-IN NEGATIVE: a healthy, fully forced real
 * World-owned save is NOT a durable account-epoch retirement receipt.
 * The one-shot ACTIVE intent must remain, and a second real TCP login
 * is denied even though disk contains the latest saved state.
 *
 * Only test-private accounts. Neither flag is made default-on and
 * this regression authorizes NO marker removal/recovery/replay.
 */
public final class G21193CoreForcedHealthySaveStillQuarantinedTest {
    private static final String USER="g21193-quarantine";
    private static final String VALUE="HEALTHY_FORCED";
    private static final String PROPERTY="extension.core_g21193.phase";
    private G21193CoreForcedHealthySaveStillQuarantinedTest(){}

    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G21.193 "+why);
    }
    @FunctionalInterface private interface Condition{
        boolean ready()throws Exception;
    }
    private static void await(Condition condition)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
        while(System.nanoTime()<end){
            if(condition.ready())return;
            Thread.sleep(10L);
        }
        check(condition.ready(),"condition timeout");
    }

    private static String diskPhase(FilePlayerRepository repository)
        throws Exception{
        Optional<PlayerSnapshot> snapshot=repository.load(USER);
        check(snapshot.isPresent(),"named account missing on disk");
        return snapshot.get().values().get(PROPERTY);
    }

    private static int attemptTcpLogin(int port)throws Exception{
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
                "server seed mismatch");
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
        Path root=Files.createTempDirectory("g21193-healthy-");
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
            check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "account fixture not initially clean");
            check(!Files.exists(marker,LinkOption.NOFOLLOW_LINKS),
                "review intent fixture not initially clean");

            FilePlayerRepository firstRepository=
                new FilePlayerRepository(LocalAccountProfiles::accountFile);
            World first=World.isolatedForTest(50L,firstRepository);
            try{
                try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession server=
                        new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                            .OneSession(first)){
                    try(Socket connection=server.login(USER)){
                        await(()->first.players().byName(USER)!=null);
                        WorldPlayer owner=first.players().byName(USER);
                        first.submit(owner,owner.generation(),()->{
                            owner.snapshotExtensions().replaceNamespace(
                                "core_g21193",
                                Collections.singletonMap("phase",VALUE));
                        }).get(5L,TimeUnit.SECONDS);
                    }
                    await(()->first.players().size()==0);
                    server.waitCompleted();
                }
                check(first.persistence().failedCount()==0,
                    "healthy World save failed");
                check(firstRepository.forcedWorldPostimageCountForTest()>0,
                    "real World forced-file publication not exercised");
                check(VALUE.equals(diskPhase(firstRepository)),
                    "healthy final postimage not saved");
            }finally{
                first.close();
            }

            check(Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS),
                "one-shot ACTIVE intent unexpectedly retired");
            LocalDurableAccountReviewIntentPrimitive
                .requireReviewMarker(account);
            byte[] beforeMarker=Files.readAllBytes(marker);
            byte[] beforeAccount=Files.readAllBytes(account);
            check(beforeMarker.length>0&&beforeAccount.length>0,
                "empty marker or saved account");

            FilePlayerRepository secondRepository=
                new FilePlayerRepository(LocalAccountProfiles::accountFile);
            check(VALUE.equals(diskPhase(secondRepository)),
                "fresh forensic repository lost healthy postimage");
            World second=World.isolatedForTest(50L,secondRepository);
            try{
                try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession server=
                        new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                            .OneSession(second)){
                    check(attemptTcpLogin(
                        server.listener.getLocalPort())!=2,
                        "new World accepted ACTIVE-marked account");
                    server.waitCompleted();
                }
                check(second.players().size()==0,
                    "denied World somehow registered owner");
                check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                    "denied TCP login leaked account lease");
            }finally{
                second.close();
            }

            check(Arrays.equals(beforeAccount,Files.readAllBytes(account)),
                "denied retry rewrote healthy saved account");
            check(Arrays.equals(beforeMarker,Files.readAllBytes(marker)),
                "denied retry altered ACTIVE intent");
            System.out.println(
                "G21193_FORCED_SAVE_REVIEW_QUARANTINE_PASS"+
                " realTcpFirstAdmission=true"+
                " healthyWorldFinalSave=true"+
                " forcedFileAndDirectory=true"+
                " exactPostimageReadback=true"+
                " activeIntentUnretired=true"+
                " secondRealTcpAdmissionDenied=true"+
                " defaultOff=true"+
                " automaticRecovery=false"+
                " usableRepeatedLogin=false"+
                " powerLossProven=false"+
                " accountEpochReceipt=false"+
                " uncooperativeWriterExcluded=false");
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
        check(args.length==0,"unexpected args");
        run();
    }
}
