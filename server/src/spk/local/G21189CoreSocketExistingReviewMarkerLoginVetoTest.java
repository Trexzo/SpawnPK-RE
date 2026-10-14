package spk.local;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
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
 * G21.189: real TCP session admission, opt-in READ-ONLY review veto.
 * A first-time marked account must not bootstrap; an existing marked
 * account must not hydrate in a fresh JVM; an unmarked account can
 * still login and complete healthy final save.
 *
 * Neither intent creation on login nor durable intent retirement is
 * implemented. No raw writer, crash epoch, or power-loss safety proof.
 */
public final class G21189CoreSocketExistingReviewMarkerLoginVetoTest {
    private static final String MARKED="g21189-marked";
    private static final String CLEAN="g21189-clean";
    private static final String NS="core_review_veto";
    private static final String KEY="phase";
    private static final String STORED="extension."+NS+"."+KEY;

    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G21.189 "+reason);
    }

    @FunctionalInterface private interface Condition{
        boolean ready()throws Exception;
    }

    private static void await(Condition c,String reason)throws Exception{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8L);
        while(System.nanoTime()<until){
            if(c.ready())return;
            Thread.sleep(10L);
        }
        check(c.ready(),"timeout "+reason);
    }

    private static int attemptedLogin(int port,String username)
        throws Exception{
        try(Socket socket=new Socket(
                InetAddress.getByName("127.0.0.1"),port)){
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
                "server login seed mismatch");
            int[] seeds={0x01020304,0x11223344,
                (int)(seed>>>32),(int)seed};
            byte[] frame=
                G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .loginFrame(seeds,username);
            out.write(16);
            out.write(frame.length);
            out.write(frame);
            out.flush();
            return in.read();
        }
    }

    private static void deny(
        World world,String username,String label
    )throws Exception{
        try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                .OneSession listener=
                new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession(world)){
            check(attemptedLogin(
                listener.listener.getLocalPort(),username)!=2,
                label+" login accepted despite review intent");
            listener.waitCompleted();
        }
        check(world.players().size()==0,
            label+" login registered World player");
        check(LocalJvmAccountSessionLease.activeCountForTest()==0,
            label+" initializer retained a rejected session token");
    }

    private static String diskPhase(FilePlayerRepository repository)
        throws Exception{
        Optional<PlayerSnapshot> snap=repository.load(CLEAN);
        check(snap.isPresent(),"clean account not persisted");
        return snap.get().values().get(STORED);
    }

    private static void child(String username)throws Exception{
        check(Boolean.getBoolean(
            LocalReviewIntentAdmissionVeto.PROPERTY),
            "child review opt-in missing");
        World world=World.isolatedForTest(50L,
            new FilePlayerRepository(LocalAccountProfiles::accountFile));
        try{
            deny(world,username,"fresh JVM marked existing account");
            System.out.println(
                "G21189_FRESH_JVM_REVIEW_DENIED"+
                " realTcpLoginRejected=true"+
                " noWorldPlayerRegistered=true"+
                " noResidualSessionLease=true");
        }finally{
            world.close();
        }
    }

    private static void runFreshJvm(
        Path settings,Path log,String username
    )throws Exception{
        String javaExecutable=System.getProperty("java.home")+
            java.io.File.separator+"bin"+java.io.File.separator+
            (System.getProperty("os.name","").toLowerCase()
                .contains("win")?"java.exe":"java");
        ProcessBuilder pb=new ProcessBuilder(javaExecutable,
            "-Dspk.local.accountFile="+settings,
            "-D"+LocalJvmAccountSessionLease.PROPERTY+"=true",
            "-D"+LocalCrossJvmAccountSessionLock.PROPERTY+"=true",
            "-D"+LocalReviewIntentAdmissionVeto.PROPERTY+"=true",
            "-cp",System.getProperty("java.class.path"),
            G21189CoreSocketExistingReviewMarkerLoginVetoTest
                .class.getName(),
            "--child",username);
        pb.redirectErrorStream(true);
        pb.redirectOutput(log.toFile());
        Process p=pb.start();
        if(!p.waitFor(20L,TimeUnit.SECONDS)){
            p.destroyForcibly();
            p.waitFor(5L,TimeUnit.SECONDS);
            throw new AssertionError("G21.189 child timed out");
        }
        String stdout=new String(Files.readAllBytes(log),
            StandardCharsets.UTF_8);
        check(p.exitValue()==0,"child failed: "+stdout);
        check(stdout.contains("G21189_FRESH_JVM_REVIEW_DENIED"),
            "fresh JVM marker denial not witnessed: "+stdout);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21189-review-veto-");
        Path settings=root.resolve("opensrc.properties");
        String oldFile=System.getProperty("spk.local.accountFile");
        String oldJvm=System.getProperty(
            LocalJvmAccountSessionLease.PROPERTY);
        String oldCross=System.getProperty(
            LocalCrossJvmAccountSessionLock.PROPERTY);
        String oldVeto=System.getProperty(
            LocalReviewIntentAdmissionVeto.PROPERTY);
        System.setProperty("spk.local.accountFile",
            settings.toString());
        System.setProperty(LocalJvmAccountSessionLease.PROPERTY,"true");
        System.setProperty(
            LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        System.setProperty(
            LocalReviewIntentAdmissionVeto.PROPERTY,"true");
        World world=null;
        try{
            Path first=LocalAccountProfiles.accountFile(MARKED);
            Path clean=LocalAccountProfiles.accountFile(CLEAN);
            Path alias=LocalAccountProfiles.accountFile("src");
            check(!Files.exists(first,LinkOption.NOFOLLOW_LINKS)&&
                !Files.exists(clean,LinkOption.NOFOLLOW_LINKS),
                "fixture account file exists before first login");
            Files.createDirectories(first.getParent());
            Path firstMarker=
                LocalDurableAccountReviewIntentPrimitive.writeActiveReview(
                    first);
            check(Files.isRegularFile(firstMarker,
                LinkOption.NOFOLLOW_LINKS),
                "first-time account review marker missing");

            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            world=World.isolatedForTest(50L,repo);
            final World initial=world;
            deny(initial,MARKED,"first-time marked");
            check(!Files.exists(first,LinkOption.NOFOLLOW_LINKS),
                "marked first-time login synthesized BOOTSTRAP account");
            check(Files.isRegularFile(firstMarker,
                LinkOption.NOFOLLOW_LINKS),
                "denied login removed first-time review marker");

            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession listener=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(initial)){
                try(Socket accepted=listener.login(CLEAN)){
                    await(()->initial.players().byName(CLEAN)!=null,
                        "unmarked account real TCP accepted");
                    WorldPlayer owner=initial.players().byName(CLEAN);
                    check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                        "clean admitted account lacks session token");
                    initial.submit(owner,owner.generation(),()->{
                        owner.snapshotExtensions().replaceNamespace(
                            NS,Collections.singletonMap(KEY,"HEALTHY_SAVE"));
                    }).get(5L,TimeUnit.SECONDS);
                }
                await(()->initial.players().size()==0,
                    "clean account final unregister");
                listener.waitCompleted();
            }
            check("HEALTHY_SAVE".equals(diskPhase(repo)),
                "unmarked account healthy final snapshot missing");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "healthy final save left session token");
            check(!Files.exists(
                LocalDurableAccountReviewIntentPrimitive.markerPath(clean),
                LinkOption.NOFOLLOW_LINKS),
                "ordinary clean login created review marker automatically");
            world.close();
            check(world.closed(),"World did not fully close");
            byte[] persisted=Files.readAllBytes(clean);
            check(persisted.length>0,"clean saved account empty");

            Path persistedReview=
                LocalDurableAccountReviewIntentPrimitive.writeActiveReview(
                    clean);
            check(Files.isRegularFile(persistedReview,
                LinkOption.NOFOLLOW_LINKS),
                "existing account review marker missing");
            byte[] markerBytes=Files.readAllBytes(persistedReview);

            runFreshJvm(settings,root.resolve("child.log"),CLEAN);

            check(Arrays.equals(persisted,Files.readAllBytes(clean)),
                "fresh denied login changed saved account bytes");
            check(Arrays.equals(markerBytes,
                Files.readAllBytes(persistedReview)),
                "fresh denied login cleared/changed review bytes");
            check(!Files.exists(alias,LinkOption.NOFOLLOW_LINKS),
                "unexpected src profile alias was created");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "launcher retained JVM account session token");
            check(MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0,
                "unrelated publication resources leaked");

            System.out.println(
                "G21189_REVIEW_ADMISSION_DIAGNOSTICS"+
                " firstTimeMarkedSocketDenied=true"+
                " firstTimeBootstrapNotCreated=true"+
                " cleanAccountSocketAccepted=true"+
                " confirmedCleanFinalSave=true"+
                " noAutomaticMarkerCreate=true"+
                " existingAccountMarkedAfterSave=true"+
                " freshJvmRealTcpRefused=true"+
                " accountBytesUnchanged=true"+
                " reviewBytesUnchanged=true"+
                " zeroJvmLeaseLeaks=true");
            System.out.println(
                "G21189_REVIEW_ADMISSION_PASS"+
                " optInReadOnlyMarkerVeto=true"+
                " defaultOff=true"+
                " requireBothSessionLocks=true"+
                " noAutomaticReviewIntentCreate=true"+
                " noAutomaticReviewIntentRetire=true"+
                " crashEpochSafe=false"+
                " uncooperativeRawWriterExcluded=false"+
                " intentCreationOnLogin=false"+
                " durableFinalSaveReceipt=false"+
                " originalClientProven=false"+
                " grants=false replay=false recovery=false");
        }finally{
            if(world!=null&&!world.closed())world.close();
            if(oldFile==null)System.clearProperty("spk.local.accountFile");
            else System.setProperty("spk.local.accountFile",oldFile);
            if(oldJvm==null)
                System.clearProperty(LocalJvmAccountSessionLease.PROPERTY);
            else System.setProperty(LocalJvmAccountSessionLease.PROPERTY,
                oldJvm);
            if(oldCross==null)
                System.clearProperty(
                    LocalCrossJvmAccountSessionLock.PROPERTY);
            else System.setProperty(
                LocalCrossJvmAccountSessionLock.PROPERTY,oldCross);
            if(oldVeto==null)
                System.clearProperty(
                    LocalReviewIntentAdmissionVeto.PROPERTY);
            else System.setProperty(
                LocalReviewIntentAdmissionVeto.PROPERTY,oldVeto);
            try(Stream<Path> files=Files.walk(root)){
                for(Path path:files.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(path);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length==2&&"--child".equals(args[0]))
            child(args[1]);
        else{
            check(args.length==0,"unexpected args");
            run();
        }
    }
}
