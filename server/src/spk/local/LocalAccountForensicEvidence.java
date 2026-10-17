package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * G21.279: READ-ONLY operator evidence for one explicitly selected
 * account leaf and five known negative sidecar names.
 *
 * This is NOT a login/admission/owner/recovery decision. In particular,
 * absence of a known marker is not evidence of a crash-safe final save,
 * a valid account owner epoch, or an authorized mailbox grant.
 * Does not create an OS lock file, marker, directory or account.
 */
public final class LocalAccountForensicEvidence {
    private static final long MAX_ACCOUNT_BYTES=16L*1024L*1024L;
    private LocalAccountForensicEvidence(){}

    static final class Evidence {
        final String accountState;
        final String accountSha256;
        final String accountHardlinkState;
        final String accountObservedLinkCount;
        final String finalReviewWitnessState;
        final String finalReviewWitnessContent;
        final Map<String,String> negativeSidecars;
        final Map<String,String> negativeSidecarContentWitnesses;
        final int observedNegativeNames;

        Evidence(String state,String sha,String links,String linkCount,
                 Map<String,String> names,Map<String,String> witnesses,
                 int count,String finalState,String finalContent){
            accountState=state;
            accountSha256=sha;
            accountHardlinkState=links;
            accountObservedLinkCount=linkCount;
            finalReviewWitnessState=finalState;
            finalReviewWitnessContent=finalContent;
            negativeSidecars=Collections.unmodifiableMap(
                new LinkedHashMap<>(names));
            negativeSidecarContentWitnesses=Collections.unmodifiableMap(
                new LinkedHashMap<>(witnesses));
            observedNegativeNames=count;
        }

        String report(){
            StringBuilder b=new StringBuilder(
                "G21279_READ_ONLY_ACCOUNT_FORENSIC_EVIDENCE "+
                "account="+accountState+
                " accountSha256="+accountSha256+
                " accountHardlinks="+accountHardlinkState+
                " accountObservedLinkCount="+accountObservedLinkCount+
                " knownNegativeNames="+observedNegativeNames+
                " finalReviewWitness="+finalReviewWitnessState+
                " finalReviewWitnessContent="+finalReviewWitnessContent);
            for(Map.Entry<String,String> entry:negativeSidecars.entrySet())
                b.append(' ').append(entry.getKey()).append('=')
                    .append(entry.getValue());
            // G21.291: these bounded, read-only digests help an
            // operator compare separate reports. They do NOT attest
            // to ownership, durable saving, atomicity, or recovery.
            for(Map.Entry<String,String> entry:
                    negativeSidecarContentWitnesses.entrySet())
                b.append(' ').append(entry.getKey()).append("Content=")
                    .append(entry.getValue());
            b.append(" recoveryAuthorized=false")
                .append(" loginAuthorized=false")
                .append(" grantAuthorized=false")
                .append(" finalSaveAck=false")
                .append(" ownerEpochProven=false")
                .append(" noKnownNamesIsNotSafety=true");
            return b.toString();
        }
    }

    private static final class Stat {
        final BasicFileAttributes attributes;
        final String classification;

        Stat(BasicFileAttributes attrs,String kind){
            attributes=attrs;
            classification=kind;
        }
    }

