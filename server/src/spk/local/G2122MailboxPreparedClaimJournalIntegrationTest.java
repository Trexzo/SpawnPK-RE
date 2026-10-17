package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * G21.22 PREPARED journal foundation. This test proves canonical snapshots,
 * stable replay identity and restart-preserved prepared markers, NOT durable
 * atomic reward settlement or successful native-client item claiming.
 */
public final class G2122MailboxPreparedClaimJournalIntegrationTest {
    public static void main(String[] args)throws Exception{
        boolean deterministicIdentity=false;
        boolean canonicalRoundTrip=false;
        boolean copiesDefensive=false;
        boolean replayIdempotent=false;
        boolean conflictingIntentRejected=false;
        boolean preimageChangeRejected=false;
        boolean staleMessageDenied=false;
        boolean tamperingDenied=false;
        boolean unsupportedStateDenied=false;
        boolean snapshotCoLocation=false;
        boolean fileRoundTripPreparedOnly=false;
        boolean repeatAfterRestartNoGrant=false;
        boolean unrelatedAccountIsolated=false;
        boolean claimedFailsClosed=false;
        boolean noCreditOrClaim=false;
        boolean crashDurabilityUnclaimed=false;
        boolean nativeWidgetNotSettled=false;

        Path directory=Files.createTempDirectory(
            "g2122-prepared-journal-"
        );
        try(World world=World.isolatedForTest(60000L)){
            WorldPlayer alice=new WorldPlayer();
            WorldPlayer bob=new WorldPlayer();
            long ag=world.registerPlayer(alice,"g2122-alice");
            long bg=world.registerPlayer(bob,"g2122-bob");
            WorldMailboxGateway gateway=new WorldMailboxGateway(
                world,alice,ag
            );
            WorldMailboxGateway other=new WorldMailboxGateway(
                world,bob,bg
            );
            gateway.deliver(message(
                "g2122:primary","Primary",995,50,560,5
            ));
            gateway.deliver(message(
                "g2122:other","Other",995,2
            ));
            other.deliver(message(
                "g2122:bob","Private",995,6
            ));

            MailboxRewardDeliveryService.Snapshot selected=
                alice.mailbox().get("g2122:primary");
            MailboxPreparedClaimJournal.Intent intent=
                MailboxPreparedClaimJournal.prepare(alice,selected);
            MailboxPreparedClaimJournal.Intent independently=
                MailboxPreparedClaimJournal.prepare(alice,selected);
            deterministicIdentity=
                intent.idempotencyKey.equals(
                    independently.idempotencyKey
                )&&
                intent.idempotencyKey.matches("[a-f0-9]{64}")&&
                "g2122-alice".equals(intent.account)&&
                "g2122:primary".equals(intent.messageId)&&
                intent.attachmentFingerprint.equals("995:50,560:5");

            SortedMap<String,String> canonical=
                MailboxPreparedClaimJournal.encode(intent);
            MailboxPreparedClaimJournal.Intent decoded=
                MailboxPreparedClaimJournal.decode(canonical);
            canonicalRoundTrip=
                canonical.size()==9&&
                canonical.equals(
                    MailboxPreparedClaimJournal.encode(decoded)
                )&&
                decoded.idempotencyKey.equals(
                    intent.idempotencyKey
                )&&
                MailboxPreparedClaimJournal.STATE.equals(
                    canonical.get("state")
                )&&
                MailboxPreparedClaimJournal.AUTHORITY.equals(
                    canonical.get("authority")
                );

            int[] expected=decoded.expectedItemIds();
            int[] proposed=decoded.proposedQuantities();
            expected[0]=12345;
            proposed[0]=12345;
            copiesDefensive=
                decoded.expectedItemIds()[0]==-1&&
                decoded.proposedQuantities()[0]==50&&
                decoded.proposedItemIds()[0]==995;

            boolean stage=MailboxPreparedClaimJournal.stageOnly(
                alice,intent
            );
            boolean replay=MailboxPreparedClaimJournal.stageOnly(
                alice,independently
            );
            replayIdempotent=
                stage&&!replay&&
                intent.idempotencyKey.equals(
                    MailboxPreparedClaimJournal.inspectPrepared(
                        alice
                    ).idempotencyKey
                );

            MailboxPreparedClaimJournal.Intent otherIntent=
                MailboxPreparedClaimJournal.prepare(
                    alice,alice.mailbox().get("g2122:other")
                );
            conflictingIntentRejected=
                rejects(()->MailboxPreparedClaimJournal.stageOnly(
                    alice,otherIntent
                ))&&
                canonical.equals(
                    alice.snapshotExtensions().namespace(
                        MailboxPreparedClaimJournal.NAMESPACE
                    )
                );

            int[] changedIds=new int[28],changedQty=new int[28];
            Arrays.fill(changedIds,-1);
            changedIds[0]=995;
            changedQty[0]=1;
            alice.bank().replaceInventorySemantic(
                changedIds,changedQty
            );
            preimageChangeRejected=
                rejects(()->MailboxPreparedClaimJournal.stageOnly(
                    alice,intent
                ))&&
                canonical.equals(
                    alice.snapshotExtensions().namespace(
                        MailboxPreparedClaimJournal.NAMESPACE
                    )
                );

            // Restore canonical preimage for the remainder of the test;
            // neither change has credited the Mailbox attachments.
            Arrays.fill(changedIds,-1);
            Arrays.fill(changedQty,0);
            alice.bank().replaceInventorySemantic(
                changedIds,changedQty
            );

            SortedMap<String,String> badKey=
                new TreeMap<>(canonical);
            badKey.put("key","0".repeat(64));
            tamperingDenied=rejects(
                ()->MailboxPreparedClaimJournal.decode(badKey)
            );
            SortedMap<String,String> badAfter=
                new TreeMap<>(canonical);
            badAfter.put("after",badAfter.get("after").replaceFirst(
                "995:50","995:51"
            ));
            tamperingDenied &=rejects(
                ()->MailboxPreparedClaimJournal.decode(badAfter)
            );
            SortedMap<String,String> badExtra=
                new TreeMap<>(canonical);
            badExtra.put("extra","unexpected");
            tamperingDenied &=rejects(
                ()->MailboxPreparedClaimJournal.decode(badExtra)
            );
            SortedMap<String,String> badMissing=
                new TreeMap<>(canonical);
            badMissing.remove("before");
            tamperingDenied &=rejects(
                ()->MailboxPreparedClaimJournal.decode(badMissing)
            );

            SortedMap<String,String> badState=
                new TreeMap<>(canonical);
            badState.put("state","COMMITTED");
            unsupportedStateDenied=rejects(
                ()->MailboxPreparedClaimJournal.decode(badState)
            );
            SortedMap<String,String> badVersion=
                new TreeMap<>(canonical);
            badVersion.put("version","2");
            unsupportedStateDenied &=rejects(
                ()->MailboxPreparedClaimJournal.decode(badVersion)
            );
            SortedMap<String,String> badAuthority=
                new TreeMap<>(canonical);
            badAuthority.put("authority","EXACT_CURRENT_CLIENT");
            unsupportedStateDenied &=rejects(
                ()->MailboxPreparedClaimJournal.decode(badAuthority)
            );

            PlayerSnapshot snapshot=PlayerSnapshotCodec.capture(
                "g2122-alice",alice
            );
            String prefix="extension."+
                MailboxPreparedClaimJournal.NAMESPACE+".";
            snapshotCoLocation=
                snapshot.value(prefix+"state").equals(
                    MailboxPreparedClaimJournal.STATE
                )&&
                snapshot.value(prefix+"key").equals(
                    intent.idempotencyKey
                )&&
                snapshot.value("extension.mailbox-g21.row.0.claim")
                    .equals("UNCLAIMED");

            // File repository serializes a *single* existing account
            // snapshot, but is not declared power-fail-durable because its
            // fallback move is non-atomic and no fsync contract exists.
            FilePlayerRepository repository=
                new FilePlayerRepository(
                    username->directory.resolve(
                        username+".properties"
                    )
                );
            repository.save(snapshot);
            PlayerSnapshot disk=repository.load(
                "g2122-alice"
            ).get();
            WorldPlayer restart=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(disk,restart);
            MailboxPreparedClaimJournal.Intent reloaded=
                MailboxPreparedClaimJournal.inspectPrepared(
                    restart
                );
            fileRoundTripPreparedOnly=
                reloaded!=null&&
                intent.idempotencyKey.equals(
                    reloaded.idempotencyKey
                )&&
                restart.mailbox().get("g2122:primary").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                restart.bank().inventorySlots()==0;

            // Restarting the same account retains a PREPARED marker but
            // never grants an item or silently marks CLAIMED.
            long old=world.unregisterPlayer(alice,ag)?ag:0;
            long fresh=world.registerPlayer(
                restart,"g2122-alice"
            );
            repeatAfterRestartNoGrant=
                old==ag&&fresh>0&&
                !MailboxPreparedClaimJournal.stageOnly(
                    restart,
                    MailboxPreparedClaimJournal.prepare(
                        restart,
                        restart.mailbox().get("g2122:primary")
                    )
                )&&
                restart.bank().inventorySlots()==0&&
                restart.mailbox().get("g2122:primary").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;

            unrelatedAccountIsolated=
                bob.snapshotExtensions().namespace(
                    MailboxPreparedClaimJournal.NAMESPACE
                ).isEmpty()&&
                bob.bank().inventorySlots()==0&&
                bob.mailbox().get("g2122:primary")==null&&
                bob.mailbox().get("g2122:bob").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;

            // No new intent can be staged for an already claimed
            // envelope, even when the selected ID still exists.
            restart.mailbox().acknowledgeAttachmentSettlement(
                "g2122:primary"
            );
            claimedFailsClosed=
                rejects(()->MailboxPreparedClaimJournal.prepare(
                    restart,
                    restart.mailbox().get("g2122:primary")
                ))&&
                rejects(()->MailboxPreparedClaimJournal.stageOnly(
                    restart,reloaded
                ));
            restart.mailbox().delete("g2122:primary");
            restart.mailbox().deliver(message(
                "g2122:primary","Replaced",995,50,560,5
            ));
            staleMessageDenied=
                rejects(()->MailboxPreparedClaimJournal.prepare(
                    restart,
                    selected
                ));

            noCreditOrClaim=
                alice.bank().inventorySlots()==0&&
                alice.mailbox().get("g2122:primary").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                bob.bank().inventorySlots()==0;
            crashDurabilityUnclaimed=
                "PREPARED_NO_GRANT".equals(
                    reloaded==null?"":MailboxPreparedClaimJournal.STATE
                )&&
                disk.value(prefix+"state").equals(
                    MailboxPreparedClaimJournal.STATE
                );

            nativeWidgetNotSettled=
                MailboxInventoryClaimPreflight.AUTHORITY.endsWith(
                    "PREFLIGHT_ONLY"
                )&&
                Arrays.stream(
                    MailboxPreparedClaimJournal.class
                        .getDeclaredMethods()
                ).noneMatch(method->
                    method.getName().startsWith("commit")||
                    method.getName().startsWith("grant")||
                    method.getName().startsWith("settle")
                );
        }finally{
            try(java.util.stream.Stream<Path> contents=
                    Files.list(directory)){
                contents.forEach(path->{
                    try{Files.deleteIfExists(path);}
                    catch(IOException fail){
                        throw new IllegalStateException(fail);
                    }
                });
            }
            Files.deleteIfExists(directory);
        }

        System.out.println(
            "G2122_MAILBOX_PREPARED_JOURNAL_DIAGNOSTICS"+
            " deterministicIdentity="+deterministicIdentity+
            " canonicalRoundTrip="+canonicalRoundTrip+
            " copiesDefensive="+copiesDefensive+
            " replayIdempotent="+replayIdempotent+
            " conflictingIntentRejected="+conflictingIntentRejected+
            " preimageChangeRejected="+preimageChangeRejected+
            " staleMessageDenied="+staleMessageDenied+
            " tamperingDenied="+tamperingDenied+
            " unsupportedStateDenied="+unsupportedStateDenied+
            " snapshotCoLocation="+snapshotCoLocation+
            " fileRoundTripPreparedOnly="+fileRoundTripPreparedOnly+
            " repeatAfterRestartNoGrant="+repeatAfterRestartNoGrant+
            " unrelatedAccountIsolated="+unrelatedAccountIsolated+
            " claimedFailsClosed="+claimedFailsClosed+
            " noCreditOrClaim="+noCreditOrClaim+
            " crashDurabilityUnclaimed="+crashDurabilityUnclaimed+
            " nativeWidgetNotSettled="+nativeWidgetNotSettled
        );
        require(
            deterministicIdentity&&canonicalRoundTrip&&
            copiesDefensive&&replayIdempotent&&
            conflictingIntentRejected&&preimageChangeRejected&&
            staleMessageDenied&&tamperingDenied&&
            unsupportedStateDenied&&snapshotCoLocation&&
            fileRoundTripPreparedOnly&&repeatAfterRestartNoGrant&&
            unrelatedAccountIsolated&&claimedFailsClosed&&
            noCreditOrClaim&&crashDurabilityUnclaimed&&
            nativeWidgetNotSettled,
            "G21.22 prepared-only journal"
        );

        System.out.println(
            "G2122_MAILBOX_PREPARED_JOURNAL_PASS"+
            " stableAccountEnvelopeIntent=true"+
            " canonicalVersionedSnapshot=true"+
            " defensivePostimages=true"+
            " replayConflictFailClosed=true"+
            " savedAndReloadedPreparedNoGrant=true"+
            " tamperAndUnsupportedStateDenied=true"+
            " inventoryCredit=false"+
            " mailboxClaimAcknowledgement=false"+
            " powerFailDurabilityClaim=false"+
            " originalSettlementPolicyClaim=false"
        );
    }

    private static RewardDeliveryMessage message(
        String id,String subject,long... pairs
    ){
        java.util.ArrayList<RewardDeliveryMessage.Attachment> items=
            new java.util.ArrayList<>();
        for(int i=0;i<pairs.length;i+=2)
            items.add(new RewardDeliveryMessage.Attachment(
                (int)pairs[i],pairs[i+1]
            ));
        return new RewardDeliveryMessage(
            id,subject,"G21.22 prepared journal",
            Collections.unmodifiableList(items),
            "CUSTOM_LOCALLAB_G2122_FIXTURE"
        );
    }

    private interface Action { Object run()throws Exception; }

    private static boolean rejects(Action action){
        try{action.run();return false;}
        catch(IllegalArgumentException|
              IllegalStateException expected){return true;}
        catch(Exception unexpected){
            throw new IllegalStateException(unexpected);
        }
    }

    private static void require(boolean condition,String label){
        if(!condition)throw new AssertionError(label);
    }

    private G2122MailboxPreparedClaimJournalIntegrationTest(){}
}
