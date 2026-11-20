package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Properties;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * R89 OFFLINE ONLY: actual R60/R63/R64 canonical account files, real
 * World-thread R71-style immutable captures, restart-reopened durable
 * watermark, successor owner rebind, and force-killed independent JVMs.
 *
 * The PREPARED record is an intentionally conservative quarantine, not
 * a recoverable login or durable replay/commit certificate.
 */
public final class R89OfflineDurableCaptureWatermarkTest {
    private static final String USER="opensrc";
    private static final String[] CUTS={
        "AFTER_PREPARE_BEFORE_ACCOUNT",
        "AFTER_ACCOUNT_BEFORE_WATERMARK",
        "AFTER_WATERMARK_BEFORE_ACK"
    };

    @FunctionalInterface private interface Checked{
        void run()throws Exception;
    }

    public static void main(String[] args)throws Exception{
        if(args.length!=0){
            if(args.length!=4||!"child".equals(args[0]))
                throw new IllegalArgumentException("R89 invalid child args");
            child(Paths.get(args[1]),args[2],Paths.get(args[3]));
            return;
        }
        Path root=Files.createTempDirectory("spk-r89-durable-capture-");
        try{
            worldCaptureReopenAndOrdinalRefusal(root.resolve("world"));
            identicalOrdinalDurableAndRepeatedRefusal(
                root.resolve("identical"));
            ownerTakeoverMustRebind(root.resolve("takeover"));
            for(int i=0;i<CUTS.length;i++){
                killAtCut(root.resolve("kill-"+i),CUTS[i]);
            }
            tamperingAndAmbiguousStageRefuse(root.resolve("quarantine"));
            System.out.println(
                "R89_OFFLINE_DURABLE_CAPTURE_WATERMARK_PASS "+
                "realR60R63R64AccountFiles=true "+
                "realWorldCaptureAndIoWorker=true "+
                "durableOrdinalSurvivesGateReopen=true "+
                "restartedWorldLowOrdinalRefused=true "+
                "identicalSnapshotOrdinalPersisted=true "+
                "staleCompetingWriterCannotOverwrite=true "+
                "explicitOwnerTakeoverRebind=true "+
                "oldOwnerDeniedAfterTakeover=true "+
                "realChildJvmKilledAtThreeWatermarkBoundaries=true "+
                "preAccountPreparedQuarantined=true "+
                "postAccountPreparedQuarantined=true "+
                "postCommittedAckRestartRefusesDuplicate=true "+
                "ambiguousStageAndTamperingQuarantined=true "+
                "allDeniedCanonicalBytesStable=true "+
                "noAutomaticLoginRecoveryOrReplay=true "+
                "noProductionRuntimeWiring=true "+
                "noPowerLossDurabilityOrMultiFileAtomicityClaim=true"
            );
        }finally{
            try(Stream<Path> paths=Files.walk(root)){
                paths.sorted(Comparator.reverseOrder()).forEach(path->{
                    try{Files.deleteIfExists(path);}
                    catch(IOException e){throw new IllegalStateException(
                        "R89 disposable temporary cleanup "+path,e);
                    }
                });
            }
        }
    }

