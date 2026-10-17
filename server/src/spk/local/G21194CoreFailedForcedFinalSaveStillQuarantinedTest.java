package spk.local;

import java.io.IOException;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * G21.194 test-only negative: failure of an opted-in forced World
 * final save must not retire the G21.190 one-shot ACTIVE intent.
 * A new World must deny real TCP login to the earlier BOOTSTRAP bytes.
 *
 * No positive account recovery, marker retirement, release or grants.
 */
public final class G21194CoreFailedForcedFinalSaveStillQuarantinedTest {
    private static final String USER="g21194-failed";
    private static final String NAMESPACE="core_g21194";
    private static final String VALUE="UNSAVED_MUTATION";
    private static final String STORED="extension.core_g21194.phase";

    private G21194CoreFailedForcedFinalSaveStillQuarantinedTest(){}

    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G21.194 "+why);
    }

    @FunctionalInterface private interface Condition {
        boolean ready()throws Exception;
    }

    private static void await(Condition condition,String why)
        throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
        while(System.nanoTime()<end){
            if(condition.ready())return;
            Thread.sleep(10L);
        }
        check(condition.ready(),"timeout "+why);
    }

    private static long completed(World world){
        String text=world.persistence().metrics();
        String key=",completed=";
        int at=text.indexOf(key);
        check(at>=0,"missing save completion counter");
        int start=at+key.length();
        int end=text.indexOf(',',start);
        check(end>start,"invalid save completion counter");
        return Long.parseLong(text.substring(start,end));
    }

    private static String diskPhase(FilePlayerRepository repo)
        throws Exception{
        Optional<PlayerSnapshot> snapshot=repo.load(USER);
        check(snapshot.isPresent(),"bootstrap file missing");
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
        Path root=Files.createTempDirectory("g21194-failure-");
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
                "first-time account unexpectedly exists");
            check(!Files.exists(marker,LinkOption.NOFOLLOW_LINKS),
                "ACTIVE intent unexpectedly exists");

            AtomicBoolean armed=new AtomicBoolean();
            AtomicInteger injected=new AtomicInteger();
            FilePlayerRepository fault=new FilePlayerRepository(
                LocalAccountProfiles::accountFile,username->{
                    if(armed.get()&&USER.equals(username)){
                        injected.incrementAndGet();
                        throw new IOException(
                            "G21.194 TEST_ONLY_FINAL_SAVE_IO_FAILURE");
                    }
                });
            FilePlayerRepository forensic=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            World first=World.isolatedForTest(50L,fault);
            byte[] bootstrap;
            byte[] active;
            try{
                try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession listener=
                        new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                            .OneSession(first)){
                    try(Socket client=listener.login(USER)){
                        await(()->first.players().byName(USER)!=null,
                            "first genuine TCP membership");
                        await(()->completed(first)>=1L,
                            "first World BOOTSTRAP save");
                        await(()->fault.forcedWorldPostimageCountForTest()>0,
                            "forced World BOOTSTRAP postimage");
                        check(Files.isRegularFile(account,
                            LinkOption.NOFOLLOW_LINKS),
                            "saved BOOTSTRAP account absent");
                        LocalDurableAccountReviewIntentPrimitive
                            .requireReviewMarker(account);
                        bootstrap=Files.readAllBytes(account);
                        active=Files.readAllBytes(marker);
                        check(bootstrap.length>0&&active.length>0,
                            "empty durable fixture");
                        check(diskPhase(forensic)==null,
                            "BOOTSTRAP unexpectedly includes mutation");

                        WorldPlayer owner=first.players().byName(USER);
                        first.submit(owner,owner.generation(),()->{
                            owner.snapshotExtensions().replaceNamespace(
                                NAMESPACE,
                                Collections.singletonMap("phase",VALUE));
                        }).get(5L,TimeUnit.SECONDS);
                        check(VALUE.equals(owner.snapshotExtensions()
                            .namespace(NAMESPACE).get("phase")),
                            "World mutation not applied");
                        armed.set(true);
                    }
                    await(()->first.players().size()==0,
                        "failed final save logout");
                    listener.waitCompleted();
                }
                check(injected.get()>=1,
                    "test-only final forced-save fault did not fire");
                check(first.persistence().failedCount()>=1,
                    "failed World final save not recorded");
                check(Arrays.equals(bootstrap,Files.readAllBytes(account)),
                    "failed final save rewrote BOOTSTRAP");
                check(Arrays.equals(active,Files.readAllBytes(marker)),
                    "failed final save touched ACTIVE marker");
                check(diskPhase(forensic)==null,
                    "failed final save published unsaved mutation");
            }finally{
                first.close();
            }

            FilePlayerRepository subsequent=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            check(diskPhase(subsequent)==null,
                "fresh repository should still see old BOOTSTRAP");
            // Failed final saves intentionally retain the original
            // JVM lease (G21.181/G21.182 fail-closed contract).
            // The fresh World must NOT acquire an additional owner.
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "failed final save did not retain its negative lease");
            World next=World.isolatedForTest(50L,subsequent);
            try{
                try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession listener=
                        new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                            .OneSession(next)){
                    check(attemptedLogin(
                        listener.listener.getLocalPort())!=2,
                        "fresh World admitted failed-final account");
                    listener.waitCompleted();
                }
                check(next.players().size()==0,
                    "refused login registered World owner");
                check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                    "refused login changed the retained negative lease");
            }finally{
                next.close();
            }

            check(Arrays.equals(bootstrap,Files.readAllBytes(account)),
                "denied fresh World changed account");
            check(Arrays.equals(active,Files.readAllBytes(marker)),
                "denied fresh World modified ACTIVE marker");

            System.out.println(
                "G21194_FORCED_FAILURE_REVIEW_QUARANTINE_PASS"+
                " genuineTcpBootstrap=true"+
                " bootstrapForcedPostimage=true"+
                " finalSaveIoFailureInjected=true"+
                " failedFinalSnapshotUnchanged=true"+
                " activeIntentPreserved=true"+
                " freshWorldTcpDenied=true"+
                " defaultOff=true"+
                " markerRetirement=false"+
                " durableEpochReceipt=false"+
                " crashRecovery=false"+
                " processRestartTested=false"+
                " powerLossTested=false");
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
