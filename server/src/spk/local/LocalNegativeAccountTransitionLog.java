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
 * R55: UNWIRED, always-nonadmitting monotonic negative transition log.
 *
 * Extend the ORIGINAL permanent R50 negative HEAD. Each step is an
 * immutable, forced PREPARE followed by a forced snapshot object and
 * independently forced COMMIT. An incomplete step is permanently
 * NEGATIVE and cannot be auto-replayed, pruned or treated as absent.
 *
 * This reuses the G21.185 cooperating OS lock (not a second lock) and
 * never removes the R50 HEAD. All phases stay NONADMITTING and cannot
 * authorize owner epochs, final-save completion or account recovery.
 * No hostile-writer CAS or machine power-loss claim is made.
 */
final class LocalNegativeAccountTransitionLog {
    private static final int MAX_RECORD=4096;
    private static final int MAX_STEPS=8;
    private static final String PREPARE_MAGIC="G21383_NEGATIVE_PREPARE_V1";
    private static final String COMMIT_MAGIC="G21383_NEGATIVE_COMMIT_V1";
    enum Phase { ACTIVE, CHECKPOINT, FINAL_PENDING, REVIEW }
    enum Stage { PREPARE_FORCED, OBJECT_FORCED, COMMIT_FORCED }
    @FunctionalInterface interface Cutpoint {
        void reached(Stage stage)throws IOException;
    }
    static final class Observation {
        final Path account;
        final int sequence;
        final Phase phase;
        final String snapshotSha256;
        final boolean admissionAuthorized=false;
        final boolean releaseAuthorized=false;
        private final String latestRecordSha256;
        private final LocalNegativeAccountHeadPrototype.NegativeObservation genesis;
        Observation(Path a,int seq,Phase p,String digest,String previous,
                    LocalNegativeAccountHeadPrototype.NegativeObservation root){
            account=a;sequence=seq;phase=p;snapshotSha256=digest;
            latestRecordSha256=previous;genesis=root;
        }
    }
    private LocalNegativeAccountTransitionLog(){}

