package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * R75: executable, completely isolated retirement counterexamples.
 *
 * A real R73/R72/R71 final save through WorldPlayerPersistence gives a
 * consistent R74 review. This is NOT a durable retirement certificate.
 *  - A separate JVM invokes FilePlayerRepository.save WHILE the parent
 *    owns R60, proving the OS lock is cooperative, not a global mutex.
 *  - An unguarded R64 owner transition bypasses the R73 blocker.
 *  - R63 can write canonical+ledger under R60 without R73 preflight,
 *    invalidating the persisted R64 owner record.
 *  - An ABA restoration can reproduce a matching R74 review, but R75
 *    still never authorizes marker removal or session admission.
 *
 * All paths point into a disposable temp tree; no live server starts.
 */
public final class R75OfflineRetirementSafetyAuditTest {
    private static final String USER="opensrc";

    private static final class Fixture implements AutoCloseable{
        final Path root,journal,accounts,generations,sessions,evidence;
        final FilePlayerRepository disk;
        final World world;
        final WorldPlayer actor=new WorldPlayer();
        final WorldPlayerPersistence persistence;
        final R71OfflineFinalSaveCheckpointFence terminal;
        final R72OfflineInFlightCommitEvidence writer;
        final R73OfflinePendingWriteAdmission admission;
        final R74OfflineReconciliationReview reconcile;
        final R75OfflineRetirementSafetyAudit audit;
        final R64OfflineSessionOwnership.Token initial;
        R64OfflineSessionOwnership.Token released;
        PlayerSnapshot finalSnapshot;
        UUID id;

        Fixture()throws Exception{
            root=Files.createTempDirectory(
                "spk-r75-no-auto-retirement-"
            );
            journal=root.resolve("journal");
            accounts=root.resolve("accounts");
            generations=root.resolve("generations");
            sessions=root.resolve("sessions");
            evidence=root.resolve("evidence");
            for(Path p:new Path[]{
                journal,accounts,generations,sessions,evidence
            })Files.createDirectory(p);
            disk=new FilePlayerRepository(
                u->accounts.resolve(u+".properties")
            );
            world=World.isolatedForTest(60_000L,disk);
            persistence=world.persistence();
            R60TradePairCoordinator.initializeEmpty(journal);
            actor.bank().spawnItem(995,100,packets());
            disk.save(PlayerSnapshotCodec.capture(USER,actor));
            R63AccountSaveAdmission.initializeExisting(
                journal,accounts,generations,USER
            );
            initial=R64OfflineSessionOwnership.claimFirst(
                journal,accounts,generations,sessions,USER
            );
            terminal=new R71OfflineFinalSaveCheckpointFence(
                journal,accounts,generations,sessions
            );
            writer=new R72OfflineInFlightCommitEvidence(
                evidence,terminal
            );
            admission=new R73OfflinePendingWriteAdmission(
                journal,accounts,generations,sessions,evidence
            );
            reconcile=new R74OfflineReconciliationReview(
                journal,accounts,generations,sessions,evidence
            );
            audit=new R75OfflineRetirementSafetyAudit(
                journal,accounts,generations,sessions,evidence
            );
            persistence.installOfflineIoWriteAdmissionForTest(writer);
            persistence.installOfflineCheckpointOwnerCaptureForTest(
                terminal
            );
            world.registerPlayer(actor,USER);
            world.start();
            terminal.bindActorForTest(
                actor,actor.generation(),initial
            );
        }

