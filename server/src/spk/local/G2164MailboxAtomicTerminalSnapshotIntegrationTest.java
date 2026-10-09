package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.stream.Stream;

/** Real-file terminal account postimage and restart-quarantine matrix. */
public final class G2164MailboxAtomicTerminalSnapshotIntegrationTest {
    public static void main(String[] args)throws Exception{
        Path folder=Files.createTempDirectory("g2164-terminal-");
        FilePlayerRepository.PathResolver paths=
            name->folder.resolve(name+".properties");
        FilePlayerRepository raw=new FilePlayerRepository(paths);
        StrictDurablePlayerSnapshotWriter strict=
            new StrictDurablePlayerSnapshotWriter(paths);
        boolean preparedAdmits=false,atomicRoundTrip=false;
        boolean terminalCoherent=false,restartQuarantine=false;
        boolean checksumTamperDenied=false,inventoryTamperDenied=false;
        boolean noJournalDenied=false,orphanMarkerDenied=false;
        boolean duplicateClaimDenied=false,preMoveIntact=false;
        boolean postMoveUncertain=false,postMoveQuarantined=false;
        boolean unrelatedLoads=false,liveNoGrant=false;
        boolean resourcesClean=false;
        try(World fixture=World.isolatedForTest(60000L);
            World restart=World.isolatedForTest(60000L,raw)){
            restart.start();
            WorldPlayer owner=new WorldPlayer();
            String account="g2164-alice",message="g2164:gift";
            long generation=fixture.registerPlayer(owner,account);
            owner.mailbox().deliver(new RewardDeliveryMessage(
                message,"Transaction fixture","No positive grant",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G2164_FIXTURE"));
            MailboxRewardDeliveryService.Snapshot row=
                owner.mailbox().get(message);
            MailboxPreparedClaimJournal.stageOnly(owner,
                MailboxPreparedClaimJournal.prepare(owner,row));
            MailboxSettlementPostimagePlanner.Proposal proposal=
                MailboxSettlementPostimagePlanner.plan(
                    owner,generation,row);
            PlayerSnapshot before=proposal.preparedPreimage;
            PlayerSnapshot terminal=
                MailboxAtomicTerminalSnapshot.compose(proposal);
            terminalCoherent=
                MailboxAtomicTerminalSnapshot.inspect(terminal).state==
                    MailboxAtomicTerminalSnapshot.State
                        .COHERENT_TERMINAL_NO_GRANT&&
                MailboxPreparedRestartAdmission.inspect(terminal).state==
                    MailboxPreparedRestartAdmission.State
                        .QUARANTINE_TERMINAL_NO_GRANT;
            strict.saveStrict(before);
            preparedAdmits=restart.persistence().load(account)
                .get().values().equals(before.values());

            StrictDurablePlayerSnapshotWriter preFault=
                new StrictDurablePlayerSnapshotWriter(paths,phase->{
                    if(phase==StrictDurablePlayerSnapshotWriter.Phase
                            .BEFORE_ATOMIC_REPLACE)
                        throw new IOException("pre-move fixture fault");
                });
            try{preFault.saveStrict(terminal);}
            catch(IOException expected){
                preMoveIntact=raw.load(account).get().values()
                    .equals(before.values());
            }

            StrictDurablePlayerSnapshotWriter.Receipt receipt=
                strict.saveStrict(terminal);
            atomicRoundTrip=receipt.matchesSnapshot(terminal)&&
                raw.load(account).get().values().equals(terminal.values());
            try{restart.persistence().load(account);}
            catch(IOException denied){
                restartQuarantine=denied.getMessage().contains(
                    "QUARANTINE_TERMINAL_NO_GRANT");
            }

            TreeMap<String,String> bad=new TreeMap<>(terminal.values());
            bad.remove("extension.mailbox-terminal-snapshot.checksum");
            checksumTamperDenied=MailboxPreparedRestartAdmission.inspect(
                new PlayerSnapshot(terminal.version(),account,bad)
            ).state==MailboxPreparedRestartAdmission.State
                .QUARANTINE_INVALID_TERMINAL_SNAPSHOT;
            bad=new TreeMap<>(terminal.values());
            bad.put("inventory.0","995,99");
            inventoryTamperDenied=MailboxAtomicTerminalSnapshot.inspect(
                new PlayerSnapshot(terminal.version(),account,bad)
            ).state==MailboxAtomicTerminalSnapshot.State.INVALID_TERMINAL;
            bad=new TreeMap<>(terminal.values());
            bad.keySet().removeIf(key->key.startsWith(
                "extension."+MailboxPreparedClaimJournal.NAMESPACE+"."));
            PlayerSnapshot orphaned=new PlayerSnapshot(
                terminal.version(),account,bad);
            noJournalDenied=MailboxAtomicTerminalSnapshot.inspect(
                orphaned).state==
                MailboxAtomicTerminalSnapshot.State.INVALID_TERMINAL;
            orphanMarkerDenied=MailboxPreparedRestartAdmission.inspect(
                orphaned).state==MailboxPreparedRestartAdmission.State
                    .QUARANTINE_INVALID_TERMINAL_SNAPSHOT;
            WorldPlayer restored=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(terminal,restored);
            restored.markRegistered(account);
            try{
                MailboxPreparedClaimJournal.prepare(restored,
                    restored.mailbox().get(message));
            }catch(IllegalStateException expected){
                duplicateClaimDenied=true;
            }

            strict.saveStrict(before);
            StrictDurablePlayerSnapshotWriter postFault=
                new StrictDurablePlayerSnapshotWriter(paths,phase->{
                    if(phase==StrictDurablePlayerSnapshotWriter.Phase
                            .BEFORE_DIRECTORY_FORCE)
                        throw new IOException("post-move fixture fault");
                });
            try{postFault.saveStrict(terminal);}
            catch(StrictDurablePlayerSnapshotWriter
                    .UnconfirmedCommitException expected){
                postMoveUncertain=true;
            }
            try{restart.persistence().load(account);}
            catch(IOException blocked){
                postMoveQuarantined=blocked.getMessage().contains(
                    "QUARANTINE_TERMINAL_NO_GRANT");
            }
            WorldPlayer unrelated=new WorldPlayer();
            unrelated.markRegistered("g2164-other");
            strict.saveStrict(PlayerSnapshotCodec.capture(
                "g2164-other",unrelated));
            unrelatedLoads=restart.persistence().load(
                "g2164-other").isPresent();
            liveNoGrant=owner.bank().inventorySlots()==0&&
                owner.mailbox().get(message).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            try(Stream<Path> all=Files.walk(folder)){
                resourcesClean=all.noneMatch(p->
                    p.getFileName().toString().endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> all=Files.walk(folder)){
                for(Path p:all.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
        System.out.println("G2164_TERMINAL_ACCOUNT_DIAGNOSTICS"+
            " preparedAdmits="+preparedAdmits+
            " terminalCoherent="+terminalCoherent+
            " atomicRoundTrip="+atomicRoundTrip+
            " restartQuarantine="+restartQuarantine+
            " checksumTamperDenied="+checksumTamperDenied+
            " inventoryTamperDenied="+inventoryTamperDenied+
            " noJournalDenied="+noJournalDenied+
            " orphanMarkerDenied="+orphanMarkerDenied+
            " duplicateClaimDenied="+duplicateClaimDenied+
            " preMoveIntact="+preMoveIntact+
            " postMoveUncertain="+postMoveUncertain+
            " postMoveQuarantined="+postMoveQuarantined+
            " unrelatedLoads="+unrelatedLoads+
            " liveNoGrant="+liveNoGrant+
            " resourcesClean="+resourcesClean);
        if(!(preparedAdmits&&terminalCoherent&&atomicRoundTrip&&
             restartQuarantine&&checksumTamperDenied&&
             inventoryTamperDenied&&noJournalDenied&&
             orphanMarkerDenied&&duplicateClaimDenied&&
             preMoveIntact&&postMoveUncertain&&postMoveQuarantined&&
             unrelatedLoads&&liveNoGrant&&resourcesClean))
            throw new AssertionError("G21.64 terminal account");
        System.out.println("G2164_TERMINAL_ACCOUNT_PASS"+
            " grant=false replay=false release=false");
    }
    private G2164MailboxAtomicTerminalSnapshotIntegrationTest(){}
}
