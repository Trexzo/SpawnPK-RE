package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * G21.157: purely in-memory detached ECDSA verification against an
 * UNTRUSTED caller-supplied candidate public key and its fingerprint.
 *
 * This is NOT an authenticated external pin, trust anchor, signer
 * enrollment, key store, original historical custody evidence,
 * old/RAW/future writer exclusion, power-loss-safe epoch or authority.
 * The caller can generate BOTH key and signature: even a valid result
 * MUST NEVER be passed into a cleanup/grant/settlement decision.
 */
final class MailboxDetachedHistoricalCandidateSignatureV1 {
    private static final String DOMAIN=
        "SPK-G21157-HISTORICAL-DETACHED-CANDIDATE-V1";
    private static final String CURVE_ORDER=
        "ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551";

    enum Status {
        VALID_SIGNATURE_UNTRUSTED_KEY_NO_AUTHORITY
    }

    static final class Report {
        final Status status=
            Status.VALID_SIGNATURE_UNTRUSTED_KEY_NO_AUTHORITY;
        final boolean candidateSignatureMathematicallyVerified=true;
        final boolean independentTrustAnchorAuthenticated=false;
        final boolean historicalSourceAuthenticated=false;
        final boolean originalFileKeyIdentityAuthenticated=false;
        final boolean historicalWriterCensusProved=false;
        final boolean rawOrDefaultWriterExclusionProved=false;
        final boolean futureWriterFenceProved=false;
        final boolean atomicRecoveryEpochProved=false;
        final boolean hardwarePowerLossProved=false;
        final boolean cleanupAuthorized=false;
        final boolean unlinkAuthorized=false;
        final boolean pruningAuthorized=false;
        final boolean rolloverAuthorized=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean sessionAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;
        private Report(){}
    }

    private static IOException veto(String why){
        return new IOException(
            "G21.157 CANDIDATE_SIGNATURE_UNSAFE_NO_AUTHORITY "+
            why+" trust=false epoch=false cleanup=false grant=false");
    }

    private static byte[] sha256(byte[] source)throws IOException{
        try{
            return MessageDigest.getInstance("SHA-256").digest(source);
        }catch(GeneralSecurityException unavailable){
            throw veto("SHA256_UNAVAILABLE");
        }
    }

    private static String hex(byte[] bytes){
        char[] digit="0123456789abcdef".toCharArray();
        StringBuilder result=new StringBuilder(bytes.length*2);
        for(byte b:bytes){
            result.append(digit[(b>>>4)&15]);
            result.append(digit[b&15]);
        }
        return result.toString();
    }

    private static void physical(DataOutputStream out,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.PhysicalIdentity p
    )throws IOException{
        // Opaque OS fileKey has no stable cross-process wire format:
        // deliberately NOT in this portable digest. It is still
        // required to be present, and missing keys veto early.
        out.writeUTF(p.sha256);
        out.writeLong(p.size);
        out.writeUTF(p.created.toString());
        out.writeUTF(p.modified.toString());
    }

    private static void capture(DataOutputStream out,String marker,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation o
    )throws IOException{
        out.writeUTF(marker);
        out.writeUTF(o.accountSha);
        out.writeUTF(o.journalSha);
        out.writeUTF(o.commitSha);
        physical(out,o.accountIdentity);
        physical(out,o.journalIdentity);
        physical(out,o.commitIdentity);
        out.writeUTF(o.v1.sha256);
        out.writeUTF(o.v1.portable);
        out.writeUTF(o.v2.sha256);
        out.writeUTF(o.v2.portable);
        out.writeUTF(o.v3.sha256);
        out.writeUTF(o.v3.portable);
        TreeMap<String,MailboxSevenPlaneContinuousPhysicalWitnessV1
            .PhysicalIdentity> sorted=
            new TreeMap<>(o.evidenceLeafIdentities);
        out.writeInt(sorted.size());
        for(Map.Entry<String,MailboxSevenPlaneContinuousPhysicalWitnessV1
                .PhysicalIdentity> leaf:sorted.entrySet()){
            out.writeUTF(leaf.getKey());
            physical(out,leaf.getValue());
        }
    }