    private static void worldCaptureReopenAndOrdinalRefusal(
        Path root)throws Exception{
        Fixture p=new Fixture(root,true);
        R64OfflineSessionOwnership.Token first=p.initialize();
        R89OfflineDurableCaptureAdmission gate=p.initializeWatermark(first);
        try(World world=World.isolatedForTest(
            60_000L,rawWriterMustNotRun(p.files()))){
            WorldPlayer player=p.playerFromDisk();
            long generation=world.registerPlayer(player,USER);
            world.persistence().installOfflineSaveAdmissionForTest(gate);
            world.start();
            AtomicReference<WorldPlayerPersistence.CapturedSave> oldest=
                new AtomicReference<>();
            AtomicReference<WorldPlayerPersistence.CapturedSave> newest=
                new AtomicReference<>();
            world.submitAndWait(player,generation,()->{
                spawn(player,560);
                oldest.set(world.persistence().captureDeferredSave(
                    USER,player,generation,0,"[r89] ","OLDER_FIRST"
                ));
                spawn(player,565);
                newest.set(world.persistence().captureDeferredSave(
                    USER,player,generation,0,"[r89] ","NEWER_FIRST"
                ));
            },10_000L);
            require(oldest.get().ticket.captureOrdinal==1L&&
                    newest.get().ticket.captureOrdinal==2L,
                "R89 captures lack World-thread source ordinals");

            WorldPlayerPersistence.SaveTicket committed=
                world.persistence().submitCapturedWithBackpressure(
                    newest.get(),5_000L);
            awaitSuccess(committed);
            require(gate.highestAcceptedOrdinal()==2L&&
                    gate.committedWrites()==1,
                "R89 highwater not committed through real IO worker");
            Bytes after=p.bytes();
            WorldPlayerPersistence.SaveTicket stale=
                world.persistence().submitCapturedWithBackpressure(
                    oldest.get(),5_000L);
            expectTicketFailure(stale,"R89_SAVE_DENIED stale capture ordinal");
            require(after.same(p.bytes()),
                "R89 old deferred World save overwrote canonical files");
        }

        R64OfflineSessionOwnership.Token current=p.ready();
        R89OfflineDurableCaptureAdmission reopened=p.reopen(current);
        require(reopened.highestAcceptedOrdinal()==2L,
            "R89 newly created Java gate lost persisted ordinal");
        Bytes committed=p.bytes();
        // On a NEW World actor, ordinals start from one again. Without
        // a durable source epoch or actor resumption this is necessarily
        // refused; R89 MUST NOT silently reset the canonical highwater.
        try(World restarted=World.isolatedForTest(
            60_000L,rawWriterMustNotRun(p.files()))){
            WorldPlayer fresh=p.playerFromDisk();
            long generation=restarted.registerPlayer(fresh,USER);
            restarted.persistence().installOfflineSaveAdmissionForTest(
                reopened
            );
            restarted.start();
            AtomicReference<WorldPlayerPersistence.SaveTicket> oldOrdinal=
                new AtomicReference<>();
            restarted.submitAndWait(fresh,generation,()->{
                spawn(fresh,385);
                oldOrdinal.set(restarted.persistence().captureAndSave(
                    USER,fresh,generation,0,"[r89] ","RESTART_ORDINAL_ONE"
                ));
            },10_000L);
            require(oldOrdinal.get().captureOrdinal==1L,
                "R89 restart did not demonstrate reset capture ordinal");
            expectTicketFailure(oldOrdinal.get(),
                "R89_SAVE_DENIED stale capture ordinal");
            require(committed.same(p.bytes()),
                "R89 restarted World overwrote durable account watermark");
        }
        require(reopened.highestAcceptedOrdinal()==2L,
            "R89 refused World restart advanced in-memory watermark");

        // A competing gate opened from the exact same committed state
        // cannot commit once another gate has advanced the canonical file.
        R89OfflineDurableCaptureAdmission competitor=p.reopen(current);
        PlayerSnapshot next=p.mutated(385);
        reopened.withOrderedAdmission(3L,next,()->{
            throw new IOException("R89_RAW_REPOSITORY_BYPASS");
        });
        Bytes afterNew=p.bytes();
        expectFailure(()->competitor.withOrderedAdmission(
                3L,next,()->{
                    throw new IOException("R89_RAW_REPOSITORY_BYPASS");
                }),
            "R64_SESSION_DENIED stale generation");
        require(afterNew.same(p.bytes()),
            "R89 competing old handle changed canonical records");
        require(p.reopen(p.ready()).highestAcceptedOrdinal()==3L,
            "R89 post-write reopen did not see durable highwater 3");
    }

