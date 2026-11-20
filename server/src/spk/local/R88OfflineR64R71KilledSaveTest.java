package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * R88 OFFLINE ONLY. Kills a real second JVM inside R64's actual R60-locked
 * account save before R63, between R63 and owner publication, or after owner
 * publication but before acknowledgement. A real isolated World captures
 * R71-ordered snapshots BEFORE the child save and submits them AFTER death.
 *
 * No live LocalSession, gameplay account, actual client socket, automatic
 * recovery, login, or production write is involved.
 */
public final class R88OfflineR64R71KilledSaveTest {
    private enum Cut {
        BEFORE_R63_SAVE,
        AFTER_R63_BEFORE_OWNER,
        AFTER_OWNER_BEFORE_ACK
    }
    @FunctionalInterface private interface Checked{
        void run()throws Exception;
    }

    private static final String USER="opensrc";

    public static void main(String[] args)throws Exception{
        if(args.length!=0){
            if(args.length!=4||!"child".equals(args[0]))
                throw new IllegalArgumentException(
                    "R88 unexpected child invocation");
            childSave(new Fixture(Paths.get(args[1]),false),
                Cut.valueOf(args[2]),Paths.get(args[3]));
            return;
        }

        Path scratch=Files.createTempDirectory("r88-r64-r71-kill-");
        try{
            runScenario(scratch.resolve("before-r63"),
                Cut.BEFORE_R63_SAVE);
            runScenario(scratch.resolve("gap-after-r63"),
                Cut.AFTER_R63_BEFORE_OWNER);
            runScenario(scratch.resolve("after-owner"),
                Cut.AFTER_OWNER_BEFORE_ACK);
            System.out.println(
                "R88_REAL_R64_R71_KILLED_SAVE_PASS "+
                "realAccountSnapshotAndR63Ledger=true "+
                "actualR64R60LockAcrossChildKill=true "+
                "realWorldCaptureOrdinalBeforeCrash=true "+
                "childJvmDestroyForciblyAtThreeSaveCutpoints=true "+
                "preR63KillCanonicalUnchanged=true "+
                "preR63R71NewerCaptureCommitsAndOlderDenied=true "+
                "postR63PreOwnerGapQuarantined=true "+
                "gapOldOwnerAndR71SnapshotsDenied=true "+
                "afterOwnerPreAckReinspectionReadyButNotLogin=true "+
                "postAckOldR71GateRefusesAllCaptures=true "+
                "explicitOfflineFreshOwnerSaveAllowed=true "+
                "allDeniedWritesPreserveCanonicalBytes=true "+
                "inMemoryR71WatermarkNotDurable=true "+
                "noAutomaticRepairOrReplay=true "+
                "noProductionSessionOrAccountChanges=true "+
                "noPowerLossDurabilityProof=true"
            );
        }finally{
            try(Stream<Path> paths=Files.walk(scratch)){
                paths.sorted(Comparator.reverseOrder()).forEach(path->{
                    try{Files.deleteIfExists(path);}
                    catch(IOException error){throw new IllegalStateException(
                        "R88 disposable fixture cleanup "+path,error);
                    }
                });
            }
        }
    }

