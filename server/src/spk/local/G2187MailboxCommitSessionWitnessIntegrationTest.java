package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/** G21.87 real World load + seven-object COMMIT forensic continuity. */
public final class G2187MailboxCommitSessionWitnessIntegrationTest {
    private static final class Seed {
        final WorldPlayer live;
        final MailboxSettlementPostimagePlanner.Proposal plan;
        final PlayerSnapshot terminal;
        Seed(WorldPlayer live,MailboxSettlementPostimagePlanner.Proposal plan){
            this.live=live;this.plan=plan;
            terminal=MailboxAtomicTerminalSnapshot.compose(plan);
        }
    }

    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2187-disk-commit-witness-");
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        StrictDurablePlayerSnapshotWriter strict=
            new StrictDurablePlayerSnapshotWriter(paths);
        MailboxDurableIdempotencyIntentJournal journal=
            new MailboxDurableIdempotencyIntentJournal(paths);
        MailboxGuardedDiskCommitRecord commit=
            new MailboxGuardedDiskCommitRecord(paths);
        FilePlayerRepository repo=new FilePlayerRepository(paths);

        boolean missingStable=false, oldTokenRefused=false;
        boolean ordinaryPreparedAllowed=false, journalPreparedAllowed=false;
        boolean diskCommitChangesWitness=false, diskCommitStable=false;
        boolean validCommitStillVetoed=false, rollbackVetoed=false;
        boolean corruptCommitVetoed=false, corruptChangesWitness=false;
        boolean oversizePreflight=false, symlinkCommitRejected=false;
        boolean appearanceDuringSessionVetoed=false;
        boolean sameBytesInodeSwapRejected=false, stableAfterSwap=false;
        boolean negativeMarkerPreserved=false, noLiveReward=true;
        boolean noTempOrLeaseLeaks=false;
        int cases=0;

