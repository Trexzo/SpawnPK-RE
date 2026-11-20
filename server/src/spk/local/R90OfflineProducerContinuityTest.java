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
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * R90 OFFLINE only: real R63/R64 account + R89 watermark under R60,
 * WorldPlayerPersistence capture-order seeding before restart, old incarnation
 * fencing even when R64 owner token is identical, real child-JVM crashes.
 */
public final class R90OfflineProducerContinuityTest {
    private static final String USER="opensrc";
    private static final String PREPARE="AFTER_PRODUCER_PREPARE";
    private static final String AFTER_R89="AFTER_R89_COMMITTED_BEFORE_PRODUCER";
    private static final String ACK="AFTER_PRODUCER_COMMIT_BEFORE_ACK";
    private static final String[] CUTS={PREPARE,AFTER_R89,ACK};

    @FunctionalInterface private interface Checked{
        void run()throws Exception;
    }

    public static void main(String[] args)throws Exception{
        if(args.length!=0){
            if(args.length!=4||!"child".equals(args[0]))
                throw new IllegalArgumentException("R90 invalid child command");
            runChild(new Fixture(Paths.get(args[1]),false),
                args[2],Paths.get(args[3]));
            return;
        }
        Path scratch=Files.createTempDirectory("spk-r90-producer-");
        try{
            realWorldRestartAndStaleProducer(
                scratch.resolve("world-restart"));
            sameOwnerEpochIncarnationRefusal(
                scratch.resolve("same-owner"));
            ownerEpochTakeoverRequiresExplicitRebind(
                scratch.resolve("owner-rebind"));
            for(int i=0;i<CUTS.length;i++)
                realKilledJvms(scratch.resolve("kill-"+i),CUTS[i]);
            markerCorruptionAndOrphanStageFailClosed(
                scratch.resolve("bad-marker"));
            System.out.println(
                "R90_OFFLINE_PRODUCER_CONTINUITY_PASS "+
                "realWorldActorAndR60R63R64R89Persistence=true "+
                "seedWorldBeforeStartFromDurableHighwater=true "+
                "streamUuidPreservedAcrossRestart=true "+
                "newIncarnationUuidAndGeneration=true "+
                "sameOwnerEpochOldProducerDenied=true "+
                "oldWorldTicketDeniedWithoutCanonicalChange=true "+
                "newWorldNextOrdinalContinues=true "+
                "explicitR64OwnerRebindCreatesNewProducerStream=true "+
                "threeRealChildJvmKillpoints=true "+
                "preR89PreparedProducerQuarantined=true "+
                "postR89PreProducerCommitQuarantined=true "+
                "postProducerCommitAckSafeExplicitRestart=true "+
                "ambiguousStagesAndCorruptionDenied=true "+
                "sameR60LockChecksAndWrites=true "+
                "noLiveWorldLoginOrAccountChanges=true "+
                "noPowerLossOrMultiFileAtomicityClaim=true"
            );
        }finally{
            try(Stream<Path> walk=Files.walk(scratch)){
                walk.sorted(Comparator.reverseOrder()).forEach(p->{
                    try{Files.deleteIfExists(p);}
                    catch(IOException e){throw new IllegalStateException(
                        "R90 test-only fixture cleanup "+p,e);
                    }
                });
            }
        }
    }

