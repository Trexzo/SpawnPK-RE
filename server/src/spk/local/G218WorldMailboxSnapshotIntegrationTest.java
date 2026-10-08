package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * G21.8: real WorldPlayer-owned Mailbox state flows through the canonical
 * WorldPlayerPersistence capture codec, rather than manual G21.7 encoding.
 */
public final class G218WorldMailboxSnapshotIntegrationTest {
    public static void main(String[] args)throws Exception{
        boolean freshAbsentNamespace=false;
        boolean worldOwnedState=false;
        boolean capturedAutomatically=false;
        boolean fileRoundTrip=false;
        boolean claimedAcknowledgementSafe=false;
        boolean readStateRestored=false;
        boolean retainedAttachments=false;
        boolean otherExtensionPreserved=false;
        boolean maliciousSnapshotRejectedBeforeApply=false;
        boolean occupiedPlayerRejectsLoad=false;
        boolean emptyTombstoneSaved=false;
        boolean deletedMessagesNotReplayed=false;
        boolean separateAccountIsolation=false;
        boolean generationTurnoverSafe=false;
        boolean noSettlementOrPacketClaim=false;

        Path root=Files.createTempDirectory(
            "spawnpk-g218-world-mailbox-"
        );

        String aliceName="g218-mailbox-alice";
        String bobName="g218-mailbox-bob";

        try{
            FilePlayerRepository repository=
                new FilePlayerRepository(
                    name->root.resolve(name+".properties")
                );

            World sourceWorld=World.isolatedForTest(
                60_000L,
                repository
            );
            WorldPlayer alice=new WorldPlayer();
            WorldPlayer bob=new WorldPlayer();

            long aliceGeneration=sourceWorld.registerPlayer(
                alice,aliceName
            );
            long bobGeneration=sourceWorld.registerPlayer(
                bob,bobName
            );

            PlayerSnapshot blank=
                PlayerSnapshotCodec.capture(aliceName,alice);

            freshAbsentNamespace=
                noMailboxNamespace(blank.values())&&
                alice.mailbox().size()==0;

            alice.snapshotExtensions().replaceNamespace(
                "unrelated-g218",
                Collections.singletonMap("marker","retain")
            );

            alice.mailbox().deliver(message(
                "g218:read",
                "Read notice",
                Collections.emptyList()
            ));
            alice.mailbox().deliver(message(
                "g218:claimed",
                "Claimed item",
                Arrays.asList(
                    new RewardDeliveryMessage.Attachment(995,5000),
                    new RewardDeliveryMessage.Attachment(4151,1)
                )
            ));
            alice.mailbox().deliver(message(
                "g218:unclaimed",
                "Unclaimed item",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,33)
                )
            ));

            alice.mailbox().markRead("g218:read");

            // External settlement acknowledgement fixture only.
            // We do not credit inventory or bank balances here.
            alice.mailbox().acknowledgeAttachmentSettlement(
                "g218:claimed"
            );

            worldOwnedState=
                alice.mailbox().size()==3&&
                alice.mailbox().unreadCount()==2&&
                alice.mailbox().get("g218:claimed").claimState==
                    MailboxRewardDeliveryService.ClaimState.CLAIMED;

            PlayerSnapshot savedSource=
                PlayerSnapshotCodec.capture(aliceName,alice);

            capturedAutomatically=
                "1".equals(
                    savedSource.value(
                        "extension.mailbox-g21.version"
                    )
                )&&
                "3".equals(
                    savedSource.value(
                        "extension.mailbox-g21.count"
                    )
                );

            // The existing player repository is the real save/load seam.
            repository.save(savedSource);
            repository.save(
                PlayerSnapshotCodec.capture(bobName,bob)
            );

            boolean sourceUnregistered=
                sourceWorld.unregisterPlayer(
                    alice,aliceGeneration
                )&&
                sourceWorld.unregisterPlayer(
                    bob,bobGeneration
                );

            sourceWorld.close();

