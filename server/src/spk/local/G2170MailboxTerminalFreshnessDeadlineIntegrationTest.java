package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.70: the full queued FIFO + cooperating publication-lock wait +
 * actual World tick + final account read has ONE monotonic deadline.
 * Delayed or rejected World continuations cannot authorize anything.
 */
public final class G2170MailboxTerminalFreshnessDeadlineIntegrationTest {
    private static final class Seed {
        final WorldPlayer owner;
        final long generation;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        final PlayerSnapshot terminal;
        final WorldPlayerPersistence.PreparedAccountReservation token;
        final MailboxTerminalLiveTransitionCandidate candidate;

        Seed(WorldPlayer owner,long generation,
             MailboxSettlementPostimagePlanner.Proposal proposal,
             PlayerSnapshot terminal,
             WorldPlayerPersistence.PreparedAccountReservation token,
             MailboxTerminalLiveTransitionCandidate candidate){
            this.owner=owner;this.generation=generation;
            this.proposal=proposal;this.terminal=terminal;
            this.token=token;this.candidate=candidate;
        }
    }

    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2170-monotonic-deadline-");
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        FilePlayerRepository repo=new FilePlayerRepository(paths);
        StrictDurablePlayerSnapshotWriter writer=
            new StrictDurablePlayerSnapshotWriter(paths);

        boolean normalSuccessNoGrant=false;
        boolean normalOneShot=false;
        boolean fifoAndLockDeadlineEnforced=false;
        boolean expiredLockCannotReenter=false;
        boolean blockedWorldDeadlineEnforced=false;
        boolean delayedWorldCommandNoSuccess=false;
        boolean ownerGenerationVetoed=false;
        boolean shutdownRejectsOutstanding=false;
        boolean shutdownLateTaskNoSuccess=false;
        boolean unrelatedAccountStillSaves=false;
        boolean allLiveInventoriesUnchanged=false;
        boolean fileBytesUnchanged=false;
        boolean restartStillQuarantined=false;
        boolean noLeaseOrTempLeaks=false;

