package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * G21.62: real file-backed PREPARED->hypothetical inventory+CLAIMED
 * strict-save fault matrix, observed through World restart admission.
 * Never permits settlement, item grants, replay or marker release.
 */
public final class G2162MailboxStrictSettlementCrashMatrixIntegrationTest {
    private static final StrictDurablePlayerSnapshotWriter.Phase[] PHASES={
        StrictDurablePlayerSnapshotWriter.Phase.BEFORE_TEMP_CREATE,
        StrictDurablePlayerSnapshotWriter.Phase.AFTER_TEMP_CREATE,
        StrictDurablePlayerSnapshotWriter.Phase.AFTER_SERIALIZE,
        StrictDurablePlayerSnapshotWriter.Phase.BEFORE_FILE_FORCE,
        StrictDurablePlayerSnapshotWriter.Phase.BEFORE_ATOMIC_REPLACE,
        StrictDurablePlayerSnapshotWriter.Phase.BEFORE_DIRECTORY_FORCE,
        StrictDurablePlayerSnapshotWriter.Phase.AFTER_DIRECTORY_FORCE
    };

    public static void main(String[] args)throws Exception{
        Path folder=Files.createTempDirectory("g2162-crash-matrix-");
        FilePlayerRepository.PathResolver paths=
            name->folder.resolve(name+".properties");
        FilePlayerRepository reader=new FilePlayerRepository(paths);
        StrictDurablePlayerSnapshotWriter writer=
            new StrictDurablePlayerSnapshotWriter(paths);
        boolean beforePrepared=true,afterQuarantined=true;
        boolean worldRestartVeto=true,preparedLoads=true;
        boolean postMoveVisible=true,preNoReceipt=true;
        boolean postNoReceipt=true,successStillQuarantined=false;
        boolean independentLoads=false,liveUnchanged=true;
        boolean noOrphans=false,noRewardGrant=true;
        int checkedPre=0,checkedPost=0;
        try(World fixture=World.isolatedForTest(60000L);
            World restarted=World.isolatedForTest(60000L,reader)){
            restarted.start();
            for(int n=0;n<PHASES.length+1;n++){
                String account="g2162-case-"+n;
                WorldPlayer owner=new WorldPlayer();
                long generation=fixture.registerPlayer(owner,account);
                String messageId=account+":gift";
                owner.mailbox().deliver(new RewardDeliveryMessage(
                    messageId,"Crash-window reward","LocalLab fixture",
                    Collections.singletonList(
                        new RewardDeliveryMessage.Attachment(995,25)
                    ),"CUSTOM_LOCALLAB_G2162_FIXTURE"
                ));
                MailboxRewardDeliveryService.Snapshot selected=
                    owner.mailbox().get(messageId);
                MailboxPreparedClaimJournal.stageOnly(
                    owner,MailboxPreparedClaimJournal.prepare(
                        owner,selected
                    )
                );
                MailboxSettlementPostimagePlanner.Proposal proposal=
                    MailboxSettlementPostimagePlanner.plan(
                        owner,generation,selected
                    );
                PlayerSnapshot before=proposal.preparedPreimage;
                PlayerSnapshot after=proposal.hypotheticalPostimage;
                if(!MailboxPreparedRestartAdmission.inspect(before)
                        .admissionAllowed||
                   MailboxPreparedRestartAdmission.inspect(after)
                        .admissionAllowed)
                    throw new AssertionError(
                        "G21.62 invalid planned restart distinction"
                    );
                writer.saveStrict(before);
                boolean success=n==PHASES.length;
                boolean beforeMove=n<5;
                boolean unconfirmed=false,failedBefore=false;
                StrictDurablePlayerSnapshotWriter.Receipt receipt=null;
                if(success){
                    receipt=writer.saveStrict(after);
                }else{
                    final StrictDurablePlayerSnapshotWriter.Phase stop=
                        PHASES[n];
                    StrictDurablePlayerSnapshotWriter failing=
                        new StrictDurablePlayerSnapshotWriter(paths,at->{
                            if(at==stop)
                                throw new IOException(
                                    "G21.62 injected "+stop
                                );
                        });
                    try{
                        failing.saveStrict(after);
                    }catch(
                        StrictDurablePlayerSnapshotWriter
                            .UnconfirmedCommitException expected
                    ){
                        unconfirmed=true;
                    }catch(IOException expected){
                        failedBefore=true;
                    }
                }
                Optional<PlayerSnapshot> disk=reader.load(account);
                if(!disk.isPresent())
                    throw new AssertionError("G21.62 disk missing");
                boolean isPrepared=disk.get().values().equals(
                    before.values());
                boolean isHypothetical=disk.get().values().equals(
                    after.values());
                if(beforeMove){
                    checkedPre++;
                    beforePrepared&=isPrepared;
                    preNoReceipt&=failedBefore&&!unconfirmed;
                    Optional<PlayerSnapshot> admitted=
                        restarted.persistence().load(account);
                    preparedLoads&=admitted.isPresent()&&
                        admitted.get().values().equals(before.values());
                }else{
                    checkedPost++;
                    afterQuarantined&=
                        isHypothetical&&!failedBefore&&
                        (success||unconfirmed);
                    postMoveVisible&=isHypothetical;
                    if(!success)postNoReceipt&=unconfirmed;
                    boolean denied=false;
                    try{
                        restarted.persistence().load(account);
                    }catch(IOException refusal){
                        denied=refusal.getMessage().contains(
                            "G21.31 MAILBOX_PREPARED_LOAD_QUARANTINE");
                    }
                    worldRestartVeto&=denied;
                }
                if(success){
                    successStillQuarantined=
                        receipt!=null&&receipt.matchesSnapshot(after)&&
                        !MailboxPreparedRestartAdmission.inspect(after)
                            .admissionAllowed;
                }
                liveUnchanged&=
                    owner.bank().inventorySlots()==0&&
                    owner.bank().inventoryCount(995)==0&&
                    owner.mailbox().get(messageId).claimState==
                        MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                    PlayerSnapshotCodec.capture(account,owner)
                        .values().equals(before.values());
                noRewardGrant&=owner.bank().inventoryCount(995)==0;
            }
            WorldPlayer ordinary=new WorldPlayer();
            ordinary.markRegistered("g2162-independent");
            PlayerSnapshot clean=PlayerSnapshotCodec.capture(
                "g2162-independent",ordinary);
            writer.saveStrict(clean);
            Optional<PlayerSnapshot> loaded=
                restarted.persistence().load("g2162-independent");
            independentLoads=loaded.isPresent()&&
                loaded.get().values().equals(clean.values());
            try(Stream<Path> all=Files.walk(folder)){
                noOrphans=all.noneMatch(p->{
                    String filename=p.getFileName().toString();
                    return filename.contains(".g2123-")||
                        filename.endsWith(".tmp");
                })&&MailboxAccountPublicationCoordinator
                    .activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> all=Files.walk(folder)){
                for(Path path:all.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }
        System.out.println("G2162_STRICT_SETTLEMENT_CRASH_MATRIX_DIAGNOSTICS"+
            " checkedPre="+checkedPre+
            " checkedPost="+checkedPost+
            " beforePrepared="+beforePrepared+
            " afterQuarantined="+afterQuarantined+
            " worldRestartVeto="+worldRestartVeto+
            " preparedLoads="+preparedLoads+
            " postMoveVisible="+postMoveVisible+
            " preNoReceipt="+preNoReceipt+
            " postNoReceipt="+postNoReceipt+
            " successStillQuarantined="+successStillQuarantined+
            " independentLoads="+independentLoads+
            " liveUnchanged="+liveUnchanged+
            " noOrphans="+noOrphans+
            " noRewardGrant="+noRewardGrant);
        if(!(checkedPre==5&&checkedPost==3&&beforePrepared&&
             afterQuarantined&&worldRestartVeto&&preparedLoads&&
             postMoveVisible&&preNoReceipt&&postNoReceipt&&
             successStillQuarantined&&independentLoads&&
             liveUnchanged&&noOrphans&&noRewardGrant))
            throw new AssertionError("G21.62 strict crash-window matrix");
        System.out.println("G2162_STRICT_SETTLEMENT_CRASH_MATRIX_PASS"+
            " preMovePrepared=5 postMoveQuarantined=3"+
            " grant=false replay=false release=false");
    }

    private G2162MailboxStrictSettlementCrashMatrixIntegrationTest(){}
}
