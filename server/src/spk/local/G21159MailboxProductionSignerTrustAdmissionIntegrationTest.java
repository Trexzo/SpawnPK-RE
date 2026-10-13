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
 * G21.159: production signer roots are unprovisioned; even a
 * mathematically valid and self-precommitted candidate cannot be
 * promoted. All signing keys here are generated only in this test.
 */
public final class G21159MailboxProductionSignerTrustAdmissionIntegrationTest {
    private static final String[] LEAVES={
        "g21159.properties.g21103-writer-attempt-v1-ffffffff-ffff-ffff-ffff-ffffffffffff.begin",
        "g21159.properties.g21114-writer-kind-v2-ffffffff-ffff-ffff-ffff-ffffffffffff.begin",
        "g21159.properties.g21120-commit-result-v3-ffffffff-ffff-ffff-ffff-ffffffffffff.result"
    };
    private static final String[] LABELS={
        "ACCOUNT","PREPARED_JOURNAL","COMMIT",
        "LEAF:"+LEAVES[0],"LEAF:"+LEAVES[1],"LEAF:"+LEAVES[2]
    };
    private static final long[] MAX={1048576L,1024L,1024L,512L,512L,512L};

    private static void check(boolean value,String message){
        if(!value)throw new AssertionError("G21.159 "+message);
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
        String text="G21159_TEST_ONLY_V1\n";
        return instantiate(
            MailboxWriterEvidencePortableArchiveV1.Snapshot.class,
            new Class<?>[]{String.class,String.class,
                MailboxWriterEvidencePortableArchiveV1.Verified.class},
            text,hash(text),null);
    }

    private static MailboxWriterKindEvidenceV2.Snapshot
        archiveV2()throws Exception{
        String text="G21159_TEST_ONLY_V2\n";
        return instantiate(
            MailboxWriterKindEvidenceV2.Snapshot.class,
            new Class<?>[]{String.class,String.class,
                MailboxWriterKindEvidenceV2.Report.class},
            text,hash(text),null);
    }