        try(World original=World.isolatedForTest(60000L);
            World restarted=World.isolatedForTest(60000L,repo)){
            restarted.start();
            String absent="g2187-missing";
            String missingToken=repo.captureRestartContinuityTokenReadOnly(absent);
            missingStable=missingToken.startsWith("G2187|")&&
                unchanged(new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(absent,missingToken));
            try{
                repo.compareRestartContinuityReadOnly(absent,
                    "G2185|"+missingToken.substring(
                        missingToken.indexOf('|')+1));
            }catch(IllegalArgumentException incompatible){
                oldTokenRefused=true;
            }
            cases++;

            Seed ordinary=seed(original,"g2187-ordinary");
            strict.saveStrict(ordinary.plan.preparedPreimage);
            ordinaryPreparedAllowed=restarted.persistence()
                .load(ordinary.plan.account).isPresent();
            Seed prepared=seed(original,"g2187-prepared");
            strict.saveStrict(prepared.plan.preparedPreimage);
            journal.publishPreparedIntent(prepared.plan);
            journalPreparedAllowed=restarted.persistence()
                .load(prepared.plan.account).isPresent();
            cases+=2;

            Seed done=seed(original,"g2187-committed");
            strict.saveStrict(done.plan.preparedPreimage);
            journal.publishPreparedIntent(done.plan);
            String prior=repo.captureRestartContinuityTokenReadOnly(
                done.plan.account);
            publishTerminalAndCommit(strict,commit,paths,done);
            String next=new FilePlayerRepository(paths)
                .captureRestartContinuityTokenReadOnly(done.plan.account);
            diskCommitChangesWitness=changed(repo
                .compareRestartContinuityReadOnly(done.plan.account,prior));
            MailboxGuardedDiskCommitRecord.Observation record=
                commit.inspect(done.plan.account);
            diskCommitStable=next.startsWith("G2187|")&&
                unchanged(new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(done.plan.account,next))&&
                record.diskCommitRecordMatched&&
                !record.transactionCommitted&&!record.liveApplied&&
                !record.grantAuthorized&&!record.replayAuthorized;
            validCommitStillVetoed=rejectsCommit(
                restarted,done.plan.account);
            strict.saveStrict(done.plan.preparedPreimage);
            // A stale PREPARED snapshot matches the journal again;
            // the durable COMMIT file must nevertheless veto hydration.
            rollbackVetoed=
                MailboxPreparedRestartAdmission.inspect(
                    repo.load(done.plan.account).get()).state==
                    MailboxPreparedRestartAdmission.State
                        .VALID_PREPARED_UNCLAIMED&&
                journal.inspect(done.plan.account).status==
                    MailboxDurableIdempotencyIntentJournal.Status
                        .PREPARED_MATCH_NO_REPLAY&&
                rejectsCommit(restarted,done.plan.account)&&
                changed(repo.compareRestartContinuityReadOnly(
                    done.plan.account,next));
            cases++;

            Seed corrupt=seed(original,"g2187-corrupt");
            strict.saveStrict(corrupt.plan.preparedPreimage);
            journal.publishPreparedIntent(corrupt.plan);
            publishTerminalAndCommit(strict,commit,paths,corrupt);
            String old=repo.captureRestartContinuityTokenReadOnly(
                corrupt.plan.account);
            Path bad=commit.recordPath(corrupt.plan.account);
            byte[] edited=Files.readAllBytes(bad);
            edited[0]^=1;
            Files.write(bad,edited);
            corruptCommitVetoed=rejectsCommit(
                restarted,corrupt.plan.account);
            corruptChangesWitness=changed(repo
                .compareRestartContinuityReadOnly(corrupt.plan.account,old))&&
                commit.inspect(corrupt.plan.account).status==
                    MailboxGuardedDiskCommitRecord.Status
                        .INVALID_RECORD_QUARANTINE;
            cases++;

            Seed large=seed(original,"g2187-large");
            strict.saveStrict(large.plan.preparedPreimage);
            journal.publishPreparedIntent(large.plan);
            publishTerminalAndCommit(strict,commit,paths,large);
            Files.write(commit.recordPath(large.plan.account),new byte[1025]);
            try{
                repo.captureRestartContinuityTokenReadOnly(large.plan.account);
            }catch(IOException bound){
                oversizePreflight=bound.getMessage().contains(
                    "G21.87 RECOVERY_DISK_COMMIT_OVERSIZE_NO_GRANT")&&
                    rejectsCommit(restarted,large.plan.account);
            }
            cases++;

            Seed appearing=seed(original,"g2187-appearing");
            strict.saveStrict(appearing.plan.preparedPreimage);
            journal.publishPreparedIntent(appearing.plan);
            Path inject=commit.recordPath(appearing.plan.account);
            AtomicBoolean injected=new AtomicBoolean();
            // A raw/uncooperative writer introduces a COMMIT marker
            // after account bytes are decoded, before final admission.
            FilePlayerRepository hooked=new FilePlayerRepository(
                paths,a->{},a->{},a->{
                    if(a.equals(appearing.plan.account)){
                        Files.write(inject,"UNCONFIRMED_COMMIT_HOLD"
                            .getBytes(StandardCharsets.US_ASCII));
                        injected.set(true);
                    }
                });
            try(World testWorld=World.isolatedForTest(60000L,hooked)){
                testWorld.start();
                appearanceDuringSessionVetoed=
                    rejectsCommit(testWorld,appearing.plan.account)&&
                    injected.get();
            }
            appearanceDuringSessionVetoed&=
                Files.exists(inject,LinkOption.NOFOLLOW_LINKS)&&
                rejectsCommit(restarted,appearing.plan.account);
            cases++;

            Seed linked=seed(original,"g2187-symlink");
            strict.saveStrict(linked.plan.preparedPreimage);
            journal.publishPreparedIntent(linked.plan);
            publishTerminalAndCommit(strict,commit,paths,linked);
            Path link=commit.recordPath(linked.plan.account);
            try{
                Files.delete(link);
                Files.createSymbolicLink(
                    link,commit.recordPath(corrupt.plan.account));
                boolean forensicVeto=false;
                try{
                    repo.captureRestartContinuityTokenReadOnly(
                        linked.plan.account);
                }catch(IOException denied){
                    forensicVeto=true;
                }
                symlinkCommitRejected=forensicVeto&&
                    rejectsCommit(restarted,linked.plan.account);
            }catch(UnsupportedOperationException|
                    java.nio.file.FileSystemException unsupported){
                symlinkCommitRejected=!Files.isSymbolicLink(link);
            }
            cases++;

            Seed swapped=seed(original,"g2187-swap");
            strict.saveStrict(swapped.plan.preparedPreimage);
            journal.publishPreparedIntent(swapped.plan);
            publishTerminalAndCommit(strict,commit,paths,swapped);
            Path swapFile=commit.recordPath(swapped.plan.account);
            byte[] originalBytes=Files.readAllBytes(swapFile);
            String witness=repo.captureRestartContinuityTokenReadOnly(
                swapped.plan.account);
            AtomicBoolean didSwap=new AtomicBoolean();
            FilePlayerRepository swapRepo=new FilePlayerRepository(
                paths,a->{},a->{},a->{},a->{
                    if(a.equals(swapped.plan.account)&&
                        didSwap.compareAndSet(false,true)){
                        Path tmp=Files.createTempFile(
                            root,"g2187-replace-",".tmp");
                        Files.write(tmp,originalBytes);
                        Files.move(tmp,swapFile,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                    }
                });
            try{
                swapRepo.captureRestartContinuityTokenReadOnly(
                    swapped.plan.account);
            }catch(IOException denied){
                sameBytesInodeSwapRejected=didSwap.get()&&
                    (denied.getMessage().contains(
                        "RECOVERY_OBJECT_REPLACED_NO_GRANT")||
                     denied.getMessage().contains(
                        "RECOVERY_INPLACE_CHANGE_NO_GRANT"));
            }
            stableAfterSwap=didSwap.get()&&
                Arrays.equals(originalBytes,Files.readAllBytes(swapFile))&&
                unchanged(new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(
                        swapped.plan.account,witness));
            cases++;

            Seed held=seed(original,"g2187-negative");
            strict.saveStrict(held.plan.preparedPreimage);
            journal.publishPreparedIntent(held.plan);
            publishTerminalAndCommit(strict,commit,paths,held);
            Path negative=paths.resolve(held.plan.account).resolveSibling(
                held.plan.account+".properties"+
                MailboxStrictUncertainFence.SUFFIX);
            byte[] negativeBytes="G2187_NEGATIVE_HOLD".getBytes(
                StandardCharsets.US_ASCII);
            Files.write(negative,negativeBytes);
            negativeMarkerPreserved=
                rejectsCommit(restarted,held.plan.account)&&
                repo.inspectRestartRecoveryReadOnly(held.plan.account)
                    .state==FilePlayerRepository.RestartRecoveryEvidence
                        .State.UNCERTAIN_COMMIT_MARKER&&
                commit.inspect(held.plan.account).status==
                    MailboxGuardedDiskCommitRecord.Status
                        .NEGATIVE_MARKER_MANUAL_HOLD&&
                Arrays.equals(negativeBytes,Files.readAllBytes(negative));
            cases++;

            noLiveReward=
                done.live.bank().inventorySlots()==0&&
                done.live.mailbox().get(done.plan.messageId).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                swapped.live.bank().inventorySlots()==0&&
                held.live.bank().inventorySlots()==0;
            try(Stream<Path> all=Files.walk(root)){
                noTempOrLeaseLeaks=all.noneMatch(p->
                    p.getFileName().toString().endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> files=Files.walk(root)){
                for(Path p:files.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
        System.out.println("G2187_DISK_COMMIT_ADMISSION_DIAGNOSTICS"+
            " missingStable="+missingStable+
            " oldTokenRefused="+oldTokenRefused+
            " ordinaryPreparedAllowed="+ordinaryPreparedAllowed+
            " journalPreparedAllowed="+journalPreparedAllowed+
            " diskCommitChangesWitness="+diskCommitChangesWitness+
            " diskCommitStable="+diskCommitStable+
            " validCommitStillVetoed="+validCommitStillVetoed+
            " rollbackVetoed="+rollbackVetoed+
            " corruptCommitVetoed="+corruptCommitVetoed+
            " corruptChangesWitness="+corruptChangesWitness+
            " oversizePreflight="+oversizePreflight+
            " symlinkCommitRejected="+symlinkCommitRejected+
            " appearanceDuringSessionVetoed="+appearanceDuringSessionVetoed+
            " sameBytesInodeSwapRejected="+sameBytesInodeSwapRejected+
            " stableAfterSwap="+stableAfterSwap+
            " negativeMarkerPreserved="+negativeMarkerPreserved+
            " noLiveReward="+noLiveReward+
            " noTempOrLeaseLeaks="+noTempOrLeaseLeaks+
            " cases="+cases);
        if(!(missingStable&&oldTokenRefused&&ordinaryPreparedAllowed&&
             journalPreparedAllowed&&diskCommitChangesWitness&&
             diskCommitStable&&validCommitStillVetoed&&rollbackVetoed&&
             corruptCommitVetoed&&corruptChangesWitness&&
             oversizePreflight&&symlinkCommitRejected&&
             appearanceDuringSessionVetoed&&
             sameBytesInodeSwapRejected&&stableAfterSwap&&
             negativeMarkerPreserved&&noLiveReward&&
             noTempOrLeaseLeaks&&cases==10))
            throw new AssertionError(
                "G21.87 disk COMMIT session and witness regression failed");
        System.out.println("G2187_DISK_COMMIT_SESSION_WITNESS_PASS"+
            " sevenObjects=true token=G2187 rollbackVeto=true"+
            " transactionCommitted=false grant=false replay=false"+
            " admission=false release=false ack=false");
    }

    private static void publishTerminalAndCommit(
        StrictDurablePlayerSnapshotWriter strict,
        MailboxGuardedDiskCommitRecord commit,
        FilePlayerRepository.PathResolver paths,Seed s
    )throws IOException{
        StrictDurablePlayerSnapshotWriter.Receipt receipt=
            strict.saveStrictTerminalForWorld(
                s.terminal,paths.resolve(s.plan.account),
                StrictDurablePlayerSnapshotWriter
                    .canonicalSnapshotSha256(s.plan.preparedPreimage),
                ()->{},()->{}
            );
        commit.recordConfirmedDiskTerminal(s.plan,receipt);
    }

    private static boolean rejectsCommit(World world,String account){
        try{
            world.persistence().load(account);
            return false;
        }catch(IOException refused){
            return refused.getMessage().contains("G21.87 ")||
                refused.getMessage().contains("G21.32 ");
        }
    }
    private static boolean unchanged(
        FilePlayerRepository.RestartContinuityComparison result
    ){
        return result.state==FilePlayerRepository
            .RestartContinuityComparison.State
                .UNCHANGED_FORENSICS_NO_GRANT&&
            !result.restartAdmissionAuthorized&&
            !result.transactionCommitted&&!result.grantAuthorized&&
            !result.replayAuthorized&&!result.releaseAuthorized&&
            !result.clientAckAuthorized;
    }
    private static boolean changed(
        FilePlayerRepository.RestartContinuityComparison result
    ){
        return result.state==FilePlayerRepository
            .RestartContinuityComparison.State
                .CHANGED_FORENSICS_QUARANTINE&&
            !result.restartAdmissionAuthorized&&
            !result.transactionCommitted&&!result.grantAuthorized&&
            !result.replayAuthorized&&!result.releaseAuthorized&&
            !result.clientAckAuthorized;
    }
    private static Seed seed(World world,String account){
        WorldPlayer p=new WorldPlayer();
        long generation=world.registerPlayer(p,account);
        String id=account+":gift";
        p.mailbox().deliver(new RewardDeliveryMessage(
            id,"G21.87 commit witness","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G2187_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot selected=p.mailbox().get(id);
        MailboxPreparedClaimJournal.stageOnly(p,
            MailboxPreparedClaimJournal.prepare(p,selected));
        return new Seed(p,MailboxSettlementPostimagePlanner.plan(
            p,generation,selected));
    }
}
