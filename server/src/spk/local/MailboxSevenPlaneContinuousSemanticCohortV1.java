package spk.local;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * G21.134: four G21.120-123 REAL semantic inspectors are observed
 * inside SAME continuously held G21.100 OS advisory lease as the
 * physical G21.129-131 seven planes. Then do pure in-memory G21.132
 * UUID and G21.133 v3 content checks on those exact two samples.
 *
 * This is NEGATIVE-ONLY, not an atomic epoch, proof of raw writer
 * exclusion, cryptographic writer provenance or a grant/cleanup token.
 */
final class MailboxSevenPlaneContinuousSemanticCohortV1 {
    static final class Bound {
        final String prepared,terminal;
        private Bound(String p,String t){
            prepared=p;terminal=t;
        }
    }
    static final class Report {
        final MailboxSevenPlaneContinuousPhysicalWitnessV1.Report physical;
        final Bound before,after;
        final boolean originalFourSemanticInspectorsInsideSameLease=true;
        final boolean strictUuidAndPayloadChecksOnCapturedSnapshots=true;
        final boolean semanticHashesMatchV3Payloads=true;
        final boolean externallyAuthenticatedWriterProvenance=false;
        final boolean rawOrDefaultWriterExclusionProved=false;
        final boolean historicalAndFutureWritersFenced=false;
        final boolean independentPinAuthenticationProved=false;
        final boolean atomicCrossFileEpochProved=false;
        final boolean hardwarePowerLossProved=false;
        final boolean rolloverAuthorized=false;
        final boolean pruningAuthorized=false;
        final boolean cleanupAuthorized=false;
        final boolean unlinkAuthorized=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean sessionAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;
        private Report(
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report p,
            Bound a,Bound b
        ){
            physical=p;before=a;after=b;
        }
    }
    private static IOException veto(String detail){
        return new IOException(
            "G21.134 CONTINUOUS_SEMANTIC_COHORT_UNSAFE_NO_GRANT "+
            detail+" epoch=false cleanup=false grant=false");
    }
    private static boolean equal(String a,String b){
        return a!=null&&a.equals(b);
    }
    private static Bound inspectInside(
        Path file,String account,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation observed
    )throws IOException{
        // All four original inspectors have already been tested by
        // G21.124 inside G21.100. The inner account publication
        // readers acquire only the established G21.39 account lock.
        // Do not invoke G21.124.comparePinnedFourBoundResults here:
        // its outer G21.100 FileLock would overlap the held lease.
        MailboxStrictPreparedResultBindingV3.Report strict=
            MailboxStrictPreparedResultBindingV3
                .inspectInside(file,account);
        MailboxPreparedJournalResultBindingV3.Report journal=
            MailboxPreparedJournalResultBindingV3
                .inspectInside(file,account);
        MailboxWorldTerminalResultBindingV3.Report world=
            MailboxWorldTerminalResultBindingV3
                .inspectInside(file,account);
        MailboxCommitResultBindingV3.Report commit=
            MailboxCommitResultBindingV3
                .inspectInside(file,account);
        if(strict.status!=MailboxStrictPreparedResultBindingV3.Status
                .BOUND_HISTORICAL_PREPARED_WITH_TERMINAL_JOURNAL_NO_AUTHORITY||
           journal.status!=MailboxPreparedJournalResultBindingV3.Status
                .BOUND_PREPARED_JOURNAL_MATCH_NO_AUTHORITY||
           world.status!=MailboxWorldTerminalResultBindingV3.Status
                .BOUND_REAL_TERMINAL_DISK_MATCH_NO_AUTHORITY||
           commit.status!=MailboxCommitResultBindingV3.Status
                .BOUND_COMMIT_DISK_MATCH_NO_AUTHORITY||
           strict.boundAttempts!=1||journal.boundAttempts!=1||
           world.boundAttempts!=1||commit.boundAttempts!=1)
            throw veto("FOUR_REAL_RESULT_READERS_NOT_ALL_BOUND");
        String prepared=strict.preparedSha256;
        String terminal=world.terminalSha256;
        if(!equal(prepared,journal.preparedSha256)||
           !equal(prepared,world.preparedSha256)||
           !equal(terminal,journal.terminalSha256)||
           !equal(terminal,commit.terminalSha256)||
           !equal(world.terminalFileBytesSha256,observed.accountSha)||
           !equal(journal.journalBytesSha256,observed.journalSha)||
           !equal(commit.commitBytesSha256,observed.commitSha)||
           strict.originalPreparedFileStillPresent)
            throw veto("FOUR_REAL_SEMANTIC_AND_PHYSICAL_MISMATCH");
        // G21.122 historical strict raw PREPARED content is no
        // longer physically present after World terminal update.
        return new Bound(prepared,terminal);
    }
    static Report comparePinnedContinuousSemantic(
        FilePlayerRepository.PathResolver resolver,String account,
        String pinnedV3,String v1Portable,String pinnedV1,
        String pinnedAccount,String pinnedJournal,String pinnedCommit,
        String pinnedV2,long acquisitionMillis,
        MailboxWriterArchiveLiveReconciliationV1.BetweenPasses between
    )throws IOException{
        Objects.requireNonNull(resolver,"resolver");
        Objects.requireNonNull(between,"between");
        Bound[] captured=new Bound[2];
        int[] visits={0};
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report physical=
            MailboxSevenPlaneContinuousPhysicalWitnessV1
                .compareWithContinuousLease(
                    resolver,account,pinnedV3,v1Portable,pinnedV1,
                    pinnedAccount,pinnedJournal,pinnedCommit,pinnedV2,
                    acquisitionMillis,between,
                    (selected,name,observation)->{
                        if(visits[0]>=captured.length)
                            throw veto("UNEXPECTED_INSIDE_LEASE_VISIT");
                        captured[visits[0]++]=
                            inspectInside(selected,name,observation);
                    });
        if(visits[0]!=2||captured[0]==null||captured[1]==null)
            throw veto("NOT_TWO_INSIDE_LEASE_SEMANTIC_CAPTURES");
        // Both validations consume EXACT observed in-memory data;
        // neither re-acquires G21.100 or rereads mutable disk files.
        MailboxSevenPlaneImmutableUuidCohortV1
            .requireCapturedUuidCohort(account,physical);
        MailboxSevenPlaneV3PayloadCohortV1
            .requireCapturedFourPayloads(account,physical);
        String[] first=MailboxSevenPlaneV3PayloadCohortV1
            .capturedCanonicalHashes(account,physical.before);
        String[] second=MailboxSevenPlaneV3PayloadCohortV1
            .capturedCanonicalHashes(account,physical.after);
        if(!equal(captured[0].prepared,captured[1].prepared)||
           !equal(captured[0].terminal,captured[1].terminal)||
           !equal(captured[0].prepared,first[0])||
           !equal(captured[0].terminal,first[1])||
           !equal(captured[1].prepared,second[0])||
           !equal(captured[1].terminal,second[1]))
            throw veto("CAPTURED_SEMANTIC_HASHES_NOT_V3_PAYLOADS");
        return new Report(physical,captured[0],captured[1]);
    }
    private MailboxSevenPlaneContinuousSemanticCohortV1(){}
}
