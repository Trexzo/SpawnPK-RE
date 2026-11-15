package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;

/** Independent, read-only R58 participant preflight with test-owned data. */
public final class R58TradePairLoginPreflightTest {
    private static final UUID TX=UUID.fromString(
        "11111111-2222-4333-8444-555555555555"
    );
    private static final UUID OTHER_TX=UUID.fromString(
        "77777777-8888-4999-aaaa-bbbbbbbbbbbb"
    );

    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("spk-r58-login-journal-");
        try{
            Path journal=root.resolve("journal");
            Path profiles=root.resolve("accounts");
            Files.createDirectory(journal);
            Files.createDirectory(profiles);

            checkStatus(root.resolve("missing"),"opensrc",
                R58TradePairLoginPreflight.Status.BLOCK_INDETERMINATE_JOURNAL);
            checkStatus(journal,"../opensrc",
                R58TradePairLoginPreflight.Status.BLOCK_INDETERMINATE_JOURNAL);
            checkStatus(journal,"opensrc",
                R58TradePairLoginPreflight.Status.NO_PENDING_PAIR_EVIDENCE);
            checkStatus(journal,"src",
                R58TradePairLoginPreflight.Status.NO_PENDING_PAIR_EVIDENCE);

            WorldPlayer a=new WorldPlayer(),b=new WorldPlayer();
            ServerPacketWriter writer=new ServerPacketWriter(
                new OutboundPacketQueue(),
                new IsaacCipher(new int[]{1,2,3,4})
            );
            PlayerSnapshot beforeA=PlayerSnapshotCodec.capture("opensrc",a);
            PlayerSnapshot beforeB=PlayerSnapshotCodec.capture("src",b);
            a.bank().spawnItem(995,1000,writer);
            b.bank().spawnItem(385,2,writer);
            PlayerSnapshot afterA=PlayerSnapshotCodec.capture("opensrc",a);
            PlayerSnapshot afterB=PlayerSnapshotCodec.capture("src",b);

            FilePlayerRepository accountRepo=new FilePlayerRepository(
                username->profiles.resolve(username+".properties")
            );
            accountRepo.save(beforeA);
            accountRepo.save(beforeB);
            byte[] savedA=Files.readAllBytes(
                profiles.resolve("opensrc.properties"));
            byte[] savedB=Files.readAllBytes(
                profiles.resolve("src.properties"));

            Path intent=journal.resolve(name(TX));
            R57TradePairJournal.writePrepared(
                intent,TX,beforeA,afterA,beforeB,afterB
            );
            byte[] initialIntent=Files.readAllBytes(intent);

            R58TradePairLoginPreflight.Result aStatus=checkStatus(
                journal,"opensrc",
                R58TradePairLoginPreflight.Status.BLOCK_PENDING_PAIR_INTENT
            );
            R58TradePairLoginPreflight.Result bStatus=checkStatus(
                journal,"src",
                R58TradePairLoginPreflight.Status.BLOCK_PENDING_PAIR_INTENT
            );
            require(aStatus.firstMatchingTransaction.equals(TX) &&
                    bStatus.firstMatchingTransaction.equals(TX) &&
                    aStatus.matchingIntents==1&&bStatus.matchingIntents==1,
                "pair not discoverable from BOTH participant logins");
            R58TradePairLoginPreflight.Result unrelated=checkStatus(
                journal,"outsider",
                R58TradePairLoginPreflight.Status.NO_PENDING_PAIR_EVIDENCE
            );
            require(unrelated.canProceedToOtherLoginChecks(),
                "unrelated player unexpectedly blocked on valid journal");
            require(!aStatus.canProceedToOtherLoginChecks() &&
                    !bStatus.canProceedToOtherLoginChecks(),
                "unresolved journal admitted a participant");
            require(aStatus.examinedEntries==1&&bStatus.examinedEntries==1,
                "unexpected scan size");

            // No checkpoint, autosave, logout or account file may be read
            // or changed by this standalone journal preflight.
            R58TradePairLoginPreflight.Result duplicate=checkStatus(
                journal,"opensrc",
                R58TradePairLoginPreflight.Status.BLOCK_PENDING_PAIR_INTENT
            );
            require(duplicate.firstMatchingTransaction.equals(TX),
                "idempotent scan returned a different transaction");

            // A staging path can indicate an active publisher or crash.
            // Never interpret its age as abandonment or clean it up.
            Path orphanStage=journal.resolve(
                "."+name(TX)+".r16-testfixture.stage"
            );
            byte[] evidence=new byte[]{3,9,12};
            Files.write(orphanStage,evidence);
            checkStatus(journal,"opensrc",
                R58TradePairLoginPreflight.Status.BLOCK_INDETERMINATE_JOURNAL);
            checkStatus(journal,"outsider",
                R58TradePairLoginPreflight.Status.BLOCK_INDETERMINATE_JOURNAL);
            require(Arrays.equals(Files.readAllBytes(orphanStage),evidence),
                "orphan stage modified");
            Files.delete(orphanStage); // created and owned by this test

            // A missing canonical plus residual stage is equally ambiguous.
            Path missingStage=journal.resolve(
                "."+name(OTHER_TX)+".r16-interrupted.stage"
            );
            Files.write(missingStage,new byte[]{31,5});
            checkStatus(journal,"src",
                R58TradePairLoginPreflight.Status.BLOCK_INDETERMINATE_JOURNAL);
            require(Files.size(missingStage)==2,
                "missing canonical stage removed or opened");
            Files.delete(missingStage);

            // Valid-looking filename containing a foreign transaction ID:
            // block globally, not just the claimed participants.
            Path swapped=journal.resolve(name(OTHER_TX));
            Files.copy(intent,swapped);
            R58TradePairLoginPreflight.Result mismatched=checkStatus(
                journal,"outsider",
                R58TradePairLoginPreflight.Status.BLOCK_INDETERMINATE_JOURNAL
            );
            require(mismatched.reasonCode.equals(
                "TRANSACTION_ID_FILENAME_MISMATCH"),
                "mismatch reason wrong: "+mismatched.reasonCode);
            Files.delete(swapped);

            // Malformed candidate and unknown directory entries also fail
            // closed rather than silently skipping a pending pair.
            Path invalid=journal.resolve(name(OTHER_TX));
            byte[] garbage="test-only malformed".getBytes(
                java.nio.charset.StandardCharsets.US_ASCII);
            Files.write(invalid,garbage);
            checkStatus(journal,"outsider",
                R58TradePairLoginPreflight.Status.BLOCK_INDETERMINATE_JOURNAL);
            require(Arrays.equals(Files.readAllBytes(invalid),garbage),
                "invalid journal was changed");
            Files.delete(invalid);
            Path unexpected=journal.resolve("README.txt");
            Files.write(unexpected,new byte[]{1});
            checkStatus(journal,"src",
                R58TradePairLoginPreflight.Status.BLOCK_INDETERMINATE_JOURNAL);
            Files.delete(unexpected);

            // Another real PREPARED manifest must be indexed independently.
            WorldPlayer c=new WorldPlayer(),d=new WorldPlayer();
            PlayerSnapshot cBefore=PlayerSnapshotCodec.capture("othera",c);
            PlayerSnapshot dBefore=PlayerSnapshotCodec.capture("otherb",d);
            c.bank().spawnItem(995,1,writer);
            d.bank().spawnItem(385,1,writer);
            R57TradePairJournal.writePrepared(
                journal.resolve(name(OTHER_TX)),OTHER_TX,
                cBefore,PlayerSnapshotCodec.capture("othera",c),
                dBefore,PlayerSnapshotCodec.capture("otherb",d)
            );
            checkStatus(journal,"othera",
                R58TradePairLoginPreflight.Status.BLOCK_PENDING_PAIR_INTENT);
            checkStatus(journal,"otherb",
                R58TradePairLoginPreflight.Status.BLOCK_PENDING_PAIR_INTENT);
            checkStatus(journal,"opensrc",
                R58TradePairLoginPreflight.Status.BLOCK_PENDING_PAIR_INTENT);
            R58TradePairLoginPreflight.Result clean=checkStatus(
                journal,"outsider",
                R58TradePairLoginPreflight.Status.NO_PENDING_PAIR_EVIDENCE
            );
            require(clean.examinedEntries==2,
                "second intent lost during directory scan");

            try{
                Path alias=root.resolve("symlinked-journal");
                Files.createSymbolicLink(alias,journal);
                checkStatus(alias,"src",
                    R58TradePairLoginPreflight.Status.BLOCK_INDETERMINATE_JOURNAL);
            }catch(UnsupportedOperationException|IOException|
                  SecurityException noSymlinkPrivilege){
                // Windows CI often cannot create symlinks. No grant requested.
            }

            require(Arrays.equals(savedA,Files.readAllBytes(
                    profiles.resolve("opensrc.properties"))) &&
                    Arrays.equals(savedB,Files.readAllBytes(
                    profiles.resolve("src.properties"))),
                "preflight wrote or replaced original account profiles");
            require(Arrays.equals(initialIntent,Files.readAllBytes(intent)),
                "preflight wrote or rewrote an immutable intent");
            require(!aStatus.automaticRecoveryAttempted() &&
                    !aStatus.accountSnapshotsOpened(),
                "preflight attempted account read or recovery");

            System.out.println(
                "R58_READ_ONLY_PAIR_LOGIN_PREFLIGHT_PASS "+
                "bothParticipantLoginsBlocked=true "+
                "unrelatedAccountClearOnlyWhenDirectoryValidated=true "+
                "orphanAndActiveStageFailClosed=true "+
                "malformedUnknownAndIDMismatchFailClosed=true "+
                "repeatedReviewIdempotent=true "+
                "accountAndJournalBytesPreserved=true "+
                "noActualLoginHookOrAutoRecovery=true");
        }finally{
            try(java.util.stream.Stream<Path> entries=Files.walk(root)){
                entries.sorted(Comparator.reverseOrder()).forEach(p->{
                    try{Files.deleteIfExists(p);}
                    catch(IOException failure){
                        throw new IllegalStateException(failure);
                    }
                });
            }
        }
    }

    private static String name(UUID tx){
        return "trade-r57-"+tx.toString()+".properties";
    }

    private static R58TradePairLoginPreflight.Result checkStatus(
        Path root,String user,R58TradePairLoginPreflight.Status expected
    ){
        R58TradePairLoginPreflight.Result result=
            R58TradePairLoginPreflight.inspect(root,user);
        require(result.status==expected,
            "account="+user+" expected="+expected+" got="+
            result.status+" reason="+result.reasonCode);
        return result;
    }

    private static void require(boolean valid,String error){
        if(!valid)throw new AssertionError(error);
    }

    private R58TradePairLoginPreflightTest(){}
}
