package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** R64 offline stale-owner, logout and crash-gap regression with real JVMs. */
public final class R64OfflineSessionOwnershipTest {
    private static final UUID UNRELATED=UUID.fromString(
        "11111111-2222-4333-8444-555555555555"
    );
    private static final UUID PENDING=UUID.fromString(
        "77777777-8888-4999-aaaa-bbbbbbbbbbbb"
    );

    public static void main(String[] args)throws Exception{
        Path scratch=Files.createTempDirectory("spk-r64-session-fence-");
        try{
            Path root=scratch.resolve("ordinary");
            Path journal=root.resolve("journal");
            Path accounts=root.resolve("accounts");
            Path ledger=root.resolve("generations");
            Path sessions=root.resolve("sessions");
            createDirs(root,journal,accounts,ledger,sessions);
            R60TradePairCoordinator.initializeEmpty(journal);

            FilePlayerRepository repo=repository(accounts);
            PlayerSnapshot original=snapshot("opensrc",995,100);
            repo.save(original);
            R63AccountSaveAdmission.initializeExisting(
                journal,accounts,ledger,"opensrc"
            );

            blocked(journal,accounts,ledger,sessions,"opensrc",
                R64OfflineSessionOwnership.Status.BLOCK_INDETERMINATE);
            R64OfflineSessionOwnership.Token old=
                R64OfflineSessionOwnership.claimFirst(
                    journal,accounts,ledger,sessions,"opensrc"
                );
            require(old.epoch==1L&&old.account.generation==1L,
                "initial owner epoch or account generation invalid");
            ready(journal,accounts,ledger,sessions,"opensrc",old.epoch);

            expectDenied(()->R64OfflineSessionOwnership.claimFirst(
                journal,accounts,ledger,sessions,"opensrc"
            ),"already initialized");
            PlayerSnapshot changed=bump(original,385);
            R64OfflineSessionOwnership.Token owner1=
                R64OfflineSessionOwnership.saveOwned(
                    journal,accounts,ledger,sessions,
                    old,changed,false
                );
            require(owner1.account.generation==2L &&
                    owner1.epoch==1L,
                "session save did not atomically advance owner token");
            expectDenied(()->R64OfflineSessionOwnership.saveOwned(
                journal,accounts,ledger,sessions,
                old,bump(changed,560),false
            ),"stale generation");

            // A completely separate JVM performs EXPLICIT ACTIVE takeover.
            // It sees the same persisted owner generation and uses the same
            // R60 OS lock. The previous owner must NEVER save afterward.
            runWorker("takeover",journal,accounts,ledger,sessions,null);
            R64OfflineSessionOwnership.Token successor=
                ready(journal,accounts,ledger,sessions,"opensrc",2L);
            require(!successor.owner.equals(owner1.owner) &&
                    successor.account.generation==2L,
                "new JVM did not replace the owner UUID");
            byte[] afterTakeover=Files.readAllBytes(
                accounts.resolve("opensrc.properties")
            );
            runWorker("late-save",journal,accounts,ledger,sessions,owner1);
            expectDenied(()->R64OfflineSessionOwnership.releaseOwner(
                journal,accounts,ledger,sessions,owner1
            ),"stale owner");
            expectDenied(()->R64OfflineSessionOwnership.saveOwned(
                journal,accounts,ledger,sessions,
                owner1,bump(changed,560),false
            ),"stale owner");
            require(Arrays.equals(afterTakeover,Files.readAllBytes(
                    accounts.resolve("opensrc.properties"))),
                "old owner clobbered new session's account");

            R64OfflineSessionOwnership.Token savedSuccessor=
                R64OfflineSessionOwnership.saveOwned(
                    journal,accounts,ledger,sessions,
                    successor,bump(changed,560),false
                );
            require(savedSuccessor.epoch==2L &&
                    savedSuccessor.account.generation==3L,
                "new owner was unable to save current account");

            R64OfflineSessionOwnership.Token released=
                R64OfflineSessionOwnership.releaseOwner(
                    journal,accounts,ledger,sessions,savedSuccessor
                );
            require(released.phase==
                    R64OfflineSessionOwnership.Phase.RELEASED,
                "logout did not fence previous owner");
            blocked(journal,accounts,ledger,sessions,"opensrc",
                R64OfflineSessionOwnership.Status.BLOCK_RELEASED);
            expectDenied(()->R64OfflineSessionOwnership.saveOwned(
                journal,accounts,ledger,sessions,
                savedSuccessor,bump(bump(changed,560),565),false
            ),"stale owner");
            expectDenied(()->R64OfflineSessionOwnership.releaseOwner(
                journal,accounts,ledger,sessions,savedSuccessor
            ),"stale owner");

            R64OfflineSessionOwnership.Token third=
                R64OfflineSessionOwnership.replaceOwner(
                    journal,accounts,ledger,sessions,released
                );
            require(third.epoch==3L &&
                    third.phase==R64OfflineSessionOwnership.Phase.ACTIVE,
                "explicit post-logout new owner not granted");
            expectDenied(()->R64OfflineSessionOwnership.saveOwned(
                journal,accounts,ledger,sessions,
                released,bump(changed,565),false
            ),"stale owner");
            ready(journal,accounts,ledger,sessions,"opensrc",3L);

            // An ambiguous session writer stage is never guessed stale.
            Path stage=sessions.resolve(
                ".opensrc.r64-session.properties.r16-orphan.stage"
            );
            Files.write(stage,new byte[]{1,3,5,7});
            blocked(journal,accounts,ledger,sessions,"opensrc",
                R64OfflineSessionOwnership.Status.BLOCK_INDETERMINATE);
            expectDenied(()->R64OfflineSessionOwnership.saveOwned(
                journal,accounts,ledger,sessions,
                third,bump(bump(changed,560),565),false
            ),"R51_AMBIGUOUS");
            require(Files.size(stage)==4L,
                "orphan session stage was altered or removed");
            Files.delete(stage); // test-owned stage only
            ready(journal,accounts,ledger,sessions,"opensrc",3L);

            // Another real JVM holds the shared journal lock. Even a
            // known owner cannot use it to bypass transaction coordination.
            holdOtherJvmLock(scratch,journal,()->{
                blocked(journal,accounts,ledger,sessions,"opensrc",
                    R64OfflineSessionOwnership.Status.BLOCK_INDETERMINATE);
                expectDenied(()->R64OfflineSessionOwnership.saveOwned(
                    journal,accounts,ledger,sessions,
                    third,bump(bump(changed,560),565),false
                ),"R60_LOCK_BUSY_OR_OVERLAPPING");
            });
            ready(journal,accounts,ledger,sessions,"opensrc",3L);

            // A journal revision for unrelated participants invalidates
            // this owner snapshot until a separately authorized rebind
            // protocol exists. R64 never silently refreshes a stale owner.
            registerPair(journal,1L,Collections.emptyList(),UNRELATED,
                "othera","otherb");
            blocked(journal,accounts,ledger,sessions,"opensrc",
                R64OfflineSessionOwnership.Status.BLOCK_INDETERMINATE);
            expectDenied(()->R64OfflineSessionOwnership.saveOwned(
                journal,accounts,ledger,sessions,
                third,bump(bump(changed,560),565),false
            ),"stale generation");
            // A PREPARED pair directly naming the owner then blocks even
            // read-side session admission and ordinary saves.
            registerPair(journal,2L,Collections.singletonList(UNRELATED),
                PENDING,"opensrc","zpeer");
            blocked(journal,accounts,ledger,sessions,"opensrc",
                R64OfflineSessionOwnership.Status.BLOCK_PENDING_PAIR);

            // Separate disposable fixture demonstrates the later failure
            // window: canonical and R63 ledger saved, but owner token not
            // published. The worker halts abruptly; no implicit adoption.
            Path gap=scratch.resolve("crash-gap");
            Path gapJournal=gap.resolve("journal");
            Path gapAccounts=gap.resolve("accounts");
            Path gapLedger=gap.resolve("generations");
            Path gapSessions=gap.resolve("sessions");
            createDirs(gap,gapJournal,gapAccounts,gapLedger,gapSessions);
            R60TradePairCoordinator.initializeEmpty(gapJournal);
            FilePlayerRepository gapRepo=repository(gapAccounts);
            PlayerSnapshot beforeGap=snapshot("opensrc",995,10);
            gapRepo.save(beforeGap);
            R63AccountSaveAdmission.initializeExisting(
                gapJournal,gapAccounts,gapLedger,"opensrc"
            );
            R64OfflineSessionOwnership.Token gapOld=
                R64OfflineSessionOwnership.claimFirst(
                    gapJournal,gapAccounts,gapLedger,gapSessions,"opensrc"
                );
            runWorker("hard-stop",gapJournal,gapAccounts,gapLedger,
                gapSessions,null);
            R63AccountSaveAdmission.Check ledgerReady=
                R63AccountSaveAdmission.inspect(
                    gapJournal,gapAccounts,gapLedger,"opensrc"
                );
            require(ledgerReady.status==
                    R63AccountSaveAdmission.Status.READY_FOR_OTHER_OFFLINE_CHECKS &&
                    ledgerReady.token.generation==2L,
                "hard stop did not advance underlying account ledger");
            byte[] beforeReview=Files.readAllBytes(
                gapAccounts.resolve("opensrc.properties")
            );
            blocked(gapJournal,gapAccounts,gapLedger,gapSessions,
                "opensrc",R64OfflineSessionOwnership.Status.BLOCK_INDETERMINATE);
            expectDenied(()->R64OfflineSessionOwnership.saveOwned(
                gapJournal,gapAccounts,gapLedger,gapSessions,
                gapOld,bump(beforeGap,565),false
            ),"stale generation");
            expectDenied(()->R64OfflineSessionOwnership.replaceOwner(
                gapJournal,gapAccounts,gapLedger,gapSessions,gapOld
            ),"stale generation");
            require(Arrays.equals(beforeReview,Files.readAllBytes(
                gapAccounts.resolve("opensrc.properties"))),
                "crash gap owner fence changed account evidence");

            System.out.println(
                "R64_OFFLINE_SESSION_OWNERSHIP_FENCE_PASS "+
                "freshJvmExplicitTakeoverSupersedesOldOwner=true "+
                "oldJvmLateSaveDeniedWithoutAccountOverwrite=true "+
                "logoutAndPostLogoutSaveDenied=true "+
                "newOwnerEpochMonotonic=true "+
                "ambiguousSessionStagePreservedAndDenied=true "+
                "crossJvmLockDeniesSessionReadsAndSaves=true "+
                "journalRevisionAndPendingPairFailClosed=true "+
                "hardStoppedAfterLedgerBeforeOwnerBlocksAll=true "+
                "noLiveSessionHookOrAutomaticRepair=true "+
                "physicalPowerLossDurabilityNotProven=true"
            );
        }finally{
            try(java.util.stream.Stream<Path> paths=Files.walk(scratch)){
                paths.sorted(Comparator.reverseOrder()).forEach(path->{
                    try{Files.deleteIfExists(path);}
                    catch(IOException fail){
                        throw new IllegalStateException(
                            "test-owned cleanup "+path,fail
                        );
                    }
                });
            }
        }
    }

