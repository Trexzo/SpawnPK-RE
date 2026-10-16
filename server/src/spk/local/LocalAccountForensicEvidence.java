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
        final Map<String,String> negativeSidecars;
        final int observedNegativeNames;

        Evidence(String state,String sha,String links,
                 Map<String,String> names,int count){
            accountState=state;
            accountSha256=sha;
            accountHardlinkState=links;
            negativeSidecars=Collections.unmodifiableMap(
                new LinkedHashMap<>(names));
            observedNegativeNames=count;
        }

        String report(){
            StringBuilder b=new StringBuilder(
                "G21279_READ_ONLY_ACCOUNT_FORENSIC_EVIDENCE "+
                "account="+accountState+
                " accountSha256="+accountSha256+
                " accountHardlinks="+accountHardlinkState+
                " knownNegativeNames="+observedNegativeNames);
            for(Map.Entry<String,String> entry:negativeSidecars.entrySet())
                b.append(' ').append(entry.getKey()).append('=')
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
    private static String accountHardlinks(Path account,Stat stat)
        throws IOException{
        if(stat.attributes==null)return "ABSENT";
        final Object count;
        try{
            count=Files.getAttribute(account,"unix:nlink",
                LinkOption.NOFOLLOW_LINKS);
        }catch(UnsupportedOperationException|IllegalArgumentException
                unavailable){
            return "UNAVAILABLE_UNPROVEN";
        }
        if(!(count instanceof Number))
            return "UNAVAILABLE_UNPROVEN";
        long n=((Number)count).longValue();
        if(n<1L)
            throw new IOException(
                "G21.285 ACCOUNT_HARDLINK_COUNT_INVALID_NO_AUTHORITY");
        return n==1L?"ONE_OBSERVED_NOT_AUTHORITY":
            "MULTIPLE_ACCOUNT_LINKS_NEGATIVE";
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

        Stat accountBefore=observe(account);
        String linksBefore=accountHardlinks(account,accountBefore);
        Map<String,Stat> sidecarsBefore=new LinkedHashMap<>();
        for(Map.Entry<String,Path> entry:names.entrySet())
            sidecarsBefore.put(entry.getKey(),observe(entry.getValue()));
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
        if(!linksBefore.equals(accountHardlinks(account,accountBefore)))
            throw new IOException(
                "G21.285 ACCOUNT_HARDLINK_COUNT_CHANGED_DURING_READ");
        Stat accountAfter=observe(account);
        if(!same(accountBefore,accountAfter))
            throw new IOException(
                "G21.279 ACCOUNT_CHANGED_DURING_FORENSIC_READ");
        Map<String,String> states=new LinkedHashMap<>();
        int found=0;
        for(Map.Entry<String,Path> entry:names.entrySet()){
            Stat before=sidecarsBefore.get(entry.getKey());
            Stat after=observe(entry.getValue());
            if(!same(before,after))
                throw new IOException(
                    "G21.279 NEGATIVE_SIDECAR_CHANGED_DURING_READ");
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
        // Read-only twice: an alias of the selected inode added or
        // removed during inspection invalidates the observation.
        // This cannot fence foreign nlink mutation after our probe.
        String linksAfter=accountHardlinks(account,accountBefore);
        if(!linksBefore.equals(linksAfter))
            throw new IOException(
                "G21.285 ACCOUNT_HARDLINK_COUNT_CHANGED_DURING_READ");
        String state=accountBefore.attributes==null
            ?"ABSENT":"PRESENT_REGULAR";
        return new Evidence(state,hash,linksAfter,states,found);
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
