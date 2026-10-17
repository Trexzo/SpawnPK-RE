package spk.local;

import java.io.IOException;
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
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * G21.160: read-only historical/current delta and signer-root denial
 * are composed before any hypothetical recovery admission. No
 * production settlement or authorization flow is touched.
 */
public final class G21160MailboxHistoricalRecoveryAdmissionIntegrationTest {
    private static final String[] LEAVES={
        "g21160.properties.g21103-writer-attempt-v1-ffffffff-ffff-ffff-ffff-ffffffffffff.begin",
        "g21160.properties.g21114-writer-kind-v2-ffffffff-ffff-ffff-ffff-ffffffffffff.begin",
        "g21160.properties.g21120-commit-result-v3-ffffffff-ffff-ffff-ffff-ffffffffffff.result"
    };
    private static final String[] LABELS={
        "ACCOUNT","PREPARED_JOURNAL","COMMIT",
        "LEAF:"+LEAVES[0],"LEAF:"+LEAVES[1],"LEAF:"+LEAVES[2]
    };
    private static final long[] MAX={1048576L,1024L,1024L,512L,512L,512L};

    private static void check(boolean value,String message){
        if(!value)throw new AssertionError("G21.160 "+message);
    }

    private static String hash(String value)throws Exception{
        byte[] bytes=MessageDigest.getInstance("SHA-256").digest(
            value.getBytes(StandardCharsets.US_ASCII));
        char[] chars="0123456789abcdef".toCharArray();
        StringBuilder result=new StringBuilder(64);
        for(byte b:bytes){
            result.append(chars[(b>>>4)&15]);
            result.append(chars[b&15]);
        }
        return result.toString();
    }

    private static <T> T instantiate(
        Class<T> type,Class<?>[] parameters,Object...args
    )throws Exception{
        Constructor<T> ctor=type.getDeclaredConstructor(parameters);
        ctor.setAccessible(true);
        return ctor.newInstance(args);
    }

    private static MailboxSevenPlaneContinuousPhysicalWitnessV1
        .PhysicalIdentity physical(
            String sha,BasicFileAttributes attrs
        )throws Exception{
        return instantiate(
            MailboxSevenPlaneContinuousPhysicalWitnessV1
                .PhysicalIdentity.class,
            new Class<?>[]{String.class,BasicFileAttributes.class},
            sha,attrs);
    }

    private static MailboxWriterEvidencePortableArchiveV1.Snapshot
        archiveV1()throws Exception{
        String text="G21160_TEST_ONLY_V1\n";
        return instantiate(
            MailboxWriterEvidencePortableArchiveV1.Snapshot.class,
            new Class<?>[]{String.class,String.class,
                MailboxWriterEvidencePortableArchiveV1.Verified.class},
            text,hash(text),null);
    }

    private static MailboxWriterKindEvidenceV2.Snapshot
        archiveV2()throws Exception{
        String text="G21160_TEST_ONLY_V2\n";
        return instantiate(
            MailboxWriterKindEvidenceV2.Snapshot.class,
            new Class<?>[]{String.class,String.class,
                MailboxWriterKindEvidenceV2.Report.class},
            text,hash(text),null);
    }


