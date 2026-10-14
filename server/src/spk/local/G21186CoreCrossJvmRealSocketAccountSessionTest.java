package spk.local;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.186: actual loopback LocalSession logins against the same named
 * account, first in a parent JVM and then in two independent CHILD
 * JVMs. The OS lock is held by an authenticated online player until
 * its real final write completed; the competing child is refused,
 * while a subsequent child can hydrate the postimage and save again.
 *
 * Narrow positive evidence for cooperating processes ONLY.
 * No crash-durable intent, raw writer exclusion, real GUI, or
 * original SpawnPK account/reward/settlement authority.
 */
public final class G21186CoreCrossJvmRealSocketAccountSessionTest {
    private static final String USER="g21186-real-socket";
    private static final String NS="core_cross_jvm_socket";
    private static final String KEY="phase";
    private static final String STORED="extension."+NS+"."+KEY;

    private G21186CoreCrossJvmRealSocketAccountSessionTest(){}

    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G21.186 "+why);
    }

    @FunctionalInterface private interface Condition{
        boolean ready()throws Exception;
    }

    private static void await(Condition cond,String reason)throws Exception{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10L);
        while(System.nanoTime()<until){
            if(cond.ready())return;
            Thread.sleep(10L);
        }
        check(cond.ready(),"timeout "+reason);
    }

    private static void change(
        World world,WorldPlayer player,String value
    )throws Exception{
        world.submit(player,player.generation(),()->{
            player.snapshotExtensions().replaceNamespace(
                NS,Collections.singletonMap(KEY,value)
            );
        }).get(5L,TimeUnit.SECONDS);
        check(value.equals(player.snapshotExtensions()
            .namespace(NS).get(KEY)),"World mutation missing");
    }

    private static String stored(FilePlayerRepository repository)
        throws Exception{
        Optional<PlayerSnapshot> s=repository.load(USER);
        check(s.isPresent(),"account file is absent");
        return s.get().values().get(STORED);
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
            check((pre[8]&255)==0,"prelogin reply mismatched");
            long serverSeed=Binary.i64(
                Binary.readExactly(in,8),0);
            check(serverSeed==LocalLoginTransport.SERVER_SEED,
                "synthetic login server seed mismatched");
            int[] seeds={
                0x01020304,0x11223344,
                (int)(serverSeed>>>32),(int)serverSeed
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

    private static void child(String mode)throws Exception{
        check("denied".equals(mode)||"admitted".equals(mode),
            "unexpected child case");
        check(Boolean.getBoolean(LocalJvmAccountSessionLease.PROPERTY),
            "child JVM lease flag disabled");
        check(Boolean.getBoolean(
            LocalCrossJvmAccountSessionLock.PROPERTY),
            "child cross-JVM lock flag disabled");
        FilePlayerRepository repo=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        World world=World.isolatedForTest(50L,repo);
        try{
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession listener=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(world)){
                if("denied".equals(mode)){
                    check(attemptedLogin(
                        listener.listener.getLocalPort())!=2,
                        "competing child TCP login succeeded");
                    listener.waitCompleted();
                    check(world.players().size()==0,
                        "denied child registered owner");
                    check(LocalJvmAccountSessionLease
                        .activeCountForTest()==0,
                        "denied child owns JVM token");
                    System.out.println(
                        "G21186_CHILD_SOCKET_DENIED"+
                        " actualLoopbackLogin=true"+
                        " competingProcessAdmissionRefused=true");
                }else{
                    check("A_FINAL".equals(stored(repo)),
                        "child did not see committed original account");
                    try(Socket accepted=listener.login(USER)){
                        await(()->world.players().byName(USER)!=null,
                            "child named login membership");
                        WorldPlayer player=
                            world.players().byName(USER);
                        check("A_FINAL".equals(player
                            .snapshotExtensions().namespace(NS).get(KEY)),
                            "child did not hydrate A final snapshot");
                        check(LocalJvmAccountSessionLease
                            .activeCountForTest()==1,
                            "child OS-backed JVM token missing");
                        change(world,player,"B_FINAL");
                    }
                    await(()->world.players().size()==0,
                        "child final logout");
                    listener.waitCompleted();
                    check("B_FINAL".equals(stored(repo)),
                        "child final account postimage missing");
                    check(LocalJvmAccountSessionLease
                        .activeCountForTest()==0,
                        "child token not released after final receipt");
                    System.out.println(
                        "G21186_CHILD_SOCKET_ADMITTED"+
                        " actualLoopbackLogin=true"+
                        " exactPreviousAccountHydrated=true"+
                        " exactChildFinalSnapshotCommitted=true");
                }
            }
        }finally{
            world.close();
        }
        check(world.closed(),"child World did not close");
        check(MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
              MailboxLocalWriterAttemptWatchV1
                .activeLocalWatchCount()==0,
            "child leaked unrelated publication resources");
    }

    private static void runExternalChild(
        Path settings,Path transcript,String mode
    )throws Exception{
        String executable=System.getProperty("java.home")+
            java.io.File.separator+"bin"+java.io.File.separator+
            (System.getProperty("os.name","").toLowerCase()
                .contains("win")?"java.exe":"java");
        ProcessBuilder builder=new ProcessBuilder(
            executable,
            "-Dspk.local.accountFile="+settings,
            "-D"+LocalJvmAccountSessionLease.PROPERTY+"=true",
            "-D"+LocalCrossJvmAccountSessionLock.PROPERTY+"=true",
            "-cp",System.getProperty("java.class.path"),
            G21186CoreCrossJvmRealSocketAccountSessionTest
                .class.getName(),
            "--child",mode
        );
        builder.redirectErrorStream(true);
        builder.redirectOutput(transcript.toFile());
        Process process=builder.start();
        boolean done=process.waitFor(25L,TimeUnit.SECONDS);
        if(!done){
            process.destroyForcibly();
            process.waitFor(5L,TimeUnit.SECONDS);
            throw new AssertionError(
                "G21.186 external JVM timeout mode="+mode);
        }
        String log=new String(
            Files.readAllBytes(transcript),
            StandardCharsets.UTF_8);
        check(process.exitValue()==0,
            "external JVM failed mode="+mode+" output="+log);
        String marker="admitted".equals(mode)
            ?"G21186_CHILD_SOCKET_ADMITTED"
            :"G21186_CHILD_SOCKET_DENIED";
        check(log.contains(marker),
            "external JVM missing expected marker: "+log);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21186-actual-tcp-");
        Path settings=root.resolve("opensrc.properties");
        String priorAccount=System.getProperty("spk.local.accountFile");
        String priorJvm=System.getProperty(
            LocalJvmAccountSessionLease.PROPERTY);
        String priorCross=System.getProperty(
            LocalCrossJvmAccountSessionLock.PROPERTY);
        System.setProperty("spk.local.accountFile",settings.toString());
        System.setProperty(LocalJvmAccountSessionLease.PROPERTY,"true");
        System.setProperty(
            LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        World parent=null;
        try{
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "prior same-JVM owner unexpectedly present");
            Path account=LocalAccountProfiles.accountFile(USER);
            Path lock=account.resolveSibling(
                account.getFileName().toString()+".session.lck");
            Path alias=LocalAccountProfiles.accountFile("src");
            check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "named profile already exists");
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            parent=World.isolatedForTest(50L,repo);
            final World world=parent;
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession listener=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(world)){
                try(Socket client=listener.login(USER)){
                    await(()->world.players().byName(USER)!=null,
                        "parent actual TCP login");
                    WorldPlayer player=world.players().byName(USER);
                    check(LocalJvmAccountSessionLease
                        .activeCountForTest()==1,
                        "parent session token missing");
                    check(Files.isRegularFile(lock,
                        LinkOption.NOFOLLOW_LINKS),
                        "parent OS lock file missing");
                    change(world,player,"A_FINAL");

                    // The rejected child is a distinct JVM and an actual
                    // new LocalSession using synthetic 317/ISAAC login.
                    runExternalChild(settings,
                        root.resolve("denied.log"),"denied");
                    check(world.players().byName(USER)==player,
                        "competing child displaced parent owner");
                    check(LocalJvmAccountSessionLease
                        .activeCountForTest()==1,
                        "parent token lost during child refusal");
                }
                await(()->world.players().size()==0,
                    "parent final unregister");
                listener.waitCompleted();
            }
            check("A_FINAL".equals(stored(repo)),
                "parent successful final snapshot not published");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "parent account token not released after final write");

            // New process authenticates only AFTER A has retired and
            // hydrates exact A postimage before final-saving B.
            runExternalChild(settings,
                root.resolve("admitted.log"),"admitted");
            check("B_FINAL".equals(stored(repo)),
                "external child postimage not published");
            check(Files.isRegularFile(lock,
                LinkOption.NOFOLLOW_LINKS),
                "child incorrectly removed stable lockfile");
            check(!Files.exists(alias,LinkOption.NOFOLLOW_LINKS),
                "secondary src account unexpectedly created");
            check(parent.persistence().failedCount()==0,
                "parent persistence writer failed");
            parent.close();
            check(parent.closed(),"parent World not closed");
            boolean noLeaks=
                LocalJvmAccountSessionLease.activeCountForTest()==0&&
                MailboxAccountPublicationCoordinator
                    .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            check(noLeaks,"parent account/publication lease leaked");
            System.out.println(
                "G21186_CROSS_JVM_SOCKET_DIAGNOSTICS"+
                " realTcpSessions=3 distinctJvmChildren=2"+
                " competingChildRefused=true"+
                " parentFinalCommitted=true"+
                " newChildPostimageHydrated=true"+
                " newChildFinalCommitted=true"+
                " stableLockfilePreserved=true"+
                " zeroJvmLeaks="+noLeaks);
            System.out.println(
                "G21186_CROSS_JVM_SOCKET_PASS"+
                " cooperatingCrossProcessSocketFence=true"+
                " featureDefaultOff=true"+
                " rawUncooperativeWritersExcluded=false"+
                " crashDurableNegative=false"+
                " processKillRecovery=false"+
                " hostileLockfileUnlinkProtected=false"+
                " multiFileCrashEpoch=false"+
                " originalGraphicalClientProven=false"+
                " settlement=false grants=false replay=false"+
                " cleanup=false rollover=false unlink=false"+
                " durableSessionRelease=false ack=false");
        }finally{
            if(parent!=null&&!parent.closed())parent.close();
            if(priorAccount==null)
                System.clearProperty("spk.local.accountFile");
            else System.setProperty("spk.local.accountFile",priorAccount);
            if(priorJvm==null)
                System.clearProperty(
                    LocalJvmAccountSessionLease.PROPERTY);
            else System.setProperty(
                LocalJvmAccountSessionLease.PROPERTY,priorJvm);
            if(priorCross==null)
                System.clearProperty(
                    LocalCrossJvmAccountSessionLock.PROPERTY);
            else System.setProperty(
                LocalCrossJvmAccountSessionLock.PROPERTY,priorCross);
            try(Stream<Path> paths=Files.walk(root)){
                for(Path path:paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length==2&&"--child".equals(args[0])){
            child(args[1]);
        }else{
            check(args.length==0,"unexpected args");
            run();
        }
    }
}
