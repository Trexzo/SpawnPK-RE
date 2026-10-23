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
    /** R97: strict inventory of the four independently required
     *  opt-in knobs, never an arbitrary JVM system property. */
    private static String[] settlementExperimentFlags(){
        return new String[]{
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_GENESIS_LAB_PROPERTY,
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_CLAIM_LAB_PROPERTY,
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_PREPARE_LAB_PROPERTY,
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_SETTLE_LAB_PROPERTY
        };
    }

    private static String checkedSettlementExperimentFlag(
        String requested
    ){
        for(String allowed:settlementExperimentFlags())
            if(allowed.equals(requested))return allowed;
        throw new IllegalArgumentException(
            "R97_UNREVIEWED_SETTLEMENT_FLAG");
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
    /** R89: real World-thread deferred FINAL snapshot capture only.
     * This unreserved variant must fail the new R90 LAB settlement. */
    private static WorldPlayerPersistence.CapturedSave
    captureFinalWithoutReservation(
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

    /**
     * R90: bind a real World deferred-final capture to the existing
     * owner/generation FIFO final slot. R91 keeps that EXACT task
     * reserved through LAB settlement; every test explicitly aborts
     * it without calling publish or acknowledging an actual save.
     */
    private static final java.util.IdentityHashMap<
        WorldPlayerPersistence.CapturedSave,
        WorldPlayerPersistence.FinalSaveReservation>
        labFinalReservations=new java.util.IdentityHashMap<>();

    private static WorldPlayerPersistence.CapturedSave captureFinal(
        World world,WorldPlayer owner,long generation
    )throws Exception{
        WorldPlayerPersistence.FinalSaveReservation reserved=
            world.persistence().reserveFinalSaveWithBackpressure(
                owner,generation,5000L);
        try{
            WorldPlayerPersistence.CapturedSave captured=
                captureFinalWithoutReservation(world,owner,generation);
            synchronized(labFinalReservations){
                labFinalReservations.put(captured,reserved);
            }
            return captured;
        }catch(Exception|Error failure){
            reserved.abort(failure);
            throw failure;
        }
    }

    private static void abortLabFinal(
        WorldPlayerPersistence.CapturedSave captured
    ){
        WorldPlayerPersistence.FinalSaveReservation reserved;
        synchronized(labFinalReservations){
            reserved=labFinalReservations.remove(captured);
        }
        check(reserved!=null,"R91 no tracked final FIFO to abort");
        reserved.abort(new IllegalStateException(
            "R91_LAB_CAPTURE_ABORT_NO_FINAL_PUBLICATION"));
    }

    private static void abortAllLabFinals(){
        java.util.ArrayList<WorldPlayerPersistence.FinalSaveReservation>
            pending;
        synchronized(labFinalReservations){
            pending=new java.util.ArrayList<>(
                labFinalReservations.values());
            labFinalReservations.clear();
        }
        for(WorldPlayerPersistence.FinalSaveReservation slot:pending)
            slot.abort(new IllegalStateException(
                "R91_LAB_CLEANUP_WITHOUT_PUBLICATION"));
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
                    abortLabFinal(preprepared);
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
                    // Genuine World final captures without a queued
                    // owner-bound reservation are also insufficient.
                    WorldPlayerPersistence.CapturedSave unreserved=
                        captureFinalWithoutReservation(
                            world,owner,generation);
                    check(unreserved.finalDisconnect,
                        "unreserved negative was not an actual final capture");
                    WorldPlayerPersistence.CapturedSave abortedLate=
                        captureFinal(world,owner,generation);
                    WorldPlayerPersistence.FinalSaveReservation oldSlot;
                    synchronized(labFinalReservations){
                        oldSlot=labFinalReservations.get(abortedLate);
                    }
                    check(oldSlot!=null,
                        "R91 old original FIFO identity not tracked");
                    abortLabFinal(abortedLate);
                    WorldPlayerPersistence.CapturedSave later=
                        captureFinal(world,owner,generation);
                    // Deterministically replay the older aborted
                    // worker's delayed cleanup AFTER the new exact
                    // same-owner/generation reservation was admitted.
                    // Prior generation-only cleanup would erase
                    // the new fence and fail the later settlement.
                    oldSlot.replayAbortedWorkerCleanupForTest();
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
                    // R91.6: a genuine but UNRESERVED final save
                    // task has no FIFO slot to clear. Reproduce its
                    // terminal cleanup while the new real reserved
                    // final capture is still held. Earlier generation-
                    // only cleanup would erase the new reservation.
                    world.persistence()
                        .replayUnboundFinalCleanupForLabTest(unreserved);
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
                    boolean unreservedDenied=false;
                    try{
                        LocalJvmAccountSessionLease
                            .settleCapturedEpochUnderOriginalLockForLab(
                                selected,owner,generation,
                                unreserved.ticket.snapshot,
                                unreserved.ownerWitness);
                    }catch(IOException denied){
                        unreservedDenied=contains(denied,
                            "R90_FINAL_CAPTURE_WITHOUT_ACTIVE_RESERVATION");
                    }
                    check(unreservedDenied,
                        "unreserved genuine World final captured LAB settlement");
                    // Even a REAL final capture with a matching FIFO
                    // reservation at capture time is denied if that
                    // exact original reservation has been aborted.
                    // The new reservation used for 'later' cannot
                    // revive the older witness (R91 ABA guard).
                    boolean abortedLateDenied=false;
                    try{
                        LocalJvmAccountSessionLease
                            .settleCapturedEpochUnderOriginalLockForLab(
                                selected,owner,generation,
                                abortedLate.ticket.snapshot,
                                abortedLate.ownerWitness);
                    }catch(IOException denied){
                        abortedLateDenied=contains(denied,
                            "R91_ORIGINAL_FINAL_RESERVATION_ABORTED_OR_REPLACED");
                    }
                    check(abortedLateDenied,
                        "R91 replaced FIFO revived a stale final snapshot");
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
                    abortLabFinal(later);
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
            System.out.println("R91_UNBOUND_FINAL_CLEANUP_NO_CROSS_SLOT_PASS"+
                " unboundFinalCannotClearBoundReservation=true"+
                " genuineBoundFinalStillSettlesLabOnly=true"+
                " noPublicationOrAcknowledgement=true");
            System.out.println("R91_ORIGINAL_FINAL_RESERVATION_CONTINUITY_PASS"+
                " abortedOriginalTaskDenied=true"+
                " replacementSameOwnerGenerationCannotRevive=true"+
                " delayedOldWorkerCleanupReplaySafe=true"+
                " exactOriginalTaskHeldAtSettlementEntry=true"+
                " originalFinalQueueAbortedWithoutPublish=true"+
                " noRealFinalACKOrRecovery=true");
            System.out.println("R90_REAL_FINAL_FIFO_RESERVATION_LAB_PASS"+
                " genuineUnreservedFinalRejected=true"+
                " realOwnerBoundFinalFIFOAtCapture=true"+
                " labFinalSlotAbortedWithoutPublication=true"+
                " noFinalSaveACKOrLeaseRelease=true"+
                " negativeAdmissionRemains=true");
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
            abortAllLabFinals();
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
        settleKillHolder(root,ready,target,false);
    }

    private static void settleKillHolder(
        Path root,Path ready,LocalReusableAccountEpochLab.Stage target,
        boolean revokeAtCutpoint
    )throws Exception{
        settleKillHolder(root,ready,target,revokeAtCutpoint,false);
    }

    private static void settleKillHolder(
        Path root,Path ready,LocalReusableAccountEpochLab.Stage target,
        boolean revokeAtCutpoint,boolean revokeAfterReadback
    )throws Exception{
        settleKillHolder(root,ready,target,revokeAtCutpoint,
            revokeAfterReadback,false,false);
    }

    private static void settleKillHolder(
        Path root,Path ready,LocalReusableAccountEpochLab.Stage target,
        boolean revokeAtCutpoint,boolean revokeAfterReadback,
        boolean disableAtCutpoint,boolean disableAfterReadback
    )throws Exception{
        settleKillHolder(root,ready,target,revokeAtCutpoint,
            revokeAfterReadback,disableAtCutpoint,disableAfterReadback,
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_SETTLE_LAB_PROPERTY,
            false);
    }

    private static void settleKillHolder(
        Path root,Path ready,LocalReusableAccountEpochLab.Stage target,
        boolean revokeAtCutpoint,boolean revokeAfterReadback,
        boolean disableAtCutpoint,boolean disableAfterReadback,
        String experimentalFlag
    )throws Exception{
        settleKillHolder(root,ready,target,revokeAtCutpoint,
            revokeAfterReadback,disableAtCutpoint,disableAfterReadback,
            experimentalFlag,false);
    }

    private static void settleKillHolder(
        Path root,Path ready,LocalReusableAccountEpochLab.Stage target,
        boolean revokeAtCutpoint,boolean revokeAfterReadback,
        boolean disableAtCutpoint,boolean disableAfterReadback,
        String experimentalFlag,boolean shutdownAtCutpoint
    )throws Exception{
        settleKillHolder(root,ready,target,revokeAtCutpoint,
            revokeAfterReadback,disableAtCutpoint,disableAfterReadback,
            experimentalFlag,shutdownAtCutpoint,false);
    }

    private static void settleKillHolder(
        Path root,Path ready,LocalReusableAccountEpochLab.Stage target,
        boolean revokeAtCutpoint,boolean revokeAfterReadback,
        boolean disableAtCutpoint,boolean disableAfterReadback,
        String experimentalFlag,boolean shutdownAtCutpoint,
        boolean fullCloseAtCutpoint
    )throws Exception{
        settleKillHolder(root,ready,target,revokeAtCutpoint,
            revokeAfterReadback,disableAtCutpoint,disableAfterReadback,
            experimentalFlag,shutdownAtCutpoint,fullCloseAtCutpoint,false);
    }

    private static void settleKillHolder(
        Path root,Path ready,LocalReusableAccountEpochLab.Stage target,
        boolean revokeAtCutpoint,boolean revokeAfterReadback,
        boolean disableAtCutpoint,boolean disableAfterReadback,
        String experimentalFlag,boolean shutdownAtCutpoint,
        boolean fullCloseAtCutpoint,boolean closeAfterReadback
    )throws Exception{
        settleKillHolder(root,ready,target,revokeAtCutpoint,
            revokeAfterReadback,disableAtCutpoint,disableAfterReadback,
            experimentalFlag,shutdownAtCutpoint,fullCloseAtCutpoint,
            closeAfterReadback,false);
    }

    private static void settleKillHolder(
        Path root,Path ready,LocalReusableAccountEpochLab.Stage target,
        boolean revokeAtCutpoint,boolean revokeAfterReadback,
        boolean disableAtCutpoint,boolean disableAfterReadback,
        String experimentalFlag,boolean shutdownAtCutpoint,
        boolean fullCloseAtCutpoint,boolean closeAfterReadback,
        boolean closeAfterFinalRead
    )throws Exception{
        settleKillHolder(root,ready,target,revokeAtCutpoint,
            revokeAfterReadback,disableAtCutpoint,disableAfterReadback,
            experimentalFlag,shutdownAtCutpoint,fullCloseAtCutpoint,
            closeAfterReadback,closeAfterFinalRead,false);
    }

    /**
     * R104/R105: production persistence closes from another thread
     * while experimental settlement holds the original account Token.
     * Neither this helper nor its caller publishes a final-save ACK.
     */
    private static void closePersistenceConcurrentlyForTest(
        World world,String milestone
    )throws IOException{
        final java.util.concurrent.atomic.AtomicReference<Throwable>
            failure=new java.util.concurrent.atomic.AtomicReference<>();
        Thread closer=new Thread(()->{
            try{world.persistence().close();}
            catch(Throwable problem){failure.set(problem);}
        },milestone+"-persistence-close");
        closer.setDaemon(true);
        closer.start();
        try{closer.join(15000L);}
        catch(InterruptedException interrupted){
            Thread.currentThread().interrupt();
            throw new IOException(
                milestone+"_CONCURRENT_CLOSE_INTERRUPTED",interrupted);
        }
        if(closer.isAlive())
            throw new IOException(
                milestone+"_CONCURRENT_CLOSE_DEADLOCK_OR_TIMEOUT");
        if(failure.get()!=null)
            throw new IOException(
                milestone+"_CONCURRENT_CLOSE_FAILED",failure.get());
    }

    private static void settleKillHolder(
        Path root,Path ready,LocalReusableAccountEpochLab.Stage target,
        boolean revokeAtCutpoint,boolean revokeAfterReadback,
        boolean disableAtCutpoint,boolean disableAfterReadback,
        String experimentalFlag,boolean shutdownAtCutpoint,
        boolean fullCloseAtCutpoint,boolean closeAfterReadback,
        boolean closeAfterFinalRead,boolean concurrentClose
    )throws Exception{
        if(concurrentClose&&
           ((!closeAfterReadback&&!closeAfterFinalRead)||
            shutdownAtCutpoint||fullCloseAtCutpoint||
            revokeAtCutpoint||revokeAfterReadback||
            disableAtCutpoint||disableAfterReadback||
            target!=LocalReusableAccountEpochLab.Stage.RECEIPT_FORCED))
            throw new IllegalArgumentException(
                "R105_CONCURRENT_CLOSE_REQUIRES_LATE_RECEIPT_MODE");
        if(closeAfterFinalRead&&
           (closeAfterReadback||shutdownAtCutpoint||fullCloseAtCutpoint||
            revokeAtCutpoint||revokeAfterReadback||
            disableAtCutpoint||disableAfterReadback||
            target!=LocalReusableAccountEpochLab.Stage.RECEIPT_FORCED))
            throw new IllegalArgumentException(
                "R103_LATE_CLOSE_REQUIRES_EXCLUSIVE_RECEIPT_STAGE");
        if(closeAfterReadback&&
            (shutdownAtCutpoint||fullCloseAtCutpoint||
             revokeAtCutpoint||revokeAfterReadback||
             disableAtCutpoint||disableAfterReadback||
             target!=LocalReusableAccountEpochLab.Stage.RECEIPT_FORCED))
            throw new IllegalArgumentException(
                "R102_POSTREADBACK_CLOSE_REQUIRES_EXCLUSIVE_RECEIPT_STAGE");
        if(fullCloseAtCutpoint&&!shutdownAtCutpoint)
            throw new IllegalArgumentException(
                "R99_FULL_CLOSE_REQUIRES_SHUTDOWN_MODE");
        if(shutdownAtCutpoint&&
           (revokeAtCutpoint||revokeAfterReadback||
            disableAtCutpoint||disableAfterReadback))
            throw new IllegalArgumentException(
                "R98_EXCLUSIVE_SHUTDOWN_FAULT_MODE");
        if(disableAtCutpoint||disableAfterReadback)
            checkedSettlementExperimentFlag(experimentalFlag);
        if((disableAtCutpoint||disableAfterReadback)&&
           (revokeAtCutpoint||revokeAfterReadback||
            (disableAtCutpoint&&disableAfterReadback)))
            throw new IllegalArgumentException("R96_EXCLUSIVE_FAULT_MODE");
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
                if(shutdownAtCutpoint||closeAfterReadback||
                   closeAfterFinalRead)
                    check(last.ownerWitness
                            .originalFinalReservationStillPresentForLabTest(),
                        "R101 original FIFO was not live before shutdown");
                boolean revocationDenied=false;
                try{
                    LocalReusableAccountEpochLab.Observation labResult=
                        LocalJvmAccountSessionLease
                        .settleCapturedEpochUnderOriginalLockForLab(
                            account,owner,generation,last.ticket.snapshot,
                            last.ownerWitness,stage->{
                                if(stage!=target)return;
                                if(shutdownAtCutpoint){
                                    // R99 invokes the full production
                                    // close lifecycle, not merely the
                                    // shared admission transition.
                                    if(fullCloseAtCutpoint)
                                        world.persistence().close();
                                    else
                                        world.persistence()
                                            .beginLabShutdownForTest();
                                    check(!last.ownerWitness
                                            .originalFinalReservationStillPresentForLabTest(),
                                        "R101 shutdown left captured FIFO witness live");
                                    return;
                                }
                                if(disableAtCutpoint){
                                    // R96: disable the original optional
                                    // LAB feature after a forced stage.
                                    // The real final FIFO stays live.
                                    System.clearProperty(experimentalFlag);
                                    return;
                                }
                                if(revokeAtCutpoint){
                                    // R93: revoke while the true final
                                    // filesystem cutpoint is executing.
                                    // At late stages, the LAB HEAD may
                                    // already be CLOSED, but it must
                                    // never become a real final ACK.
                                    abortLabFinal(last);
                                    return;
                                }
                                // R95 must pass through the final
                                // receipt-force callback, let the
                                // LAB inspect/owner checks finish, and
                                // only THEN revoke its original FIFO.
                                if(revokeAfterReadback||
                                   disableAfterReadback||
                                   closeAfterReadback||
                                   closeAfterFinalRead)return;
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
                            },()->{
                                if(revokeAfterReadback)
                                    abortLabFinal(last);
                                if(disableAfterReadback)
                                    System.clearProperty(experimentalFlag);
                                if(closeAfterReadback){
                                    // R102/R104: actual close AFTER
                                    // journal readback and owner checks.
                                    // R104 uses another thread to
                                    // exercise monitor/visibility
                                    // boundaries while LAB retains
                                    // the original World account Token.
                                    if(concurrentClose)
                                        closePersistenceConcurrentlyForTest(
                                            world,"R104");
                                    else
                                        world.persistence().close();
                                    check(!last.ownerWitness
                                            .originalFinalReservationStillPresentForLabTest(),
                                        "R104 postreadback close left FIFO witness active");
                                }
                            },()->{
                                if(closeAfterFinalRead){
                                    // R105 closes on an independent
                                    // thread AFTER the final volatile
                                    // FIFO witness read, before return.
                                    if(concurrentClose)
                                        closePersistenceConcurrentlyForTest(
                                            world,"R105");
                                    else
                                        world.persistence().close();
                                    check(!last.ownerWitness
                                            .originalFinalReservationStillPresentForLabTest(),
                                        "R105 late close left FIFO witness active");
                                }
                            });
                    if(closeAfterFinalRead)
                        check(labResult.phase==
                                LocalReusableAccountEpochLab.Phase.CLOSED&&
                              !labResult.admissionAuthorized&&
                              !labResult.releaseAuthorized,
                            "R103 late LAB observation acquired real authority");
                }catch(IOException denied){
                    if(!revokeAtCutpoint&&!revokeAfterReadback&&
                       !disableAtCutpoint&&!disableAfterReadback&&
                       !shutdownAtCutpoint&&!closeAfterReadback)
                        throw denied;
                    revocationDenied=contains(denied,
                        disableAtCutpoint||disableAfterReadback
                            ?"R96_SETTLEMENT_LAB_DISABLED_DURING_TRANSITION"
                            :(revokeAfterReadback||closeAfterReadback)
                                ?"R94_FINAL_RESERVATION_REVOKED_BEFORE_LAB_RESULT"
                                :"R92_FINAL_RESERVATION_REVOKED_DURING_SETTLEMENT");
                }
                if(closeAfterFinalRead){
                    check(!revocationDenied,
                        "R103 post-final-check close unexpectedly vetoed LAB result");
                    check(!last.ownerWitness
                            .originalFinalReservationStillPresentForLabTest(),
                        "R103 late close revived FIFO witness");
                    check(LocalJvmAccountSessionLease
                            .activeCountForTest()==1,
                        "R103 late close released original World lease");
                    check(!last.ticket.completion.isDone(),
                        "R103 unpublished final save completed unexpectedly");
                    System.out.println(
                        (concurrentClose
                            ?"R105_WORLD_CONCURRENT_POST_FINAL_READ_CLOSE_PASS"
                            :"R103_WORLD_POST_FINAL_READ_CLOSE_PASS")+
                        " stage="+target+
                        " labOnlyObservationNonAuthorizing=true"+
                        " actualProductionClose=true"+
                        " closeThreadIndependent="+concurrentClose+
                        " originalFifoRevoked=true"+
                        " originalWorldLeaseHeld=true"+
                        " noPublishedFinalAck=true");
                    System.out.flush();
                    return;
                }
                if(closeAfterReadback){
                    check(revocationDenied,
                        "R102/R104 production close returned LAB result");
                    if(concurrentClose)
                        check(!last.ticket.completion.isDone(),
                            "R104 unpublished final save completed during concurrent close");
                    check(!last.ownerWitness
                            .originalFinalReservationStillPresentForLabTest(),
                        "R102 postreadback FIFO witness revived");
                    check(LocalJvmAccountSessionLease
                            .activeCountForTest()==1,
                        "R102 production close released original World lease");
                    for(String flag:settlementExperimentFlags())
                        check(Boolean.getBoolean(flag),
                            "R102 close unexpectedly disabled LAB flag "+flag);
                    System.out.println(
                        (concurrentClose
                            ?"R104_WORLD_CONCURRENT_PRODUCTION_CLOSE_PASS"
                            :"R102_WORLD_POSTREADBACK_PRODUCTION_CLOSE_PASS")+
                        " stage="+target+
                        " fullyInspectedReceipt=true"+
                        " actualProductionClose=true"+
                        " closeThreadIndependent="+concurrentClose+
                        " originalFifoRevoked=true"+
                        " originalWorldLeaseHeld=true"+
                        " noLabResultOrRealFinalAck=true");
                    System.out.flush();
                    return;
                }
                if(shutdownAtCutpoint){
                    check(revocationDenied,
                        "R98 shutting down persistence did not veto LAB");
                    check(!last.ownerWitness
                            .originalFinalReservationStillPresentForLabTest(),
                        "R101 shutdown witness revived after settlement veto");
                    check(LocalJvmAccountSessionLease
                            .activeCountForTest()==1,
                        "R98 shutdown released original World lease");
                    check(Boolean.getBoolean(
                        LocalJvmAccountSessionLease
                            .CAPTURED_EPOCH_SETTLE_LAB_PROPERTY),
                        "R98 did not isolate shutdown from flag opt-out");
                    System.out.println("R98_WORLD_PERSISTENCE_SHUTDOWN_PASS"+
                        " stage="+target+
                        " productionClose="+fullCloseAtCutpoint+
                        " actualFifoReservationCaptured=true"+
                        " shutdownRevokedWitness=true"+
                        " originalSessionLockRetained=true"+
                        " noLabSuccessOrRealFinalAck=true");
                    System.out.flush();
                    return;
                }
                if(disableAtCutpoint||disableAfterReadback){
                    check(revocationDenied,
                        "R96 disabled settlement feature returned result");
                    check(!Boolean.getBoolean(experimentalFlag),
                        "R97 requested flag did not switch OFF");
                    for(String other:settlementExperimentFlags())
                        if(!other.equals(experimentalFlag))
                            check(Boolean.getBoolean(other),
                                "R97 unrelated LAB flag switched OFF");
                    check(LocalJvmAccountSessionLease
                            .activeCountForTest()==1,
                        "R96 disabled LAB released original session lease");
                    Path head=account.resolveSibling(
                        account.getFileName()+".g21385-ledger-head");
                    String headText=new String(Files.readAllBytes(head),
                        StandardCharsets.US_ASCII);
                    boolean moved=disableAfterReadback||
                        target.ordinal()>=LocalReusableAccountEpochLab
                            .Stage.HEAD_MOVED_BEFORE_DIR_FORCE.ordinal();
                    check(headText.contains("sequence="+(moved?3:2)+"\n")&&
                        headText.contains("phase="+
                            (moved?"CLOSED":"FINAL_PENDING")+"\n"),
                        "R96 disabled LAB persisted unexpected head");
                    System.out.println("R96_WORLD_SETTLEMENT_OPT_OUT_PASS"+
                        " stage="+target+
                        " afterReadback="+disableAfterReadback+
                        " disabledFlag="+experimentalFlag+
                        " originalFifoNotPublished=true"+
                        " originalOwnerLockRetained=true"+
                        " noSuccessfulLabResultOrRealAck=true");
                    System.out.flush();
                    return;
                }
                if(revokeAfterReadback){
                    check(revocationDenied,
                        "R95 original FIFO revoked after inspection "+
                            "but LAB result returned");
                    check(LocalJvmAccountSessionLease
                            .activeCountForTest()==1,
                        "R95 after-inspection revocation released lease");
                    Path head=account.resolveSibling(
                        account.getFileName()+".g21385-ledger-head");
                    String bytes=new String(Files.readAllBytes(head),
                        StandardCharsets.US_ASCII);
                    check(bytes.contains("sequence=3\n")&&
                        bytes.contains("phase=CLOSED\n"),
                        "R95 complete LAB receipt missing CLOSED head");
                    System.out.println(
                        "R95_WORLD_POSTREADBACK_REVOCATION_VETO_PASS"+
                        " allJournalReadbacksFinished=true"+
                        " originalFifoRevokedAfterInspect=true"+
                        " labResultWithheld=true"+
                        " originalSessionLockHeld=true"+
                        " noRealFinalACKOrRecovery=true");
                    System.out.flush();
                    return;
                }
                if(revokeAtCutpoint){
                    check(revocationDenied,
                        "R93 force stage did not veto revoked FIFO slot "+
                            target);
                    check(LocalJvmAccountSessionLease
                            .activeCountForTest()==1,
                        "R93 lab force released original World lease");
                    Path head=account.resolveSibling(
                        account.getFileName()+".g21385-ledger-head");
                    String bytes=new String(Files.readAllBytes(head),
                        StandardCharsets.US_ASCII);
                    boolean headMoved=target.ordinal()>=
                        LocalReusableAccountEpochLab.Stage
                            .HEAD_MOVED_BEFORE_DIR_FORCE.ordinal();
                    check(bytes.contains("sequence="+(headMoved?3:2)+
                            "\n")&&
                        bytes.contains("phase="+
                            (headMoved?"CLOSED":"FINAL_PENDING")+"\n"),
                        "R93 aborted force persisted wrong HEAD "+target);
                    System.out.println("R92_WORLD_FORCE_REVOCATION_VETO_PASS"+
                        " stage="+target+
                        " originalFifoAbortedAtForcedStage=true"+
                        " headMoved="+headMoved+
                        " noRealFinalACKOrGrant=true"+
                        " originalSessionLockStillHeld=true");
                    System.out.flush();
                    return;
                }
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
        settlementFreshProbe(root,stage,false);
    }

    private static int r109OwnedWorldTemps(Path account)
        throws IOException{
        int count=0;
        try(java.nio.file.DirectoryStream<Path> entries=
                Files.newDirectoryStream(account.getParent(),
                    account.getFileName().toString()+
                        ".g21170-world-*.tmp")){
            for(Path ignored:entries)count++;
        }
        return count;
    }

    /**
     * R106: prove default-OFF legacy and TCP admission denial while
     * the ORIGINAL authenticated World JVM is still alive with its
     * original OS account lock. Reusing the post-kill probe as-is
     * would block trying to reclaim that lock before owner exit.
     */
    private static void settlementFreshProbe(
        Path root,LocalReusableAccountEpochLab.Stage stage,
        boolean originalWorldStillAlive
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
        // R108: a second, default-OFF JVM must not be able to
        // overwrite this genuine World account through the separate
        // DIRECT repository save path. This check is intentionally
        // independent of the OS session lock: even after the
        // original JVM is killed, retained LAB evidence remains a
        // permanent negative writer veto, before legacy temp I/O.
        byte[] oldAccount=Files.readAllBytes(account);
        Path rawTemp=account.resolveSibling(
            account.getFileName().toString()+".tmp");
        boolean rawTempExisted=Files.exists(
            rawTemp,LinkOption.NOFOLLOW_LINKS);
        check(!rawTempExisted,
            "R108 test baseline contains preexisting RAW temp");
        boolean rawWriteDenied=false;
        try{
            repository.save(PlayerSnapshotCodec.capture(
                USER,new WorldPlayer()));
        }catch(IOException rejected){
            rawWriteDenied=contains(rejected,
                "R63_REUSABLE_LAB_EVIDENCE_PRESENT_NO_LEGACY_ADMISSION");
        }
        check(rawWriteDenied,
            "R108 default-OFF direct repository save bypassed LAB veto");
        check(!Files.exists(rawTemp,LinkOption.NOFOLLOW_LINKS),
            "R108 rejected RAW save allocated legacy temp");
        check(Arrays.equals(oldAccount,Files.readAllBytes(account)),
            "R108 rejected RAW save changed original account bytes");
        // R109: the direct guarded World writer is also callable
        // independently of a currently admitted World save task.
        // Neither successful OS-lock reacquisition after a crash
        // nor default-OFF producer flags can authorize writing
        // over an account whose detached LAB sidecars persist.
        int worldTempsBefore=r109OwnedWorldTemps(account);
        boolean worldWriteDenied=false;
        try{
            repository.saveForWorld(PlayerSnapshotCodec.capture(
                USER,new WorldPlayer()));
        }catch(IOException rejected){
            worldWriteDenied=contains(rejected,
                "R63_REUSABLE_LAB_EVIDENCE_PRESENT_NO_LEGACY_ADMISSION");
        }
        check(worldWriteDenied,
            "R109 direct World save bypassed retained LAB evidence");
        check(r109OwnedWorldTemps(account)==worldTempsBefore,
            "R109 rejected World save changed temp-file inventory");
        check(Arrays.equals(oldAccount,Files.readAllBytes(account)),
            "R109 rejected World save replaced legacy account");
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
        // R107: test the actual cooperating OS session lock in
        // this SECOND JVM, independent of LAB's negative evidence.
        // tryLock is nonblocking; never wait for the original owner.
        // A successful handle here while the original World is live
        // is a test failure, not permission to admit/recover anyone.
        System.setProperty(
            LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        System.setProperty(
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,"true");
        if(originalWorldStillAlive){
            boolean heldByOriginal=false;
            try(LocalCrossJvmAccountSessionLock.Handle unexpected=
                    LocalCrossJvmAccountSessionLock.acquire(account)){
                unexpected.requireOriginalLockNameStillPresent();
            }catch(IOException busy){
                heldByOriginal=contains(
                    busy,"G21.185 ACCOUNT_SESSION_LOCK_BUSY");
            }
            check(heldByOriginal,
                "R107 second JVM acquired original World OS lock "+
                stage);
        }else{
            // R107: original JVM exited, so the kernel must permit
            // the independent JVM to reacquire the stable lock file.
            // This is NOT authorization to load the quarantined
            // account or clear its negative sidecar evidence.
            try(LocalCrossJvmAccountSessionLock.Handle reclaimed=
                    LocalCrossJvmAccountSessionLock.acquire(account)){
                reclaimed.requireOriginalLockNameStillPresent();
            }
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
        System.out.println(
            (originalWorldStillAlive
                ?"R106_LIVE_OVERLAP_FRESH_JVM_NEGATIVE_PASS"
                :"R83_FRESH_JVM_SETTLEMENT_NEGATIVE_PASS")+
            " stage="+stage+
            " fullReceipt="+
                (stage==LocalReusableAccountEpochLab.Stage.RECEIPT_FORCED)+
            " originalWorldStillAlive="+originalWorldStillAlive+
            " lockReleasedAfterKill="+(!originalWorldStillAlive)+
            " r107SecondJvmOsLockBusyWhileOriginalAlive="+
                originalWorldStillAlive+
            " r107OsLockReacquiredAfterOriginalExit="+
                (!originalWorldStillAlive)+
            " r108DirectRawSaveDeniedBeforeTemp=true"+
            " r109DirectWorldSaveDeniedBeforeTemp=true"+
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
            // R106: test an independent default-OFF JVM while the
            // first authenticated World remains actively parked at
            // the forced filesystem boundary with its original lock.
            // Probe both pending and moved HEAD and a forced receipt.
            boolean liveOverlapStage=
                stage==LocalReusableAccountEpochLab.Stage.SNAPSHOT_FORCED||
                stage==LocalReusableAccountEpochLab.Stage
                    .HEAD_MOVED_BEFORE_DIR_FORCE||
                stage==LocalReusableAccountEpochLab.Stage.RECEIPT_FORCED;
            if(liveOverlapStage){
                Path overlapLog=root.resolve("live-overlap-fresh.log");
                ProcessBuilder overlapping=new ProcessBuilder(
                    javaExe(),"-cp",System.getProperty("java.class.path"),
                    G21399RealCapturedEpochGenesisRegressionTest
                        .class.getName(),
                    "--settlement-overlap-fresh",
                    root.toString(),stage.name());
                overlapping.redirectErrorStream(true);
                overlapping.redirectOutput(overlapLog.toFile());
                Process secondJvm=overlapping.start();
                if(!secondJvm.waitFor(35,TimeUnit.SECONDS)){
                    secondJvm.destroyForcibly();
                    secondJvm.waitFor(10,TimeUnit.SECONDS);
                    throw new AssertionError(
                        "R106 live overlapping JVM probe timed out "+stage);
                }
                String overlapTrace=new String(
                    Files.readAllBytes(overlapLog),StandardCharsets.UTF_8);
                // R106.1: the child prints its bounded fullReceipt
                // field BETWEEN stage and originalWorldStillAlive.
                // Match each independently, along with the explicit
                // direct/TCP denial and zero-exit proof. Requiring
                // those fields to be adjacent is not a safety check.
                check(secondJvm.exitValue()==0&&
                    overlapTrace.contains(
                        "R106_LIVE_OVERLAP_FRESH_JVM_NEGATIVE_PASS"+
                        " stage="+stage+" fullReceipt="+
                        (stage==LocalReusableAccountEpochLab.Stage
                            .RECEIPT_FORCED))&&
                    overlapTrace.contains(" originalWorldStillAlive=true")&&
                    overlapTrace.contains(
                        " r107SecondJvmOsLockBusyWhileOriginalAlive=true")&&
                    overlapTrace.contains(
                        " r108DirectRawSaveDeniedBeforeTemp=true")&&
                    overlapTrace.contains(
                        " r109DirectWorldSaveDeniedBeforeTemp=true")&&
                    overlapTrace.contains(
                        " freshRepositoryDirectAndTcpDenied=true")&&
                    overlapTrace.contains(" noFinalAckOrRecovery=true"),
                    "R106 live second-JVM admission or trace failed "+
                    stage+" "+overlapTrace);
                check(running.isAlive()&&
                    Files.exists(ready,LinkOption.NOFOLLOW_LINKS),
                    "R106 original World exited during live overlap "+stage);
                check(Arrays.equals(baseline,Files.readAllBytes(account)),
                    "R106 live overlap changed real legacy account "+stage);
                System.out.println(
                    "R106_LIVE_TWO_JVM_ADMISSION_VETO_PASS"+
                    " stage="+stage+
                    " originalAuthenticatedWorldAlive=true"+
                    " originalOsLockStillHeldByWorld=true"+
                    " independentJvmTryLockDenied=true"+
                    " secondJvmRawSaveDeniedBeforeTemp=true"+
                    " secondJvmWorldSaveDeniedBeforeTemp=true"+
                    " secondJvmLegacyAndTcpDenied=true"+
                    " oldAccountBytesUnchanged=true"+
                    " noReplayOrGrant=true");
            }
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
                "R83_FRESH_JVM_SETTLEMENT_NEGATIVE_PASS")&&
                output.contains(
                    " r107OsLockReacquiredAfterOriginalExit=true")&&
                output.contains(
                    " r108DirectRawSaveDeniedBeforeTemp=true")&&
                output.contains(
                    " r109DirectWorldSaveDeniedBeforeTemp=true")&&
                output.contains(
                    " freshRepositoryDirectAndTcpDenied=true"),
                "R107 fresh JVM did not reacquire OS lock and "+
                "continue denying old account admission: "+output);
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

    /**
     * R92: an authenticated World aborts its exact original final
     * FIFO at the first forced settlement stage. A fresh independent
     * JVM must still refuse all LAB evidence.
     */
    private static void runRevokedSettlement(
        LocalReusableAccountEpochLab.Stage stage
    )throws Exception{
        runRevokedSettlement(stage,false);
    }

    private static void runRevokedSettlement(
        LocalReusableAccountEpochLab.Stage stage,boolean afterReadback
    )throws Exception{
        runRevokedSettlement(stage,afterReadback,false);
    }

    private static void runRevokedSettlement(
        LocalReusableAccountEpochLab.Stage stage,boolean afterReadback,
        boolean disableLab
    )throws Exception{
        runRevokedSettlement(stage,afterReadback,
            disableLab
                ?LocalJvmAccountSessionLease.CAPTURED_EPOCH_SETTLE_LAB_PROPERTY
                :null);
    }

    private static void runRevokedSettlement(
        LocalReusableAccountEpochLab.Stage stage,boolean afterReadback,
        String disabledFlag
    )throws Exception{
        runRevokedSettlement(stage,afterReadback,disabledFlag,false);
    }

    private static void runRevokedSettlement(
        LocalReusableAccountEpochLab.Stage stage,boolean afterReadback,
        String disabledFlag,boolean shutdownAtCutpoint
    )throws Exception{
        runRevokedSettlement(stage,afterReadback,disabledFlag,
            shutdownAtCutpoint,false);
    }

    private static void runRevokedSettlement(
        LocalReusableAccountEpochLab.Stage stage,boolean afterReadback,
        String disabledFlag,boolean shutdownAtCutpoint,
        boolean fullCloseAtCutpoint
    )throws Exception{
        runRevokedSettlement(stage,afterReadback,disabledFlag,
            shutdownAtCutpoint,fullCloseAtCutpoint,false);
    }

    private static void runRevokedSettlement(
        LocalReusableAccountEpochLab.Stage stage,boolean afterReadback,
        String disabledFlag,boolean shutdownAtCutpoint,
        boolean fullCloseAtCutpoint,boolean closeAfterReadback
    )throws Exception{
        runRevokedSettlement(stage,afterReadback,disabledFlag,
            shutdownAtCutpoint,fullCloseAtCutpoint,closeAfterReadback,false);
    }

    private static void runRevokedSettlement(
        LocalReusableAccountEpochLab.Stage stage,boolean afterReadback,
        String disabledFlag,boolean shutdownAtCutpoint,
        boolean fullCloseAtCutpoint,boolean closeAfterReadback,
        boolean closeAfterFinalRead
    )throws Exception{
        runRevokedSettlement(stage,afterReadback,disabledFlag,
            shutdownAtCutpoint,fullCloseAtCutpoint,closeAfterReadback,
            closeAfterFinalRead,false);
    }

    private static void runRevokedSettlement(
        LocalReusableAccountEpochLab.Stage stage,boolean afterReadback,
        String disabledFlag,boolean shutdownAtCutpoint,
        boolean fullCloseAtCutpoint,boolean closeAfterReadback,
        boolean closeAfterFinalRead,boolean concurrentClose
    )throws Exception{
        if(concurrentClose&&
           ((!closeAfterReadback&&!closeAfterFinalRead)||!afterReadback||
            disabledFlag!=null||shutdownAtCutpoint||fullCloseAtCutpoint||
            stage!=LocalReusableAccountEpochLab.Stage.RECEIPT_FORCED))
            throw new IllegalArgumentException(
                "R105_CONCURRENT_CLOSE_REQUIRES_EXCLUSIVE_LATE_MODE");
        if(closeAfterFinalRead&&
            (!afterReadback||disabledFlag!=null||shutdownAtCutpoint||
             fullCloseAtCutpoint||closeAfterReadback||
             stage!=LocalReusableAccountEpochLab.Stage.RECEIPT_FORCED))
            throw new IllegalArgumentException(
                "R103_LATE_CLOSE_REQUIRES_EXCLUSIVE_RECEIPT_STAGE");
        if(closeAfterReadback&&
            (!afterReadback||disabledFlag!=null||shutdownAtCutpoint||
             fullCloseAtCutpoint||
             stage!=LocalReusableAccountEpochLab.Stage.RECEIPT_FORCED))
            throw new IllegalArgumentException(
                "R102_POSTREADBACK_CLOSE_REQUIRES_EXCLUSIVE_RECEIPT_STAGE");
        if(fullCloseAtCutpoint&&!shutdownAtCutpoint)
            throw new IllegalArgumentException(
                "R99_PRODUCTION_CLOSE_REQUIRES_SHUTDOWN_MODE");
        if(shutdownAtCutpoint&&(disabledFlag!=null||afterReadback))
            throw new IllegalArgumentException(
                "R98_SHUTDOWN_TEST_REQUIRES_FORCE_STAGE");
        boolean disableLab=disabledFlag!=null;
        if(disableLab)
            checkedSettlementExperimentFlag(disabledFlag);
        if(afterReadback&&stage!=
                LocalReusableAccountEpochLab.Stage.RECEIPT_FORCED)
            throw new IllegalArgumentException(
                "R95_REQUIRES_RECEIPT_FORCED");
        Path root=Files.createTempDirectory(
            (concurrentClose&&closeAfterFinalRead
                ?"r105-concurrent-postfinal-":
             concurrentClose?"r104-concurrent-close-":
             closeAfterFinalRead?"r103-postfinal-close-":
            closeAfterReadback?"r102-postreadback-close-":
            fullCloseAtCutpoint?"r99-production-close-":
            shutdownAtCutpoint?"r98-shutdown-":
            disableLab?"r96-opt-out-":
                afterReadback?"r95-post-inspection-":"r93-revoked-")+
            stage.name().toLowerCase(java.util.Locale.ROOT)+"-");
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
        String[] previous=new String[flags.length];
        for(int i=0;i<flags.length;i++)
            previous[i]=System.getProperty(flags[i]);
        try{
            configure(root,false);
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            repo.save(PlayerSnapshotCodec.capture(USER,new WorldPlayer()));
            Path account=LocalAccountProfiles.accountFile(USER);
            byte[] baseline=Files.readAllBytes(account);
            Path holderLog=root.resolve("revoked-holder.log");
            ProcessBuilder holder=new ProcessBuilder(
                javaExe(),"-cp",System.getProperty("java.class.path"),
                G21399RealCapturedEpochGenesisRegressionTest.class.getName(),
                concurrentClose&&closeAfterFinalRead
                    ?"--settlement-concurrent-close-post-final-read"
                    :concurrentClose
                    ?"--settlement-concurrent-close-post-readback"
                    :closeAfterFinalRead
                    ?"--settlement-production-close-post-final-read"
                    :closeAfterReadback
                    ?"--settlement-production-close-post-readback"
                    :shutdownAtCutpoint
                    ?fullCloseAtCutpoint
                        ?"--settlement-production-close-at-stage"
                        :"--settlement-shutdown-at-stage"
                    :disableLab
                        ?afterReadback
                            ?"--settlement-opt-out-post-readback"
                            :"--settlement-opt-out-stage"
                        :afterReadback
                        ?"--settlement-post-readback-revoked"
                        :"--settlement-revoked",
                root.toString(),stage.name());
            if(disableLab)holder.command().add(disabledFlag);
            holder.redirectErrorStream(true);
            holder.redirectOutput(holderLog.toFile());
            Process p=holder.start();
            if(!p.waitFor(35,TimeUnit.SECONDS)){
                p.destroyForcibly();
                p.waitFor(10,TimeUnit.SECONDS);
                throw new AssertionError("R92 aborted World holder timed out");
            }
            String trace=new String(Files.readAllBytes(holderLog),
                StandardCharsets.UTF_8);
            check(p.exitValue()==0&&trace.contains(
                concurrentClose&&closeAfterFinalRead
                    ?"R105_WORLD_CONCURRENT_POST_FINAL_READ_CLOSE_PASS stage="+stage
                    :concurrentClose
                    ?"R104_WORLD_CONCURRENT_PRODUCTION_CLOSE_PASS stage="+stage
                    :closeAfterFinalRead
                    ?"R103_WORLD_POST_FINAL_READ_CLOSE_PASS stage="+stage
                    :closeAfterReadback
                    ?"R102_WORLD_POSTREADBACK_PRODUCTION_CLOSE_PASS stage="+stage
                    :shutdownAtCutpoint
                    ?"R98_WORLD_PERSISTENCE_SHUTDOWN_PASS stage="+stage+
                        " productionClose="+fullCloseAtCutpoint
                    :disableLab
                        ?"R96_WORLD_SETTLEMENT_OPT_OUT_PASS stage="+stage+
                        " afterReadback="+afterReadback+
                        " disabledFlag="+disabledFlag
                    :afterReadback
                        ?"R95_WORLD_POSTREADBACK_REVOCATION_VETO_PASS"
                        :"R92_WORLD_FORCE_REVOCATION_VETO_PASS stage="+stage),
                "R93/R95/R96/R98 original World settlement did not fail closed: "+
                    stage+" "+trace);
            check(Arrays.equals(baseline,Files.readAllBytes(account)),
                "R92 aborted settlement rewrote legacy account");
            Path head=account.resolveSibling(
                account.getFileName()+".g21385-ledger-head");
            String headText=new String(Files.readAllBytes(head),
                StandardCharsets.US_ASCII);
            boolean headMoved=stage.ordinal()>=
                LocalReusableAccountEpochLab.Stage
                    .HEAD_MOVED_BEFORE_DIR_FORCE.ordinal();
            check(headText.contains("sequence="+(headMoved?3:2)+"\n")&&
                headText.contains("phase="+
                    (headMoved?"CLOSED":"FINAL_PENDING")+"\n"),
                "R93 revoked stage persisted unexpected HEAD "+stage);
            Path intent=account.resolveSibling(
                account.getFileName()+".g21386-intent-0003");
            Path receipt=account.resolveSibling(
                account.getFileName()+".g21386-receipt-0003");
            boolean intentForced=stage.ordinal()>=
                LocalReusableAccountEpochLab.Stage
                    .INTENT_FORCED.ordinal();
            boolean receiptForced=stage==
                LocalReusableAccountEpochLab.Stage.RECEIPT_FORCED;
            check(Files.exists(intent,LinkOption.NOFOLLOW_LINKS)==
                    intentForced&&
                Files.exists(receipt,LinkOption.NOFOLLOW_LINKS)==
                    receiptForced,
                "R93 revoked-stage journal inventory unexpected "+stage);
            // R94: revocation and hard-kill must produce the same
            // exact immutable sidecar inventory at each cutpoint:
            // one newly forced future image, a candidate archive
            // only after its own force, and a temporary HEAD only
            // while the temp has not yet been atomically renamed.
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
                "R94 revoked stage lost exact future image "+stage);
            check(archives==
                    (stage.ordinal()>=LocalReusableAccountEpochLab.Stage
                        .ARCHIVED_HEAD_FORCED.ordinal()?1:0),
                "R94 revoked stage archive inventory mismatch "+stage);
            check(stagedHeads==
                    (stage==LocalReusableAccountEpochLab.Stage
                        .HEAD_TEMP_FORCED?1:0),
                "R94 revoked stage temp inventory mismatch "+stage);
            boolean negative=false;
            try{
                LocalReusableLabEvidenceNegativeGate
                    .requireNoLegacyCollision(account);
            }catch(IOException invalid){negative=contains(invalid,NEGATIVE);}
            check(negative,"R92 partial LAB evidence allowed old account");
            Path freshLog=root.resolve("revoked-fresh.log");
            ProcessBuilder probe=new ProcessBuilder(
                javaExe(),"-cp",System.getProperty("java.class.path"),
                G21399RealCapturedEpochGenesisRegressionTest.class.getName(),
                "--settlement-fresh",root.toString(),
                stage.name());
            probe.redirectErrorStream(true);
            probe.redirectOutput(freshLog.toFile());
            Process fresh=probe.start();
            if(!fresh.waitFor(35,TimeUnit.SECONDS)){
                fresh.destroyForcibly();
                fresh.waitFor(10,TimeUnit.SECONDS);
                throw new AssertionError("R92 fresh denial probe timed out");
            }
            String result=new String(Files.readAllBytes(freshLog),
                StandardCharsets.UTF_8);
            check(fresh.exitValue()==0&&result.contains(
                "R83_FRESH_JVM_SETTLEMENT_NEGATIVE_PASS"),
                "R92 fresh JVM did not refuse partial settlement: "+result);
            System.out.println(
                (concurrentClose&&closeAfterFinalRead
                    ?"R105_CONCURRENT_POST_FINAL_READ_FRESH_JVM_PASS"
                    :concurrentClose
                    ?"R104_CONCURRENT_CLOSE_FRESH_JVM_PASS"
                    :closeAfterFinalRead
                    ?"R103_POST_FINAL_READ_CLOSE_FRESH_JVM_PASS"
                    :closeAfterReadback
                    ?"R102_POSTREADBACK_CLOSE_FRESH_JVM_PASS"
                    :fullCloseAtCutpoint
                    ?"R99_PRODUCTION_CLOSE_FRESH_JVM_PASS"
                    :shutdownAtCutpoint
                        ?"R98_PERSISTENCE_SHUTDOWN_FRESH_JVM_PASS"
                        :disableLab
                        ?"R96_SETTLEMENT_OPT_OUT_FRESH_JVM_PASS"
                        :afterReadback
                        ?"R95_POSTREADBACK_REVOKED_FRESH_JVM_PASS"
                        :"R93_REVOKED_SETTLEMENT_FRESH_JVM_PASS")+
                " stage="+stage+
                " originalWorldAndFifo=true"+
                " reservationRevokedAfterInspect="+
                    (afterReadback&&!disableLab)+
                " experimentalOptOutDuringTransition="+disableLab+
                " persistenceExecutorShutdown="+shutdownAtCutpoint+
                " productionClose="+fullCloseAtCutpoint+
                " postReadbackProductionClose="+closeAfterReadback+
                " closeAfterFinalRead="+closeAfterFinalRead+
                " concurrentClose="+concurrentClose+
                " disabledFlag="+(disableLab?disabledFlag:"none")+
                " exactSevenStageInventory=true"+
                " headMoved="+headMoved+
                " receiptForced="+receiptForced+
                " oldAccountBytesUnchanged=true"+
                " freshJvmTcpAndLegacyDenied=true"+
                (closeAfterFinalRead
                    ?" labOnlyObservationNotAnACK=true"
                    :" noSettlementResultOrRealFinalACK=true"));
        }finally{
            for(int i=0;i<flags.length;i++){
                if(previous[i]==null)System.clearProperty(flags[i]);
                else System.setProperty(flags[i],previous[i]);
            }
            try(Stream<Path> entries=Files.walk(root)){
                for(Path entry:entries.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(entry);
            }
        }
    }


    /**
     * R110: all four detached LAB namespaces are negative even if a
     * foreign actor introduces one AFTER the first two World writer
     * checks inside the publication callback but BEFORE atomic move.
     * No live recovery, ACK, unlink or data migration is performed.
     */
    private static void runLateLabEvidenceWorldWriteMatrix()
        throws Exception{
        String[] namespaces={
            ".g21385-r110-late-orphan",
            ".g21386-r110-late-intent",
            ".g21387-r110-late-archive",
            ".g21378-object-r110-late"
        };
        String[] flagNames={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_GENESIS_LAB_PROPERTY,
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_CLAIM_LAB_PROPERTY,
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_PREPARE_LAB_PROPERTY,
            LocalJvmAccountSessionLease.CAPTURED_EPOCH_SETTLE_LAB_PROPERTY
        };
        String[] previous=new String[flagNames.length];
        for(int i=0;i<flagNames.length;i++)
            previous[i]=System.getProperty(flagNames[i]);
        for(String namespace:namespaces){
            Path root=Files.createTempDirectory("r110-late-world-");
            try{
                configure(root,false);
                FilePlayerRepository original=new FilePlayerRepository(
                    LocalAccountProfiles::accountFile);
                original.save(PlayerSnapshotCodec.capture(
                    USER,new WorldPlayer()));
                Path account=LocalAccountProfiles.accountFile(USER);
                byte[] oldBytes=Files.readAllBytes(account);
                int oldTemps=r109OwnedWorldTemps(account);
                Path sidecar=account.resolveSibling(
                    account.getFileName().toString()+namespace);
                java.util.concurrent.atomic.AtomicInteger injections=
                    new java.util.concurrent.atomic.AtomicInteger();
                FilePlayerRepository raced=new FilePlayerRepository(
                    LocalAccountProfiles::accountFile,
                    name->{},
                    name->{
                        check(USER.equals(name),
                            "R110 callback changed selected account");
                        Files.write(sidecar,new byte[]{1},
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE);
                        injections.incrementAndGet();
                    });
                boolean rejected=false;
                try{
                    raced.saveForWorld(PlayerSnapshotCodec.capture(
                        USER,new WorldPlayer()));
                }catch(IOException denied){
                    rejected=contains(denied,NEGATIVE);
                }
                check(rejected,
                    "R110 late LAB sidecar crossed World move "+namespace);
                check(injections.get()==1&&
                    Files.exists(sidecar,LinkOption.NOFOLLOW_LINKS),
                    "R110 injection not reached or sidecar lost "+namespace);
                check(Arrays.equals(oldBytes,Files.readAllBytes(account)),
                    "R110 late LAB sidecar replaced legacy bytes "+namespace);
                check(r109OwnedWorldTemps(account)==oldTemps,
                    "R110 rejected World write leaked owned temp "+namespace);
                boolean readDenied=false;
                try{original.loadForWorldSession(USER);}
                catch(IOException denied){
                    readDenied=contains(denied,NEGATIVE);
                }
                check(readDenied,
                    "R110 retained late LAB sidecar allowed login "+
                    namespace);
                System.out.println(
                    "R110_LATE_LAB_EVIDENCE_WORLD_MOVE_VETO_PASS"+
                    " namespace="+namespace+
                    " injectedInsidePublicationLock=true"+
                    " oldAccountUnchanged=true"+
                    " ownedWorldTempCleaned=true"+
                    " loginRemainsDenied=true"+
                    " noACKOrRecovery=true");
            }finally{
                for(int i=0;i<flagNames.length;i++){
                    if(previous[i]==null)
                        System.clearProperty(flagNames[i]);
                    else System.setProperty(flagNames[i],previous[i]);
                }
                try(Stream<Path> entries=Files.walk(root)){
                    for(Path file:entries.sorted(
                            Comparator.reverseOrder()).toArray(Path[]::new))
                        Files.deleteIfExists(file);
                }
            }
        }
    }


    /**
     * R111: a default-OFF RAW writer begins with a healthy account,
     * serializes its legacy .tmp, then a foreign actor introduces
     * one of four detached LAB sidecar namespaces in the existing
     * beforeRawReplace TEST hook. The late negative gate must veto
     * before replacing real account bytes and must remove only its
     * own abandoned RAW temp, never the evidence sidecar.
     */
    private static void runLateLabEvidenceRawWriteMatrix()
        throws Exception{
        String[] namespaces={
            ".g21385-r111-late-orphan",
            ".g21386-r111-late-intent",
            ".g21387-r111-late-archive",
            ".g21378-object-r111-late"
        };
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
        for(int i=0;i<flags.length;i++)
            saved[i]=System.getProperty(flags[i]);
        for(String namespace:namespaces){
            Path root=Files.createTempDirectory("r111-late-raw-");
            try{
                configure(root,false);
                FilePlayerRepository normal=new FilePlayerRepository(
                    LocalAccountProfiles::accountFile);
                normal.save(PlayerSnapshotCodec.capture(
                    USER,new WorldPlayer()));
                Path account=LocalAccountProfiles.accountFile(USER);
                byte[] oldBytes=Files.readAllBytes(account);
                Path rawTmp=account.resolveSibling(
                    account.getFileName().toString()+".tmp");
                check(!Files.exists(rawTmp,LinkOption.NOFOLLOW_LINKS),
                    "R111 baseline has preexisting RAW temp");
                Path sidecar=account.resolveSibling(
                    account.getFileName().toString()+namespace);
                java.util.concurrent.atomic.AtomicInteger injections=
                    new java.util.concurrent.atomic.AtomicInteger();
                FilePlayerRepository raced=new FilePlayerRepository(
                    LocalAccountProfiles::accountFile,
                    name->{},name->{},name->{},name->{},
                    name->{
                        check(USER.equals(name),
                            "R111 RAW callback account identity changed");
                        check(Files.exists(rawTmp,LinkOption.NOFOLLOW_LINKS),
                            "R111 RAW callback did not reach serialized temp");
                        Files.write(sidecar,new byte[]{1},
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE);
                        injections.incrementAndGet();
                    });
                boolean denied=false;
                try{
                    raced.save(PlayerSnapshotCodec.capture(
                        USER,new WorldPlayer()));
                }catch(IOException rejected){
                    denied=contains(rejected,NEGATIVE);
                }
                check(denied,
                    "R111 late LAB sidecar passed RAW move "+namespace);
                check(injections.get()==1&&
                    Files.exists(sidecar,LinkOption.NOFOLLOW_LINKS),
                    "R111 injected LAB evidence missing "+namespace);
                check(Arrays.equals(oldBytes,Files.readAllBytes(account)),
                    "R111 RAW save replaced old account "+namespace);
                check(!Files.exists(rawTmp,LinkOption.NOFOLLOW_LINKS),
                    "R111 RAW save leaked discarded temp "+namespace);
                boolean loadDenied=false;
                try{normal.loadForWorldSession(USER);}
                catch(IOException rejected){
                    loadDenied=contains(rejected,NEGATIVE);
                }
                check(loadDenied,
                    "R111 late RAW evidence was not admission-negative "+
                    namespace);
                System.out.println(
                    "R111_LATE_LAB_EVIDENCE_RAW_MOVE_VETO_PASS"+
                    " namespace="+namespace+
                    " injectedAfterSerialization=true"+
                    " oldAccountBytesUnchanged=true"+
                    " abandonedRawTempCleaned=true"+
                    " negativeAdmissionPreserved=true"+
                    " noACKOrRecovery=true");
            }finally{
                for(int i=0;i<flags.length;i++){
                    if(saved[i]==null)System.clearProperty(flags[i]);
                    else System.setProperty(flags[i],saved[i]);
                }
                try(Stream<Path> entries=Files.walk(root)){
                    for(Path file:entries.sorted(
                            Comparator.reverseOrder()).toArray(Path[]::new))
                        Files.deleteIfExists(file);
                }
            }
        }
    }


    /**
     * R112: a detached LAB sidecar can appear AFTER RAW account
     * replacement. The outcome is uncertain, NOT rolled back or
     * acknowledged. The exact negative namespace must quarantine the
     * replaced account without deleting the independent evidence.
     */
    private static void runPostMoveLabEvidenceRawMatrix()
        throws Exception{
        String[] namespaces={
            ".g21385-r112-after-move",
            ".g21386-r112-after-move",
            ".g21387-r112-after-move",
            ".g21378-object-r112-after-move"
        };
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
        for(int i=0;i<flags.length;i++)
            saved[i]=System.getProperty(flags[i]);
        for(String namespace:namespaces){
            Path root=Files.createTempDirectory("r112-postmove-raw-");
            try{
                configure(root,false);
                FilePlayerRepository original=new FilePlayerRepository(
                    LocalAccountProfiles::accountFile);
                original.save(PlayerSnapshotCodec.capture(
                    USER,new WorldPlayer()));
                Path account=LocalAccountProfiles.accountFile(USER);
                byte[] before=Files.readAllBytes(account);
                Path sidecar=account.resolveSibling(
                    account.getFileName().toString()+namespace);
                Path rawTemp=account.resolveSibling(
                    account.getFileName().toString()+".tmp");
                check(!Files.exists(rawTemp,LinkOption.NOFOLLOW_LINKS),
                    "R112 stale initial RAW temp");
                java.util.concurrent.atomic.AtomicInteger reached=
                    new java.util.concurrent.atomic.AtomicInteger();
                FilePlayerRepository raced=new FilePlayerRepository(
                    LocalAccountProfiles::accountFile,
                    name->{},name->{},name->{},name->{},name->{},
                    name->{},
                    name->{
                        check(USER.equals(name),
                            "R112 postmove identity drift");
                        check(Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                            "R112 account missing after actual RAW move");
                        Files.write(sidecar,new byte[]{1},
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE);
                        reached.incrementAndGet();
                    });
                WorldPlayer changed=new WorldPlayer();
                changed.snapshotExtensions().replaceNamespace(
                    "r112_postmove",
                    Collections.singletonMap("phase","NEW_UNCONFIRMED"));
                boolean denied=false;
                try{
                    raced.save(PlayerSnapshotCodec.capture(USER,changed));
                }catch(IOException exception){
                    denied=contains(exception,NEGATIVE);
                }
                check(denied,
                    "R112 postmove LAB evidence reported successful RAW save "+
                    namespace);
                check(reached.get()==1&&
                    Files.exists(sidecar,LinkOption.NOFOLLOW_LINKS),
                    "R112 postmove test injection not reached "+namespace);
                check(!Arrays.equals(before,Files.readAllBytes(account)),
                    "R112 RAW account unexpectedly unchanged after move "+
                    namespace);
                check(!Files.exists(rawTemp,LinkOption.NOFOLLOW_LINKS),
                    "R112 postmove RAW temp not moved "+namespace);
                boolean loadDenied=false;
                try{original.loadForWorldSession(USER);}
                catch(IOException exception){
                    loadDenied=contains(exception,NEGATIVE);
                }
                check(loadDenied,
                    "R112 postmove LAB evidence permitted stale relogin "+
                    namespace);
                System.out.println(
                    "R112_POSTMOVE_RAW_LAB_EVIDENCE_UNCERTAIN_PASS"+
                    " namespace="+namespace+
                    " actualRawAccountReplaced=true"+
                    " lateSidecarRetained=true"+
                    " saveReturnedFailure=true"+
                    " freshAdmissionDenied=true"+
                    " noRollbackOrACK=true");
            }finally{
                for(int i=0;i<flags.length;i++){
                    if(saved[i]==null)System.clearProperty(flags[i]);
                    else System.setProperty(flags[i],saved[i]);
                }
                try(Stream<Path> entries=Files.walk(root)){
                    for(Path file:entries.sorted(
                            Comparator.reverseOrder()).toArray(Path[]::new))
                        Files.deleteIfExists(file);
                }
            }
        }
    }


    /**
     * R113: a second, independent JVM publishes one LAB sidecar at the
     * exact RAW or guarded-World pre-replacement callback. The
     * original repository must observe the now-existing negative
     * evidence without relying on same-process test state.
     */
    private static void publishR113NegativeFromOtherJvm(
        Path account,Path sidecar
    )throws Exception{
        // R115: the former log name was based on the sidecar basename.
        // Opening that log itself published a SECOND .g213* negative
        // leaf before the independent process ran! Keep the trace
        // outside the account's LAB namespace and prove the account
        // has no negative sidecars before spawning the foreign writer.
        Path trace=sidecar.resolveSibling("r115-foreign-writer.log");
        check(!Files.exists(trace,LinkOption.NOFOLLOW_LINKS)&&
              !Files.exists(sidecar,LinkOption.NOFOLLOW_LINKS),
            "R115 preexisting child log or foreign sidecar");
        LocalReusableLabEvidenceNegativeGate
            .requireNoLegacyCollision(account);
        ProcessBuilder command=new ProcessBuilder(
            javaExe(),"-cp",System.getProperty("java.class.path"),
            G21399RealCapturedEpochGenesisRegressionTest.class.getName(),
            "--r113-create-negative",sidecar.toString());
        command.redirectErrorStream(true);
        command.redirectOutput(trace.toFile());
        Process child=command.start();
        if(!child.waitFor(20,TimeUnit.SECONDS)){
            child.destroyForcibly();
            child.waitFor(10,TimeUnit.SECONDS);
            throw new IOException("R113_FOREIGN_JVM_WRITER_TIMED_OUT");
        }
        String output=new String(Files.readAllBytes(trace),
            StandardCharsets.UTF_8);
        check(child.exitValue()==0&&output.contains(
            "R113_INDEPENDENT_JVM_NEGATIVE_SIDECAR_WRITTEN")&&
            Files.exists(sidecar,LinkOption.NOFOLLOW_LINKS),
            "R115 external JVM did not publish negative sidecar "+output);
        boolean veto=false;
        try{
            LocalReusableLabEvidenceNegativeGate
                .requireNoLegacyCollision(account);
        }catch(IOException denied){
            veto=contains(denied,NEGATIVE);
        }
        check(veto,
            "R115 foreign publication did not change LAB gate");
    }

    private static void runCrossJvmLateLabEvidenceMatrix()
        throws Exception{
        String[] namespaces={
            ".g21385-r113-foreign-",
            ".g21386-r113-foreign-",
            ".g21387-r113-foreign-",
            ".g21378-object-r113-foreign-"
        };
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
        for(int i=0;i<flags.length;i++)
            saved[i]=System.getProperty(flags[i]);
        for(boolean worldWriter:new boolean[]{false,true}){
            for(String namespace:namespaces){
                Path root=Files.createTempDirectory(
                    worldWriter?"r113-twojvm-world-":"r113-twojvm-raw-");
                try{
                    configure(root,false);
                    FilePlayerRepository initial=new FilePlayerRepository(
                        LocalAccountProfiles::accountFile);
                    initial.save(PlayerSnapshotCodec.capture(
                        USER,new WorldPlayer()));
                    Path account=LocalAccountProfiles.accountFile(USER);
                    byte[] previousBytes=Files.readAllBytes(account);
                    Path sidecar=account.resolveSibling(
                        account.getFileName().toString()+namespace+
                        (worldWriter?"world":"raw"));
                    Path rawTemp=account.resolveSibling(
                        account.getFileName().toString()+".tmp");
                    int ownedWorldTemps=r109OwnedWorldTemps(account);
                    check(!Files.exists(sidecar,LinkOption.NOFOLLOW_LINKS)&&
                        !Files.exists(rawTemp,LinkOption.NOFOLLOW_LINKS),
                        "R113 selected baseline unexpectedly dirty");
                    java.util.concurrent.atomic.AtomicInteger invoked=
                        new java.util.concurrent.atomic.AtomicInteger();
                    FilePlayerRepository.BeforeWorldReplace inject=name->{
                        check(USER.equals(name),"R113 account drift");
                        if(worldWriter)
                            check(r109OwnedWorldTemps(account)>
                                    ownedWorldTemps,
                                "R113 World temp not allocated at callback");
                        else
                            check(Files.exists(
                                    rawTemp,LinkOption.NOFOLLOW_LINKS),
                                "R113 RAW temp not allocated at callback");
                        try{
                            publishR113NegativeFromOtherJvm(
                                account,sidecar);
                        }catch(Exception failure){
                            throw new IOException(
                                "R113_CROSS_JVM_SIDECAR_PROCESS_FAILED",
                                failure);
                        }
                        invoked.incrementAndGet();
                    };
                    FilePlayerRepository raced=new FilePlayerRepository(
                        LocalAccountProfiles::accountFile,
                        name->{},
                        name->{if(worldWriter)inject.run(name);},
                        name->{},name->{},
                        name->{if(!worldWriter)inject.run(name);});
                    boolean rejected=false;
                    try{
                        PlayerSnapshot image=PlayerSnapshotCodec.capture(
                            USER,new WorldPlayer());
                        if(worldWriter)raced.saveForWorld(image);
                        else raced.save(image);
                    }catch(IOException denied){
                        rejected=contains(denied,NEGATIVE);
                    }
                    check(rejected&&invoked.get()==1,
                        "R113 cross-JVM sidecar failed to veto "+
                        (worldWriter?"World":"RAW")+" "+namespace);
                    check(Files.exists(sidecar,LinkOption.NOFOLLOW_LINKS)&&
                        Arrays.equals(previousBytes,Files.readAllBytes(account)),
                        "R113 other-JVM evidence lost or account replaced "+
                        namespace);
                    check(r109OwnedWorldTemps(account)==ownedWorldTemps&&
                        !Files.exists(rawTemp,LinkOption.NOFOLLOW_LINKS),
                        "R113 rejected writer leaked temp "+namespace);
                    boolean deniedAdmission=false;
                    try{initial.loadForWorldSession(USER);}
                    catch(IOException rejectedLoad){
                        deniedAdmission=contains(rejectedLoad,NEGATIVE);
                    }
                    check(deniedAdmission,
                        "R113 independent negative witness allowed login");
                    System.out.println(
                        "R113_TWO_JVM_LATE_WRITE_VETO_PASS"+
                        " mode="+(worldWriter?"WORLD":"RAW")+
                        " namespace="+namespace+
                        " secondProcessPublishedSidecar=true"+
                        " parentLogOutsideLabNamespace=true"+
                        " prechildLabGateClear=true"+
                        " postchildLabGateNegative=true"+
                        " originalAccountUnchanged=true"+
                        " writerTempCleaned=true"+
                        " freshWorldLoadDenied=true"+
                        " noRecoveryOrSaveACK=true");
                }finally{
                    for(int i=0;i<flags.length;i++){
                        if(saved[i]==null)System.clearProperty(flags[i]);
                        else System.setProperty(flags[i],saved[i]);
                    }
                    try(Stream<Path> entries=Files.walk(root)){
                        for(Path leaf:entries.sorted(
                                Comparator.reverseOrder()).toArray(Path[]::new))
                            Files.deleteIfExists(leaf);
                    }
                }
            }
        }
    }


    /**
     * R114: after non-forced guarded World ATOMIC_MOVE, injection of
     * detached LAB evidence cannot undo new account bytes. The exact
     * late evidence must turn normal save completion into uncertainty
     * and continue to prevent subsequent account admission.
     */
    private static void runPostMoveLabEvidenceWorldMatrix()
        throws Exception{
        String[] namespaces={
            ".g21385-r114-after-world-move",
            ".g21386-r114-after-world-move",
            ".g21387-r114-after-world-move",
            ".g21378-object-r114-after-world-move"
        };
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
        String[] originalFlags=new String[flags.length];
        for(int i=0;i<flags.length;i++)
            originalFlags[i]=System.getProperty(flags[i]);
        for(String namespace:namespaces){
            Path root=Files.createTempDirectory("r114-postmove-world-");
            try{
                configure(root,false);
                FilePlayerRepository initial=new FilePlayerRepository(
                    LocalAccountProfiles::accountFile);
                initial.save(PlayerSnapshotCodec.capture(
                    USER,new WorldPlayer()));
                Path account=LocalAccountProfiles.accountFile(USER);
                byte[] before=Files.readAllBytes(account);
                int oldWorldTemps=r109OwnedWorldTemps(account);
                Path sidecar=account.resolveSibling(
                    account.getFileName().toString()+namespace);
                java.util.concurrent.atomic.AtomicInteger reached=
                    new java.util.concurrent.atomic.AtomicInteger();
                FilePlayerRepository raced=new FilePlayerRepository(
                    LocalAccountProfiles::accountFile,
                    name->{},name->{},name->{},name->{},
                    name->{},name->{},name->{},
                    name->{
                        check(USER.equals(name),
                            "R114 postmove World identity changed");
                        check(Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                            "R114 postmove World account missing");
                        Files.write(sidecar,new byte[]{1},
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE);
                        reached.incrementAndGet();
                    });
                WorldPlayer changed=new WorldPlayer();
                changed.snapshotExtensions().replaceNamespace(
                    "r114_world",
                    Collections.singletonMap("phase","POSTMOVE_UNCERTAIN"));
                boolean denied=false;
                try{
                    raced.saveForWorld(PlayerSnapshotCodec.capture(
                        USER,changed));
                }catch(IOException failure){
                    denied=contains(failure,NEGATIVE);
                }
                check(denied&&reached.get()==1,
                    "R114 late LAB evidence reported World save success "+
                    namespace);
                check(Files.exists(sidecar,LinkOption.NOFOLLOW_LINKS)&&
                    !Arrays.equals(before,Files.readAllBytes(account)),
                    "R114 new World bytes missing or evidence lost "+
                    namespace);
                check(r109OwnedWorldTemps(account)==oldWorldTemps,
                    "R114 postmove World temp not consumed "+namespace);
                boolean loadDenied=false;
                try{initial.loadForWorldSession(USER);}
                catch(IOException failure){
                    loadDenied=contains(failure,NEGATIVE);
                }
                check(loadDenied,
                    "R114 postmove World evidence permitted relogin "+
                    namespace);
                System.out.println(
                    "R114_POSTMOVE_WORLD_LAB_EVIDENCE_UNCERTAIN_PASS"+
                    " namespace="+namespace+
                    " accountActuallyReplaced=true"+
                    " lateSidecarPreserved=true"+
                    " saveReturnedFailure=true"+
                    " laterWorldAdmissionDenied=true"+
                    " noRollbackOrSaveACK=true");
            }finally{
                for(int i=0;i<flags.length;i++){
                    if(originalFlags[i]==null)
                        System.clearProperty(flags[i]);
                    else System.setProperty(flags[i],originalFlags[i]);
                }
                try(Stream<Path> entries=Files.walk(root)){
                    for(Path entry:entries.sorted(
                            Comparator.reverseOrder()).toArray(Path[]::new))
                        Files.deleteIfExists(entry);
                }
            }
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length==2&&"--r113-create-negative".equals(args[0])){
            Path sidecar=Paths.get(args[1]);
            Files.write(sidecar,new byte[]{1},
                StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            System.out.println(
                "R113_INDEPENDENT_JVM_NEGATIVE_SIDECAR_WRITTEN");
            return;
        }
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
        if(args.length==3&&
                "--settlement-concurrent-close-post-final-read".equals(args[0])){
            settleKillHolder(Paths.get(args[1]),
                Paths.get(args[1]).resolve("unused-ready"),
                LocalReusableAccountEpochLab.Stage.valueOf(args[2]),
                false,false,false,false,
                LocalJvmAccountSessionLease.CAPTURED_EPOCH_SETTLE_LAB_PROPERTY,
                false,false,false,true,true);
            return;
        }
        if(args.length==3&&
                "--settlement-concurrent-close-post-readback".equals(args[0])){
            settleKillHolder(Paths.get(args[1]),
                Paths.get(args[1]).resolve("unused-ready"),
                LocalReusableAccountEpochLab.Stage.valueOf(args[2]),
                false,false,false,false,
                LocalJvmAccountSessionLease.CAPTURED_EPOCH_SETTLE_LAB_PROPERTY,
                false,false,true,false,true);
            return;
        }
        if(args.length==3&&
                "--settlement-production-close-post-final-read".equals(args[0])){
            settleKillHolder(Paths.get(args[1]),
                Paths.get(args[1]).resolve("unused-ready"),
                LocalReusableAccountEpochLab.Stage.valueOf(args[2]),
                false,false,false,false,
                LocalJvmAccountSessionLease.CAPTURED_EPOCH_SETTLE_LAB_PROPERTY,
                false,false,false,true);
            return;
        }
        if(args.length==3&&
                "--settlement-production-close-post-readback".equals(args[0])){
            settleKillHolder(Paths.get(args[1]),
                Paths.get(args[1]).resolve("unused-ready"),
                LocalReusableAccountEpochLab.Stage.valueOf(args[2]),
                false,false,false,false,
                LocalJvmAccountSessionLease.CAPTURED_EPOCH_SETTLE_LAB_PROPERTY,
                false,false,true);
            return;
        }
        if(args.length==3&&
                "--settlement-production-close-at-stage".equals(args[0])){
            settleKillHolder(Paths.get(args[1]),
                Paths.get(args[1]).resolve("unused-ready"),
                LocalReusableAccountEpochLab.Stage.valueOf(args[2]),
                false,false,false,false,
                LocalJvmAccountSessionLease.CAPTURED_EPOCH_SETTLE_LAB_PROPERTY,
                true,true);
            return;
        }
        if(args.length==3&&
                "--settlement-shutdown-at-stage".equals(args[0])){
            settleKillHolder(Paths.get(args[1]),
                Paths.get(args[1]).resolve("unused-ready"),
                LocalReusableAccountEpochLab.Stage.valueOf(args[2]),
                false,false,false,false,
                LocalJvmAccountSessionLease.CAPTURED_EPOCH_SETTLE_LAB_PROPERTY,
                true);
            return;
        }
        if((args.length==3||args.length==4)&&
                ("--settlement-opt-out-stage".equals(args[0])||
                 "--settlement-opt-out-post-readback".equals(args[0]))){
            boolean late="--settlement-opt-out-post-readback".equals(
                args[0]);
            String disabled=args.length==4
                ?checkedSettlementExperimentFlag(args[3])
                :LocalJvmAccountSessionLease.CAPTURED_EPOCH_SETTLE_LAB_PROPERTY;
            settleKillHolder(Paths.get(args[1]),
                Paths.get(args[1]).resolve("unused-ready"),
                LocalReusableAccountEpochLab.Stage.valueOf(args[2]),
                false,false,!late,late,disabled);
            return;
        }
        if(args.length==3&&
                "--settlement-post-readback-revoked".equals(args[0])){
            settleKillHolder(Paths.get(args[1]),
                Paths.get(args[1]).resolve("unused-ready"),
                LocalReusableAccountEpochLab.Stage.valueOf(args[2]),
                false,true);
            return;
        }
        if(args.length==3&&"--settlement-revoked".equals(args[0])){
            settleKillHolder(Paths.get(args[1]),
                Paths.get(args[1]).resolve("unused-ready"),
                LocalReusableAccountEpochLab.Stage.valueOf(args[2]),true);
            return;
        }
        if(args.length==4&&"--settlement-holder".equals(args[0])){
            settleKillHolder(Paths.get(args[1]),Paths.get(args[2]),
                LocalReusableAccountEpochLab.Stage.valueOf(args[3]));
            return;
        }
        if(args.length==3&&"--settlement-overlap-fresh".equals(args[0])){
            settlementFreshProbe(Paths.get(args[1]),
                LocalReusableAccountEpochLab.Stage.valueOf(args[2]),
                true);
            return;
        }
        if(args.length==3&&"--settlement-fresh".equals(args[0])){
            settlementFreshProbe(Paths.get(args[1]),
                LocalReusableAccountEpochLab.Stage.valueOf(args[2]));
            return;
        }
        check(args.length==0,"unexpected args");
        exercise();
        // R110: injected LAB sidecars at the formerly unguarded
        // inside-publication callback must prevent even default-OFF
        // World account replacement at all four namespace prefixes.
        runLateLabEvidenceWorldWriteMatrix();
        // R111: test the separate, default-OFF RAW writer during
        // serialization, not only the guarded World writer.
        runLateLabEvidenceRawWriteMatrix();
        // R112: after actual default-OFF RAW move, negative LAB
        // sidecar injection must prevent a false successful return.
        // Changed disk bytes are uncertain, NEVER rolled back.
        runPostMoveLabEvidenceRawMatrix();
        // R113: verify actual cross-process sidecar visibility
        // for RAW and guarded World writers, four namespaces each.
        runCrossJvmLateLabEvidenceMatrix();
        // R114: characterize late LAB evidence after a non-forced
        // World move, separately from the forced World receipt path.
        runPostMoveLabEvidenceWorldMatrix();
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
        // R109: additionally exercise direct saveForWorld; LAB
        // evidence must veto this guarded World entrypoint before
        // temp allocation and under the publication lock.
        // R108: every fresh JVM probe also attempts a default-OFF
        // direct repository save. It must be rejected BEFORE a RAW
        // temp appears, while existing account bytes remain identical,
        // both before and after original World termination.
        // R107: selected R106 child-JVM overlap stages also verify
        // the OS lock stays busy while the real authenticated owner
        // lives, then can be reacquired only after its process dies.
        // The existing full stage suite retains fresh default-OFF
        // negative account admission regardless of OS lock release.
        // R94: exhaustive, exact enum inventory. Every forced
        // boundary gets its OWN original authenticated World holder,
        // revoked FIFO, and independent default-OFF JVM denial.
        for(LocalReusableAccountEpochLab.Stage stage:
                LocalReusableAccountEpochLab.Stage.values())
            runRevokedSettlement(stage);
        // R95: distinct completion/readback window AFTER the last
        // receipt force callback, BEFORE returning any LAB result.
        runRevokedSettlement(
            LocalReusableAccountEpochLab.Stage.RECEIPT_FORCED,true);
        // R97: each of the FOUR independent experiment flags
        // must fail closed at a moved HEAD, a fully forced LAB
        // receipt, and after complete readback (12 original TCP
        // World subprocesses + 12 separate default-OFF JVMs).
        for(String flag:settlementExperimentFlags()){
            runRevokedSettlement(
                LocalReusableAccountEpochLab.Stage.HEAD_MOVED_BEFORE_DIR_FORCE,
                false,flag);
            runRevokedSettlement(
                LocalReusableAccountEpochLab.Stage.RECEIPT_FORCED,
                false,flag);
            runRevokedSettlement(
                LocalReusableAccountEpochLab.Stage.RECEIPT_FORCED,
                true,flag);
        }
        // R98: executor shutdown is a distinct revocation trigger.
        // Authenticated World keeps its original lease and all LAB
        // feature flags ON, but no shutdown FIFO may settle a LAB.
        runRevokedSettlement(
            LocalReusableAccountEpochLab.Stage.SNAPSHOT_FORCED,
            false,null,true);
        runRevokedSettlement(
            LocalReusableAccountEpochLab.Stage.RECEIPT_FORCED,
            false,null,true);
        // R105: independent-thread production close AFTER the
        // final FIFO witness read. A completed LAB Observation may
        // return, but never as an ACK or recovery grant; a fresh
        // default-OFF JVM must still refuse old-account admission.
        runRevokedSettlement(
            LocalReusableAccountEpochLab.Stage.RECEIPT_FORCED,
            true,null,false,false,false,true,true);
        // R104: concurrently close the actual persistence executor
        // from a separate thread while original World settlement
        // holds the session token and complete receipt readback.
        // Check bounded thread termination, FIFO revocation,
        // no unearned ACK, and independent default-OFF JVM denial.
        runRevokedSettlement(
            LocalReusableAccountEpochLab.Stage.RECEIPT_FORCED,
            true,null,false,false,true,false,true);
        // R103: closing AFTER the last volatile original-reservation
        // read may still return a LAB-only observation. It must
        // authorize neither final ACK nor release/admission.
        runRevokedSettlement(
            LocalReusableAccountEpochLab.Stage.RECEIPT_FORCED,
            true,null,false,false,false,true);
        // R102: after complete receipt journal inspection but before
        // even a non-admitting LAB settlement result, invoke the
        // production persistence close. Original FIFO revocation
        // must veto the result and a separate default-OFF JVM must
        // still refuse legacy hydration and TCP admission.
        runRevokedSettlement(
            LocalReusableAccountEpochLab.Stage.RECEIPT_FORCED,
            true,null,false,false,true);
        // R100: exercise the ENTIRE production close lifecycle,
        // not just the R98 admission helper, at three materially
        // different on-disk states under the original authenticated
        // World lock and real final FIFO reservation:
        // - pending HEAD after forced snapshot
        // - CLOSED HEAD renamed before directory force
        // - complete, forced laboratory receipt
        // Each gets its own original World, exact sidecar-inventory
        // checks, unchanged legacy account bytes, and a separate
        // default-OFF JVM admission denial. No real ACK is enabled.
        for(LocalReusableAccountEpochLab.Stage stage:
                new LocalReusableAccountEpochLab.Stage[]{
                    LocalReusableAccountEpochLab.Stage.SNAPSHOT_FORCED,
                    LocalReusableAccountEpochLab.Stage.HEAD_MOVED_BEFORE_DIR_FORCE,
                    LocalReusableAccountEpochLab.Stage.RECEIPT_FORCED
                })
            runRevokedSettlement(stage,false,null,true,true);
    }
}
