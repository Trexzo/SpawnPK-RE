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
 * Chat 1 G21.179: a first-time session may publish default BOOTSTRAP
 * bytes before a later failed final account save. Another World can
 * then hydrate those earlier bytes. Characterization, NOT a fix.
 */
public final class G21179CoreSocketFirstSessionFailedFinalSaveIntegrationTest{
    private static final String USER="g21179-new";
    private static final String NS="core_first_session";
    private static final String KEY="phase";
    private static final String VALUE="FIRST_UNSAVED";

    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError("G21.179 "+message);
    }

    @FunctionalInterface private interface Condition{
        boolean ready()throws Exception;
    }

    private static void await(Condition condition,String description)
        throws Exception{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);
        while(System.nanoTime()<until){
            if(condition.ready())return;
            Thread.sleep(10);
        }
        check(condition.ready(),"timeout "+description);
    }

    private static String phase(WorldPlayer player){
        return player.snapshotExtensions().namespace(NS).get(KEY);
    }

    private static long completed(World world){
        String metrics=world.persistence().metrics();
        String key=",completed=";
        int at=metrics.indexOf(key);
        check(at>=0,"missing persistence completion metrics");
        int start=at+key.length(),end=metrics.indexOf(',',start);
        check(end>start,"invalid persistence completion metrics");
        return Long.parseLong(metrics.substring(start,end));
    }

    private static int deniedLogin(int port)throws Exception{
        try(Socket socket=new Socket(
                InetAddress.getByName("127.0.0.1"),port)){
            socket.setSoTimeout(5000);
            InputStream in=socket.getInputStream();
            OutputStream out=socket.getOutputStream();
            out.write(14);
            out.write(7);
            out.flush();
            byte[] pre=Binary.readExactly(in,9);
            check((pre[8]&255)==0,"prelogin refused");
            long seed=Binary.i64(Binary.readExactly(in,8),0);
            check(seed==LocalLoginTransport.SERVER_SEED,
                "seed unexpected");
            int[] seeds={
                0x01020304,0x11223344,
                (int)(seed>>>32),(int)seed
            };
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
        Path root=Files.createTempDirectory("g21179-first-session-");
        String previous=System.getProperty("spk.local.accountFile");
        System.setProperty("spk.local.accountFile",
            root.resolve("opensrc.properties").toString());
        World first=null,newWorld=null;
        try{
            Path file=LocalAccountProfiles.accountFile(USER);
            FilePlayerRepository normal=
                new FilePlayerRepository(LocalAccountProfiles::accountFile);
            check(!Files.exists(file,LinkOption.NOFOLLOW_LINKS),
                "first-session account must start absent");
            AtomicBoolean armed=new AtomicBoolean();
            AtomicInteger faults=new AtomicInteger();
            FilePlayerRepository fault=new FilePlayerRepository(
                LocalAccountProfiles::accountFile,account->{
                    if(armed.get()&&USER.equals(account)){
                        faults.incrementAndGet();
                        throw new IOException(
                            "G21.179 TEST_ONLY_FINAL_SAVE_FAILED");
                    }
                });
            first=World.isolatedForTest(50L,fault);
            final World initial=first;
            byte[][] baseline={null};
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession listener=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(initial)){
                try(Socket client=listener.login(USER)){
                    await(()->initial.players().byName(USER)!=null,
                        "first successful socket login");
                    WorldPlayer player=initial.players().byName(USER);
                    await(()->completed(initial)>=1L,
                        "real bootstrap account save");
                    check(Files.isRegularFile(
                            file,LinkOption.NOFOLLOW_LINKS),
                        "bootstrap did not publish the first account");
                    baseline[0]=Files.readAllBytes(file);
                    check(baseline[0].length>0,
                        "bootstrap default file empty");
                    check(normal.load(USER).isPresent()&&
                        !normal.load(USER).get().values().containsKey(
                            "extension."+NS+"."+KEY),
                        "bootstrap already contained final mutation");
                    initial.submit(player,player.generation(),()->{
                        player.snapshotExtensions().replaceNamespace(
                            NS,Collections.singletonMap(KEY,VALUE));
                    }).get(4,TimeUnit.SECONDS);
                    check(VALUE.equals(phase(player)),
                        "World-owned in-session mutation absent");
                    armed.set(true);
                }
                await(()->initial.players().size()==0,
                    "failed-final session unregistration");
                listener.waitCompleted();
            }
            check(faults.get()==1,"one final save fault required");
            check(initial.persistence().failedCount()>=1,
                "failed final write not counted");
            await(()->initial.persistence()
                    .failedFinalSaveAdmissionVetoCount()==1,
                "negative World-lifetime account admission");
            check(Arrays.equals(baseline[0],Files.readAllBytes(file)),
                "failed final save changed BOOTSTRAP account bytes");
            check(!normal.load(USER).get().values().containsKey(
                "extension."+NS+"."+KEY),
                "failed mutation was incorrectly committed");
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession refused=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(initial)){
                check(deniedLogin(refused.listener.getLocalPort())!=2,
                    "same World admitted failed-final account");
                refused.waitCompleted();
            }
            check(initial.players().size()==0,
                "refused login registered a player");
            first.close();
            check(first.closed(),"first World still active");

            newWorld=World.isolatedForTest(50L,normal);
            final World restarted=newWorld;
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession login=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(restarted)){
                try(Socket socket=login.login(USER)){
                    await(()->restarted.players().byName(USER)!=null,
                        "cross-World bootstrap reconnect");
                    WorldPlayer player=restarted.players().byName(USER);
                    check(phase(player)==null,
                        "NEW failed mutation hydrated after restart");
                }
                await(()->restarted.players().size()==0,
                    "new World logout");
                login.waitCompleted();
            }
            newWorld.close();
            check(newWorld.closed(),"new World still active");
            boolean leaks=
                MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1.activeLocalWatchCount()==0;
            check(leaks,"JVM leases or watches leaked");
            System.out.println(
                "G21179_FIRST_SESSION_DIAGNOSTICS"+
                " startingAccountAbsent=true"+
                " bootstrapFirstAccountSaved=true"+
                " bootstrapCompletedBeforeFinalFailure=true"+
                " injectedFinalSaveFailures="+faults.get()+
                " bootstrapBytesPreserved=true"+
                " sameWorldLoginDenied=true"+
                " newWorldBootstrapDefaultsAccepted=true"+
                " failedMutationNotRecovered=true"+
                " zeroJvmLeases="+leaks);
            System.out.println(
                "G21179_FIRST_SESSION_PASS"+
                " realSocketSessions=true"+
                " worldLifetimeVeto=true"+
                " durableCrossWorldFence=false"+
                " safeRecoveryImplemented=false"+
                " rawWriterExclusion=false atomicCrashEpoch=false"+
                " independentProvenance=false cleanup=false"+
                " rollover=false pruning=false unlink=false"+
                " grant=false replay=false sessionRelease=false ack=false");
        }finally{
            if(newWorld!=null&&!newWorld.closed())newWorld.close();
            if(first!=null&&!first.closed())first.close();
            if(previous==null)System.clearProperty("spk.local.accountFile");
            else System.setProperty("spk.local.accountFile",previous);
            try(Stream<Path> entries=Files.walk(root)){
                for(Path p:entries.sorted(Comparator.reverseOrder())
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