    /**
     * Synthetic shape-only v3 portable inventory. Physical fixture
     * source bytes are NOT authenticated by this string. Use actual
     * G21.125 KIND/LEAF/BYTES_BASE64 formatting so G21.159 can
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
        String text=fixtureV3Portable("g21159.properties",sourceBody);
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

    private static void rootVeto(
        CheckedOperation operation,String reason
    )throws Exception{
        boolean checked=false;
        try{
            operation.run();
        }catch(IOException failure){
            String message=String.valueOf(failure.getMessage());
            checked=message.contains(
                "G21.159 PRODUCTION_SIGNER_TRUST_UNSAFE_NO_AUTHORITY")&&
                message.contains(reason);
        }
        check(checked,"production trust veto missing "+reason);
    }

    private static void reviewVeto(
        CheckedOperation operation,String reason
    )throws Exception{
        boolean checked=false;
        try{
            operation.run();
        }catch(IOException failure){
            String message=String.valueOf(failure.getMessage());
            checked=message.contains("UNSAFE_NO_AUTHORITY")&&
                message.contains(reason);
        }
        check(checked,"untrusted review veto missing "+reason);
    }

    private static void review(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical,
        String account,java.security.KeyPair signer,byte[] signature,
        String expectedAccount,String proposedExpectedPin
    )throws Exception{
        String claimed=hexSha(signer.getPublic().getEncoded());
        MailboxProductionSignerTrustAdmissionV1.Review result=
            MailboxProductionSignerTrustAdmissionV1
                .reviewUntrustedCandidate(
                    historical,account,signer.getPublic().getEncoded(),
                    claimed,signature,expectedAccount,proposedExpectedPin);
        check(result.status==
            MailboxProductionSignerTrustAdmissionV1.Status
                .REVIEWED_UNTRUSTED_SIGNATURE_AND_PIN_NO_AUTHORITY&&
            result.detachedSignatureMathematicallyVerified&&
            result.callerSuppliedExpectedPinMatched&&
            !result.productionSignerRootEnrolled&&
            !result.independentlyAuthenticatedTrustAnchor&&
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
            "candidate review must never authorize production");
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21159-no-enrolled-roots-");
        try{
            BasicFileAttributes[] attrs=new BasicFileAttributes[6];
            String[] shas=new String[6];
            String[] bodies=new String[6];
            for(int i=0;i<6;i++){
                Path physicalSource=root.resolve("physical-"+i);
                String body="G21159_NOFOLLOW_SOURCE_"+i+"\n";
                bodies[i]=body;
                Files.write(physicalSource,
                    body.getBytes(StandardCharsets.US_ASCII));
                attrs[i]=Files.readAttributes(physicalSource,
                    BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                check(attrs[i].isRegularFile()&&attrs[i].fileKey()!=null,
                    "real NOFOLLOW source identity missing");
                shas[i]=hash(body);
            }
            MailboxWriterEvidencePortableArchiveV1.Snapshot v1=archiveV1();
            MailboxWriterKindEvidenceV2.Snapshot v2=archiveV2();
            MailboxFourBoundResultLeavesArchiveV1.Snapshot v3=
                archiveV3(bodies[5]);
            MailboxSevenPlaneContinuousPhysicalWitnessV1
                .Observation[] all=
                    new MailboxSevenPlaneContinuousPhysicalWitnessV1
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
            String account="g21159";
            java.security.KeyPairGenerator maker=
                java.security.KeyPairGenerator.getInstance("EC");
            maker.initialize(new java.security.spec.ECGenParameterSpec(
                "secp256r1"));
            java.security.KeyPair original=maker.generateKeyPair();
            java.security.KeyPair attacker=maker.generateKeyPair();
            byte[] originalSig=sign(
                original.getPrivate(),historical,account);
            byte[] attackerSig=sign(
                attacker.getPrivate(),historical,account);
            String originalPin=hexSha(original.getPublic().getEncoded());
            String attackerPin=hexSha(attacker.getPublic().getEncoded());
            check(MailboxProductionSignerTrustAdmissionV1
                .independentlyEnrolledProductionRoots()==0,
                "production key registry must be empty");
            review(historical,account,original,originalSig,
                account,originalPin);
            review(historical,account,attacker,attackerSig,
                account,attackerPin);

            int productionVetoes=0,reviewVetoes=0,
                signedButUntrusted=2;
            rootVeto(()->MailboxProductionSignerTrustAdmissionV1
                .requireProductionSignerRoot(
                    account,original.getPublic().getEncoded()),
                "NO_INDEPENDENTLY_ENROLLED_PRODUCTION_ROOT");
            productionVetoes++;
            rootVeto(()->MailboxProductionSignerTrustAdmissionV1
                .requireProductionSignerRoot(
                    account,attacker.getPublic().getEncoded()),
                "NO_INDEPENDENTLY_ENROLLED_PRODUCTION_ROOT");
            productionVetoes++;
            rootVeto(()->MailboxProductionSignerTrustAdmissionV1
                .requireProductionSignerRoot(
                    "other-account",original.getPublic().getEncoded()),
                "NO_INDEPENDENTLY_ENROLLED_PRODUCTION_ROOT");
            productionVetoes++;
            rootVeto(()->MailboxProductionSignerTrustAdmissionV1
                .requireProductionSignerRoot(
                    "INVALID ACCOUNT!",original.getPublic().getEncoded()),
                "INVALID_PRODUCTION_ACCOUNT_SCOPE");
            productionVetoes++;
            rootVeto(()->MailboxProductionSignerTrustAdmissionV1
                .requireProductionSignerRoot(account,null),
                "INVALID_PRODUCTION_SIGNER_CANDIDATE");
            productionVetoes++;
            rootVeto(()->MailboxProductionSignerTrustAdmissionV1
                .requireProductionSignerRoot(account,new byte[2]),
                "INVALID_PRODUCTION_SIGNER_CANDIDATE");
            productionVetoes++;
            rootVeto(()->MailboxProductionSignerTrustAdmissionV1
                .requireProductionSignerRoot(account,new byte[257]),
                "INVALID_PRODUCTION_SIGNER_CANDIDATE");
            productionVetoes++;

            reviewVeto(()->MailboxProductionSignerTrustAdmissionV1
                .reviewUntrustedCandidate(
                    historical,account,attacker.getPublic().getEncoded(),
                    attackerPin,attackerSig,account,originalPin),
                "CANDIDATE_KEY_NOT_PRECOMMITTED");
            reviewVetoes++;
            reviewVeto(()->MailboxProductionSignerTrustAdmissionV1
                .reviewUntrustedCandidate(
                    historical,account,original.getPublic().getEncoded(),
                    originalPin,attackerSig,account,originalPin),
                "CANDIDATE_SIGNATURE_INVALID");
            reviewVetoes++;
            reviewVeto(()->MailboxProductionSignerTrustAdmissionV1
                .reviewUntrustedCandidate(
                    historical,"INVALID ACCOUNT!",
                    original.getPublic().getEncoded(),originalPin,
                    originalSig,account,originalPin),
                "INVALID_PRODUCTION_ACCOUNT_SCOPE");
            reviewVetoes++;

            // Coherent post-lease report forgery self-signed by an
            // attacker passes only the untrusted REVIEW boundary.
            // It never alters the empty production root registry.
            Field portable=field(
                MailboxWriterEvidencePortableArchiveV1.Snapshot.class,
                "portable");
            Field hashField=field(
                MailboxWriterEvidencePortableArchiveV1.Snapshot.class,
                "sha256");
            String oldPortable=(String)portable.get(v1);
            String oldSha=(String)hashField.get(v1);
            try{
                String forged=oldPortable+"G21159_FORGED_HISTORY\n";
                portable.set(v1,forged);
                hashField.set(v1,hash(forged));
                matched(historical,current);
                reviewVeto(()->MailboxProductionSignerTrustAdmissionV1
                    .reviewUntrustedCandidate(
                        historical,account,original.getPublic()
                            .getEncoded(),originalPin,originalSig,
                        account,originalPin),
                    "CANDIDATE_SIGNATURE_INVALID");
                reviewVetoes++;
                byte[] newForgerySignature=sign(
                    attacker.getPrivate(),historical,account);
                review(historical,account,attacker,
                    newForgerySignature,account,attackerPin);
                signedButUntrusted++;
                rootVeto(()->MailboxProductionSignerTrustAdmissionV1
                    .requireProductionSignerRoot(account,
                        attacker.getPublic().getEncoded()),
                    "NO_INDEPENDENTLY_ENROLLED_PRODUCTION_ROOT");
                productionVetoes++;
            }finally{
                portable.set(v1,oldPortable);
                hashField.set(v1,oldSha);
            }
            matched(historical,current);

            Field key=field(
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .PhysicalIdentity.class,"fileKey");
            Object oldKey=key.get(all[0].accountIdentity);
            try{
                key.set(all[0].accountIdentity,null);
                reviewVeto(()->MailboxProductionSignerTrustAdmissionV1
                    .reviewUntrustedCandidate(
                        historical,account,original.getPublic()
                            .getEncoded(),originalPin,originalSig,
                        account,originalPin),
                    "UNCERTAIN_OR_CHANGED_HISTORICAL_REPORT");
                reviewVetoes++;
            }finally{
                key.set(all[0].accountIdentity,oldKey);
            }
            matched(historical,current);
            review(historical,account,original,originalSig,
                account,originalPin);
            boolean noLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            check(productionVetoes==8&&reviewVetoes==5&&
                signedButUntrusted==3&&
                MailboxProductionSignerTrustAdmissionV1
                    .independentlyEnrolledProductionRoots()==0&&noLeases,
                "root/review/forgery case counts or lease leak");
            System.out.println(
                "G21159_DENY_UNENROLLED_ROOT_DIAGNOSTICS"+
                " realNofollowSources=6 productionTrustRejections="+
                productionVetoes+" reviewRejections="+reviewVetoes+
                " mathematicallyValidUntrustedCandidates="+
                signedButUntrusted+
                " enrolledProductionRoots=0 restoredHealthyMatch=true"+
                " zeroJvmLeases="+noLeases);
            System.out.println(
                "G21159_DENY_UNENROLLED_ROOT_PASS"+
                " productionRootRegistryEmpty=true"+
                " runtimeRootEnrollment=false"+
                " attackerSignedHistoryNotAdmitted=true"+
                " independentProvenance=false rawWriterFence=false"+
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
