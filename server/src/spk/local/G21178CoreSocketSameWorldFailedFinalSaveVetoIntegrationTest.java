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
 * Chat 1 / G21.178: World-lifetime negative session admission after
 * a real final account write failure. Unlike an actual durable
 * intent, this fence does NOT survive a World/process restart.
 * The last step proves that cross-World gap remains.
 */
public final class G21178CoreSocketSameWorldFailedFinalSaveVetoIntegrationTest {
    private static final String USER="g21178-account";
    private static final String NAMESPACE="core_failure_veto";
    private static final String KEY="phase";
    private static final String SNAPSHOT_KEY="extension.core_failure_veto.phase";
    private static final String OLD="OLD_SAVED";
    private static final String NEW="NEW_NOT_SAVED";

    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G21.178 "+why);
    }

    @FunctionalInterface private interface Condition{
        boolean get()throws Exception;
    }

    private static void await(Condition test,String why)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(6L);
        while(System.nanoTime()<deadline){
            if(test.get())return;
            Thread.sleep(10L);
        }
        check(test.get(),"timeout "+why);
    }

    private static String phase(WorldPlayer player){
        return player.snapshotExtensions().namespace(NAMESPACE).get(KEY);
    }

    private static void change(World world,WorldPlayer player,String phase)
        throws Exception{
        world.submit(player,player.generation(),()->{
            player.snapshotExtensions().replaceNamespace(
                NAMESPACE,Collections.singletonMap(KEY,phase));
        }).get(4L,TimeUnit.SECONDS);
        check(phase.equals(phase(player)),"World command mutation lost");
    }

    private static String stored(FilePlayerRepository disk)throws Exception{
        Optional<PlayerSnapshot> saved=disk.load(USER);
        check(saved.isPresent(),"prior account unavailable");
        return saved.get().values().get(SNAPSHOT_KEY);
    }

    private static long completedSaves(World world){
        String state=world.persistence().metrics();
        String key=",completed=";
        int i=state.indexOf(key);
        check(i>=0,"missing metrics");
        i+=key.length();
        int end=state.indexOf(',',i);
        check(end>i,"invalid completed metric");
        return Long.parseLong(state.substring(i,end));
    }

    private static int refusedSocketLogin(int port)throws Exception{
        try(Socket socket=new Socket(
                InetAddress.getByName("127.0.0.1"),port)){
            socket.setSoTimeout(5000);
            InputStream input=socket.getInputStream();
            OutputStream output=socket.getOutputStream();
            output.write(14);
            output.write(7);
            output.flush();
            byte[] handshake=Binary.readExactly(input,9);
            check((handshake[8]&255)==0,"unexpected prelogin failure");
            long seed=Binary.i64(Binary.readExactly(input,8),0);
            check(seed==LocalLoginTransport.SERVER_SEED,
                "wrong server seed");
            int[] seeds={
                0x01020304,0x11223344,
                (int)(seed>>>32),(int)seed
            };
            byte[] frame=
                G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .loginFrame(seeds,USER);
            output.write(16);
            output.write(frame.length);
            output.write(frame);
            output.flush();
            return input.read();
        }
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21178-world-lifetime-");
        String previous=System.getProperty("spk.local.accountFile");
        System.setProperty("spk.local.accountFile",
            root.resolve("opensrc.properties").toString());
        World first=null;
        World guarded=null;
        World newWorld=null;
        try{
            Path file=LocalAccountProfiles.accountFile(USER);
            FilePlayerRepository normal=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            first=World.isolatedForTest(50L,normal);
            final World initial=first;
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession listener=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(initial)){
                try(Socket socket=listener.login(USER)){
                    await(()->initial.players().byName(USER)!=null,
                        "first socket login");
                    change(initial,initial.players().byName(USER),OLD);
                }
                await(()->initial.players().size()==0,
                    "first socket logout");
                listener.waitCompleted();
            }
            check(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS),
                "missing original committed account");
            check(OLD.equals(stored(normal)),
                "prior account not durably committed");
            first.close();

            AtomicBoolean armed=new AtomicBoolean();
            AtomicInteger injected=new AtomicInteger();
            FilePlayerRepository fault=new FilePlayerRepository(
                LocalAccountProfiles::accountFile,account->{
                    if(armed.get()&&USER.equals(account)){
                        injected.incrementAndGet();
                        throw new IOException(
                            "G21.178 TEST_ONLY_FINAL_IO_FAILURE");
                    }
                });
            guarded=World.isolatedForTest(50L,fault);
            final World second=guarded;
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession listener=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(second)){
                try(Socket socket=listener.login(USER)){
                    await(()->second.players().byName(USER)!=null,
                        "second socket login");
                    WorldPlayer current=second.players().byName(USER);
                    check(OLD.equals(phase(current)),
                        "second World did not hydrate old snapshot");
                    await(()->completedSaves(second)>=1L,
                        "second World bootstrap save");
                    byte[] before=Files.readAllBytes(file);
                    change(second,current,NEW);
                    armed.set(true);
                    socket.close();
                    await(()->second.players().size()==0,
                        "failed final-save unregister");
                    listener.waitCompleted();
                    check(injected.get()==1,
                        "expected exactly one failed final save");
                    check(second.persistence().failedCount()>=1L,
                        "failed final save not counted");
                    // The failing future wakes the session thread
                    // before the SaveTask's finally records the
                    // negative set. The worker still marks it before
                    // running any later LoadTask in the SAME FIFO.
                    await(()->second.persistence()
                            .failedFinalSaveAdmissionVetoCount()==1,
                        "World-lifetime final-save negative recorded");
                    check(Arrays.equals(before,Files.readAllBytes(file)),
                        "failed final save changed old account bytes");
                }
            }

            // Same World, next admitted TCP login is refused BEFORE any
            // repository account bytes are used to create a player.
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession rejected=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(second)){
                int answer=refusedSocketLogin(
                    rejected.listener.getLocalPort());
                rejected.waitCompleted();
                check(answer!=2,"same-World stale account was admitted");
                check(second.players().size()==0,
                    "refused reconnect registered a stale player");
            }
            check(second.persistence().failedFinalSaveAdmissionVetoCount()==1,
                "same-World negative admission vanished");
            check(OLD.equals(stored(normal)),
                "same-World refusal tampered with durable OLD");
            second.close();

            // Explicitly confirm what G21.178 does NOT fix: the negative
            // set was memory-only, so a third newly constructed World
            // currently still permits OLD from the previous disk file.
            newWorld=World.isolatedForTest(50L,normal);
            final World third=newWorld;
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession listener=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(third)){
                try(Socket socket=listener.login(USER)){
                    await(()->third.players().byName(USER)!=null,
                        "fresh World old file relogin");
                    check(OLD.equals(phase(third.players().byName(USER))),
                        "new World did not expose cross-restart gap");
                }
                await(()->third.players().size()==0,
                    "third World logout");
                listener.waitCompleted();
            }
            newWorld.close();
            boolean zeroLeases=
                MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1.activeLocalWatchCount()==0;
            check(zeroLeases,"JVM lock/watch leak");
            System.out.println("G21178_WORLD_LIFETIME_DIAGNOSTICS"+
                " realTcpSuccessfulLogins=3 failedFinalSaves=1"+
                " sameWorldStaleReconnectDenied=1"+
                " crossWorldStaleReconnectStillAllowed=1"+
                " oldDiskPostimagePreserved=true"+
                " negativeAccountSetSize=1"+
                " zeroJvmLeases="+zeroLeases);
            System.out.println("G21178_WORLD_LIFETIME_PASS"+
                " sameWorldNegativeAdmission=true"+
                " negativeNotAutoReleased=true"+
                " readOnlyForensicsUnchanged=true"+
                " durableCrossWorldFence=false"+
                " rawWriterExclusion=false atomicCrashEpoch=false"+
                " independentProvenance=false cleanup=false"+
                " rollover=false pruning=false unlink=false"+
                " grant=false replay=false sessionRelease=false ack=false");
        }finally{
            if(newWorld!=null&&!newWorld.closed())newWorld.close();
            if(guarded!=null&&!guarded.closed())guarded.close();
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
