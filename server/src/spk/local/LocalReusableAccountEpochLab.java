package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * R57 STANDALONE, UNWIRED LAB: reusable monotonically sequenced account
 * HEAD under the existing G21.185 cooperating cross-process lock.
 *
 * CLOSED is a LAB STATE, NOT permission to login or reuse any real
 * account. Neither this state nor tokens authenticate World ownership.
 * No LocalSession/World/RAW data path reads this HEAD.
 *
 * Persistent orphan image or head staging evidence makes inspection
 * and new transitions fail closed. A stage after atomic move but before
 * directory force is UNCERTAIN; this experiment does not certify
 * machine power-loss safety or exclusion of hostile raw writers.
 */
final class LocalReusableAccountEpochLab {
    private static final String MAGIC="G21385_REUSABLE_LEDGER_LAB_V1";
    private static final String HEAD_SUFFIX=".g21385-ledger-head";
    private static final String IMAGE_PREFIX=".g21385-image-";
    private static final String TEMP_PREFIX=".g21385-head-stage-";
    private static final String IMAGE_SUFFIX=".snapshot";
    private static final int MAX_HEAD=4096;
    private static final int MAX_IMAGE=8*1024*1024;
    private static final int MAX_SEQUENCE=32;
    private static final String ZERO="0000000000000000000000000000000000000000000000000000000000000000";
    enum Phase { CLOSED, OWNED, FINAL_PENDING }
    enum Stage { SNAPSHOT_FORCED, ARCHIVED_HEAD_FORCED, INTENT_FORCED,
                 HEAD_TEMP_FORCED, HEAD_MOVED_BEFORE_DIR_FORCE,
                 HEAD_DIR_FORCED, RECEIPT_FORCED }
    @FunctionalInterface interface Cutpoint {
        void reached(Stage stage)throws IOException;
    }
    static final class Observation {
        final Path account;
        final long epoch;
        final int sequence;
        final Phase phase;
        final long worldGeneration;
        final String ownerNonce;
        final int imageSequence;
        final String imageSha;
        final int imageLength;
        final String previousHeadSha;
        final boolean admissionAuthorized=false;
        final boolean releaseAuthorized=false;
        Observation(Path a,long e,int s,Phase p,long g,String owner,int img,
                    String image,int length,String prior){
            account=a;epoch=e;sequence=s;phase=p;worldGeneration=g;
            ownerNonce=owner;imageSequence=img;imageSha=image;
            imageLength=length;previousHeadSha=prior;
        }
    }
    private LocalReusableAccountEpochLab(){}

