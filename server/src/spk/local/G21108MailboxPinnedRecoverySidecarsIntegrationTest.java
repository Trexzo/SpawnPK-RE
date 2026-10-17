package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

/** Content witnesses only: synthetic sidecar bytes are NOT valid journal semantics. */
public final class G21108MailboxPinnedRecoverySidecarsIntegrationTest {
    private static final String ACCOUNT="g21108-account";
    private static final String NEGATIVE=
        "G21.108 PINNED_SIDECAR_CONTENT_MISMATCH_NO_AUTHORITY";

    private static void check(boolean ok,String s){
        if(!ok)throw new AssertionError("G21.108 "+s);
    }
    @FunctionalInterface
    private interface Action { void run()throws IOException; }
    private static void rejected(Action test,String reason)throws Exception{
        boolean failed=false;
        try{test.run();}
        catch(IOException expected){
            failed=String.valueOf(expected.getMessage()).contains(reason);
        }
        check(failed,"unexpected acceptance or rejection: "+reason);
    }
    private static String sha(byte[] content)throws Exception{
        byte[] bytes=MessageDigest.getInstance("SHA-256").digest(content);
        StringBuilder hex=new StringBuilder(64);
        for(byte b:bytes)
            hex.append(String.format(Locale.ROOT,"%02x",b&255));
        return hex.toString();
    }
    private static void mutate(Path file,byte[] original,FileTime mtime)
        throws Exception{
        byte[] damaged=Arrays.copyOf(original,original.length);
        damaged[0]=(byte)(damaged[0]^1);
        Files.write(file,damaged);
        Files.setLastModifiedTime(file,mtime);
    }
    private static boolean physicalStillSame(
        Path path,BasicFileAttributes prior
    )throws IOException{
        BasicFileAttributes a=Files.readAttributes(path,
            BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        return prior.fileKey().equals(a.fileKey())&&
            prior.size()==a.size()&&
            prior.lastModifiedTime().equals(a.lastModifiedTime());
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21108-sidecar-pins-");
        boolean absentPassed=false,presentPassed=false;
        boolean malformedDenied=false,wrongDigestDenied=false;
        boolean unanticipatedPresenceDenied=false;
        boolean missingPresentDenied=false,preparedTamperDenied=false;
        boolean commitTamperDenied=false,interpassDenied=false;
        boolean oversizedDenied=false,nonregularDenied=false;
        boolean inheritedStillNegative=false,exactBytesRestored=false;
        boolean zeroLeases=false,allAuthorityDenied=false;
        try{
            FilePlayerRepository.PathResolver paths=
                a->root.resolve(a+".properties");
            new StrictDurablePlayerSnapshotWriter(paths)
                .saveStrict(PlayerSnapshotCodec.capture(
                    ACCOUNT,new WorldPlayer()));
            MailboxWriterAttemptEvidenceV1.runRecorded(
                paths,ACCOUNT,()->true);
            MailboxWriterEvidencePortableArchiveV1.Snapshot archive=
                MailboxWriterEvidencePortableArchiveV1.capture(
                    paths,ACCOUNT);
            Path account=paths.resolve(ACCOUNT);
            String canonical=sha(Files.readAllBytes(account));
            Path prepared=account.resolveSibling(
                account.getFileName().toString()+
                MailboxDurableIdempotencyIntentJournal.SUFFIX);
            Path commit=account.resolveSibling(
                account.getFileName().toString()+
                MailboxGuardedDiskCommitRecord.SUFFIX);
            MailboxWriterArchiveLiveReconciliationV1.Report empty=
                MailboxWriterArchiveLiveReconciliationV1
                    .comparePinnedRecoveryLeaves(
                        paths,ACCOUNT,archive.portable,archive.sha256,
                        canonical,"ABSENT","ABSENT");
            absentPassed=empty.pinnedCanonicalBytesMatched&&
                empty.pinnedRecoverySidecarBytesMatched;
            allAuthorityDenied=!empty.cleanupAuthorized&&
                !empty.unlinkAuthorized&&!empty.rolloverAuthorized&&
                !empty.recordPruningAuthorized&&!empty.grantAuthorized&&
                !empty.replayAuthorized&&!empty.sessionAdmissionAuthorized&&
                !empty.clientAckAuthorized&&!empty.allWriterVersionsFenced;

            // Synthetic fixtures: these tests prove exact byte witnesses,
            // NOT that arbitrary strings are valid PREPARED/COMMIT records.
            byte[] journal=("G21108_PREPARED_TEST_BYTES\n")
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            byte[] marker=("G21108_COMMIT_TEST_BYTES\n")
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            Files.write(prepared,journal);
            Files.write(commit,marker);
            FileTime journalTime=Files.getLastModifiedTime(
                prepared,LinkOption.NOFOLLOW_LINKS);
            FileTime commitTime=Files.getLastModifiedTime(
                commit,LinkOption.NOFOLLOW_LINKS);
            BasicFileAttributes journalAttrs=Files.readAttributes(
                prepared,BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
            BasicFileAttributes commitAttrs=Files.readAttributes(
                commit,BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
            String pinnedJournal=sha(journal),pinnedCommit=sha(marker);

            MailboxWriterArchiveLiveReconciliationV1.Report full=
                MailboxWriterArchiveLiveReconciliationV1
                    .comparePinnedRecoveryLeaves(
                        paths,ACCOUNT,archive.portable,archive.sha256,
                        canonical,pinnedJournal,pinnedCommit);
            presentPassed=full.pinnedCanonicalBytesMatched&&
                full.pinnedRecoverySidecarBytesMatched&&
                full.matchedRecords==2;
            allAuthorityDenied&=!full.cleanupAuthorized&&
                !full.unlinkAuthorized&&!full.rolloverAuthorized&&
                !full.grantAuthorized&&!full.replayAuthorized&&
                !full.sessionAdmissionAuthorized&&!full.clientAckAuthorized;

            MailboxWriterArchiveLiveReconciliationV1.Report older=
                MailboxWriterArchiveLiveReconciliationV1
                    .comparePinnedAccount(paths,ACCOUNT,archive.portable,
                        archive.sha256,canonical);
            inheritedStillNegative=!older.pinnedRecoverySidecarBytesMatched&&
                older.pinnedCanonicalBytesMatched&&
                !older.cleanupAuthorized;

            rejected(()->MailboxWriterArchiveLiveReconciliationV1
                .comparePinnedRecoveryLeaves(paths,ACCOUNT,
                    archive.portable,archive.sha256,canonical,
                    "NOT_SHA","ABSENT"),NEGATIVE);
            malformedDenied=true;
            rejected(()->MailboxWriterArchiveLiveReconciliationV1
                .comparePinnedRecoveryLeaves(paths,ACCOUNT,
                    archive.portable,archive.sha256,canonical,
                    "f".repeat(64),pinnedCommit),NEGATIVE);
            wrongDigestDenied=true;
            rejected(()->MailboxWriterArchiveLiveReconciliationV1
                .comparePinnedRecoveryLeaves(paths,ACCOUNT,
                    archive.portable,archive.sha256,canonical,
                    "ABSENT",pinnedCommit),NEGATIVE);
            unanticipatedPresenceDenied=true;

            Files.delete(prepared);
            rejected(()->MailboxWriterArchiveLiveReconciliationV1
                .comparePinnedRecoveryLeaves(paths,ACCOUNT,
                    archive.portable,archive.sha256,canonical,
                    pinnedJournal,pinnedCommit),NEGATIVE);
            missingPresentDenied=true;
            Files.write(prepared,journal);
            Files.setLastModifiedTime(prepared,journalTime);
            journalAttrs=Files.readAttributes(prepared,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);

            mutate(prepared,journal,journalTime);
            check(physicalStillSame(prepared,journalAttrs),
                "prepared same-physical fixture failed");
            rejected(()->MailboxWriterArchiveLiveReconciliationV1
                .comparePinnedRecoveryLeaves(paths,ACCOUNT,
                    archive.portable,archive.sha256,canonical,
                    pinnedJournal,pinnedCommit),NEGATIVE);
            preparedTamperDenied=true;
            Files.write(prepared,journal);
            Files.setLastModifiedTime(prepared,journalTime);

            mutate(commit,marker,commitTime);
            check(physicalStillSame(commit,commitAttrs),
                "commit same-physical fixture failed");
            rejected(()->MailboxWriterArchiveLiveReconciliationV1
                .comparePinnedRecoveryLeaves(paths,ACCOUNT,
                    archive.portable,archive.sha256,canonical,
                    pinnedJournal,pinnedCommit),NEGATIVE);
            commitTamperDenied=true;
            Files.write(commit,marker);
            Files.setLastModifiedTime(commit,commitTime);

            rejected(()->MailboxWriterArchiveLiveReconciliationV1
                .comparePinnedRecoveryLeaves(paths,ACCOUNT,
                    archive.portable,archive.sha256,canonical,
                    pinnedJournal,pinnedCommit,()->{
                        try{mutate(commit,marker,commitTime);}
                        catch(Exception e){throw new IOException(e);}
                    }),NEGATIVE);
            interpassDenied=true;
            Files.write(commit,marker);
            Files.setLastModifiedTime(commit,commitTime);

            Files.write(prepared,new byte[1024*1024+1]);
            rejected(()->MailboxWriterArchiveLiveReconciliationV1
                .comparePinnedRecoveryLeaves(paths,ACCOUNT,
                    archive.portable,archive.sha256,canonical,
                    pinnedJournal,pinnedCommit),NEGATIVE);
            oversizedDenied=true;
            Files.delete(prepared);
            Files.createDirectory(prepared);
            // G21.99's existing canonical-leaf preflight rejects a
            // nonregular journal BEFORE the G21.108 byte-pin verifier.
            rejected(()->MailboxWriterArchiveLiveReconciliationV1
                .comparePinnedRecoveryLeaves(paths,ACCOUNT,
                    archive.portable,archive.sha256,canonical,
                    pinnedJournal,pinnedCommit),
                "G21.99 CANONICAL_NONREGULAR_OR_UNKEYED_NO_UNLINK");
            nonregularDenied=true;
            Files.delete(prepared);
            Files.write(prepared,journal);

            exactBytesRestored=Arrays.equals(
                    Files.readAllBytes(prepared),journal)&&
                Arrays.equals(Files.readAllBytes(commit),marker);
            zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0;
            check(absentPassed&&presentPassed&&malformedDenied&&
                wrongDigestDenied&&unanticipatedPresenceDenied&&
                missingPresentDenied&&preparedTamperDenied&&
                commitTamperDenied&&interpassDenied&&oversizedDenied&&
                nonregularDenied&&inheritedStillNegative&&
                exactBytesRestored&&zeroLeases&&allAuthorityDenied,
                "opt-in negative three-leaf evidence");
            System.out.println("G21108_PINNED_SIDECAR_DIAGNOSTICS"+
                " explicitAbsentAccepted="+absentPassed+
                " independentlyPinnedPresentAccepted="+presentPassed+
                " incorrectAndMalformedHashDenied="+wrongDigestDenied+
                " unexpectedPresentDenied="+unanticipatedPresenceDenied+
                " missingRequiredDenied="+missingPresentDenied+
                " sameInodePreparedTamperDenied="+preparedTamperDenied+
                " sameInodeCommitTamperDenied="+commitTamperDenied+
                " interpassCommitChangeDenied="+interpassDenied+
                " oversizeAndNonregularDenied="+oversizedDenied+
                " previousG21107Unaltered="+inheritedStillNegative+
                " sourceFixtureBytesRestored="+exactBytesRestored+
                " publicationLeasesZero="+zeroLeases);
            System.out.println("G21108_PINNED_THREE_LEAF_PASS"+
                " archiveAndAccountAndSidecarsPinned=true"+
                " protocolPayloadValidityAsserted=false"+
                " legacyWritersFenced=false cleanup=false unlink=false"+
                " rollover=false grant=false replay=false session=false"+
                " ack=false");
        }finally{
            try(Stream<Path> all=Files.walk(root)){
                for(Path p:all.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"no command arguments accepted");
        run();
    }
}
