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
 * G21.158: caller-supplied expected signer pin can reject a swapped
 * candidate key, yet is NOT an independently trusted public key.
 * All keys here are test-generated and never deployed.
 */
public final class G21158MailboxPrecommittedSignerPinIntegrationTest {
    private static final String[] LEAVES={
        "g21158.properties.g21103-writer-attempt-v1-ffffffff-ffff-ffff-ffff-ffffffffffff.begin",
        "g21158.properties.g21114-writer-kind-v2-ffffffff-ffff-ffff-ffff-ffffffffffff.begin",
        "g21158.properties.g21120-commit-result-v3-ffffffff-ffff-ffff-ffff-ffffffffffff.result"
    };
    private static final String[] LABELS={
        "ACCOUNT","PREPARED_JOURNAL","COMMIT",
        "LEAF:"+LEAVES[0],"LEAF:"+LEAVES[1],"LEAF:"+LEAVES[2]
    };
    private static final long[] MAX={1048576L,1024L,1024L,512L,512L,512L};

    private static void check(boolean value,String message){
        if(!value)throw new AssertionError("G21.158 "+message);
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
        String text="G21158_TEST_ONLY_V1\n";
        return instantiate(
            MailboxWriterEvidencePortableArchiveV1.Snapshot.class,
            new Class<?>[]{String.class,String.class,
                MailboxWriterEvidencePortableArchiveV1.Verified.class},
            text,hash(text),null);
    }

    private static MailboxWriterKindEvidenceV2.Snapshot
        archiveV2()throws Exception{
        String text="G21158_TEST_ONLY_V2\n";
        return instantiate(
            MailboxWriterKindEvidenceV2.Snapshot.class,
            new Class<?>[]{String.class,String.class,
                MailboxWriterKindEvidenceV2.Report.class},
            text,hash(text),null);
    }