    private static Path canonical(Path account)throws IOException{
        if(account==null)throw new IOException("R57_NULL_ACCOUNT");
        Path a=account.toAbsolutePath().normalize();
        if(a.getParent()==null||a.getFileName()==null)
            throw new IOException("R57_INVALID_PATH");
        return a;
    }
    private static Path head(Path a){
        return a.resolveSibling(a.getFileName()+HEAD_SUFFIX);
    }
    private static String prefix(Path a){return a.getFileName().toString();}
    private static Path image(Path a,int sequence,String digest)
        throws IOException{
        if(sequence<0||sequence>MAX_SEQUENCE||
           !digest.matches("[0-9a-f]{64}"))
            throw new IOException("R57_IMAGE_NAME_INVALID");
        return a.resolveSibling(prefix(a)+IMAGE_PREFIX+
            String.format(java.util.Locale.ROOT,"%04d",sequence)+
            "-"+digest+IMAGE_SUFFIX);
    }
    private static void requiredLock()throws IOException{
        if(!Boolean.getBoolean(LocalCrossJvmAccountSessionLock.PROPERTY)||
           !Boolean.getBoolean(LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY))
            throw new IOException("R57_REQUIRES_EXISTING_CROSS_JVM_LOCK_CONTINUITY");
    }
    private static void parent(Path a)throws IOException{
        LocalCrossJvmAccountSessionLock.requireNoSymlinkAncestors(
            a.getParent());
        if(!Files.isDirectory(a.getParent(),LinkOption.NOFOLLOW_LINKS))
            throw new IOException("R57_UNSAFE_PARENT");
    }
    private static boolean exists(Path p)throws IOException{
        try{
            Files.readAttributes(p,BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
            return true;
        }catch(NoSuchFileException missing){return false;}
    }
    private static void forceDir(Path a)throws IOException{
        try(FileChannel d=FileChannel.open(a.getParent(),
                StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            d.force(true);
        }
    }
    private static byte[] read(Path path,int max)throws IOException{
        BasicFileAttributes before=Files.readAttributes(path,
            BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!before.isRegularFile()||before.isSymbolicLink()||
           before.fileKey()==null||before.size()==0||before.size()>max)
            throw new IOException("R57_UNSAFE_RECORD_NEGATIVE");
        ByteBuffer buffer=ByteBuffer.allocate(max+1);
        try(FileChannel channel=FileChannel.open(path,
                StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            while(buffer.hasRemaining()){
                int n=channel.read(buffer);
                if(n<0)break;
                if(n==0)throw new IOException("R57_STALLED_RECORD_READ");
            }
        }
        BasicFileAttributes after=Files.readAttributes(path,
            BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(buffer.position()==0||buffer.position()>max||
           !after.isRegularFile()||after.isSymbolicLink()||
           !Objects.equals(before.fileKey(),after.fileKey())||
           !Objects.equals(before.creationTime(),after.creationTime())||
           before.size()!=after.size()||
           before.size()!=buffer.position())
            throw new IOException("R57_RECORD_CHANGED_DURING_READ");
        return Arrays.copyOf(buffer.array(),buffer.position());
    }
    private static String sha(byte[] bytes)throws IOException{
        try{
            StringBuilder result=new StringBuilder();
            for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes)){
                String hex=Integer.toHexString(b&255);
                if(hex.length()==1)result.append('0');
                result.append(hex);
            }
            return result.toString();
        }catch(NoSuchAlgorithmException missing){
            throw new IOException("R57_SHA_UNAVAILABLE",missing);
        }
    }
    private static void forcedCreate(Path path,byte[] bytes,int max)
        throws IOException{
        if(bytes==null||bytes.length==0||bytes.length>max)
            throw new IOException("R57_RECORD_SIZE_INVALID");
        try(FileChannel channel=FileChannel.open(path,
                StandardOpenOption.WRITE,StandardOpenOption.CREATE_NEW,
                LinkOption.NOFOLLOW_LINKS)){
            ByteBuffer b=ByteBuffer.wrap(bytes);
            while(b.hasRemaining())channel.write(b);
            channel.force(true);
        }
        forceDir(path);
        if(!Arrays.equals(bytes,read(path,max)))
            throw new IOException("R57_RECORD_FORCE_READBACK_UNCERTAIN");
    }
    private static void scan(Path a,int imageSequence,String imageDigest,
                             boolean headRequired)throws IOException{
        String imagePrefix=prefix(a)+IMAGE_PREFIX;
        String stagePrefix=prefix(a)+TEMP_PREFIX;
        int imageCount=0;
        try(java.nio.file.DirectoryStream<Path> files=
                Files.newDirectoryStream(a.getParent())){
            for(Path child:files){
                String name=child.getFileName().toString();
                if(name.startsWith(stagePrefix))
                    throw new IOException("R57_ORPHAN_HEAD_TEMP_MANUAL_REVIEW");
                if(!name.startsWith(imagePrefix))continue;
                String remaining=name.substring(imagePrefix.length());
                if(!remaining.matches("[0-9]{4}-[0-9a-f]{64}\\.snapshot"))
                    throw new IOException("R57_MALFORMED_IMAGE_LEAF_MANUAL_REVIEW");
                int step=Integer.parseInt(remaining.substring(0,4));
                if(step>imageSequence||!headRequired)
                    throw new IOException("R57_ORPHAN_FUTURE_IMAGE_MANUAL_REVIEW");
                if(step==imageSequence){
                    imageCount++;
                    if(!name.equals(image(a,imageSequence,imageDigest)
                            .getFileName().toString()))
                        throw new IOException("R57_DIVERGENT_CURRENT_IMAGE_MANUAL_REVIEW");
                }
            }
        }
        if(headRequired&&imageCount!=1)
            throw new IOException("R57_CURRENT_IMAGE_UNCERTAIN");
    }

    // R58: forced immutable write-ahead intent is a PERMANENT negative
    // barrier until a matching post-dir-force receipt exists. Do not
    // remove or reinterpret a stranded intent as a safe retry.
    private static final String INTENT_MAGIC="G21386_LAB_INTENT_V1";
    private static final String RECEIPT_MAGIC="G21386_LAB_RECEIPT_V1";
    private static final String JOURNAL_PREFIX=".g21386-";
    private static final String JOURNAL_STATE=
        "state=LAB_ONLY_NO_ADMISSION_OR_RELEASE";
    private static Path journalLeaf(Path a,int sequence,boolean receipt)
        throws IOException{
        if(sequence<0||sequence>MAX_SEQUENCE)
            throw new IOException("R58_SEQUENCE_OUT_OF_BOUNDS");
        return a.resolveSibling(prefix(a)+JOURNAL_PREFIX+
            (receipt?"receipt-":"intent-")+
            String.format(java.util.Locale.ROOT,"%04d",sequence));
    }
    private static byte[] journalRecord(
        int sequence,String headSha,String previousHeadSha,
        String previousReceiptSha,boolean receipt
    )throws IOException{
        if(sequence<0||sequence>MAX_SEQUENCE||
           !headSha.matches("[0-9a-f]{64}")||
           !previousHeadSha.matches("[0-9a-f]{64}")||
           !previousReceiptSha.matches("[0-9a-f]{64}"))
            throw new IOException("R58_JOURNAL_ARGUMENTS_INVALID");
        String data=(receipt?RECEIPT_MAGIC:INTENT_MAGIC)+"\n"+
            "sequence="+sequence+"\n"+
            "headSha256="+headSha+"\n"+
            "previousHeadSha256="+previousHeadSha+"\n"+
            "previousReceiptSha256="+previousReceiptSha+"\n"+
            JOURNAL_STATE+"\n";
        return data.getBytes(StandardCharsets.US_ASCII);
    }
    private static java.util.SortedSet<String> journalLeaves(Path a)
        throws IOException{
        java.util.SortedSet<String> result=
            new java.util.TreeSet<String>();
        String prefix=prefix(a)+JOURNAL_PREFIX;
        try(java.nio.file.DirectoryStream<Path> files=
                Files.newDirectoryStream(a.getParent())){
            for(Path child:files){
                String leaf=child.getFileName().toString();
                if(!leaf.startsWith(prefix))continue;
                String tail=leaf.substring(prefix.length());
                if(!tail.matches("(?:intent|receipt)-[0-9]{4}"))
                    throw new IOException(
                        "R58_MALFORMED_JOURNAL_LEAF_MANUAL_REVIEW");
                result.add(leaf);
                if(result.size()>2*(MAX_SEQUENCE+1))
                    throw new IOException(
                        "R58_JOURNAL_SET_UNBOUNDED_MANUAL_REVIEW");
            }
        }
        return result;
    }
    private static void requireFreshJournal(Path a)throws IOException{
        if(!journalLeaves(a).isEmpty())
            throw new IOException(
                "R58_EXISTING_JOURNAL_NO_INITIALIZE_OR_REPAIR");
    }
    private static void inspectJournal(
        Path a,Observation current,byte[] currentHeadBytes
    )throws IOException{
        java.util.SortedSet<String> expected=
            new java.util.TreeSet<String>();
        for(int i=0;i<=current.sequence;i++){
            expected.add(journalLeaf(a,i,false).getFileName().toString());
            expected.add(journalLeaf(a,i,true).getFileName().toString());
        }
        if(!expected.equals(journalLeaves(a)))
            throw new IOException(
                "R58_ORPHAN_OR_MISSING_JOURNAL_MANUAL_REVIEW");
        String prevHead=ZERO;
        String prevReceipt=ZERO;
        String lastPreviousHead=ZERO;
        java.util.List<String> receiptHashes=
            new java.util.ArrayList<String>();
        for(int i=0;i<=current.sequence;i++){
            final byte[] receipt=read(journalLeaf(a,i,true),MAX_HEAD);
            String[] fields=new String(receipt,StandardCharsets.US_ASCII)
                .split("\n",-1);
            if(fields.length!=7||
               !RECEIPT_MAGIC.equals(fields[0])||
               !fields[1].equals("sequence="+i)||
               !fields[2].startsWith("headSha256=")||
               !fields[3].equals("previousHeadSha256="+prevHead)||
               !fields[4].equals("previousReceiptSha256="+prevReceipt)||
               !fields[5].equals(JOURNAL_STATE)||
               !fields[6].isEmpty())
                throw new IOException(
                    "R58_NONCANONICAL_RECEIPT_MANUAL_REVIEW");
            String stepHead=fields[2].substring("headSha256=".length());
            if(!stepHead.matches("[0-9a-f]{64}")||
               !Arrays.equals(receipt,
                   journalRecord(i,stepHead,prevHead,prevReceipt,true))||
               !Arrays.equals(
                   read(journalLeaf(a,i,false),MAX_HEAD),
                   journalRecord(i,stepHead,prevHead,prevReceipt,false)))
                throw new IOException(
                    "R58_INTENT_RECEIPT_MISMATCH_MANUAL_REVIEW");
            lastPreviousHead=prevHead;
            prevHead=stepHead;
            prevReceipt=sha(receipt);
            receiptHashes.add(stepHead);
        }
        if(!sha(currentHeadBytes).equals(prevHead)||
           !current.previousHeadSha.equals(lastPreviousHead))
            throw new IOException(
                "R58_HEAD_ROLLBACK_OR_HISTORY_MISMATCH_MANUAL_REVIEW");
        if(!expected.equals(journalLeaves(a)))
            throw new IOException(
                "R58_JOURNAL_SET_CHANGED_DURING_AUDIT_NEGATIVE");
        // The receipt hashes are insufficient by themselves: the
        // complete immutable HEAD objects and their historical
        // account snapshot references must all still be verifiable.
        validateArchivedHeadHistory(
            a,current,currentHeadBytes,receiptHashes);
    }

    // R59: independently forced, content-addressed full HEAD image
    // per committed sequence. An immutable receipt's hash without
    // these bytes cannot validate historical image references.
    // Records are LAB-only; no automatic quarantine retirement.
    private static final String ARCHIVE_PREFIX=".g21387-head-";
    private static Path archivedHead(Path a,int sequence,String digest)
        throws IOException{
        if(sequence<0||sequence>MAX_SEQUENCE||digest==null||
                !digest.matches("[0-9a-f]{64}"))
            throw new IOException("R59_ARCHIVE_NAME_INVALID");
        return a.resolveSibling(prefix(a)+ARCHIVE_PREFIX+
            String.format(java.util.Locale.ROOT,"%04d",sequence)+
            "-"+digest+".head");
    }
    private static java.util.SortedSet<String> archiveLeaves(Path a)
        throws IOException{
        String prefix=prefix(a)+ARCHIVE_PREFIX;
        java.util.SortedSet<String> result=
            new java.util.TreeSet<String>();
        try(java.nio.file.DirectoryStream<Path> children=
                Files.newDirectoryStream(a.getParent())){
            for(Path path:children){
                String name=path.getFileName().toString();
                if(!name.startsWith(prefix))continue;
                if(!name.substring(prefix.length()).matches(
                        "[0-9]{4}-[0-9a-f]{64}\\.head"))
                    throw new IOException(
                        "R59_MALFORMED_ARCHIVE_LEAF_MANUAL_REVIEW");
                result.add(name);
                if(result.size()>MAX_SEQUENCE+1)
                    throw new IOException(
                        "R59_UNBOUNDED_ARCHIVE_LEAVES_MANUAL_REVIEW");
            }
        }
        return result;
    }
    private static void requireFreshArchives(Path a)throws IOException{
        if(!archiveLeaves(a).isEmpty())
            throw new IOException("R59_PREEXISTING_ARCHIVE_NEGATIVE");
    }
    private static void validateHistoryTransition(
        Observation prior,Observation next
    )throws IOException{
        if(next.sequence!=prior.sequence+1||
           !next.previousHeadSha.matches("[0-9a-f]{64}"))
            throw new IOException(
                "R59_HISTORICAL_SEQUENCE_LINK_INVALID");
        boolean sameImage=next.imageSequence==prior.imageSequence&&
            next.imageSha.equals(prior.imageSha)&&
            next.imageLength==prior.imageLength;
        boolean valid=false;
        if(prior.phase==Phase.CLOSED){
            valid=next.phase==Phase.OWNED&&
                prior.epoch<Long.MAX_VALUE&&next.epoch==prior.epoch+1&&
                next.worldGeneration>0&&!"none".equals(next.ownerNonce)&&
                sameImage;
        }else if(prior.phase==Phase.OWNED){
            valid=next.phase==Phase.FINAL_PENDING&&
                next.epoch==prior.epoch&&
                next.worldGeneration==prior.worldGeneration&&
                next.ownerNonce.equals(prior.ownerNonce)&&sameImage;
        }else if(prior.phase==Phase.FINAL_PENDING){
            valid=next.phase==Phase.CLOSED&&
                next.epoch==prior.epoch&&next.worldGeneration==0&&
                "none".equals(next.ownerNonce)&&
                next.imageSequence==next.sequence;
        }
        if(!valid)
            throw new IOException(
                "R59_HISTORICAL_PHASE_OR_OWNER_LINK_NEGATIVE");
    }

    /**
     * R60: an IMAGE at an earlier sequence is not harmless merely
     * because no currently selected HEAD references its digest.
     * All historical image files at <= current image sequence must
     * correspond exactly to a validated archived HEAD reference.
     *
     * Leave correctly formed FUTURE images to the original R57
     * negative-orphan check, preserving that diagnostic boundary.
     */
    private static void requireExactHistoricalImageInventory(
        Path account,int currentImageSequence,
        java.util.SortedSet<String> expected
    )throws IOException{
        final String imagePrefix=prefix(account)+IMAGE_PREFIX;
        java.util.SortedSet<String> actual=
            new java.util.TreeSet<String>();
        try(java.nio.file.DirectoryStream<Path> children=
                Files.newDirectoryStream(account.getParent())){
            for(Path child:children){
                String name=child.getFileName().toString();
                if(!name.startsWith(imagePrefix))continue;
                String rest=name.substring(imagePrefix.length());
                if(!rest.matches("[0-9]{4}-[0-9a-f]{64}\\.snapshot"))
                    throw new IOException(
                        "R60_MALFORMED_HISTORICAL_IMAGE_LEAF_NEGATIVE");
                int sequence=Integer.parseInt(rest.substring(0,4));
                if(sequence>currentImageSequence)continue;
                actual.add(name);
                if(actual.size()>MAX_SEQUENCE+1)
                    throw new IOException(
                        "R60_HISTORICAL_IMAGE_SET_UNBOUNDED_NEGATIVE");
            }
        }
        if(!actual.equals(expected))
            throw new IOException(
                "R60_UNREFERENCED_HISTORICAL_IMAGE_NEGATIVE");
    }
    private static void validateArchivedHeadHistory(
        Path a,Observation current,byte[] headBytes,
        java.util.List<String> receiptHashes
    )throws IOException{
        java.util.SortedSet<String> expected=
            new java.util.TreeSet<String>();
        for(int i=0;i<receiptHashes.size();i++)
            expected.add(archivedHead(a,i,receiptHashes.get(i))
                .getFileName().toString());
        if(!expected.equals(archiveLeaves(a)))
            throw new IOException(
                "R59_ORPHAN_OR_MISSING_ARCHIVE_MANUAL_REVIEW");
        Observation previous=null;
        String lastDigest=ZERO;
        byte[] lastBytes=null;
        java.util.SortedSet<String> historicalImages=
            new java.util.TreeSet<String>();
        for(int i=0;i<receiptHashes.size();i++){
            String expectedHash=receiptHashes.get(i);
            byte[] record=read(archivedHead(a,i,expectedHash),MAX_HEAD);
            if(!sha(record).equals(expectedHash))
                throw new IOException(
                    "R59_ARCHIVED_HEAD_DIGEST_NEGATIVE");
            Observation historic=decode(a,record);
            if(historic.sequence!=i||
               !historic.previousHeadSha.equals(lastDigest))
                throw new IOException(
                    "R59_HISTORICAL_HEAD_PREDECESSOR_NEGATIVE");
            if(previous==null){
                if(i!=0||historic.epoch!=1||
                   historic.phase!=Phase.CLOSED||
                   historic.worldGeneration!=0||
                   !"none".equals(historic.ownerNonce)||
                   historic.imageSequence!=0)
                    throw new IOException(
                        "R59_HISTORICAL_GENESIS_NEGATIVE");
            }else{
                validateHistoryTransition(previous,historic);
            }
            final Path snapshot=image(
                a,historic.imageSequence,historic.imageSha);
            historicalImages.add(snapshot.getFileName().toString());
            byte[] imageBytes;
            try{
                imageBytes=read(snapshot,MAX_IMAGE);
            }catch(IOException unsafe){
                throw new IOException(
                    "R59_HISTORICAL_IMAGE_UNREADABLE_NEGATIVE",unsafe);
            }
            if(imageBytes.length!=historic.imageLength||
               !sha(imageBytes).equals(historic.imageSha)||
               !Arrays.equals(imageBytes,read(snapshot,MAX_IMAGE)))
                throw new IOException(
                    "R59_HISTORICAL_IMAGE_TAMPERED_NEGATIVE");
            if(!Arrays.equals(record,
                    read(archivedHead(a,i,expectedHash),MAX_HEAD)))
                throw new IOException(
                    "R59_ARCHIVE_CHANGED_DURING_READ_NEGATIVE");
            previous=historic;
            lastDigest=expectedHash;
            lastBytes=record;
        }
        if(lastBytes==null||previous==null||
           !Arrays.equals(lastBytes,headBytes)||
           previous.sequence!=current.sequence||
           previous.epoch!=current.epoch||
           previous.phase!=current.phase||
           !previous.imageSha.equals(current.imageSha))
            throw new IOException(
                "R59_CURRENT_HEAD_NOT_ARCHIVED_NEGATIVE");
        if(!expected.equals(archiveLeaves(a)))
            throw new IOException(
                "R59_ARCHIVE_DIRECTORY_CHANGED_NEGATIVE");
        requireExactHistoricalImageInventory(
            a,current.imageSequence,historicalImages);
    }
    private static byte[] encode(Observation h)throws IOException{
        String canonical=MAGIC+"\n"+
            "accountSha256="+sha(h.account.toString().getBytes(
                StandardCharsets.UTF_8))+"\n"+
            "epoch="+h.epoch+"\n"+
            "sequence="+h.sequence+"\n"+
            "phase="+h.phase.name()+"\n"+
            "generation="+h.worldGeneration+"\n"+
            "ownerNonce="+h.ownerNonce+"\n"+
            "imageSequence="+h.imageSequence+"\n"+
            "imageSha256="+h.imageSha+"\n"+
            "imageLength="+h.imageLength+"\n"+
            "previousHeadSha256="+h.previousHeadSha+"\n"+
            "authority=LAB_ONLY_NO_ADMISSION_OR_RELEASE\n";
        return canonical.getBytes(StandardCharsets.US_ASCII);
    }
    private static Observation decode(Path a,byte[] bytes)throws IOException{
        String[] lines=new String(bytes,StandardCharsets.US_ASCII)
            .split("\n",-1);
        if(lines.length!=13||!MAGIC.equals(lines[0])||
           !lines[1].equals("accountSha256="+sha(
               a.toString().getBytes(StandardCharsets.UTF_8)))||
           !lines[2].startsWith("epoch=")||
           !lines[3].startsWith("sequence=")||
           !lines[4].startsWith("phase=")||
           !lines[5].startsWith("generation=")||
           !lines[6].startsWith("ownerNonce=")||
           !lines[7].startsWith("imageSequence=")||
           !lines[8].startsWith("imageSha256=")||
           !lines[9].startsWith("imageLength=")||
           !lines[10].startsWith("previousHeadSha256=")||
           !lines[11].equals("authority=LAB_ONLY_NO_ADMISSION_OR_RELEASE")||
           !lines[12].isEmpty())
            throw new IOException("R57_MALFORMED_HEAD_MANUAL_REVIEW");
        try{
            long epoch=Long.parseLong(lines[2].substring(6));
            int seq=Integer.parseInt(lines[3].substring(9));
            Phase phase=Phase.valueOf(lines[4].substring(6));
            long generation=Long.parseLong(lines[5].substring(11));
            String nonce=lines[6].substring(11);
            int imgSeq=Integer.parseInt(lines[7].substring(14));
            String digest=lines[8].substring(12);
            int length=Integer.parseInt(lines[9].substring(12));
            String prior=lines[10].substring(19);
            if(epoch<1||seq<0||seq>MAX_SEQUENCE||
               imgSeq<0||imgSeq>seq||
               generation<0||length<1||length>MAX_IMAGE||
               !digest.matches("[0-9a-f]{64}")||
               !prior.matches("[0-9a-f]{64}")||
               !nonce.matches("none|[a-z0-9_-]{8,64}")||
               (phase==Phase.CLOSED&&(generation!=0||!"none".equals(nonce)))||
               (phase!=Phase.CLOSED&&(generation<=0||"none".equals(nonce))))
                throw new IllegalArgumentException("invalid canonical fields");
            Observation obs=new Observation(a,epoch,seq,phase,
                generation,nonce,imgSeq,digest,length,prior);
            if(!Arrays.equals(bytes,encode(obs)))
                throw new IllegalArgumentException("noncanonical record");
            return obs;
        }catch(IllegalArgumentException invalid){
            throw new IOException("R57_INVALID_HEAD_FIELDS_MANUAL_REVIEW",
                invalid);
        }
    }
    /** A parsed CLOSED phase NEVER grants login or World admission. */
    static Observation inspect(Path account)throws IOException{
        Path a=canonical(account);
        parent(a);
        LocalNegativeAccountHeadPrototype.requireNoPublishedHead(a);
        byte[] first;
        try{first=read(head(a),MAX_HEAD);}
        catch(NoSuchFileException missing){
            throw new IOException("R57_NO_LEDGER_HEAD_MANUAL_REVIEW",missing);
        }
        Observation current=decode(a,first);
        // R58.1: reject a rolled-back CLOSED/OWNED HEAD against the
        // complete immutable intent/receipt set BEFORE interpreting
        // an old imageSequence as an R57 orphan-future-image issue.
        // A second journal audit below still brackets the image reads
        // and detects changed sidecar observations.
        inspectJournal(a,current,first);
        scan(a,current.imageSequence,current.imageSha,true);
        byte[] imageBytes=read(image(a,current.imageSequence,
            current.imageSha),MAX_IMAGE);
        if(imageBytes.length!=current.imageLength||
           !sha(imageBytes).equals(current.imageSha))
            throw new IOException("R57_CURRENT_IMAGE_TAMPERED_MANUAL_REVIEW");
        if(!Arrays.equals(first,read(head(a),MAX_HEAD))||
           !Arrays.equals(imageBytes,read(image(a,current.imageSequence,
               current.imageSha),MAX_IMAGE)))
            throw new IOException("R57_POSTREAD_HEAD_IMAGE_CHANGED");
        scan(a,current.imageSequence,current.imageSha,true);
        inspectJournal(a,current,first);
        return current;
    }
    private static void publish(Path a,Observation next,
                                Cutpoint cuts)throws IOException{
        byte[] exact=encode(next);
        // Before ANY HEAD rename, persist permanent evidence that the
        // attempted sequence exists. An orphan intent must veto even
        // when the old HEAD survived a process/host crash.
        final String newHeadSha=sha(exact);
        final String previousReceiptSha;
        if(next.sequence==0){
            if(exists(head(a)))
                throw new IOException("R58_GENESIS_HEAD_ALREADY_PRESENT");
            requireFreshJournal(a);
            if(!ZERO.equals(next.previousHeadSha))
                throw new IOException("R58_GENESIS_PREVIOUS_HEAD_INVALID");
            previousReceiptSha=ZERO;
        }else{
            if(!exists(head(a))||
               !sha(read(head(a),MAX_HEAD))
                    .equals(next.previousHeadSha))
                throw new IOException("R58_OLD_HEAD_CHANGED_BEFORE_INTENT");
            previousReceiptSha=sha(read(
                journalLeaf(a,next.sequence-1,true),MAX_HEAD));
        }
        Path intent=journalLeaf(a,next.sequence,false);
        Path receipt=journalLeaf(a,next.sequence,true);
        if(exists(intent)||exists(receipt))
            throw new IOException("R58_JOURNAL_STEP_ALREADY_PRESENT");
        byte[] intentBytes=journalRecord(next.sequence,newHeadSha,
            next.previousHeadSha,previousReceiptSha,false);
        byte[] receiptBytes=journalRecord(next.sequence,newHeadSha,
            next.previousHeadSha,previousReceiptSha,true);
        // Persist the complete proposed record first. A crash here
        // leaves an orphan archive (permanent negative), even with
        // NO intent yet, instead of silently allowing the old CLOSED
        // head to be reused.
        Path candidate=archivedHead(a,next.sequence,newHeadSha);
        if(exists(candidate))
            throw new IOException("R59_ARCHIVE_STEP_ALREADY_PRESENT");
        forcedCreate(candidate,exact,MAX_HEAD);
        cuts.reached(Stage.ARCHIVED_HEAD_FORCED);
        forcedCreate(intent,intentBytes,MAX_HEAD);
        cuts.reached(Stage.INTENT_FORCED);
        Path temp=Files.createTempFile(
            a.getParent(),prefix(a)+TEMP_PREFIX,".tmp");
        BasicFileAttributes inode=Files.readAttributes(temp,
            BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        try(FileChannel c=FileChannel.open(temp,StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
                LinkOption.NOFOLLOW_LINKS)){
            ByteBuffer b=ByteBuffer.wrap(exact);
            while(b.hasRemaining())c.write(b);
            c.force(true);
        }
        if(!inode.fileKey().equals(Files.readAttributes(temp,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).fileKey())||
           !Arrays.equals(exact,read(temp,MAX_HEAD)))
            throw new IOException("R57_STAGED_HEAD_DRIFT");
        cuts.reached(Stage.HEAD_TEMP_FORCED);
        LocalNegativeAccountHeadPrototype.requireNoPublishedHead(a);
        try{
            Files.move(temp,head(a),StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        }catch(AtomicMoveNotSupportedException unsupported){
            throw new IOException("R57_ATOMIC_HEAD_REQUIRED",unsupported);
        }
        cuts.reached(Stage.HEAD_MOVED_BEFORE_DIR_FORCE);
        forceDir(a);
        cuts.reached(Stage.HEAD_DIR_FORCED);
        if(!Arrays.equals(exact,read(head(a),MAX_HEAD))||
           !Arrays.equals(intentBytes,read(intent,MAX_HEAD)))
            throw new IOException(
                "R58_HEAD_OR_INTENT_POSTMOVE_UNCONFIRMED");
        // Only after HEAD directory force + exact readback may the
        // independent forced receipt be created. No auto-completion
        // of an orphan INTENT occurs on restart.
        forcedCreate(receipt,receiptBytes,MAX_HEAD);
        cuts.reached(Stage.RECEIPT_FORCED);
        if(!Arrays.equals(exact,read(head(a),MAX_HEAD)))
            throw new IOException("R57_HEAD_POSTMOVE_UNCONFIRMED");
        Observation inspected=inspect(a);
        if(inspected.sequence!=next.sequence||
           inspected.epoch!=next.epoch||
           !inspected.imageSha.equals(next.imageSha)||
           inspected.phase!=next.phase)
            throw new IOException("R57_HEAD_RECEIPT_UNCERTAIN");
    }
    private static Observation transition(
        Path account,int expected,Phase required,Phase next,
        long generation,String nonce,byte[] newImage,Cutpoint cuts
    )throws IOException{
        requiredLock();
        if(cuts==null||account==null)
            throw new IOException("R57_NULL_TRANSITION");
        Path a=canonical(account);
        try(LocalCrossJvmAccountSessionLock.Handle lock=
                LocalCrossJvmAccountSessionLock.acquire(a)){
            return lock.withHeldOriginalAccount(a,()->
                transitionInsideHeldLock(a,expected,required,next,
                    generation,nonce,newImage,cuts,lock));
        }
    }

    /** The already-acquired cooperative lock must not be reacquired. */
    private static Observation transitionInsideHeldLock(
        Path a,int expected,Phase required,Phase next,
        long generation,String nonce,byte[] newImage,Cutpoint cuts,
        LocalCrossJvmAccountSessionLock.Handle lock
    )throws IOException{
            lock.requireOriginalLockNameStillPresent();
            Observation prior=inspect(a);
            if(expected!=prior.sequence)
                throw new IOException("R57_STALE_SEQUENCE_DENIED");
            if(prior.phase!=required)
                throw new IOException("R57_WRONG_PHASE_DENIED");
            if(prior.sequence==MAX_SEQUENCE)
                throw new IOException("R57_SEQUENCE_LIMIT_REVIEW");
            if(next==Phase.OWNED&&required==Phase.CLOSED){
                if(generation<=0||nonce==null||
                   !nonce.matches("[a-z0-9_-]{8,64}")||
                   prior.epoch==Long.MAX_VALUE)
                    throw new IOException("R57_CLAIM_TOKEN_INVALID");
            }else if(prior.worldGeneration!=generation||
                     !Objects.equals(prior.ownerNonce,nonce)){
                throw new IOException("R57_OWNER_CONTINUITY_DENIED");
            }
            if(newImage!=null&&(newImage.length==0||
                    newImage.length>MAX_IMAGE))
                throw new IOException("R57_IMAGE_SIZE_INVALID");
            if(newImage!=null&&next!=Phase.CLOSED)
                throw new IOException("R57_ONLY_FINAL_SETTLEMENT_WRITES_IMAGE");
            if(next==Phase.CLOSED&&newImage==null)
                throw new IOException("R57_FINAL_IMAGE_REQUIRED");
            int seq=prior.sequence+1;
            int imgSeq=newImage==null?prior.imageSequence:seq;
            String imageSha=newImage==null?prior.imageSha:sha(newImage);
            int imageLen=newImage==null?prior.imageLength:newImage.length;
            long epoch=required==Phase.CLOSED?prior.epoch+1:prior.epoch;
            long nextGen=next==Phase.CLOSED?0:generation;
            String nextOwner=next==Phase.CLOSED?"none":nonce;
            String priorSha=sha(read(head(a),MAX_HEAD));
            Observation after=new Observation(a,epoch,seq,next,nextGen,
                nextOwner,imgSeq,imageSha,imageLen,priorSha);
            if(newImage!=null){
                Path imagePath=image(a,imgSeq,imageSha);
                if(exists(imagePath))
                    throw new IOException("R57_IMAGE_ALREADY_EXISTS");
                forcedCreate(imagePath,newImage,MAX_IMAGE);
                lock.requireOriginalLockNameStillPresent();
                cuts.reached(Stage.SNAPSHOT_FORCED);
                lock.requireOriginalLockNameStillPresent();
            }
            publish(a,after,stage->{
                lock.requireOriginalLockNameStillPresent();
                cuts.reached(stage);
                lock.requireOriginalLockNameStillPresent();
            });
            lock.requireOriginalLockNameStillPresent();
            return inspect(a);
    }

    /**
     * R61 LAB ONLY: borrow an existing G21.185 session lock, rather
     * than deadlocking on a second same-account FileLock. An untrusted
     * generation and nonce remain insufficient for live World admission.
     * No token release, World wiring or positive authorization.
     */
    static Observation claimUnderHeldAccountLock(
        Path account,LocalCrossJvmAccountSessionLock.Handle borrowed,
        int expected,long generation,String nonce
    )throws IOException{
        if(borrowed==null)
            throw new IOException("R61_NO_BORROWED_ACCOUNT_LOCK");
        return transitionWithBorrowedLock(account,borrowed,expected,
            Phase.CLOSED,Phase.OWNED,generation,nonce,null,stage->{});
    }

    static Observation initialize(Path account,byte[] bytes)
        throws IOException{
        requiredLock();
        if(bytes==null||bytes.length==0||bytes.length>MAX_IMAGE)
            throw new IOException("R57_INIT_IMAGE_SIZE_INVALID");
        Path a=canonical(account);
        try(LocalCrossJvmAccountSessionLock.Handle lock=
                LocalCrossJvmAccountSessionLock.acquire(a)){
            lock.requireOriginalLockNameStillPresent();
            parent(a);
            LocalNegativeAccountHeadPrototype.requireNoPublishedHead(a);
            if(exists(head(a)))
                throw new IOException("R57_LEDGER_ALREADY_INITIALIZED");
            scan(a,0,ZERO,false);
            requireFreshJournal(a);
            requireFreshArchives(a);
            String digest=sha(bytes);
            Path snapshot=image(a,0,digest);
            forcedCreate(snapshot,bytes,MAX_IMAGE);
            Observation seed=new Observation(a,1L,0,Phase.CLOSED,0L,
                "none",0,digest,bytes.length,ZERO);
            publish(a,seed,stage->{
                lock.requireOriginalLockNameStillPresent();
            });
            return inspect(a);
        }
    }
    static Observation claim(Path a,int expected,long generation,
                             String nonce)throws IOException{
        return claim(a,expected,generation,nonce,stage->{});
    }
    static Observation claim(Path a,int expected,long generation,
                             String nonce,Cutpoint cuts)throws IOException{
        return transition(a,expected,Phase.CLOSED,Phase.OWNED,
            generation,nonce,null,cuts);
    }
    /**
     * R62: experimental transitions inside the ORIGINAL borrowed OS
     * lock; no second FileChannel, transfer, release or World grant.
     * All caller-made nonce/generation fields remain untrusted.
     */
    private static Observation transitionWithBorrowedLock(
        Path account,LocalCrossJvmAccountSessionLock.Handle borrowed,
        int expected,Phase required,Phase next,long generation,
        String nonce,byte[] snapshot,Cutpoint cutpoint
    )throws IOException{
        requiredLock();
        if(borrowed==null||cutpoint==null)
            throw new IOException("R62_BORROWED_TRANSITION_ARGUMENT_INVALID");
        Path exact=canonical(account);
        return borrowed.withHeldOriginalAccount(exact,()->
            transitionInsideHeldLock(exact,expected,required,next,
                generation,nonce,snapshot,cutpoint,borrowed));
    }

    static Observation prepareFinalUnderHeldAccountLock(
        Path account,LocalCrossJvmAccountSessionLock.Handle borrowed,
        int expected,long generation,String nonce
    )throws IOException{
        return transitionWithBorrowedLock(account,borrowed,expected,
            Phase.OWNED,Phase.FINAL_PENDING,generation,nonce,null,stage->{});
    }

    static Observation settleLabFinalUnderHeldAccountLock(
        Path account,LocalCrossJvmAccountSessionLock.Handle borrowed,
        int expected,long generation,String nonce,byte[] finalImage,
        Cutpoint cutpoint
    )throws IOException{
        return transitionWithBorrowedLock(account,borrowed,expected,
            Phase.FINAL_PENDING,Phase.CLOSED,generation,nonce,finalImage,
            cutpoint);
    }

    static Observation prepareFinal(Path a,int expected,long generation,
                                     String nonce)throws IOException{
        return transition(a,expected,Phase.OWNED,Phase.FINAL_PENDING,
            generation,nonce,null,stage->{});
    }
    static Observation settleLabFinal(Path a,int expected,long generation,
                                      String nonce,byte[] postimage)
        throws IOException{
        return settleLabFinal(a,expected,generation,nonce,postimage,
            stage->{});
    }
    static Observation settleLabFinal(Path a,int expected,long generation,
                                      String nonce,byte[] postimage,
                                      Cutpoint cuts)throws IOException{
        return transition(a,expected,Phase.FINAL_PENDING,Phase.CLOSED,
            generation,nonce,postimage,cuts);
    }
}