        void completeFinal()throws Exception{
            AtomicReference<WorldPlayerPersistence.CapturedSave> out=
                new AtomicReference<>();
            world.submitAndWait(actor,()->{
                actor.bank().spawnItem(560,1,packets());
                WorldPlayerPersistence.CapturedSave captured=
                    persistence.captureDeferredFinalSave(
                        USER,actor,actor.generation(),0,
                        "[r75-offline] ","RETIREMENT_RACE"
                    );
                try{
                    id=admission.registerFinalBeforeQueueForTest(
                        captured,initial,writer
                    );
                }catch(IOException error){
                    throw new IllegalStateException(
                        "test fixture intent registration",error
                    );
                }
                out.set(captured);
            },5_000L);
            require(out.get()!=null&&id!=null,
                "real World actor did not capture final snapshot");
            persistence.submitCapturedWithBackpressure(
                out.get(),5_000L
            ).completion.get(15L,TimeUnit.SECONDS);
            require(R72OfflineInFlightCommitEvidence.inspect(
                    evidence,id).status==
                    R72OfflineInFlightCommitEvidence.ReviewStatus
                        .BLOCK_COMPLETION_NOT_AUTHORIZED,
                "R72 final acknowledgement never published");
            released=R60TradePairCoordinator.withExclusive(
                journal,false,()->
                    R64OfflineSessionOwnership
                        .requireReleasedOwnerUnderLock(
                            journal,accounts,generations,sessions,USER
                        )
            );
            finalSnapshot=disk.load(USER).orElseThrow(
                ()->new IOException("canonical final account missing")
            );
            require(released.account.generation==2L,
                "test final save did not advance generation to 2");
        }

        Path blocker(){
            return evidence.resolve(
                "r73-opensrc.intent.properties"
            );
        }

        @Override public void close()throws Exception{
            if(actor.registered())world.unregisterPlayer(actor);
            world.close();
            try(java.util.stream.Stream<Path> files=Files.walk(root)){
                files.sorted(Comparator.reverseOrder()).forEach(p->{
                    try{Files.deleteIfExists(p);}
                    catch(IOException e){
                        throw new IllegalStateException(
                            "R75 isolated cleanup "+p,e
                        );
                    }
                });
            }
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length>0){
            runWorker(args);
            return;
        }
        validateKnownSourceIngress();
        counterexamplesAfterConsistentAck();
        System.out.println(
            "R75_OFFLINE_RETIREMENT_SAFETY_AUDIT_PASS "+
            "eightSourceIngressGapsReviewed=true "+
            "realR74NormalAckConsistentButStillBlocked=true "+
            "separateJvmFileWriterBypassesHeldR60Lock=true "+
            "staleObservationDetectedAfterDirectAccountWrite=true "+
            "snapshotABAStillNeverAuthorizesRetirement=true "+
            "R73GuardRefusesOwnerTakeover=true "+
            "unguardedR64OwnerTransitionBypassesR73=true "+
            "unguardedR63GenerationSaveBypassesR73=true "+
            "ownerAndAccountDivergenceFailClosed=true "+
            "allEvidenceAndWriteIntentRemainUnretired=true "+
            "noProductionLoginOrFilesystemRepair=true"
        );
    }