        try(World world=World.isolatedForTest(60000L,repo)){
            world.start();
            Seed normal=seed(world,writer,"g2170-normal");
            byte[] normalDisk=Files.readAllBytes(
                paths.resolve(normal.proposal.account));
            WorldPlayerPersistence.TerminalWorldFreshnessEvidence confirmed=
                world.persistence().attestReservedTerminalWorldFreshness(
                    normal.token,normal.proposal,normal.candidate
                ).get(8L,TimeUnit.SECONDS);
            normalSuccessNoGrant=
                confirmed.exactDiskBeforeAndAfterWorldTick&&
                confirmed.ownerPreparedOnWorldTick&&
                confirmed.publicationLockReleasedOnReturn&&
                confirmed.account.equals(normal.proposal.account)&&
                !confirmed.grantAuthorized&&!confirmed.liveApplied&&
                !confirmed.transactionCommitted&&!confirmed.replayAuthorized&&
                !confirmed.releaseAuthorized&&!confirmed.clientAckAuthorized;
            normalOneShot=rejects(()->world.persistence()
                .attestReservedTerminalWorldFreshness(
                    normal.token,normal.proposal,normal.candidate));

            // Task is already admitted to the FIFO but cannot enter
            // the cooperating account lock until AFTER the 5s budget.
            Seed lockDelayed=seed(world,writer,"g2170-lockwait");
            final Path lockFile=paths.resolve(lockDelayed.proposal.account);
            CountDownLatch held=new CountDownLatch(1);
            CountDownLatch releaseLock=new CountDownLatch(1);
            AtomicReference<Throwable> holderFailure=new AtomicReference<>();
            Thread holder=new Thread(()->{
                try{
                    MailboxAccountPublicationCoordinator
                        .withExclusivePublication(lockFile,()->{
                            held.countDown();
                            try{
                                if(!releaseLock.await(8L,TimeUnit.SECONDS))
                                    throw new IOException(
                                        "G21.70 account lock holder timeout");
                            }catch(InterruptedException interrupted){
                                Thread.currentThread().interrupt();
                                throw new IOException(
                                    "G21.70 holder interrupted",
                                    interrupted);
                            }
                            return null;
                        });
                }catch(Throwable failure){holderFailure.set(failure);}
            },"g2170-cooperating-file-holder");
            holder.start();
            CompletableFuture<
                WorldPlayerPersistence.TerminalWorldFreshnessEvidence
            > waiting;
            try{
                if(!held.await(5L,TimeUnit.SECONDS))
                    throw new AssertionError(
                        "G21.70 account holder never acquired lock");
                waiting=world.persistence().attestReservedTerminalWorldFreshness(
                    lockDelayed.token,lockDelayed.proposal,
                    lockDelayed.candidate);
                Thread.sleep(5600L);
            }finally{
                releaseLock.countDown();
                holder.join(5000L);
            }
            if(holder.isAlive()||holderFailure.get()!=null)
                throw new AssertionError(
                    "G21.70 account lock holder unresolved",
                    holderFailure.get());
            Throwable lockOutcome=failed(waiting);
            fifoAndLockDeadlineEnforced=lockOutcome!=null&&
                lockOutcome.getMessage().contains(
                    "G21.70 TERMINAL_FRESHNESS_DEADLINE_EXPIRED")&&
                lockDelayed.token.isActive();
            expiredLockCannotReenter=rejects(()->world.persistence()
                .attestReservedTerminalWorldFreshness(
                    lockDelayed.token,lockDelayed.proposal,
                    lockDelayed.candidate));

            // A DIFFERENT player's command stalls the ONE World tick
            // executor, without holding our reserved owner's lock.
            // A queued freshness command must be canceled/expired.
            Seed tickDelayed=seed(world,writer,"g2170-worldwait");
            WorldPlayer blocker=new WorldPlayer();
            long blockerGen=world.registerPlayer(
                blocker,"g2170-tick-blocker");
            CountDownLatch worldEntered=new CountDownLatch(1);
            CountDownLatch worldRelease=new CountDownLatch(1);
            CompletableFuture<Void> blocking=world.submit(
                blocker,blockerGen,()->{
                    worldEntered.countDown();
                    if(!worldRelease.await(8L,TimeUnit.SECONDS))
                        throw new IllegalStateException(
                            "G21.70 unrelated World blocker expired");
                });
            try{
                if(!worldEntered.await(5L,TimeUnit.SECONDS))
                    throw new AssertionError(
                        "G21.70 World stall fixture never started");
                CompletableFuture<
                    WorldPlayerPersistence.TerminalWorldFreshnessEvidence
                > delayed=world.persistence()
                    .attestReservedTerminalWorldFreshness(
                        tickDelayed.token,tickDelayed.proposal,
                        tickDelayed.candidate);
                Throwable delayedOutcome=failed(delayed);
                blockedWorldDeadlineEnforced=delayedOutcome!=null&&
                    tickDelayed.token.isActive();
                worldRelease.countDown();
                blocking.get(6L,TimeUnit.SECONDS);
                // A canceled G21.69 command may still appear in the
                // World inbox; it can only read and veto, never apply.
                world.submitAndWait(blocker,blockerGen,()->{
                    // FIFO witness: all older command actions drained.
                },5000L);
                delayedWorldCommandNoSuccess=
                    delayed.isCompletedExceptionally()&&
                    tickDelayed.owner.bank().inventorySlots()==0&&
                    rejects(()->world.persistence()
                        .attestReservedTerminalWorldFreshness(
                            tickDelayed.token,tickDelayed.proposal,
                            tickDelayed.candidate));
            }finally{
                worldRelease.countDown();
            }

            Seed retired=seed(world,writer,"g2170-retired");
            boolean removed=world.unregisterPlayer(
                retired.owner,retired.generation);
            ownerGenerationVetoed=removed&&rejects(()->world.persistence()
                .attestReservedTerminalWorldFreshness(
                    retired.token,retired.proposal,retired.candidate));

            WorldPlayer separate=new WorldPlayer();
            long separateGen=world.registerPlayer(
                separate,"g2170-independent");
            PlayerSnapshot other=PlayerSnapshotCodec.capture(
                "g2170-independent",separate);
            repo.saveForWorld(other);
            unrelatedAccountStillSaves=separate.accepts(separateGen)&&
                repo.load("g2170-independent").isPresent();

            allLiveInventoriesUnchanged=
                unchanged(normal)&&unchanged(lockDelayed)&&
                unchanged(tickDelayed)&&unchanged(retired);
            fileBytesUnchanged=Arrays.equals(
                normalDisk,Files.readAllBytes(
                    paths.resolve(normal.proposal.account)))&&
                repo.load(lockDelayed.proposal.account).get()
                    .values().equals(lockDelayed.terminal.values())&&
                repo.load(tickDelayed.proposal.account).get()
                    .values().equals(tickDelayed.terminal.values());
            try{
                world.persistence().load(normal.proposal.account);
            }catch(IOException denied){
                restartStillQuarantined=denied.getMessage().contains(
                    "QUARANTINE_TERMINAL_NO_GRANT");
            }
            boolean[] shutdown=shutdownWhileLockBlocked(
                root.resolve("forced-shutdown"));
            shutdownRejectsOutstanding=shutdown[0];
            shutdownLateTaskNoSuccess=shutdown[1];

            try(Stream<Path> tree=Files.walk(root)){
                noLeaseOrTempLeaks=tree.noneMatch(p->
                    p.getFileName().toString().endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> tree=Files.walk(root)){
                for(Path p:tree.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }

        System.out.println("G2170_TERMINAL_DEADLINE_DIAGNOSTICS"+
            " normalSuccessNoGrant="+normalSuccessNoGrant+
            " normalOneShot="+normalOneShot+
            " fifoAndLockDeadlineEnforced="+fifoAndLockDeadlineEnforced+
            " expiredLockCannotReenter="+expiredLockCannotReenter+
            " blockedWorldDeadlineEnforced="+blockedWorldDeadlineEnforced+
            " delayedWorldCommandNoSuccess="+delayedWorldCommandNoSuccess+
            " ownerGenerationVetoed="+ownerGenerationVetoed+
            " shutdownRejectsOutstanding="+shutdownRejectsOutstanding+
            " shutdownLateTaskNoSuccess="+shutdownLateTaskNoSuccess+
            " unrelatedAccountStillSaves="+unrelatedAccountStillSaves+
            " allLiveInventoriesUnchanged="+allLiveInventoriesUnchanged+
            " fileBytesUnchanged="+fileBytesUnchanged+
            " restartStillQuarantined="+restartStillQuarantined+
            " noLeaseOrTempLeaks="+noLeaseOrTempLeaks);
        if(!(normalSuccessNoGrant&&normalOneShot&&
             fifoAndLockDeadlineEnforced&&expiredLockCannotReenter&&
             blockedWorldDeadlineEnforced&&delayedWorldCommandNoSuccess&&
             ownerGenerationVetoed&&shutdownRejectsOutstanding&&
             shutdownLateTaskNoSuccess&&unrelatedAccountStillSaves&&
             allLiveInventoriesUnchanged&&fileBytesUnchanged&&
             restartStillQuarantined&&noLeaseOrTempLeaks))
            throw new AssertionError(
                "G21.70 bounded terminal freshness and lifecycle");
        System.out.println("G2170_TERMINAL_DEADLINE_PASS"+
            " admissionBudget=true cancelLateWorld=true"+
            " grant=false apply=false replay=false release=false");
    }


    private static boolean[] shutdownWhileLockBlocked(Path dir)
        throws Exception{
        Files.createDirectories(dir);
        FilePlayerRepository.PathResolver paths=
            name->dir.resolve(name+".properties");
        FilePlayerRepository repository=new FilePlayerRepository(paths);
        StrictDurablePlayerSnapshotWriter strict=
            new StrictDurablePlayerSnapshotWriter(paths);
        boolean rejected=false;
        boolean noLateSuccess=false;
        try(World world=World.isolatedForTest(60000L,repository)){
            world.start();
            Seed seed=seed(world,strict,"g2170-shutdown");
            Path file=paths.resolve(seed.proposal.account);
            CountDownLatch held=new CountDownLatch(1);
            CountDownLatch release=new CountDownLatch(1);
            AtomicReference<Throwable> holderFailure=new AtomicReference<>();
            AtomicReference<Throwable> closeFailure=new AtomicReference<>();
            Thread holder=new Thread(()->{
                try{
                    MailboxAccountPublicationCoordinator
                        .withExclusivePublication(file,()->{
                            held.countDown();
                            try{
                                if(!release.await(9L,TimeUnit.SECONDS))
                                    throw new IOException(
                                        "G21.70 shutdown holder timeout");
                            }catch(InterruptedException error){
                                Thread.currentThread().interrupt();
                                throw new IOException(
                                    "G21.70 shutdown holder interrupted",
                                    error);
                            }
                            return null;
                        });
                }catch(Throwable failure){holderFailure.set(failure);}
            },"g2170-shutdown-file-holder");
            holder.start();
            Thread closer=null;
            try{
                if(!held.await(5L,TimeUnit.SECONDS))
                    throw new AssertionError(
                        "G21.70 shutdown file lock not acquired");
                CompletableFuture<
                    WorldPlayerPersistence.TerminalWorldFreshnessEvidence
                > pending=world.persistence()
                    .attestReservedTerminalWorldFreshness(
                        seed.token,seed.proposal,seed.candidate);
                closer=new Thread(()->{
                    try{
                        world.persistence().close();
                    }catch(Throwable error){closeFailure.set(error);}
                },"g2170-shutdown-persistence");
                closer.start();
                // Forced shutdown rejects the in-flight task even when
                // the JVM monitor cannot be interrupted immediately.
                Thread.sleep(6800L);
                release.countDown();
                closer.join(6000L);
                Throwable failure=failed(pending);
                rejected=failure!=null&&seed.token.isActive()&&
                    seed.owner.bank().inventorySlots()==0;
                // The worker can leave the lock only after release;
                // no later successful completion or grant can appear.
                noLateSuccess=pending.isCompletedExceptionally()&&
                    unchanged(seed)&&
                    Arrays.equals(
                        seed.terminal.values().toString().getBytes(
                            java.nio.charset.StandardCharsets.UTF_8),
                        repository.load(seed.proposal.account).get()
                            .values().toString().getBytes(
                                java.nio.charset.StandardCharsets.UTF_8));
            }finally{
                release.countDown();
                holder.join(6000L);
                if(closer!=null)closer.join(6000L);
            }
            if(holder.isAlive()||
               (closer!=null&&closer.isAlive())||
               holderFailure.get()!=null||closeFailure.get()!=null)
                throw new AssertionError(
                    "G21.70 forced-close fixture did not quiesce",
                    holderFailure.get()!=null
                        ?holderFailure.get():closeFailure.get());
        }
        return new boolean[]{rejected,noLateSuccess};
    }

    private static boolean unchanged(Seed s){
        return s.owner.bank().inventorySlots()==0&&
            s.owner.mailbox().get(s.proposal.messageId).claimState==
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
            PlayerSnapshotCodec.capture(
                s.proposal.account,s.owner).values()
                .equals(s.proposal.preparedPreimage.values())&&
            s.token.isActive();
    }

    private static Seed seed(World world,
        StrictDurablePlayerSnapshotWriter writer,
        String account)throws Exception{
        WorldPlayer player=new WorldPlayer();
        long gen=world.registerPlayer(player,account);
        AtomicReference<MailboxSettlementPostimagePlanner.Proposal>
            plan=new AtomicReference<>();
        world.submitAndWait(player,gen,()->{
            String id=account+":gift";
            player.mailbox().deliver(new RewardDeliveryMessage(
                id,"G21.70 deadline fixture","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G2170_FIXTURE"));
            MailboxRewardDeliveryService.Snapshot row=
                player.mailbox().get(id);
            MailboxPreparedClaimJournal.stageOnly(player,
                MailboxPreparedClaimJournal.prepare(player,row));
            plan.set(MailboxSettlementPostimagePlanner.plan(
                player,gen,row));
        },5000L);
        PlayerSnapshot term=MailboxAtomicTerminalSnapshot.compose(plan.get());
        writer.saveStrict(plan.get().preparedPreimage);
        WorldPlayerPersistence.PreparedAccountReservation token=
            world.persistence().reservePreparedAccount(player,gen,plan.get());
        WorldPlayerPersistence.TerminalPublicationEvidence published=
            world.persistence().publishReservedTerminalStrictly(
                token,plan.get(),term,writer).get(8L,TimeUnit.SECONDS);
        WorldPlayerPersistence.TerminalReconciliationEvidence observed=
            world.persistence().reconcileReservedTerminalReadOnly(
                token,plan.get(),term,published).get(8L,TimeUnit.SECONDS);
        if(observed.state!=
                WorldPlayerPersistence.TerminalReconciliationEvidence.State
                    .EXACT_TERMINAL_BOUND_RECEIPT_NO_GRANT)
            throw new AssertionError("G21.70 bad seed receipt");
        MailboxTerminalLiveTransitionCandidate candidate=
            world.persistence().stageReservedTerminalWorldCandidate(
                token,plan.get(),term,observed).get(8L,TimeUnit.SECONDS);
        return new Seed(player,gen,plan.get(),term,token,candidate);
    }

    private static Throwable failed(CompletableFuture<?> f){
        try{f.get(8L,TimeUnit.SECONDS);return null;}
        catch(ExecutionException expected){return expected.getCause();}
        catch(Exception other){throw new AssertionError(
            "G21.70 unexpected future wait failure",other);}
    }
    private static boolean rejects(Runnable op){
        try{op.run();return false;}
        catch(IllegalArgumentException|IllegalStateException expected){
            return true;
        }
    }
    private G2170MailboxTerminalFreshnessDeadlineIntegrationTest(){}
}
