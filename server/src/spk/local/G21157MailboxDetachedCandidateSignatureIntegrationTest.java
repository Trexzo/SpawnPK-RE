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
 * G21.157: verify detached signature math over a bound retained
 * historical report, while refusing independent trust or authority.
 * Ephemeral test keys are NEVER production provenance keys.
 */
public final class G21157MailboxDetachedCandidateSignatureIntegrationTest {
    private static final String[] LEAVES={
        "g21157.properties.g21103-writer-attempt-v1-ffffffff-ffff-ffff-ffff-ffffffffffff.begin",
        "g21157.properties.g21114-writer-kind-v2-ffffffff-ffff-ffff-ffff-ffffffffffff.begin",
        "g21157.properties.g21120-commit-result-v3-ffffffff-ffff-ffff-ffff-ffffffffffff.result"
    };
    private static final String[] LABELS={
        "ACCOUNT","PREPARED_JOURNAL","COMMIT",
        "LEAF:"+LEAVES[0],"LEAF:"+LEAVES[1],"LEAF:"+LEAVES[2]
    };
    private static final long[] MAX={1048576L,1024L,1024L,512L,512L,512L};

    private static void check(boolean value,String message){
        if(!value)throw new AssertionError("G21.157 "+message);
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
        String text="G21157_TEST_ONLY_V1\n";
        return instantiate(
            MailboxWriterEvidencePortableArchiveV1.Snapshot.class,
            new Class<?>[]{String.class,String.class,
                MailboxWriterEvidencePortableArchiveV1.Verified.class},
            text,hash(text),null);
    }

    private static MailboxWriterKindEvidenceV2.Snapshot
        archiveV2()throws Exception{
        String text="G21157_TEST_ONLY_V2\n";
        return instantiate(
            MailboxWriterKindEvidenceV2.Snapshot.class,
            new Class<?>[]{String.class,String.class,
                MailboxWriterKindEvidenceV2.Report.class},
            text,hash(text),null);
    }