    private static void realWorldRestartAndStaleProducer(
        Path root)throws Exception{
        Fixture fixture=new Fixture(root,true);
        R64OfflineSessionOwnership.Token first=fixture.initialize();
        R89OfflineDurableCaptureAdmission.initialize(
            fixture.journal,fixture.accounts,
            fixture.generations,fixture.sessions,first
        );
        R90OfflineProducerContinuity.Session original=
            fixture.initializeProducer(first);
        require(original.ticket.committedOrdinal==0L &&
                original.ticket.producerGeneration==1L,
            "R90 initial incarnation metadata invalid");

        WorldPlayerPersistence.CapturedSave capturedOld;
        try(World world=World.isolatedForTest(
                60_000L,rawWriterMustFail(fixture.files()))){
            WorldPlayer player=fixture.player();
            long generation=world.registerPlayer(player,USER);
            world.persistence().installOfflineSaveAdmissionForTest(
                original.gate);
            original.seedWorldBeforeStartForTest(world);
            world.start();
            AtomicReference<WorldPlayerPersistence.CapturedSave> old=
                new AtomicReference<>();
            AtomicReference<WorldPlayerPersistence.CapturedSave> current=
                new AtomicReference<>();
            world.submitAndWait(player,generation,()->{
                spawn(player,560);
                old.set(world.persistence().captureDeferredSave(
                    USER,player,generation,0,"[r90] ","OLDER"
                ));
                spawn(player,565);
                current.set(world.persistence().captureDeferredSave(
                    USER,player,generation,0,"[r90] ","NEWER"
                ));
            },10_000L);
            require(old.get().ticket.captureOrdinal==1L&&
                    current.get().ticket.captureOrdinal==2L,
                "R90 actual World capture ordinals incorrect");
            capturedOld=old.get();
            awaitSuccess(world.persistence().submitCapturedWithBackpressure(
                current.get(),5_000L));
            require(original.gate.highestAcceptedOrdinal()==2L,
                "R90 committed highwater missing");
            Bytes beforeOld=fixture.bytes();
            expectTicketDenied(
                world.persistence().submitCapturedWithBackpressure(
                    capturedOld,5_000L),
                "R89_SAVE_DENIED stale capture ordinal");
            require(beforeOld.same(fixture.bytes()),
                "R90 old World ticket overwrote canonical state");
        }

        R64OfflineSessionOwnership.Token owner=fixture.ready();
        R90OfflineProducerContinuity.Session restarted=
            fixture.restart(owner);
        require(restarted.ticket.producerGeneration==2L&&
                restarted.ticket.stream.equals(original.ticket.stream)&&
                !restarted.ticket.incarnation.equals(
                    original.ticket.incarnation)&&
                restarted.ticket.committedOrdinal==2L,
            "R90 restart did not bind new producer incarnation to highwater");

        // In the prior incarnation, R64 ownership remains the same; a
        // supposedly newer capture MUST be stopped by the new producer
        // fence rather than relying on a stale account-generation token.
        Bytes beforeStale=fixture.bytes();
        expectFailure(()->original.gate.withOrderedAdmission(
            3L,fixture.changed(385),()->{
                throw new IOException("R90_RAW_REPOSITORY_BYPASS");
            }),"R90_STALE_PRODUCER");
        require(beforeStale.same(fixture.bytes()),
            "R90 old producer mutated files after restart");
        require(fixture.inspectProducer(owner)==2L,
            "R90 restarted producer watermark lost ordinal2");

        try(World world=World.isolatedForTest(
                60_000L,rawWriterMustFail(fixture.files()))){
            WorldPlayer player=fixture.player();
            long generation=world.registerPlayer(player,USER);
            world.persistence().installOfflineSaveAdmissionForTest(
                restarted.gate);
            restarted.seedWorldBeforeStartForTest(world);
            world.start();
            AtomicReference<WorldPlayerPersistence.SaveTicket> saved=
                new AtomicReference<>();
            world.submitAndWait(player,generation,()->{
                spawn(player,385);
                saved.set(world.persistence().captureAndSave(
                    USER,player,generation,0,"[r90] ","AFTER_RESTART"
                ));
            },10_000L);
            require(saved.get().captureOrdinal==3L,
                "R90 World actor restart did not resume at ordinal3");
            awaitSuccess(saved.get());
        }
        require(fixture.inspectProducer(fixture.ready())==3L,
            "R90 restarted World did not persist ordinal3");
        R90OfflineProducerContinuity.Session third=
            fixture.restart(fixture.ready());
        require(third.ticket.producerGeneration==3L &&
                third.ticket.committedOrdinal==3L,
            "R90 second reboot lost producer generation or sequence");
    }