    private static void runScenario(Path root,Cut cut)throws Exception{
        Fixture p=new Fixture(root,true);
        R64OfflineSessionOwnership.Token initial=p.initialize();
        Bytes baseline=p.bytes();

        try(World world=World.isolatedForTest(
                60_000L,rawWriterMustFail(p.files()))){
            WorldPlayer player=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(
                p.files().load(USER).orElseThrow(
                    ()->new IOException("R88 missing initial snapshot")),
                player
            );
            long generation=world.registerPlayer(player,USER);
            R71OfflineOrderedPersistenceAdmission gate=
                new R71OfflineOrderedPersistenceAdmission(
                    p.journal,p.accounts,p.generations,p.sessions,initial
                );
            world.persistence().installOfflineSaveAdmissionForTest(gate);
            world.start();
            AtomicReference<WorldPlayerPersistence.CapturedSave> old=
                new AtomicReference<>();
            AtomicReference<WorldPlayerPersistence.CapturedSave> newer=
                new AtomicReference<>();
            ServerPacketWriter packets=packets();

            // Both immutable R71 snapshots are captured on the actual
            // isolated World actor before the unrelated child JVM saves.
            world.submitAndWait(player,generation,()->{
                spawn(player,560,packets);
                old.set(world.persistence().captureDeferredSave(
                    USER,player,generation,0,"[r88] ","PRE_CRASH_OLDER"
                ));
                spawn(player,565,packets);
                newer.set(world.persistence().captureDeferredSave(
                    USER,player,generation,0,"[r88] ","PRE_CRASH_NEWER"
                ));
            },10_000L);
            require(old.get()!=null && newer.get()!=null &&
                    old.get().ticket.captureOrdinal==1L &&
                    newer.get().ticket.captureOrdinal==2L,
                "R88 World-thread captures not ordered before child death");

            killChildAtCutpoint(p,cut);
            Bytes killed=p.bytes();
            checkPublishedStage(p,cut,baseline,killed,initial);
            require(gate.highestAcceptedOrdinal()==0L,
                "R88 capture ordinals advanced without an admitted save");

            if(cut==Cut.BEFORE_R63_SAVE){
                // A killed child which published no canonical changes
                // leaves its previously-held owner token current.
                // This is a deliberate, authorized OFFLINE test gate,
                // not an automatic real-world session recovery.
                WorldPlayerPersistence.SaveTicket latest=
                    world.persistence().submitCapturedWithBackpressure(
                        newer.get(),5_000L);
                awaitSuccess(latest);
                require(gate.highestAcceptedOrdinal()==2L &&
                        gate.committedWrites()==1,
                    "R88 surviving R71 worker missed the newer capture");
                Bytes afterNew=p.bytes();
                WorldPlayerPersistence.SaveTicket stale=
                    world.persistence().submitCapturedWithBackpressure(
                        old.get(),5_000L);
                expectTicketDenied(stale,
                    "R71_SAVE_DENIED stale capture ordinal");
                require(afterNew.same(p.bytes()) &&
                        gate.rejectedOldWrites()==1,
                    "R88 old capture overwrote the newer post-kill save");
                checkGeneration(p,2L);
            }else{
                // The child has advanced R63, with or without the final
                // R64 owner record. The parent R71 gate still holds the
                // PRE-crash owner-generation token; even a new numerical
                // capture ordinal MUST fail before any canonical write.
                WorldPlayerPersistence.SaveTicket latest=
                    world.persistence().submitCapturedWithBackpressure(
                        newer.get(),5_000L);
                expectTicketDenied(latest,
                    "R64_SESSION_DENIED stale generation/hash/index revision");
                WorldPlayerPersistence.SaveTicket stale=
                    world.persistence().submitCapturedWithBackpressure(
                        old.get(),5_000L);
                expectTicketDenied(stale,
                    "R64_SESSION_DENIED stale generation/hash/index revision");
                require(gate.highestAcceptedOrdinal()==0L &&
                        gate.committedWrites()==0 &&
                        gate.identicalWrites()==0 &&
                        killed.same(p.bytes()),
                    "R88 R71 stale owner incorrectly changed records or watermark");

                expectDenied(()->R64OfflineSessionOwnership.saveOwned(
                    p.journal,p.accounts,p.generations,p.sessions,
                    initial,newer.get().snapshot,false),
                    "R64_SESSION_DENIED stale generation");
                expectDenied(()->R64OfflineSessionOwnership.replaceOwner(
                    p.journal,p.accounts,p.generations,p.sessions,initial),
                    "R64_SESSION_DENIED stale generation");
                require(killed.same(p.bytes()),
                    "R88 stale direct owner operation changed canonical evidence");
            }
        }

        if(cut==Cut.AFTER_OWNER_BEFORE_ACK){
            // An observational R64 review sees the fully committed owner.
            // It cannot authorize actual login or automatic recovery.
            R64OfflineSessionOwnership.Review review=p.review();
            require(review.status==
                    R64OfflineSessionOwnership.Status
                        .READY_FOR_OTHER_OFFLINE_CHECKS &&
                    review.token!=null &&
                    !review.loginAuthorized() &&
                    !review.automaticRecoveryAttempted(),
                "R88 post-ack review did not preserve read-only boundary");
            // Explicit TEST-ONLY adoption of the reviewed token by a fresh
            // isolated World, with a new R71 in-memory ordinal domain.
            explicitFreshOfflineSave(p,review.token);
            checkGeneration(p,3L);
        }
    }

