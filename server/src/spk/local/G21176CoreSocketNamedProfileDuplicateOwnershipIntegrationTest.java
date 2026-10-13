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
 * G21.176: real localhost TCP named-profile duplicate admission.
 *
 * Explicit arbitrary profile aliases must never acquire a second
 * simultaneous World owner; LocalLab's special opensrc/localtest
 * two-slot convenience rule is INTENTIONALLY OUTSIDE this test.
 *
 * No original-client GUI, raw writer exclusion, historic reward or
 * complete transaction epoch authority is inferred.
 */
public final class G21176CoreSocketNamedProfileDuplicateOwnershipIntegrationTest {
    private static final String USER="g21176-owned";
    private static final String NAMESPACE="core_ownership";
    private static final String KEY="phase";
    private static final String SNAPSHOT_KEY="extension.core_ownership.phase";

    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G21.176 "+reason);
    }

    @FunctionalInterface private interface Condition{
        boolean get()throws Exception;
    }

    private static void await(Condition condition,String description)
        throws Exception{
        final long deadline=System.nanoTime()+
            TimeUnit.SECONDS.toNanos(6L);
        while(System.nanoTime()<deadline){
            if(condition.get())return;
            Thread.sleep(10L);
        }
        check(condition.get(),"timed out "+description);
    }

    private static String phase(WorldPlayer owner){
        return owner.snapshotExtensions().namespace(NAMESPACE).get(KEY);
    }

    private static void phase(World world,WorldPlayer owner,String value)
        throws Exception{
        world.submit(owner,owner.generation(),()->{
            owner.snapshotExtensions().replaceNamespace(
                NAMESPACE,Collections.singletonMap(KEY,value));
        }).get(4L,TimeUnit.SECONDS);
        check(value.equals(phase(owner)),
            "World-owned player mutation was not applied");
    }

    private static int duplicateLoginResponse(int port)throws Exception{
        try(Socket second=new Socket(
                InetAddress.getByName("127.0.0.1"),port)){
            second.setSoTimeout(5000);
            InputStream in=second.getInputStream();
            OutputStream out=second.getOutputStream();
            out.write(14);
            out.write(7);
            out.flush();

            byte[] prelogin=Binary.readExactly(in,9);
            check((prelogin[8]&255)==0,"duplicate prelogin rejected early");
            long serverSeed=Binary.i64(Binary.readExactly(in,8),0);
            check(serverSeed==LocalLoginTransport.SERVER_SEED,
                "unexpected duplicate login server seed");
            int[] seeds={
                0x01020304,0x11223344,
                (int)(serverSeed>>>32),(int)serverSeed
            };
            byte[] frame=
                G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .loginFrame(seeds,USER);
            check(frame.length<256,"synthetic duplicate login oversized");
            out.write(16);
            out.write(frame.length);
            out.write(frame);
            out.flush();

            // An accepted session begins with 2/205/0. Normal denied
            // LocalSession initialization logs and closes the socket.
            // This must not become a successful second owner.
            return in.read();
        }
    }

    private static String storedPhase(FilePlayerRepository repository)
        throws Exception{
        Optional<PlayerSnapshot> snapshot=repository.load(USER);
        check(snapshot.isPresent(),"account snapshot did not persist");
        return snapshot.get().values().get(SNAPSHOT_KEY);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21176-owner-restart-");
        String previous=System.getProperty("spk.local.accountFile");
        System.setProperty("spk.local.accountFile",
            root.resolve("opensrc.properties").toString());
        World initial=null;
        World next=null;
        try{
            Path namedFile=LocalAccountProfiles.accountFile(USER);
            Path legacySecondary=LocalAccountProfiles.accountFile("src");
            check(namedFile.getParent().endsWith("profiles"),
                "ordinary named profile did not use hashed profile root");
            check(!Files.exists(namedFile,LinkOption.NOFOLLOW_LINKS),
                "unexpected account fixture preimage");
            FilePlayerRepository saved=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);

            initial=World.isolatedForTest(50L);
            final World first=initial;
            WorldPlayer originalOwner;
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession firstListener=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(first);
                G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession competingListener=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(first)){
                try(Socket original=firstListener.login(USER)){
                    await(()->first.players().byName(USER)!=null,
                        "first named account authenticated");
                    originalOwner=first.players().byName(USER);
                    check(first.players().size()==1,
                        "first session unexpectedly created extra member");
                    phase(first,originalOwner,"OWNER_A");

                    int refused=duplicateLoginResponse(
                        competingListener.listener.getLocalPort());
                    competingListener.waitCompleted();
                    check(refused!=2,
                        "second simultaneous named profile was admitted");
                    check(first.players().size()==1&&
                        first.players().byName(USER)==originalOwner&&
                        originalOwner.registered(),
                        "duplicate login replaced registered first owner");
                    check("OWNER_A".equals(phase(originalOwner)),
                        "duplicate login modified original World state");

                    // The ORIGINAL authenticated socket still owns the
                    // account and may mutate after rejected competitor.
                    phase(first,originalOwner,"OWNER_A_LATE");
                    await(()->first.clock().tick()>=2L,
                        "first World ticks");
                }
                await(()->first.players().size()==0,
                    "first named account final logout");
                firstListener.waitCompleted();
            }

            check(Files.isRegularFile(
                    namedFile,LinkOption.NOFOLLOW_LINKS),
                "named profile final save was not created");
            check("OWNER_A_LATE".equals(storedPhase(saved)),
                "rejected competitor clobbered final account snapshot");
            check(!Files.exists(
                    legacySecondary,LinkOption.NOFOLLOW_LINKS),
                "named account unexpectedly selected legacy src alias");
            initial.close();
            check(initial.closed(),"first World did not shut down");

            next=World.isolatedForTest(50L);
            final World reboot=next;
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession relogin=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(reboot)){
                try(Socket client=relogin.login(USER)){
                    await(()->reboot.players().byName(USER)!=null,
                        "restart named profile relogin");
                    WorldPlayer hydrated=reboot.players().byName(USER);
                    check(hydrated!=originalOwner,
                        "old WorldPlayer reused after World restart");
                    check("OWNER_A_LATE".equals(phase(hydrated)),
                        "restart failed to hydrate exact owner mutation");
                    await(()->reboot.clock().tick()>=2L,
                        "reboot World ticks");
                }
                await(()->reboot.players().size()==0,
                    "restarted named account final logout");
                relogin.waitCompleted();
            }
            check("OWNER_A_LATE".equals(storedPhase(saved)),
                "restarted account did not preserve exact snapshot");
            check(!Files.exists(
                    legacySecondary,LinkOption.NOFOLLOW_LINKS),
                "secondary LocalLab account unexpectedly created");
            next.close();
            check(next.closed(),"second World failed to close");
            boolean noLeases=
                MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1.activeLocalWatchCount()==0;
            check(noLeases,"persistence JVM lease/watch retained");

            System.out.println(
                "G21176_NAMED_OWNER_DIAGNOSTICS"+
                " successfulSocketLogins=2"+
                " refusedConcurrentDuplicateLogins=1"+
                " initialOwnedPlayers=1"+
                " originalOwnerSurvivedDuplicate=true"+
                " laterWorldMutationPersisted=true"+
                " hashedNamedProfile=true"+
                " originalSnapshotHydratedAfterWorldRestart=true"+
                " legacySrcCreated=false"+
                " worldInstances=2 zeroJvmLeases="+noLeases
            );
            System.out.println(
                "G21176_NAMED_OWNER_PASS"+
                " exactGenericAccountOwnership=true"+
                " duplicateSessionCannotPublish=true"+
                " legacyOpensrcLocaltestAliasPolicyUnchanged=true"+
                " originalGraphicalClientProven=false"+
                " allUncooperativeWritersExcluded=false"+
                " atomicCrashEpoch=false independentProvenance=false"+
                " cleanup=false rollover=false pruning=false"+
                " unlink=false grant=false replay=false"+
                " sessionRelease=false ack=false"
            );
        }finally{
            if(next!=null&&!next.closed())next.close();
            if(initial!=null&&!initial.closed())initial.close();
            if(previous==null)
                System.clearProperty("spk.local.accountFile");
            else
                System.setProperty("spk.local.accountFile",previous);
            try(Stream<Path> paths=Files.walk(root)){
                for(Path path:paths.sorted(Comparator.reverseOrder())
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