    private static void createDirs(Path... paths)throws IOException{
        for(Path path:paths)Files.createDirectory(path);
    }

    private static FilePlayerRepository repository(Path accounts){
        return new FilePlayerRepository(
            user->accounts.resolve(user+".properties")
        );
    }

    private static PlayerSnapshot snapshot(
        String name,int item,int count)throws IOException{
        WorldPlayer player=new WorldPlayer();
        ServerPacketWriter writer=new ServerPacketWriter(
            new OutboundPacketQueue(),
            new IsaacCipher(new int[]{1,2,3,4})
        );
        player.bank().spawnItem(item,count,writer);
        return PlayerSnapshotCodec.capture(name,player);
    }

    private static PlayerSnapshot bump(PlayerSnapshot original,int item)
            throws IOException{
        WorldPlayer copied=new WorldPlayer();
        PlayerSnapshotCodec.applyValidated(original,copied);
        ServerPacketWriter writer=new ServerPacketWriter(
            new OutboundPacketQueue(),
            new IsaacCipher(new int[]{1,2,3,4})
        );
        copied.bank().spawnItem(item,1,writer);
        return PlayerSnapshotCodec.capture(original.username(),copied);
    }

    private static void registerPair(Path journal,long previous,
                                     java.util.List<UUID> ids,UUID id,
                                     String a,String b)throws IOException{
        PlayerSnapshot beforeA=snapshot(a,995,2);
        PlayerSnapshot beforeB=snapshot(b,385,2);
        long revision=R60TradePairCoordinator.registerPrepared(
            journal,previous,ids,id,
            beforeA,bump(beforeA,560),
            beforeB,bump(beforeB,565)
        );
        require(revision==previous+1L,
            "registered pair revision mismatch");
    }