    private static void identicalOrdinalDurableAndRepeatedRefusal(
        Path root)throws Exception{
        Fixture p=new Fixture(root,true);
        R64OfflineSessionOwnership.Token first=p.initialize();
        R89OfflineDurableCaptureAdmission gate=p.initializeWatermark(first);
        Bytes original=p.bytes();
        PlayerSnapshot unchanged=p.files().load(USER)
            .orElseThrow(()->new IOException("R89 missing account"));
        gate.withOrderedAdmission(7L,unchanged,()->{
            throw new IOException("R89_RAW_REPOSITORY_BYPASS");
        });
        require(gate.identicalWrites()==1&&
                gate.highestAcceptedOrdinal()==7L,
            "R89 identical snapshot did not persist its capture ordinal");
        Bytes after=p.bytes();
        require(Arrays.equals(original.account,after.account)&&
                Arrays.equals(original.ledger,after.ledger)&&
                Arrays.equals(original.owner,after.owner)&&
                !Arrays.equals(original.watermark,after.watermark),
            "R89 identical capture mutated account or failed watermark");
        R89OfflineDurableCaptureAdmission reopened=p.reopen(first);
        require(reopened.highestAcceptedOrdinal()==7L,
            "R89 identical committed ordinal lost after reopen");
        expectFailure(()->reopened.withOrderedAdmission(
            7L,unchanged,()->{
                throw new IOException("R89_RAW_REPOSITORY_BYPASS");
            }),"R89_SAVE_DENIED stale capture ordinal");
        require(after.same(p.bytes()),
            "R89 duplicate identical callback changed canonical record");
        reopened.withOrderedAdmission(8L,unchanged,()->{
            throw new IOException("R89_RAW_REPOSITORY_BYPASS");
        });
        require(p.reopen(first).highestAcceptedOrdinal()==8L,
            "R89 second identical capture not persisted after reopen");
    }

    private static void ownerTakeoverMustRebind(Path root)
        throws Exception{
        Fixture p=new Fixture(root,true);
        R64OfflineSessionOwnership.Token first=p.initialize();
        R89OfflineDurableCaptureAdmission gate=p.initializeWatermark(first);
        PlayerSnapshot unchanged=p.files().load(USER)
            .orElseThrow(()->new IOException("R89 missing base account"));
        gate.withOrderedAdmission(4L,unchanged,()->{
            throw new IOException("R89_RAW_REPOSITORY_BYPASS");
        });
        R64OfflineSessionOwnership.Token successor=
            R64OfflineSessionOwnership.replaceOwner(
                p.journal,p.accounts,p.generations,p.sessions,first
            );
        Bytes beforeRebind=p.bytes();
        expectFailure(()->p.reopen(successor),
            "R89_STALE_WATERMARK");
        expectFailure(()->gate.withOrderedAdmission(
            5L,p.mutated(560),()->{
                throw new IOException("R89_RAW_REPOSITORY_BYPASS");
            }),"R64_SESSION_DENIED stale owner");
        require(beforeRebind.same(p.bytes()),
            "R89 stale writer changed bytes after transfer");
        R89OfflineDurableCaptureAdmission rebound=
            R89OfflineDurableCaptureAdmission.rebindAfterTakeover(
                p.journal,p.accounts,p.generations,p.sessions,
                first,successor
            );
        require(rebound.highestAcceptedOrdinal()==0L &&
                p.reopen(successor).highestAcceptedOrdinal()==0L,
            "R89 explicit new owner rebind failed reset");
        PlayerSnapshot next=p.mutated(385);
        rebound.withOrderedAdmission(1L,next,()->{
            throw new IOException("R89_RAW_REPOSITORY_BYPASS");
        });
        require(p.reopen(p.ready()).highestAcceptedOrdinal()==1L,
            "R89 successor capture did not survive reopen");
    }