    private static void sameOwnerEpochIncarnationRefusal(
        Path root)throws Exception{
        Fixture fixture=new Fixture(root,true);
        R64OfflineSessionOwnership.Token owner=fixture.initialize();
        R89OfflineDurableCaptureAdmission.initialize(
            fixture.journal,fixture.accounts,
            fixture.generations,fixture.sessions,owner);
        R90OfflineProducerContinuity.Session original=
            fixture.initializeProducer(owner);
        PlayerSnapshot unchanged=fixture.files().load(USER).orElseThrow(
            ()->new IOException("R90 no test-owned snapshot"));
        original.gate.withOrderedAdmission(7L,unchanged,()->{
            throw new IOException("R90_RAW_REPOSITORY_BYPASS");
        });
        Bytes committed=fixture.bytes();
        R90OfflineProducerContinuity.Session successor=
            fixture.restart(owner);
        require(successor.ticket.committedOrdinal==7L&&
                successor.ticket.producerGeneration==2L,
            "R90 identical snapshot ordinal did not resume");
        Bytes afterRestart=fixture.bytes();
        require(Arrays.equals(committed.account,afterRestart.account)&&
                Arrays.equals(committed.ledger,afterRestart.ledger)&&
                Arrays.equals(committed.owner,afterRestart.owner),
            "R90 producer restart changed account owner or ledger");
        expectFailure(()->original.gate.withOrderedAdmission(
            8L,unchanged,()->{
                throw new IOException("R90_RAW_REPOSITORY_BYPASS");
            }),"R90_STALE_PRODUCER");
        require(afterRestart.same(fixture.bytes()),
            "R90 stale no-op producer modified durable files");
        successor.gate.withOrderedAdmission(8L,unchanged,()->{
            throw new IOException("R90_RAW_REPOSITORY_BYPASS");
        });
        require(fixture.inspectProducer(owner)==8L,
            "R90 valid restarted producer could not persist ordinal8");
    }

    private static void ownerEpochTakeoverRequiresExplicitRebind(
        Path root)throws Exception{
        Fixture f=new Fixture(root,true);
        R64OfflineSessionOwnership.Token oldOwner=f.initialize();
        R89OfflineDurableCaptureAdmission.initialize(
            f.journal,f.accounts,f.generations,f.sessions,oldOwner);
        R90OfflineProducerContinuity.Session previous=
            f.initializeProducer(oldOwner);
        PlayerSnapshot same=f.files().load(USER).orElseThrow(
            ()->new IOException("R90 no base account"));
        previous.gate.withOrderedAdmission(4L,same,()->{
            throw new IOException("R90_RAW_REPOSITORY_BYPASS");
        });
        R64OfflineSessionOwnership.Token successor=
            R64OfflineSessionOwnership.replaceOwner(
                f.journal,f.accounts,f.generations,f.sessions,oldOwner);
        Bytes before=f.bytes();
        expectFailure(()->f.restart(successor),
            "R89_STALE_WATERMARK");
        expectFailure(()->previous.gate.withOrderedAdmission(
            5L,f.changed(560),()->{
                throw new IOException("R90_RAW_REPOSITORY_BYPASS");
            }),"R64_SESSION_DENIED stale owner");
        require(before.same(f.bytes()),
            "R90 old owner continued after R64 takeover");
        R89OfflineDurableCaptureAdmission.rebindAfterTakeover(
            f.journal,f.accounts,f.generations,f.sessions,
            oldOwner,successor);
        R90OfflineProducerContinuity.Session rebound=
            R90OfflineProducerContinuity.rebindAfterOwnerChange(
                f.journal,f.accounts,f.generations,f.sessions,
                oldOwner,successor);
        require(rebound.ticket.ownerEpoch==oldOwner.epoch+1L &&
                !rebound.ticket.stream.equals(previous.ticket.stream)&&
                rebound.ticket.producerGeneration==1L&&
                rebound.ticket.committedOrdinal==0L,
            "R90 explicit owner change failed to allocate source stream");
        rebound.gate.withOrderedAdmission(1L,f.changed(565),()->{
            throw new IOException("R90_RAW_REPOSITORY_BYPASS");
        });
        require(f.inspectProducer(f.ready())==1L,
            "R90 new owner stream failed to publish ordinal1");
    }