    private static R64OfflineSessionOwnership.Token ready(
        Path journal,Path accounts,Path generations,Path sessions,
        String user,long expectedEpoch
    ){
        R64OfflineSessionOwnership.Review r=
            R64OfflineSessionOwnership.inspect(
                journal,accounts,generations,sessions,user
            );
        require(r.status==
                R64OfflineSessionOwnership.Status
                    .READY_FOR_OTHER_OFFLINE_CHECKS &&
                r.token!=null&&r.token.epoch==expectedEpoch &&
                !r.loginAuthorized() &&
                !r.automaticRecoveryAttempted(),
            "owner inspection not ready "+r.status+" "+r.reason);
        return r.token;
    }

    private static void blocked(
        Path journal,Path accounts,Path generations,Path sessions,
        String user,R64OfflineSessionOwnership.Status wanted
    ){
        R64OfflineSessionOwnership.Review r=
            R64OfflineSessionOwnership.inspect(
                journal,accounts,generations,sessions,user
            );
        require(r.status==wanted&&r.token==null&&
                !r.loginAuthorized()&&!r.accountFilesModified(),
            "invalid session still ready "+r.status+" wanted "+wanted);
    }

    @FunctionalInterface private interface Action {
        void run()throws Exception;
    }
    private static void expectDenied(Action task,String reason)throws Exception{
        try{
            task.run();
            throw new AssertionError("expected refusal "+reason);
        }catch(IOException denied){
            require(denied.getMessage().contains(reason),
                "wrong refusal "+denied+" wanted "+reason);
        }
    }

