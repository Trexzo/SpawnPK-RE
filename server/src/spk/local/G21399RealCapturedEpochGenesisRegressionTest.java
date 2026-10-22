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
 * R79 checks one original-token transition to FINAL_PENDING without
 * an account postimage, final receipt, release or recovery authority.
 * R80 kills genuine World processes at PREPARE archive and HEAD-dir-
 * forced boundaries, verifying both remain negative across restart.
 * R81 extends the same authentic World matrix to after HEAD move but
 * before directory force and after full receipt force: even a fully
 * complete LAB FINAL_PENDING is negative, not positive admission.
 * R82: a later genuine World mutation is captured as the detached
 * CLOSED LAB image; this never writes/replays the real account or
 * grants final ACK, release, or subsequent login.
 * R84 covers all seven detached settlement force stages and checks
 * the exact orphan archive/intent/temp/HEAD/receipt inventory.
 * R85 binds all LAB operations to the exact World-captured immutable
 * PlayerSnapshot object, forbidding swapped same-account snapshots.
 * R86 rejects even a genuine same-session capture if its World
 * capture began before R79 FINAL_PENDING was completed.
 * R87 refuses manufactured package-level witnesses with no original
 * single-use captured-start ticket, even if snapshot identity matches.
 * R88 requires the genuine World execution context at ticket mint
 * and consumption; off-thread callers may not fabricate capture order.
 * R89 requires a genuine deferred FINAL-disconnect World capture for
 * the detached LAB settlement, not an ordinary deferred/checkpoint.
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
        System.clearProperty(
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_PREPARE_LAB_PROPERTY);
        System.clearProperty(
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_SETTLE_LAB_PROPERTY);
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
        freshProbe(root,killed,false);
    }
    private static void freshProbe(
        Path root,boolean killed,boolean prepareKilled
    )throws Exception{
        freshProbe(root,killed,prepareKilled,false);
    }
    private static void freshProbe(
        Path root,boolean killed,boolean prepareKilled,
        boolean completeReceipt
    )throws Exception{
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
        if(prepareKilled){
            if(completeReceipt){
                // A fully receipt-complete LAB history is still
                // intentionally NEGATIVE, not positive final-save.
                LocalReusableAccountEpochLab.Observation complete=
                    LocalReusableAccountEpochLab.inspect(account);
                check(complete.sequence==2&&complete.epoch==2&&
                    complete.phase==LocalReusableAccountEpochLab
                        .Phase.FINAL_PENDING&&
                    complete.worldGeneration>0&&
                    complete.ownerNonce.matches("r78_[0-9a-f]{32}")&&
                    !complete.admissionAuthorized&&
                    !complete.releaseAuthorized,
                    "completed LAB receipt promoted to live authority");
                System.out.println(
                    "R81_FRESH_JVM_COMPLETE_LAB_RECEIPT_STILL_DENIED"+
                    " exactLedgerInspect=true"+
                    " realTcpDenied=true"+
                    " noOriginalOsLockAfterKill=true"+
                    " noFinalAckOrRecovery=true");
            }else{
                // Old OWNED HEAD may remain, or the new FINAL_PENDING
                // HEAD may have moved; no matching receipt chain.
                boolean incomplete=false;
                try{LocalReusableAccountEpochLab.inspect(account);}
                catch(IOException invalidLedger){
                    incomplete=contains(invalidLedger,"MANUAL_REVIEW")||
                        contains(invalidLedger,"NEGATIVE");
                }
                check(incomplete,
                    "interrupted prepare was a valid LAB HEAD");
                System.out.println("R80_FRESH_JVM_PREPARE_INCOMPLETE_VETO"+
                    " realTcpDenied=true"+
                    " originalOsLockReleasedAfterKill=true"+
                    " incompleteLedgerNotAccepted=true");
            }
        }else if(killed){
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
            check(lab.sequence==3&&lab.epoch==2&&
                lab.phase==LocalReusableAccountEpochLab.Phase.CLOSED&&
                lab.worldGeneration==0&&
                "none".equals(lab.ownerNonce)&&
                lab.imageSequence==3&&
                lab.imageSha.matches("[0-9a-f]{64}")&&
                !lab.admissionAuthorized&&!lab.releaseAuthorized,
                "fresh inspection misread completed CLOSED as authority");
            System.out.println("R82_FRESH_JVM_CLOSED_LAB_STILL_NEGATIVE");
            System.out.println("R76_FRESH_JVM_NEGATIVE_GENESIS_VETO_PASS"+
                " defaultOffRepositoryDenied=true"+
                " defaultOffLegacyDenied=true"+
                " genuineTcpWorldDenied=true"+
                " originalOsLockStillHeld=true"+
                " noPositiveAdmission=true");
        }
    }
    private static void probeInChild(Path root,boolean killed)
        throws Exception{
        probeInChild(root,killed,false);
    }
    private static void probeInChild(
        Path root,boolean killed,boolean prepareKilled
    )throws Exception{
        probeInChild(root,killed,prepareKilled,false);
    }
    private static void probeInChild(
        Path root,boolean killed,boolean prepareKilled,
        boolean completeReceipt
    )throws Exception{
        Path log=root.resolve(completeReceipt?"receipt-fresh.log":
            prepareKilled?"prepare-fresh.log":
                killed?"killed-fresh.log":"fresh.log");
        ProcessBuilder b=new ProcessBuilder(
            javaExe(),"-cp",System.getProperty("java.class.path"),
            G21399RealCapturedEpochGenesisRegressionTest.class.getName(),
            completeReceipt?"--prepare-receipt-complete":
                prepareKilled?"--prepare-killed":
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
            completeReceipt?
                "R81_FRESH_JVM_COMPLETE_LAB_RECEIPT_STILL_DENIED":
                prepareKilled?"R80_FRESH_JVM_PREPARE_INCOMPLETE_VETO":
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
    /** R89: genuine World-thread final-disconnect SNAPSHOT CAPTURE,
     * never a submitted final write, ACK, retirement or recovery. */
    private static WorldPlayerPersistence.CapturedSave captureFinal(
        World world,WorldPlayer owner,long generation
    )throws Exception{
        AtomicReference<WorldPlayerPersistence.CapturedSave> result=
            new AtomicReference<>();
        world.submit(owner,generation,()->
            result.set(world.persistence().captureDeferredFinalSave(
                USER,owner,generation,0,"[r89] ","R89_LAB_FINAL_CAPTURE"))
        ).get(5,TimeUnit.SECONDS);
        WorldPlayerPersistence.CapturedSave captured=result.get();
        check(captured!=null&&captured.owner==owner&&
            captured.expectedGeneration==generation&&
            captured.finalDisconnect&&captured.ownerWitness!=null,
            "R89 not a genuine deferred final-disconnect World capture");
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
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_CLAIM_LAB_PROPERTY,
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_PREPARE_LAB_PROPERTY,
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_SETTLE_LAB_PROPERTY
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

                    boolean syntheticGenesisDenied=false;
                    try{
                        LocalJvmAccountSessionLease
                            .initializeCapturedEpochGenesisUnderOriginalLockForLab(
                                selected,owner,generation,
                                PlayerSnapshotCodec.capture(
                                    USER,new WorldPlayer()),c.ownerWitness);
                    }catch(IOException invalid){
                        syntheticGenesisDenied=contains(invalid,
                            "R85_CAPTURE_SNAPSHOT_IDENTITY_MISMATCH");
                    }
                    check(syntheticGenesisDenied,
                        "synthetic same-account image borrowed true witness");

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
                            "R85_CAPTURE_SNAPSHOT_IDENTITY_MISMATCH");
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

                    boolean prepareDefaultOff=false;
                    try{
                        LocalJvmAccountSessionLease
                            .prepareCapturedEpochUnderOriginalLockForLab(
                                selected,owner,generation,
                                c.ticket.snapshot,c.ownerWitness);
                    }catch(IOException invalid){
                        prepareDefaultOff=contains(invalid,
                            "R79_CAPTURED_EPOCH_PREPARE_LAB_DEFAULT_OFF");
                    }
                    check(prepareDefaultOff,
                        "R79 final prepare bypassed default OFF");
                    System.setProperty(
                        LocalJvmAccountSessionLease
                            .CAPTURED_EPOCH_PREPARE_LAB_PROPERTY,"true");
                    boolean forgedPrepare=false;
                    try{
                        LocalJvmAccountSessionLease
                            .prepareCapturedEpochUnderOriginalLockForLab(
                                selected,forged,generation,
                                c.ticket.snapshot,c.ownerWitness);
                    }catch(IOException invalid){
                        forgedPrepare=contains(invalid,
                            "R79_CAPTURE_WITNESS_NOT_ORIGINAL_OWNER");
                    }
                    check(forgedPrepare,
                        "forged World owner prepared a LAB final");
                    boolean wrongPrepare=false;
                    try{
                        LocalJvmAccountSessionLease
                            .prepareCapturedEpochUnderOriginalLockForLab(
                                selected,owner,generation,
                                PlayerSnapshotCodec.capture(
                                    USER,new WorldPlayer()),c.ownerWitness);
                    }catch(IOException invalid){
                        wrongPrepare=contains(invalid,
                            "R85_CAPTURE_SNAPSHOT_IDENTITY_MISMATCH");
                    }
                    check(wrongPrepare,
                        "unrelated snapshot prepared original LAB final");
                    boolean offThreadMintDenied=false;
                    try{
                        LocalJvmAccountSessionLease
                            .markWorldSnapshotCaptureStartForLab(
                                world,selected,USER,owner,generation);
                    }catch(IllegalStateException invalid){
                        offThreadMintDenied=contains(invalid,
                            "R88_CAPTURE_START_NOT_ACTUAL_WORLD_EXECUTION_CONTEXT");
                    }
                    check(offThreadMintDenied,
                        "R88 off-World-thread caller minted capture ticket");
                    // R87: mint one original-Token ticket on the real
                    // World execution thread, bind it once to its
                    // immutable capture, and refuse both cross-path
                    // consumption and a second same-path binding.
                    world.submit(owner,generation,()->{
                        synchronized(owner.mutationLock()){
                            LocalJvmAccountSessionLease.WorldCaptureStartForLab
                                started=LocalJvmAccountSessionLease
                                    .markWorldSnapshotCaptureStartForLab(
                                        live,selected,USER,owner,generation);
                            check(started!=null,
                                "R87 original World capture mint missing");
                            PlayerSnapshot sameWorldSnapshot=
                                PlayerSnapshotCodec.capture(USER,owner);
                            boolean foreignPathDenied=false;
                            try{
                                LocalJvmAccountSessionLease
                                    .captureSaveWitness(
                                        selected.resolveSibling(
                                            "r87-foreign.properties"),
                                        USER,owner,generation,
                                        sameWorldSnapshot,started);
                            }catch(IllegalStateException invalid){
                                foreignPathDenied=contains(invalid,
                                    "R87_CAPTURE_START_NOT_ORIGINAL_WORLD_TOKEN");
                            }
                            check(foreignPathDenied,
                                "R87 original ticket rebound other account");
                            LocalJvmAccountSessionLease.SaveCaptureWitness
                                first=LocalJvmAccountSessionLease
                                    .captureSaveWitness(
                                        selected,USER,owner,generation,
                                        sameWorldSnapshot,started);
                            check(first!=null,
                                "R87 first immutable World binding missing");
                            boolean duplicateDenied=false;
                            try{
                                LocalJvmAccountSessionLease
                                    .captureSaveWitness(
                                        selected,USER,owner,generation,
                                        sameWorldSnapshot,started);
                            }catch(IllegalStateException invalid){
                                duplicateDenied=contains(invalid,
                                    "R87_CAPTURE_START_ALREADY_CONSUMED");
                            }
                            check(duplicateDenied,
                                "R87 captured-start token was replayed");
                        }
                    }).get(5,TimeUnit.SECONDS);
                    // R88: a legitimate World-minted ticket cannot
                    // later be consumed by an arbitrary off-World
                    // caller, even with the correct owner and image.
                    AtomicReference<
                        LocalJvmAccountSessionLease.WorldCaptureStartForLab>
                        unconsumed=new AtomicReference<>();
                    AtomicReference<PlayerSnapshot> exactForTicket=
                        new AtomicReference<>();
                    world.submit(owner,generation,()->{
                        synchronized(owner.mutationLock()){
                            unconsumed.set(LocalJvmAccountSessionLease
                                .markWorldSnapshotCaptureStartForLab(
                                    live,selected,USER,owner,generation));
                            exactForTicket.set(
                                PlayerSnapshotCodec.capture(USER,owner));
                        }
                    }).get(5,TimeUnit.SECONDS);
                    check(unconsumed.get()!=null&&
                        exactForTicket.get()!=null,
                        "R88 actual World-start ticket or image missing");
                    boolean offThreadConsumeDenied=false;
                    try{
                        LocalJvmAccountSessionLease.captureSaveWitness(
                            selected,USER,owner,generation,
                            exactForTicket.get(),unconsumed.get());
                    }catch(IllegalStateException invalid){
                        offThreadConsumeDenied=contains(invalid,
                            "R88_CAPTURE_WITNESS_NOT_ACTUAL_WORLD_CONTEXT");
                    }
                    check(offThreadConsumeDenied,
                        "R88 off-thread caller consumed World ticket");
                    world.submit(owner,generation,()->{
                        LocalJvmAccountSessionLease.SaveCaptureWitness
                            minted=LocalJvmAccountSessionLease
                                .captureSaveWitness(selected,USER,
                                    owner,generation,
                                    exactForTicket.get(),unconsumed.get());
                        check(minted!=null,
                            "R88 legitimate World ticket not consumable");
                    }).get(5,TimeUnit.SECONDS);
                    // R86: a fully genuine capture of DISTINCT World
                    // state that starts BEFORE FINAL_PENDING must
                    // never count as a post-preparation final image.
                    world.submit(owner,generation,()->
                        owner.snapshotExtensions().replaceNamespace(
                            "r86_early",
                            Collections.singletonMap(
                                "phase","BEFORE_PREPARATION"))
                    ).get(5,TimeUnit.SECONDS);
                    WorldPlayerPersistence.CapturedSave preprepared=
                        captureFinal(world,owner,generation);
                    check("BEFORE_PREPARATION".equals(
                        preprepared.ticket.snapshot.value(
                            "extension.r86_early.phase")),
                        "R86 pre-prepare capture missing");
                    LocalReusableAccountEpochLab.Observation pending=
                        LocalJvmAccountSessionLease
                            .prepareCapturedEpochUnderOriginalLockForLab(
                                selected,owner,generation,
                                c.ticket.snapshot,c.ownerWitness);
                    check(pending.sequence==2&&pending.epoch==owned.epoch&&
                        pending.phase==LocalReusableAccountEpochLab
                            .Phase.FINAL_PENDING&&
                        pending.worldGeneration==generation&&
                        pending.ownerNonce.equals(owned.ownerNonce)&&
                        pending.imageSha.equals(owned.imageSha)&&
                        !pending.admissionAuthorized&&!pending.releaseAuthorized,
                        "genuine owner final prepare broke nonce/epoch");
                    boolean duplicatePrepareDenied=false;
                    try{
                        LocalJvmAccountSessionLease
                            .prepareCapturedEpochUnderOriginalLockForLab(
                                selected,owner,generation,
                                c.ticket.snapshot,c.ownerWitness);
                    }catch(IOException invalid){
                        duplicatePrepareDenied=contains(invalid,
                            "R79_OWNED_DISK_WITNESS_NOT_ORIGINAL_TOKEN");
                    }
                    check(duplicatePrepareDenied,
                        "replayed final prepare advanced LAB sequence");
                    check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                        "negative final-pending ledger released real lease");

                    // A second genuinely World-thread-captured image,
                    // not an arbitrary legacy file nor synthetic
                    // caller-supplied final snapshot.
                    world.submit(owner,generation,()->
                        owner.snapshotExtensions().replaceNamespace(
                            "r82_final",
                            Collections.singletonMap("phase","AFTER_PENDING"))
                    ).get(5,TimeUnit.SECONDS);
                    // R89 first proves an ordinary genuine post-
                    // prepare World snapshot is never a final capture.
                    WorldPlayerPersistence.CapturedSave ordinary=
                        capture(world,owner,generation);
                    check(!ordinary.finalDisconnect,
                        "ordinary World capture mislabeled final");
                    WorldPlayerPersistence.CapturedSave later=
                        captureFinal(world,owner,generation);
                    check("AFTER_PENDING".equals(
                        later.ticket.snapshot.value(
                            "extension.r82_final.phase")),
                        "later World postimage was not genuinely captured");
                    LocalCapturedWorldSnapshotFingerprintLab.Observation
                        postimage=LocalCapturedWorldSnapshotFingerprintLab
                            .fingerprint(selected,later.ticket.snapshot,
                                generation);
                    check(!postimage.canonicalSha256.equals(
                        pending.imageSha),
                        "later World state did not change snapshot");

                    boolean settlementDefaultOff=false;
                    try{
                        LocalJvmAccountSessionLease
                            .settleCapturedEpochUnderOriginalLockForLab(
                                selected,owner,generation,
                                later.ticket.snapshot,later.ownerWitness);
                    }catch(IOException rejected){
                        settlementDefaultOff=contains(rejected,
                            "R82_CAPTURED_EPOCH_SETTLEMENT_LAB_DEFAULT_OFF");
                    }
                    check(settlementDefaultOff,
                        "R82 detached settlement bypassed default OFF");
                    System.setProperty(
                        LocalJvmAccountSessionLease
                            .CAPTURED_EPOCH_SETTLE_LAB_PROPERTY,"true");
                    boolean ordinaryDenied=false;
                    try{
                        LocalJvmAccountSessionLease
                            .settleCapturedEpochUnderOriginalLockForLab(
                                selected,owner,generation,
                                ordinary.ticket.snapshot,
                                ordinary.ownerWitness);
                    }catch(IOException denied){
                        ordinaryDenied=contains(denied,
                            "R89_SETTLEMENT_REQUIRES_FINAL_DISCONNECT_CAPTURE");
                    }
                    check(ordinaryDenied,
                        "ordinary World capture settled final LAB image");
                    boolean forgedSettle=false;
                    try{
                        LocalJvmAccountSessionLease
                            .settleCapturedEpochUnderOriginalLockForLab(
                                selected,forged,generation,
                                later.ticket.snapshot,later.ownerWitness);
                    }catch(IOException denied){
                        forgedSettle=contains(denied,
                            "R82_CAPTURE_WITNESS_NOT_ORIGINAL_OWNER");
                    }
                    check(forgedSettle,
                        "forged World owner settled original LAB epoch");
                    boolean syntheticSettleDenied=false;
                    try{
                        LocalJvmAccountSessionLease
                            .settleCapturedEpochUnderOriginalLockForLab(
                                selected,owner,generation,
                                PlayerSnapshotCodec.capture(
                                    USER,new WorldPlayer()),
                                later.ownerWitness);
                    }catch(IOException invalid){
                        syntheticSettleDenied=contains(invalid,
                            "R85_CAPTURE_SNAPSHOT_IDENTITY_MISMATCH");
                    }
                    check(syntheticSettleDenied,
                        "synthetic postimage used real World owner witness");
                    boolean swappedWitnessDenied=false;
                    try{
                        LocalJvmAccountSessionLease
                            .settleCapturedEpochUnderOriginalLockForLab(
                                selected,owner,generation,
                                later.ticket.snapshot,c.ownerWitness);
                    }catch(IOException invalid){
                        swappedWitnessDenied=contains(invalid,
                            "R85_CAPTURE_SNAPSHOT_IDENTITY_MISMATCH");
                    }
                    check(swappedWitnessDenied,
                        "later World image used older same-owner witness");
                    // R87: this package-level compatibility factory
                    // binds the REAL late snapshot/owner/token but
                    // has no minted capture-start ticket. It must
                    // not manufacture positive ordering from them.
                    LocalJvmAccountSessionLease.SaveCaptureWitness
                        untrustedOrdinalWitness=
                            LocalJvmAccountSessionLease.captureSaveWitness(
                                selected,USER,owner,generation,
                                later.ticket.snapshot);
                    boolean unmintedDenied=false;
                    try{
                        LocalJvmAccountSessionLease
                            .settleCapturedEpochUnderOriginalLockForLab(
                                selected,owner,generation,
                                later.ticket.snapshot,
                                untrustedOrdinalWitness);
                    }catch(IOException invalid){
                        unmintedDenied=contains(invalid,
                            "R86_FINAL_CAPTURE_NOT_STARTED_AFTER_PREPARE");
                    }
                    check(unmintedDenied,
                        "package witness fabricated R87 capture ordering");
                    check(!LocalCapturedWorldSnapshotFingerprintLab
                            .fingerprint(selected,
                                preprepared.ticket.snapshot,generation)
                            .canonicalSha256.equals(pending.imageSha),
                        "preparation ordering test lacks distinct content");
                    boolean prepreparedDenied=false;
                    try{
                        LocalJvmAccountSessionLease
                            .settleCapturedEpochUnderOriginalLockForLab(
                                selected,owner,generation,
                                preprepared.ticket.snapshot,
                                preprepared.ownerWitness);
                    }catch(IOException invalid){
                        prepreparedDenied=contains(invalid,
                            "R86_FINAL_CAPTURE_NOT_STARTED_AFTER_PREPARE");
                    }
                    check(prepreparedDenied,
                        "R86 genuine pre-preparation image wrongly settled");
                    boolean replayOldImage=false;
                    try{
                        LocalJvmAccountSessionLease
                            .settleCapturedEpochUnderOriginalLockForLab(
                                selected,owner,generation,
                                c.ticket.snapshot,c.ownerWitness);
                    }catch(IOException invalid){
                        // The old real World capture is now rejected
                        // even earlier, by the R86 sequencing fence.
                        replayOldImage=contains(invalid,
                            "R86_FINAL_CAPTURE_NOT_STARTED_AFTER_PREPARE");
                    }
                    check(replayOldImage,
                        "preparation image replay counted as postimage");
                    LocalReusableAccountEpochLab.Observation settled=
                        LocalJvmAccountSessionLease
                            .settleCapturedEpochUnderOriginalLockForLab(
                                selected,owner,generation,
                                later.ticket.snapshot,later.ownerWitness);
                    check(settled.sequence==3&&settled.epoch==2&&
                        settled.phase==LocalReusableAccountEpochLab.Phase.CLOSED&&
                        settled.worldGeneration==0&&
                        "none".equals(settled.ownerNonce)&&
                        settled.imageSequence==3&&
                        settled.imageSha.equals(postimage.canonicalSha256)&&
                        settled.imageLength==postimage.canonicalBytes&&
                        !settled.admissionAuthorized&&!settled.releaseAuthorized,
                        "real World postimage mismatch or unwanted authority");
                    boolean replaySettlement=false;
                    try{
                        LocalJvmAccountSessionLease
                            .settleCapturedEpochUnderOriginalLockForLab(
                                selected,owner,generation,
                                later.ticket.snapshot,later.ownerWitness);
                    }catch(IOException denied){
                        replaySettlement=contains(denied,
                            "R82_PENDING_DISK_NOT_ORIGINAL_TOKEN");
                    }
                    check(replaySettlement,
                        "LAB settlement replay rewrote completed head");
                    check(Arrays.equals(original,Files.readAllBytes(account)),
                        "LAB CLOSED image wrote old legacy account bytes");
                    check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                        "LAB settlement prematurely released original lease");
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
            System.out.println("R89_FINAL_DISCONNECT_CAPTURE_CLASS_PASS"+
                " ordinaryPostPrepareWorldCaptureDenied=true"+
                " realDeferredFinalCaptureAcceptedLabOnly=true"+
                " distinctOriginalTokenAndImage=true"+
                " noFinalSaveSubmittedOrACK=true"+
                " noReleaseOrAdmission=true");
            System.out.println("R88_WORLD_EXECUTION_CAPTURE_TICKET_PASS"+
                " offWorldThreadMintDenied=true"+
                " offWorldThreadConsumeDenied=true"+
                " genuineWorldContextMintAndConsume=true"+
                " noActualSaveOrGrant=true");
            System.out.println("R87_OPAQUE_SINGLE_USE_CAPTURE_PROVENANCE_PASS"+
                " callerRawOrdinalNoLongerAccepted=true"+
                " unmintedExactWorldSnapshotDenied=true"+
                " mintedOriginalWorldSnapshotSettledLabOnly=true"+
                " legacyBytesUnchanged=true"+
                " noAdmissionOrRelease=true");
            System.out.println("R86_POST_PREPARATION_CAPTURE_ORDER_PASS"+
                " exactWorldCaptureStartOrdinal=true"+
                " distinctPrePrepareWorldImageDenied=true"+
                " genuinePostPrepareWorldImageAcceptedLabOnly=true"+
                " noLiveFinalACKOrAdmit=true");
            System.out.println("R85_EXACT_WORLD_SNAPSHOT_WITNESS_PASS"+
                " genesisSameAccountSyntheticDenied=true"+
                " claimSameAccountSyntheticDenied=true"+
                " prepareSameAccountSyntheticDenied=true"+
                " settlementSameAccountSyntheticDenied=true"+
                " swappedOriginalWorldWitnessDenied=true"+
                " actualDistinctWorldCaptureSettlesLab=true"+
                " noLiveAdmitOrFinalReceipt=true");
            System.out.println("R82_REAL_WORLD_CAPTURED_DETACHED_SETTLEMENT_PASS"+
                " exactLaterWorldCapture=true"+
                " priorPendingOriginalTokenAndNonce=true"+
                " distinctCanonicalLabPostimageForced=true"+
                " forgedOwnerAndSameImageDenied=true"+
                " replayAfterClosedDenied=true"+
                " realLegacyAccountBytesUnchanged=true"+
                " failedWorldFinalRetainsOriginalLease=true"+
                " freshJvmClosedStillNoAdmission=true"+
                " noPositiveACKOrRecovery=true");
            System.out.println("R79_REAL_WORLD_CAPTURED_FINAL_PENDING_PASS"+
                " originalOwnedJVMTokenRemembered=true"+
                " sameOriginalWorldCaptureAndNonce=true"+
                " forgedOwnerWrongSnapshotAndReplayDenied=true"+
                " freshJvmFinalPendingDoesNotGrantAdmission=true"+
                " realAccountPostimageNotPublished=true"+
                " noFinalAckReleaseOrRecovery=true");
            System.out.println("R78_REAL_WORLD_CAPTURED_OWNED_PHASE_PASS"+
                " sameOriginalWorldTokenAndLock=true"+
                " nonceGeneratedInternally=true"+
                " forgedWorldAndWrongCaptureDenied=true"+
                " capturedGenesisDigestAndEpochBound=true"+
                " duplicateClaimDenied=true"+
                " ownedIntermediateNeverAuthorizedAdmission=true"+
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
                            System.out.println(
                                "R77_REAL_WORLD_GENESIS_ARCHIVE_FORCED");
                            System.out.flush();
                            // Parent observes ready only after the
                            // process-kill marker was flushed.
                            Files.write(ready,new byte[]{1},
                                StandardOpenOption.CREATE_NEW);
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
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_CLAIM_LAB_PROPERTY,
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_PREPARE_LAB_PROPERTY,
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_SETTLE_LAB_PROPERTY
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

    /**
     * R80 genuine original TCP World, R76 genesis and R78 owner-bound
     * claim followed by R79 FINAL_PENDING preparation interrupted at
     * a forced journal stage. The JVM must never return from this test.
     */
    private static void prepareKillHolder(
        Path root,Path ready,LocalReusableAccountEpochLab.Stage target
    )throws Exception{
        configure(root,true);
        System.setProperty(
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_CLAIM_LAB_PROPERTY,
            "true");
        System.setProperty(
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_PREPARE_LAB_PROPERTY,
            "true");
        FilePlayerRepository repository=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        World world=World.isolatedForTest(50L,repository);
        try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                .OneSession server=
                new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession(world)){
            try(Socket socket=server.login(USER)){
                await(()->world.players().byName(USER)!=null,
                    "R80 authenticated World owner");
                WorldPlayer owner=world.players().byName(USER);
                long generation=owner.generation();
                check(generation>0&&
                    LocalJvmAccountSessionLease.activeCountForTest()==1,
                    "R80 no genuine original World token");
                world.submit(owner,generation,()->
                    owner.snapshotExtensions().replaceNamespace(
                        "r80_prepared",
                        Collections.singletonMap("phase","PREPARE_KILL"))
                ).get(5,TimeUnit.SECONDS);
                WorldPlayerPersistence.CapturedSave captured=
                    capture(world,owner,generation);
                check("PREPARE_KILL".equals(
                    captured.ticket.snapshot.value(
                        "extension.r80_prepared.phase")),
                    "R80 snapshot not captured on World thread");
                Path account=LocalAccountProfiles.accountFile(USER);
                LocalReusableAccountEpochLab.Observation genesis=
                    LocalJvmAccountSessionLease
                        .initializeCapturedEpochGenesisUnderOriginalLockForLab(
                            account,owner,generation,
                            captured.ticket.snapshot,captured.ownerWitness);
                LocalReusableAccountEpochLab.Observation owned=
                    LocalJvmAccountSessionLease
                        .claimCapturedEpochUnderOriginalLockForLab(
                            account,owner,generation,
                            captured.ticket.snapshot,captured.ownerWitness);
                check(genesis.sequence==0&&owned.sequence==1&&
                    owned.phase==LocalReusableAccountEpochLab.Phase.OWNED&&
                    !owned.admissionAuthorized&&!owned.releaseAuthorized,
                    "R80 authentic genesis/claim did not hold");
                LocalJvmAccountSessionLease
                    .prepareCapturedEpochUnderOriginalLockForLab(
                        account,owner,generation,captured.ticket.snapshot,
                        captured.ownerWitness,stage->{
                            if(stage!=target)return;
                            System.out.println(
                                "R80_REAL_WORLD_PREPARE_CUTPOINT_"+target);
                            System.out.flush();
                            // Parent observes ready only after the
                            // process-kill marker was flushed.
                            Files.write(ready,new byte[]{1},
                                StandardOpenOption.CREATE_NEW);
                            try{Thread.sleep(120000L);}
                            catch(InterruptedException interrupted){
                                Thread.currentThread().interrupt();
                                throw new IOException(
                                    "R80_HOLDER_INTERRUPTED",interrupted);
                            }
                            throw new AssertionError(
                                "R80_HOLDER_MUST_BE_KILLED");
                        });
            }
        }finally{
            if(!world.closed())world.close();
        }
        throw new AssertionError("R80 prepare-holder returned normally");
    }

    private static void runKilledPrepare(
        LocalReusableAccountEpochLab.Stage stage
    )throws Exception{
        Path root=Files.createTempDirectory(
            "r80-preparation-"+stage.name().toLowerCase(
                java.util.Locale.ROOT)+"-");
        String[] flags={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_GENESIS_LAB_PROPERTY,
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_CLAIM_LAB_PROPERTY,
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_PREPARE_LAB_PROPERTY
        };
        String[] previous=new String[flags.length];
        for(int i=0;i<flags.length;i++)
            previous[i]=System.getProperty(flags[i]);
        Process child=null;
        try{
            configure(root,false);
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            repo.save(PlayerSnapshotCodec.capture(USER,new WorldPlayer()));
            Path account=LocalAccountProfiles.accountFile(USER);
            byte[] baseline=Files.readAllBytes(account);
            check(baseline.length>0,"R80 original account absent");
            Path ready=root.resolve("ready.flag");
            Path log=root.resolve("prepare-holder.log");
            ProcessBuilder builder=new ProcessBuilder(
                javaExe(),"-cp",System.getProperty("java.class.path"),
                G21399RealCapturedEpochGenesisRegressionTest.class.getName(),
                "--prepare-holder",root.toString(),ready.toString(),
                stage.name());
            builder.redirectErrorStream(true);
            builder.redirectOutput(log.toFile());
            child=builder.start();
            final Process running=child;
            await(()->Files.exists(ready,LinkOption.NOFOLLOW_LINKS)||
                !running.isAlive(),
                "R80 real World preparation force boundary "+stage);
            String trace=new String(Files.readAllBytes(log),
                StandardCharsets.UTF_8);
            check(Files.exists(ready,LinkOption.NOFOLLOW_LINKS)&&
                running.isAlive()&&trace.contains(
                    "R80_REAL_WORLD_PREPARE_CUTPOINT_"+stage),
                "R80 did not reach forced target "+stage+" "+trace);
            child.destroyForcibly();
            check(child.waitFor(10,TimeUnit.SECONDS)&&
                child.exitValue()!=0,
                "R80 real World child not forcibly terminated");
            check(Arrays.equals(baseline,Files.readAllBytes(account)),
                "R80 partial preparation rewrote legacy account image");
            Path head=account.resolveSibling(
                account.getFileName()+".g21385-ledger-head");
            String headText=new String(Files.readAllBytes(head),
                StandardCharsets.US_ASCII);
            int expectedSequence=
                stage==LocalReusableAccountEpochLab.Stage
                    .ARCHIVED_HEAD_FORCED?1:2;
            String expectedPhase=expectedSequence==1?"OWNED":"FINAL_PENDING";
            check(headText.contains("sequence="+expectedSequence+"\n")&&
                headText.contains("phase="+expectedPhase+"\n"),
                "R80 HEAD not at expected interrupted phase "+stage);
            boolean negative=false;
            try{
                LocalReusableLabEvidenceNegativeGate
                    .requireNoLegacyCollision(account);
            }catch(IOException veto){
                negative=contains(veto,NEGATIVE);
            }
            check(negative,
                "R80 incomplete final preparation lacked negative sidecar");
            boolean receiptComplete=
                stage==LocalReusableAccountEpochLab.Stage.RECEIPT_FORCED;
            Path receipt=account.resolveSibling(
                account.getFileName()+".g21386-receipt-0002");
            check(Files.exists(receipt,LinkOption.NOFOLLOW_LINKS)==
                receiptComplete,
                "R81 wrong final receipt presence for cutpoint "+stage);
            probeInChild(root,true,true,receiptComplete);
            check(Arrays.equals(baseline,Files.readAllBytes(account)),
                "R81 fresh JVM probe modified old account bytes");
            System.out.println("R81_REAL_WORLD_PREPARE_KILL_MATRIX_PASS"+
                " stage="+stage+
                " genuineWorldCaptureAndClaim=true"+
                " originalOSLockHeldAtKill=true"+
                " completeReceipt="+receiptComplete+
                " ledgerNeverAuthorizesAdmission=true"+
                " freshJvmAdmissionAndTCPDenied=true"+
                " previousAccountUnchanged=true"+
                " noPositiveFinalReceiptOrRecovery=true"+
                " noPowerLossClaim=true");
        }finally{
            if(child!=null&&child.isAlive()){
                child.destroyForcibly();
                child.waitFor(10,TimeUnit.SECONDS);
            }
            for(int i=0;i<flags.length;i++){
                if(previous[i]==null)System.clearProperty(flags[i]);
                else System.setProperty(flags[i],previous[i]);
            }
            try(Stream<Path> files=Files.walk(root)){
                for(Path file:files.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(file);
            }
        }
    }

    /**
     * R83: original authenticated TCP World completes R76 -> R79
     * but is hard-killed during R82 detached LAB settlement.
     * No real final save, no legacy account mutation and no release.
     */
    private static void settleKillHolder(
        Path root,Path ready,LocalReusableAccountEpochLab.Stage target
    )throws Exception{
        configure(root,true);
        System.setProperty(
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_CLAIM_LAB_PROPERTY,
            "true");
        System.setProperty(
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_PREPARE_LAB_PROPERTY,
            "true");
        System.setProperty(
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_SETTLE_LAB_PROPERTY,
            "true");
        FilePlayerRepository repository=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        World world=World.isolatedForTest(50L,repository);
        try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                .OneSession server=
                new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession(world)){
            try(Socket socket=server.login(USER)){
                await(()->world.players().byName(USER)!=null,
                    "R83 original authenticated TCP World");
                WorldPlayer owner=world.players().byName(USER);
                long generation=owner.generation();
                check(generation>0&&
                    LocalJvmAccountSessionLease.activeCountForTest()==1,
                    "R83 original World lease was not held");
                world.submit(owner,generation,()->
                    owner.snapshotExtensions().replaceNamespace(
                        "r83_initial",
                        Collections.singletonMap("phase","ORIGINAL"))
                ).get(5,TimeUnit.SECONDS);
                WorldPlayerPersistence.CapturedSave first=
                    capture(world,owner,generation);
                Path account=LocalAccountProfiles.accountFile(USER);
                LocalReusableAccountEpochLab.Observation seed=
                    LocalJvmAccountSessionLease
                        .initializeCapturedEpochGenesisUnderOriginalLockForLab(
                            account,owner,generation,first.ticket.snapshot,
                            first.ownerWitness);
                LocalReusableAccountEpochLab.Observation owned=
                    LocalJvmAccountSessionLease
                        .claimCapturedEpochUnderOriginalLockForLab(
                            account,owner,generation,first.ticket.snapshot,
                            first.ownerWitness);
                LocalReusableAccountEpochLab.Observation pending=
                    LocalJvmAccountSessionLease
                        .prepareCapturedEpochUnderOriginalLockForLab(
                            account,owner,generation,first.ticket.snapshot,
                            first.ownerWitness);
                check(seed.sequence==0&&owned.sequence==1&&
                    pending.sequence==2&&pending.epoch==2&&
                    pending.phase==LocalReusableAccountEpochLab
                        .Phase.FINAL_PENDING&&
                    !pending.admissionAuthorized&&!pending.releaseAuthorized,
                    "R83 real owner setup incomplete");
                world.submit(owner,generation,()->
                    owner.snapshotExtensions().replaceNamespace(
                        "r83_final",
                        Collections.singletonMap("phase","LATER"))
                ).get(5,TimeUnit.SECONDS);
                WorldPlayerPersistence.CapturedSave last=
                    captureFinal(world,owner,generation);
                check("LATER".equals(last.ticket.snapshot.value(
                    "extension.r83_final.phase")),
                    "R83 actual later World capture missing");
                LocalJvmAccountSessionLease
                    .settleCapturedEpochUnderOriginalLockForLab(
                        account,owner,generation,last.ticket.snapshot,
                        last.ownerWitness,stage->{
                            if(stage!=target)return;
                            System.out.println(
                                "R83_WORLD_SETTLEMENT_FORCED_"+target);
                            System.out.flush();
                            // Parent observes ready only after the
                            // process-kill marker was flushed.
                            Files.write(ready,new byte[]{1},
                                StandardOpenOption.CREATE_NEW);
                            try{Thread.sleep(120000L);}
                            catch(InterruptedException interrupted){
                                Thread.currentThread().interrupt();
                                throw new IOException(
                                    "R83_HOLDER_INTERRUPTED",interrupted);
                            }
                            throw new AssertionError(
                                "R83_HOLDER_MUST_BE_KILLED");
                        });
            }
        }finally{
            if(!world.closed())world.close();
        }
        throw new AssertionError("R83 holder was not hard-killed");
    }

    /**
     * R83 independent JVM with every producer opt-in disabled.
     * The original OS lock must be released by kernel process exit,
     * while LAB orphan/complete evidence still permanently vetoes
     * legacy reads and fresh authenticated World login.
     */
    private static void settlementFreshProbe(
        Path root,LocalReusableAccountEpochLab.Stage stage
    )throws Exception{
        configure(root,false);
        Path account=LocalAccountProfiles.accountFile(USER);
        FilePlayerRepository repository=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        boolean denied=false;
        try{repository.loadForWorldSession(USER);}
        catch(IOException invalid){denied=contains(invalid,NEGATIVE);}
        check(denied,"R83 fresh World repository accepted LAB evidence");
        WorldPlayer direct=new WorldPlayer();
        denied=false;
        try{
            LocalAccountProfiles.load(USER,direct.bank(),direct.equipment(),
                direct.movement(),direct.petState(),direct.playerState());
        }catch(IOException invalid){denied=contains(invalid,NEGATIVE);}
        check(denied,"R83 fresh RAW hydration accepted LAB evidence");
        World world=World.isolatedForTest(50L,repository);
        try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                .OneSession server=
                new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession(world)){
            check(tcpResponse(server.listener.getLocalPort())!=2&&
                world.players().size()==0,
                "R83 fresh actual TCP World admitted settlement evidence");
            server.waitCompleted();
        }finally{
            if(!world.closed())world.close();
        }
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        System.setProperty(
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,"true");
        try(LocalCrossJvmAccountSessionLock.Handle reclaimed=
                LocalCrossJvmAccountSessionLock.acquire(account)){
            reclaimed.requireOriginalLockNameStillPresent();
        }
        if(stage==LocalReusableAccountEpochLab.Stage.RECEIPT_FORCED){
            LocalReusableAccountEpochLab.Observation complete=
                LocalReusableAccountEpochLab.inspect(account);
            check(complete.sequence==3&&complete.epoch==2&&
                complete.phase==LocalReusableAccountEpochLab.Phase.CLOSED&&
                complete.worldGeneration==0&&
                "none".equals(complete.ownerNonce)&&
                complete.imageSequence==3&&
                !complete.admissionAuthorized&&!complete.releaseAuthorized,
                "R83 fully forced CLOSED ledger granted live authority");
        }else{
            String expected;
            if(stage==LocalReusableAccountEpochLab.Stage.SNAPSHOT_FORCED)
                expected="R57_ORPHAN_FUTURE_IMAGE_MANUAL_REVIEW";
            else if(stage==LocalReusableAccountEpochLab.Stage
                    .ARCHIVED_HEAD_FORCED)
                expected="R59_ORPHAN_OR_MISSING_ARCHIVE_MANUAL_REVIEW";
            else expected="R58_ORPHAN_OR_MISSING_JOURNAL_MANUAL_REVIEW";
            // R84: no auto-repair, orphan cleanup, retry or
            // promotion between two forensic observations.
            for(int attempt=0;attempt<2;attempt++){
                boolean incomplete=false;
                try{LocalReusableAccountEpochLab.inspect(account);}
                catch(IOException invalid){
                    incomplete=contains(invalid,expected);
                }
                check(incomplete,
                    "R84 interrupted settlement not persistently "+
                    "negative: "+stage+" attempt="+attempt);
            }
        }
        System.out.println("R83_FRESH_JVM_SETTLEMENT_NEGATIVE_PASS"+
            " stage="+stage+
            " fullReceipt="+
                (stage==LocalReusableAccountEpochLab.Stage.RECEIPT_FORCED)+
            " lockReleasedAfterKill=true"+
            " freshRepositoryDirectAndTcpDenied=true"+
            " noFinalAckOrRecovery=true");
    }

    private static void runKilledSettlement(
        LocalReusableAccountEpochLab.Stage stage
    )throws Exception{
        Path root=Files.createTempDirectory(
            "r83-settlement-"+stage.name().toLowerCase(
                java.util.Locale.ROOT)+"-");
        String[] flags={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_GENESIS_LAB_PROPERTY,
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_CLAIM_LAB_PROPERTY,
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_PREPARE_LAB_PROPERTY,
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_SETTLE_LAB_PROPERTY
        };
        String[] saved=new String[flags.length];
        for(int i=0;i<flags.length;i++)saved[i]=System.getProperty(flags[i]);
        Process holder=null;
        try{
            configure(root,false);
            FilePlayerRepository repository=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            repository.save(PlayerSnapshotCodec.capture(
                USER,new WorldPlayer()));
            Path account=LocalAccountProfiles.accountFile(USER);
            byte[] baseline=Files.readAllBytes(account);
            Path ready=root.resolve("ready.flag");
            Path log=root.resolve("holder.log");
            ProcessBuilder builder=new ProcessBuilder(
                javaExe(),"-cp",System.getProperty("java.class.path"),
                G21399RealCapturedEpochGenesisRegressionTest.class.getName(),
                "--settlement-holder",root.toString(),ready.toString(),
                stage.name());
            builder.redirectErrorStream(true);
            builder.redirectOutput(log.toFile());
            holder=builder.start();
            final Process running=holder;
            await(()->Files.exists(ready,LinkOption.NOFOLLOW_LINKS)||
                !running.isAlive(),"R83 settlement stage "+stage);
            String trace=new String(Files.readAllBytes(log),
                StandardCharsets.UTF_8);
            check(Files.exists(ready,LinkOption.NOFOLLOW_LINKS)&&
                running.isAlive()&&trace.contains(
                    "R83_WORLD_SETTLEMENT_FORCED_"+stage),
                "R83 real World never reached forced cutpoint "+trace);
            holder.destroyForcibly();
            check(holder.waitFor(10,TimeUnit.SECONDS)&&
                holder.exitValue()!=0,"R83 original JVM not killed");
            check(Arrays.equals(baseline,Files.readAllBytes(account)),
                "R83 killed LAB settlement rewrote historical account");
            Path head=account.resolveSibling(
                account.getFileName()+".g21385-ledger-head");
            String content=new String(Files.readAllBytes(head),
                StandardCharsets.US_ASCII);
            int step=stage.ordinal();
            boolean moved=step>=LocalReusableAccountEpochLab.Stage
                .HEAD_MOVED_BEFORE_DIR_FORCE.ordinal();
            check(content.contains("sequence="+(moved?3:2)+"\n")&&
                content.contains("phase="+(moved?"CLOSED":"FINAL_PENDING")+
                    "\n"),"R84 wrong persisted HEAD after interruption "+
                    stage);
            Path intent=account.resolveSibling(
                account.getFileName()+".g21386-intent-0003");
            Path receipt=account.resolveSibling(
                account.getFileName()+".g21386-receipt-0003");
            check(Files.exists(intent,LinkOption.NOFOLLOW_LINKS)==
                (step>=LocalReusableAccountEpochLab.Stage
                    .INTENT_FORCED.ordinal()),
                "R84 intent presence contradicted forced cutpoint "+stage);
            check(Files.exists(receipt,LinkOption.NOFOLLOW_LINKS)==
                (stage==LocalReusableAccountEpochLab.Stage.RECEIPT_FORCED),
                "R84 receipt incorrectly present/absent "+stage);
            // No other stage may silently create/remove a future
            // image, candidate archive or uncommitted staged HEAD.
            int futureImages=0,archives=0,stagedHeads=0;
            String stem=account.getFileName().toString();
            try(java.nio.file.DirectoryStream<Path> siblings=
                    Files.newDirectoryStream(account.getParent())){
                for(Path sibling:siblings){
                    String leaf=sibling.getFileName().toString();
                    if(leaf.startsWith(
                            stem+".g21385-image-0003-")&&
                       leaf.endsWith(".snapshot"))
                        futureImages++;
                    if(leaf.startsWith(
                            stem+".g21387-head-0003-")&&
                       leaf.endsWith(".head"))
                        archives++;
                    if(leaf.startsWith(
                            stem+".g21385-head-stage-")&&
                       leaf.endsWith(".tmp"))
                        stagedHeads++;
                }
            }
            check(futureImages==1,
                "R84 forced future settlement snapshot missing "+stage);
            check(archives==(step>=LocalReusableAccountEpochLab.Stage
                    .ARCHIVED_HEAD_FORCED.ordinal()?1:0),
                "R84 immutable archived HEAD inventory wrong "+stage);
            check(stagedHeads==(stage==LocalReusableAccountEpochLab.Stage
                    .HEAD_TEMP_FORCED?1:0),
                "R84 pre-move staged HEAD inventory wrong "+stage);
            boolean negative=false;
            try{
                LocalReusableLabEvidenceNegativeGate
                    .requireNoLegacyCollision(account);
            }catch(IOException evidence){negative=contains(evidence,NEGATIVE);}
            check(negative,"R83 original account lost permanent LAB veto");
            Path fresh=root.resolve("fresh.log");
            ProcessBuilder next=new ProcessBuilder(
                javaExe(),"-cp",System.getProperty("java.class.path"),
                G21399RealCapturedEpochGenesisRegressionTest.class.getName(),
                "--settlement-fresh",root.toString(),stage.name());
            next.redirectErrorStream(true);
            next.redirectOutput(fresh.toFile());
            Process probe=next.start();
            if(!probe.waitFor(25,TimeUnit.SECONDS)){
                probe.destroyForcibly();
                probe.waitFor(10,TimeUnit.SECONDS);
                throw new AssertionError("R83 fresh JVM timed out");
            }
            String output=new String(Files.readAllBytes(fresh),
                StandardCharsets.UTF_8);
            check(probe.exitValue()==0&&output.contains(
                "R83_FRESH_JVM_SETTLEMENT_NEGATIVE_PASS"),
                "R83 fresh JVM did not veto settlement: "+output);
            check(Arrays.equals(baseline,Files.readAllBytes(account)),
                "R83 fresh probe rewrote real legacy account bytes");
            System.out.println("R84_SEVEN_STAGE_SETTLEMENT_KILL_PASS"+
                " stage="+stage+
                " exactOrphanInventoryConfirmed=true"+
                " forensicDoubleInspectionFailClosed=true");
            System.out.println("R83_REAL_WORLD_SETTLEMENT_KILL_PASS"+
                " stage="+stage+
                " authenticWorldCapture=true"+
                " originalOsLockAtForce=true"+
                " receiptComplete="+
                    (stage==LocalReusableAccountEpochLab.Stage.RECEIPT_FORCED)+
                " legacyAccountUnmodified=true"+
                " freshIndependentJvmAdmissionDenied=true"+
                " noPositiveWorldAckOrPowerLossClaim=true");
        }finally{
            if(holder!=null&&holder.isAlive()){
                holder.destroyForcibly();
                holder.waitFor(10,TimeUnit.SECONDS);
            }
            for(int i=0;i<flags.length;i++){
                if(saved[i]==null)System.clearProperty(flags[i]);
                else System.setProperty(flags[i],saved[i]);
            }
            try(Stream<Path> children=Files.walk(root)){
                for(Path leaf:children.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(leaf);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length==2&&("--fresh".equals(args[0])||
                            "--killed".equals(args[0])||
                            "--prepare-killed".equals(args[0])||
                            "--prepare-receipt-complete".equals(args[0]))){
            boolean receiptComplete=
                "--prepare-receipt-complete".equals(args[0]);
            freshProbe(Paths.get(args[1]),
                !"--fresh".equals(args[0]),
                "--prepare-killed".equals(args[0])||receiptComplete,
                receiptComplete);
            return;
        }
        if(args.length==3&&"--kill-holder".equals(args[0])){
            killHolder(Paths.get(args[1]),Paths.get(args[2]));
            return;
        }
        if(args.length==4&&"--prepare-holder".equals(args[0])){
            prepareKillHolder(Paths.get(args[1]),Paths.get(args[2]),
                LocalReusableAccountEpochLab.Stage.valueOf(args[3]));
            return;
        }
        if(args.length==4&&"--settlement-holder".equals(args[0])){
            settleKillHolder(Paths.get(args[1]),Paths.get(args[2]),
                LocalReusableAccountEpochLab.Stage.valueOf(args[3]));
            return;
        }
        if(args.length==3&&"--settlement-fresh".equals(args[0])){
            settlementFreshProbe(Paths.get(args[1]),
                LocalReusableAccountEpochLab.Stage.valueOf(args[2]));
            return;
        }
        check(args.length==0,"unexpected args");
        exercise();
        runKilledGenesis();
        runKilledPrepare(
            LocalReusableAccountEpochLab.Stage.ARCHIVED_HEAD_FORCED);
        runKilledPrepare(
            LocalReusableAccountEpochLab.Stage.HEAD_DIR_FORCED);
        // Move without directory-force is never host power-loss proof:
        // on a still-running filesystem this missing receipt stays
        // negative whether the renamed HEAD is visible or not.
        runKilledPrepare(
            LocalReusableAccountEpochLab.Stage.HEAD_MOVED_BEFORE_DIR_FORCE);
        runKilledPrepare(
            LocalReusableAccountEpochLab.Stage.RECEIPT_FORCED);
        // Exhaustive enum stage inventory: each exact force
        // boundary is a distinct independent World child-JVM kill.
        for(LocalReusableAccountEpochLab.Stage stage:
                LocalReusableAccountEpochLab.Stage.values())
            runKilledSettlement(stage);
    }
}
