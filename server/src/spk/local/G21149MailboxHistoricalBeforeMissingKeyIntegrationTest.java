package spk.local;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * G21.149: synthetic retained physical reports backed by actual
 * NOFOLLOW filesystem attributes. No World grant, physical mutation
 * or historical provenance is asserted. This isolates the G21.145
 * classifier blind spot for a missing historical BEFORE fileKey.
 */
public final class G21149MailboxHistoricalBeforeMissingKeyIntegrationTest {
    private static final String PREFIX="g21149.properties";
    private static final String[] LEAF_NAMES={
        PREFIX+".g21103-writer-attempt-v1-ffffffff-ffff-ffff-ffff-ffffffffffff.begin",
        PREFIX+".g21114-writer-kind-v2-ffffffff-ffff-ffff-ffff-ffffffffffff.begin",
        PREFIX+".g21120-commit-result-v3-ffffffff-ffff-ffff-ffff-ffffffffffff.result"
    };
    private static final String[] LABELS={
        "ACCOUNT","PREPARED_JOURNAL","COMMIT",
        "LEAF:"+LEAF_NAMES[0],
        "LEAF:"+LEAF_NAMES[1],
        "LEAF:"+LEAF_NAMES[2]
    };

    private static void require(boolean condition,String reason){
        if(!condition)throw new AssertionError("G21.149 "+reason);
    }

    private static String digest(String ascii)throws Exception{
        byte[] sha=MessageDigest.getInstance("SHA-256").digest(
            ascii.getBytes(StandardCharsets.US_ASCII));
        final char[] digits="0123456789abcdef".toCharArray();
        StringBuilder out=new StringBuilder(64);
        for(byte b:sha){
            out.append(digits[(b>>>4)&15]);
            out.append(digits[b&15]);
        }
        return out.toString();
    }

    private static MailboxWriterEvidencePortableArchiveV1.Snapshot
        v1()throws Exception{
        Constructor<MailboxWriterEvidencePortableArchiveV1.Snapshot>
            c=MailboxWriterEvidencePortableArchiveV1.Snapshot.class
                .getDeclaredConstructor(String.class,String.class,
                    MailboxWriterEvidencePortableArchiveV1.Verified.class);
        c.setAccessible(true);
        String body="G21149_TEST_ONLY_V1\\n";
        return c.newInstance(body,digest(body),null);
    }

    private static MailboxWriterKindEvidenceV2.Snapshot
        v2()throws Exception{
        Constructor<MailboxWriterKindEvidenceV2.Snapshot>
            c=MailboxWriterKindEvidenceV2.Snapshot.class
                .getDeclaredConstructor(String.class,String.class,
                    MailboxWriterKindEvidenceV2.Report.class);
        c.setAccessible(true);
        String body="G21149_TEST_ONLY_V2\\n";
        return c.newInstance(body,digest(body),null);
    }


    /**
     * Synthetic shape-only v3 portable inventory. Physical fixture
     * source bytes are NOT authenticated by this string. Use actual
     * G21.125 KIND/LEAF/BYTES_BASE64 formatting so G21.155 can
     * compare leaf names without changing inherited test purpose.
     */
    private static String fixtureV3Portable(String basename,String sourceBody){
        String uuid="ffffffff-ffff-ffff-ffff-ffffffffffff";
        String[] leaves={
            basename+".g21120-commit-result-v3-"+uuid+".result",
            basename+".g21121-prepared-result-v3-"+uuid+".result",
            basename+".g21122-strict-prepared-result-v3-"+uuid+".result",
            basename+".g21123-world-terminal-result-v3-"+uuid+".result"
        };
        String account=basename.endsWith(".properties")?
            basename.substring(0,basename.length()-11):basename;
        StringBuilder result=new StringBuilder(
            "SPK-G21125-FOUR-V3-LEAVES-V1\n"+
            "ACCOUNT="+account+"\nCOUNT=4\n");
        String encoded=java.util.Base64.getEncoder().encodeToString(
            sourceBody.getBytes(StandardCharsets.US_ASCII));
        for(int kind=0;kind<4;kind++)
            result.append("KIND=").append(kind).append('\n')
                .append("LEAF=").append(leaves[kind]).append('\n')
                .append("BYTES_BASE64=").append(encoded).append('\n');
        return result.toString();
    }

    private static MailboxFourBoundResultLeavesArchiveV1.Snapshot
        v3(String sourceBody)throws Exception{
        Constructor<MailboxFourBoundResultLeavesArchiveV1.Snapshot>
            c=MailboxFourBoundResultLeavesArchiveV1.Snapshot.class
                .getDeclaredConstructor(String.class,String.class);
        c.setAccessible(true);
        String body=fixtureV3Portable("g21149.properties",sourceBody);
        return c.newInstance(body,digest(body));
    }

