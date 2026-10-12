package spk.local;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Objects;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.148: bounded ASCII SHA-256 self-check of retained v1/v2/v3
 * portable archive snapshots. All corruption is test-only within
 * old JVM objects; no independent pin or grant authority implied.
 */
public final class G21148MailboxPortableSelfHashIntegrationTest {
    private static final String ACCOUNT="g21148-cohort";
    private static final long LEASE_MS=300L;
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError("G21.148 "+message);
    }
    private static FilePlayerRepository.PathResolver resolver(Path dir){
        return a->dir.resolve(a+".properties");
    }
    private static String sha(Path file)throws Exception{
        byte[] bytes=MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(file));
        StringBuilder text=new StringBuilder(64);
        for(byte b:bytes)
            text.append(String.format(Locale.ROOT,"%02x",b&255));
        return text.toString();
    }
    private static final class Pins{
        final MailboxWriterEvidencePortableArchiveV1.Snapshot v1;
        final MailboxWriterKindEvidenceV2.Snapshot v2;
        final MailboxFourBoundResultLeavesArchiveV1.Snapshot v3;
        final String account,prepared,commit;
        Pins(FilePlayerRepository.PathResolver r)throws Exception{
            v1=MailboxWriterEvidencePortableArchiveV1.capture(r,ACCOUNT);
            v2=MailboxWriterKindEvidenceV2.capture(r,ACCOUNT);
            v3=MailboxFourBoundResultLeavesArchiveV1.capture(r,ACCOUNT);
            account=sha(r.resolve(ACCOUNT));
            prepared=sha(new MailboxDurableIdempotencyIntentJournal(r)
                .journalPath(ACCOUNT));
            commit=sha(new MailboxGuardedDiskCommitRecord(r)
                .recordPath(ACCOUNT));
        }
    }
    private static MailboxSevenPlaneContinuousPhysicalWitnessV1.Report
        physical(FilePlayerRepository.PathResolver r,Pins p)
        throws IOException{
        return MailboxSevenPlaneContinuousPhysicalWitnessV1
            .compareWithContinuousLease(r,ACCOUNT,p.v3.sha256,
                p.v1.portable,p.v1.sha256,p.account,p.prepared,
                p.commit,p.v2.sha256,LEASE_MS,()->{});
    }
    private static MailboxSevenPlaneImmutableUuidCohortV1.Report
        strict(FilePlayerRepository.PathResolver r,Pins p)
        throws IOException{
        return MailboxSevenPlaneImmutableUuidCohortV1
            .comparePinnedImmutableUuidCohort(
                r,ACCOUNT,p.v3.sha256,p.v1.portable,p.v1.sha256,
                p.account,p.prepared,p.commit,p.v2.sha256,
                LEASE_MS,()->{});
    }
    private static boolean denied(
        FilePlayerRepository.PathResolver r,Pins p,String expected
    ){
        try{strict(r,p);return false;}
        catch(IOException refused){
            return String.valueOf(refused.getMessage())
                .contains(expected);
        }
    }
    private static void negative(
        MailboxSevenPlaneImmutableUuidCohortV1.Report result
    ){
        check(result.exactFourV1V2V3UuidCohortObserved&&
            result.physical.sevenPhysicalPinsMatched&&
            result.physical.threePhysicalIdentitiesStableAcrossCaptures&&
            result.physical.evidenceLeafMetadataStableAcrossCaptures&&
            !result.v3ResultPayloadAuthorAuthenticated&&
            !result.realCallbackExecutionProved&&
            !result.rawOrDefaultWritersExcluded&&
            !result.historicalAndFutureWritersFenced&&
            !result.independentPinAuthenticationProved&&
            !result.atomicRecoveryEpochProved&&
            !result.richSemanticCohortWithinLeaseProved&&
            !result.rolloverAuthorized&&!result.pruningAuthorized&&
            !result.cleanupAuthorized&&!result.unlinkAuthorized&&
            !result.grantAuthorized&&!result.replayAuthorized&&
            !result.sessionAuthorized&&!result.releaseAuthorized&&
            !result.clientAckAuthorized,"physical cohort promoted grant");
    }
    private static MailboxSevenPlaneV3PayloadCohortV1.Report payload(
        FilePlayerRepository.PathResolver r,Pins p
    )throws IOException{
        return MailboxSevenPlaneV3PayloadCohortV1
            .comparePinnedFourPayloads(
                r,ACCOUNT,p.v3.sha256,p.v1.portable,p.v1.sha256,
                p.account,p.prepared,p.commit,p.v2.sha256,
                LEASE_MS,()->{});
    }
    private static MailboxSevenPlaneContinuousSemanticCohortV1.Report
        continuous(FilePlayerRepository.PathResolver r,Pins p)
        throws IOException{
        return MailboxSevenPlaneContinuousSemanticCohortV1
            .comparePinnedContinuousSemantic(
                r,ACCOUNT,p.v3.sha256,p.v1.portable,p.v1.sha256,
                p.account,p.prepared,p.commit,p.v2.sha256,
                LEASE_MS,()->{});
    }
    private static MailboxSevenPlaneContinuousPhysicalWitnessV1.Report
        withObserver(
            FilePlayerRepository.PathResolver r,Pins p,
            MailboxWriterArchiveLiveReconciliationV1.BetweenPasses between,
            MailboxSevenPlaneContinuousPhysicalWitnessV1.InsideLeaseObserver
                visitor
        )throws IOException{
        return MailboxSevenPlaneContinuousPhysicalWitnessV1
            .compareWithContinuousLease(
                r,ACCOUNT,p.v3.sha256,p.v1.portable,p.v1.sha256,
                p.account,p.prepared,p.commit,p.v2.sha256,
                LEASE_MS,between,visitor);
    }
    private static void healthy(FilePlayerRepository.PathResolver r,Pins p)
        throws IOException{
        check(physical(r,p).sevenPhysicalPinsMatched,
            "original physical witness did not recover");
        check(continuous(r,p).originalFourSemanticInspectorsInsideSameLease,
            "original semantic witness did not recover");
        check(MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
            "G21.100 lock retained after healthy re-entry");
    }
    private static Path evidence(Path account,String glob)
        throws IOException{
        try(DirectoryStream<Path> found=Files.newDirectoryStream(
                account.getParent(),
                account.getFileName().toString()+glob)){
            for(Path p:found)return p;
        }
        throw new IOException("G21.148 missing original "+glob);
    }
    private static Object physicalKey(Path file)throws IOException{
        BasicFileAttributes a=Files.readAttributes(file,
            BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        check(a.isRegularFile()&&!a.isSymbolicLink()&&a.fileKey()!=null,
            "expected nofollow regular fileKey on source");
        return a.fileKey();
    }
    /**
     * Two individually pinned valid observations can have
     * DIFFERENT file identities. Unlike G21.143's negative control,
     * explicitly RETAIN the historical report and compare it with
     * fresh separately leased physical snapshots in memory only.
     */
    private static boolean historicalDeltaDetects(
        FilePlayerRepository.PathResolver r,Pins pinned,
        Path target,Path shadow,String expectedLabel
    )throws Exception{
        check(!Files.exists(shadow,LinkOption.NOFOLLOW_LINKS),
            "neutral shadow must not exist");
        byte[] oldBytes=Files.readAllBytes(target);
        FileTime oldTime=Files.getLastModifiedTime(target);
        Object oldKey=physicalKey(target);
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report prior=
            physical(r,pinned);
        MailboxSevenPlaneHistoricalIdentityDeltaV1.Report baseline=
            MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                prior,physical(r,pinned));
        check(baseline.status==
            MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                .MATCHED_OBSERVED_METADATA_NO_AUTHORITY&&
            baseline.changedSources.isEmpty(),
            "unchanged baseline cannot claim physical drift");
        check(MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0,
            "previous witness must have released G21.100 lease");
        boolean distinct=false,byteIdentical=false,timeIdentical=false;
        boolean freshAccepted=false,historicalDeltaDetected=false;
        boolean authorityDenied=false;
        try{
            Files.move(target,shadow);
            Files.copy(shadow,target,StandardCopyOption.COPY_ATTRIBUTES);
            Files.setLastModifiedTime(target,oldTime);
            distinct=!Objects.equals(oldKey,physicalKey(target));
            byteIdentical=Arrays.equals(oldBytes,
                Files.readAllBytes(target));
            timeIdentical=oldTime.equals(
                Files.getLastModifiedTime(target));
            check(distinct&&byteIdentical&&timeIdentical,
                "fixture must provide new fileKey, same bytes/mtime");

            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report now=
                physical(r,pinned);
            MailboxSevenPlaneContinuousSemanticCohortV1.Report semantic=
                continuous(r,pinned);
            freshAccepted=now.sevenPhysicalPinsMatched&&
                semantic.originalFourSemanticInspectorsInsideSameLease&&
                semantic.strictUuidAndPayloadChecksOnCapturedSnapshots&&
                semantic.semanticHashesMatchV3Payloads;

            MailboxSevenPlaneHistoricalIdentityDeltaV1.Report delta=
                MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                    prior,now);
            historicalDeltaDetected=
                delta.status==MailboxSevenPlaneHistoricalIdentityDeltaV1
                    .Status.CHANGED_OBSERVED_METADATA_NO_AUTHORITY&&
                delta.changedSources.size()==1&&
                delta.changedSources.contains(expectedLabel);
            authorityDenied=
                !delta.historicalReportAuthenticated&&
                !delta.independentPinAuthenticationProved&&
                !delta.rawOrDefaultWriterExclusionProved&&
                !delta.historicalAndFutureWritersFenced&&
                !delta.atomicCrossFileEpochProved&&
                !delta.hardwarePowerLossProved&&
                !delta.cleanupAuthorized&&!delta.unlinkAuthorized&&
                !delta.pruningAuthorized&&!delta.rolloverAuthorized&&
                !delta.grantAuthorized&&!delta.replayAuthorized&&
                !delta.sessionAuthorized&&!delta.releaseAuthorized&&
                !delta.clientAckAuthorized&&
                !semantic.externallyAuthenticatedWriterProvenance&&
                !semantic.rawOrDefaultWriterExclusionProved&&
                !semantic.atomicCrossFileEpochProved&&
                !semantic.grantAuthorized&&!semantic.cleanupAuthorized;
        }finally{
            if(Files.exists(shadow,LinkOption.NOFOLLOW_LINKS)){
                Files.deleteIfExists(target);
                Files.move(shadow,target);
            }
            check(Arrays.equals(oldBytes,Files.readAllBytes(target))&&
                oldTime.equals(Files.getLastModifiedTime(target))&&
                Objects.equals(oldKey,physicalKey(target)),
                "real original inode and attributes not restored");
        }
        MailboxSevenPlaneHistoricalIdentityDeltaV1.Report recovered=
            MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                prior,physical(r,pinned));
        check(recovered.status==
            MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                .MATCHED_OBSERVED_METADATA_NO_AUTHORITY&&
            recovered.changedSources.isEmpty(),
            "restored original should match retained past report");
        return distinct&&byteIdentical&&timeIdentical&&freshAccepted&&
            historicalDeltaDetected&&authorityDenied;
    }
    private static MailboxSevenPlaneContinuousPhysicalWitnessV1
        .PhysicalIdentity selectIdentity(
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation obs,
            int n,String label
        ){
        if(n==0)return obs.accountIdentity;
        if(n==1)return obs.journalIdentity;
        if(n==2)return obs.commitIdentity;
        return obs.evidenceLeafIdentities.get(label.substring(5));
    }

    /**
     * A missing provider fileKey cannot be constructed on the hosted
     * Linux inode filesystem, so use isolated TEST-ONLY reflection to
     * simulate the JDK BasicFileAttributes.fileKey()==null result
     * in one already-captured immutable read-only report.
     * No actual files are touched; all private final fields are
     * restored before the next comparison.
     */
    private static boolean nullKeyUncertainty(
        FilePlayerRepository.PathResolver r,Pins pinned,
        int plane,String label,int mode
    )throws Exception{
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical=
            physical(r,pinned);
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current=
            physical(r,pinned);
        MailboxSevenPlaneHistoricalIdentityDeltaV1.Report initial=
            MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                historical,current);
        check(initial.status==
                MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                    .MATCHED_OBSERVED_METADATA_NO_AUTHORITY&&
                initial.uncertainSources.isEmpty()&&
                initial.changedSources.isEmpty(),
            "real hosted NOFOLLOW keys should match before simulation");

        MailboxSevenPlaneContinuousPhysicalWitnessV1
            .PhysicalIdentity[] ids={
                selectIdentity(historical.after,plane,label),
                selectIdentity(current.before,plane,label),
                selectIdentity(current.after,plane,label)
            };
        Field keyField=MailboxSevenPlaneContinuousPhysicalWitnessV1
            .PhysicalIdentity.class.getDeclaredField("fileKey");
        keyField.setAccessible(true);
        Object[] originalKeys=new Object[ids.length];
        for(int i=0;i<ids.length;i++){
            check(ids[i]!=null,"missing physical identity sample");
            originalKeys[i]=keyField.get(ids[i]);
            check(originalKeys[i]!=null,
                "expected real fileKey before simulated provider absence");
        }
        int[] chosen=mode==0?new int[]{0}:
            mode==1?new int[]{1}:
            mode==2?new int[]{2}:new int[]{0,1,2};
        boolean uncertain=false,immutable=false,authorityDenied=false;
        try{
            for(int index:chosen){
                keyField.set(ids[index],null);
                check(keyField.get(ids[index])==null,
                    "synthetic missing physical key was not installed");
            }
            MailboxSevenPlaneHistoricalIdentityDeltaV1.Report delta=
                MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                    historical,current);
            uncertain=delta.status==
                    MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                        .UNCERTAIN_MISSING_FILE_KEY_NO_AUTHORITY&&
                delta.changedSources.isEmpty()&&
                delta.uncertainSources.size()==1&&
                delta.uncertainSources.contains(label);
            try{
                delta.uncertainSources.add("FORGED");
            }catch(UnsupportedOperationException expected){
                immutable=true;
            }
            authorityDenied=!delta.historicalReportAuthenticated&&
                !delta.independentPinAuthenticationProved&&
                !delta.rawOrDefaultWriterExclusionProved&&
                !delta.historicalAndFutureWritersFenced&&
                !delta.atomicCrossFileEpochProved&&
                !delta.hardwarePowerLossProved&&
                !delta.cleanupAuthorized&&!delta.unlinkAuthorized&&
                !delta.pruningAuthorized&&!delta.rolloverAuthorized&&
                !delta.grantAuthorized&&!delta.replayAuthorized&&
                !delta.sessionAuthorized&&!delta.releaseAuthorized&&
                !delta.clientAckAuthorized;
        }finally{
            for(int i=0;i<ids.length;i++)
                keyField.set(ids[i],originalKeys[i]);
        }
        MailboxSevenPlaneHistoricalIdentityDeltaV1.Report restored=
            MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                historical,current);
        check(restored.status==
                MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                    .MATCHED_OBSERVED_METADATA_NO_AUTHORITY&&
                restored.changedSources.isEmpty()&&
                restored.uncertainSources.isEmpty(),
            "simulated provider null must be fully restored");
        return uncertain&&immutable&&authorityDenied;
    }

    /**
     * Test-only corrupt one final field in an already-captured
     * read-only report and restore it in finally. All actual writer
     * files and physical observations remain unchanged.
     */
    private static boolean corruptFieldCheckedVeto(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current,
        Object target,String field
    )throws Exception{
        Field selected=target.getClass().getDeclaredField(field);
        selected.setAccessible(true);
        Object original=selected.get(target);
        check(original!=null,
            "expected non-null observed field "+field);
        boolean rejected=false;
        try{
            if(selected.getType()==boolean.class)
                selected.setBoolean(target,false);
            else
                selected.set(target,null);
            try{
                MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                    historical,current);
            }catch(IOException safelyRejected){
                rejected=String.valueOf(safelyRejected.getMessage())
                    .contains(
                        "HISTORICAL_IDENTITY_DELTA_UNSAFE_NO_AUTHORITY");
            }
        }finally{
            selected.set(target,original);
        }
        MailboxSevenPlaneHistoricalIdentityDeltaV1.Report restored=
            MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                historical,current);
        check(restored.status==
                MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                    .MATCHED_OBSERVED_METADATA_NO_AUTHORITY&&
                restored.changedSources.isEmpty()&&
                restored.uncertainSources.isEmpty(),
            "fixture must restore complete report after "+field);
        return rejected;
    }

    private static int malformedReportCases(
        FilePlayerRepository.PathResolver resolver,Pins pinned,
        String[] labels
    )throws Exception{
        int cases=0;
        // 6 original account/journal/COMMIT/v1/v2/v3 identities
        // x 3 observed positions x 3 required metadata fields.
        for(int plane=0;plane<labels.length;plane++){
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report h=
                physical(resolver,pinned);
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report c=
                physical(resolver,pinned);
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation[]
                observations={h.after,c.before,c.after};
            for(int pos=0;pos<observations.length;pos++){
                Object sample=selectIdentity(
                    observations[pos],plane,labels[plane]);
                for(String key:new String[]{
                        "sha256","created","modified"}){
                    check(corruptFieldCheckedVeto(h,c,sample,key),
                        "malformed physical metadata accepted "+
                        labels[plane]+" pos="+pos+" field="+key);
                    cases++;
                }
            }
        }
        // 3 archive versions x 3 observation positions x
        // required portable body and digest fields.
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report h=
            physical(resolver,pinned);
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report c=
            physical(resolver,pinned);
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation[]
            observations={h.after,c.before,c.after};
        for(MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation
                o:observations){
            Object[] versions={o.v1,o.v2,o.v3};
            for(Object archive:versions){
                for(String key:new String[]{"portable","sha256"}){
                    check(corruptFieldCheckedVeto(h,c,archive,key),
                        "missing original archive "+key+
                        " not checked");
                    cases++;
                }
            }
        }
        // Historical BEFORE is validated too, despite historical
        // AFTER being the primary comparator input; a NULL evidence
        // map and forged failure flags must be checked fail-closed.
        check(corruptFieldCheckedVeto(h,c,h,"before"),
            "missing historical before observation accepted");
        cases++;
        check(corruptFieldCheckedVeto(
                h,c,c.after,"evidenceLeafIdentities"),
            "missing evidence map accepted");
        cases++;
        check(corruptFieldCheckedVeto(
                h,c,h,"sevenPhysicalPinsMatched"),
            "invalid physical proof flags accepted");
        cases++;
        check(MailboxAccountPublicationCoordinator
            .activeJvmLeaseCount()==0,
            "malformed report comparison leaked lifecycle lease");
        return cases;
    }

    /**
     * Each input remains structurally complete with all claimed
     * report-success flags TRUE, yet its own before/after snapshots
     * now disagree. G21.146 shape checks must not be the only gate.
     */
    private static boolean oneInternalContradictionVeto(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current,
        Object target,String field
    )throws Exception{
        Field sample=target.getClass().getDeclaredField(field);
        sample.setAccessible(true);
        Object original=sample.get(target);
        check(original!=null,
            "expected original known non-null "+field);
        Object forged=field.equals("fileKey")
            ?"g21148-alternate-test-only-identity"
            :String.valueOf(original)+"-g21148-modified";
        check(!forged.equals(original),"changed sample must differ");
        boolean checkedDrift=false;
        try{
            sample.set(target,forged);
            try{
                MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                    historical,current);
            }catch(IOException expected){
                String message=String.valueOf(expected.getMessage());
                checkedDrift=message.contains(
                    "HISTORICAL_IDENTITY_DELTA_UNSAFE_NO_AUTHORITY")&&
                    message.contains("INTERNAL_BEFORE_AFTER_DRIFT_");
            }
        }finally{
            sample.set(target,original);
        }
        MailboxSevenPlaneHistoricalIdentityDeltaV1.Report recovered=
            MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                historical,current);
        check(recovered.status==
                MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                    .MATCHED_OBSERVED_METADATA_NO_AUTHORITY&&
                recovered.changedSources.isEmpty()&&
                recovered.uncertainSources.isEmpty(),
                "restored snapshots must yield matched, no authority");
        return checkedDrift;
    }

    private static int internalContradictionCases(
        FilePlayerRepository.PathResolver resolver,Pins pins,
        String[] labels
    )throws Exception{
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical=
            physical(resolver,pins);
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current=
            physical(resolver,pins);
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation[] all={
            historical.before,historical.after,current.before,current.after
        };
        MailboxSevenPlaneHistoricalIdentityDeltaV1.Report baseline=
            MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                historical,current);
        check(baseline.status==
                MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                    .MATCHED_OBSERVED_METADATA_NO_AUTHORITY&&
                !baseline.grantAuthorized&&!baseline.cleanupAuthorized,
            "original reports should match with no authority");

        int cases=0;
        for(int plane=0;plane<labels.length;plane++){
            for(int pos=0;pos<all.length;pos++){
                Object identity=selectIdentity(
                    all[pos],plane,labels[plane]);
                check(oneInternalContradictionVeto(
                    historical,current,identity,"fileKey"),
                    "contradictory physical identity accepted label="+
                    labels[plane]+" position="+pos);
                cases++;
            }
        }
        for(int version=0;version<3;version++){
            for(int pos=0;pos<all.length;pos++){
                Object archive=version==0?all[pos].v1:
                    version==1?all[pos].v2:all[pos].v3;
                check(oneInternalContradictionVeto(
                    historical,current,archive,"portable"),
                    "contradictory original archive accepted version="+
                    version+" position="+pos);
                cases++;
            }
        }
        check(MailboxAccountPublicationCoordinator
            .activeJvmLeaseCount()==0,
            "no G21.100 lease may remain after in-memory checks");
        return cases;
    }

    private static Object archive(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation obs,
        int version
    ){
        return version==0?obs.v1:version==1?obs.v2:obs.v3;
    }

    private static String forgedText(String field,String original){
        if(field.equals("sha256")){
            // Valid length + alphabet, but wrong claimed digest.
            return (original.charAt(0)=='a'?'b':'a')+
                original.substring(1);
        }
        return original+"\nG21148_CORRUPTED_PORTABLE";
    }

    private static boolean archiveSelfHashVeto(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current,
        Object first,Object second,String field,
        String forced,String expectedReason
    )throws Exception{
        Field a=first.getClass().getDeclaredField(field);
        Field b=second==null?null:
            second.getClass().getDeclaredField(field);
        a.setAccessible(true);
        if(b!=null)b.setAccessible(true);
        Object originalA=a.get(first);
        Object originalB=b==null?null:b.get(second);
        check(originalA instanceof String&&
            (b==null||originalA.equals(originalB)),
            "paired archive samples not identical before injection");
        String replacement=forced!=null?forced:
            forgedText(field,(String)originalA);
        check(!replacement.equals(originalA),
            "archive corruption must change original bytes/digest");
        boolean checkedVeto=false;
        try{
            a.set(first,replacement);
            if(b!=null)b.set(second,replacement);
            try{
                MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                    historical,current);
            }catch(IOException expected){
                String message=String.valueOf(expected.getMessage());
                checkedVeto=message.contains(
                    "HISTORICAL_IDENTITY_DELTA_UNSAFE_NO_AUTHORITY")&&
                    message.contains(expectedReason);
            }
        }finally{
            a.set(first,originalA);
            if(b!=null)b.set(second,originalB);
        }
        MailboxSevenPlaneHistoricalIdentityDeltaV1.Report restored=
            MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                historical,current);
        check(restored.status==
                MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                    .MATCHED_OBSERVED_METADATA_NO_AUTHORITY&&
                restored.changedSources.isEmpty()&&
                restored.uncertainSources.isEmpty()&&
                !restored.grantAuthorized&&!restored.cleanupAuthorized,
            "restored portable snapshots must MATCH with no authority");
        return checkedVeto;
    }

    private static int portableDigestIntegrityCases(
        FilePlayerRepository.PathResolver resolver,Pins pins
    )throws Exception{
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historic=
            physical(resolver,pins);
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current=
            physical(resolver,pins);
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation[] all={
            historic.before,historic.after,current.before,current.after
        };
        int passed=0;
        // 3 versions x 4 physical observation positions x
        // 2 corrupted fields = 24 test-only mismatches.
        for(int v=0;v<3;v++){
            for(int pos=0;pos<4;pos++){
                Object target=archive(all[pos],v);
                for(String field:new String[]{"portable","sha256"}){
                    check(archiveSelfHashVeto(
                        historic,current,target,null,field,null,
                        "INTERNAL_BEFORE_AFTER_DRIFT_"),
                        "single corrupt portable or hash accepted v="+v+
                        " pos="+pos+" field="+field);
                    passed++;
                }
            }
        }
        // Modify BOTH BEFORE and AFTER identically within ONE
        // claimed-success Report; G21.147 intra-report comparison
        // alone sees no mismatch. The new digest check still vetoes.
        for(int v=0;v<3;v++){
            for(int report=0;report<2;report++){
                Object left=archive(all[report==0?0:2],v);
                Object right=archive(all[report==0?1:3],v);
                for(String field:new String[]{"portable","sha256"}){
                    check(archiveSelfHashVeto(
                        historic,current,left,right,field,null,
                        "PORTABLE_SHA_MISMATCH"),
                        "coordinated within-report corruption accepted v="+
                        v+" report="+report+" field="+field);
                    passed++;
                }
            }
        }
        // Original v1/v2 max=32768 ASCII bytes, v3 max=4000.
        for(int v=0;v<3;v++){
            Object first=archive(historic.before,v);
            Object second=archive(historic.after,v);
            for(String bad:new String[]{"","\u00e9",
                    new String(new char[v==2?4001:32769])
                        .replace('\0','a')}){
                // Change BOTH historical observations identically,
                // leaving their G21.147 comparison consistent.
                check(archiveSelfHashVeto(
                    historic,current,first,second,"portable",bad,
                    "BAD_PORTABLE_LENGTH_OR_ENCODING"),
                    "noncanonical empty/Unicode/oversized archive accepted"+
                        " version="+v);
                passed++;
            }
        }
        check(MailboxAccountPublicationCoordinator
            .activeJvmLeaseCount()==0,
            "no active G21.100 lease after read-only hash checks");
        return passed;
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21148-cohort-");
        boolean genuine=false,allSixHistoricalIdentityDeltas=false;
        boolean allSixMissingFileKeysUncertain=false;
        boolean malformedReportsCheckedVetoed=false;
        boolean internalContradictionsCheckedVetoed=false;
        boolean portableSelfHashChecksPassed=false;
        boolean originalLegacyApiPreserved=false;
        boolean healthyRetryAfterEverySwap=false;
        boolean noGrant=false,zeroLeases=false;
        try(World world=World.isolatedForTest(60000L,
                new FilePlayerRepository(resolver(root)))){
            world.start();
            FilePlayerRepository.PathResolver r=resolver(root);
            WorldPlayer player=new WorldPlayer();
            long generation=world.registerPlayer(player,ACCOUNT);
            String id=ACCOUNT+":gift";
            player.mailbox().deliver(new RewardDeliveryMessage(
                id,"G21.148 semantic cohort","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G21148_TEST_ONLY"));
            MailboxRewardDeliveryService.Snapshot selected=
                player.mailbox().get(id);
            MailboxPreparedClaimJournal.stageOnly(player,
                MailboxPreparedClaimJournal.prepare(player,selected));
            MailboxSettlementPostimagePlanner.Proposal proposal=
                MailboxSettlementPostimagePlanner.plan(
                    player,generation,selected);
            PlayerSnapshot terminal=MailboxAtomicTerminalSnapshot
                .compose(proposal);
            StrictDurablePlayerSnapshotWriter strictWriter=
                new StrictDurablePlayerSnapshotWriter(r);
            MailboxDurableIdempotencyIntentJournal journal=
                new MailboxDurableIdempotencyIntentJournal(r);
            MailboxGuardedDiskCommitRecord disk=
                new MailboxGuardedDiskCommitRecord(r);
            strictWriter.saveStrictWithResultBinding(
                proposal.preparedPreimage);
            journal.publishPreparedIntentWithResultBinding(proposal);
            WorldPlayerPersistence.PreparedAccountReservation ticket=
                world.persistence().reservePreparedAccount(
                    player,generation,proposal);
            WorldPlayerPersistence.TerminalPublicationEvidence saved=
                world.persistence()
                    .publishReservedTerminalStrictlyWithResultBinding(
                        ticket,proposal,terminal,strictWriter)
                    .get(15L,TimeUnit.SECONDS);
            check(saved.strictReceipt.matchesSnapshot(terminal),
                "real World strict Receipt");
            disk.recordConfirmedDiskTerminalWithResultBinding(
                proposal,saved.strictReceipt);
            Pins original=new Pins(r);
            negative(strict(r,original));
            check(payload(r,original).canonicalFourV3PayloadsObserved,
                "original G21.133 canonical payloads");
            MailboxSevenPlaneContinuousSemanticCohortV1.Report real=
                continuous(r,original);
            check(real.originalFourSemanticInspectorsInsideSameLease&&
                real.strictUuidAndPayloadChecksOnCapturedSnapshots&&
                real.semanticHashesMatchV3Payloads&&
                !real.externallyAuthenticatedWriterProvenance&&
                !real.rawOrDefaultWriterExclusionProved&&
                !real.historicalAndFutureWritersFenced&&
                !real.independentPinAuthenticationProved&&
                !real.atomicCrossFileEpochProved&&
                !real.hardwarePowerLossProved&&
                !real.cleanupAuthorized&&!real.unlinkAuthorized&&
                !real.pruningAuthorized&&!real.rolloverAuthorized&&
                !real.grantAuthorized&&!real.replayAuthorized&&
                !real.sessionAuthorized&&!real.releaseAuthorized&&
                !real.clientAckAuthorized,
                "semantic witness improperly promoted authority");
            genuine=true;

            Path account=r.resolve(ACCOUNT);
            Path[] targets={
                account,
                new MailboxDurableIdempotencyIntentJournal(r)
                    .journalPath(ACCOUNT),
                new MailboxGuardedDiskCommitRecord(r)
                    .recordPath(ACCOUNT),
                evidence(account,".g21103-writer-attempt-v1-*.begin"),
                evidence(account,".g21114-writer-kind-v2-*.begin"),
                evidence(account,".g21120-commit-result-v3-*.result")
            };
            String[] labels={
                "ACCOUNT","PREPARED_JOURNAL","COMMIT",
                "LEAF:"+targets[3].getFileName(),
                "LEAF:"+targets[4].getFileName(),
                "LEAF:"+targets[5].getFileName()
            };
            int identityDeltas=0;
            for(int i=0;i<targets.length;i++){
                Path shadow=root.resolve(
                    "g21148-original-inode-shadow-"+i+".bak");
                check(historicalDeltaDetects(
                    r,original,targets[i],shadow,labels[i]),
                    "retained observation failed to detect identity "+
                        labels[i]);
                identityDeltas++;
                healthy(r,original);
            }
            allSixHistoricalIdentityDeltas=identityDeltas==6;
            // For EACH of six original physical categories simulate:
            // only historical.after missing, only current.before
            // missing, only current.after missing, and ALL missing.
            // It must never be a positive historical identity match.
            int simulatedUnknowns=0;
            for(int i=0;i<labels.length;i++){
                for(int mode=0;mode<4;mode++){
                    check(nullKeyUncertainty(
                        r,original,i,labels[i],mode),
                        "missing NOFOLLOW fileKey wrongly matched "+
                            labels[i]+" mode="+mode);
                    simulatedUnknowns++;
                }
            }
            allSixMissingFileKeysUncertain=simulatedUnknowns==24;
            int checkedMalformed=malformedReportCases(
                r,original,labels);
            malformedReportsCheckedVetoed=checkedMalformed==75;
            int checkedContradictions=internalContradictionCases(
                r,original,labels);
            internalContradictionsCheckedVetoed=
                checkedContradictions==36;
            int portableSelfHashCases=portableDigestIntegrityCases(
                r,original);
            portableSelfHashChecksPassed=portableSelfHashCases==45;
            healthyRetryAfterEverySwap=true;
            originalLegacyApiPreserved=
                physical(r,original).sevenPhysicalPinsMatched;
            noGrant=player.bank().inventorySlots()==0&&
                player.mailbox().get(id).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            System.out.println("G21148_PORTABLE_SELF_HASH_DIAGNOSTICS"+
                " genuineFourBoundWorldBaseline="+genuine+
                " inheritedSixRealInodeDrifts="+identityDeltas+
                " inheritedMissingKeys="+simulatedUnknowns+
                " inheritedMalformedReports="+checkedMalformed+
                " inheritedInternalContradictions="+checkedContradictions+
                " checkedPortableSelfHashCases="+portableSelfHashCases+
                " originalWorldFilesUnmodified=true"+
                " healthySameAccountRetries="+
                    healthyRetryAfterEverySwap+
                " noLiveRewards="+noGrant+" zeroJvmLeases="+zeroLeases);
            check(genuine&&allSixHistoricalIdentityDeltas&&
                allSixMissingFileKeysUncertain&&
                malformedReportsCheckedVetoed&&
                internalContradictionsCheckedVetoed&&
                portableSelfHashChecksPassed&&
                originalLegacyApiPreserved&&
                healthyRetryAfterEverySwap&&noGrant&&zeroLeases,
                "G21.148 portable self-SHA integrity test");
            System.out.println("G21148_PORTABLE_SELF_HASH_PASS"+
                " v1V2V3FourCapturedPositionsChecked=true"+
                " coordinatedBeforeAfterPortableCorruptionVetoed=true"+
                " 45ArchiveMutationAndBoundCasesChecked=true"+
                " originalSourceAndWriterUnchanged=true"+
                " independentSourceAuth=false writerFence=false"+
                " atomicEpoch=false rollover=false pruning=false"+
                " cleanup=false unlink=false grant=false replay=false"+
                " session=false release=false ack=false");
        }finally{
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        run();
    }
}