            Optional<PlayerSnapshot> aliceDisk=
                repository.load(aliceName);
            Optional<PlayerSnapshot> bobDisk=
                repository.load(bobName);

            require(aliceDisk.isPresent()&&bobDisk.isPresent(),
                "disk snapshots absent");

            World freshWorld=World.isolatedForTest(
                60_000L,
                repository
            );
            WorldPlayer loadedAlice=new WorldPlayer();
            WorldPlayer loadedBob=new WorldPlayer();

            PlayerSnapshotCodec.applyValidated(
                aliceDisk.get(),
                loadedAlice
            );
            PlayerSnapshotCodec.applyValidated(
                bobDisk.get(),
                loadedBob
            );

            long newAliceGeneration=freshWorld.registerPlayer(
                loadedAlice,aliceName
            );
            freshWorld.registerPlayer(loadedBob,bobName);

            fileRoundTrip=
                loadedAlice.mailbox().size()==3&&
                loadedAlice.mailboxSnapshotKnown()&&
                loadedAlice.mailbox().snapshot().get(0).message
                    .messageId.equals("g218:read");

            claimedAcknowledgementSafe=
                loadedAlice.mailbox().get("g218:claimed").claimState==
                    MailboxRewardDeliveryService.ClaimState.CLAIMED;

            readStateRestored=
                loadedAlice.mailbox().get("g218:read").readState==
                    MailboxRewardDeliveryService.ReadState.READ&&
                loadedAlice.mailbox().unreadCount()==2;

            retainedAttachments=
                loadedAlice.mailbox().get("g218:claimed")
                    .message.attachments.size()==2&&
                loadedAlice.mailbox().get("g218:unclaimed")
                    .message.attachments.get(0).amount==33;

            otherExtensionPreserved=
                "retain".equals(
                    loadedAlice.snapshotExtensions()
                        .namespace("unrelated-g218").get("marker")
                );

            separateAccountIsolation=
                loadedBob.mailbox().size()==0&&
                noMailboxNamespace(
                    bobDisk.get().values()
                )&&
                !loadedBob.mailboxSnapshotKnown();

            // A malformed namespace is rejected in the detached staging
            // pass before a new live player's core schema is applied.
            TreeMap<String,String> malicious=
                new TreeMap<>(aliceDisk.get().values());

            malicious.put(
                "extension.mailbox-g21.row.1.claim",
                "EMPTY"
            );

            PlayerSnapshot corrupt=
                new PlayerSnapshot(
                    PlayerSnapshot.CURRENT_VERSION,
                    aliceName,
                    malicious
                );
            WorldPlayer notApplied=new WorldPlayer();

            maliciousSnapshotRejectedBeforeApply=
                rejectsArgument(
                    ()->PlayerSnapshotCodec.applyValidated(
                        corrupt,notApplied
                    )
                )&&
                notApplied.mailbox().size()==0&&
                !notApplied.mailboxSnapshotKnown()&&
                notApplied.snapshotExtensions().snapshot().isEmpty();

            occupiedPlayerRejectsLoad=
                rejectsState(
                    ()->PlayerSnapshotCodec.applyValidated(
                        aliceDisk.get(),loadedAlice
                    )
                )&&
                loadedAlice.mailbox().size()==3;

            // Persist the mailbox after removing every message. The
            // explicit zero-count tombstone prevents replay of old mail.
            loadedAlice.mailbox().delete("g218:read");
            loadedAlice.mailbox().delete("g218:claimed");
            loadedAlice.mailbox().delete("g218:unclaimed");

            PlayerSnapshot emptied=
                PlayerSnapshotCodec.capture(
                    aliceName,loadedAlice
                );

            emptyTombstoneSaved=
                loadedAlice.mailbox().size()==0&&
                "0".equals(
                    emptied.value(
                        "extension.mailbox-g21.count"
                    )
                );

            repository.save(emptied);

            WorldPlayer afterDeletion=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(
                repository.load(aliceName).get(),
                afterDeletion
            );