    /**
     * Synthetic shape-only v3 portable inventory. Physical fixture
     * source bytes are NOT authenticated by this string. Use actual
     * G21.125 KIND/LEAF/BYTES_BASE64 formatting so G21.158 can
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
        String text=fixtureV3Portable("g21158.properties",sourceBody);
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

    private static MailboxPrecommittedCandidateSignerPinV1.Report
        verify(
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report history,
            String account,java.security.KeyPair key,
            byte[] signature,String pinnedAccount,String expectedPin
        )throws Exception{
        return MailboxPrecommittedCandidateSignerPinV1
            .verifyAgainstSeparatePrecommitment(
                history,account,key.getPublic().getEncoded(),
                hexSha(key.getPublic().getEncoded()),signature,
                pinnedAccount,expectedPin);
    }

    private static void matchedPrecommitment(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report history,
        String account,java.security.KeyPair candidate,
        byte[] signature,String pinnedAccount,String expectedPin
    )throws Exception{
        MailboxPrecommittedCandidateSignerPinV1.Report observed=
            verify(history,account,candidate,signature,
                pinnedAccount,expectedPin);
        check(observed.status==
            MailboxPrecommittedCandidateSignerPinV1.Status
                .MATCHED_UNTRUSTED_PRECOMMITMENT_NO_AUTHORITY&&
            observed.candidatePublicKeyMatchedPrecommitment&&
            observed.detachedCandidateSignatureMathematicallyVerified&&
            !observed.precommitmentIndependentlyProvisioned&&
            !observed.externalSignerTrustAuthenticated&&
            !observed.historicalEvidenceSourceAuthenticated&&
            !observed.originalFileKeyIdentityAuthenticated&&
            !observed.historicalWriterCensusProved&&
            !observed.rawOrDefaultWriterExclusionProved&&
            !observed.futureWriterFenceProved&&
            !observed.atomicRecoveryEpochProved&&
            !observed.hardwarePowerLossProved&&
            !observed.cleanupAuthorized&&!observed.unlinkAuthorized&&
            !observed.pruningAuthorized&&!observed.rolloverAuthorized&&
            !observed.grantAuthorized&&!observed.replayAuthorized&&
            !observed.sessionAuthorized&&!observed.releaseAuthorized&&
            !observed.clientAckAuthorized,
            "a matched candidate pin must not grant trust/authority");
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21158-precommit-");
        try{
            BasicFileAttributes[] attrs=new BasicFileAttributes[6];
            String[] shas=new String[6];
            String[] sourceBodies=new String[6];
            for(int i=0;i<6;i++){
                Path file=root.resolve("physical-"+i);
                String body="G21158_NOFOLLOW_SOURCE_"+i+"\n";
                sourceBodies[i]=body;
                Files.write(file,
                    body.getBytes(StandardCharsets.US_ASCII));
                attrs[i]=Files.readAttributes(file,
                    BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                check(attrs[i].isRegularFile()&&attrs[i].fileKey()!=null,
                    "NOFOLLOW original source identity unavailable");
                shas[i]=hash(body);
            }
            MailboxWriterEvidencePortableArchiveV1.Snapshot v1=archiveV1();
            MailboxWriterKindEvidenceV2.Snapshot v2=archiveV2();
            MailboxFourBoundResultLeavesArchiveV1.Snapshot v3=
                archiveV3(sourceBodies[5]);
            MailboxSevenPlaneContinuousPhysicalWitnessV1
                .Observation[] all=new
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .Observation[4];
            for(int i=0;i<4;i++){
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .PhysicalIdentity[] ids=new
                    MailboxSevenPlaneContinuousPhysicalWitnessV1
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
            String account="g21158";
            java.security.KeyPairGenerator gen=
                java.security.KeyPairGenerator.getInstance("EC");
            gen.initialize(new java.security.spec.ECGenParameterSpec(
                "secp256r1"));
            java.security.KeyPair owner=gen.generateKeyPair();
            java.security.KeyPair attacker=gen.generateKeyPair();
            String ownerPin=hexSha(owner.getPublic().getEncoded());
            String attackerPin=hexSha(attacker.getPublic().getEncoded());
            byte[] ownerSig=sign(owner.getPrivate(),historical,account);
            byte[] attackerSig=sign(
                attacker.getPrivate(),historical,account);
            matchedPrecommitment(historical,account,owner,ownerSig,
                account,ownerPin);

            int rejected=0,forgedNoAuthority=0;
            // A second signer can self-sign an exact same history,
            // but must be vetoed against an independently specified
            // expected owner fingerprint.
            veto(()->verify(historical,account,attacker,attackerSig,
                account,ownerPin),"CANDIDATE_KEY_NOT_PRECOMMITTED");
            rejected++;
            veto(()->verify(historical,account,owner,ownerSig,
                "wrong-account",ownerPin),
                "PRECOMMITMENT_ACCOUNT_SCOPE_MISMATCH");
            rejected++;
            veto(()->verify(historical,account,owner,ownerSig,
                account,"not-a-fingerprint"),
                "MISSING_OR_MALFORMED_PRECOMMITTED_FINGERPRINT");
            rejected++;
            veto(()->verify(historical,account,owner,ownerSig,
                account,null),
                "MISSING_OR_MALFORMED_PRECOMMITTED_FINGERPRINT");
            rejected++;
            veto(()->verify(historical,account,owner,ownerSig,
                account,ownerPin.toUpperCase(java.util.Locale.ROOT)),
                "MISSING_OR_MALFORMED_PRECOMMITTED_FINGERPRINT");
            rejected++;
            veto(()->MailboxPrecommittedCandidateSignerPinV1
                .verifyAgainstSeparatePrecommitment(
                    historical,account,owner.getPublic().getEncoded(),
                    "bad",ownerSig,account,ownerPin),
                "MALFORMED_CANDIDATE_FINGERPRINT");
            rejected++;
            veto(()->verify(historical,account,owner,ownerSig,
                "INVALID ACCOUNT!",ownerPin),"INVALID_ACCOUNT_SCOPE");
            rejected++;
            veto(()->MailboxPrecommittedCandidateSignerPinV1
                .verifyAgainstSeparatePrecommitment(
                    historical,account,attacker.getPublic().getEncoded(),
                    ownerPin,attackerSig,account,ownerPin),
                "CANDIDATE_KEY_FINGERPRINT_MISMATCH");
            rejected++;
            veto(()->verify(historical,account,owner,attackerSig,
                account,ownerPin),"CANDIDATE_SIGNATURE_INVALID");
            rejected++;
            veto(()->verify(historical,account,owner,new byte[0],
                account,ownerPin),
                "INVALID_CANDIDATE_KEY_OR_SIGNATURE_SHAPE");
            rejected++;

            // Proof of the residual limitation: if the attacker also
            // supplies THEIR expected precommitment, the signature
            // remains mathematically valid but no trust is gained.
            matchedPrecommitment(historical,account,attacker,attackerSig,
                account,attackerPin);
            forgedNoAuthority++;

            // Coherent historical content substitutions that still
            // pass the old report metadata checks cannot validate
            // against a previously signed unchanged message.
            Field portable=field(
                MailboxWriterEvidencePortableArchiveV1.Snapshot.class,
                "portable");
            Field sha=field(
                MailboxWriterEvidencePortableArchiveV1.Snapshot.class,
                "sha256");
            String originalPortable=(String)portable.get(v1);
            String originalSha=(String)sha.get(v1);
            try{
                String alternate=originalPortable+"G21158_FORGERY\n";
                portable.set(v1,alternate);
                sha.set(v1,hash(alternate));
                matched(historical,current);
                veto(()->verify(historical,account,owner,ownerSig,
                    account,ownerPin),"CANDIDATE_SIGNATURE_INVALID");
                rejected++;
                byte[] resigned=sign(
                    attacker.getPrivate(),historical,account);
                veto(()->verify(historical,account,attacker,resigned,
                    account,ownerPin),"CANDIDATE_KEY_NOT_PRECOMMITTED");
                rejected++;
                matchedPrecommitment(historical,account,attacker,
                    resigned,account,attackerPin);
                forgedNoAuthority++;
            }finally{
                portable.set(v1,originalPortable);
                sha.set(v1,originalSha);
            }
            matched(historical,current);

            // File-key uncertainty remains a veto despite a self
            // consistent retained map and correctly pinned signature.
            Field key=field(
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .PhysicalIdentity.class,"fileKey");
            Object originalKey=key.get(all[0].accountIdentity);
            try{
                key.set(all[0].accountIdentity,null);
                veto(()->verify(historical,account,owner,ownerSig,
                    account,ownerPin),
                    "UNCERTAIN_OR_CHANGED_HISTORICAL_REPORT");
                rejected++;
            }finally{
                key.set(all[0].accountIdentity,originalKey);
            }
            matched(historical,current);
            matchedPrecommitment(historical,account,owner,ownerSig,
                account,ownerPin);
            boolean zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            check(rejected==13&&forgedNoAuthority==2&&zeroLeases,
                "wrong rejection/forgery counts or JVM lease leak");
            System.out.println("G21158_SIGNER_PRECOMMITMENT_DIAGNOSTICS"+
                " physicalNofollowSources=6 rejectedCases="+rejected+
                " validUntrustedForgeries="+forgedNoAuthority+
                " ownerPinRejectsSubstitutedSigner=true"+
                " healthyMatchedReportRestored=true"+
                " zeroJvmLeases="+zeroLeases);
            System.out.println("G21158_SIGNER_PRECOMMITMENT_PASS"+
                " separateExpectedSignerFingerprintRequired=true"+
                " accountScopedExpectedFingerprint=true"+
                " independentlyProvisionedAnchor=false"+
                " historicalSourceAuthenticated=false"+
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