    private static void holdOtherJvmLock(
        Path scratch,Path journal,Action run
    )throws Exception{
        Path ready=scratch.resolve("r64-lock-ready");
        Path release=scratch.resolve("r64-lock-release");
        Process worker=new ProcessBuilder(
            javaExe(),"-cp",System.getProperty("java.class.path"),
            "spk.local.R60TradePairLockWorker",
            journal.toString(),ready.toString(),
            release.toString(),"hold"
        ).redirectErrorStream(true).start();
        try{
            long deadline=System.nanoTime()+
                TimeUnit.SECONDS.toNanos(12L);
            while(!Files.exists(ready)){
                if(!worker.isAlive()||System.nanoTime()>deadline)
                    throw new AssertionError("cross-process lock not acquired");
                Thread.sleep(20L);
            }
            run.run();
        }finally{
            Files.write(release,new byte[]{1});
            if(!worker.waitFor(15,TimeUnit.SECONDS)){
                worker.destroyForcibly();
                worker.waitFor(5,TimeUnit.SECONDS);
                throw new AssertionError("test-owned lock holder hung");
            }
            require(worker.exitValue()==0,
                "test-owned lock holder failed");
        }
    }

    private static void runWorker(
        String mode,Path journal,Path accounts,
        Path ledger,Path sessions,R64OfflineSessionOwnership.Token token
    )throws Exception{
        java.util.List<String> args=new java.util.ArrayList<>();
        args.add(javaExe());args.add("-cp");
        args.add(System.getProperty("java.class.path"));
        args.add("spk.local.R64SessionFenceWorker");
        args.add(mode);
        for(Path path:new Path[]{journal,accounts,ledger,sessions})
            args.add(path.toString());
        if(token!=null){
            args.add(token.username);
            args.add(token.owner.toString());
            args.add(Long.toString(token.epoch));
            args.add(token.phase.name());
            args.add(Long.toString(token.account.generation));
            args.add(Long.toString(token.account.journalRevision));
            args.add(token.account.snapshotDigest);
        }
        Process process=new ProcessBuilder(args)
            .redirectErrorStream(true).start();
        if(!process.waitFor(25,TimeUnit.SECONDS)){
            process.destroyForcibly();
            process.waitFor(5,TimeUnit.SECONDS);
            throw new AssertionError("test-owned session worker timed out");
        }
        String output=new String(
            process.getInputStream().readAllBytes(),StandardCharsets.UTF_8
        );
        int code="hard-stop".equals(mode)?94:0;
        require(process.exitValue()==code &&
                ("hard-stop".equals(mode)||
                 output.contains("R64_SESSION_WORKER_PASS")),
            "session worker "+mode+" failed exit="+process.exitValue()+
            " output="+output);
    }

    private static String javaExe(){
        return Paths.get(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase().contains("win")?
                "java.exe":"java").toString();
    }

    private static void require(boolean valid,String message){
        if(!valid)throw new AssertionError(message);
    }