    private static void counterexamplesAfterConsistentAck()
            throws Exception{
        try(Fixture f=new Fixture()){
            f.completeFinal();
            assertR74(f,
                R74OfflineReconciliationReview.Status
                    .BLOCK_CONSISTENT_BUT_QUARANTINED);
            R75OfflineRetirementSafetyAudit.Review before=
                assertAudit(f,
                    R75OfflineRetirementSafetyAudit.Status
                        .BLOCK_UNFENCED_WRITER_AND_OWNER_PATHS);
            require(before.generation==2L&&
                    before.observationId.equals(f.id) &&
                    before.knownGaps.size()>=8,
                "matching ACK unexpectedly became a retirement grant");
            byte[] blocker=Files.readAllBytes(f.blocker());
            byte[] originalLedger=Files.readAllBytes(
                f.generations.resolve(
                    "opensrc.r63-generation.properties"
                )
            );
            byte[] originalOwner=Files.readAllBytes(
                f.sessions.resolve(
                    "opensrc.r64-session.properties"
                )
            );

            // The parent owns R60, yet a different JVM can write the
            // canonical file without acquiring that lock. Publication
            // is atomic, but NOT R73 owner/generation fenced.
            R60TradePairCoordinator.withExclusive(
                f.journal,false,()->{
                    byte[] old=Files.readAllBytes(
                        f.accounts.resolve("opensrc.properties")
                    );
                    runChild("direct-file",f.accounts.toString());
                    byte[] after=Files.readAllBytes(
                        f.accounts.resolve("opensrc.properties")
                    );
                    require(!Arrays.equals(old,after),
                        "unfenced FilePlayerRepository was blocked by R60");
                    return null;
                }
            );
            require(Arrays.equals(
                    blocker,Files.readAllBytes(f.blocker()))&&
                    Arrays.equals(originalLedger,Files.readAllBytes(
                        f.generations.resolve(
                            "opensrc.r63-generation.properties"
                        )
                    ))&&
                    Arrays.equals(originalOwner,Files.readAllBytes(
                        f.sessions.resolve(
                            "opensrc.r64-session.properties"
                        )
                    )),
                "direct unfenced account write unexpectedly coordinated "+
                "R73/R63/R64 evidence");
            assertR74(f,
                R74OfflineReconciliationReview.Status
                    .BLOCK_EVIDENCE_MISMATCH);
            R75OfflineRetirementSafetyAudit.Review stale=
                f.audit.recheck(USER,before);
            require(stale.status==
                    R75OfflineRetirementSafetyAudit.Status
                        .BLOCK_STALE_RECONCILIATION_OBSERVATION,
                "post-review independent file write was not noticed");
            denyAll(stale);

            // An exact ABA-like restoration can make the account
            // evidence appear identical again. No reviewer can use
            // that appearance as an absence-of-writers proof.
            f.disk.save(f.finalSnapshot);
            assertR74(f,
                R74OfflineReconciliationReview.Status
                    .BLOCK_CONSISTENT_BUT_QUARANTINED);
            assertAudit(f,
                R75OfflineRetirementSafetyAudit.Status
                    .BLOCK_UNFENCED_WRITER_AND_OWNER_PATHS);
            denyAll(f.audit.recheck(USER,before));

            // R73 opt-in takeover rejects its never-retired blocker.
            try{
                f.admission.replaceOwnerIfClear(f.released);
                throw new AssertionError(
                    "R73 takeover unexpectedly ignored blocker"
                );
            }catch(IOException denied){
                require(denied.getMessage().contains(
                    "R73_UNRESOLVED_WRITE_DENIED"),
                    "wrong R73 denial: "+denied);
            }

            // R64's original, intentionally offline helper bypasses
            // R73: transfer under R60 in a different child JVM even
            // after the immutable intent was registered and ACKed.
            runChild(
                "direct-owner",f.journal.toString(),
                f.accounts.toString(),f.generations.toString(),
                f.sessions.toString(),f.released.username,
                f.released.owner.toString(),
                Long.toString(f.released.epoch),
                f.released.phase.name(),
                Long.toString(f.released.account.generation),
                Long.toString(f.released.account.journalRevision),
                f.released.account.snapshotDigest
            );
            assertR74(f,
                R74OfflineReconciliationReview.Status
                    .BLOCK_OWNER_NOT_RELEASED);
            require(f.audit.recheck(USER,before).status==
                    R75OfflineRetirementSafetyAudit.Status
                        .BLOCK_STALE_RECONCILIATION_OBSERVATION,
                "post-review R64 transition was not observable");
            require(Arrays.equals(
                    blocker,Files.readAllBytes(f.blocker())),
                "unguarded R64 replacement retired R73 blocker");

            // R63's direct save path participates in R60 but does
            // NOT consult R73. It advances account+generation only;
            // the R64 record is left on the old generation.
            R63AccountSaveAdmission.Check current=
                R63AccountSaveAdmission.inspect(
                    f.journal,f.accounts,f.generations,USER
                );
            require(current.status==
                    R63AccountSaveAdmission.Status
                        .READY_FOR_OTHER_OFFLINE_CHECKS &&
                    current.token.generation==2L,
                "R63 pre-direct write state not current");
            runChild("direct-r63",
                f.journal.toString(),f.accounts.toString(),
                f.generations.toString(),USER,
                Long.toString(current.token.generation),
                Long.toString(current.token.journalRevision),
                current.token.snapshotDigest
            );
            R63AccountSaveAdmission.Check later=
                R63AccountSaveAdmission.inspect(
                    f.journal,f.accounts,f.generations,USER
                );
            require(later.status==
                    R63AccountSaveAdmission.Status
                        .READY_FOR_OTHER_OFFLINE_CHECKS &&
                    later.token.generation==3L,
                "unguarded R63 save did not advance own generation");
            require(R64OfflineSessionOwnership.inspect(
                    f.journal,f.accounts,f.generations,f.sessions,USER
                ).status==
                    R64OfflineSessionOwnership.Status
                        .BLOCK_INDETERMINATE,
                "R63 bypass did not create R64 generation divergence");
            assertR74(f,
                R74OfflineReconciliationReview.Status
                    .BLOCK_EVIDENCE_MISMATCH);
            assertAudit(f,
                R75OfflineRetirementSafetyAudit.Status
                    .BLOCK_UNRESOLVED_ACCOUNT_EVIDENCE);
            require(Arrays.equals(
                    blocker,Files.readAllBytes(f.blocker())),
                "unguarded R63 writer removed R73 evidence");
        }
    }

