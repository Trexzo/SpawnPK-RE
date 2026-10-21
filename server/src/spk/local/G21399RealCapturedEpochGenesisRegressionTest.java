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
import java.nio.file.StandardOpenOption;
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
 * R77 adds a real-World child-JVM hard-kill at forced genesis archive
 * before HEAD/intent. A fresh JVM must deny both loaders and TCP.
 * R78 binds OWNED to this same authentic World capture with an
 * internal nonce. OWNED remains nonadmitting permanent negative.
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
        // Claim has a distinct, deliberately default-OFF opt-in,
        // even when R76/R77 genesis experiments are explicitly on.
        System.clearProperty(
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_CLAIM_LAB_PROPERTY);
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
    private static void freshProbe(Path root,boolean killed)
        throws Exception{
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
        check(locked==!killed,
            killed?"killed World OS lock was not released":
                "stranded original World released OS lock");
        if(killed){
            check(!Files.exists(
                    account.resolveSibling(
                        account.getFileName().toString()+
                        ".g21385-ledger-head"),LinkOption.NOFOLLOW_LINKS),
                "pre-HEAD hard kill somehow published HEAD");
            boolean absent=false;
            try{LocalReusableAccountEpochLab.inspect(account);}
            catch(IOException invalid){
                absent=contains(invalid,
                    "R57_NO_LEDGER_HEAD_MANUAL_REVIEW");
            }
            check(absent,
                "orphan archive was treated as valid ledger genesis");
            System.out.println("R77_FRESH_JVM_PRE_HEAD_ORPHAN_DENIED"+
                " realTcpDenied=true"+
                " oldAccountDenied=true"+
                " originalOsLockReleasedAfterProcessKill=true"+
                " headAbsentNoGrant=true");
        }else{
            LocalReusableAccountEpochLab.Observation lab=
                LocalReusableAccountEpochLab.inspect(account);
            check(lab.sequence==1&&lab.epoch==2&&
                lab.phase==LocalReusableAccountEpochLab.Phase.OWNED&&
                lab.worldGeneration>0&&
                lab.ownerNonce.matches("r78_[0-9a-f]{32}")&&
                !lab.admissionAuthorized&&!lab.releaseAuthorized,
                "fresh inspection misread OWNED as authority");
            System.out.println("R78_FRESH_JVM_OWNED_PHASE_REMAINS_NEGATIVE");
            System.out.println("R76_FRESH_JVM_NEGATIVE_GENESIS_VETO_PASS"+
                " defaultOffRepositoryDenied=true"+
                " defaultOffLegacyDenied=true"+
                " genuineTcpWorldDenied=true"+
                " originalOsLockStillHeld=true"+
                " noPositiveAdmission=true");
        }
    }
    private static void probeInChild(Path root,boolean killed)throws Exception{
        Path log=root.resolve(killed?"killed-fresh.log":"fresh.log");
        ProcessBuilder b=new ProcessBuilder(
            javaExe(),"-cp",System.getProperty("java.class.path"),
            G21399RealCapturedEpochGenesisRegressionTest.class.getName(),
            killed?"--killed":"--fresh",root.toString());
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
            killed?"R77_FRESH_JVM_PRE_HEAD_ORPHAN_DENIED":
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
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_GENESIS_LAB_PROPERTY,
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_CLAIM_LAB_PROPERTY
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

                    boolean claimDefaultOff=false;
                    try{
                        LocalJvmAccountSessionLease
                            .claimCapturedEpochUnderOriginalLockForLab(
                                selected,owner,generation,
                                c.ticket.snapshot,c.ownerWitness);
                    }catch(IOException invalid){
                        claimDefaultOff=contains(invalid,
                            "R78_CAPTURED_EPOCH_CLAIM_LAB_DEFAULT_OFF");
                    }
                    check(claimDefaultOff,"R78 claim bypassed default off");
                    System.setProperty(
                        LocalJvmAccountSessionLease
                            .CAPTURED_EPOCH_CLAIM_LAB_PROPERTY,"true");
                    boolean forgedClaim=false;
                    try{
                        LocalJvmAccountSessionLease
                            .claimCapturedEpochUnderOriginalLockForLab(
                                selected,forged,generation,
                                c.ticket.snapshot,c.ownerWitness);
                    }catch(IOException invalid){
                        forgedClaim=contains(invalid,
                            "R78_CAPTURE_WITNESS_NOT_ORIGINAL_OWNER");
                    }
                    check(forgedClaim,"forged owner claimed original epoch");
                    boolean wrongCapture=false;
                    try{
                        LocalJvmAccountSessionLease
                            .claimCapturedEpochUnderOriginalLockForLab(
                                selected,owner,generation,
                                PlayerSnapshotCodec.capture(
                                    USER,new WorldPlayer()),c.ownerWitness);
                    }catch(IOException invalid){
                        wrongCapture=contains(invalid,
                            "R78_CAPTURE_NOT_LEDGER_GENESIS");
                    }
                    check(wrongCapture,
                        "unrelated captured content claimed prior genesis");
                    LocalReusableAccountEpochLab.Observation owned=
                        LocalJvmAccountSessionLease
                            .claimCapturedEpochUnderOriginalLockForLab(
                                selected,owner,generation,
                                c.ticket.snapshot,c.ownerWitness);
                    check(owned.sequence==1&&owned.epoch==2&&
                        owned.phase==LocalReusableAccountEpochLab.Phase.OWNED&&
                        owned.worldGeneration==generation&&
                        owned.ownerNonce.matches("r78_[0-9a-f]{32}")&&
                        owned.imageSha.equals(seeded.imageSha)&&
                        owned.imageLength==seeded.imageLength&&
                        !owned.admissionAuthorized&&!owned.releaseAuthorized,
                        "authentic World capture not bound to OWNED");
                    boolean staleDenied=false;
                    try{
                        LocalJvmAccountSessionLease
                            .claimCapturedEpochUnderOriginalLockForLab(
                                selected,owner,generation,
                                c.ticket.snapshot,c.ownerWitness);
                    }catch(IOException invalid){
                        staleDenied=contains(invalid,
                            "R78_GENESIS_NOT_CLOSED_OR_SEQUENCE_CHANGED");
                    }
                    check(staleDenied,
                        "replayed captured epoch claim replaced OWNED");
                    check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                        "negative owned ledger released real session owner");
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
            probeInChild(root,false);
            check(Arrays.equals(original,Files.readAllBytes(account)),
                "R78 OWNED state altered historical account bytes");
            world.close();
            check(world.closed(),"original World could not close");
            System.out.println("R78_REAL_WORLD_CAPTURED_OWNED_PHASE_PASS"+
                " sameOriginalWorldTokenAndLock=true"+
                " nonceGeneratedInternally=true"+
                " forgedWorldAndWrongCaptureDenied=true"+
                " capturedGenesisDigestAndEpochBound=true"+
                " duplicateClaimDenied=true"+
                " freshJvmOWNEDDoesNotGrantAdmission=true"+
                " originalAccountUnmodified=true"+
                " noPositiveFinalSaveOrRecovery=true");
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
    /** R77: a REAL TCP World dies with archive forced but HEAD absent. */
    private static void killHolder(Path root,Path ready)throws Exception{
        configure(root,true);
        FilePlayerRepository repo=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        World world=World.isolatedForTest(50L,repo);
        try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                .OneSession server=
                new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession(world)){
            try(Socket client=server.login(USER)){
                await(()->world.players().byName(USER)!=null,
                    "R77 real World owner registration");
                WorldPlayer owner=world.players().byName(USER);
                long generation=owner.generation();
                check(generation>0&&
                    LocalJvmAccountSessionLease.activeCountForTest()==1,
                    "R77 real World original lock not owned");
                world.submit(owner,generation,()->
                    owner.snapshotExtensions().replaceNamespace(
                        "r77_killed",
                        Collections.singletonMap("phase","POST_BOOT"))
                ).get(5,TimeUnit.SECONDS);
                WorldPlayerPersistence.CapturedSave capture=
                    capture(world,owner,generation);
                check("POST_BOOT".equals(
                    capture.ticket.snapshot.value(
                        "extension.r77_killed.phase")),
                    "R77 immutable actual World mutation absent");
                LocalJvmAccountSessionLease
                    .initializeCapturedEpochGenesisUnderOriginalLockForLab(
                        LocalAccountProfiles.accountFile(USER),
                        owner,generation,capture.ticket.snapshot,
                        capture.ownerWitness,stage->{
                            if(stage!=LocalReusableAccountEpochLab.Stage
                                    .ARCHIVED_HEAD_FORCED)
                                return;
                            Files.write(ready,new byte[]{1},
                                StandardOpenOption.CREATE_NEW);
                            System.out.println(
                                "R77_REAL_WORLD_GENESIS_ARCHIVE_FORCED");
                            System.out.flush();
                            try{Thread.sleep(120000L);}
                            catch(InterruptedException interrupted){
                                Thread.currentThread().interrupt();
                                throw new IOException(
                                    "R77_HOLDER_INTERRUPTED",interrupted);
                            }
                            throw new AssertionError(
                                "R77_HOLDER_MUST_BE_KILLED");
                        });
            }
        }finally{
            if(!world.closed())world.close();
        }
        throw new AssertionError("R77 holder returned unexpectedly");
    }

    private static void runKilledGenesis()throws Exception{
        Path root=Files.createTempDirectory("r77-killed-genesis-");
        String[] flags={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_GENESIS_LAB_PROPERTY,
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_CLAIM_LAB_PROPERTY
        };
        String[] before=new String[flags.length];
        for(int i=0;i<flags.length;i++)
            before[i]=System.getProperty(flags[i]);
        Process holder=null;
        try{
            configure(root,false);
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            repo.save(PlayerSnapshotCodec.capture(USER,new WorldPlayer()));
            Path account=LocalAccountProfiles.accountFile(USER);
            byte[] baseline=Files.readAllBytes(account);
            check(baseline.length>0,"pre-kill legacy baseline missing");
            Path ready=root.resolve("ready.flag");
            Path log=root.resolve("holder.log");
            ProcessBuilder builder=new ProcessBuilder(
                javaExe(),"-cp",System.getProperty("java.class.path"),
                G21399RealCapturedEpochGenesisRegressionTest.class.getName(),
                "--kill-holder",root.toString(),ready.toString());
            builder.redirectErrorStream(true);
            builder.redirectOutput(log.toFile());
            holder=builder.start();
            final Process running=holder;
            await(()->Files.exists(ready,LinkOption.NOFOLLOW_LINKS)||
                !running.isAlive(),"original World genesis archive cutpoint");
            String capturedLog=new String(Files.readAllBytes(log),
                StandardCharsets.UTF_8);
            check(Files.exists(ready,LinkOption.NOFOLLOW_LINKS)&&
                running.isAlive()&&capturedLog.contains(
                    "R77_REAL_WORLD_GENESIS_ARCHIVE_FORCED"),
                "holder missed forced pre-HEAD cutpoint: "+capturedLog);
            holder.destroyForcibly();
            check(holder.waitFor(10,TimeUnit.SECONDS),
                "hard-killed World process did not exit");
            check(holder.exitValue()!=0,"holder exited normally");
            check(Arrays.equals(baseline,Files.readAllBytes(account)),
                "pre-HEAD hard kill rewrote original account bytes");

            boolean negative=false;
            try{
                LocalReusableLabEvidenceNegativeGate
                    .requireNoLegacyCollision(account);
            }catch(IOException evidence){
                negative=contains(evidence,NEGATIVE);
            }
            check(negative,
                "orphan forced archive did not create negative evidence");
            // Independent JVM uses default-OFF legacy and actual TCP
            // and proves the killed holder's OS lock was released.
            probeInChild(root,true);
            check(Arrays.equals(baseline,Files.readAllBytes(account)),
                "fresh-JVM negative probes mutated old account bytes");
            System.out.println("R77_GENUINE_WORLD_GENESIS_PRE_HEAD_KILL_PASS"+
                " originalWorldOwnerCaptured=true"+
                " archiveForcedBeforeHead=true"+
                " originalProcessHardKilled=true"+
                " orphanArchivePermanentlyNegative=true"+
                " freshJvmTcpAndLegacyDenied=true"+
                " oldAccountBytesUnchanged=true"+
                " noPositiveRecoveryOrPowerLossClaim=true");
        }finally{
            if(holder!=null&&holder.isAlive()){
                holder.destroyForcibly();
                holder.waitFor(10,TimeUnit.SECONDS);
            }
            for(int i=0;i<flags.length;i++){
                if(before[i]==null)System.clearProperty(flags[i]);
                else System.setProperty(flags[i],before[i]);
            }
            try(Stream<Path> files=Files.walk(root)){
                for(Path path:files.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(path);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length==2&&("--fresh".equals(args[0])||
                            "--killed".equals(args[0]))){
            freshProbe(Paths.get(args[1]),"--killed".equals(args[0]));
            return;
        }
        if(args.length==3&&"--kill-holder".equals(args[0])){
            killHolder(Paths.get(args[1]),Paths.get(args[2]));
            return;
        }
        check(args.length==0,"unexpected args");
        exercise();
        runKilledGenesis();
    }
}