            deletedMessagesNotReplayed=
                afterDeletion.mailbox().size()==0&&
                afterDeletion.mailboxSnapshotKnown()&&
                "0".equals(
                    PlayerSnapshotCodec.capture(
                        aliceName,afterDeletion
                    ).value("extension.mailbox-g21.count")
                );

            generationTurnoverSafe=
                sourceUnregistered&&
                aliceGeneration>0&&
                newAliceGeneration>0&&
                freshWorld.players().owns(
                    loadedAlice,
                    newAliceGeneration
                );

            noSettlementOrPacketClaim=
                loadedAlice.mailbox().size()==0&&
                loadedBob.mailbox().size()==0;

            freshWorld.close();
        }finally{
            Files.deleteIfExists(root.resolve(
                aliceName+".properties"
            ));
            Files.deleteIfExists(root.resolve(
                bobName+".properties"
            ));
            Files.deleteIfExists(root);
        }

        require(
            freshAbsentNamespace&&
            worldOwnedState&&
            capturedAutomatically&&
            fileRoundTrip&&
            claimedAcknowledgementSafe&&
            readStateRestored&&
            retainedAttachments&&
            otherExtensionPreserved&&
            maliciousSnapshotRejectedBeforeApply&&
            occupiedPlayerRejectsLoad&&
            emptyTombstoneSaved&&
            deletedMessagesNotReplayed&&
            separateAccountIsolation&&
            generationTurnoverSafe&&
            noSettlementOrPacketClaim,
            "G21.8 acceptance"
        );

        System.out.println(
            "G218_WORLD_MAILBOX_SNAPSHOT_PASS"+
            " freshAbsentNamespace="+freshAbsentNamespace+
            " worldOwnedState="+worldOwnedState+
            " capturedAutomatically="+capturedAutomatically+
            " fileRoundTrip="+fileRoundTrip+
            " claimedAcknowledgementSafe="+
                claimedAcknowledgementSafe+
            " readStateRestored="+readStateRestored+
            " retainedAttachments="+retainedAttachments+
            " otherExtensionPreserved="+otherExtensionPreserved+
            " maliciousSnapshotRejectedBeforeApply="+
                maliciousSnapshotRejectedBeforeApply+
            " occupiedPlayerRejectsLoad="+occupiedPlayerRejectsLoad+
            " emptyTombstoneSaved="+emptyTombstoneSaved+
            " deletedMessagesNotReplayed="+
                deletedMessagesNotReplayed+
            " separateAccountIsolation="+
                separateAccountIsolation+
            " generationTurnoverSafe="+generationTurnoverSafe+
            " noSettlementOrPacketClaim="+noSettlementOrPacketClaim+
            " liveC2SRootClaim=false"+
            " originalMailboxStoragePolicyClaim=false"+
            " rewardGrantClaim=false"
        );
    }

    private static RewardDeliveryMessage message(
        String id,
        String subject,
        java.util.List<RewardDeliveryMessage.Attachment> attachments
    ){
        return new RewardDeliveryMessage(
            id,subject,
            "G21.8 world-owned synthetic envelope",
            attachments,
            "CUSTOM_LOCALLAB_G218_FIXTURE"
        );
    }

    private static boolean noMailboxNamespace(
        SortedMap<String,String> values
    ){
        return values.keySet().stream().noneMatch(
            key->key.startsWith("extension.mailbox-g21.")
        );
    }

    private interface Check{
        void run();
    }

    private static boolean rejectsArgument(Check action){
        try{
            action.run();
            return false;
        }catch(IllegalArgumentException expected){
            return true;
        }
    }

    private static boolean rejectsState(Check action){
        try{
            action.run();
            return false;
        }catch(IllegalStateException expected){
            return true;
        }
    }

    private static void require(boolean ok,String detail){
        if(!ok)
            throw new AssertionError(detail);
    }

    private G218WorldMailboxSnapshotIntegrationTest(){}
}
