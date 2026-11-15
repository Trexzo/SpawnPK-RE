package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * R59 offline-only index registration, revision and completeness controls.
 * Disposable fixture only: no LocalSession or real player accounts touched.
 */
public final class R59TradePairIntentIndexTest {
    private static final UUID FIRST=UUID.fromString(
        "11111111-2222-4333-8444-555555555555");
    private static final UUID SECOND=UUID.fromString(
        "77777777-8888-4999-aaaa-bbbbbbbbbbbb");
    private static final UUID THIRD=UUID.fromString(
        "99999999-aaaa-4bbb-8ccc-dddddddddddd");

    public static void main(String[] args)throws Exception{
        Path scratch=Files.createTempDirectory("spk-r59-index-");
        try{
            Path root=scratch.resolve("pair-journal");
            Path accounts=scratch.resolve("test-profiles");
            Files.createDirectory(root);
            Files.createDirectory(accounts);
            FilePlayerRepository accountRepo=new FilePlayerRepository(
                name->accounts.resolve(name+".properties")
            );

            R59TradePairIntentIndex.Result missing=status(
                root,"opensrc",R59TradePairIntentIndex.Status
                    .BLOCK_INDETERMINATE_INDEX
            );
            require(!missing.canProceedToOtherLoginChecks() &&
                    !missing.loginAuthorized(),
                "missing index admitted a new or returning account");

            Path first=writeIntent(root,FIRST,"opensrc","src",accountRepo);
            Path second=writeIntent(root,SECOND,"othera","otherb",accountRepo);
            byte[] firstBytes=Files.readAllBytes(first);
            byte[] secondBytes=Files.readAllBytes(second);
            byte[] savedA=Files.readAllBytes(
                accounts.resolve("opensrc.properties"));
            byte[] savedB=Files.readAllBytes(
                accounts.resolve("src.properties"));

            // A missing registry cannot hide already-created PREPARED
            // manifest files; initial registration must be complete.
            status(root,"outsider",R59TradePairIntentIndex.Status
                .BLOCK_INDETERMINATE_INDEX);
            expectIOException(()->R59TradePairIntentIndex.publishRevision(
                root,0L,Arrays.asList(FIRST)),
                "unindexed pending intent");
            require(!Files.exists(root.resolve(
                R59TradePairIntentIndex.INDEX_FILE)),
                "incomplete initial index was published");

            List<UUID> both=Arrays.asList(FIRST,SECOND);
            long revision=R59TradePairIntentIndex.publishRevision(
                root,0L,both);
            require(revision==1L,"unexpected first revision");

            verifyPending(root,"opensrc",FIRST,1);
            verifyPending(root,"src",FIRST,1);
            verifyPending(root,"othera",SECOND,1);
            verifyPending(root,"otherb",SECOND,1);
            R59TradePairIntentIndex.Result clear=status(
                root,"outsider",R59TradePairIntentIndex.Status
                    .NO_INDEXED_PENDING_PAIR
            );
            require(clear.revision==1L &&
                    clear.canProceedToOtherLoginChecks() &&
                    !clear.loginAuthorized() &&
                    !clear.automaticRecoveryAttempted(),
                "clear index incorrectly authorized gameplay login");

            expectIOException(()->R59TradePairIntentIndex.publishRevision(
                root,0L,both),"stale revision");
            long secondRevision=R59TradePairIntentIndex.publishRevision(
                root,1L,both);
            require(secondRevision==2L,"nonmonotonic index revision");
            expectIOException(()->R59TradePairIntentIndex.publishRevision(
                root,2L,Arrays.asList(FIRST)),
                "unresolved intent removed");
            verifyPending(root,"src",FIRST,2);

            // A new durable manifest published BEFORE index registration
            // must immediately invalidate ALL offline lookups.
            Path third=writeIntent(root,THIRD,"third_a","third_b",
                accountRepo);
            status(root,"src",R59TradePairIntentIndex.Status
                .BLOCK_INDETERMINATE_INDEX);
            status(root,"outsider",R59TradePairIntentIndex.Status
                .BLOCK_INDETERMINATE_INDEX);
            expectIOException(()->R59TradePairIntentIndex.publishRevision(
                root,2L,Arrays.asList(FIRST,SECOND)),
                "unindexed pending intent");
            long thirdRevision=R59TradePairIntentIndex.publishRevision(
                root,2L,Arrays.asList(FIRST,SECOND,THIRD));
            require(thirdRevision==3L,"new registration failed");
            verifyPending(root,"third_a",THIRD,3);
            verifyPending(root,"third_b",THIRD,3);
            verifyPending(root,"opensrc",FIRST,3);
            require(status(root,"outsider",R59TradePairIntentIndex.Status
                    .NO_INDEXED_PENDING_PAIR).revision==3L,
                "indexed outsider did not clear");

            Path orphanStage=root.resolve(
                ".trade-r57-"+FIRST+".properties.r16-test.stage");
            byte[] orphanEvidence=new byte[]{7,11,17};
            Files.write(orphanStage,orphanEvidence);
            status(root,"outsider",R59TradePairIntentIndex.Status
                .BLOCK_INDETERMINATE_INDEX);
            require(Arrays.equals(
                Files.readAllBytes(orphanStage),orphanEvidence),
                "preflight mutated orphan journal stage");
            Files.delete(orphanStage); // test-owned only

            Path index=root.resolve(R59TradePairIntentIndex.INDEX_FILE);
            Path indexStage=root.resolve(
                "."+R59TradePairIntentIndex.INDEX_FILE+
                ".r16-unpublished.stage");
            Files.write(indexStage,new byte[]{4,5,6});
            status(root,"opensrc",R59TradePairIntentIndex.Status
                .BLOCK_INDETERMINATE_INDEX);
            require(Files.size(indexStage)==3L,"index stage mutated");
            Files.delete(indexStage);

            byte[] indexedBytes=Files.readAllBytes(index);
            Files.write(root.resolve("unknown.txt"),new byte[]{1});
            status(root,"src",R59TradePairIntentIndex.Status
                .BLOCK_INDETERMINATE_INDEX);
            Files.delete(root.resolve("unknown.txt"));

            byte[] thirdBytes=Files.readAllBytes(third);
            Properties tampered=new Properties();
            try(InputStream input=Files.newInputStream(third)){
                tampered.load(input);
            }
            tampered.setProperty("participant.a","foreign");
            try(OutputStream output=Files.newOutputStream(third)){
                tampered.store(output,"test-owned tamper");
            }
            status(root,"outsider",R59TradePairIntentIndex.Status
                .BLOCK_INDETERMINATE_INDEX);
            Files.write(third,thirdBytes); // only test-owned file
            require(Arrays.equals(indexedBytes,Files.readAllBytes(index)),
                "read-only inspection modified index");

            Properties corruptIndex=new Properties();
            try(InputStream input=Files.newInputStream(index)){
                corruptIndex.load(input);
            }
            corruptIndex.setProperty("entry.0.id","broken-id");
            try(OutputStream output=Files.newOutputStream(index)){
                corruptIndex.store(output,"test-owned malformed index");
            }
            status(root,"src",R59TradePairIntentIndex.Status
                .BLOCK_INDETERMINATE_INDEX);
            Files.write(index,indexedBytes); // only test-owned index

            Files.delete(second);
            status(root,"opensrc",R59TradePairIntentIndex.Status
                .BLOCK_INDETERMINATE_INDEX);
            Files.write(second,secondBytes);
            verifyPending(root,"otherb",SECOND,3);

            require(Arrays.equals(firstBytes,Files.readAllBytes(first)) &&
                    Arrays.equals(secondBytes,Files.readAllBytes(second)) &&
                    Arrays.equals(savedA,Files.readAllBytes(
                        accounts.resolve("opensrc.properties"))) &&
                    Arrays.equals(savedB,Files.readAllBytes(
                        accounts.resolve("src.properties"))),
                "account or journal bytes changed by index checks");

            System.out.println(
                "R59_OFFLINE_PAIR_INDEX_REVISION_FENCE_PASS "+
                "bothParticipantsDiscoverable=true "+
                "missingAndUnindexedManifestFailClosed=true "+
                "staleRevisionAndIntentRemovalRejected=true "+
                "journalAndIndexStagesPreserved=true "+
                "corruptMissingOrUnknownEntriesFailClosed=true "+
                "accountSnapshotsUntouched=true "+
                "noLiveLoginTradeSaveHooks=true "+
                "crossProcessCASNotProven=true "+
                "powerLossAtomicityNotProven=true");
        }finally{
            try(java.util.stream.Stream<Path> entries=Files.walk(scratch)){
                entries.sorted(Comparator.reverseOrder()).forEach(path->{
                    try{Files.deleteIfExists(path);}
                    catch(IOException error){
                        throw new IllegalStateException(error);
                    }
                });
            }
        }
    }