    private static Path canonical(Path account)throws IOException{
        if(account==null)throw new IOException("R55_NULL_ACCOUNT");
        Path path=account.toAbsolutePath().normalize();
        if(path.getParent()==null||path.getFileName()==null)
            throw new IOException("R55_INVALID_ACCOUNT_PATH");
        return path;
    }
    private static Path record(Path account,int sequence,String part)
        throws IOException{
        Path selected=canonical(account);
        if(sequence<1||sequence>MAX_STEPS+1)
            throw new IOException("R55_SEQUENCE_OUT_OF_BOUNDS");
        return selected.resolveSibling(selected.getFileName().toString()+
            ".g21383-step-"+String.format(java.util.Locale.ROOT,"%02d",sequence)+
            "-"+part);
    }
    private static Path image(Path account,int sequence,String digest)
        throws IOException{
        if(digest==null||!digest.matches("[0-9a-f]{64}"))
            throw new IOException("R55_IMAGE_DIGEST_INVALID");
        return record(account,sequence,"image-"+digest+".snapshot");
    }
    private static void requireLockConfiguration()throws IOException{
        if(!Boolean.getBoolean(LocalCrossJvmAccountSessionLock.PROPERTY)||
           !Boolean.getBoolean(
                LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY))
            throw new IOException(
                "R55_REQUIRES_CROSS_JVM_LOCK_AND_CONTINUITY");
    }
    private static boolean present(Path p)throws IOException{
        try{
            Files.readAttributes(p,BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
            return true;
        }catch(NoSuchFileException missing){return false;}
    }
    private static void forceParent(Path parent)throws IOException{
        try(FileChannel dir=FileChannel.open(parent,StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS)){
            dir.force(true);
        }
    }
    private static void createForced(Path path,byte[] data,int limit)
        throws IOException{
        if(data==null||data.length==0||data.length>limit)
            throw new IOException("R55_RECORD_SIZE_INVALID");
        try(FileChannel f=FileChannel.open(path,
                StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS)){
            ByteBuffer b=ByteBuffer.wrap(data);
            while(b.hasRemaining())f.write(b);
            f.force(true);
        }
        forceParent(path.getParent());
    }
    private static byte[] read(Path p,int max)throws IOException{
        BasicFileAttributes before=Files.readAttributes(
            p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!before.isRegularFile()||before.isSymbolicLink()||
           before.fileKey()==null||before.size()<=0||before.size()>max)
            throw new IOException("R55_NONREGULAR_OR_UNBOUNDED_NEGATIVE");
        ByteBuffer buf=ByteBuffer.allocate(max+1);
        try(FileChannel f=FileChannel.open(
                p,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            while(buf.hasRemaining()){
                int n=f.read(buf);
                if(n<0)break;
                if(n==0)throw new IOException("R55_READ_STALLED");
            }
        }
        BasicFileAttributes after=Files.readAttributes(
            p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(buf.position()==0||buf.position()>max||
           !after.isRegularFile()||after.isSymbolicLink()||
           !Objects.equals(before.fileKey(),after.fileKey())||
           !Objects.equals(before.creationTime(),after.creationTime())||
           before.size()!=after.size()||before.size()!=buf.position())
            throw new IOException("R55_READ_OBJECT_DRIFT_NEGATIVE");
        return Arrays.copyOf(buf.array(),buf.position());
    }
    private static String sha(byte[] bytes)throws IOException{
        try{
            byte[] h=MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder b=new StringBuilder(h.length*2);
            for(byte n:h){
                String pair=Integer.toHexString(n&255);
                if(pair.length()==1)b.append('0');
                b.append(pair);
            }
            return b.toString();
        }catch(NoSuchAlgorithmException unavailable){
            throw new IOException("R55_SHA_UNAVAILABLE",unavailable);
        }
    }
    private static byte[] ascii(String value){
        return value.getBytes(StandardCharsets.US_ASCII);
    }
    private static String prepare(
        int sequence,String prev,
        LocalNegativeAccountHeadPrototype.NegativeObservation genesis,
        Phase phase,String imageSha,int imageLength
    )throws IOException{
        String accountSha=sha(ascii(genesis.account.toString()));
        return PREPARE_MAGIC+"\n"+
            "sequence="+sequence+"\n"+
            "previousSha256="+prev+"\n"+
            "accountPathSha256="+accountSha+"\n"+
            "username="+genesis.username+"\n"+
            "untrustedEpoch="+genesis.untrustedEpoch+"\n"+
            "untrustedWorldGeneration="+genesis.untrustedWorldGeneration+"\n"+
            "phase="+phase.name()+"\n"+
            "snapshotSha256="+imageSha+"\n"+
            "snapshotLength="+imageLength+"\n"+
            "state=NEGATIVE_NO_ADMISSION_NO_RELEASE\n";
    }
    private static String commit(byte[] prepared){
        return COMMIT_MAGIC+"\n"+
            "prepareSha256="+digestUnchecked(prepared)+"\n"+
            "state=NEGATIVE_NO_ADMISSION_NO_RELEASE\n";
    }
    private static String digestUnchecked(byte[] data){
        try{return sha(data);}
        catch(IOException impossible){throw new IllegalStateException(impossible);}
    }
    private static IOException incomplete(String reason){
        return new IOException("R55_INCOMPLETE_TRANSITION_MANUAL_REVIEW "+reason);
    }

    /**
     * R56: an orphan immutable image is negative evidence even when no
     * corresponding PREPARE survived. A bare PREPARE/COMMIT scan would
     * otherwise silently omit this artifact from the forensic chain.
     *
     * Enumerating names is OBSERVATIONAL; it does not pin directory
     * inodes or fence a hostile writer between checks.
     */
    private static java.util.SortedSet<String> auditedStepLeaves(Path a)
        throws IOException{
        String prefix=a.getFileName().toString()+".g21383-step-";
        java.util.SortedSet<String> observed=
            new java.util.TreeSet<String>();
        try(java.nio.file.DirectoryStream<Path> children=
                Files.newDirectoryStream(a.getParent())){
            for(Path child:children){
                String name=child.getFileName().toString();
                if(!name.startsWith(prefix))continue;
                String suffix=name.substring(prefix.length());
                if(!suffix.matches(
                        "0[1-9]-(?:prepare|commit|image-[0-9a-f]{64}\\.snapshot)"))
                    throw new IOException(
                        "R56_MALFORMED_STEP_LEAF_NEGATIVE");
                observed.add(name);
                if(observed.size()>3*(MAX_STEPS+1))
                    throw new IOException(
                        "R56_UNBOUNDED_STEP_LEAVES_NEGATIVE");
            }
        }
        return observed;
    }

    private static String observedImageLeaf(
        java.util.SortedSet<String> leaves,Path account,int sequence
    )throws IOException{
        String prefix=record(account,sequence,"image-")
            .getFileName().toString();
        String only=null;
        for(String name:leaves){
            if(!name.startsWith(prefix))continue;
            if(only!=null)
                throw new IOException(
                    "R56_MULTIPLE_STEP_IMAGES_NEGATIVE");
            only=name;
        }
        return only;
    }

    /**
     * Read-only forensic inspection. No caller may interpret a valid
     * chain as account admission or successful final-save release.
     */
    static Observation inspect(Path account)throws IOException{
        Path a=canonical(account);
        LocalNegativeAccountHeadPrototype.NegativeObservation genesis=
            LocalNegativeAccountHeadPrototype.inspectNegative(a);
        String prev=sha(read(
            LocalNegativeAccountHeadPrototype.headPath(a),MAX_RECORD));
        // Capture every R55 step leaf, not only known prepare/commit
        // names. An orphan image remains permanent negative evidence.
        final java.util.SortedSet<String> stepLeaves=
            auditedStepLeaves(a);
        int last=0;
        Phase phase=Phase.ACTIVE;
        String digest=genesis.snapshotSha256;
        boolean gap=false;
        for(int seq=1;seq<=MAX_STEPS+1;seq++){
            Path preparePath=record(a,seq,"prepare");
            Path commitPath=record(a,seq,"commit");
            boolean pre=present(preparePath),com=present(commitPath);
            // Fail on directory enumeration drift, not just on an
            // incomplete PREPARE/COMMIT pair.
            if(pre!=stepLeaves.contains(
                    preparePath.getFileName().toString())||
               com!=stepLeaves.contains(
                    commitPath.getFileName().toString()))
                throw new IOException(
                    "R56_TRANSITION_LEAF_RACE_NEGATIVE");
            String observedImage=observedImageLeaf(stepLeaves,a,seq);
            if(!pre&&!com){
                if(observedImage!=null)
                    throw new IOException(
                        "R56_ORPHAN_STEP_IMAGE_NEGATIVE");
                gap=true;
                continue;
            }
            if(seq>MAX_STEPS||gap||!pre||!com)
                throw incomplete("gap_or_unpaired_step="+seq);
            byte[] prepareBytes=read(preparePath,MAX_RECORD);
            byte[] commitBytes=read(commitPath,MAX_RECORD);
            if(!Arrays.equals(ascii(commit(prepareBytes)),commitBytes))
                throw incomplete("commit_mismatch_step="+seq);
            String[] fields=new String(prepareBytes,StandardCharsets.US_ASCII)
                .split("\n",-1);
            if(fields.length!=12||
                !PREPARE_MAGIC.equals(fields[0])||
                !fields[1].equals("sequence="+seq)||
                !fields[2].equals("previousSha256="+prev)||
                !fields[3].equals("accountPathSha256="+
                    sha(ascii(genesis.account.toString())))||
                !fields[4].equals("username="+genesis.username)||
                !fields[5].equals("untrustedEpoch="+genesis.untrustedEpoch)||
                !fields[6].equals("untrustedWorldGeneration="+
                    genesis.untrustedWorldGeneration)||
                !fields[7].startsWith("phase=")||
                !fields[8].startsWith("snapshotSha256=")||
                !fields[9].startsWith("snapshotLength=")||
                !fields[10].equals("state=NEGATIVE_NO_ADMISSION_NO_RELEASE")||
                !fields[11].isEmpty())
                throw incomplete("malformed_prepare_step="+seq);
            final Phase next;
            final int length;
            try{
                next=Phase.valueOf(fields[7].substring("phase=".length()));
                length=Integer.parseInt(
                    fields[9].substring("snapshotLength=".length()));
            }catch(IllegalArgumentException invalid){
                throw incomplete("invalid_phase_or_length_step="+seq);
            }
            String nextSha=fields[8].substring("snapshotSha256=".length());
            if(!nextSha.matches("[0-9a-f]{64}")||
               length<=0||length>LocalNegativeAccountHeadPrototype.MAX_SNAPSHOT_BYTES)
                throw incomplete("invalid_snapshot_bound_step="+seq);
            if(phase==Phase.FINAL_PENDING&&next!=Phase.REVIEW ||
               phase==Phase.REVIEW ||
               phase!=Phase.FINAL_PENDING&&next!=Phase.CHECKPOINT&&
                   next!=Phase.FINAL_PENDING&&next!=Phase.REVIEW)
                throw incomplete("invalid_phase_transition_step="+seq);
            if(next==Phase.REVIEW){
                if(observedImage!=null)
                    throw new IOException(
                        "R56_REVIEW_IMAGE_NOT_ALLOWED_NEGATIVE");
                if(!nextSha.equals(digest))
                    throw incomplete("review_digest_changed_step="+seq);
            }else{
                if(!image(a,seq,nextSha).getFileName().toString()
                        .equals(observedImage))
                    throw new IOException(
                        "R56_UNREFERENCED_STEP_IMAGE_NEGATIVE");
                byte[] bytes;
                try{
                    bytes=read(image(a,seq,nextSha),
                        LocalNegativeAccountHeadPrototype.MAX_SNAPSHOT_BYTES);
                }catch(IOException invalid){
                    throw new IOException(
                        "R55_INCOMPLETE_TRANSITION_IMAGE_MANUAL_REVIEW",invalid);
                }
                if(bytes.length!=length||!sha(bytes).equals(nextSha)||
                   !Arrays.equals(bytes,read(image(a,seq,nextSha),
                       LocalNegativeAccountHeadPrototype.MAX_SNAPSHOT_BYTES)))
                    throw incomplete("snapshot_integrity_step="+seq);
            }
            String canonical=prepare(seq,prev,genesis,next,nextSha,length);
            if(!Arrays.equals(prepareBytes,ascii(canonical)))
                throw incomplete("noncanonical_prepare_step="+seq);
            phase=next;
            digest=nextSha;
            prev=sha(commitBytes);
            last=seq;
        }
        // A second directory observation catches newly published
        // sidecar names seen while reading earlier PREPARE/COMMIT
        // objects. This is not an atomic directory snapshot.
        if(!stepLeaves.equals(auditedStepLeaves(a)))
            throw new IOException(
                "R56_STEP_DIRECTORY_CHANGED_DURING_AUDIT_NEGATIVE");
        // Observe R50 original HEAD again after all independent reads:
        // a changing genesis must never be classified as healthy.
        if(!prev.isEmpty()){
            LocalNegativeAccountHeadPrototype.NegativeObservation again=
                LocalNegativeAccountHeadPrototype.inspectNegative(a);
            if(!again.untrustedEpoch.equals(genesis.untrustedEpoch)||
               !again.snapshotSha256.equals(genesis.snapshotSha256)||
               again.untrustedWorldGeneration!=genesis.untrustedWorldGeneration)
                throw incomplete("genesis_changed");
        }
        return new Observation(a,last,phase,digest,prev,genesis);
    }
    /**
     * Append exactly one immutable negative transition. No repair:
     * an orphan PREPARE or snapshot never gets a positive retry.
     */
    static Observation append(
        Path account,int expectedSequence,Phase next,byte[] snapshot,
        Cutpoint cutpoint
    )throws IOException{
        requireLockConfiguration();
        if(account==null||next==null||cutpoint==null)
            throw new IOException("R55_NULL_APPEND_ARGUMENT");
        Path a=canonical(account);
        try(LocalCrossJvmAccountSessionLock.Handle lock=
                LocalCrossJvmAccountSessionLock.acquire(a)){
            lock.requireOriginalLockNameStillPresent();
            Observation current=inspect(a);
            if(expectedSequence!=current.sequence)
                throw new IOException(
                    "R55_STALE_EXPECTED_SEQUENCE_NEGATIVE");
            if(current.sequence>=MAX_STEPS)
                throw new IOException("R55_SEQUENCE_EXHAUSTED_NEGATIVE");
            if(current.phase==Phase.REVIEW||
               current.phase==Phase.FINAL_PENDING&&next!=Phase.REVIEW||
               current.phase!=Phase.FINAL_PENDING&&
                   next!=Phase.CHECKPOINT&&next!=Phase.FINAL_PENDING&&
                   next!=Phase.REVIEW)
                throw new IOException("R55_INVALID_TRANSITION_NEGATIVE");
            final boolean review=next==Phase.REVIEW;
            if(review? snapshot!=null :
                snapshot==null||snapshot.length==0||
                snapshot.length>LocalNegativeAccountHeadPrototype.MAX_SNAPSHOT_BYTES)
                throw new IOException("R55_IMAGE_ARGUMENT_INVALID");
            int sequence=current.sequence+1;
            String imageSha=review?current.snapshotSha256:sha(snapshot);
            int imageLen=review?
                current.genesis.snapshotLength:snapshot.length;
            // REVIEW retains digest and length of the last step, not
            // the genesis length. Take the prior canonical length from
            // its actual immutable object when it differs.
            if(review&&current.sequence>0){
                Path prior=record(a,current.sequence,
                    "prepare");
                String[] fields=new String(read(prior,MAX_RECORD),
                    StandardCharsets.US_ASCII).split("\n",-1);
                try{
                    imageLen=Integer.parseInt(
                        fields[9].substring("snapshotLength=".length()));
                }catch(RuntimeException invalid){
                    throw incomplete("prior_snapshot_length_invalid");
                }
            }
            byte[] prep=ascii(prepare(sequence,current.latestRecordSha256,
                current.genesis,next,imageSha,imageLen));
            Path preparePath=record(a,sequence,"prepare");
            Path commitPath=record(a,sequence,"commit");
            // No allocation after a prior orphan or competing step.
            if(present(preparePath)||present(commitPath))
                throw incomplete("pending_step_already_present");
            createForced(preparePath,prep,MAX_RECORD);
            lock.requireOriginalLockNameStillPresent();
            cutpoint.reached(Stage.PREPARE_FORCED);
            lock.requireOriginalLockNameStillPresent();
            if(!review){
                Path newImage=image(a,sequence,imageSha);
                if(present(newImage))
                    throw incomplete("image_already_present");
                createForced(newImage,snapshot,
                    LocalNegativeAccountHeadPrototype.MAX_SNAPSHOT_BYTES);
                if(!Arrays.equals(snapshot,read(newImage,
                    LocalNegativeAccountHeadPrototype.MAX_SNAPSHOT_BYTES)))
                    throw incomplete("object_readback_changed");
            }
            lock.requireOriginalLockNameStillPresent();
            cutpoint.reached(Stage.OBJECT_FORCED);
            lock.requireOriginalLockNameStillPresent();
            if(!Arrays.equals(prep,read(preparePath,MAX_RECORD)))
                throw incomplete("prepare_bytes_changed");
            createForced(commitPath,ascii(commit(prep)),MAX_RECORD);
            lock.requireOriginalLockNameStillPresent();
            cutpoint.reached(Stage.COMMIT_FORCED);
            lock.requireOriginalLockNameStillPresent();
            Observation after=inspect(a);
            if(after.sequence!=sequence||after.phase!=next||
               !after.snapshotSha256.equals(imageSha))
                throw incomplete("completed_chain_drift");
            return after;
        }
    }
}
