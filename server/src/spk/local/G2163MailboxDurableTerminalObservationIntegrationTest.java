package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Stream;

/** Deterministic real-file, negative-only terminal observation matrix. */
public final class G2163MailboxDurableTerminalObservationIntegrationTest {
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2163-terminal-");
        FilePlayerRepository.PathResolver paths=
            name->root.resolve(name+".properties");
        FilePlayerRepository repository=new FilePlayerRepository(paths);
        StrictDurablePlayerSnapshotWriter writer=
            new StrictDurablePlayerSnapshotWriter(paths);
        MailboxDurableTerminalObservation ledger=
            new MailboxDurableTerminalObservation(paths);
        boolean noRecordAbsent=false,preimageNotPublishable=false;
        boolean postimageRecorded=false,duplicateNoClobber=false;
        boolean changedAccountDetected=false,corruptedDetected=false;
        boolean uncertaintyRetained=false,uncertainStillReadable=false;
        boolean restartQuarantine=false,independentUnaffected=false;
        boolean noGrant=true,noAutoRelease=true,noTempLeaks=false;
        try(World fixture=World.isolatedForTest(60000L);
            World restarted=World.isolatedForTest(60000L,repository)){
            restarted.start();
            MailboxSettlementPostimagePlanner.Proposal proposal=
                proposed(fixture,"g2163-alice");
            noRecordAbsent=ledger.inspect(proposal.account).status==
                MailboxDurableTerminalObservation.Status.ABSENT;
            writer.saveStrict(proposal.preparedPreimage);
            try{
                ledger.recordObservedHypothetical(proposal);
            }catch(IOException expected){
                preimageNotPublishable=true;
            }
            writer.saveStrict(proposal.hypotheticalPostimage);
            ledger.recordObservedHypothetical(proposal);
            Path marker=ledger.recordPath(proposal.account);
            byte[] original=Files.readAllBytes(marker);
            MailboxDurableTerminalObservation.Observation observed=
                ledger.inspect(proposal.account);
            postimageRecorded=observed.status==
                MailboxDurableTerminalObservation.Status
                    .OBSERVED_HYPOTHETICAL_POSTIMAGE&&
                !observed.grantAuthorized&&!observed.replayAuthorized&&
                !observed.rollbackAuthorized&&!observed.releaseAuthorized&&
                !observed.clientAckAuthorized;
            try{
                ledger.recordObservedHypothetical(proposal);
            }catch(IOException duplicate){
                duplicateNoClobber=java.util.Arrays.equals(
                    original,Files.readAllBytes(marker));
            }
            writer.saveStrict(proposal.preparedPreimage);
            changedAccountDetected=ledger.inspect(proposal.account)
                .status==MailboxDurableTerminalObservation.Status
                    .ACCOUNT_DIVERGED;
            Files.writeString(marker,"corrupted",
                StandardOpenOption.TRUNCATE_EXISTING);
            corruptedDetected=ledger.inspect(proposal.account)
                .status==MailboxDurableTerminalObservation.Status
                    .INVALID_RECORD;

            MailboxSettlementPostimagePlanner.Proposal second=
                proposed(fixture,"g2163-bob");
            writer.saveStrict(second.hypotheticalPostimage);
            MailboxDurableTerminalObservation uncertain=
                new MailboxDurableTerminalObservation(paths,phase->{
                    if(phase==MailboxDurableTerminalObservation.Phase
                            .AFTER_LINK)
                        throw new IOException("G21.63 synthetic postlink");
                });
            try{
                uncertain.recordObservedHypothetical(second);
            }catch(MailboxDurableTerminalObservation
                    .UnconfirmedObservationException failure){
                uncertaintyRetained=Files.exists(
                    uncertain.recordPath(second.account),
                    LinkOption.NOFOLLOW_LINKS);
            }
            uncertainStillReadable=ledger.inspect(second.account)
                .status==MailboxDurableTerminalObservation.Status
                    .OBSERVED_HYPOTHETICAL_POSTIMAGE;

            try{
                restarted.persistence().load(second.account);
            }catch(IOException refused){
                restartQuarantine=refused.getMessage().contains(
                    "G21.31 MAILBOX_PREPARED_LOAD_QUARANTINE");
            }
            WorldPlayer ordinary=new WorldPlayer();
            ordinary.markRegistered("g2163-ordinary");
            writer.saveStrict(PlayerSnapshotCodec.capture(
                "g2163-ordinary",ordinary));
            independentUnaffected=
                restarted.persistence().load("g2163-ordinary")
                    .isPresent()&&
                ledger.inspect("g2163-ordinary").status==
                    MailboxDurableTerminalObservation.Status.ABSENT;
            noGrant=second.preparedPreimage!=null&&
                !MailboxPreparedRestartAdmission.inspect(
                    second.hypotheticalPostimage).admissionAllowed;
            noAutoRelease=Files.exists(
                    uncertain.recordPath(second.account),
                    LinkOption.NOFOLLOW_LINKS)&&
                Files.exists(marker,LinkOption.NOFOLLOW_LINKS);
            try(Stream<Path> all=Files.walk(root)){
                noTempLeaks=all.noneMatch(p->
                    p.getFileName().toString().endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> all=Files.walk(root)){
                for(Path p:all.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
        System.out.println("G2163_TERMINAL_OBSERVATION_DIAGNOSTICS"+
            " noRecordAbsent="+noRecordAbsent+
            " preimageNotPublishable="+preimageNotPublishable+
            " postimageRecorded="+postimageRecorded+
            " duplicateNoClobber="+duplicateNoClobber+
            " changedAccountDetected="+changedAccountDetected+
            " corruptedDetected="+corruptedDetected+
            " uncertaintyRetained="+uncertaintyRetained+
            " uncertainStillReadable="+uncertainStillReadable+
            " restartQuarantine="+restartQuarantine+
            " independentUnaffected="+independentUnaffected+
            " noGrant="+noGrant+
            " noAutoRelease="+noAutoRelease+
            " noTempLeaks="+noTempLeaks);
        if(!(noRecordAbsent&&preimageNotPublishable&&postimageRecorded&&
             duplicateNoClobber&&changedAccountDetected&&
             corruptedDetected&&uncertaintyRetained&&
             uncertainStillReadable&&restartQuarantine&&
             independentUnaffected&&noGrant&&noAutoRelease&&noTempLeaks))
            throw new AssertionError("G21.63 durable terminal observation");
        System.out.println("G2163_TERMINAL_OBSERVATION_PASS"+
            " grant=false replay=false release=false");
    }

    private static MailboxSettlementPostimagePlanner.Proposal proposed(
        World world,String account
    ){
        WorldPlayer owner=new WorldPlayer();
        long generation=world.registerPlayer(owner,account);
        String message=account+":gift";
        owner.mailbox().deliver(new RewardDeliveryMessage(
            message,"Terminal observation fixture","No live settlement",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G2163_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot selected=
            owner.mailbox().get(message);
        MailboxPreparedClaimJournal.stageOnly(owner,
            MailboxPreparedClaimJournal.prepare(owner,selected));
        return MailboxSettlementPostimagePlanner.plan(
            owner,generation,selected);
    }
    private G2163MailboxDurableTerminalObservationIntegrationTest(){}
}
