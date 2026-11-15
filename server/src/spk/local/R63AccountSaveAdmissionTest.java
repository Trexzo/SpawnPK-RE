package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Isolated R63 account generation, OS lock, stale token and crash tests. */
public final class R63AccountSaveAdmissionTest {
    private static final UUID PAIR=UUID.fromString(
        "11111111-2222-4333-8444-555555555555"
    );

    public static void main(String[] args)throws Exception{
        Path scratch=Files.createTempDirectory("spk-r63-fence-");
        try{
            Path journal=scratch.resolve("journal");
            Path accounts=scratch.resolve("accounts");
            Path states=scratch.resolve("states");
            Files.createDirectory(journal);
            Files.createDirectory(accounts);
            Files.createDirectory(states);
            R60TradePairCoordinator.initializeEmpty(journal);

            FilePlayerRepository repo=repository(accounts);
            PlayerSnapshot oldA=snapshot("opensrc",995,1000);
            PlayerSnapshot oldB=snapshot("src",385,2);
            repo.save(oldA);
            repo.save(oldB);

            blocked(journal,accounts,states,"opensrc",
                R63AccountSaveAdmission.Status.BLOCK_INDETERMINATE);
            R63AccountSaveAdmission.Token first=
                R63AccountSaveAdmission.initializeExisting(
                    journal,accounts,states,"opensrc"
                );
            R63AccountSaveAdmission.Token bFirst=
                R63AccountSaveAdmission.initializeExisting(
                    journal,accounts,states,"src"
                );
            require(first.generation==1L && first.journalRevision==1L,
                "first account not initialized under revision 1");
            require(bFirst.generation==1L,
                "second account initial generation not one");
            expectIOException(()->R63AccountSaveAdmission.initializeExisting(
                journal,accounts,states,"opensrc"
            ),"already registered");
            require(ready(journal,accounts,states,"opensrc").generation==1L,
                "initial generation read wrong");

            PlayerSnapshot proposed=bump(oldA,385);
            R63AccountSaveAdmission.Token second=
                R63AccountSaveAdmission.saveIfCurrent(
                    journal,accounts,states,first,proposed,false
                );
            require(second.generation==2L &&
                    second.journalRevision==1L &&
                    second.snapshotDigest.equals(
                        R57TradePairJournal.digest(proposed)),
                "atomic offline generation advance incorrect");
            require(ready(journal,accounts,states,"opensrc").generation==2L,
                "post-save generation not persisted");

            byte[] stableA=Files.readAllBytes(
                accounts.resolve("opensrc.properties")
            );
            expectIOException(()->R63AccountSaveAdmission.saveIfCurrent(
                journal,accounts,states,first,bump(proposed,560),false
            ),"stale generation");
            expectIOException(()->R63AccountSaveAdmission.saveIfCurrent(
                journal,accounts,states,second,proposed,false
            ),"unchanged snapshot");
            require(Arrays.equals(stableA,Files.readAllBytes(
                accounts.resolve("opensrc.properties"))),
                "stale token changed canonical account");

            // A distinct JVM holds the SAME R60 OS lock. Both offline
            // inspection and save must refuse while it is held.
            holdSecondJvmLock(scratch,journal,()->{
                blocked(journal,accounts,states,"opensrc",
                    R63AccountSaveAdmission.Status.BLOCK_INDETERMINATE);
                expectIOException(()->R63AccountSaveAdmission.saveIfCurrent(
                    journal,accounts,states,second,
                    bump(proposed,560),false
                ),"R60_LOCK_BUSY_OR_OVERLAPPING");
                require(Arrays.equals(stableA,Files.readAllBytes(
                    accounts.resolve("opensrc.properties"))),
                    "concurrent holder allowed account publication");
            });
            require(ready(journal,accounts,states,"opensrc").generation==2L,
                "worker lock release did not restore readiness");

            // Register a PREPARED pair for src while opensrc is unrelated.
            // src is BLOCKED; opensrc still passes a new journal check,
            // but its old revision-1 token can no longer authorize a save.
            PlayerSnapshot preparedA=snapshot("src",995,1);
            PlayerSnapshot preparedB=snapshot("zpeer",385,1);
            long registered=R60TradePairCoordinator.registerPrepared(
                journal,1L,Collections.emptyList(),PAIR,
                preparedA,bump(preparedA,560),
                preparedB,bump(preparedB,560)
            );
            require(registered==2L,"paired registration did not advance");
            blocked(journal,accounts,states,"src",
                R63AccountSaveAdmission.Status.BLOCK_PENDING_PAIR);
            byte[] bBefore=Files.readAllBytes(
                accounts.resolve("src.properties")
            );
            expectIOException(()->R63AccountSaveAdmission.saveIfCurrent(
                journal,accounts,states,bFirst,bump(oldB,995),false
            ),"blocked or indeterminate");
            require(Arrays.equals(bBefore,Files.readAllBytes(
                accounts.resolve("src.properties"))),
                "pending trade allowed second account autosave");

            R63AccountSaveAdmission.Token current=
                ready(journal,accounts,states,"opensrc");
            require(current.journalRevision==2L &&
                    current.generation==2L,
                "unrelated account did not see revised index");
            expectIOException(()->R63AccountSaveAdmission.saveIfCurrent(
                journal,accounts,states,second,bump(proposed,560),false
            ),"stale generation");
            R63AccountSaveAdmission.Token third=
                R63AccountSaveAdmission.saveIfCurrent(
                    journal,accounts,states,current,bump(proposed,560),false
                );
            require(third.generation==3L &&
                    third.journalRevision==2L,
                "refreshed token could not advance generation");

            Path ledger=states.resolve(
                "opensrc.r63-generation.properties"
            );
            Path stage=states.resolve(
                ".opensrc.r63-generation.properties.r16-fixture.stage"
            );
            Files.write(stage,new byte[]{17,11,5});
            blocked(journal,accounts,states,"opensrc",
                R63AccountSaveAdmission.Status.BLOCK_INDETERMINATE);
            require(Files.size(stage)==3L,
                "staged ledger evidence removed or adopted");
            Files.delete(stage); // solely test-owned
            require(ready(journal,accounts,states,"opensrc").generation==3L,
                "staged ledger cleanup did not restore readiness");

            // A noncooperative direct profile save causes a hash mismatch.
            // It cannot become a new trusted generation automatically.
            PlayerSnapshot unexpected=bump(bump(proposed,560),565);
            repo.save(unexpected);
            byte[] foreignSave=Files.readAllBytes(
                accounts.resolve("opensrc.properties")
            );
            blocked(journal,accounts,states,"opensrc",
                R63AccountSaveAdmission.Status.BLOCK_INDETERMINATE);
            require(Arrays.equals(foreignSave,Files.readAllBytes(
                accounts.resolve("opensrc.properties"))),
                "unknown autosave overwritten by admission probe");

            // Test-owned separate directory for a hard-stop gap. The worker
            // terminates abruptly after its canonical save, before the
            // generation ledger can advance. No account is auto-repaired.
            Path gap=scratch.resolve("hardstop");
            Path gapJournal=gap.resolve("journal");
            Path gapAccounts=gap.resolve("accounts");
            Path gapStates=gap.resolve("states");
            Files.createDirectories(gapJournal);
            Files.createDirectories(gapAccounts);
            Files.createDirectories(gapStates);
            R60TradePairCoordinator.initializeEmpty(gapJournal);
            FilePlayerRepository gapRepo=repository(gapAccounts);
            PlayerSnapshot gapOld=snapshot("opensrc",995,10);
            gapRepo.save(gapOld);
            R63AccountSaveAdmission.Token gapToken=
                R63AccountSaveAdmission.initializeExisting(
                    gapJournal,gapAccounts,gapStates,"opensrc"
                );
            runHardStoppedSaveWorker(
                gapJournal,gapAccounts,gapStates
            );
            PlayerSnapshot savedAfterHalt=gapRepo.load("opensrc")
                .orElseThrow(()->new AssertionError("worker deleted profile"));
            require(!R57TradePairJournal.digest(savedAfterHalt).equals(
                    R57TradePairJournal.digest(gapOld)),
                "worker did not write the first canonical snapshot");
            byte[] gapEvidence=Files.readAllBytes(
                gapAccounts.resolve("opensrc.properties")
            );
            blocked(gapJournal,gapAccounts,gapStates,"opensrc",
                R63AccountSaveAdmission.Status.BLOCK_INDETERMINATE);
            expectIOException(()->R63AccountSaveAdmission.saveIfCurrent(
                gapJournal,gapAccounts,gapStates,gapToken,
                bump(gapOld,560),false
            ),"blocked or indeterminate");
            require(Arrays.equals(gapEvidence,Files.readAllBytes(
                gapAccounts.resolve("opensrc.properties"))),
                "abrupt gap evidence was replaced");
            byte[] ledgerGap=Files.readAllBytes(gapStates.resolve(
                "opensrc.r63-generation.properties"
            ));
            require(ledgerGap.length>0L,
                "ledger unexpectedly removed after worker death");

            System.out.println(
                "R63_OFFLINE_GENERATION_SAVE_FENCE_PASS "+
                "realCrossJvmLockBlocksSaveAndRead=true "+
                "singleAccountGenerationAdvanceVerified=true "+
                "staleGenerationAndJournalRevisionRefused=true "+
                "pendingTradeBlocksCooperativeAutosave=true "+
                "ambiguousLedgerStagePreserved=true "+
                "unexpectedAutosaveRefusedWithoutClobber=true "+
                "abruptWorkerSaveBeforeLedgerFailsClosed=true "+
                "noAutomaticRepairOrProductionLogin=true "+
                "noPhysicalPowerLossGuarantee=true"
            );
        }finally{
            try(java.util.stream.Stream<Path> entries=Files.walk(scratch)){
                entries.sorted(Comparator.reverseOrder()).forEach(path->{
                    try{Files.deleteIfExists(path);}
                    catch(IOException error){
                        throw new IllegalStateException(
                            "test-only cleanup "+path,error
                        );
                    }
                });
            }
        }
    }