    private static MailboxSevenPlaneContinuousPhysicalWitnessV1
        .PhysicalIdentity identity(
            String sha,BasicFileAttributes attrs
        )throws Exception{
        Constructor<MailboxSevenPlaneContinuousPhysicalWitnessV1
            .PhysicalIdentity> ctor=
            MailboxSevenPlaneContinuousPhysicalWitnessV1
                .PhysicalIdentity.class.getDeclaredConstructor(
                    String.class,BasicFileAttributes.class);
        ctor.setAccessible(true);
        return ctor.newInstance(sha,attrs);
    }

    private static MailboxSevenPlaneContinuousPhysicalWitnessV1
        .Observation observation(
            MailboxWriterEvidencePortableArchiveV1.Snapshot a,
            MailboxWriterKindEvidenceV2.Snapshot b,
            MailboxFourBoundResultLeavesArchiveV1.Snapshot c,
            BasicFileAttributes[] attrs,String[] shas
        )throws Exception{
        Map<String,MailboxSevenPlaneContinuousPhysicalWitnessV1
            .PhysicalIdentity> leaves=new TreeMap<>();
        for(int i=3;i<6;i++)
            leaves.put(LEAF_NAMES[i-3],identity(shas[i],attrs[i]));
        // G21.154: real v3 capture ALWAYS includes all four
        // distinct result kinds. Synthetic sources stay read-only.
        String[] extra={
            PREFIX+".g21121-prepared-result-v3-ffffffff-ffff-ffff-ffff-ffffffffffff.result",
            PREFIX+".g21122-strict-prepared-result-v3-ffffffff-ffff-ffff-ffff-ffffffffffff.result",
            PREFIX+".g21123-world-terminal-result-v3-ffffffff-ffff-ffff-ffff-ffffffffffff.result"
        };
        for(String name:extra)
            leaves.put(name,identity(shas[5],attrs[5]));
        Constructor<MailboxSevenPlaneContinuousPhysicalWitnessV1
            .Observation> ctor=
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation.class
                .getDeclaredConstructor(
                    MailboxWriterEvidencePortableArchiveV1.Snapshot.class,
                    MailboxWriterKindEvidenceV2.Snapshot.class,
                    MailboxFourBoundResultLeavesArchiveV1.Snapshot.class,
                    MailboxSevenPlaneContinuousPhysicalWitnessV1
                        .PhysicalIdentity.class,
                    MailboxSevenPlaneContinuousPhysicalWitnessV1
                        .PhysicalIdentity.class,
                    MailboxSevenPlaneContinuousPhysicalWitnessV1
                        .PhysicalIdentity.class,
                    Map.class);
        ctor.setAccessible(true);
        return ctor.newInstance(a,b,c,
            identity(shas[0],attrs[0]),
            identity(shas[1],attrs[1]),
            identity(shas[2],attrs[2]),leaves);
    }

    private static MailboxSevenPlaneContinuousPhysicalWitnessV1.Report
        report(
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation first,
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation second
        )throws Exception{
        Constructor<MailboxSevenPlaneContinuousPhysicalWitnessV1.Report>
            ctor=MailboxSevenPlaneContinuousPhysicalWitnessV1.Report.class
                .getDeclaredConstructor(
                    MailboxSevenPlaneContinuousPhysicalWitnessV1
                        .Observation.class,
                    MailboxSevenPlaneContinuousPhysicalWitnessV1
                        .Observation.class);
        ctor.setAccessible(true);
        return ctor.newInstance(first,second);
    }

    private static MailboxSevenPlaneContinuousPhysicalWitnessV1
        .PhysicalIdentity source(
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation o,
            int position
        ){
        if(position==0)return o.accountIdentity;
        if(position==1)return o.journalIdentity;
        if(position==2)return o.commitIdentity;
        return o.evidenceLeafIdentities.get(LEAF_NAMES[position-3]);
    }