    private static void killAtCut(Path root,String stage)
        throws Exception{
        Fixture p=new Fixture(root,true);
        R64OfflineSessionOwnership.Token initial=p.initialize();
        R89OfflineDurableCaptureAdmission original=
            p.initializeWatermark(initial);
        Bytes before=p.bytes();
        Path marker=p.root.resolve("r89-ready-"+stage);
        Path log=p.root.resolve("r89-child-"+stage+".log");
        boolean win=System.getProperty("os.name","")
            .toLowerCase(Locale.ROOT).contains("win");
        Path java=Paths.get(System.getProperty("java.home"),"bin",
            win?"java.exe":"java");
        Process child=new ProcessBuilder(
            java.toString(),"-cp",System.getProperty("java.class.path"),
            R89OfflineDurableCaptureWatermarkTest.class.getName(),
            "child",p.root.toString(),stage,marker.toString()
        ).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try{
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
            while(!Files.isRegularFile(marker)&&child.isAlive()&&
                    System.nanoTime()<deadline)
                Thread.sleep(20L);
            require(Files.isRegularFile(marker)&&child.isAlive()&&
                    Files.readString(marker,StandardCharsets.UTF_8)
                        .equals(stage),
                "R89 child did not reach locked stage "+stage+" "+
                (Files.exists(log)?Files.readString(log):"no log"));
            expectFailure(()->R60TradePairCoordinator.withExclusive(
                p.journal,false,()->null),
                "R60_LOCK_BUSY_OR_OVERLAPPING");
            require(p.review().status==
                    R64OfflineSessionOwnership.Status.BLOCK_INDETERMINATE,
                "R89 foreign OS lock did not block passive owner read");
            child.destroyForcibly();
            require(child.waitFor(15L,TimeUnit.SECONDS)&&!child.isAlive(),
                "R89 child did not terminate forcibly");
        }finally{
            if(child.isAlive()){
                child.destroyForcibly();
                child.waitFor(15L,TimeUnit.SECONDS);
            }
        }
        awaitR60Released(p.journal);
        Bytes after=p.bytes();
        Properties watermark=p.watermark();
        require(watermark.getProperty("phase").equals(
                    stage.equals(CUTS[2])?"COMMITTED":"PREPARED"),
            "R89 wrong durable phase after child termination "+stage);
        if(stage.equals(CUTS[0])){
            require(Arrays.equals(before.account,after.account)&&
                    Arrays.equals(before.ledger,after.ledger)&&
                    Arrays.equals(before.owner,after.owner),
                "R89 pre-account kill changed account/owner canonical bytes");
        }else{
            require(!Arrays.equals(before.account,after.account)&&
                    !Arrays.equals(before.ledger,after.ledger)&&
                    !Arrays.equals(before.owner,after.owner),
                "R89 post-account kill did not change all three account files");
        }
        R64OfflineSessionOwnership.Token now=p.ready();
        require(now.account.generation==
                (stage.equals(CUTS[0])?1L:2L),
            "R89 R63/R64 generation mismatch after process kill");

        if(!stage.equals(CUTS[2])){
            expectFailure(()->p.reopen(now),
                "R89_PREPARED_QUARANTINED");
            if(stage.equals(CUTS[0])){
                expectFailure(()->original.withOrderedAdmission(
                    2L,p.mutated(560),()->{
                        throw new IOException("R89_RAW_REPOSITORY_BYPASS");
                    }),"R89_PREPARED_QUARANTINED");
            }
            Bytes quarantined=p.bytes();
            // The explicit R64 owner may appear usable; the new R89
            // gate still refuses even an identical-snapshot callback.
            require(after.same(quarantined),
                "R89 quarantine observation changed canonical bytes");
        }else{
            expectFailure(()->original.withOrderedAdmission(
                2L,p.mutated(560),()->{
                    throw new IOException("R89_RAW_REPOSITORY_BYPASS");
                }),"R64_SESSION_DENIED stale generation");
            R89OfflineDurableCaptureAdmission resumed=p.reopen(now);
            require(resumed.highestAcceptedOrdinal()==1L,
                "R89 acknowledged watermark missing after process kill");
            Bytes stable=p.bytes();
            expectFailure(()->resumed.withOrderedAdmission(
                1L,p.mutated(560),()->{
                    throw new IOException("R89_RAW_REPOSITORY_BYPASS");
                }),"R89_SAVE_DENIED stale capture ordinal");
            require(stable.same(p.bytes()),
                "R89 duplicate after killed ack changed files");
            resumed.withOrderedAdmission(2L,p.mutated(565),()->{
                throw new IOException("R89_RAW_REPOSITORY_BYPASS");
            });
            require(p.reopen(p.ready()).highestAcceptedOrdinal()==2L,
                "R89 next ordinal not persisted after acknowledged crash");
        }
    }