    private static FilePlayerRepository repository(Path root){
        return new FilePlayerRepository(
            username->root.resolve(username+".properties")
        );
    }

    private static PlayerSnapshot snapshot(
        String username,int item,int count
    )throws IOException{
        WorldPlayer player=new WorldPlayer();
        ServerPacketWriter packets=new ServerPacketWriter(
            new OutboundPacketQueue(),
            new IsaacCipher(new int[]{1,2,3,4})
        );
        player.bank().spawnItem(item,count,packets);
        return PlayerSnapshotCodec.capture(username,player);
    }

    private static PlayerSnapshot bump(
        PlayerSnapshot original,int item
    )throws IOException{
        WorldPlayer copied=new WorldPlayer();
        PlayerSnapshotCodec.applyValidated(original,copied);
        ServerPacketWriter packets=new ServerPacketWriter(
            new OutboundPacketQueue(),
            new IsaacCipher(new int[]{1,2,3,4})
        );
        copied.bank().spawnItem(item,1,packets);
        return PlayerSnapshotCodec.capture(original.username(),copied);
    }

    private static R63AccountSaveAdmission.Token ready(
        Path journal,Path accounts,Path states,String username
    ){
        R63AccountSaveAdmission.Check result=
            R63AccountSaveAdmission.inspect(
                journal,accounts,states,username
            );
        require(result.status==
                R63AccountSaveAdmission.Status
                    .READY_FOR_OTHER_OFFLINE_CHECKS &&
                result.token!=null &&
                !result.loginAuthorized(),
            "expected offline readiness for "+username+
                " but got "+result.status+" "+result.reason);
        return result.token;
    }

