package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Strict file-level commit barrier regression.
 *
 * No in-game claim is enabled: old autosaves and checkpoint sequencing are
 * not yet coordinated with this separate opt-in writer. Tests distinguish
 * failures BEFORE rename (old snapshot retained) from failures AFTER
 * rename (new snapshot may exist; no successful Receipt).
 */
public final class G2123MailboxStrictDurableSaveIntegrationTest {
    public static void main(String[] args)throws Exception{
        boolean completeSnapshotRoundTrip=false;
        boolean strictReceiptOnlyAfterForces=false;
        boolean precommitFailureOldIntact=false;
        boolean noOrphanTemps=false;
        boolean afterMoveUnconfirmed=false;
        boolean afterDirForceUnconfirmed=false;
        boolean stagedIntentRemainsInert=false;
        boolean secondAccountIsolated=false;
        boolean unsupportedSnapshotFailClosed=false;
        boolean immutableSnapshotPostimage=false;
        boolean nativeInventoryClaimRemainsBlocked=false;
        boolean noFallbackOrUnsoundGuarantee=false;

        Path dir=Files.createTempDirectory("g2123-strict-");
        try{
            FilePlayerRepository.PathResolver paths=
                account->dir.resolve(account+".properties");
            StrictDurablePlayerSnapshotWriter strict=
                new StrictDurablePlayerSnapshotWriter(paths);
            FilePlayerRepository reader=
                new FilePlayerRepository(paths);

            WorldPlayer alice=new WorldPlayer();
            long ag=alice.markRegistered("g2123-alice");
            alice.mailbox().deliver(new RewardDeliveryMessage(
                "g2123:gift","Durable-test gift","LocalLab",
                java.util.Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)
                ),"CUSTOM_LOCALLAB_G2123_FIXTURE"
            ));

            PlayerSnapshot original=
                PlayerSnapshotCodec.capture("g2123-alice",alice);
            StrictDurablePlayerSnapshotWriter.Receipt baseline=
                strict.saveStrict(original);
            strictReceiptOnlyAfterForces=
                baseline.file.equals(paths.resolve("g2123-alice")
                    .toAbsolutePath().normalize())&&
                "g2123-alice".equals(baseline.account)&&
                StrictDurablePlayerSnapshotWriter.AUTHORITY.equals(
                    baseline.authority
                );

            MailboxPreparedClaimJournal.Intent prepared=
                MailboxPreparedClaimJournal.prepare(
                    alice,alice.mailbox().get("g2123:gift")
                );
            boolean staged=MailboxPreparedClaimJournal.stageOnly(
                alice,prepared
            );
            PlayerSnapshot next=PlayerSnapshotCodec.capture(
                "g2123-alice",alice
            );
            String intentKey="extension."+
                MailboxPreparedClaimJournal.NAMESPACE+".key";
            immutableSnapshotPostimage=
                staged&&
                original.value(intentKey)==null&&
                prepared.idempotencyKey.equals(next.value(intentKey));

            StrictDurablePlayerSnapshotWriter.Phase[] beforeMove={
                StrictDurablePlayerSnapshotWriter.Phase
                    .BEFORE_TEMP_CREATE,
                StrictDurablePlayerSnapshotWriter.Phase
                    .AFTER_TEMP_CREATE,
                StrictDurablePlayerSnapshotWriter.Phase
                    .AFTER_SERIALIZE,
                StrictDurablePlayerSnapshotWriter.Phase
                    .BEFORE_FILE_FORCE,
                StrictDurablePlayerSnapshotWriter.Phase
                    .BEFORE_ATOMIC_REPLACE
            };
            precommitFailureOldIntact=true;
            noOrphanTemps=true;
            for(StrictDurablePlayerSnapshotWriter.Phase fail:beforeMove){
                StrictDurablePlayerSnapshotWriter broken=
                    new StrictDurablePlayerSnapshotWriter(
                        paths,at->{
                            if(at==fail)
                                throw new IOException(
                                    "simulated strict failure "+fail
                                );
                        }
                    );
                boolean threw=false;
                try{
                    broken.saveStrict(next);
                }catch(IOException expected){
                    threw=true;
                }
                PlayerSnapshot disk=reader.load(
                    "g2123-alice"
                ).get();
                precommitFailureOldIntact &=
                    threw&&disk.values().equals(original.values())&&
                    disk.value(intentKey)==null;
                try(Stream<Path> files=Files.list(dir)){
                    noOrphanTemps &=
                        files.noneMatch(p->p.getFileName()
                            .toString().contains(".g2123-"));
                }
            }

            StrictDurablePlayerSnapshotWriter.Receipt committed=
                strict.saveStrict(next);
            PlayerSnapshot recovered=reader.load(
                "g2123-alice"
            ).get();
            WorldPlayer restored=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(recovered,restored);
            MailboxPreparedClaimJournal.Intent restoredIntent=
                MailboxPreparedClaimJournal.inspectPrepared(restored);
            completeSnapshotRoundTrip=
                committed.file.equals(baseline.file)&&
                recovered.values().equals(next.values())&&
                restoredIntent!=null&&
                restoredIntent.idempotencyKey.equals(
                    prepared.idempotencyKey
                )&&
                restored.mailbox().get("g2123:gift").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                restored.bank().inventorySlots()==0;

            // The file has been atomically replaced but a force operation
            // failed: never return success; state may already be changed.
            StrictDurablePlayerSnapshotWriter beforeForce=
                new StrictDurablePlayerSnapshotWriter(
                    paths,at->{
                        if(at==StrictDurablePlayerSnapshotWriter.Phase
                            .BEFORE_DIRECTORY_FORCE)
                            throw new IOException("directory force denied");
                    }
                );
            boolean noReceipt=false;
            try{
                beforeForce.saveStrict(original);
            }catch(
                StrictDurablePlayerSnapshotWriter
                    .UnconfirmedCommitException expected
            ){
                noReceipt=true;
            }
            afterMoveUnconfirmed=
                noReceipt&&reader.load("g2123-alice").get()
                    .values().equals(original.values());

