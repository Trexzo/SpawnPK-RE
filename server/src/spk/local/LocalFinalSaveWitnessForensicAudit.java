package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

/**
 * G21.323-326: independent, bounded, NOFOLLOW forensic inspection of
 * ONE selected G21.298 final-review witness. Never admitted, trusted,
 * deleted, authenticated, repaired, replayed or interpreted as an ACK.
 *
 * This is NOT a coherent multi-leaf filesystem snapshot; foreign RAW
 * writers do not participate in this read-only scan.
 */
final class LocalFinalSaveWitnessForensicAudit {
    private static final int MAX_BYTES=4096;
    enum State {
        ABSENT_UNPROVEN,
        NONREGULAR_NEGATIVE,
        UNIDENTIFIED_NEGATIVE,
        OVERSIZE_NEGATIVE,
        MULTIPLE_LINKS_NEGATIVE,
        MALFORMED_NEGATIVE,
        WRONG_ACCOUNT_NEGATIVE,
        ACTIVE_MISSING_NEGATIVE,
        SOURCE_MISMATCH_NEGATIVE,
        SOURCE_LINK_COUNT_UNPROVEN_NEGATIVE,
        ACTIVE_LINK_COUNT_UNPROVEN_NEGATIVE,
        ACTIVE_INVALID_NEGATIVE,
        MATCHES_UNTRUSTED_NEGATIVE
    }
    static final class Evidence {
        final State state;
        final String witnessSha256;
        final String untrustedGeneration;
        final String untrustedSaveSequence;
        Evidence(State s,String sha,String gen,String seq){
            state=s;
            witnessSha256=sha;
            untrustedGeneration=gen;
            untrustedSaveSequence=seq;
        }
        String report(){
            return "G21323_READ_ONLY_FINAL_WITNESS_AUDIT"+
                " state="+state+
                " witnessSha256="+witnessSha256+
                " untrustedGeneration="+untrustedGeneration+
                " untrustedSaveSequence="+untrustedSaveSequence+
                " isAtomicSnapshot=false"+
                " loginAuthorized=false"+
                " recoveryAuthorized=false"+
                " finalSaveAck=false"+
                " ownerEpochProven=false"+
                " grantAuthorized=false"+
                " safeToReplay=false";
        }
    }
    @FunctionalInterface interface AfterFirstReadProbe {
        void run(Path witness)throws IOException;
    }
    private LocalFinalSaveWitnessForensicAudit(){}

    static Evidence inspect(Path account,String username)throws IOException{
        return inspect(account,username,witness->{});
    }
    private static Evidence evidence(State state,String sha){
        return new Evidence(state,sha,"UNPROVEN","UNPROVEN");
    }