    private static void blocked(
        Path journal,Path accounts,Path states,
        String username,R63AccountSaveAdmission.Status expected
    ){
        R63AccountSaveAdmission.Check result=
            R63AccountSaveAdmission.inspect(
                journal,accounts,states,username
            );
        require(result.status==expected &&
                result.token==null &&
                !result.loginAuthorized() &&
                !result.automaticRecoveryAttempted(),
            "account "+username+" improperly admitted: "+
                result.status+" "+result.reason);
    }

    @FunctionalInterface interface Action{void run()throws Exception;}

    private static void holdSecondJvmLock(
        Path scratch,Path journal,Action action
    )throws Exception{
        Path ready=scratch.resolve("lock-ready");
        Path release=scratch.resolve("lock-release");
        Process worker=new ProcessBuilder(
            javaExecutable(),"-cp",
            System.getProperty("java.class.path"),
            "spk.local.R60TradePairLockWorker",
            journal.toString(),ready.toString(),
            release.toString(),"hold"
        ).redirectErrorStream(true).start();
        try{
            long deadline=System.nanoTime()+
                TimeUnit.SECONDS.toNanos(9);
            while(!Files.exists(ready)){
                if(!worker.isAlive()||System.nanoTime()>deadline)
                    throw new AssertionError(
                        "cross-JVM lock worker not ready"
                    );
                Thread.sleep(20L);
            }
            action.run();
        }finally{
            Files.write(release,new byte[]{1});
            if(!worker.waitFor(12,TimeUnit.SECONDS)){
                worker.destroyForcibly();
                worker.waitFor(5,TimeUnit.SECONDS);
                throw new AssertionError(
                    "test-owned lock worker timed out"
                );
            }
            require(worker.exitValue()==0,
                "lock holder failed after release");
        }
    }