    private static void assertMatched(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current
    )throws Exception{
        MailboxSevenPlaneHistoricalIdentityDeltaV1.Report delta=
            MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                historical,current);
        require(delta.status==
            MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                .MATCHED_OBSERVED_METADATA_NO_AUTHORITY &&
            delta.changedSources.isEmpty() &&
            delta.uncertainSources.isEmpty() &&
            !delta.grantAuthorized && !delta.cleanupAuthorized,
            "restored valid report did not match without authority");
    }

    private static void assertUncertain(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current,
        String label
    )throws Exception{
        MailboxSevenPlaneHistoricalIdentityDeltaV1.Report delta=
            MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                historical,current);
        SortedSet<String> missing=delta.uncertainSources;
        require(delta.status==
            MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                .UNCERTAIN_MISSING_FILE_KEY_NO_AUTHORITY &&
            delta.changedSources.isEmpty() &&
            missing.size()==1 && missing.contains(label) &&
            !delta.historicalReportAuthenticated &&
            !delta.independentPinAuthenticationProved &&
            !delta.rawOrDefaultWriterExclusionProved &&
            !delta.atomicCrossFileEpochProved &&
            !delta.cleanupAuthorized && !delta.unlinkAuthorized &&
            !delta.rolloverAuthorized && !delta.pruningAuthorized &&
            !delta.grantAuthorized && !delta.replayAuthorized &&
            !delta.sessionAuthorized && !delta.releaseAuthorized &&
            !delta.clientAckAuthorized,
            "missing original key classified as MATCH or authority: "+label);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21149-original-key-");
        try{
            Path[] files=new Path[6];
            files[0]=root.resolve(PREFIX);
            files[1]=root.resolve(PREFIX+".prepared");
            files[2]=root.resolve(PREFIX+".commit");
            for(int i=3;i<6;i++)
                files[i]=root.resolve(LEAF_NAMES[i-3]);
            BasicFileAttributes[] attrs=new BasicFileAttributes[6];
            String[] shas=new String[6];
            String[] sourceBodies=new String[6];
            for(int i=0;i<6;i++){
                String body="G21149_ORIGINAL_SOURCE_"+i+"\\n";
                sourceBodies[i]=body;
                Files.write(files[i],
                    body.getBytes(StandardCharsets.US_ASCII));
                attrs[i]=Files.readAttributes(files[i],
                    BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                require(attrs[i].isRegularFile() &&
                    attrs[i].fileKey()!=null,
                    "host must supply six real NOFOLLOW fileKeys");
                shas[i]=digest(body);
            }
            MailboxWriterEvidencePortableArchiveV1.Snapshot a=v1();
            MailboxWriterKindEvidenceV2.Snapshot b=v2();
            MailboxFourBoundResultLeavesArchiveV1.Snapshot c=v3(sourceBodies[5]);
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation[]
                observations=new MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .Observation[4];
            for(int i=0;i<observations.length;i++)
                observations[i]=observation(a,b,c,attrs,shas);
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical=
                report(observations[0],observations[1]);
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current=
                report(observations[2],observations[3]);
            assertMatched(historical,current);
            Field key=MailboxSevenPlaneContinuousPhysicalWitnessV1
                .PhysicalIdentity.class.getDeclaredField("fileKey");
            key.setAccessible(true);
            int singleCaptureCases=0;
            int originalBeforeOnlyCases=0;
            for(int plane=0;plane<6;plane++){
                for(int position=0;position<4;position++){
                    MailboxSevenPlaneContinuousPhysicalWitnessV1
                        .PhysicalIdentity sample=source(
                            observations[position],plane);
                    require(sample!=null,"missing real source "+plane);
                    Object original=key.get(sample);
                    require(original!=null,"fixture key absent");
                    try{
                        key.set(sample,null);
                        assertUncertain(historical,current,LABELS[plane]);
                        if(position==0)originalBeforeOnlyCases++;
                        singleCaptureCases++;
                    }finally{
                        key.set(sample,original);
                    }
                    assertMatched(historical,current);
                }
            }
            require(singleCaptureCases==24 &&
                originalBeforeOnlyCases==6 &&
                MailboxAccountPublicationCoordinator
                    .activeJvmLeaseCount()==0 &&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0,
                "expected 24 checks, six restored historical BEFORE cases");
            System.out.println(
                "G21149_HISTORICAL_BEFORE_KEY_DIAGNOSTICS"+
                " originalRealNofollowSources=6"+
                " fourCapturePositionsChecked=4"+
                " isolatedNullKeyCases="+singleCaptureCases+
                " formerlyHiddenHistoricalBeforeCases="+
                    originalBeforeOnlyCases+
                " restoredMatchingReports=true"+
                " noLiveRewards=true zeroJvmLeases=true");
            System.out.println(
                "G21149_HISTORICAL_BEFORE_KEY_PASS"+
                " missingOriginalHistoricalBeforeFileKeyUncertain=true"+
                " inheritedG21145UncertaintyPreserved=true"+
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
        require(args.length==0,"unexpected args");
        run();
    }
}
