package spk.local;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * R76: authenticated real TCP World and immutable G21.202 capture
 * initialize the detached R57 ledger genesis under the ORIGINAL held
 * G21.185 OS FileLock. This is PERMANENT NEGATIVE LAB evidence only.
 *
 * Real account bytes remain unchanged, original final save is vetoed,
 * the session lock is retained, and a fresh default-off JVM must deny
 * both repository hydration and actual TCP login. No durable positive
 * epoch, final-save receipt, replay, release or power-loss proof.
 */
public final class G21399RealCapturedEpochGenesisRegressionTest {
    private G21399RealCapturedEpochGenesisRegressionTest(){}
    private static final String USER="r76-real-epoch-genesis";
    private static final String NEGATIVE=
        "R63_REUSABLE_LAB_EVIDENCE_PRESENT_NO_LEGACY_ADMISSION";

    private static void check(boolean valid,String message){
        if(!valid)throw new AssertionError("R76_REAL_GENESIS "+message);
    }
    @FunctionalInterface private interface Condition{
        boolean ready()throws Exception;
    }
    private static void await(Condition condition,String context)
        throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(System.nanoTime()<deadline){
            if(condition.ready())return;
            Thread.sleep(10L);
        }
        check(condition.ready(),"timeout "+context);
    }
    private static boolean contains(Throwable t,String marker){
        for(Throwable x=t;x!=null;x=x.getCause())
            if(String.valueOf(x.getMessage()).contains(marker))return true;
        return false;
    }
    private static String javaExe(){
        return System.getProperty("java.home")+File.separator+"bin"+
            File.separator+
            (System.getProperty("os.name","").toLowerCase().contains("win")
                ?"java.exe":"java");
    }
    private static void configure(Path root,boolean enabled){
        System.setProperty("spk.local.accountFile",
            root.resolve("opensrc.properties").toString());
        String[] flags={
            LocalJvmAccountSessionLease.PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_GENESIS_LAB_PROPERTY
        };
        for(String flag:flags){
            if(enabled)System.setProperty(flag,"true");
            else System.clearProperty(flag);
        }
    }
    private static int tcpResponse(int port)throws Exception{
        try(Socket socket=new Socket(
                InetAddress.getByName("127.0.0.1"),port)){
            socket.setSoTimeout(5000);
            InputStream in=socket.getInputStream();
            OutputStream out=socket.getOutputStream();
            out.write(14);out.write(7);out.flush();
            byte[] pre=Binary.readExactly(in,9);
            check((pre[8]&255)==0,"prelogin response invalid");
            long seed=Binary.i64(Binary.readExactly(in,8),0);
            check(seed==LocalLoginTransport.SERVER_SEED,
                "server seed changed");
            int[] seeds={0x01020304,0x11223344,
                (int)(seed>>>32),(int)seed};
            byte[] frame=
                G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .loginFrame(seeds,USER);
            out.write(16);out.write(frame.length);
            out.write(frame);out.flush();
            return in.read();
        }
    }
    private static void freshProbe(Path root)throws Exception{
        configure(root,false);
        Path account=LocalAccountProfiles.accountFile(USER);
        FilePlayerRepository repo=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        boolean denied=false;
        try{repo.loadForWorldSession(USER);}
        catch(IOException invalid){denied=contains(invalid,NEGATIVE);}
        check(denied,"fresh JVM repository accepted LAB ledger");

        WorldPlayer legacy=new WorldPlayer();
        denied=false;
        try{
            LocalAccountProfiles.load(USER,legacy.bank(),
                legacy.equipment(),legacy.movement(),
                legacy.petState(),legacy.playerState());
        }catch(IOException invalid){denied=contains(invalid,NEGATIVE);}
        check(denied,"fresh JVM legacy load ignored negative genesis");

        World world=World.isolatedForTest(50L,repo);
        try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                .OneSession server=
                new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession(world)){
            int response=tcpResponse(server.listener.getLocalPort());
            check(response!=2&&world.players().size()==0,
                "fresh authenticated TCP World accepted LAB ledger");
            server.waitCompleted();
        }finally{
            if(!world.closed())world.close();
        }
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,
            "true");
        System.setProperty(
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,"true");
        boolean locked=false;
        try(LocalCrossJvmAccountSessionLock.Handle unexpected=
                LocalCrossJvmAccountSessionLock.acquire(account)){
            // Even after failed final save, the original parent's OS
            // lock cannot be acquired by this cooperating fresh JVM.
        }catch(IOException invalid){
            locked=contains(invalid,"G21.185 ACCOUNT_SESSION_LOCK_BUSY");
        }
        check(locked,"stranded original World released OS lock");
        LocalReusableAccountEpochLab.Observation lab=
            LocalReusableAccountEpochLab.inspect(account);
        check(lab.sequence==0&&
            lab.phase==LocalReusableAccountEpochLab.Phase.CLOSED&&
            !lab.admissionAuthorized&&!lab.releaseAuthorized,
            "fresh inspection misread LAB as positive admission");
        System.out.println("R76_FRESH_JVM_NEGATIVE_GENESIS_VETO_PASS"+
            " defaultOffRepositoryDenied=true"+
            " defaultOffLegacyDenied=true"+
            " genuineTcpWorldDenied=true"+
            " originalOsLockStillHeld=true"+
            " noPositiveAdmission=true");
    }
    private static void probeInChild(Path root)throws Exception{
        Path log=root.resolve("fresh.log");
        ProcessBuilder b=new ProcessBuilder(
            javaExe(),"-cp",System.getProperty("java.class.path"),
            G21399RealCapturedEpochGenesisRegressionTest.class.getName(),
            "--fresh",root.toString());
        b.redirectErrorStream(true);
        b.redirectOutput(log.toFile());
        Process p=b.start();
        if(!p.waitFor(25,TimeUnit.SECONDS)){
            p.destroyForcibly();
            p.waitFor(10,TimeUnit.SECONDS);
            throw new AssertionError("fresh JVM timed out");
        }
        String output=new String(Files.readAllBytes(log),
            StandardCharsets.UTF_8);
        check(p.exitValue()==0&&output.contains(
            "R76_FRESH_JVM_NEGATIVE_GENESIS_VETO_PASS"),
            "fresh JVM did not veto ledger: "+output);
    }
    private static WorldPlayerPersistence.CapturedSave capture(
        World world,WorldPlayer owner,long generation
    )throws Exception{
        AtomicReference<WorldPlayerPersistence.CapturedSave> result=
            new AtomicReference<>();
        world.submit(owner,generation,()->
            result.set(world.persistence().captureDeferredSave(
                USER,owner,generation,0,"[r76] ","R76_LAB_GENESIS"))
        ).get(5,TimeUnit.SECONDS);
        WorldPlayerPersistence.CapturedSave captured=result.get();
        check(captured!=null&&captured.owner==owner&&
            captured.expectedGeneration==generation&&
            captured.ownerWitness!=null,
            "not genuine immutable G21.202 World capture");
        return captured;
    }
    private static void exercise()throws Exception{
        Path root=Files.createTempDirectory("r76-real-genesis-");
        String[] names={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_GENESIS_LAB_PROPERTY
        };
        String[] previous=new String[names.length];
        for(int i=0;i<names.length;i++)
            previous[i]=System.getProperty(names[i]);
        configure(root,false);
        World world=null;
        try{
            FilePlayerRepository repository=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            repository.save(PlayerSnapshotCodec.capture(
                USER,new WorldPlayer()));
            Path account=LocalAccountProfiles.accountFile(USER);
            byte[] original=Files.readAllBytes(account);
            check(original.length>0,"seed baseline empty");

            for(int i=1;i<4;i++)
                System.setProperty(names[i],"true");
            world=World.isolatedForTest(50L,repository);
            World live=world;
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession server=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(world)){
                try(Socket socket=server.login(USER)){
                    await(()->live.players().byName(USER)!=null,
                        "authenticated original World login");
                    WorldPlayer owner=world.players().byName(USER);
                    long generation=owner.generation();
                    check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                        "no original World session lease");

                    world.submit(owner,generation,()->
                        owner.snapshotExtensions().replaceNamespace(
                            "r76_genuine",
                            Collections.singletonMap("phase","CAPTURED"))
                    ).get(5,TimeUnit.SECONDS);
                    WorldPlayerPersistence.CapturedSave c=
                        capture(world,owner,generation);
                    Path selected=LocalAccountProfiles.accountFile(USER);
                    boolean disabled=false;
                    try{
                        LocalJvmAccountSessionLease
                            .initializeCapturedEpochGenesisUnderOriginalLockForLab(
                                selected,owner,generation,
                                c.ticket.snapshot,c.ownerWitness);
                    }catch(IOException reject){
                        disabled=contains(reject,
                            "R76_CAPTURED_EPOCH_GENESIS_LAB_DEFAULT_OFF");
                    }
                    check(disabled,"default-off created epoch genesis");

                    System.setProperty(names[4],"true");
                    WorldPlayer forged=new WorldPlayer();
                    forged.markRegistered(USER);
                    boolean forgedDenied=false;
                    try{
                        LocalJvmAccountSessionLease
                            .initializeCapturedEpochGenesisUnderOriginalLockForLab(
                                selected,forged,generation,
                                c.ticket.snapshot,c.ownerWitness);
                    }catch(IOException invalid){
                        forgedDenied=contains(invalid,
                            "R76_CAPTURE_WITNESS_NOT_ORIGINAL_OWNER");
                    }
                    check(forgedDenied,"forged owner initialized epoch");

                    LocalReusableAccountEpochLab.Observation seeded=
                        LocalJvmAccountSessionLease
                            .initializeCapturedEpochGenesisUnderOriginalLockForLab(
                                selected,owner,generation,
                                c.ticket.snapshot,c.ownerWitness);
                    String expected=
                        LocalCapturedWorldSnapshotFingerprintLab.fingerprint(
                            selected,c.ticket.snapshot,generation)
                            .canonicalSha256;
                    check(seeded.sequence==0&&seeded.epoch==1&&
                        seeded.phase==LocalReusableAccountEpochLab.Phase.CLOSED&&
                        expected.equals(seeded.imageSha)&&
                        !seeded.admissionAuthorized&&
                        !seeded.releaseAuthorized,
                        "LAB genesis not identical to real capture");
                    check(Arrays.equals(original,Files.readAllBytes(account)),
                        "LAB genesis replaced legacy account bytes");
                    boolean repeated=false;
                    try{
                        LocalJvmAccountSessionLease
                            .initializeCapturedEpochGenesisUnderOriginalLockForLab(
                                selected,owner,generation,
                                c.ticket.snapshot,c.ownerWitness);
                    }catch(IOException invalid){
                        repeated=contains(invalid,NEGATIVE)||
                            contains(invalid,"R57_LEDGER_ALREADY_INITIALIZED");
                    }
                    check(repeated,"duplicate LAB genesis unexpectedly passed");
                    check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                        "genesis erroneously released real session owner");
                }
                await(()->live.players().size()==0,
                    "original World unregister");
                server.waitCompleted();
            }
            check(world.persistence().failedCount()>0,
                "negative genesis did not veto original final write");
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "failed final incorrectly released session token");
            check(Arrays.equals(original,Files.readAllBytes(account)),
                "failed final replaced historical legacy bytes");
            probeInChild(root);
            world.close();
            check(world.closed(),"original World could not close");
            System.out.println("R76_GENUINE_CAPTURE_BORROWED_EPOCH_GENESIS_PASS"+
                " authenticatedWorldCapture=true"+
                " genesisUsesOriginalHeldOsLock=true"+
                " persistedCanonicalDigestMatchesCapture=true"+
                " forgedOwnerAndDefaultOffDenied=true"+
                " oldRealAccountBytesUnchanged=true"+
                " failedFinalCannotReleaseLease=true"+
                " separateFreshJvmTcpDenied=true"+
                " noPositiveReceiptOrRecovery=true"+
                " noPowerLossClaim=true");
        }finally{
            if(world!=null&&!world.closed())world.close();
            for(int i=0;i<names.length;i++){
                if(previous[i]==null)System.clearProperty(names[i]);
                else System.setProperty(names[i],previous[i]);
            }
            try(Stream<Path> files=Files.walk(root)){
                for(Path path:files.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(path);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==2&&"--fresh".equals(args[0])){
            freshProbe(Paths.get(args[1]));
            return;
        }
        check(args.length==0,"unexpected args");
        exercise();
    }
}