    private static void runHardStoppedSaveWorker(
        Path journal,Path accounts,Path states
    )throws Exception{
        Process worker=new ProcessBuilder(
            javaExecutable(),"-cp",
            System.getProperty("java.class.path"),
            "spk.local.R63AccountSaveCrashWorker",
            journal.toString(),accounts.toString(),states.toString()
        ).redirectErrorStream(true).start();
        if(!worker.waitFor(20,TimeUnit.SECONDS)){
            worker.destroyForcibly();
            worker.waitFor(5,TimeUnit.SECONDS);
            throw new AssertionError(
                "test-owned account save worker timed out"
            );
        }
        String output=new String(
            worker.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8
        );
        require(worker.exitValue()==93,
            "worker did not abruptly terminate at injected gap: "+
                worker.exitValue()+" "+output);
    }

    private static String javaExecutable(){
        return Paths.get(System.getProperty("java.home"),
            "bin",System.getProperty("os.name").toLowerCase()
                .contains("win")?"java.exe":"java").toString();
    }

    private static void expectIOException(
        Action action,String expectedMessage
    )throws Exception{
        try{
            action.run();
            throw new AssertionError(
                "expected refusal "+expectedMessage
            );
        }catch(IOException expected){
            require(expected.getMessage().contains(expectedMessage),
                "wrong refusal "+expected+" wanted "+expectedMessage);
        }
    }

    private static void require(boolean ok,String why){
        if(!ok)throw new AssertionError(why);
    }
    private R63AccountSaveAdmissionTest(){}
}

/** Only this explicitly test-owned worker JVM terminates via Runtime.halt. */
final class R63AccountSaveCrashWorker {
    public static void main(String[] args)throws Exception{
        if(args.length!=3)throw new IllegalArgumentException("args");
        Path journal=Paths.get(args[0]);
        Path accounts=Paths.get(args[1]);
        Path states=Paths.get(args[2]);
        R63AccountSaveAdmission.Check check=
            R63AccountSaveAdmission.inspect(
                journal,accounts,states,"opensrc"
            );
        if(check.status!=
                R63AccountSaveAdmission.Status
                    .READY_FOR_OTHER_OFFLINE_CHECKS)
            throw new AssertionError("worker ledger not ready");
        FilePlayerRepository repository=new FilePlayerRepository(
            user->accounts.resolve(user+".properties")
        );
        PlayerSnapshot prior=repository.load("opensrc").orElseThrow(
            ()->new AssertionError("worker account not found"));
        WorldPlayer mutated=new WorldPlayer();
        PlayerSnapshotCodec.applyValidated(prior,mutated);
        ServerPacketWriter writer=new ServerPacketWriter(
            new OutboundPacketQueue(),
            new IsaacCipher(new int[]{8,7,6,5})
        );
        mutated.bank().spawnItem(385,1,writer);
        PlayerSnapshot proposed=PlayerSnapshotCodec.capture(
            "opensrc",mutated
        );
        try{
            R63AccountSaveAdmission.saveIfCurrent(
                journal,accounts,states,check.token,proposed,true
            );
            throw new AssertionError(
                "injected gap did not interrupt ledger publication"
            );
        }catch(IOException expected){
            if(!expected.getMessage().contains(
                "R63_INJECTED_AFTER_CANONICAL_SAVE_BEFORE_LEDGER"))
                throw expected;
            Runtime.getRuntime().halt(93);
            throw new AssertionError("worker halt returned");
        }
    }
}