    private R64OfflineSessionOwnershipTest(){}
}

/** Separate test-owned JVM for takeover, stale save and abrupt exit. */
final class R64SessionFenceWorker {
    public static void main(String[] args)throws Exception{
        if(args.length!=5&&args.length!=12)
            throw new IllegalArgumentException("R64 fixture args");
        String mode=args[0];
        Path journal=Paths.get(args[1]);
        Path accounts=Paths.get(args[2]);
        Path generations=Paths.get(args[3]);
        Path sessions=Paths.get(args[4]);
        if("takeover".equals(mode)){
            R64OfflineSessionOwnership.Review before=
                R64OfflineSessionOwnership.inspect(
                    journal,accounts,generations,sessions,"opensrc"
                );
            if(before.status!=
                    R64OfflineSessionOwnership.Status
                        .READY_FOR_OTHER_OFFLINE_CHECKS)
                throw new AssertionError("prior owner not readable");
            R64OfflineSessionOwnership.Token after=
                R64OfflineSessionOwnership.replaceOwner(
                    journal,accounts,generations,sessions,before.token
                );
            if(after.epoch!=before.token.epoch+1L ||
               after.owner.equals(before.token.owner))
                throw new AssertionError("separate JVM takeover ineffective");
        }else if("late-save".equals(mode)){
            if(args.length!=12)
                throw new IllegalArgumentException("missing old owner token");
            R64OfflineSessionOwnership.Token stale=
                new R64OfflineSessionOwnership.Token(
                    args[5],UUID.fromString(args[6]),
                    Long.parseLong(args[7]),
                    R64OfflineSessionOwnership.Phase.valueOf(args[8]),
                    new R63AccountSaveAdmission.Token(
                        args[5],Long.parseLong(args[9]),
                        Long.parseLong(args[10]),args[11]
                    )
                );
            FilePlayerRepository repo=new FilePlayerRepository(
                user->accounts.resolve(user+".properties")
            );
            PlayerSnapshot original=repo.load("opensrc").orElseThrow(
                ()->new AssertionError("missing test canonical"));
            WorldPlayer player=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(original,player);
            ServerPacketWriter writer=new ServerPacketWriter(
                new OutboundPacketQueue(),
                new IsaacCipher(new int[]{8,7,6,5})
            );
            player.bank().spawnItem(565,1,writer);
            try{
                R64OfflineSessionOwnership.saveOwned(
                    journal,accounts,generations,sessions,stale,
                    PlayerSnapshotCodec.capture("opensrc",player),false
                );
                throw new AssertionError("stale JVM published an account");
            }catch(IOException denied){
                if(!denied.getMessage().contains(
                    "R64_SESSION_DENIED stale owner"))
                    throw denied;
            }
        }else if("hard-stop".equals(mode)){
            R64OfflineSessionOwnership.Review before=
                R64OfflineSessionOwnership.inspect(
                    journal,accounts,generations,sessions,"opensrc"
                );
            if(before.status!=
                    R64OfflineSessionOwnership.Status
                        .READY_FOR_OTHER_OFFLINE_CHECKS)
                throw new AssertionError("crash fixture owner not ready");
            FilePlayerRepository repo=new FilePlayerRepository(
                user->accounts.resolve(user+".properties")
            );
            PlayerSnapshot original=repo.load("opensrc").orElseThrow(
                ()->new AssertionError("missing test canonical"));
            WorldPlayer player=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(original,player);
            ServerPacketWriter writer=new ServerPacketWriter(
                new OutboundPacketQueue(),
                new IsaacCipher(new int[]{8,7,6,5})
            );
            player.bank().spawnItem(385,1,writer);
            try{
                R64OfflineSessionOwnership.saveOwned(
                    journal,accounts,generations,sessions,
                    before.token,
                    PlayerSnapshotCodec.capture("opensrc",player),
                    true
                );
                throw new AssertionError("missing test fault after R63");
            }catch(IOException fault){
                if(!fault.getMessage().contains(
                    "R64_INJECTED_AFTER_LEDGER_BEFORE_OWNER_RECORD"))
                    throw fault;
                // ONLY this explicitly test-owned child, never a live JVM.
                Runtime.getRuntime().halt(94);
                throw new AssertionError("Runtime.halt returned");
            }
        }else{
            throw new IllegalArgumentException("unknown mode");
        }
        System.out.println("R64_SESSION_WORKER_PASS mode="+mode);
    }
}
