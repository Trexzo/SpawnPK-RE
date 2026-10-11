package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * G21.114: separately versioned, immutable opt-in writer-kind companions
 * for G21.103 v1 UUIDs. This is attribution of OBSERVED opt-in calls,
 * not authentication of provenance or whole-version writer coverage.
 *
 * V1 leaves and its G21.105 portable archive stay byte-for-byte intact.
 * No cleanup/unlink/pruning/grant/session/replay/release/ACK authority.
 */
final class MailboxWriterKindEvidenceV2 {
    static final String PROTOCOL="SPK-G21114-WRITER-KIND-V2";
    private static final String PREFIX=".g21114-writer-kind-";
    private static final int MAX_LEAVES=32;
    private static final int MAX_BYTES=512;
    private static final long LOCK_MS=1500L;
    private static final String UUID=
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"+
        "[0-9a-f]{4}-[0-9a-f]{12}";

    enum Kind {
        STRICT_PREPARED, PREPARED_JOURNAL,
        WORLD_TERMINAL, DISK_COMMIT,
        // G21.118: opt-in cooperating DIAGNOSTIC attempt only.
        // NOT a fifth business/World publication kind and NOT
        // proof of whole-version, future or legacy participation.
        COOPERATING_DIAGNOSTIC
    }
    enum Status {
        NO_TYPED_EVIDENCE_UNKNOWN,
        OBSERVED_TYPED_PAIRED_NO_AUTHORITY,
        MIXED_TYPED_AND_UNTYPED_UNKNOWN,
        INCOMPLETE_TYPED_NO_AUTHORITY
    }
    static final class Report {
        final Status status;
        final int typedBegins,typedCompletes;
        final int untypedV1Leaves;
        final Map<Kind,Integer> pairedByKind;
        final boolean sourceAuthenticityVerified=false;
        final boolean completeHistoricalWriterPopulation=false;
        final boolean defaultWritersEnrolled=false;
        final boolean allFutureWritersExcluded=false;
        final boolean crashEpochProved=false;
        final boolean cleanupAuthorized=false;
        final boolean unlinkAuthorized=false;
        final boolean rolloverAuthorized=false;
        final boolean pruningAuthorized=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean sessionAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;
        private Report(
            Status s,int b,int c,int u,Map<Kind,Integer> pair
        ){
            status=s;typedBegins=b;typedCompletes=c;
            untypedV1Leaves=u;
            EnumMap<Kind,Integer> copied=new EnumMap<>(Kind.class);
            copied.putAll(pair);
            pairedByKind=java.util.Collections.unmodifiableMap(copied);
        }
    }
    private static String checked(String a){
        if(a==null||!a.matches("[a-z0-9_-]{1,64}"))
            throw new IllegalArgumentException(
                "G21.114 INVALID_ACCOUNT_NO_AUTHORITY");
        return a;
    }
    private static String prefix(Path file){
        return file.getFileName().toString()+PREFIX;
    }
    private static Path path(Path file,String uuid,boolean begin){
        return file.resolveSibling(prefix(file)+"v2-"+uuid+
            (begin?".begin":".complete"));
    }
    private static String payload(
        String account,String uuid,Kind kind,boolean begin
    ){
        return PROTOCOL+"\nACCOUNT="+account+"\nATTEMPT="+uuid+
            "\nKIND="+kind.name()+"\nSTAGE="+
            (begin?"BEGIN":"COMPLETE")+"\n";
    }
    private static IOException unsafe(String detail){
        return new IOException(
            "G21.114 TYPED_WRITER_EVIDENCE_UNSAFE_NO_CLEANUP "+detail);
    }
    private static BasicFileAttributes attrs(Path p)throws IOException{
        BasicFileAttributes x=Files.readAttributes(
            p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!x.isRegularFile()||x.isSymbolicLink()||
           x.fileKey()==null||x.size()<1||x.size()>MAX_BYTES)
            throw unsafe("NONREGULAR_OR_OVERSIZE "+p.getFileName());
        return x;
    }
    private static String read(
        Path path,BasicFileAttributes expected
    )throws IOException{
        ByteBuffer buffer=ByteBuffer.allocate((int)expected.size()+1);
        try(FileChannel channel=FileChannel.open(
            path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            while(buffer.hasRemaining()){
                int n=channel.read(buffer);
                if(n<0)break;
                if(n==0)throw unsafe("STALLED_READ");
            }
        }
        if(buffer.position()!=expected.size())
            throw unsafe("CHANGED_LENGTH");
        return new String(buffer.array(),0,(int)expected.size(),
            StandardCharsets.US_ASCII);
    }
    private static void forceParent(Path parent)throws IOException{
        try(FileChannel d=FileChannel.open(parent,
            StandardOpenOption.READ)){d.force(true);}
    }

    /** Called only while the parent-owned G21.100 lifetime is held. */
    static void recordInside(
        Path file,String account,String uuid,Kind kind,boolean begin
    )throws IOException{
        Objects.requireNonNull(file,"file");
        Objects.requireNonNull(kind,"kind");
        checked(account);
        if(uuid==null||!uuid.matches(UUID))
            throw unsafe("NONCANONICAL_UUID");
        final Path target=path(file,uuid,begin);
        final Path parent=target.getParent();
        final Path temp=Files.createTempFile(parent,
            file.getFileName()+".g21114-kind-stage-",".tmp");
        try{
            byte[] bytes=payload(account,uuid,kind,begin)
                .getBytes(StandardCharsets.US_ASCII);
            try(FileChannel out=FileChannel.open(
                    temp,StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)){
                ByteBuffer b=ByteBuffer.wrap(bytes);
                while(b.hasRemaining())out.write(b);
                out.force(true);
            }
            // Immutable create-only hard link, no overwrites of evidence.
            Files.createLink(target,temp);
            forceParent(parent);
        }finally{
            // The staging link, NOT durable v1/v2 evidence, may be removed.
            Files.deleteIfExists(temp);
        }
    }

    private static final class Pair {
        Kind kind;
        int flags;
    }

    /** Bounded nofollow scan, requires already-held G21.100 lifecycle. */
    static Report inspectInside(Path selected,String account)
        throws IOException{
        checked(account);
        final Path file=Objects.requireNonNull(selected,"selected")
            .toAbsolutePath().normalize();
        MailboxWriterAttemptEvidenceV1.Report v1=
            MailboxWriterAttemptEvidenceV1.inspectInside(file,account);
        Map<String,Pair> map=new HashMap<>();
        String prefix=prefix(file);
        int leaves=0;
        try(DirectoryStream<Path> found=Files.newDirectoryStream(
                file.getParent(),prefix+"*")){
            for(Path p:found){
                if(++leaves>MAX_LEAVES)
                    throw unsafe("TYPED_LEDGER_OVER_CAPACITY");
                String filename=p.getFileName().toString();
                String suffix=filename.substring(prefix.length());
                if(!suffix.matches("v2-"+UUID+"\\.(begin|complete)"))
                    throw unsafe("UNKNOWN_KIND_VERSION "+filename);
                int dot=suffix.lastIndexOf('.');
                String uuid=suffix.substring(3,dot);
                boolean begin=suffix.endsWith(".begin");
                BasicFileAttributes first=attrs(p);
                String text=read(p,first);
                BasicFileAttributes last=attrs(p);
                if(!Objects.equals(first.fileKey(),last.fileKey())||
                   first.size()!=last.size()||
                   !first.lastModifiedTime().equals(last.lastModifiedTime()))
                    throw unsafe("CHANGED_PHYSICAL_IDENTITY");
                String[] fields=text.split("\n",-1);
                if(fields.length!=6||
                   !PROTOCOL.equals(fields[0])||
                   !("ACCOUNT="+account).equals(fields[1])||
                   !("ATTEMPT="+uuid).equals(fields[2])||
                   !("STAGE="+(begin?"BEGIN":"COMPLETE"))
                       .equals(fields[4])||
                   !fields[5].isEmpty()||
                   !fields[3].startsWith("KIND="))
                    throw unsafe("INVALID_TYPED_PAYLOAD "+filename);
                Kind kind;
                try{
                    kind=Kind.valueOf(fields[3].substring(5));
                }catch(IllegalArgumentException unknown){
                    throw unsafe("UNKNOWN_WRITER_KIND "+filename);
                }
                if(!payload(account,uuid,kind,begin).equals(text))
                    throw unsafe("NONCANONICAL_KIND_PAYLOAD");
                // V1 must exist and has already passed its own
                // full canonical payload and identity scan.
                Path v1p=file.resolveSibling(
                    file.getFileName()+".g21103-writer-attempt-v1-"+
                    uuid+(begin?".begin":".complete"));
                if(!Files.isRegularFile(v1p,LinkOption.NOFOLLOW_LINKS))
                    throw unsafe("ORPHAN_KIND_WITHOUT_V1 "+filename);
                Pair pair=map.computeIfAbsent(uuid,u->new Pair());
                if(pair.kind!=null&&pair.kind!=kind)
                    throw unsafe("CROSS_KIND_PAIR_DISAGREEMENT");
                pair.kind=kind;
                int flag=begin?1:2;
                if((pair.flags&flag)!=0)
                    throw unsafe("DUPLICATE_KIND_STAGE");
                pair.flags|=flag;
            }
        }
        int begins=0,completes=0;
        Map<Kind,Integer> byKind=new EnumMap<>(Kind.class);
        for(Pair pair:map.values()){
            if((pair.flags&1)==0)
                throw unsafe("COMPLETE_WITHOUT_TYPED_BEGIN");
            begins++;
            if((pair.flags&2)!=0){
                completes++;
                byKind.put(pair.kind,
                    byKind.getOrDefault(pair.kind,0)+1);
            }
        }
        if(leaves>v1.recordCount)
            throw unsafe("MORE_TYPED_THAN_V1");
        Status status=begins!=completes
            ?Status.INCOMPLETE_TYPED_NO_AUTHORITY
            :leaves==0?Status.NO_TYPED_EVIDENCE_UNKNOWN
            :leaves<v1.recordCount
                ?Status.MIXED_TYPED_AND_UNTYPED_UNKNOWN
                :Status.OBSERVED_TYPED_PAIRED_NO_AUTHORITY;
        return new Report(status,begins,completes,
            v1.recordCount-leaves,byKind);
    }

    /**
     * G21.115: bounded, in-memory portable capture of exactly the
     * currently observed v2 bytes. No file is written or removed.
     * An accompanying SHA-256 is error detection; expected digest must
     * come from an independently trusted source before reconciliation.
     */
    static final class Snapshot {
        final String portable;
        final String sha256;
        final Report report;
        final boolean sourceAuthenticityProved=false;
        final boolean allWriterVersionsFenced=false;
        final boolean cleanupAuthorized=false;
        final boolean rolloverAuthorized=false;
        private Snapshot(String p,String hash,Report r){
            portable=p;sha256=hash;report=r;
        }
    }
    static Snapshot capture(
        FilePlayerRepository.PathResolver resolver,String account
    )throws IOException{
        Objects.requireNonNull(resolver,"resolver");
        final Path file=Objects.requireNonNull(
            resolver.resolve(checked(account)),"file")
            .toAbsolutePath().normalize();
        return MailboxAccountPublicationCoordinator
            .withExclusivePublicationBounded(
                MailboxPublicationWriterLifecycle.domain(file),
                LOCK_MS,()->captureInside(file,account));
    }

    static Snapshot captureInside(Path selected,String account)
        throws IOException{
        final Path file=Objects.requireNonNull(selected,"selected")
            .toAbsolutePath().normalize();
        final Report first=inspectInside(file,account);
        final String prefix=prefix(file);
        java.util.List<Path> paths=new java.util.ArrayList<>();
        try(DirectoryStream<Path> found=Files.newDirectoryStream(
                file.getParent(),prefix+"*")){
            for(Path p:found){
                if(paths.size()>=MAX_LEAVES)
                    throw unsafe("CAPTURE_OVER_CAPACITY");
                paths.add(p);
            }
        }
        java.util.Collections.sort(paths);
        if(paths.size()!=first.typedBegins+first.typedCompletes)
            throw unsafe("CAPTURE_LEAF_COUNT_CHANGED");
        StringBuilder portable=new StringBuilder();
        portable.append("SPK-G21115-READONLY-TYPED-CAPTURE-V1\n")
            .append("ACCOUNT=").append(checked(account)).append('\n');
        for(Path p:paths){
            BasicFileAttributes before=attrs(p);
            String body=read(p,before);
            BasicFileAttributes after=attrs(p);
            if(!Objects.equals(before.fileKey(),after.fileKey())||
               before.size()!=after.size()||
               !Objects.equals(before.lastModifiedTime(),
                   after.lastModifiedTime()))
                throw unsafe("CAPTURE_CHANGED_PHYSICAL_LEAF");
            portable.append("LEAF|").append(p.getFileName())
                .append('|').append(java.util.Base64.getEncoder()
                    .encodeToString(body.getBytes(
                        StandardCharsets.US_ASCII))).append('\n');
            if(portable.length()>32768)
                throw unsafe("CAPTURE_TOO_LARGE");
        }
        final Report after=inspectInside(file,account);
        if(after.status!=first.status||
           after.typedBegins!=first.typedBegins||
           after.typedCompletes!=first.typedCompletes||
           after.untypedV1Leaves!=first.untypedV1Leaves||
           !after.pairedByKind.equals(first.pairedByKind))
            throw unsafe("CAPTURE_CROSS_SCAN_DRIFT");
        final byte[] digest;
        try{
            digest=java.security.MessageDigest.getInstance("SHA-256")
                .digest(portable.toString().getBytes(
                    StandardCharsets.US_ASCII));
        }catch(java.security.NoSuchAlgorithmException impossible){
            throw new IllegalStateException(impossible);
        }
        StringBuilder hex=new StringBuilder(64);
        for(byte b:digest)
            hex.append(String.format(java.util.Locale.ROOT,
                "%02x",b&255));
        return new Snapshot(portable.toString(),hex.toString(),after);
    }

    /** Do not enroll into a new typed operation over an orphan BEGIN. */
    static void requireAdmissibleInside(Path file,String account)
        throws IOException{
        Report r=inspectInside(file,account);
        if(r.status==Status.INCOMPLETE_TYPED_NO_AUTHORITY)
            throw unsafe("PREVIOUS_TYPED_BEGIN_INCOMPLETE");
    }

    /** Read-only independent lock owner, never returns cleanup authority. */
    static Report inspect(
        FilePlayerRepository.PathResolver resolver,String account
    )throws IOException{
        Objects.requireNonNull(resolver,"resolver");
        Path file=Objects.requireNonNull(
            resolver.resolve(checked(account)),"file")
            .toAbsolutePath().normalize();
        return MailboxAccountPublicationCoordinator
            .withExclusivePublicationBounded(
                MailboxPublicationWriterLifecycle.domain(file),
                LOCK_MS,()->inspectInside(file,account));
    }
    private MailboxWriterKindEvidenceV2(){}
}