    /**
     * A candidate domain-bound deterministic digest message, NOT a
     * trust anchor or an independent signing channel. Package-private
     * only for synthetic detached-signature regression fixtures.
     */
    static byte[] candidateMessage(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical,
        String account
    )throws IOException{
        if(account==null||!account.matches("[a-z0-9_-]{1,64}"))
            throw veto("BAD_ACCOUNT_CONTEXT");
        if(historical==null)
            throw veto("MISSING_HISTORICAL_REPORT");
        MailboxSevenPlaneHistoricalIdentityDeltaV1.Report preflight=
            MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                historical,historical);
        if(preflight.status!=
                MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                    .MATCHED_OBSERVED_METADATA_NO_AUTHORITY||
           !preflight.changedSources.isEmpty()||
           !preflight.uncertainSources.isEmpty())
            throw veto("UNCERTAIN_OR_CHANGED_HISTORICAL_REPORT");
        final String accountField="\nACCOUNT="+account+"\n";
        if(!historical.before.v3.portable.contains(accountField)||
           !historical.after.v3.portable.contains(accountField))
            throw veto("HISTORICAL_ACCOUNT_CONTEXT_MISMATCH");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)){
            out.writeUTF(DOMAIN);
            out.writeUTF(account);
            capture(out,"BEFORE",historical.before);
            capture(out,"AFTER",historical.after);
            out.flush();
        }
        if(bytes.size()>262144)
            throw veto("CANONICAL_RECORD_TOO_LARGE");
        String message=DOMAIN+"\nACCOUNT="+account+
            "\nCANDIDATE_SHA256="+hex(sha256(bytes.toByteArray()))+"\n";
        return message.getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * ECDSA verification with a key and SHA-256 fingerprint BOTH
     * supplied by the caller. A caller-generated key can always
     * self-certify a forged snapshot; success is NO AUTHORITY.
     */
    static Report verifyCandidate(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical,
        String account,byte[] publicKeyDer,
        String candidateKeyFingerprint,byte[] signatureDer
    )throws IOException{
        if(publicKeyDer==null||publicKeyDer.length<65||
           publicKeyDer.length>256||candidateKeyFingerprint==null||
           !candidateKeyFingerprint.matches("[0-9a-f]{64}")||
           signatureDer==null||signatureDer.length<64||
           signatureDer.length>80)
            throw veto("INVALID_CANDIDATE_KEY_OR_SIGNATURE_SHAPE");
        if(!hex(sha256(publicKeyDer)).equals(candidateKeyFingerprint))
            throw veto("CANDIDATE_KEY_FINGERPRINT_MISMATCH");
        final byte[] payload=candidateMessage(historical,account);
        try{
            PublicKey key=KeyFactory.getInstance("EC").generatePublic(
                new X509EncodedKeySpec(
                    Arrays.copyOf(publicKeyDer,publicKeyDer.length)));
            if(!(key instanceof ECPublicKey))
                throw veto("NOT_P256_CANDIDATE_KEY");
            ECPublicKey ec=(ECPublicKey)key;
            if(ec.getParams().getCurve().getField().getFieldSize()!=256||
               !CURVE_ORDER.equals(
                   ec.getParams().getOrder().toString(16)))
                throw veto("NOT_P256_CANDIDATE_KEY");
            Signature verifier=Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(key);
            verifier.update(payload);
            if(!verifier.verify(signatureDer))
                throw veto("CANDIDATE_SIGNATURE_INVALID");
        }catch(GeneralSecurityException invalid){
            throw veto("BAD_CANDIDATE_SIGNATURE_OR_KEY");
        }
        // No external trust anchor is provisioned by this method.
        // Signature validity is NOT evidence of independent origin.
        return new Report();
    }

    private MailboxDetachedHistoricalCandidateSignatureV1(){}
}