    private static void checkPublishedStage(
        Fixture p,Cut cut,Bytes baseline,Bytes killed,
        R64OfflineSessionOwnership.Token before)throws Exception{
        R64OfflineSessionOwnership.Review review=p.review();
        require(!review.loginAuthorized() &&
                !review.automaticRecoveryAttempted() &&
                !review.accountFilesModified(),
            "R88 observational inspect unexpectedly claimed authority");
        switch(cut){
            case BEFORE_R63_SAVE:
                require(killed.same(baseline) &&
                        review.status==
                        R64OfflineSessionOwnership.Status
                            .READY_FOR_OTHER_OFFLINE_CHECKS &&
                        review.token!=null &&
                        review.token.account.generation==1L,
                    "R88 pre-R63 process kill changed a canonical record");
                checkGeneration(p,1L);
                return;
            case AFTER_R63_BEFORE_OWNER:
                require(!Arrays.equals(killed.account,baseline.account) &&
                        !Arrays.equals(killed.ledger,baseline.ledger) &&
                        Arrays.equals(killed.owner,baseline.owner) &&
                        review.status==
                        R64OfflineSessionOwnership.Status.BLOCK_INDETERMINATE &&
                        review.token==null,
                    "R88 real R63-before-owner gap was not quarantined");
                checkGeneration(p,2L);
                return;
            case AFTER_OWNER_BEFORE_ACK:
                require(!Arrays.equals(killed.account,baseline.account) &&
                        !Arrays.equals(killed.ledger,baseline.ledger) &&
                        !Arrays.equals(killed.owner,baseline.owner) &&
                        review.status==
                        R64OfflineSessionOwnership.Status
                            .READY_FOR_OTHER_OFFLINE_CHECKS &&
                        review.token!=null &&
                        review.token.owner.equals(before.owner) &&
                        review.token.epoch==before.epoch &&
                        review.token.account.generation==2L,
                    "R88 post-owner kill did not persist complete canonical commit");
                checkGeneration(p,2L);
                return;
            default:throw new IllegalStateException("unknown R88 cutpoint");
        }
    }

    private static void explicitFreshOfflineSave(
        Fixture p,R64OfflineSessionOwnership.Token current)
        throws Exception{
        try(World isolated=World.isolatedForTest(
                60_000L,rawWriterMustFail(p.files()))){
            WorldPlayer player=new WorldPlayer();
            PlayerSnapshot alreadyCommitted=p.files().load(USER)
                .orElseThrow(()->new IOException("R88 missing committed child state"));
            PlayerSnapshotCodec.applyValidated(alreadyCommitted,player);
            long generation=isolated.registerPlayer(player,USER);
            R71OfflineOrderedPersistenceAdmission gate=
                new R71OfflineOrderedPersistenceAdmission(
                    p.journal,p.accounts,p.generations,p.sessions,current);
            isolated.persistence().installOfflineSaveAdmissionForTest(gate);
            isolated.start();
            AtomicReference<WorldPlayerPersistence.SaveTicket> saved=
                new AtomicReference<>();
            isolated.submitAndWait(player,generation,()->{
                spawn(player,560,packets());
                saved.set(isolated.persistence().captureAndSave(
                    USER,player,generation,0,"[r88] ",
                    "EXPLICIT_TEST_ONLY_FRESH_OFFLINE_OWNER"
                ));
            },10_000L);
            awaitSuccess(saved.get());
            require(gate.highestAcceptedOrdinal()==1L &&
                    gate.committedWrites()==1 &&
                    gate.currentToken().account.generation==3L,
                "R88 explicitly reopened offline owner failed valid R71 save");
            PlayerSnapshot canonical=p.files().load(USER)
                .orElseThrow(()->new IOException("R88 no final account"));
            require(R57TradePairJournal.digest(canonical).equals(
                    gate.currentToken().account.snapshotDigest),
                "R88 final canonical snapshot does not match R63 digest");
        }
    }