    static Evidence inspect(Path account,String username,
                            AfterFirstReadProbe probe)throws IOException{
        Objects.requireNonNull(probe,"probe");
        if(account==null||username==null||
           !username.matches("[a-z0-9_-]{1,64}"))
            throw new IOException("G21.323 INVALID_EXACT_AUDIT_SELECTION");
        Path selected=account.toAbsolutePath().normalize();
        Path witness=LocalFinalSaveNegativeWitness.witnessPath(selected);
        // Unlike the publication coordinator, read-only audit must
        // never CREATE a lock or parent. Reject unsafe ancestry.
        MailboxAccountPublicationCoordinator
            .requireSafeWorldTempAncestry(selected,false);
        final BasicFileAttributes before;
        try{
            before=Files.readAttributes(witness,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        }catch(NoSuchFileException absent){
            return evidence(State.ABSENT_UNPROVEN,"ABSENT");
        }
        if(!before.isRegularFile()||before.isSymbolicLink())
            return evidence(State.NONREGULAR_NEGATIVE,"OPAQUE");
        if(before.fileKey()==null)
            return evidence(State.UNIDENTIFIED_NEGATIVE,"OPAQUE");
        if(before.size()>MAX_BYTES)
            return evidence(State.OVERSIZE_NEGATIVE,"OPAQUE");
        if(!singleObservedHardlink(witness))
            return evidence(State.MULTIPLE_LINKS_NEGATIVE,"OPAQUE");

        byte[] first=readBounded(witness);
        probe.run(witness);
        // Two independent reads catch same-inode byte changes even
        // when a writer restores size/mtime. Still not a signed receipt.
        byte[] second=readBounded(witness);
        BasicFileAttributes after=Files.readAttributes(witness,
            BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!after.isRegularFile()||after.isSymbolicLink()||
           after.fileKey()==null||
           !before.fileKey().equals(after.fileKey())||
           before.size()!=after.size()||
           !before.lastModifiedTime().equals(after.lastModifiedTime())||
           !Arrays.equals(first,second)||
           !singleObservedHardlink(witness))
            throw new IOException(
                "G21.326 FINAL_WITNESS_CHANGED_DURING_FORENSIC_READ");
        final String digest="SHA256:"+sha256(first);
        if(first.length==0||first.length>MAX_BYTES)
            return evidence(State.MALFORMED_NEGATIVE,digest);
        for(byte b:first){
            // This V1 record is strictly ASCII, not best-effort
            // replacement-decoded UTF-8.
            if((b&255)>127||b==0)
                return evidence(State.MALFORMED_NEGATIVE,digest);
        }
        String body=new String(first,StandardCharsets.US_ASCII);
        String[] fields=body.split("\\n",-1);
        if(fields.length!=11||
           !"G21298_NEGATIVE_FINAL_REVIEW_V1".equals(fields[0])||
           !"state=UNTRUSTED_OPERATOR_REVIEW_ONLY".equals(fields[6])||
           !"recoveryAuthorized=false".equals(fields[7])||
           !"ownerEpochProven=false".equals(fields[8])||
           !"finalSaveAck=false".equals(fields[9])||
           !fields[10].isEmpty()||
           !fields[2].startsWith("worldGeneration=")||
           !fields[3].startsWith("worldSaveSequence=")||
           !fields[4].matches("accountSha256=[0-9a-f]{64}")||
           !fields[5].matches("activeMarkerWitness=SHA256:[0-9a-f]{64}"))
            return evidence(State.MALFORMED_NEGATIVE,digest);
        final String gen=fields[2].substring("worldGeneration=".length());
        final String seq=fields[3].substring("worldSaveSequence=".length());
        if(!positiveLong(gen)||!positiveLong(seq))
            return evidence(State.MALFORMED_NEGATIVE,digest);
        if(!("account="+username).equals(fields[1]))
            return new Evidence(State.WRONG_ACCOUNT_NEGATIVE,digest,gen,seq);

        LocalAccountForensicEvidence.Evidence current=
            LocalAccountForensicEvidence.inspect(selected);
        if(!"PRESENT_REGULAR".equals(current.accountState)||
           !fields[4].equals("accountSha256="+current.accountSha256))
            return new Evidence(
                State.SOURCE_MISMATCH_NEGATIVE,digest,gen,seq);
        // G21.327: a matching account digest cannot prove single
        // observed inode ownership after new hardlink names appear.
        if(!"1".equals(current.accountObservedLinkCount))
            return new Evidence(
                State.SOURCE_LINK_COUNT_UNPROVEN_NEGATIVE,
                digest,gen,seq);
        String marker=current.negativeSidecarContentWitnesses
            .get("activeReview");
        if("ABSENT".equals(marker))
            return new Evidence(
                State.ACTIVE_MISSING_NEGATIVE,digest,gen,seq);
        if(!fields[5].equals("activeMarkerWitness="+marker))
            return new Evidence(
                State.SOURCE_MISMATCH_NEGATIVE,digest,gen,seq);
        // G21.328: ACTIVE may have another hardlink even when
        // its fingerprint matches the original forced record.
        Path active=LocalDurableAccountReviewIntentPrimitive
            .markerPath(selected);
        if(!singleObservedHardlink(active))
            return new Evidence(
                State.ACTIVE_LINK_COUNT_UNPROVEN_NEGATIVE,
                digest,gen,seq);
        try{
            LocalDurableAccountReviewIntentPrimitive
                .requireReviewMarker(selected);
        }catch(IOException malformed){
            return new Evidence(
                State.ACTIVE_INVALID_NEGATIVE,digest,gen,seq);
        }
        // A perfectly matching, canonical V1 record is STILL
        // untrusted negative operator evidence. No positive return.
        return new Evidence(
            State.MATCHES_UNTRUSTED_NEGATIVE,digest,gen,seq);
    }

    private static boolean positiveLong(String value){
        try{
            return Long.parseLong(value)>0L&&
                !value.startsWith("+")&&
                !value.startsWith("0");
        }catch(NumberFormatException invalid){
            return false;
        }
    }
    private static boolean singleObservedHardlink(Path path)
        throws IOException{
        final Object count;
        try{
            count=Files.getAttribute(path,"unix:nlink",
                LinkOption.NOFOLLOW_LINKS);
        }catch(UnsupportedOperationException|IllegalArgumentException e){
            return false; // UNKNOWN, never imply one original owner
        }
        return count instanceof Number&&
            ((Number)count).longValue()==1L;
    }
    private static byte[] readBounded(Path path)throws IOException{
        ByteBuffer bytes=ByteBuffer.allocate(MAX_BYTES+1);
        try(FileChannel input=FileChannel.open(path,
                StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            while(bytes.hasRemaining()){
                int n=input.read(bytes);
                if(n<0)break;
                if(n==0)
                    throw new IOException(
                        "G21.326 FINAL_WITNESS_FORENSIC_READ_NO_PROGRESS");
            }
        }
        return Arrays.copyOf(bytes.array(),bytes.position());
    }
    private static String sha256(byte[] data)throws IOException{
        try{
            byte[] hash=MessageDigest.getInstance("SHA-256")
                .digest(data);
            StringBuilder b=new StringBuilder(64);
            for(byte v:hash){
                String part=Integer.toHexString(v&255);
                if(part.length()==1)b.append('0');
                b.append(part);
            }
            return b.toString();
        }catch(NoSuchAlgorithmException missing){
            throw new IOException("G21.323 SHA256_UNAVAILABLE",missing);
        }
    }
}
