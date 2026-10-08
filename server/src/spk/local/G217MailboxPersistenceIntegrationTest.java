package spk.local;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

public final class G217MailboxPersistenceIntegrationTest {
    public static void main(String[] args)throws Exception{
        boolean fileRoundTrip=false;
        boolean stableInsertionOrder=false;
        boolean readStateRestored=false;
        boolean claimAcknowledgementRestored=false;
        boolean attachmentAmountsRestored=false;
        boolean unicodeBodyRestored=false;
        boolean absentNamespaceEmpty=false;
        boolean exactSchemaFailClosed=false;
        boolean malformedUtf8FailClosed=false;
        boolean noncanonicalReject=false;
        boolean duplicateIdFailClosed=false;
        boolean capacityFailClosed=false;
        boolean atomicRestoreFailClosed=false;
        boolean replayRestoreDenied=false;
        boolean noRewardMutation=false;
        boolean noPacketDependency=false;

        MailboxRewardDeliveryService original=
            new MailboxRewardDeliveryService(8);

        original.deliver(
            message(
                "g217:notice",
                "Notice",
                "Body line 1\nBody line 2. 🛰",
                Collections.emptyList()
            )
        );

        original.deliver(
            message(
                "g217:reward",
                "Reward - Skåne",
                "UTF-8 envelope with ä and Ω",
                Arrays.asList(
                    new RewardDeliveryMessage.Attachment(995,5000),
                    new RewardDeliveryMessage.Attachment(
                        4151,
                        2147483648L
                    )
                )
            )
        );

        original.deliver(
            message(
                "g217:unclaimed",
                "Unclaimed",
                "External claim is not automated",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(65535,7)
                )
            )
        );

        original.markRead("g217:notice");
        original.acknowledgeAttachmentSettlement("g217:reward");

        SortedMap<String,String> encoded=
            LocalLabMailboxPersistence.encode(original);

        List<MailboxRewardDeliveryService.RestoredEntry> decoded=
            LocalLabMailboxPersistence.decode(encoded);

        Path root=Files.createTempDirectory(
            "spawnpk-g217-mailbox-"
        );
        String username="mailbox-g217-persist";
        Path file=root.resolve(username+".properties");

        try{
            FilePlayerRepository repository=
                new FilePlayerRepository(
                    name->root.resolve(name+".properties")
                );

            WorldPlayer source=new WorldPlayer();
            source.snapshotExtensions().replaceNamespace(
                LocalLabMailboxPersistence.NAMESPACE,
                encoded
            );

            repository.save(
                PlayerSnapshotCodec.capture(username,source)
            );

            Optional<PlayerSnapshot> saved=
                repository.load(username);

            require(saved.isPresent(),"missing saved Mailbox account");

            WorldPlayer fresh=new WorldPlayer();

            PlayerSnapshotCodec.applyValidated(
                saved.get(),
                fresh
            );

            SortedMap<String,String> persisted=
                fresh.snapshotExtensions().namespace(
                    LocalLabMailboxPersistence.NAMESPACE
                );

            fileRoundTrip=
                persisted.equals(encoded);

            MailboxRewardDeliveryService restored=
                new MailboxRewardDeliveryService(8);

            restored.restore(
                LocalLabMailboxPersistence.decode(
                    persisted
                )
            );

            List<MailboxRewardDeliveryService.Snapshot> rows=
                restored.snapshot();

            stableInsertionOrder=
                rows.size()==3&&
                "g217:notice".equals(rows.get(0).message.messageId)&&
                "g217:reward".equals(rows.get(1).message.messageId)&&
                "g217:unclaimed".equals(rows.get(2).message.messageId);

            readStateRestored=
                restored.get("g217:notice").readState==
                    MailboxRewardDeliveryService.ReadState.READ&&
                restored.get("g217:reward").readState==
                    MailboxRewardDeliveryService.ReadState.UNREAD&&
                restored.unreadCount()==2;

            claimAcknowledgementRestored=
                restored.get("g217:notice").claimState==
                    MailboxRewardDeliveryService.ClaimState.EMPTY&&
                restored.get("g217:reward").claimState==
                    MailboxRewardDeliveryService.ClaimState.CLAIMED&&
                restored.get("g217:unclaimed").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;

            attachmentAmountsRestored=
                restored.get("g217:reward")
                    .message.attachments.size()==2&&
                restored.get("g217:reward")
                    .message.attachments.get(1).amount==2147483648L&&
                restored.get("g217:unclaimed")
                    .message.attachments.get(0).itemId==65535;

            unicodeBodyRestored=
                "Body line 1\nBody line 2. 🛰".equals(
                    restored.get("g217:notice").message.body
                )&&
                "Reward - Skåne".equals(
                    restored.get("g217:reward").message.subject
                );

            absentNamespaceEmpty=
                LocalLabMailboxPersistence.decode(
                    Collections.emptySortedMap()
                ).isEmpty();

            replayRestoreDenied=
                rejectsState(()->restored.restore(decoded))&&
                restored.size()==3;

            noRewardMutation=
                restored.get("g217:reward").claimState==
                    MailboxRewardDeliveryService.ClaimState.CLAIMED&&
                restored.get("g217:reward").message.attachments
                    .get(0).amount==5000&&
                restored.get("g217:unclaimed").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
        }finally{
            Files.deleteIfExists(file);
            Files.deleteIfExists(root);
        }