    private static void childSave(Fixture p,Cut requested,Path ready)
        throws Exception{
        R64OfflineSessionOwnership.Review prior=p.review();
        require(prior.status==
                R64OfflineSessionOwnership.Status
                    .READY_FOR_OTHER_OFFLINE_CHECKS &&
                prior.token!=null,
            "R88 child cannot resolve prior offline owner");
        PlayerSnapshot stored=p.files().load(USER)
            .orElseThrow(()->new IOException("R88 child account missing"));
        WorldPlayer player=new WorldPlayer();
        PlayerSnapshotCodec.applyValidated(stored,player);
        spawn(player,385,packets());
        PlayerSnapshot updated=PlayerSnapshotCodec.capture(USER,player);

        R64OfflineSessionOwnership.installOfflineSaveCrashCutpointForTest(
            phase->{
                if(!requested.name().equals(phase))return;
                Files.writeString(ready,phase,StandardCharsets.UTF_8);
                long deadline=System.nanoTime()+
                    TimeUnit.SECONDS.toNanos(30);
                while(System.nanoTime()<deadline){
                    try{Thread.sleep(100L);}
                    catch(InterruptedException e){
                        Thread.currentThread().interrupt();
                        throw new IOException(
                            "R88 child crash cutpoint interrupted",e);
                    }
                }
                throw new IOException(
                    "R88 child was not forcibly killed at cutpoint");
            }
        );
        try{
            R64OfflineSessionOwnership.saveOwned(
                p.journal,p.accounts,p.generations,p.sessions,
                prior.token,updated,false
            );
            throw new AssertionError(
                "R88 child R64 save acknowledged unexpectedly");
        }finally{
            R64OfflineSessionOwnership.clearOfflineSaveCrashCutpointForTest();
        }
    }

    private static void killChildAtCutpoint(Fixture p,Cut cut)
        throws Exception{
        Path ready=p.root.resolve("r88-ready-"+cut.name());
        Path log=p.root.resolve("r88-child-"+cut.name()+".log");
        boolean windows=System.getProperty("os.name","")
            .toLowerCase(Locale.ROOT).contains("win");
        Path java=Paths.get(System.getProperty("java.home"),"bin",
            windows?"java.exe":"java");
        Process child=new ProcessBuilder(java.toString(),"-cp",
            System.getProperty("java.class.path"),
            R88OfflineR64R71KilledSaveTest.class.getName(),
            "child",p.root.toString(),cut.name(),ready.toString()
        ).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try{
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
            while(!Files.isRegularFile(ready)&&child.isAlive()&&
                    System.nanoTime()<deadline)
                Thread.sleep(15L);
            require(Files.isRegularFile(ready)&&child.isAlive() &&
                    cut.name().equals(Files.readString(
                        ready,StandardCharsets.UTF_8)),
                "R88 child failed to reach locked cutpoint "+cut+
                ": "+(Files.exists(log)?Files.readString(log):"no log"));
            expectDenied(()->R60TradePairCoordinator.withExclusive(
                    p.journal,false,()->null),
                "R60_LOCK_BUSY_OR_OVERLAPPING");
            require(p.review().status==
                    R64OfflineSessionOwnership.Status.BLOCK_INDETERMINATE,
                "R88 read admission passed while another JVM held R60 lock");

            child.destroyForcibly();
            require(child.waitFor(15L,TimeUnit.SECONDS)&&!child.isAlive(),
                "R88 killed child did not terminate");
        }finally{
            if(child.isAlive()){
                child.destroyForcibly();
                child.waitFor(15L,TimeUnit.SECONDS);
            }
        }
        awaitOsLockReleased(p.journal);
    }

