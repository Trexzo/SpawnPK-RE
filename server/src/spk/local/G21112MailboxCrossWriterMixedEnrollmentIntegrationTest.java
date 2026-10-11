package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * G21.112: REAL cross-writer enrollment gaps on one account, not a new
 * cleanup, archival, raw-writer fencing, or live settlement protocol.
 */
public final class G21112MailboxCrossWriterMixedEnrollmentIntegrationTest {
    private static final String OPTIN="g21112-optin";
    private static final String MIXED="g21112-mixed";
    private static final String PARTIAL="g21112-partial";
    private static final String FULL="g21112-full";

    private static void check(boolean b,String s){
        if(!b)throw new AssertionError("G21.112 "+s);
    }
    private static FilePlayerRepository.PathResolver paths(Path dir){
        return account->dir.resolve(account+".properties");
    }
    private static final class Seed {
        final WorldPlayer owner;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        final PlayerSnapshot terminal;
        Seed(WorldPlayer w,MailboxSettlementPostimagePlanner.Proposal p){
            owner=w;proposal=p;
            terminal=MailboxAtomicTerminalSnapshot.compose(p);
        }
    }
    private static Seed seed(World world,String account){
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,account);
        String id=account+":gift";
        player.mailbox().deliver(new RewardDeliveryMessage(
            id,"G21.112 mixed writer coverage","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G21112_TEST_ONLY"));
        MailboxRewardDeliveryService.Snapshot current=
            player.mailbox().get(id);
        MailboxPreparedClaimJournal.stageOnly(player,
            MailboxPreparedClaimJournal.prepare(player,current));
        return new Seed(player,MailboxSettlementPostimagePlanner.plan(
            player,generation,current));
    }
    private static StrictDurablePlayerSnapshotWriter.Receipt terminal(
        Seed s,FilePlayerRepository.PathResolver resolver,
        StrictDurablePlayerSnapshotWriter strict
    )throws IOException{
        return strict.saveStrictTerminalForWorld(
            s.terminal,resolver.resolve(s.proposal.account),
            StrictDurablePlayerSnapshotWriter.canonicalSnapshotSha256(
                s.proposal.preparedPreimage),()->{},()->{});
    }
    private static MailboxWriterAttemptEvidenceV1.Report report(
        FilePlayerRepository.PathResolver r,String a
    )throws IOException{
        MailboxWriterAttemptEvidenceV1.Report v=
            MailboxWriterAttemptEvidenceV1.inspect(r,a);
        check(!v.completeWriterPopulationKnown&&
            !v.everyHistoricalGenerationParticipates&&
            !v.crashEpochSealed&&!v.uncooperativeWritersExcluded&&
            !v.futureWritersExcluded&&!v.historicalEvidencePruningAllowed&&
            !v.unlinkAuthorized&&!v.cleanupAuthorized&&
            !v.accountMutationAuthorized&&!v.grantAuthorized&&
            !v.replayAuthorized&&!v.restartAdmissionAuthorized&&
            !v.releaseAuthorized&&!v.clientAckAuthorized,
            "v1 report transferred destructive/settlement authority");
        return v;
    }
    private static void noAuthority(
        MailboxGuardedDiskCommitRecord.Observation c
    ){
        check(!c.transactionCommitted&&!c.liveApplied&&
            !c.grantAuthorized&&!c.replayAuthorized&&
            !c.rollbackAuthorized&&!c.restartAdmissionAuthorized&&
            !c.releaseAuthorized&&!c.clientAckAuthorized,
            "COMMIT evidence transferred reward authority");
    }
    private static Path[] records(Path dir,String a)throws IOException{
        try(Stream<Path> list=Files.list(dir)){
            String prefix=a+".properties.g21103-writer-attempt-";
            return list.filter(p->p.getFileName().toString()
                .startsWith(prefix))
                .sorted(Comparator.comparing(Path::toString))
                .toArray(Path[]::new);
        }
    }
    private static String[] fingerprint(Path dir,String a)
        throws IOException{
        Path[] leaves=records(dir,a);
        String[] contents=new String[leaves.length];
        for(int i=0;i<leaves.length;i++)
            contents[i]=leaves[i].getFileName()+"|"+
                java.util.Base64.getEncoder().encodeToString(
                    Files.readAllBytes(leaves[i]));
        return contents;
    }
    private static String sha256(byte[] bytes)throws Exception{
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex=new StringBuilder(64);
        for(byte b:digest)
            hex.append(String.format(Locale.ROOT,"%02x",b&255));
        return hex.toString();
    }
    @FunctionalInterface
    private interface Action { void run()throws IOException; }
    private static void rejected(Action a,String marker)throws Exception{
        boolean denied=false;
        try{a.run();}
        catch(IOException error){
            denied=String.valueOf(error.getMessage()).contains(marker);
        }
        check(denied,"required negative rejection "+marker);
    }
    private static boolean untypedRecords(Path dir,String account)
        throws IOException{
        Path[] leaves=records(dir,account);
        if(leaves.length==0)return false;
        for(Path path:leaves){
            String body=new String(Files.readAllBytes(path),
                StandardCharsets.US_ASCII);
            if(!body.contains("SPK-MAILBOX-WRITER-ATTEMPT-V1")||
               body.contains("KIND=")||body.contains("WRITER_TYPE="))
                return false;
        }
        return true;
    }
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21112-cross-writer-");
        boolean threePaired=false,terminalUnrecorded=false;
        boolean mixedDefaultsInvisible=false,crossKindCrashVeto=false;
        boolean incompleteDefaultCanPublish=false,shared32Limit=false;
        boolean lateLegacyChangesCanonical=false;
        boolean pinnedContentCatchesLateLegacy=false;
        boolean evidenceUntyped=false,noLiveGrant=false,noLeases=false;
        try(World world=World.isolatedForTest(60000L)){
            FilePlayerRepository.PathResolver r=paths(root);
            StrictDurablePlayerSnapshotWriter strict=
                new StrictDurablePlayerSnapshotWriter(r);
            MailboxDurableIdempotencyIntentJournal journal=
                new MailboxDurableIdempotencyIntentJournal(r);
            MailboxGuardedDiskCommitRecord commit=
                new MailboxGuardedDiskCommitRecord(r);

            // A full REAL G21.109 -> G21.110 -> G21.66 -> G21.111
            // chain has SIX leaves, but terminal strict is unrecorded.
            Seed s=seed(world,OPTIN);
            strict.saveStrictWithWriterEvidence(s.proposal.preparedPreimage);
            int afterStrict=report(r,OPTIN).recordCount;
            journal.publishPreparedIntentWithWriterEvidence(s.proposal);
            int afterJournal=report(r,OPTIN).recordCount;
            StrictDurablePlayerSnapshotWriter.Receipt receipt=
                terminal(s,r,strict);
            int afterTerminal=report(r,OPTIN).recordCount;
            commit.recordConfirmedDiskTerminalWithWriterEvidence(
                s.proposal,receipt);
            MailboxWriterAttemptEvidenceV1.Report all=report(r,OPTIN);
            MailboxGuardedDiskCommitRecord.Observation matched=
                commit.inspect(OPTIN);
            noAuthority(matched);
            threePaired=afterStrict==2&&afterJournal==4&&
                all.recordCount==6&&all.started==3&&all.completed==3&&
                all.status==MailboxWriterAttemptEvidenceV1.Status
                    .ALL_OBSERVED_ATTEMPTS_PAIRED_NO_CLEANUP&&
                matched.status==MailboxGuardedDiskCommitRecord.Status
                    .DISK_COMMIT_MATCH_NO_LIVE_APPLY;
            terminalUnrecorded=afterTerminal==afterJournal&&
                receipt.matchesSnapshot(s.terminal);
            evidenceUntyped=untypedRecords(root,OPTIN);

            // A matched archive/pinned current three-leaf witness is
            // valid at this instant, but this does NOT authenticate its
            // provenance or fence any future legacy writer.
            MailboxWriterEvidencePortableArchiveV1.Snapshot archive=
                MailboxWriterEvidencePortableArchiveV1.capture(r,OPTIN);
            String canonical=sha256(Files.readAllBytes(r.resolve(OPTIN)));
            String pinnedJournal=sha256(Files.readAllBytes(
                journal.journalPath(OPTIN)));
            String pinnedCommit=sha256(Files.readAllBytes(
                commit.recordPath(OPTIN)));
            MailboxWriterArchiveLiveReconciliationV1.Report good=
                MailboxWriterArchiveLiveReconciliationV1
                    .comparePinnedRecoveryLeaves(
                        r,OPTIN,archive.portable,archive.sha256,canonical,
                        pinnedJournal,pinnedCommit);
            check(good.pinnedCanonicalBytesMatched&&
                good.pinnedRecoverySidecarBytesMatched&&
                !good.allWriterVersionsFenced&&!good.rolloverAuthorized,
                "verified snapshot minted rollover authority");
            String[] originalV1=fingerprint(root,OPTIN);
            byte[] terminalBefore=Files.readAllBytes(r.resolve(OPTIN));
            // Ordinary non-World strict remains unrecorded; G21.88
            // only blocks guarded World writes after COMMIT.
            strict.saveStrict(s.proposal.preparedPreimage);
            MailboxGuardedDiskCommitRecord.Observation diverged=
                commit.inspect(OPTIN);
            noAuthority(diverged);
            lateLegacyChangesCanonical=
                !Arrays.equals(terminalBefore,
                    Files.readAllBytes(r.resolve(OPTIN)))&&
                Arrays.equals(originalV1,fingerprint(root,OPTIN))&&
                report(r,OPTIN).recordCount==6&&
                diverged.status==MailboxGuardedDiskCommitRecord.Status
                    .PREPARED_NOT_COMMITTED;
            rejected(()->MailboxWriterArchiveLiveReconciliationV1
                .comparePinnedRecoveryLeaves(
                    r,OPTIN,archive.portable,archive.sha256,canonical,
                    pinnedJournal,pinnedCommit),
                "G21.107 PINNED_CANONICAL_CONTENT_MISMATCH");
            pinnedContentCatchesLateLegacy=true;

            // A v1 ledger can be FULLY paired while three genuinely
            // performed same-account stages never entered it.
            Seed m=seed(world,MIXED);
            strict.saveStrictWithWriterEvidence(m.proposal.preparedPreimage);
            journal.publishPreparedIntent(m.proposal);
            int afterDefaultJournal=report(r,MIXED).recordCount;
            StrictDurablePlayerSnapshotWriter.Receipt mixedReceipt=
                terminal(m,r,strict);
            int afterDefaultTerminal=report(r,MIXED).recordCount;
            commit.recordConfirmedDiskTerminal(m.proposal,mixedReceipt);
            MailboxWriterAttemptEvidenceV1.Report mixed=report(r,MIXED);
            MailboxGuardedDiskCommitRecord.Observation mixedState=
                commit.inspect(MIXED);
            noAuthority(mixedState);
            mixedDefaultsInvisible=afterDefaultJournal==2&&
                afterDefaultTerminal==2&&mixed.recordCount==2&&
                mixed.started==1&&mixed.completed==1&&
                mixed.status==MailboxWriterAttemptEvidenceV1.Status
                    .ALL_OBSERVED_ATTEMPTS_PAIRED_NO_CLEANUP&&
                mixedState.status==MailboxGuardedDiskCommitRecord.Status
                    .DISK_COMMIT_MATCH_NO_LIVE_APPLY;

            // An uncertain REAL journal operation blocks both other
            // kinds of future OPT-IN writer before their callback.
            // Default terminal and COMMIT can STILL proceed, proving
            // there is no version-wide writer enrollment.
            Seed uncertain=seed(world,PARTIAL);
            strict.saveStrict(uncertain.proposal.preparedPreimage);
            MailboxDurableIdempotencyIntentJournal faulted=
                new MailboxDurableIdempotencyIntentJournal(r,phase->{
                    if(phase==MailboxDurableIdempotencyIntentJournal.Phase
                            .AFTER_DIRECTORY_FORCE)
                        throw new IOException(
                            "G21112_FORCED_POSTLINK_UNCERTAINTY");
                });
            boolean postlinkUnconfirmed=false;
            try{
                faulted.publishPreparedIntentWithWriterEvidence(
                    uncertain.proposal);
            }catch(MailboxDurableIdempotencyIntentJournal
                    .UnconfirmedJournalException expected){
                postlinkUnconfirmed=true;
            }
            MailboxWriterAttemptEvidenceV1.Report partial=
                report(r,PARTIAL);
            String[] partialEvidence=fingerprint(root,PARTIAL);
            byte[] beforeDeniedStrict=Files.readAllBytes(
                r.resolve(PARTIAL));
            rejected(()->strict.saveStrictWithWriterEvidence(
                uncertain.proposal.preparedPreimage),
                "G21.104 PRIOR_INCOMPLETE_WRITER_ATTEMPT_NO_ENROLL");
            crossKindCrashVeto=postlinkUnconfirmed&&partial.started==1&&
                partial.completed==0&&
                Files.exists(journal.journalPath(PARTIAL),
                    LinkOption.NOFOLLOW_LINKS)&&
                Arrays.equals(beforeDeniedStrict,Files.readAllBytes(
                    r.resolve(PARTIAL)));
            StrictDurablePlayerSnapshotWriter.Receipt partialReceipt=
                terminal(uncertain,r,strict);
            byte[] beforeDeniedCommit=Files.readAllBytes(
                r.resolve(PARTIAL));
            rejected(()->commit.recordConfirmedDiskTerminalWithWriterEvidence(
                    uncertain.proposal,partialReceipt),
                "G21.104 PRIOR_INCOMPLETE_WRITER_ATTEMPT_NO_ENROLL");
            crossKindCrashVeto&=
                Arrays.equals(beforeDeniedCommit,Files.readAllBytes(
                    r.resolve(PARTIAL)))&&
                !Files.exists(commit.recordPath(PARTIAL),
                    LinkOption.NOFOLLOW_LINKS);
            commit.recordConfirmedDiskTerminal(
                uncertain.proposal,partialReceipt);
            MailboxGuardedDiskCommitRecord.Observation defaultAfterVeto=
                commit.inspect(PARTIAL);
            noAuthority(defaultAfterVeto);
            incompleteDefaultCanPublish=
                defaultAfterVeto.status==
                    MailboxGuardedDiskCommitRecord.Status
                        .DISK_COMMIT_MATCH_NO_LIVE_APPLY&&
                Arrays.equals(partialEvidence,fingerprint(root,PARTIAL))&&
                report(r,PARTIAL).recordCount==1;

            // All opt-in kinds share the SAME 32-record/16-pair cap.
            Seed bounded=seed(world,FULL);
            for(int i=0;i<13;i++)
                MailboxWriterAttemptEvidenceV1.runRecorded(
                    r,FULL,()->Boolean.TRUE);
            strict.saveStrictWithWriterEvidence(
                bounded.proposal.preparedPreimage);
            journal.publishPreparedIntentWithWriterEvidence(
                bounded.proposal);
            int beforeTerminalFull=report(r,FULL).recordCount;
            StrictDurablePlayerSnapshotWriter.Receipt fullReceipt=
                terminal(bounded,r,strict);
            int afterTerminalFull=report(r,FULL).recordCount;
            commit.recordConfirmedDiskTerminalWithWriterEvidence(
                bounded.proposal,fullReceipt);
            MailboxWriterAttemptEvidenceV1.Report full=report(r,FULL);
            byte[] accountBefore=Files.readAllBytes(r.resolve(FULL));
            byte[] commitBefore=Files.readAllBytes(commit.recordPath(FULL));
            String[] recordsBefore=fingerprint(root,FULL);
            String limit="G21.104 WRITER_EVIDENCE_RETENTION_FULL_NO_ENROLL";
            rejected(()->strict.saveStrictWithWriterEvidence(
                bounded.proposal.preparedPreimage),limit);
            rejected(()->journal.publishPreparedIntentWithWriterEvidence(
                bounded.proposal),limit);
            rejected(()->commit.recordConfirmedDiskTerminalWithWriterEvidence(
                bounded.proposal,fullReceipt),limit);
            shared32Limit=beforeTerminalFull==30&&
                afterTerminalFull==30&&full.recordCount==32&&
                full.started==16&&full.completed==16&&
                full.remainingRecordSlots==0&&
                Arrays.equals(accountBefore,Files.readAllBytes(
                    r.resolve(FULL)))&&
                Arrays.equals(commitBefore,Files.readAllBytes(
                    commit.recordPath(FULL)))&&
                Arrays.equals(recordsBefore,fingerprint(root,FULL));

            noLiveGrant=s.owner.bank().inventorySlots()==0&&
                m.owner.bank().inventorySlots()==0&&
                uncertain.owner.bank().inventorySlots()==0&&
                bounded.owner.bank().inventorySlots()==0&&
                s.owner.mailbox().get(s.proposal.messageId)
                    .claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                m.owner.mailbox().get(m.proposal.messageId)
                    .claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            noLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0;
            check(threePaired&&terminalUnrecorded&&
                mixedDefaultsInvisible&&crossKindCrashVeto&&
                incompleteDefaultCanPublish&&shared32Limit&&
                lateLegacyChangesCanonical&&
                pinnedContentCatchesLateLegacy&&evidenceUntyped&&
                noLiveGrant&&noLeases,
                "negative-only real cross-writer gap audit failed");
            System.out.println("G21112_CROSS_WRITER_DIAGNOSTICS"+
                " threeOptInsSixLeaves="+threePaired+
                " terminalStrictSaveUnrecorded="+terminalUnrecorded+
                " defaultJournalCommitUnrecorded="+
                    mixedDefaultsInvisible+
                " v1RecordsHaveNoWriterKind="+evidenceUntyped+
                " uncertainJournalBlocksOtherOptIns="+crossKindCrashVeto+
                " defaultCommitCanBypassIncomplete="+
                    incompleteDefaultCanPublish+
                " shared32RecordRetention="+shared32Limit+
                " lateDefaultStrictMutatesWithoutV1="+
                    lateLegacyChangesCanonical+
                " priorPinnedCanonicalVetoesLegacy="+
                    pinnedContentCatchesLateLegacy+
                " noWorldReward="+noLiveGrant+" zeroJvmLeases="+noLeases);
            System.out.println("G21112_MIXED_WRITER_COVERAGE_PASS"+
                " realG2123G2183G2186=true"+
                " untypedV1=true terminalWorldStrictUnrecorded=true"+
                " defaultWritersNotEnrolled=true"+
                " historicalGenerationsCovered=false"+
                " legacyWriterFence=false rollover=false pruning=false"+
                " cleanup=false unlink=false grant=false replay=false"+
                " session=false ack=false");
        }finally{
            try(Stream<Path> all=Files.walk(root)){
                for(Path p:all.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        run();
    }
}