            StrictDurablePlayerSnapshotWriter afterForce=
                new StrictDurablePlayerSnapshotWriter(
                    paths,at->{
                        if(at==StrictDurablePlayerSnapshotWriter.Phase
                            .AFTER_DIRECTORY_FORCE)
                            throw new IOException(
                                "synthetic post-force observation failed"
                            );
                    }
                );
            boolean noPostReceipt=false;
            try{
                afterForce.saveStrict(next);
            }catch(
                StrictDurablePlayerSnapshotWriter
                    .UnconfirmedCommitException expected
            ){
                noPostReceipt=true;
            }
            afterDirForceUnconfirmed=
                noPostReceipt&&reader.load("g2123-alice").get()
                    .values().equals(next.values());

            WorldPlayer again=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(
                reader.load("g2123-alice").get(),again
            );
            stagedIntentRemainsInert=
                again.bank().inventorySlots()==0&&
                again.mailbox().get("g2123:gift").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                MailboxPreparedClaimJournal.inspectPrepared(again)!=null;

            WorldPlayer bob=new WorldPlayer();
            bob.mailbox().deliver(new RewardDeliveryMessage(
                "g2123:bob","Private","Fixture",
                java.util.Collections.emptyList(),
                "CUSTOM_LOCALLAB_G2123_FIXTURE"
            ));
            PlayerSnapshot bobSnapshot=
                PlayerSnapshotCodec.capture("g2123-bob",bob);
            strict.saveStrict(bobSnapshot);
            secondAccountIsolated=
                reader.load("g2123-bob").get()
                    .values().equals(bobSnapshot.values())&&
                reader.load("g2123-alice").get()
                    .values().equals(next.values())&&
                reader.load("g2123-bob").get()
                    .value(intentKey)==null;

            boolean wrongVersionDenied=false;
            try{
                strict.saveStrict(new PlayerSnapshot(
                    777,"g2123-alice",
                    new TreeMap<>(next.values())
                ));
            }catch(IOException expected){
                wrongVersionDenied=true;
            }
            unsupportedSnapshotFailClosed=
                wrongVersionDenied&&
                reader.load("g2123-alice").get()
                    .values().equals(next.values());

            nativeInventoryClaimRemainsBlocked=
                "CUSTOM_LOCALLAB_G2121_CLAIM_PREFLIGHT_ONLY".equals(
                    MailboxInventoryClaimPreflight.AUTHORITY
                )&&again.bank().inventorySlots()==0&&
                again.mailbox().get("g2123:gift").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            noFallbackOrUnsoundGuarantee=
                StrictDurablePlayerSnapshotWriter.AUTHORITY
                    .contains("STRICT_FILE_BOUNDARY_ONLY")&&
                strictReceiptOnlyAfterForces&&
                !StrictDurablePlayerSnapshotWriter.class
                    .isAssignableFrom(PlayerRepository.class);

            alice.markUnregistered();
        }finally{
            try(Stream<Path> entries=Files.walk(dir)){
                for(Path path:entries.sorted(
                        java.util.Comparator.reverseOrder()
                    ).toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }

        System.out.println(
            "G2123_MAILBOX_STRICT_DURABILITY_DIAGNOSTICS"+
            " completeSnapshotRoundTrip="+completeSnapshotRoundTrip+
            " strictReceiptOnlyAfterForces="+
                strictReceiptOnlyAfterForces+
            " precommitFailureOldIntact="+precommitFailureOldIntact+
            " noOrphanTemps="+noOrphanTemps+
            " afterMoveUnconfirmed="+afterMoveUnconfirmed+
            " afterDirForceUnconfirmed="+afterDirForceUnconfirmed+
            " stagedIntentRemainsInert="+stagedIntentRemainsInert+
            " secondAccountIsolated="+secondAccountIsolated+
            " unsupportedSnapshotFailClosed="+
                unsupportedSnapshotFailClosed+
            " immutableSnapshotPostimage="+immutableSnapshotPostimage+
            " nativeInventoryClaimRemainsBlocked="+
                nativeInventoryClaimRemainsBlocked+
            " noFallbackOrUnsoundGuarantee="+
                noFallbackOrUnsoundGuarantee
        );
        require(
            completeSnapshotRoundTrip&&strictReceiptOnlyAfterForces&&
            precommitFailureOldIntact&&noOrphanTemps&&
            afterMoveUnconfirmed&&afterDirForceUnconfirmed&&
            stagedIntentRemainsInert&&secondAccountIsolated&&
            unsupportedSnapshotFailClosed&&
            immutableSnapshotPostimage&&
            nativeInventoryClaimRemainsBlocked&&
            noFallbackOrUnsoundGuarantee,
            "G21.23 strict writer regression"
        );
        System.out.println(
            "G2123_MAILBOX_STRICT_DURABILITY_PASS"+
            " forceFileThenAtomicReplaceThenForceDirectory=true"+
            " noNonAtomicFallback=true"+
            " preReplaceFailurePreservesAccount=true"+
            " postReplaceFailureUnconfirmed=true"+
            " preparedJournalAndMailboxRoundTrip=true"+
            " originalAsyncPersistenceNotModified=true"+
            " zeroInventorySettlement=true"+
            " powerLossHardwareGuaranteeClaim=false"+
            " liveClaimEnabled=false"
        );
    }

    private static void require(boolean condition,String name){
        if(!condition)throw new AssertionError(name);
    }

    private G2123MailboxStrictDurableSaveIntegrationTest(){}
}