    private static void awaitOsLockReleased(Path journal)
        throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(System.nanoTime()<deadline){
            try{
                R60TradePairCoordinator.withExclusive(
                    journal,false,()->null);
                return;
            }catch(IOException refused){
                if(!refused.getMessage().contains(
                        "R60_LOCK_BUSY_OR_OVERLAPPING"))
                    throw refused;
            }
            Thread.sleep(30L);
        }
        throw new AssertionError(
            "R88_R60_RELEASE_TIMEOUT child exited but OS lock unavailable");
    }

    private static void checkGeneration(Fixture p,long wanted)
        throws Exception{
        R63AccountSaveAdmission.Check check=
            R63AccountSaveAdmission.inspect(
                p.journal,p.accounts,p.generations,USER
            );
        require(check.status==
                R63AccountSaveAdmission.Status
                    .READY_FOR_OTHER_OFFLINE_CHECKS &&
                check.token!=null && check.token.generation==wanted,
            "R88 actual R63 ledger and canonical generation mismatch");
    }

    private static void awaitSuccess(
        WorldPlayerPersistence.SaveTicket ticket)throws Exception{
        require(ticket!=null,"R88 missing World save ticket");
        ticket.completion.get(15L,TimeUnit.SECONDS);
    }

    private static void expectTicketDenied(
        WorldPlayerPersistence.SaveTicket ticket,String message)
        throws Exception{
        require(ticket!=null,"R88 missing refused World ticket");
        try{
            ticket.completion.get(15L,TimeUnit.SECONDS);
            throw new AssertionError(
                "R88 stale World snapshot was incorrectly committed");
        }catch(ExecutionException expected){
            require(expected.getCause() instanceof IOException &&
                    expected.getCause().getMessage().contains(message),
                "R88 unexpected R71 refusal: "+expected.getCause());
        }
    }

    private static PlayerRepository rawWriterMustFail(
        FilePlayerRepository files){
        return new PlayerRepository(){
            @Override public Optional<PlayerSnapshot> load(
                String user)throws IOException{
                return files.load(user);
            }
            @Override public void save(PlayerSnapshot snapshot)
                throws IOException{
                throw new IOException("R88_RAW_WRITER_BYPASS_DENIED");
            }
        };
    }

    private static void spawn(
        WorldPlayer player,int item,ServerPacketWriter packets)
        throws IOException{
        String result=player.bank().spawnItem(item,1,packets);
        require(result.startsWith("ITEM_SPAWN_OK"),
            "R88 snapshot mutation refused: "+result);
    }

    private static ServerPacketWriter packets(){
        return new ServerPacketWriter(
            new OutboundPacketQueue(),new IsaacCipher(new int[]{1,2,3,4})
        );
    }

    private static void expectDenied(Checked operation,String wanted)
        throws Exception{
        try{
            operation.run();
            throw new AssertionError("R88 expected refusal absent: "+wanted);
        }catch(IOException refused){
            require(refused.getMessage().contains(wanted),
                "R88 unexpected denial "+refused+" expected "+wanted);
        }
    }

    private static void require(boolean yes,String reason){
        if(!yes)throw new AssertionError(reason);
    }

    private static final class Bytes{
        final byte[] account,ledger,owner;
        Bytes(byte[] account,byte[] ledger,byte[] owner){
            this.account=account;this.ledger=ledger;this.owner=owner;
        }
        boolean same(Bytes another){
            return Arrays.equals(account,another.account)&&
                Arrays.equals(ledger,another.ledger)&&
                Arrays.equals(owner,another.owner);
        }
    }

    private static final class Fixture{
        final Path root,journal,accounts,generations,sessions;
        Fixture(Path root,boolean create)throws IOException{
            this.root=root;
            journal=root.resolve("journal");
            accounts=root.resolve("accounts");
            generations=root.resolve("generations");
            sessions=root.resolve("sessions");
            if(create){
                Files.createDirectory(root);
                Files.createDirectory(journal);
                Files.createDirectory(accounts);
                Files.createDirectory(generations);
                Files.createDirectory(sessions);
            }
        }
        FilePlayerRepository files(){
            return new FilePlayerRepository(
                name->accounts.resolve(name+".properties"));
        }
        R64OfflineSessionOwnership.Token initialize()
            throws IOException{
            WorldPlayer first=new WorldPlayer();
            spawn(first,995,packets());
            files().save(PlayerSnapshotCodec.capture(USER,first));
            R60TradePairCoordinator.initializeEmpty(journal);
            R63AccountSaveAdmission.initializeExisting(
                journal,accounts,generations,USER);
            return R64OfflineSessionOwnership.claimFirst(
                journal,accounts,generations,sessions,USER);
        }
        Bytes bytes()throws IOException{
            return new Bytes(
                Files.readAllBytes(accounts.resolve(
                    USER+".properties")),
                Files.readAllBytes(generations.resolve(
                    USER+".r63-generation.properties")),
                Files.readAllBytes(sessions.resolve(
                    USER+".r64-session.properties"))
            );
        }
        R64OfflineSessionOwnership.Review review(){
            return R64OfflineSessionOwnership.inspect(
                journal,accounts,generations,sessions,USER);
        }
    }

    private R88OfflineR64R71KilledSaveTest(){}
}