    /**
     * Synthetic shape-only v3 portable inventory. Physical fixture
     * source bytes are NOT authenticated by this string. Use actual
     * G21.125 KIND/LEAF/BYTES_BASE64 formatting so G21.157 can
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
        String text=fixtureV3Portable("g21157.properties",sourceBody);
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
                "CANDIDATE_SIGNATURE_UNSAFE_NO_AUTHORITY")&&
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

    private static void valid(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical,
        String account,java.security.KeyPair pair,byte[] signature
    )throws Exception{
        MailboxDetachedHistoricalCandidateSignatureV1.Report result=
            MailboxDetachedHistoricalCandidateSignatureV1.verifyCandidate(
                historical,account,pair.getPublic().getEncoded(),
                hexSha(pair.getPublic().getEncoded()),signature);
        check(result.status==
            MailboxDetachedHistoricalCandidateSignatureV1.Status
                .VALID_SIGNATURE_UNTRUSTED_KEY_NO_AUTHORITY&&
            result.candidateSignatureMathematicallyVerified&&
            !result.independentTrustAnchorAuthenticated&&
            !result.historicalSourceAuthenticated&&
            !result.originalFileKeyIdentityAuthenticated&&
            !result.historicalWriterCensusProved&&
            !result.rawOrDefaultWriterExclusionProved&&
            !result.futureWriterFenceProved&&
            !result.atomicRecoveryEpochProved&&
            !result.hardwarePowerLossProved&&
            !result.cleanupAuthorized&&!result.unlinkAuthorized&&
            !result.pruningAuthorized&&!result.rolloverAuthorized&&
            !result.grantAuthorized&&!result.replayAuthorized&&
            !result.sessionAuthorized&&!result.releaseAuthorized&&
            !result.clientAckAuthorized,
            "valid mathematical signature was promoted to AUTHORITY");
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21157-candidate-sig-");
        try{
            BasicFileAttributes[] attrs=new BasicFileAttributes[6];
            String[] shas=new String[6];
            String[] sourceBodies=new String[6];
            for(int i=0;i<6;i++){
                Path file=root.resolve("physical-"+i);
                String body="G21157_NOFOLLOW_SOURCE_"+i+"\n";
                sourceBodies[i]=body;
                Files.write(file,
                    body.getBytes(StandardCharsets.US_ASCII));
                attrs[i]=Files.readAttributes(file,
                    BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                check(attrs[i].isRegularFile()&&attrs[i].fileKey()!=null,
                    "missing real source NOFOLLOW metadata");
                shas[i]=hash(body);
            }
            MailboxWriterEvidencePortableArchiveV1.Snapshot v1=archiveV1();
            MailboxWriterKindEvidenceV2.Snapshot v2=archiveV2();
            MailboxFourBoundResultLeavesArchiveV1.Snapshot v3=
                archiveV3(sourceBodies[5]);
            MailboxSevenPlaneContinuousPhysicalWitnessV1
                .Observation[] all=
                new MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .Observation[4];
            for(int i=0;i<4;i++){
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .PhysicalIdentity[] ids=
                    new MailboxSevenPlaneContinuousPhysicalWitnessV1
                        .PhysicalIdentity[6];
                for(int j=0;j<6;j++)
                    ids[j]=physical(shas[j],attrs[j]);
                all[i]=observation(ids,v1,v2,v3,attrs,shas);
            }
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical=
                report(all[0],all[1]);
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current=
                report(all[2],all[3]);
            matched(historical,current);
            final String account="g21157";
            java.security.KeyPairGenerator maker=
                java.security.KeyPairGenerator.getInstance("EC");
            maker.initialize(new java.security.spec.ECGenParameterSpec(
                "secp256r1"));
            java.security.KeyPair first=maker.generateKeyPair();
            java.security.KeyPair attacker=maker.generateKeyPair();
            byte[] sig=sign(first.getPrivate(),historical,account);
            valid(historical,account,first,sig);

            int rejected=0,coherentForgeries=0;
            veto(()->MailboxDetachedHistoricalCandidateSignatureV1
                .verifyCandidate(historical,account,
                    attacker.getPublic().getEncoded(),
                    hexSha(attacker.getPublic().getEncoded()),sig),
                "CANDIDATE_SIGNATURE_INVALID");
            rejected++;
            veto(()->MailboxDetachedHistoricalCandidateSignatureV1
                .verifyCandidate(historical,account,
                    first.getPublic().getEncoded(),
                    hexSha(attacker.getPublic().getEncoded()),sig),
                "CANDIDATE_KEY_FINGERPRINT_MISMATCH");
            rejected++;
            veto(()->MailboxDetachedHistoricalCandidateSignatureV1
                .verifyCandidate(historical,"another-account",
                    first.getPublic().getEncoded(),
                    hexSha(first.getPublic().getEncoded()),sig),
                "HISTORICAL_ACCOUNT_CONTEXT_MISMATCH");
            rejected++;
            byte[] modifiedSignature=sig.clone();
            modifiedSignature[modifiedSignature.length-1]^=1;
            veto(()->MailboxDetachedHistoricalCandidateSignatureV1
                .verifyCandidate(historical,account,
                    first.getPublic().getEncoded(),
                    hexSha(first.getPublic().getEncoded()),
                    modifiedSignature),
                "CANDIDATE_SIGNATURE_INVALID");
            rejected++;
            veto(()->MailboxDetachedHistoricalCandidateSignatureV1
                .verifyCandidate(historical,account,
                    new byte[2],hexSha(first.getPublic().getEncoded()),
                    sig),"INVALID_CANDIDATE_KEY_OR_SIGNATURE_SHAPE");
            rejected++;
            veto(()->MailboxDetachedHistoricalCandidateSignatureV1
                .verifyCandidate(historical,account,
                    first.getPublic().getEncoded(),"not-a-pin",sig),
                "INVALID_CANDIDATE_KEY_OR_SIGNATURE_SHAPE");
            rejected++;
            veto(()->MailboxDetachedHistoricalCandidateSignatureV1
                .verifyCandidate(historical,account,
                    first.getPublic().getEncoded(),
                    hexSha(first.getPublic().getEncoded()),new byte[0]),
                "INVALID_CANDIDATE_KEY_OR_SIGNATURE_SHAPE");
            rejected++;

            // An attacker who supplies the public key AND its pin can
            // sign the same history. Signature math succeeds but
            // independent provenance never becomes true.
            byte[] forged=sign(attacker.getPrivate(),historical,account);
            valid(historical,account,attacker,forged);
            coherentForgeries++;

            // Even if the attacker consistently changes BEFORE and
            // AFTER portable bytes and recomputes its SHA, the
            // original detached signature is invalid. Attacker-owned
            // new key + pin may still sign it without ANY authority.
            Field portable=field(
                MailboxWriterEvidencePortableArchiveV1.Snapshot.class,
                "portable");
            Field sha=field(
                MailboxWriterEvidencePortableArchiveV1.Snapshot.class,
                "sha256");
            String oldPortable=(String)portable.get(v1);
            String oldSha=(String)sha.get(v1);
            try{
                String alternate=oldPortable+"COHERENT_FORGERY\n";
                portable.set(v1,alternate);
                sha.set(v1,hash(alternate));
                matched(historical,current);
                veto(()->MailboxDetachedHistoricalCandidateSignatureV1
                    .verifyCandidate(historical,account,
                        first.getPublic().getEncoded(),
                        hexSha(first.getPublic().getEncoded()),sig),
                    "CANDIDATE_SIGNATURE_INVALID");
                rejected++;
                byte[] secondSignature=sign(
                    attacker.getPrivate(),historical,account);
                valid(historical,account,attacker,secondSignature);
                coherentForgeries++;
            }finally{
                portable.set(v1,oldPortable);
                sha.set(v1,oldSha);
            }
            matched(historical,current);
            // Missing fileKey is ALWAYS UNCERTAIN, including for
            // a self-matching historical report.
            Field key=field(
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .PhysicalIdentity.class,"fileKey");
            Object originalKey=key.get(all[0].accountIdentity);
            try{
                key.set(all[0].accountIdentity,null);
                veto(()->MailboxDetachedHistoricalCandidateSignatureV1
                    .verifyCandidate(historical,account,
                        first.getPublic().getEncoded(),
                        hexSha(first.getPublic().getEncoded()),sig),
                    "UNCERTAIN_OR_CHANGED_HISTORICAL_REPORT");
                rejected++;
            }finally{
                key.set(all[0].accountIdentity,originalKey);
            }
            matched(historical,current);
            valid(historical,account,first,sig);
            boolean zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            check(rejected==9&&coherentForgeries==2&&zeroLeases,
                "missing candidate-sig veto/forgery counts or leases");
            System.out.println(
                "G21157_DETACHED_CANDIDATE_DIAGNOSTICS"+
                " physicalNofollowSources=6 rejectedCases="+rejected+
                " cryptographicallyValidUntrustedForgeries="+
                coherentForgeries+" validBaseline=true"+
                " restoredHistoricalMatch=true zeroJvmLeases="+
                zeroLeases);
            System.out.println(
                "G21157_DETACHED_CANDIDATE_PASS"+
                " p256SignatureChecked=true accountDomainBound=true"+
                " candidatePublicKeySha256Checked=true"+
                " externallyTrustedAnchorProved=false"+
                " historicalSourceAuthenticated=false"+
                " rawWriterFence=false atomicEpoch=false"+
                " rollover=false pruning=false cleanup=false"+
                " unlink=false grant=false replay=false session=false"+
                " release=false ack=false");
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