    private static Stat observe(Path path)throws IOException{
        final BasicFileAttributes a;
        try{
            a=Files.readAttributes(path,BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        }catch(NoSuchFileException absent){
            return new Stat(null,"ABSENT");
        }
        return new Stat(a,a.isRegularFile()&&!a.isSymbolicLink()
            ?"PRESENT_REGULAR_NEGATIVE":"PRESENT_NONREGULAR_NEGATIVE");
    }

    private static boolean same(Stat before,Stat after){
        if(!before.classification.equals(after.classification))
            return false;
        if(before.attributes==null||after.attributes==null)
            return before.attributes==null&&after.attributes==null;
        BasicFileAttributes a=before.attributes,b=after.attributes;
        return a.isRegularFile()==b.isRegularFile()&&
            a.isSymbolicLink()==b.isSymbolicLink()&&
            Objects.equals(a.fileKey(),b.fileKey())&&
            Objects.equals(a.creationTime(),b.creationTime())&&
            Objects.equals(a.lastModifiedTime(),b.lastModifiedTime())&&
            a.size()==b.size();
    }

    private static MessageDigest digest(){
        try{
            return MessageDigest.getInstance("SHA-256");
        }catch(NoSuchAlgorithmException impossible){
            throw new IllegalStateException("SHA-256 unavailable",impossible);
        }
    }

    private static String hex(byte[] bytes){
        final char[] alphabet="0123456789abcdef".toCharArray();
        StringBuilder out=new StringBuilder(bytes.length*2);
        for(byte item:bytes){
            int value=item&255;
            out.append(alphabet[value>>>4]).append(alphabet[value&15]);
        }
        return out.toString();
    }

    private static void requireForensicAncestryStillSafe(Path account)
        throws IOException{
        try{
            MailboxAccountPublicationCoordinator
                .requireSafeWorldTempAncestry(account,false);
        }catch(IOException changed){
            throw new IOException(
                "G21.283 FORENSIC_ACCOUNT_ANCESTRY_CHANGED_NO_AUTHORITY",
                changed);
        }
    }

    /**
     * G21.285 read-only nlink observation. A single observed name
     * does NOT authorize any login or owner epoch. Unsupported
     * filesystems are UNPROVEN, never assumed single-name.
     */
    private static final class HardlinkWitness {
        final String state;
        // Exact count is retained only within the scan, never admission
        // authority. Negative sentinels distinguish absent/unsupported.
        final long count;
        HardlinkWitness(String state,long count){
            this.state=state;
            this.count=count;
        }
    }

    private static HardlinkWitness accountHardlinks(Path account,Stat stat)
        throws IOException{
        if(stat.attributes==null)
            return new HardlinkWitness("ABSENT",-2L);
        final Object count;
        try{
            count=Files.getAttribute(account,"unix:nlink",
                LinkOption.NOFOLLOW_LINKS);
        }catch(UnsupportedOperationException|IllegalArgumentException
                unavailable){
            return new HardlinkWitness("UNAVAILABLE_UNPROVEN",-1L);
        }
        if(!(count instanceof Number))
            return new HardlinkWitness("UNAVAILABLE_UNPROVEN",-1L);
        long n=((Number)count).longValue();
        if(n<1L)
            throw new IOException(
                "G21.285 ACCOUNT_HARDLINK_COUNT_INVALID_NO_AUTHORITY");
        return new HardlinkWitness(n==1L?"ONE_OBSERVED_NOT_AUTHORITY":
            "MULTIPLE_ACCOUNT_LINKS_NEGATIVE",n);
    }

    private static void requireSameHardlinkWitness(
        HardlinkWitness before,HardlinkWitness after)throws IOException{
        if(!before.state.equals(after.state))
            throw new IOException(
                "G21.285 ACCOUNT_HARDLINK_COUNT_CHANGED_DURING_READ");
        // G21.287: comparing just ONE/MULTIPLE loses 2->3 and 3->2
        // alias churn. Even though MULTIPLE is already negative,
        // such a report must not imply a stable filesystem witness.
        if(before.count!=after.count)
            throw new IOException(
                "G21.287 ACCOUNT_HARDLINK_EXACT_COUNT_CHANGED_DURING_READ");
    }

    private static String hashAccount(Path account,Stat stat)
        throws IOException{
        if(stat.attributes==null)return "ABSENT";
        BasicFileAttributes a=stat.attributes;
        if(!a.isRegularFile()||a.isSymbolicLink()||
           a.fileKey()==null||a.size()>MAX_ACCOUNT_BYTES)
            throw new IOException(
                "G21.279 ACCOUNT_LEAF_UNSAFE_OR_OVERSIZE_NO_AUTHORITY");
        MessageDigest md=digest();
        long total=0L;
        ByteBuffer buf=ByteBuffer.allocate(8192);
        try(FileChannel channel=FileChannel.open(account,
                StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            while(channel.read(buf)!=-1){
                buf.flip();
                int count=buf.remaining();
                total+=count;
                if(total>MAX_ACCOUNT_BYTES)
                    throw new IOException(
                        "G21.279 ACCOUNT_SIZE_DRIFT_NO_AUTHORITY");
                md.update(buf);
                buf.clear();
            }
        }
        return hex(md.digest());
    }

    /**
     * G21.289: selected negative marker content is a forensic witness
     * even if an uncooperative actor restores its size/mtime after
     * editing the same inode. Hash only bounded, regular, NOFOLLOW
     * sidecars. Nonregular/oversize/unidentifiable objects stay
     * explicitly opaque NEGATIVE evidence, never clean authority.
     *
     * This does NOT decode markers, validate their authority, create
     * account epochs, or establish an atomic snapshot.
     */
    private static String negativeSidecarContentWitness(
        Path sidecar,Stat observed)throws IOException{
        if(observed.attributes==null)return "ABSENT";
        BasicFileAttributes attrs=observed.attributes;
        if(!attrs.isRegularFile()||attrs.isSymbolicLink())
            return "NONREGULAR_OPAQUE_NEGATIVE";
        if(attrs.fileKey()==null)
            return "UNIDENTIFIED_OPAQUE_NEGATIVE";
        if(attrs.size()>MAX_ACCOUNT_BYTES)
            return "OVERSIZE_OPAQUE_NEGATIVE";
        return "SHA256:"+hashAccount(sidecar,observed);
    }

    /**
     * Read only; every retained sidecar is NEGATIVE regardless of
     * contents. A nonregular (including symlink) sidecar is negative,
     * not a reason to follow its target. Races trigger an exception.
     */
    @FunctionalInterface
    interface AfterFirstDigestProbe {
        void run(Path selectedAccount)throws IOException;
    }

    static Evidence inspect(Path selectedAccount)throws IOException{
        return inspect(selectedAccount,account->{});
    }

    /**
     * G21.281 package-only race seam. Production calls only the
     * callback-free overload; this does not create any writer or
     * recovery authority. The second full bounded digest reduces
     * missed in-place edits with restored metadata.
     */
    static Evidence inspect(Path selectedAccount,
                            AfterFirstDigestProbe afterFirstDigest)
        throws IOException{
        Objects.requireNonNull(afterFirstDigest,"afterFirstDigest");
        if(selectedAccount==null)
            throw new IOException("G21.279 NULL_ACCOUNT_PATH");
        Path account=selectedAccount.toAbsolutePath().normalize();
        if(account.getFileName()==null||account.getParent()==null)
            throw new IOException("G21.279 INVALID_ACCOUNT_PATH");
        // Read-only preflight; unlike withExclusivePublication this
        // does not CREATE a lock leaf or any parent directories.
        MailboxAccountPublicationCoordinator
            .requireSafeWorldTempAncestry(account,false);
        FilePlayerRepository.PathResolver pinned=requested->account;
        final Path review=LocalDurableAccountReviewIntentPrimitive
            .markerPath(account);
        final Map<String,Path> names=new LinkedHashMap<>();
        names.put("activeReview",review);
        names.put("mailboxReview",
            new MailboxDurableReviewFence(pinned).fencePath("forensic"));
        names.put("strictUncertain",
            new MailboxStrictUncertainFence(pinned).fencePath("forensic"));
        names.put("strictWriteIntent",
            new MailboxStrictWriteIntentFence(pinned)
                .fencePath("forensic"));
        names.put("diskCommit",account.resolveSibling(
            account.getFileName().toString()+
            MailboxGuardedDiskCommitRecord.SUFFIX));

        // G21.331: a sixth negative leaf, independent of the original five.
        final Path finalReview=LocalFinalSaveNegativeWitness.witnessPath(account);
        final Stat finalBefore=observe(finalReview);
        final String finalBytesBefore=negativeSidecarContentWitness(finalReview,finalBefore);
        Stat accountBefore=observe(account);
        HardlinkWitness linksBefore=accountHardlinks(account,accountBefore);
        Map<String,Stat> sidecarsBefore=new LinkedHashMap<>();
        Map<String,String> sidecarBytesBefore=new LinkedHashMap<>();
        for(Map.Entry<String,Path> entry:names.entrySet()){
            Stat previous=observe(entry.getValue());
            sidecarsBefore.put(entry.getKey(),previous);
            sidecarBytesBefore.put(entry.getKey(),
                negativeSidecarContentWitness(entry.getValue(),previous));
        }
        final String hash=hashAccount(account,accountBefore);
        afterFirstDigest.run(account);
        // G21.283: NOFOLLOW on the final account leaf does NOT
        // prevent an intermediate parent directory from being
        // replaced by a symbolic link after the initial ancestry
        // preflight. Observe the entire parent ancestry again
        // before trusting postread metadata or its second digest.
        requireForensicAncestryStillSafe(account);
        // G21.285: check the independently observed nlink witness at
        // the exact after-first-digest seam before metadata checks.
        // Otherwise filesystem-specific ctime mapping may classify
        // the same alias race as a generic metadata drift.
        requireSameHardlinkWitness(linksBefore,
            accountHardlinks(account,accountBefore));
        Stat accountAfter=observe(account);
        if(!same(accountBefore,accountAfter))
            throw new IOException(
                "G21.279 ACCOUNT_CHANGED_DURING_FORENSIC_READ");
        final Stat finalAfter=observe(finalReview);
        if(!same(finalBefore,finalAfter))
            throw new IOException("G21.331 FINAL_REVIEW_WITNESS_CHANGED_DURING_READ");
        final String finalBytesAfter=negativeSidecarContentWitness(finalReview,finalAfter);
        if(!Objects.equals(finalBytesBefore,finalBytesAfter))
            throw new IOException("G21.333 FINAL_REVIEW_WITNESS_BYTES_CHANGED_DURING_READ");
        Map<String,String> states=new LinkedHashMap<>();
        Map<String,String> stableContentWitnesses=new LinkedHashMap<>();
        int found=0;
        for(Map.Entry<String,Path> entry:names.entrySet()){
            Stat before=sidecarsBefore.get(entry.getKey());
            Stat after=observe(entry.getValue());
            if(!same(before,after))
                throw new IOException(
                    "G21.279 NEGATIVE_SIDECAR_CHANGED_DURING_READ");
            // An ordinary stat-only check misses in-place byte edits
            // when a foreign process restores original size and mtime.
            final String latestWitness=
                negativeSidecarContentWitness(entry.getValue(),after);
            if(!Objects.equals(sidecarBytesBefore.get(entry.getKey()),
                latestWitness))
                throw new IOException(
                    "G21.289 NEGATIVE_SIDECAR_BYTES_CHANGED_DURING_READ");
            stableContentWitnesses.put(entry.getKey(),latestWitness);
            states.put(entry.getKey(),after.classification);
            if(after.attributes!=null)found++;
        }
        // Last account witness after sidecar observations. This cannot
        // make an atomic filesystem snapshot against foreign actors.
        if(!same(accountBefore,observe(account)))
            throw new IOException(
                "G21.279 ACCOUNT_CHANGED_DURING_FORENSIC_READ");
        // G21.281: an adversarial or racing actor can edit an existing
        // inode in place and restore the original size/mtime, leaving
        // every metadata witness apparently unchanged. Rehash the
        // exact bounded NOFOLLOW account bytes before returning.
        // This is still NOT an atomic snapshot or proof against ABA
        // edit-and-restore between the two independent observations.
        final String secondHash=hashAccount(account,accountBefore);
        if(!hash.equals(secondHash))
            throw new IOException(
                "G21.281 ACCOUNT_BYTES_CHANGED_DURING_FORENSIC_READ");
        if(!same(accountBefore,observe(account)))
            throw new IOException(
                "G21.279 ACCOUNT_CHANGED_DURING_FORENSIC_READ");
        requireForensicAncestryStillSafe(account);
        // Keep sixth-leaf continuity until after account second hash.
        final Stat finalLast=observe(finalReview);
        if(!same(finalBefore,finalLast)||
           !Objects.equals(finalBytesBefore,
               negativeSidecarContentWitness(finalReview,finalLast)))
            throw new IOException("G21.331 FINAL_REVIEW_WITNESS_CHANGED_DURING_READ");
        // Read-only twice: an alias of the selected inode added or
        // removed during inspection invalidates the observation.
        // This cannot fence foreign nlink mutation after our probe.
        HardlinkWitness linksAfter=accountHardlinks(account,accountBefore);
        requireSameHardlinkWitness(linksBefore,linksAfter);
        String state=accountBefore.attributes==null
            ?"ABSENT":"PRESENT_REGULAR";
        // Exact count is an observation, not a link ownership grant;
        // unsupported/absent filesystems remain explicitly unproven.
        String observedCount=linksAfter.count>=0L
            ?Long.toString(linksAfter.count):linksAfter.state;
        return new Evidence(state,hash,linksAfter.state,observedCount,
            states,stableContentWitnesses,found,
            finalAfter.classification,finalBytesAfter);
    }

    /** Operator CLI: java -cp ... spk.local.LocalAccountForensicEvidence
     * /absolute/path/to/account.properties */
    public static void main(String[] args)throws Exception{
        if(args.length!=1)
            throw new IllegalArgumentException(
                "G21.279 requires one selected account file path");
        System.out.println(inspect(Path.of(args[0])).report());
    }
}