    private static void realKilledJvms(
        Path root,String cutpoint)throws Exception{
        Fixture p=new Fixture(root,true);
        R64OfflineSessionOwnership.Token owner=p.initialize();
        R89OfflineDurableCaptureAdmission.initialize(
            p.journal,p.accounts,p.generations,p.sessions,owner);
        R90OfflineProducerContinuity.Session original=
            p.initializeProducer(owner);
        Bytes before=p.bytes();

        Path marker=p.root.resolve("r90-ready-"+cutpoint);
        Path log=p.root.resolve("r90-child-"+cutpoint+".log");
        boolean windows=System.getProperty("os.name","")
            .toLowerCase(Locale.ROOT).contains("win");
        Path java=Paths.get(System.getProperty("java.home"),"bin",
            windows?"java.exe":"java");
        Process child=new ProcessBuilder(
            java.toString(),"-cp",System.getProperty("java.class.path"),
            R90OfflineProducerContinuityTest.class.getName(),
            "child",p.root.toString(),cutpoint,marker.toString()
        ).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try{
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20L);
            while(!Files.isRegularFile(marker)&&child.isAlive()&&
                    System.nanoTime()<deadline)
                Thread.sleep(20L);
            require(Files.isRegularFile(marker)&&child.isAlive()&&
                    cutpoint.equals(Files.readString(
                        marker,StandardCharsets.UTF_8)),
                "R90 child never reached under-lock cutpoint "+cutpoint+
                (Files.exists(log)?Files.readString(log):" no log"));
            expectFailure(()->R60TradePairCoordinator.withExclusive(
                p.journal,false,()->null),
                "R60_LOCK_BUSY_OR_OVERLAPPING");
            child.destroyForcibly();
            require(child.waitFor(15L,TimeUnit.SECONDS)&&!child.isAlive(),
                "R90 force-killed JVM did not exit");
        }finally{
            if(child.isAlive()){
                child.destroyForcibly();
                child.waitFor(15L,TimeUnit.SECONDS);
            }
        }
        awaitLockRelease(p.journal);
        Properties producer=p.producer();
        Properties watermark=p.watermark();
        Bytes after=p.bytes();
        boolean completed=ACK.equals(cutpoint);
        require(producer.getProperty("phase").equals(
                    completed?"COMMITTED":"PREPARED"),
            "R90 killed JVM published incorrect producer phase");
        require(watermark.getProperty("phase").equals(
                    PREPARE.equals(cutpoint)?"COMMITTED":"COMMITTED"),
            "R90 killed JVM R89 phase mismatch");
        if(PREPARE.equals(cutpoint)){
            require(Arrays.equals(before.account,after.account)&&
                    Arrays.equals(before.ledger,after.ledger)&&
                    Arrays.equals(before.owner,after.owner)&&
                    Arrays.equals(before.watermark,after.watermark),
                "R90 pre-R89 killed JVM changed account or R89 watermark");
        }else{
            require(!Arrays.equals(before.account,after.account)&&
                    !Arrays.equals(before.ledger,after.ledger)&&
                    !Arrays.equals(before.owner,after.owner)&&
                    !Arrays.equals(before.watermark,after.watermark),
                "R90 post-R89 commit killed JVM did not publish all files");
        }

