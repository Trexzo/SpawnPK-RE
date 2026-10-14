package spk.local;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.181: opt-in same-JVM named-account lease serializes two Worlds'
 * real authenticated socket sessions from pre-load to confirmed final
 * save. This is NOT enabled by default, cross-JVM, or crash durable.
 */
public final class G21181CoreSocketOptInJvmAccountSessionLeaseIntegrationTest {
    private static final String USER="g21181-lease";
    private static final String NS="core_jvm_lease";
    private static final String KEY="phase";
    private static final String STORED="extension."+NS+"."+KEY;

    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G21.181 "+reason);
    }

    @FunctionalInterface private interface Condition{
        boolean ready()throws Exception;
    }

    private static void await(Condition condition,String reason)
        throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(System.nanoTime()<deadline){
            if(condition.ready())return;
            Thread.sleep(10);
        }
        check(condition.ready(),"timeout "+reason);
    }

    private static String phase(WorldPlayer player){
        return player.snapshotExtensions().namespace(NS).get(KEY);
    }

    private static void change(World world,WorldPlayer player,String value)
        throws Exception{
        world.submit(player,player.generation(),()->{
            player.snapshotExtensions().replaceNamespace(
                NS,Collections.singletonMap(KEY,value));
        }).get(4,TimeUnit.SECONDS);
        check(value.equals(phase(player)),"World mutation missing");
    }

    private static int response(int port)throws Exception{
        try(Socket socket=new Socket(
                InetAddress.getByName("127.0.0.1"),port)){
            socket.setSoTimeout(5000);
            InputStream in=socket.getInputStream();
            OutputStream out=socket.getOutputStream();
            out.write(14);
            out.write(7);
            out.flush();
            byte[] initial=Binary.readExactly(in,9);
            check((initial[8]&255)==0,"prelogin mismatch");
            long seed=Binary.i64(Binary.readExactly(in,8),0);
            check(seed==LocalLoginTransport.SERVER_SEED,
                "unexpected seed");
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

    private static String onDisk(FilePlayerRepository repository)
        throws Exception{
        Optional<PlayerSnapshot> stored=repository.load(USER);
        check(stored.isPresent(),"account missing");
        return stored.get().values().get(STORED);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21181-lease-");
        String oldFile=System.getProperty("spk.local.accountFile");
        String oldFlag=System.getProperty(
            LocalJvmAccountSessionLease.PROPERTY);
        System.setProperty("spk.local.accountFile",
            root.resolve("opensrc.properties").toString());
        System.setProperty(LocalJvmAccountSessionLease.PROPERTY,"true");
        World first=null,second=null;
        try{
            Path account=LocalAccountProfiles.accountFile(USER);
            Path alias=LocalAccountProfiles.accountFile("src");
            check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "account unexpectedly exists");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "prior lease unexpectedly present");
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            first=World.isolatedForTest(50L,repo);
            second=World.isolatedForTest(50L,repo);
            final World a=first,b=second;

            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession listenerA=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(a)){
                try(Socket clientA=listenerA.login(USER)){
                    await(()->a.players().byName(USER)!=null,
                        "World A login");
                    check(LocalJvmAccountSessionLease
                            .activeCountForTest()==1,
                        "World A account lease missing");
                    WorldPlayer ownerA=a.players().byName(USER);
                    change(a,ownerA,"A_FINAL");

                    try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                            .OneSession duplicate=
                            new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                                .OneSession(b)){
                        check(response(duplicate.listener.getLocalPort())!=2,
                            "World B duplicate login was accepted");
                        duplicate.waitCompleted();
                    }
                    check(b.players().size()==0,
                        "duplicate World B owner was registered");
                    check(a.players().byName(USER)==ownerA,
                        "World A original owner displaced by duplicate");
                    check(LocalJvmAccountSessionLease
                            .activeCountForTest()==1,
                        "failed admission incorrectly released A lease");
                }
                await(()->a.players().size()==0,"World A unregister");
                listenerA.waitCompleted();
            }
            check("A_FINAL".equals(onDisk(repo)),
                "World A final snapshot not persisted");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "lease was not released after confirmed A final save");

            // World A remains instantiated; only its session has retired.
            // A new authenticated session in the distinct World B can
            // now hydrate A's exact confirmed account postimage.
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession listenerB=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(b)){
                try(Socket clientB=listenerB.login(USER)){
                    await(()->b.players().byName(USER)!=null,
                        "World B post-commit login");
                    WorldPlayer ownerB=b.players().byName(USER);
                    check("A_FINAL".equals(phase(ownerB)),
                        "World B did not hydrate A confirmed postimage");
                    check(LocalJvmAccountSessionLease
                            .activeCountForTest()==1,
                        "World B lease not transferred to new owner");
                    change(b,ownerB,"B_FINAL");
                }
                await(()->b.players().size()==0,"World B unregister");
                listenerB.waitCompleted();
            }
            check("B_FINAL".equals(onDisk(repo)),
                "World B final snapshot not persisted");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "confirmed World B save left stranded lease");
            check(!Files.exists(alias,LinkOption.NOFOLLOW_LINKS),
                "wrong secondary src account created");
            check(a.persistence().failedCount()==0&&
                  b.persistence().failedCount()==0,
                "successful final writes marked failed");
            first.close();
            second.close();
            check(first.closed()&&second.closed(),
                "Worlds did not close cleanly");
            boolean noLeaks=
                MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1.activeLocalWatchCount()==0;
            check(noLeaks,"publication lease/watch leak");
            System.out.println(
                "G21181_OPTIN_JVM_LEASE_DIAGNOSTICS"+
                " simultaneousWorlds=2 sameProfile=true"+
                " firstSocketAdmitted=true competingWorldSocketDenied=true"+
                " firstFinalSaveCommitted=true"+
                " newWorldSocketAfterCommitAdmitted=true"+
                " exactFirstSnapshotHydrated=true"+
                " secondFinalSaveCommitted=true"+
                " outstandingAccountSessionLeases=0"+
                " zeroJvmPublicationLeases="+noLeaks);
            System.out.println(
                "G21181_OPTIN_JVM_LEASE_PASS"+
                " experimentalOptIn=true defaultPolicyUnchanged=true"+
                " concurrentSameJvmSessionFence=true"+
                " leaseHeldUntilConfirmedFinalSave=true"+
                " crossJvmProcessFence=false crashDurability=false"+
                " rawLegacyWriterExcluded=false"+
                " completeFailedFinalSaveRecovery=false"+
                " originalGraphicalClientProven=false"+
                " atomicCrashEpoch=false independentProvenance=false"+
                " cleanup=false rollover=false pruning=false unlink=false"+
                " grant=false replay=false sessionRelease=false ack=false");
        }finally{
            if(second!=null&&!second.closed())second.close();
            if(first!=null&&!first.closed())first.close();
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