    /**
     * Synthetic shape-only v3 portable inventory. Physical fixture
     * source bytes are NOT authenticated by this string. Use actual
     * G21.125 KIND/LEAF/BYTES_BASE64 formatting so G21.160 can
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
        archiveV3(String sourceBody)throws Exception{
        String text=fixtureV3Portable("g21160.properties",sourceBody);
        return instantiate(
            MailboxFourBoundResultLeavesArchiveV1.Snapshot.class,
            new Class<?>[]{String.class,String.class},
            text,hash(text));
    }

    private static MailboxSevenPlaneContinuousPhysicalWitnessV1
        .Observation observation(
            MailboxSevenPlaneContinuousPhysicalWitnessV1
                .PhysicalIdentity[] physical,
            MailboxWriterEvidencePortableArchiveV1.Snapshot v1,
            MailboxWriterKindEvidenceV2.Snapshot v2,
            MailboxFourBoundResultLeavesArchiveV1.Snapshot v3,
            BasicFileAttributes[] attrs,String[] shas
        )throws Exception{
        Map<String,MailboxSevenPlaneContinuousPhysicalWitnessV1
            .PhysicalIdentity> leaves=new TreeMap<>();
        for(int i=3;i<6;i++)leaves.put(LEAVES[i-3],physical[i]);
        // Keep all four v3 kinds in synthetic retained reports.
        String basename=LEAVES[2].substring(0,
            LEAVES[2].indexOf(".g21120-"));
        String[] names={
            basename+".g21121-prepared-result-v3-ffffffff-ffff-ffff-ffff-ffffffffffff.result",
            basename+".g21122-strict-prepared-result-v3-ffffffff-ffff-ffff-ffff-ffffffffffff.result",
            basename+".g21123-world-terminal-result-v3-ffffffff-ffff-ffff-ffff-ffffffffffff.result"
        };
        for(String name:names)
            leaves.put(name,physical(shas[5],attrs[5]));
        return instantiate(
            MailboxSevenPlaneContinuousPhysicalWitnessV1
                .Observation.class,
            new Class<?>[]{
                MailboxWriterEvidencePortableArchiveV1.Snapshot.class,
                MailboxWriterKindEvidenceV2.Snapshot.class,
                MailboxFourBoundResultLeavesArchiveV1.Snapshot.class,
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .PhysicalIdentity.class,
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .PhysicalIdentity.class,
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .PhysicalIdentity.class,
                Map.class
            },
            v1,v2,v3,physical[0],physical[1],physical[2],leaves);
    }

    private static MailboxSevenPlaneContinuousPhysicalWitnessV1.Report
        report(
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation a,
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation b
        )throws Exception{
        return instantiate(
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report.class,
            new Class<?>[]{
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .Observation.class,
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .Observation.class},
            a,b);
    }

    private static void matched(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report old,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current
    )throws Exception{
        MailboxSevenPlaneHistoricalIdentityDeltaV1.Report delta=
            MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(old,current);
        check(delta.status==
            MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                .MATCHED_OBSERVED_METADATA_NO_AUTHORITY &&
            delta.changedSources.isEmpty() &&
            delta.uncertainSources.isEmpty() &&
            !delta.cleanupAuthorized && !delta.grantAuthorized,
            "legitimate fixture should MATCH without authority");
    }

    private static Field field(Class<?> type,String name)
        throws Exception{
        Field found=type.getDeclaredField(name);
        found.setAccessible(true);
        return found;
    }

    @FunctionalInterface
    private interface CheckedOperation {
        void run()throws Exception;
    }

    private static void veto(
        CheckedOperation operation,String reason
    )throws Exception{
        boolean rejected=false;
        try{
            operation.run();
        }catch(IOException expected){
            String diagnostic=String.valueOf(expected.getMessage());
            rejected=diagnostic.contains(
                "UNSAFE_NO_AUTHORITY")&&
                diagnostic.contains(reason);
        }
        check(rejected,"expected checked detached signature veto "+reason);
    }

    private static String hexSha(byte[] input)throws Exception{
        byte[] sha=MessageDigest.getInstance("SHA-256").digest(input);
        char[] chars="0123456789abcdef".toCharArray();
        StringBuilder result=new StringBuilder(64);
        for(byte b:sha){
            result.append(chars[(b>>>4)&15]);
            result.append(chars[b&15]);
        }
        return result.toString();
    }

    private static byte[] sign(
        java.security.PrivateKey key,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report report,
        String account
    )throws Exception{
        byte[] message=MailboxDetachedHistoricalCandidateSignatureV1
            .candidateMessage(report,account);
        java.security.Signature signer=
            java.security.Signature.getInstance("SHA256withECDSA");
        signer.initSign(key);
        signer.update(message);
        return signer.sign();
    }

    private static void admissionVeto(
        CheckedOperation operation,String expectedReason
    )throws Exception{
        boolean checked=false;
        try{
            operation.run();
        }catch(IOException failure){
            String message=String.valueOf(failure.getMessage());
            checked=message.contains("UNSAFE_NO_AUTHORITY")&&
                message.contains(expectedReason);
        }
        check(checked,"missing checked admission veto "+expectedReason);
    }

    private static MailboxHistoricalRecoveryAdmissionV1.Review review(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report old,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current,
        String account,java.security.KeyPair signer,byte[] signature,
        String expectedPin
    )throws Exception{
        return MailboxHistoricalRecoveryAdmissionV1.reviewCandidate(
            old,current,account,signer.getPublic().getEncoded(),
            hexSha(signer.getPublic().getEncoded()),signature,
            account,expectedPin);
    }

    private static void reviewUntrustedMatch(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report old,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current,
        String account,java.security.KeyPair signer,byte[] signature
    )throws Exception{
        MailboxHistoricalRecoveryAdmissionV1.Review r=review(
            old,current,account,signer,signature,
            hexSha(signer.getPublic().getEncoded()));
        check(r.status==MailboxHistoricalRecoveryAdmissionV1.Status
                .MATCHED_UNTRUSTED_CANDIDATE_REVIEW_NO_AUTHORITY&&
            r.historicalAndCurrentObservedMatch&&
            r.candidateSignatureReviewed&&
            r.changedSources.isEmpty()&&
            r.uncertainSources.isEmpty()&&
            !r.independentlyAuthenticatedProvenance&&
            !r.productionSignerTrustAdmitted&&
            !r.originalFileKeyIdentityAuthenticated&&
            !r.rawOrDefaultWriterExclusionProved&&
            !r.historicalAndFutureWritersExcluded&&
            !r.atomicRecoveryEpochProved&&
            !r.hardwarePowerLossProved&&
            !r.cleanupAuthorized&&!r.unlinkAuthorized&&
            !r.pruningAuthorized&&!r.rolloverAuthorized&&
            !r.grantAuthorized&&!r.replayAuthorized&&
            !r.sessionAuthorized&&!r.releaseAuthorized&&
            !r.clientAckAuthorized,
            "review must never promote self-signed candidate to trust");
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21160-composed-admission-");
        try{
            BasicFileAttributes[] attrs=new BasicFileAttributes[6];
            String[] shas=new String[6];
            String[] bodies=new String[6];
            for(int i=0;i<6;i++){
                Path source=root.resolve("physical-"+i);
                String body="G21160_NOFOLLOW_SOURCE_"+i+"\n";
                bodies[i]=body;
                Files.write(source,
                    body.getBytes(StandardCharsets.US_ASCII));
                attrs[i]=Files.readAttributes(source,
                    BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                check(attrs[i].isRegularFile()&&attrs[i].fileKey()!=null,
                    "original NOFOLLOW fixture unavailable");
                shas[i]=hash(body);
            }
            Path changedSource=root.resolve("changed-account");
            String changedBody="G21160_CHANGED_SOURCE\n";
            Files.write(changedSource,
                changedBody.getBytes(StandardCharsets.US_ASCII));
            BasicFileAttributes changedAttrs=Files.readAttributes(
                changedSource,BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
            check(changedAttrs.fileKey()!=null&&
                changedAttrs.isRegularFile(),
                "changed physical fixture unavailable");
            MailboxWriterEvidencePortableArchiveV1.Snapshot v1=archiveV1();
            MailboxWriterKindEvidenceV2.Snapshot v2=archiveV2();
            MailboxFourBoundResultLeavesArchiveV1.Snapshot v3=
                archiveV3(bodies[5]);
            MailboxSevenPlaneContinuousPhysicalWitnessV1
                .Observation[] all=new
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .Observation[4];
            for(int i=0;i<4;i++){
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .PhysicalIdentity[] identities=new
                    MailboxSevenPlaneContinuousPhysicalWitnessV1
                        .PhysicalIdentity[6];
                for(int j=0;j<6;j++)
                    identities[j]=physical(shas[j],attrs[j]);
                all[i]=observation(identities,v1,v2,v3,attrs,shas);
            }
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical=
                report(all[0],all[1]);
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current=
                report(all[2],all[3]);
            matched(historical,current);
            final String account="g21160";
            java.security.KeyPairGenerator factory=
                java.security.KeyPairGenerator.getInstance("EC");
            factory.initialize(new java.security.spec.ECGenParameterSpec(
                "secp256r1"));
            java.security.KeyPair original=factory.generateKeyPair();
            java.security.KeyPair attacker=factory.generateKeyPair();
            byte[] signature=sign(
                original.getPrivate(),historical,account);
            byte[] forgery=sign(
                attacker.getPrivate(),historical,account);
            reviewUntrustedMatch(historical,current,account,
                original,signature);
            reviewUntrustedMatch(historical,current,account,
                attacker,forgery);

            int productionDenied=0,otherDenied=0,reviewMatches=2,
                driftReviews=0,uncertainReviews=0;
            admissionVeto(()->MailboxHistoricalRecoveryAdmissionV1
                .requireProductionRecoveryAdmission(
                    historical,current,account,
                    original.getPublic().getEncoded()),
                "NO_INDEPENDENTLY_ENROLLED_PRODUCTION_ROOT");
            productionDenied++;
            admissionVeto(()->MailboxHistoricalRecoveryAdmissionV1
                .requireProductionRecoveryAdmission(
                    historical,current,account,
                    attacker.getPublic().getEncoded()),
                "NO_INDEPENDENTLY_ENROLLED_PRODUCTION_ROOT");
            productionDenied++;
            admissionVeto(()->MailboxHistoricalRecoveryAdmissionV1
                .requireProductionRecoveryAdmission(
                    historical,current,"INVALID ACCOUNT!",
                    original.getPublic().getEncoded()),
                "INVALID_RECOVERY_ACCOUNT_CONTEXT");
            otherDenied++;
            admissionVeto(()->MailboxHistoricalRecoveryAdmissionV1
                .requireProductionRecoveryAdmission(
                    historical,current,account,new byte[0]),
                "INVALID_PRODUCTION_SIGNER_CANDIDATE");
            otherDenied++;
            admissionVeto(()->review(historical,current,account,
                attacker,forgery,
                hexSha(original.getPublic().getEncoded())),
                "CANDIDATE_KEY_NOT_PRECOMMITTED");
            otherDenied++;
            admissionVeto(()->review(historical,current,account,
                original,forgery,
                hexSha(original.getPublic().getEncoded())),
                "CANDIDATE_SIGNATURE_INVALID");
            otherDenied++;

            // Construct TWO complete current observations whose
            // account source has genuinely different NOFOLLOW bytes
            // and file attributes. This is observed content drift,
            // not a malformed retained report.
            MailboxSevenPlaneContinuousPhysicalWitnessV1
                .Observation[] changed=new
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .Observation[2];
            for(int index=0;index<2;index++){
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .PhysicalIdentity[] ids=new
                    MailboxSevenPlaneContinuousPhysicalWitnessV1
                        .PhysicalIdentity[6];
                for(int j=0;j<6;j++)
                    ids[j]=physical(shas[j],attrs[j]);
                ids[0]=physical(hash(changedBody),changedAttrs);
                changed[index]=observation(ids,v1,v2,v3,attrs,shas);
            }
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report
                changedCurrent=report(changed[0],changed[1]);
            MailboxHistoricalRecoveryAdmissionV1.Review drift=
                review(historical,changedCurrent,account,
                    original,signature,
                    hexSha(original.getPublic().getEncoded()));
            check(drift.status==
                    MailboxHistoricalRecoveryAdmissionV1.Status
                        .CHANGED_OBSERVED_EVIDENCE_NO_AUTHORITY&&
                drift.changedSources.contains("ACCOUNT")&&
                !drift.historicalAndCurrentObservedMatch&&
                !drift.candidateSignatureReviewed&&
                !drift.grantAuthorized&&!drift.cleanupAuthorized,
                "changed current account falsely accepted");
            driftReviews++;
            admissionVeto(()->MailboxHistoricalRecoveryAdmissionV1
                .requireProductionRecoveryAdmission(
                    historical,changedCurrent,account,
                    original.getPublic().getEncoded()),
                "CHANGED_RECOVERY_EVIDENCE");
            otherDenied++;

            // Missing current fileKey is uncertain, even when
            // portable hashes, timestamps and SHA all match.
            Field key=field(
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .PhysicalIdentity.class,"fileKey");
            Object originalKey=key.get(all[2].accountIdentity);
            try{
                key.set(all[2].accountIdentity,null);
                MailboxHistoricalRecoveryAdmissionV1.Review uncertain=
                    review(historical,current,account,
                        original,signature,
                        hexSha(original.getPublic().getEncoded()));
                check(uncertain.status==
                    MailboxHistoricalRecoveryAdmissionV1.Status
                        .UNCERTAIN_OBSERVED_EVIDENCE_NO_AUTHORITY&&
                    uncertain.uncertainSources.contains("ACCOUNT")&&
                    !uncertain.candidateSignatureReviewed&&
                    !uncertain.grantAuthorized,
                    "missing fileKey must stop candidate review");
                uncertainReviews++;
                admissionVeto(()->MailboxHistoricalRecoveryAdmissionV1
                    .requireProductionRecoveryAdmission(
                        historical,current,account,
                        original.getPublic().getEncoded()),
                    "UNCERTAIN_RECOVERY_EVIDENCE");
                otherDenied++;
            }finally{
                key.set(all[2].accountIdentity,originalKey);
            }
            matched(historical,current);

            // Coherent signed forgery of ALL retained objects still
            // only returns a review, never reaches production root
            // or live reward. Reset source fields in finally.
            Field portable=field(
                MailboxWriterEvidencePortableArchiveV1.Snapshot.class,
                "portable");
            Field sha=field(
                MailboxWriterEvidencePortableArchiveV1.Snapshot.class,
                "sha256");
            String oldPortable=(String)portable.get(v1);
            String oldSha=(String)sha.get(v1);
            try{
                String replaced=oldPortable+"G21160_SELF_SIGNED_FORGERY\n";
                portable.set(v1,replaced);
                sha.set(v1,hash(replaced));
                matched(historical,current);
                admissionVeto(()->review(historical,current,account,
                    original,signature,
                    hexSha(original.getPublic().getEncoded())),
                    "CANDIDATE_SIGNATURE_INVALID");
                otherDenied++;
                byte[] selfSigned=sign(
                    attacker.getPrivate(),historical,account);
                reviewUntrustedMatch(historical,current,account,
                    attacker,selfSigned);
                reviewMatches++;
                admissionVeto(()->MailboxHistoricalRecoveryAdmissionV1
                    .requireProductionRecoveryAdmission(
                        historical,current,account,
                        attacker.getPublic().getEncoded()),
                    "NO_INDEPENDENTLY_ENROLLED_PRODUCTION_ROOT");
                productionDenied++;
            }finally{
                portable.set(v1,oldPortable);
                sha.set(v1,oldSha);
            }
            matched(historical,current);
            reviewUntrustedMatch(historical,current,account,
                original,signature);

            boolean zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            check(productionDenied==3&&otherDenied==7&&
                reviewMatches==3&&driftReviews==1&&
                uncertainReviews==1&&zeroLeases,
                "G21.160 case totals/zero lease invariant failed");
            System.out.println(
                "G21160_COMPOSED_RECOVERY_ADMISSION_DIAGNOSTICS"+
                " physicalNofollowSources=7 productionRootVetoes="+
                productionDenied+" otherCheckedVetoes="+otherDenied+
                " untrustedSignedReviews="+reviewMatches+
                " changedObservedReviews="+driftReviews+
                " uncertainObservedReviews="+uncertainReviews+
                " healthyReportsRestored=true zeroJvmLeases="+
                zeroLeases);
            System.out.println(
                "G21160_COMPOSED_RECOVERY_ADMISSION_PASS"+
                " signedUntrustedHistoryNeverPromoted=true"+
                " changedEvidenceRejected=true"+
                " uncertainEvidenceRejected=true"+
                " independentRootProvisioning=false"+
                " originalProductionCallersUnchanged=true"+
                " rawWriterFence=false atomicEpoch=false"+
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