    private static void child(Path root,String requested,Path ready)
        throws Exception{
        Fixture p=new Fixture(root,false);
        R64OfflineSessionOwnership.Token now=p.ready();
        R89OfflineDurableCaptureAdmission gate=p.reopen(now);
        PlayerSnapshot changed=p.mutated(385);
        R89OfflineDurableCaptureAdmission.installCrashHookForTest(
            phase->{
                if(!requested.equals(phase))return;
                Files.writeString(ready,phase,StandardCharsets.UTF_8);
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
                while(System.nanoTime()<deadline){
                    try{Thread.sleep(80L);}
                    catch(InterruptedException interrupted){
                        Thread.currentThread().interrupt();
                        throw new IOException(
                            "R89 child interrupted at killpoint",interrupted);
                    }
                }
                throw new IOException(
                    "R89 child was not force killed at cutpoint");
            }
        );
        try{
            gate.withOrderedAdmission(1L,changed,()->{
                throw new IOException("R89_RAW_REPOSITORY_BYPASS");
            });
            throw new AssertionError(
                "R89 child returned without being terminated");
        }finally{
            R89OfflineDurableCaptureAdmission.clearCrashHookForTest();
        }
    }

    private static void tamperingAndAmbiguousStageRefuse(
        Path root)throws Exception{
        Fixture p=new Fixture(root,true);
        R64OfflineSessionOwnership.Token initial=p.initialize();
        p.initializeWatermark(initial);
        Bytes valid=p.bytes();
        Path watermarkPath=p.watermarkFile();
        Path orphan=p.sessions.resolve(
            "."+watermarkPath.getFileName()+".r16-orphan.stage");
        Files.write(orphan,new byte[]{9,8,7});
        expectFailure(()->p.reopen(initial),"R51_AMBIGUOUS");
        require(valid.same(p.bytes()),
            "R89 orphan stage was unexpectedly deleted or mutated");
        Files.delete(orphan); // Cleanup ONLY test-owned injected marker.

        Properties parsed=p.watermark();
        parsed.setProperty("highest.ordinal","999");
        try(java.io.OutputStream output=Files.newOutputStream(watermarkPath)){
            parsed.store(output,"R89 test-only checksum mismatch");
        }
        expectFailure(()->p.reopen(initial),
            "R89_WATERMARK_QUARANTINED");
        require(Arrays.equals(valid.account,p.bytes().account)&&
                Arrays.equals(valid.ledger,p.bytes().ledger)&&
                Arrays.equals(valid.owner,p.bytes().owner),
            "R89 corrupted watermark caused account mutation");
        // Restore exact test-owned canonical bytes only after denial.
        Files.write(watermarkPath,valid.watermark);
        require(p.reopen(initial).highestAcceptedOrdinal()==0L,
            "R89 original valid watermark did not reopen");
    }

