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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * G21.182: opt-in same-JVM negative retention after a genuinely failed
 * final account save. The token is intentionally NOT released by the
 * session, even when its World has been closed; this is a characterization
 * of the guarded mode only, not durable recovery or crash safety.
 */
public final class G21182CoreSocketFailedFinalLeaseRetentionTest {
    private static final String USER="g21182-failure";
    private static final String NS="core_jvm_failure";
    private static final String KEY="phase";
    private static final String VALUE="UNSAVED";

    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G21.182 "+reason);
    }

    @FunctionalInterface private interface Condition{
        boolean ready()throws Exception;
    }

    private static void await(Condition condition,String reason)
        throws Exception{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8L);
        while(System.nanoTime()<until){
            if(condition.ready())return;
            Thread.sleep(10);
        }
        check(condition.ready(),"timeout "+reason);
    }

    private static long completed(World world){
        String metrics=world.persistence().metrics();
        String key=",completed=";
        int pos=metrics.indexOf(key);
        check(pos>=0,"missing persistence completion metric");
        int start=pos+key.length(),end=metrics.indexOf(',',start);
        check(end>start,"invalid completed metric");
        return Long.parseLong(metrics.substring(start,end));
    }

    private static int loginResponse(int port)throws Exception{
        try(Socket socket=new Socket(InetAddress.getByName("127.0.0.1"),port)){
            socket.setSoTimeout(5000);
            InputStream in=socket.getInputStream();
            OutputStream out=socket.getOutputStream();
            out.write(14);
            out.write(7);
            out.flush();
            byte[] pre=Binary.readExactly(in,9);
            check((pre[8]&255)==0,"prelogin rejected");
            long seed=Binary.i64(Binary.readExactly(in,8),0);
            check(seed==LocalLoginTransport.SERVER_SEED,
                "unexpected server seed");
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
        Path root=Files.createTempDirectory("g21182-failed-final-");
        String oldFile=System.getProperty("spk.local.accountFile");
        String oldFlag=System.getProperty(
            LocalJvmAccountSessionLease.PROPERTY);
        System.setProperty("spk.local.accountFile",
            root.resolve("opensrc.properties").toString());
        System.setProperty(LocalJvmAccountSessionLease.PROPERTY,"true");
        World initial=null,another=null;
        try{
            int before=LocalJvmAccountSessionLease.activeCountForTest();
            Path account=LocalAccountProfiles.accountFile(USER);
            check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "fixture named account unexpectedly exists");
            AtomicBoolean armed=new AtomicBoolean();
            AtomicInteger faults=new AtomicInteger();
            FilePlayerRepository injected=new FilePlayerRepository(
                LocalAccountProfiles::accountFile,name->{
                    if(armed.get()&&USER.equals(name)){
                        faults.incrementAndGet();
                        throw new IOException(
                            "G21.182 TEST_ONLY_FAILED_FINAL_NO_AUTHORITY");
                    }
                });
            initial=World.isolatedForTest(50L,injected);
            final World a=initial;
            byte[][] bootstrap={null};
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession listener=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(a)){
                try(Socket client=listener.login(USER)){
                    await(()->a.players().byName(USER)!=null,
                        "first World admitted login");
                    await(()->completed(a)>=1L,
                        "initial real bootstrap persistence");
                    check(Files.isRegularFile(
                        account,LinkOption.NOFOLLOW_LINKS),
                        "bootstrap account was not published");
                    bootstrap[0]=Files.readAllBytes(account);
                    check(bootstrap[0].length>0,
                        "bootstrap bytes missing");
                    WorldPlayer player=a.players().byName(USER);
                    a.submit(player,player.generation(),()->{
                        player.snapshotExtensions().replaceNamespace(
                            NS,Collections.singletonMap(KEY,VALUE));
                    }).get(4,TimeUnit.SECONDS);
                    check(VALUE.equals(player.snapshotExtensions()
                            .namespace(NS).get(KEY)),
                        "World-owned mutation missing");
                    check(LocalJvmAccountSessionLease
                            .activeCountForTest()==before+1,
                        "active account lease not acquired");
                    armed.set(true);
                }
                await(()->a.players().size()==0,
                    "failed-final session unregister");
                listener.waitCompleted();
            }
            check(faults.get()==1,"expected exactly one failed final write");
            check(a.persistence().failedCount()>=1L,
                "failed persistence task not counted");
            await(()->a.persistence()
                    .failedFinalSaveAdmissionVetoCount()==1,
                "same-World negative not recorded");
            check(Arrays.equals(bootstrap[0],Files.readAllBytes(account)),
                "failed save changed durable BOOTSTRAP preimage");
            check(LocalJvmAccountSessionLease
                    .activeCountForTest()==before+1,
                "uncertain final write released JVM token");
            initial.close();
            check(initial.closed(),"failed-first World still running");

            FilePlayerRepository normal=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            another=World.isolatedForTest(50L,normal);
            final World b=another;
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession refused=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(b)){
                check(loginResponse(refused.listener.getLocalPort())!=2,
                    "fresh World admitted account after failed final save");
                refused.waitCompleted();
            }
            check(b.players().size()==0,
                "rejected World registered forbidden owner");
            check(LocalJvmAccountSessionLease
                    .activeCountForTest()==before+1,
                "failed-final negative was removed by refused login");
            check(Arrays.equals(bootstrap[0],Files.readAllBytes(account)),
                "refused re-login changed durable account bytes");
            another.close();
            check(another.closed(),"refusing World still running");
            boolean noPublicationLeases=
                MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1.activeLocalWatchCount()==0;
            check(noPublicationLeases,"publication lease/watch leaked");
            System.out.println(
                "G21182_FAILED_FINAL_LEASE_DIAGNOSTICS"+
                " bootstrapCommitted=true failedFinalWrites="+faults.get()+
                " originalAccountUnchanged=true"+
                " finalWorldClosed=true crossWorldSocketDenied=true"+
                " sessionLeaseRetained=true"+
                " negativeSameJvmOnly=true"+
                " publicationLeaseLeaks=0");
            System.out.println(
                "G21182_FAILED_FINAL_LEASE_PASS"+
                " experimentalOptIn=true"+
                " uncertainFinalSaveNotReleased=true"+
                " newWorldSameJvmAdmissionRefused=true"+
                " safeAutomaticRecovery=false"+
                " crashDurableNegative=false crossProcessLock=false"+
                " rawWriterExclusion=false atomicCrashEpoch=false"+
                " originalGraphicalClientProven=false"+
                " cleanup=false rollover=false pruning=false unlink=false"+
                " grant=false replay=false sessionRelease=false ack=false");
        }finally{
            if(another!=null&&!another.closed())another.close();
            if(initial!=null&&!initial.closed())initial.close();
            if(oldFile==null)System.clearProperty("spk.local.accountFile");
            else System.setProperty("spk.local.accountFile",oldFile);
            if(oldFlag==null)System.clearProperty(
                LocalJvmAccountSessionLease.PROPERTY);
            else System.setProperty(LocalJvmAccountSessionLease.PROPERTY,
                oldFlag);
            try(Stream<Path> paths=Files.walk(root)){
                for(Path p:paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        run();
    }
}
