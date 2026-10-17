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
 * Chat 1 G21.183: disabling the experimental property while an opt-in
 * account lease ALREADY exists must not allow a competing World to
 * bypass it. This is intentionally only a same-JVM existing-token test,
 * not a proof of safe arbitrary runtime flag transitions.
 */
public final class G21183CoreSocketActiveLeaseFlagOffVetoTest {
    private static final String USER="g21183-guarded";
    private static final String NS="core_lease_flag";
    private static final String KEY="phase";
    private static final String DISK_KEY="extension."+NS+"."+KEY;

    private static void check(boolean condition,String message){
        if(!condition)throw new AssertionError("G21.183 "+message);
    }

    @FunctionalInterface private interface Condition{
        boolean ready()throws Exception;
    }

    private static void await(Condition condition,String description)
        throws Exception{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8L);
        while(System.nanoTime()<until){
            if(condition.ready())return;
            Thread.sleep(10L);
        }
        check(condition.ready(),"timeout "+description);
    }

    private static void mutate(World world,WorldPlayer owner,String value)
        throws Exception{
        world.submit(owner,owner.generation(),()->{
            owner.snapshotExtensions().replaceNamespace(
                NS,Collections.singletonMap(KEY,value));
        }).get(4,TimeUnit.SECONDS);
        check(value.equals(owner.snapshotExtensions().namespace(NS).get(KEY)),
            "World-owned mutation not applied");
    }

    private static String stored(FilePlayerRepository repo)throws Exception{
        Optional<PlayerSnapshot> snapshot=repo.load(USER);
        check(snapshot.isPresent(),"account snapshot missing");
        return snapshot.get().values().get(DISK_KEY);
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
            check((pre[8]&255)==0,"prelogin refused");
            long serverSeed=Binary.i64(Binary.readExactly(in,8),0);
            check(serverSeed==LocalLoginTransport.SERVER_SEED,
                "server seed mismatch");
            int[] seeds={0x01020304,0x11223344,
                (int)(serverSeed>>>32),(int)serverSeed};
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
        Path root=Files.createTempDirectory("g21183-flag-off-");
        String previousFile=System.getProperty("spk.local.accountFile");
        String previousFlag=System.getProperty(
            LocalJvmAccountSessionLease.PROPERTY);
        System.setProperty("spk.local.accountFile",
            root.resolve("opensrc.properties").toString());
        System.setProperty(LocalJvmAccountSessionLease.PROPERTY,"true");
        World first=null,second=null;
        try{
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "unexpected preexisting test process lease");
            Path file=LocalAccountProfiles.accountFile(USER);
            Path secondary=LocalAccountProfiles.accountFile("src");
            check(!Files.exists(file,LinkOption.NOFOLLOW_LINKS),
                "fixture account already exists");
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
                        "first guarded socket login");
                    WorldPlayer ownerA=a.players().byName(USER);
                    check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                        "initial opt-in session did not acquire token");
                    mutate(a,ownerA,"A_GUARDED");

                    // Unlike G21.181, toggle the opt-in OFF after the
                    // original owner is already admitted. The existing
                    // token still must prevent a same-path second owner.
                    System.setProperty(
                        LocalJvmAccountSessionLease.PROPERTY,"false");
                    try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                            .OneSession denied=
                            new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                                .OneSession(b)){
                        check(attemptedLogin(
                                denied.listener.getLocalPort())!=2,
                            "flag-off bypassed the existing account lease");
                        denied.waitCompleted();
                    }
                    check(b.players().size()==0,
                        "second World registered bypassed account");
                    check(a.players().byName(USER)==ownerA,
                        "original owner lost membership");
                    check("A_GUARDED".equals(
                            ownerA.snapshotExtensions().namespace(NS).get(KEY)),
                        "denied competing login changed first owner");
                    check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                        "flag change removed original owner token");
                }
                await(()->a.players().size()==0,
                    "first session final unregister");
                listenerA.waitCompleted();
            }
            check("A_GUARDED".equals(stored(repo)),
                "first final save did not commit exact value");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "healthy final save did not release token");

            // With the last token released and the property OFF, prior
            // default-off behavior is retained. This is not a claim
            // about enabling the property after unleased logins.
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession listenerB=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(b)){
                try(Socket clientB=listenerB.login(USER)){
                    await(()->b.players().byName(USER)!=null,
                        "default-off later session login");
                    WorldPlayer ownerB=b.players().byName(USER);
                    check("A_GUARDED".equals(
                            ownerB.snapshotExtensions().namespace(NS).get(KEY)),
                        "later login did not hydrate confirmed account");
                    check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                        "default-off later login unexpectedly obtained token");
                    mutate(b,ownerB,"B_DEFAULT_OFF");
                }
                await(()->b.players().size()==0,
                    "second session final unregister");
                listenerB.waitCompleted();
            }
            check("B_DEFAULT_OFF".equals(stored(repo)),
                "default-off final save failed");
            check(!Files.exists(secondary,LinkOption.NOFOLLOW_LINKS),
                "normal named profile created src alias");
            check(a.persistence().failedCount()==0&&
                  b.persistence().failedCount()==0,
                "healthy final saves were marked failed");
            first.close();
            second.close();
            check(first.closed()&&second.closed(),"World close failed");
            boolean noLeaks=
                LocalJvmAccountSessionLease.activeCountForTest()==0&&
                MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1.activeLocalWatchCount()==0;
            check(noLeaks,"JVM account/publication token or watch leak");
            System.out.println(
                "G21183_FLAG_DRIFT_DIAGNOSTICS"+
                " sameJvmWorlds=2 admittedFirstGuardedOwner=true"+
                " flagDisabledWhileLeaseHeld=true"+
                " competingWorldDenied=true"+
                " originalSnapshotCommitted=true"+
                " tokenReleasedOnConfirmedSave=true"+
                " defaultOffSecondLoginAccepted=true"+
                " exactHydration=true defaultOffSecondSaveCommitted=true"+
                " zeroJvmLeases="+noLeaks);
            System.out.println(
                "G21183_FLAG_DRIFT_PASS"+
                " existingTokenVetoSurvivesFlagOff=true"+
                " arbitraryFlagTransitionsSafe=false"+
                " defaultOffWithoutTokensUnchanged=true"+
                " crossProcessFence=false crashDurable=false"+
                " rawWriterExclusion=false"+
                " independentProvenance=false atomicCrashEpoch=false"+
                " originalGraphicalClientProven=false"+
                " cleanup=false rollover=false pruning=false unlink=false"+
                " grant=false replay=false sessionRelease=false ack=false");
        }finally{
            if(second!=null&&!second.closed())second.close();
            if(first!=null&&!first.closed())first.close();
            if(previousFile==null)
                System.clearProperty("spk.local.accountFile");
            else System.setProperty("spk.local.accountFile",previousFile);
            if(previousFlag==null)
                System.clearProperty(LocalJvmAccountSessionLease.PROPERTY);
            else System.setProperty(LocalJvmAccountSessionLease.PROPERTY,
                previousFlag);
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