        R64OfflineSessionOwnership.Token current=p.ready();
        if(!completed){
            Bytes quarantine=p.bytes();
            expectFailure(()->p.restart(current),
                "R90_PREPARED_QUARANTINED");
            expectFailure(()->p.inspectProducer(current),
                "R90_PREPARED_QUARANTINED");
            if(PREPARE.equals(cutpoint)){
                expectFailure(()->original.gate.withOrderedAdmission(
                    2L,p.changed(565),()->{
                        throw new IOException("R90_RAW_REPOSITORY_BYPASS");
                    }),"R90_STALE_PRODUCER");
            }
            require(quarantine.same(p.bytes()),
                "R90 quarantine attempted auto-repair or file cleanup");
        }else{
            // R89 plus R90 canonical writes finished before ack. Reopen
            // explicitly; an old owner-generation or old incarnation
            // cannot replay ordinal1.
            R90OfflineProducerContinuity.Session resumed=
                p.restart(current);
            require(resumed.ticket.committedOrdinal==1L &&
                    resumed.ticket.producerGeneration==3L,
                "R90 killed-after-commit restart lost producer state");
            Bytes stable=p.bytes();
            expectFailure(()->resumed.gate.withOrderedAdmission(
                1L,p.changed(565),()->{
                    throw new IOException("R90_RAW_REPOSITORY_BYPASS");
                }),"R89_SAVE_DENIED stale capture ordinal");
            require(stable.same(p.bytes()),
                "R90 killed-after-commit duplicate changed bytes");
            resumed.gate.withOrderedAdmission(
                2L,p.changed(560),()->{
                    throw new IOException("R90_RAW_REPOSITORY_BYPASS");
                });
            require(p.inspectProducer(p.ready())==2L,
                "R90 safe resumed producer failed ordinal2");
        }
    }

    private static void runChild(
        Fixture p,String cutpoint,Path ready)throws Exception{
        R64OfflineSessionOwnership.Token current=p.ready();
        R90OfflineProducerContinuity.Session active=p.restart(current);
        PlayerSnapshot snapshot=p.changed(385);
        R90OfflineProducerContinuity.installCrashCutpointForTest(
            where->pauseAt(where,cutpoint,ready));
        if(AFTER_R89.equals(cutpoint))
            R89OfflineDurableCaptureAdmission.installCrashHookForTest(
                where->{
                    if("AFTER_WATERMARK_BEFORE_ACK".equals(where))
                        pauseAt(AFTER_R89,cutpoint,ready);
                });
        try{
            active.gate.withOrderedAdmission(1L,snapshot,()->{
                throw new IOException("R90_RAW_REPOSITORY_BYPASS");
            });
            throw new AssertionError("R90 child escaped forced-kill hook");
        }finally{
            R90OfflineProducerContinuity.clearCrashCutpointForTest();
            R89OfflineDurableCaptureAdmission.clearCrashHookForTest();
        }
    }

    private static void pauseAt(
        String stage,String desired,Path ready)throws IOException{
        if(!stage.equals(desired))return;
        Files.writeString(ready,stage,StandardCharsets.UTF_8);
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30L);
        while(System.nanoTime()<deadline){
            try{Thread.sleep(80L);}
            catch(InterruptedException interrupt){
                Thread.currentThread().interrupt();
                throw new IOException("R90 child interrupted",interrupt);
            }
        }
        throw new IOException("R90 child not killed at requested stage");
    }

    private static void markerCorruptionAndOrphanStageFailClosed(
        Path root)throws Exception{
        Fixture f=new Fixture(root,true);
        R64OfflineSessionOwnership.Token owner=f.initialize();
        R89OfflineDurableCaptureAdmission.initialize(
            f.journal,f.accounts,f.generations,f.sessions,owner);
        f.initializeProducer(owner);
        Bytes baseline=f.bytes();
        Path orphan=f.sessions.resolve(
            "."+f.producerFile().getFileName()+".r16-orphan.stage");
        Files.write(orphan,new byte[]{1,2,3});
        expectFailure(()->f.restart(owner),
            "R51_AMBIGUOUS_ACCOUNT_STAGING_DENIED");
        require(baseline.same(f.bytes()),"R90 orphan stage not preserved");
        Files.delete(orphan); // disposable test-created marker only

        Properties altered=f.producer();
        altered.setProperty("producer.generation","999");
        try(java.io.OutputStream output=
                Files.newOutputStream(f.producerFile())){
            altered.store(output,"R90 test-only corruption");
        }
        expectFailure(()->f.restart(owner),
            "R90_PRODUCER_QUARANTINED");
        require(Arrays.equals(baseline.account,f.bytes().account)&&
                Arrays.equals(baseline.ledger,f.bytes().ledger)&&
                Arrays.equals(baseline.owner,f.bytes().owner)&&
                Arrays.equals(baseline.watermark,f.bytes().watermark),
            "R90 tampered producer corrupted real account files");
        // Explicit fixture-only restoration; not a recovery API.
        Files.write(f.producerFile(),baseline.producer);
        require(f.restart(owner).ticket.producerGeneration==2L,
            "R90 original fixture failed strict reopen");
    }

    private static void awaitLockRelease(Path journal)
        throws Exception{
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
        throw new AssertionError("R90_R60_RELEASE_TIMEOUT killed JVM lock");
    }

    private static void expectTicketDenied(
        WorldPlayerPersistence.SaveTicket ticket,String message)
        throws Exception{
        try{
            ticket.completion.get(15L,TimeUnit.SECONDS);
            throw new AssertionError(
                "R90 expected rejected World ticket completed");
        }catch(ExecutionException rejected){
            require(rejected.getCause() instanceof IOException&&
                    rejected.getCause().getMessage().contains(message),
                "R90 wrong World ticket refusal "+rejected.getCause());
        }
    }

    private static void awaitSuccess(
        WorldPlayerPersistence.SaveTicket ticket)throws Exception{
        ticket.completion.get(15L,TimeUnit.SECONDS);
    }

    private static void expectFailure(Checked task,String fragment)
        throws Exception{
        try{
            task.run();
            throw new AssertionError(
                "R90 expected fail-closed denial missing "+fragment);
        }catch(IOException refused){
            require(refused.getMessage().contains(fragment),
                "R90 unexpected denial "+refused+" expected "+fragment);
        }
    }

    private static PlayerRepository rawWriterMustFail(
        FilePlayerRepository file){
        return new PlayerRepository(){
            @Override public Optional<PlayerSnapshot> load(
                String user)throws IOException{
                return file.load(user);
            }
            @Override public void save(PlayerSnapshot snapshot)
                throws IOException{
                throw new IOException("R90_RAW_REPOSITORY_BYPASS");
            }
        };
    }

    private static void spawn(WorldPlayer player,int item)
        throws IOException{
        String result=player.bank().spawnItem(item,1,
            new ServerPacketWriter(new OutboundPacketQueue(),
                new IsaacCipher(new int[]{1,2,3,4})));
        require(result.startsWith("ITEM_SPAWN_OK"),
            "R90 test bank mutation refused "+result);
    }

    private static void require(boolean condition,String detail){
        if(!condition)throw new AssertionError(detail);
    }

    private static final class Bytes{
        final byte[] account,ledger,owner,watermark,producer;
        Bytes(byte[] a,byte[] l,byte[] o,byte[] w,byte[] p){
            account=a;ledger=l;owner=o;watermark=w;producer=p;
        }
        boolean same(Bytes other){
            return Arrays.equals(account,other.account)&&
                Arrays.equals(ledger,other.ledger)&&
                Arrays.equals(owner,other.owner)&&
                Arrays.equals(watermark,other.watermark)&&
                Arrays.equals(producer,other.producer);
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
                username->accounts.resolve(username+".properties"));
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
        WorldPlayer player()throws IOException{
            WorldPlayer player=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(files().load(USER).orElseThrow(
                ()->new IOException("R90 missing account")),player);
            return player;
        }
        PlayerSnapshot changed(int item)throws IOException{
            WorldPlayer player=player();
            spawn(player,item);
            return PlayerSnapshotCodec.capture(USER,player);
        }
        R64OfflineSessionOwnership.Token ready(){
            R64OfflineSessionOwnership.Review review=
                R64OfflineSessionOwnership.inspect(
                    journal,accounts,generations,sessions,USER);
            require(review.status==
                    R64OfflineSessionOwnership.Status
                        .READY_FOR_OTHER_OFFLINE_CHECKS &&
                    review.token!=null&&!review.loginAuthorized(),
                "R90 offline R64 account is not consistent");
            return review.token;
        }
        R90OfflineProducerContinuity.Session initializeProducer(
            R64OfflineSessionOwnership.Token owner)throws IOException{
            return R90OfflineProducerContinuity.initialize(
                journal,accounts,generations,sessions,owner);
        }
        R90OfflineProducerContinuity.Session restart(
            R64OfflineSessionOwnership.Token owner)throws IOException{
            return R90OfflineProducerContinuity.restart(
                journal,accounts,generations,sessions,owner);
        }
        long inspectProducer(R64OfflineSessionOwnership.Token owner)
            throws IOException{
            return R90OfflineProducerContinuity
                .inspectCommittedOrdinalForTest(
                    journal,accounts,generations,sessions,owner);
        }
        Path producerFile(){
            return sessions.resolve(USER+".r90-producer.properties");
        }
        Properties producer()throws IOException{
            Properties p=new Properties();
            try(InputStream input=Files.newInputStream(producerFile())){
                p.load(input);
            }
            return p;
        }
        Properties watermark()throws IOException{
            Properties p=new Properties();
            try(InputStream input=Files.newInputStream(
                sessions.resolve(USER+".r89-watermark.properties"))){
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
                Files.readAllBytes(sessions.resolve(
                    USER+".r89-watermark.properties")),
                Files.readAllBytes(producerFile()));
        }
    }
    private R90OfflineProducerContinuityTest(){}
}
