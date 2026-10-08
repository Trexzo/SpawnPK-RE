package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.43: concrete FilePlayerRepository strict PREPARED barriers must
 * reject stale caller-provided whole-account snapshots BEFORE enqueue,
 * replacing files or installing stale-capture cutoffs.
 *
 * No native widget calls, reward grants, replay or fence release.
 */
public final class G2143MailboxStrictCurrentOwnerAdmissionIntegrationTest {
    private static final class Seed {
        final WorldPlayer player;
        final String account;
        final long generation;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        Seed(WorldPlayer player,String account,long generation,
             MailboxSettlementPostimagePlanner.Proposal proposal){
            this.player=player;
            this.account=account;
            this.generation=generation;
            this.proposal=proposal;
        }
    }

    public static void main(String[] args)throws Exception{
        boolean preparedSnapshotInitiallyCurrent=false;
        boolean liveMovementDivergenceRejected=false;
        boolean noDiskMutationOnStaleAdmission=false;
        boolean noBarrierQueuedOnRefusal=false;
        boolean freshCurrentSnapshotAccepted=false;
        boolean freshCompleteSnapshotPersisted=false;
        boolean oldSnapshotStillRejected=false;
        boolean forgedJournalRejected=false;
        boolean forgeryCannotTouchAccount=false;
        boolean normalUnchangedSnapshotAccepted=false;
        boolean unrelatedAccountUnaffected=false;
        boolean noLiveInventoryCredit=false;
        boolean mailboxRemainsUnclaimed=false;
        boolean noReviewMarkersCreated=false;
        boolean noTemporaryArtifacts=false;
        boolean noLockLeaseLeaks=false;
        boolean receiptHasNoGrantAuthority=false;

        Path root=Files.createTempDirectory(
            "g2143-mailbox-strict-current-owner-"
        );
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        FilePlayerRepository file=new FilePlayerRepository(paths);
        StrictDurablePlayerSnapshotWriter writer=
            new StrictDurablePlayerSnapshotWriter(paths);
        MailboxDurableReviewFence fence=
            new MailboxDurableReviewFence(paths);
        try{
            try(World world=World.isolatedForTest(60000L,file)){
                world.start();

                Seed stale=seed(world,"g2143-stale","g2143:stale");
                Seed forged=seed(world,"g2143-forged","g2143:forged");
                Seed valid=seed(world,"g2143-valid","g2143:valid");

                file.save(stale.proposal.preparedPreimage);
                file.save(forged.proposal.preparedPreimage);
                file.save(valid.proposal.preparedPreimage);
                byte[] staleBefore=Files.readAllBytes(
                    paths.resolve(stale.account)
                );
                byte[] forgedBefore=Files.readAllBytes(
                    paths.resolve(forged.account)
                );
                preparedSnapshotInitiallyCurrent=
                    strictDigest(stale.proposal.preparedPreimage)
                        .equals(strictDigest(
                            PlayerSnapshotCodec.capture(
                                stale.account,stale.player
                            )
                        ))&&
                    MailboxPreparedRestartAdmission.inspect(
                        stale.proposal.preparedPreimage
                    ).state==MailboxPreparedRestartAdmission.State
                        .VALID_PREPARED_UNCLAIMED;

                // Same exact owner and generation, with unchanged
                // PREPARED journal but changed MOVEMENT. Previously
                // the strict barrier accepted an old full snapshot.
                world.submitAndWait(
                    stale.player,stale.generation,
                    ()->stale.player.movement().setRunEnergy(37),
                    5000L
                );
                int queuedBefore=world.persistence().queuedWrites();
                liveMovementDivergenceRejected=rejectsAdmission(
                    world,stale,stale.proposal.preparedPreimage,
                    writer,"G21.43 STRICT_PREPARED_STALE_LIVE_OWNER_SNAPSHOT"
                );
                noDiskMutationOnStaleAdmission=
                    Arrays.equals(staleBefore,
                        Files.readAllBytes(paths.resolve(stale.account)));
                noBarrierQueuedOnRefusal=
                    world.persistence().queuedWrites()==queuedBefore;

                // Re-capture from the actually owned WorldPlayer,
                // preserving the staged UNCLAIMED journal. A current
                // canonical PREPARED snapshot may pass as before.
                AtomicReference<PlayerSnapshot> newer=
                    new AtomicReference<>();
                world.submitAndWait(
                    stale.player,stale.generation,
                    ()->newer.set(PlayerSnapshotCodec.capture(
                        stale.account,stale.player
                    )),5000L
                );
                StrictDurablePlayerSnapshotWriter.Receipt accepted=
                    submit(world,stale,newer.get(),writer)
                        .get(8,TimeUnit.SECONDS);
                freshCurrentSnapshotAccepted=
                    accepted.matchesSnapshot(newer.get())&&
                    !strictDigest(stale.proposal.preparedPreimage)
                        .equals(strictDigest(newer.get()));
                freshCompleteSnapshotPersisted=
                    file.load(stale.account).get().values().equals(
                        newer.get().values()
                    )&&!fence.present(stale.account);
                oldSnapshotStillRejected=rejectsAdmission(
                    world,stale,stale.proposal.preparedPreimage,
                    writer,"G21.43 STRICT_PREPARED_STALE_LIVE_OWNER_SNAPSHOT"
                );

                // Canonical identity alone is not enough; retaining
                // a superficial PREPARED state with an invalid key
                // must not evade G21.31's full intent validation.
                TreeMap<String,String> changedKeys=new TreeMap<>(
                    forged.proposal.preparedPreimage.values()
                );
                changedKeys.put(
                    "extension."+
                    MailboxPreparedClaimJournal.NAMESPACE+".key",
                    "0000000000000000000000000000000000000000000000000000000000000000"
                );
                PlayerSnapshot invalid=new PlayerSnapshot(
                    PlayerSnapshot.CURRENT_VERSION,forged.account,
                    changedKeys
                );
                forgedJournalRejected=rejectsAdmission(
                    world,forged,invalid,writer,
                    "G21.43 STRICT_PREPARED_ACCOUNT_NOT_CANONICAL"
                )||
                    rejectsAdmission(
                        world,forged,invalid,writer,
                        "G21.43 STRICT_PREPARED_ACCOUNT_SNAPSHOT_INVALID"
                    );
                forgeryCannotTouchAccount=
                    Arrays.equals(forgedBefore,
                        Files.readAllBytes(paths.resolve(forged.account)));

                StrictDurablePlayerSnapshotWriter.Receipt normal=
                    submit(world,valid,
                        valid.proposal.preparedPreimage,writer)
                    .get(8,TimeUnit.SECONDS);
                normalUnchangedSnapshotAccepted=
                    normal.matchesSnapshot(
                        valid.proposal.preparedPreimage
                    )&&!fence.present(valid.account);
                unrelatedAccountUnaffected=
                    file.load(valid.account).get().values().equals(
                        valid.proposal.preparedPreimage.values()
                    )&&file.load(forged.account).get().values().equals(
                        forged.proposal.preparedPreimage.values()
                    );

                noLiveInventoryCredit=
                    stale.player.bank().inventorySlots()==0&&
                    forged.player.bank().inventorySlots()==0&&
                    valid.player.bank().inventorySlots()==0;
                mailboxRemainsUnclaimed=true;
                for(Seed seed:new Seed[]{stale,forged,valid}){
                    mailboxRemainsUnclaimed &=
                        seed.player.mailbox().get(
                            seed.proposal.messageId
                        ).claimState==
                            MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
                }
                noReviewMarkersCreated=
                    !fence.present(stale.account)&&
                    !fence.present(forged.account)&&
                    !fence.present(valid.account);
                receiptHasNoGrantAuthority=
                    StrictDurablePlayerSnapshotWriter.AUTHORITY
                        .contains("STRICT_FILE_BOUNDARY_ONLY")&&
                    MailboxPreparedClaimJournal.STATE.equals(
                        "PREPARED_NO_GRANT"
                    );
                noLockLeaseLeaks=
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
                try(Stream<Path> pathsOnDisk=Files.list(root)){
                    noTemporaryArtifacts=pathsOnDisk.noneMatch(
                        path->path.getFileName().toString().endsWith(
                            ".tmp"
                        )
                    );
                }
            }
        }finally{
            try(Stream<Path> pathsOnDisk=Files.walk(root)){
                for(Path path:pathsOnDisk.sorted(
                    Comparator.reverseOrder()
                ).toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }

        System.out.println(
            "G2143_MAILBOX_STRICT_CURRENT_OWNER_DIAGNOSTICS"+
            " originallyExact="+preparedSnapshotInitiallyCurrent+
            " movementStaleRejected="+liveMovementDivergenceRejected+
            " noStaleDiskChange="+noDiskMutationOnStaleAdmission+
            " noQueuedBarrierOnRefusal="+noBarrierQueuedOnRefusal+
            " refreshedSnapshotAccepted="+freshCurrentSnapshotAccepted+
            " refreshedSnapshotSaved="+freshCompleteSnapshotPersisted+
            " olderSnapshotStillDenied="+oldSnapshotStillRejected+
            " invalidIntentRejected="+forgedJournalRejected+
            " invalidIntentNoDiskWrite="+forgeryCannotTouchAccount+
            " cleanSnapshotAccepted="+normalUnchangedSnapshotAccepted+
            " foreignAccountPreserved="+unrelatedAccountUnaffected+
            " inventoryNeverCredited="+noLiveInventoryCredit+
            " mailboxNeverClaimed="+mailboxRemainsUnclaimed+
            " noNegativeMarkerCreated="+noReviewMarkersCreated+
            " noTemporaryFiles="+noTemporaryArtifacts+
            " noJvmLockLeaks="+noLockLeaseLeaks+
            " noGrantAuthority="+receiptHasNoGrantAuthority
        );
        require(
            preparedSnapshotInitiallyCurrent&&
            liveMovementDivergenceRejected&&
            noDiskMutationOnStaleAdmission&&
            noBarrierQueuedOnRefusal&&
            freshCurrentSnapshotAccepted&&
            freshCompleteSnapshotPersisted&&
            oldSnapshotStillRejected&&forgedJournalRejected&&
            forgeryCannotTouchAccount&&
            normalUnchangedSnapshotAccepted&&
            unrelatedAccountUnaffected&&noLiveInventoryCredit&&
            mailboxRemainsUnclaimed&&noReviewMarkersCreated&&
            noTemporaryArtifacts&&noLockLeaseLeaks&&
            receiptHasNoGrantAuthority,
            "G21.43 exact current-owned PREPARED snapshot admission"
        );
        System.out.println(
            "G2143_MAILBOX_STRICT_CURRENT_OWNER_ADMISSION_PASS"+
            " staleCompleteSnapshotDeniedBeforeQueue=true"+
            " freshSnapshotAccepted=true"+
            " invalidPreparedIntentDenied=true"+
            " grant=false replay=false release=false"
        );
    }

    private static String strictDigest(PlayerSnapshot snapshot){
        return StrictDurablePlayerSnapshotWriter
            .canonicalSnapshotSha256(snapshot);
    }

    private static Seed seed(
        World world,String account,String messageId
    )throws Exception{
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,account);
        AtomicReference<MailboxSettlementPostimagePlanner.Proposal>
            proposal=new AtomicReference<>();
        world.submitAndWait(player,generation,()->{
            player.mailbox().deliver(new RewardDeliveryMessage(
                messageId,"Stale strict barrier","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)
                ),"CUSTOM_LOCALLAB_G2143_FIXTURE"
            ));
            MailboxRewardDeliveryService.Snapshot selected=
                player.mailbox().get(messageId);
            MailboxPreparedClaimJournal.stageOnly(
                player,MailboxPreparedClaimJournal.prepare(
                    player,selected
                )
            );
            proposal.set(MailboxSettlementPostimagePlanner.plan(
                player,generation,selected
            ));
        },5000L);
        return new Seed(player,account,generation,proposal.get());
    }

    private static CompletableFuture<
        StrictDurablePlayerSnapshotWriter.Receipt> submit(
        World world,Seed seed,PlayerSnapshot snapshot,
        StrictDurablePlayerSnapshotWriter writer
    ){
        return world.persistence().submitPreparedStrictBarrier(
            seed.player,seed.generation,snapshot,writer
        );
    }

    private static boolean rejectsAdmission(
        World world,Seed seed,PlayerSnapshot snapshot,
        StrictDurablePlayerSnapshotWriter writer,String error
    ){
        try{
            submit(world,seed,snapshot,writer);
            return false;
        }catch(IllegalStateException failure){
            return failure.getMessage()!=null&&
                failure.getMessage().contains(error);
        }
    }

    private static void require(boolean value,String reason){
        if(!value)throw new AssertionError(reason);
    }

    private G2143MailboxStrictCurrentOwnerAdmissionIntegrationTest(){}
}