    private static void validateKnownSourceIngress()throws Exception{
        Path source=Paths.get("src","spk","local");
        if(!Files.isDirectory(source))
            source=Paths.get("server","src","spk","local");
        require(Files.isDirectory(source),
            "R75 expected the actual checked-out source tree");
        for(R75OfflineRetirementSafetyAudit.Gap gap:
                R75OfflineRetirementSafetyAudit.knownSourceGaps()){
            Path file=source.resolve(gap.source);
            require(Files.isRegularFile(file),
                "R75 known ingress source missing "+gap.source);
            String text=Files.readString(file);
            require(text.contains(gap.operation),
                "R75 audited writer/owner ingress moved in "+gap.source+
                " operation="+gap.operation);
            require(!text.contains(
                    "R73OfflinePendingWriteAdmission"),
                "R75 writer/owner ingress has changed; re-audit "+
                gap.source);
        }
    }

    private static void runWorker(String[] args)throws Exception{
        switch(args[0]){
            case "direct-file":{
                require(args.length==2,"direct-file args");
                FilePlayerRepository disk=new FilePlayerRepository(
                    user->Paths.get(args[1]).resolve(
                        user+".properties"
                    )
                );
                PlayerSnapshot before=disk.load(USER).orElseThrow(
                    ()->new IOException("R75 direct writer account absent")
                );
                WorldPlayer actor=new WorldPlayer();
                PlayerSnapshotCodec.applyValidated(before,actor);
                actor.bank().spawnItem(565,1,packets());
                disk.save(PlayerSnapshotCodec.capture(USER,actor));
                System.out.println("R75_DIRECT_FILE_WRITER_PASS");
                return;
            }
            case "direct-owner":{
                require(args.length==12,"direct-owner args");
                R64OfflineSessionOwnership.Token expected=
                    new R64OfflineSessionOwnership.Token(
                        args[5],UUID.fromString(args[6]),
                        Long.parseLong(args[7]),
                        R64OfflineSessionOwnership.Phase.valueOf(
                            args[8]
                        ),
                        new R63AccountSaveAdmission.Token(
                            args[5],Long.parseLong(args[9]),
                            Long.parseLong(args[10]),args[11]
                        )
                    );
                R64OfflineSessionOwnership.Token next=
                    R64OfflineSessionOwnership.replaceOwner(
                        Paths.get(args[1]),Paths.get(args[2]),
                        Paths.get(args[3]),Paths.get(args[4]),
                        expected
                    );
                require(next.epoch==expected.epoch+1L &&
                        next.phase==
                            R64OfflineSessionOwnership.Phase.ACTIVE,
                    "unfenced R64 transition not applied");
                System.out.println("R75_DIRECT_OWNER_PASS");
                return;
            }
            case "direct-r63":{
                require(args.length==8,"direct-r63 args");
                Path journal=Paths.get(args[1]);
                Path accounts=Paths.get(args[2]);
                Path generations=Paths.get(args[3]);
                String user=args[4];
                R63AccountSaveAdmission.Token expected=
                    new R63AccountSaveAdmission.Token(
                        user,Long.parseLong(args[5]),
                        Long.parseLong(args[6]),args[7]
                    );
                FilePlayerRepository disk=new FilePlayerRepository(
                    u->accounts.resolve(u+".properties")
                );
                PlayerSnapshot before=disk.load(user).orElseThrow(
                    ()->new IOException("R75 R63 account absent")
                );
                WorldPlayer actor=new WorldPlayer();
                PlayerSnapshotCodec.applyValidated(before,actor);
                actor.bank().spawnItem(561,1,packets());
                R63AccountSaveAdmission.Token after=
                    R63AccountSaveAdmission.saveIfCurrent(
                        journal,accounts,generations,expected,
                        PlayerSnapshotCodec.capture(user,actor),false
                    );
                require(after.generation==expected.generation+1L,
                    "unguarded R63 write not advanced");
                System.out.println("R75_DIRECT_R63_PASS");
                return;
            }
            default:
                throw new IllegalArgumentException(
                    "unknown test-only R75 worker "+args[0]
                );
        }
    }