    private static void awaitR60Released(Path journal)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15L);
        while(System.nanoTime()<deadline){
            try{
                R60TradePairCoordinator.withExclusive(
                    journal,false,()->null);
                return;
            }catch(IOException e){
                if(!e.getMessage().contains(
                        "R60_LOCK_BUSY_OR_OVERLAPPING"))
                    throw e;
            }
            Thread.sleep(30L);
        }
        throw new AssertionError("R89 R60 lock unreleased after kill");
    }

    private static void expectTicketFailure(
        WorldPlayerPersistence.SaveTicket ticket,String expected)
        throws Exception{
        try{
            ticket.completion.get(15L,TimeUnit.SECONDS);
            throw new AssertionError("R89 denied ticket unexpectedly saved");
        }catch(ExecutionException failure){
            require(failure.getCause() instanceof IOException&&
                    failure.getCause().getMessage().contains(expected),
                "R89 wrong World ticket refusal "+failure.getCause());
        }
    }

    private static void awaitSuccess(
        WorldPlayerPersistence.SaveTicket ticket)throws Exception{
        ticket.completion.get(15L,TimeUnit.SECONDS);
    }

    private static void expectFailure(Checked task,String expected)
        throws Exception{
        try{
            task.run();
            throw new AssertionError("R89 expected denial absent "+expected);
        }catch(IOException refused){
            require(refused.getMessage().contains(expected),
                "R89 wrong refusal "+refused+", wanted "+expected);
        }
    }

    private static PlayerRepository rawWriterMustNotRun(
        FilePlayerRepository files){
        return new PlayerRepository(){
            @Override public Optional<PlayerSnapshot> load(
                String user)throws IOException{
                return files.load(user);
            }
            @Override public void save(PlayerSnapshot snapshot)
                throws IOException{
                throw new IOException("R89_RAW_REPOSITORY_BYPASS");
            }
        };
    }

    private static void spawn(WorldPlayer player,int item)
        throws IOException{
        String result=player.bank().spawnItem(item,1,
            new ServerPacketWriter(
                new OutboundPacketQueue(),
                new IsaacCipher(new int[]{1,2,3,4})));
        require(result.startsWith("ITEM_SPAWN_OK"),
            "R89 bank fixture mutation failed "+result);
    }

    private static void require(boolean pass,String reason){
        if(!pass)throw new AssertionError(reason);
    }

    private static final class Bytes{
        final byte[] account,ledger,owner,watermark;
        Bytes(byte[] account,byte[] ledger,
              byte[] owner,byte[] watermark){
            this.account=account;
            this.ledger=ledger;
            this.owner=owner;
            this.watermark=watermark;
        }
        boolean same(Bytes b){
            return Arrays.equals(account,b.account)&&
                Arrays.equals(ledger,b.ledger)&&
                Arrays.equals(owner,b.owner)&&
                Arrays.equals(watermark,b.watermark);
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
                username->accounts.resolve(username+".properties")
            );
        }
        R64OfflineSessionOwnership.Token initialize()
            throws IOException{
            WorldPlayer player=new WorldPlayer();
            spawn(player,995);
            files().save(PlayerSnapshotCodec.capture(USER,player));
            R60TradePairCoordinator.initializeEmpty(journal);
            R63AccountSaveAdmission.initializeExisting(
                journal,accounts,generations,USER);
            return R64OfflineSessionOwnership.claimFirst(
                journal,accounts,generations,sessions,USER);
        }
        R89OfflineDurableCaptureAdmission initializeWatermark(
            R64OfflineSessionOwnership.Token first)throws IOException{
            return R89OfflineDurableCaptureAdmission.initialize(
                journal,accounts,generations,sessions,first);
        }
        R89OfflineDurableCaptureAdmission reopen(
            R64OfflineSessionOwnership.Token token)throws IOException{
            return R89OfflineDurableCaptureAdmission.reopen(
                journal,accounts,generations,sessions,token);
        }
        R64OfflineSessionOwnership.Review review(){
            return R64OfflineSessionOwnership.inspect(
                journal,accounts,generations,sessions,USER);
        }
        R64OfflineSessionOwnership.Token ready(){
            R64OfflineSessionOwnership.Review r=review();
            require(r.status==
                    R64OfflineSessionOwnership.Status
                        .READY_FOR_OTHER_OFFLINE_CHECKS&&
                    r.token!=null&&!r.loginAuthorized()&&
                    !r.automaticRecoveryAttempted(),
                "R89 R64 owner fixture not passively ready");
            return r.token;
        }
        WorldPlayer playerFromDisk()throws IOException{
            PlayerSnapshot loaded=files().load(USER)
                .orElseThrow(()->new IOException("R89 missing fixture account"));
            WorldPlayer player=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(loaded,player);
            return player;
        }
        PlayerSnapshot mutated(int item)throws IOException{
            WorldPlayer player=playerFromDisk();
            spawn(player,item);
            return PlayerSnapshotCodec.capture(USER,player);
        }
        Path watermarkFile(){
            return sessions.resolve(
                USER+".r89-watermark.properties");
        }
        Properties watermark()throws IOException{
            Properties p=new Properties();
            try(InputStream input=Files.newInputStream(watermarkFile())){
                p.load(input);
            }
            return p;
        }
        Bytes bytes()throws IOException{
            return new Bytes(
                Files.readAllBytes(accounts.resolve(USER+".properties")),
                Files.readAllBytes(generations.resolve(
                    USER+".r63-generation.properties")),
                Files.readAllBytes(sessions.resolve(
                    USER+".r64-session.properties")),
                Files.readAllBytes(watermarkFile())
            );
        }
    }

    private R89OfflineDurableCaptureWatermarkTest(){}
}