    private static Path writeIntent(
        Path root,UUID transaction,String a,String b,
        FilePlayerRepository accountRepo
    )throws Exception{
        WorldPlayer playerA=new WorldPlayer();
        WorldPlayer playerB=new WorldPlayer();
        PlayerSnapshot beforeA=PlayerSnapshotCodec.capture(a,playerA);
        PlayerSnapshot beforeB=PlayerSnapshotCodec.capture(b,playerB);
        accountRepo.save(beforeA);
        accountRepo.save(beforeB);
        ServerPacketWriter writer=new ServerPacketWriter(
            new OutboundPacketQueue(),new IsaacCipher(new int[]{1,2,3,4})
        );
        playerA.bank().spawnItem(995,1,writer);
        playerB.bank().spawnItem(385,1,writer);
        PlayerSnapshot afterA=PlayerSnapshotCodec.capture(a,playerA);
        PlayerSnapshot afterB=PlayerSnapshotCodec.capture(b,playerB);
        Path file=root.resolve(
            "trade-r57-"+transaction+".properties");
        R57TradePairJournal.writePrepared(
            file,transaction,beforeA,afterA,beforeB,afterB
        );
        return file;
    }

    private static void verifyPending(
        Path root,String username,UUID transaction,long expectedRevision
    ){
        R59TradePairIntentIndex.Result actual=status(
            root,username,R59TradePairIntentIndex.Status.BLOCK_INDEXED_PAIR
        );
        require(actual.revision==expectedRevision &&
                actual.transactions.size()==1 &&
                actual.transactions.get(0).equals(transaction) &&
                !actual.canProceedToOtherLoginChecks() &&
                !actual.loginAuthorized(),
            "indexed account not blocked as expected: "+username);
    }

    private static R59TradePairIntentIndex.Result status(
        Path root,String username,R59TradePairIntentIndex.Status wanted
    ){
        R59TradePairIntentIndex.Result result=
            R59TradePairIntentIndex.inspect(root,username);
        require(result.status==wanted,
            "user="+username+" expected="+wanted+
            " got="+result.status+" reason="+result.reason);
        return result;
    }

    @FunctionalInterface interface CheckedAction {void run()throws Exception;}
    private static void expectIOException(CheckedAction action,String marker)
            throws Exception{
        try{
            action.run();
            throw new AssertionError("expected refusal "+marker);
        }catch(IOException expected){
            require(expected.getMessage().contains(marker),
                "wrong refusal "+expected+" expected="+marker);
        }
    }
    private static void require(boolean truth,String explanation){
        if(!truth)throw new AssertionError(explanation);
    }
    private R59TradePairIntentIndexTest(){}
}