        exactSchemaFailClosed=
            decodeRejects(change(encoded,"version","99"))&&
            decodeRejects(change(encoded,"authority","UNTRUSTED"))&&
            decodeRejects(without(encoded,"row.0.id"))&&
            decodeRejects(change(encoded,"row.1.claim","EMPTY"))&&
            decodeRejects(change(encoded,"row.0.claim","UNCLAIMED"))&&
            decodeRejects(change(encoded,"row.1.item.0.amount","0"))&&
            decodeRejects(change(encoded,"row.1.item.0.id","-1"))&&
            decodeRejects(change(encoded,"row.0.read","UNRECOGNIZED"))&&
            decodeRejects(change(encoded,"count","4"));

        TreeMap<String,String> unexpected=new TreeMap<>(encoded);
        unexpected.put("row.0.reward-grant","995");
        exactSchemaFailClosed &=decodeRejects(unexpected);

        malformedUtf8FailClosed=
            decodeRejects(change(encoded,"row.0.body","/w=="))&&
            decodeRejects(change(encoded,"row.0.body","!!not-base64!!"));

        noncanonicalReject=
            decodeRejects(change(encoded,"count","03"))&&
            decodeRejects(change(encoded,"row.1.item.0.amount","+5000"))&&
            decodeRejects(change(encoded,"row.1.item.0.amount","05000"));

        duplicateIdFailClosed=
            decodeRejects(change(
                encoded,
                "row.2.id",
                encoded.get("row.1.id")
            ));

        MailboxRewardDeliveryService undersized=
            new MailboxRewardDeliveryService(2);

        capacityFailClosed=
            rejectsArgument(()->undersized.restore(decoded))&&
            undersized.size()==0;

        MailboxRewardDeliveryService badLate=
            new MailboxRewardDeliveryService(8);

        List<MailboxRewardDeliveryService.RestoredEntry> late=
            new ArrayList<>(decoded);

        late.add(decoded.get(0));

        atomicRestoreFailClosed=
            rejectsArgument(()->badLate.restore(late))&&
            badLate.size()==0;

        noPacketDependency=
            Arrays.stream(
                LocalLabMailboxPersistence.class
                    .getDeclaredMethods()
            ).noneMatch(method->
                Arrays.stream(method.getParameterTypes())
                    .anyMatch(type->
                        type==ServerPacketWriter.class
                    )
            );

        require(
            fileRoundTrip&&
            stableInsertionOrder&&
            readStateRestored&&
            claimAcknowledgementRestored&&
            attachmentAmountsRestored&&
            unicodeBodyRestored&&
            absentNamespaceEmpty&&
            exactSchemaFailClosed&&
            malformedUtf8FailClosed&&
            noncanonicalReject&&
            duplicateIdFailClosed&&
            capacityFailClosed&&
            atomicRestoreFailClosed&&
            replayRestoreDenied&&
            noRewardMutation&&
            noPacketDependency,
            "G21.7 acceptance"
        );

        System.out.println(
            "G217_MAILBOX_PERSISTENCE_PASS"+
            " fileRoundTrip="+fileRoundTrip+
            " stableInsertionOrder="+stableInsertionOrder+
            " readStateRestored="+readStateRestored+
            " claimAcknowledgementRestored="+
                claimAcknowledgementRestored+
            " attachmentAmountsRestored="+attachmentAmountsRestored+
            " unicodeBodyRestored="+unicodeBodyRestored+
            " absentNamespaceEmpty="+absentNamespaceEmpty+
            " exactSchemaFailClosed="+exactSchemaFailClosed+
            " malformedUtf8FailClosed="+malformedUtf8FailClosed+
            " noncanonicalReject="+noncanonicalReject+
            " duplicateIdFailClosed="+duplicateIdFailClosed+
            " capacityFailClosed="+capacityFailClosed+
            " atomicRestoreFailClosed="+atomicRestoreFailClosed+
            " replayRestoreDenied="+replayRestoreDenied+
            " noRewardMutation="+noRewardMutation+
            " noPacketDependency="+noPacketDependency+
            " liveWorldIntegrationClaim=false"+
            " originalStoragePolicyClaim=false"+
            " rewardSettlementClaim=false"
        );
    }

    private static RewardDeliveryMessage message(
        String id,String subject,String body,
        List<RewardDeliveryMessage.Attachment> attachments
    ){
        return new RewardDeliveryMessage(
            id,subject,body,attachments,
            "CUSTOM_LOCALLAB_G217_FIXTURE"
        );
    }

    private static SortedMap<String,String> change(
        SortedMap<String,String> original,
        String key,String value
    ){
        TreeMap<String,String> mutable=new TreeMap<>(original);
        mutable.put(key,value);
        return mutable;
    }

    private static SortedMap<String,String> without(
        SortedMap<String,String> original,
        String key
    ){
        TreeMap<String,String> mutable=new TreeMap<>(original);
        mutable.remove(key);
        return mutable;
    }

    private interface Operation {
        void run();
    }

    private static boolean rejectsArgument(Operation test){
        try{
            test.run();
            return false;
        }catch(IllegalArgumentException expected){
            return true;
        }
    }

    private static boolean rejectsState(Operation test){
        try{
            test.run();
            return false;
        }catch(IllegalStateException expected){
            return true;
        }
    }

    private static boolean decodeRejects(
        SortedMap<String,String> values
    ){
        return rejectsArgument(
            ()->LocalLabMailboxPersistence.decode(values)
        );
    }

    private static void require(boolean yes,String message){
        if(!yes)
            throw new AssertionError(message);
    }

    private G217MailboxPersistenceIntegrationTest(){}
}