    private static void runChild(String... args)throws Exception{
        String[] command=new String[4+args.length];
        command[0]=javaExecutable();
        command[1]="-cp";
        command[2]=System.getProperty("java.class.path");
        command[3]="spk.local.R75OfflineRetirementSafetyAuditTest";
        System.arraycopy(args,0,command,4,args.length);
        Process child=new ProcessBuilder(command)
            .redirectErrorStream(true).start();
        if(!child.waitFor(20L,TimeUnit.SECONDS)){
            child.destroyForcibly();
            child.waitFor(5L,TimeUnit.SECONDS);
            throw new AssertionError(
                "R75 isolated external writer timed out"
            );
        }
        String output=new String(
            child.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8
        );
        require(child.exitValue()==0&&output.contains(
                "R75_DIRECT_"),
            "R75 child writer failed code="+child.exitValue()+
            " output="+output);
    }

    private static R75OfflineRetirementSafetyAudit.Review assertAudit(
        Fixture f,R75OfflineRetirementSafetyAudit.Status expected
    )throws Exception{
        Map<String,byte[]> before=durableBytes(f.root);
        R75OfflineRetirementSafetyAudit.Review found=
            f.audit.inspect(USER);
        require(found.status==expected,
            "R75 status expected="+expected+" found="+found.status+
            " R74="+found.r74+" reason="+found.reason);
        denyAll(found);
        require(sameBytes(before,durableBytes(f.root)),
            "R75 read-only retirement audit mutated evidence");
        return found;
    }

    private static void assertR74(
        Fixture f,R74OfflineReconciliationReview.Status expected
    )throws Exception{
        R74OfflineReconciliationReview.Review check=
            f.reconcile.inspect(USER);
        require(check.status==expected,
            "R74 state expected="+expected+" found="+check.status+
            " reason="+check.reason);
        require(!check.loginAuthorized()&&!check.writeIntentRetired(),
            "R74 improperly authorized retirement");
    }

    private static void denyAll(
        R75OfflineRetirementSafetyAudit.Review result
    ){
        require(!result.retirementAuthorized()&&
                !result.loginAuthorized()&&
                !result.recoveryAuthorized()&&
                !result.replayAuthorized()&&
                !result.quarantineReleased()&&
                !result.markersModified()&&
                !result.allWritersQuiescent()&&
                !result.productionAuthenticated(),
            "R75 improperly authorized marker cleanup/recovery");
    }

    private static Map<String,byte[]> durableBytes(
        Path root
    )throws IOException{
        Map<String,byte[]> out=new HashMap<>();
        try(java.util.stream.Stream<Path> entries=Files.walk(root)){
            entries.filter(Files::isRegularFile)
                .filter(file->{
                    Path rel=root.relativize(file);
                    if(rel.getNameCount()<2)return false;
                    String directory=rel.getName(0).toString();
                    return "journal".equals(directory)||
                        "accounts".equals(directory)||
                        "generations".equals(directory)||
                        "sessions".equals(directory)||
                        "evidence".equals(directory);
                })
                .forEach(file->{
                    try{
                        out.put(root.relativize(file).toString(),
                            Files.readAllBytes(file));
                    }catch(IOException e){
                        throw new IllegalStateException(e);
                    }
                });
        }
        return out;
    }

    private static boolean sameBytes(
        Map<String,byte[]> left,Map<String,byte[]> right
    ){
        if(!left.keySet().equals(right.keySet()))return false;
        for(String file:left.keySet())
            if(!Arrays.equals(
                    left.get(file),right.get(file)))
                return false;
        return true;
    }

    private static ServerPacketWriter packets(){
        return new ServerPacketWriter(
            new OutboundPacketQueue(),
            new IsaacCipher(new int[]{1,2,3,4})
        );
    }

    private static String javaExecutable(){
        return Paths.get(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase().contains("win")
                ?"java.exe":"java").toString();
    }

    private static void require(boolean truth,String why){
        if(!truth)throw new AssertionError(why);
    }
}
