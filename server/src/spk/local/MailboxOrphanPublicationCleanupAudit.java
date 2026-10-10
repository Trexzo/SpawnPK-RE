package spk.local;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * G21.99: bounded, explicit, NON-DESTRUCTIVE publication temp audit.
 *
 * An advisory publication lock does NOT fence an active strict writer's
 * pre-lock temporary serialization. Nor do a filename, inode, or age
 * prove that another JVM will never resume writing. Consequently NO
 * entry grants deletion, cleanup, repair, adoption, or reward authority.
 */
final class MailboxOrphanPublicationCleanupAudit {
    static final int MAX_TEMP_CANDIDATES=48;
    private static final long LOCK_BUDGET_MS=1500L;

    enum Kind { STRICT_ACCOUNT, PREPARED_INTENT, DISK_COMMIT }
    enum Disposition {
        POSSIBLY_ACTIVE_OR_ORPHAN_KEEP,
        CANONICAL_HARDLINK_ALIAS_KEEP,
        NONREGULAR_OR_SYMLINK_KEEP,
        MISSING_PHYSICAL_IDENTITY_KEEP
    }
    static final class Candidate {
        final Path path;
        final Kind kind;
        final Disposition disposition;
        final String fileKey;
        final long size;
        final long modifiedMillis;
        // Hard false even for old, apparently unlinked, fully serialized
        // temps. No deletion path is exposed by this class.
        final boolean unlinkAuthorized=false;
        final boolean authoritative=false;
        private Candidate(
            Path p,Kind k,Disposition d,
            String key,long length,long modified
        ){
            path=p;
            kind=k;
            disposition=d;
            fileKey=key;
            size=length;
            modifiedMillis=modified;
        }
    }
    static final class Result {
        final String account;
        final List<Candidate> candidates;
        final boolean cleanupAuthorized=false;
        final boolean unlinkAuthorized=false;
        final boolean accountMutationAuthorized=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean restartAdmissionAuthorized=false;
        final boolean clientAckAuthorized=false;
        private Result(String name,List<Candidate> observed){
            account=name;
            candidates=Collections.unmodifiableList(
                new ArrayList<>(observed));
        }
        int count(Disposition d){
            int n=0;
            for(Candidate c:candidates)
                if(c.disposition==d)n++;
            return n;
        }
        int count(Kind kind){
            int n=0;
            for(Candidate c:candidates)
                if(c.kind==kind)n++;
            return n;
        }
    }

    private final FilePlayerRepository.PathResolver resolver;
    MailboxOrphanPublicationCleanupAudit(
        FilePlayerRepository.PathResolver paths
    ){
        resolver=Objects.requireNonNull(paths,"account resolver");
    }

    Result inspect(String account)throws IOException{
        if(account==null||!account.matches("[a-z0-9_-]{1,64}"))
            throw new IllegalArgumentException(
                "G21.99 noncanonical cleanup audit account");
        final Path selected=Objects.requireNonNull(
            resolver.resolve(account),"G21.99 account file")
            .toAbsolutePath().normalize();
        if(selected.getParent()==null)
            throw new IOException("G21.99 no account parent");
        // Reuse G21.73 verified NOFOLLOW ancestry and bounded JVM+OS
        // advisory lock. This excludes cooperating PUBLICATION phases,
        // but notably NOT any writer stalled before entering that lock.
        return MailboxAccountPublicationCoordinator
            .withExclusivePublicationBounded(
                selected,LOCK_BUDGET_MS,()->inspectLocked(account,selected));
    }

    private Result inspectLocked(String account,Path selected)
        throws IOException{
        final Path parent=selected.getParent();
        final String filename=selected.getFileName().toString();
        final Path journal=selected.resolveSibling(
            filename+MailboxDurableIdempotencyIntentJournal.SUFFIX);
        final Path commit=selected.resolveSibling(
            filename+MailboxGuardedDiskCommitRecord.SUFFIX);
        final Path[] canonical={selected,journal,commit};
        BasicFileAttributes[] before=new BasicFileAttributes[3];
        for(int i=0;i<canonical.length;i++)
            before[i]=optionalRegularEvidence(canonical[i]);

        final List<Candidate> seen=new ArrayList<>();
        scanKind(parent,filename+".g2123-",Kind.STRICT_ACCOUNT,
            canonical,before,seen);
        scanKind(parent,filename+
            MailboxDurableIdempotencyIntentJournal.SUFFIX+
            ".write-",Kind.PREPARED_INTENT,canonical,before,seen);
        scanKind(parent,filename+
            MailboxGuardedDiskCommitRecord.SUFFIX+
            ".write-",Kind.DISK_COMMIT,canonical,before,seen);

        for(int i=0;i<canonical.length;i++){
            BasicFileAttributes now=optionalRegularEvidence(canonical[i]);
            if(!samePhysicalWitness(before[i],now))
                throw new IOException(
                    "G21.99 CANONICAL_CHANGED_DURING_AUDIT_NO_UNLINK");
        }
        seen.sort(Comparator.comparing(c->c.path.toString()));
        return new Result(account,seen);
    }

    private static void scanKind(
        Path parent,String prefix,Kind kind,
        Path[] canonical,BasicFileAttributes[] before,
        List<Candidate> into
    )throws IOException{
        // Exact account-qualified filename pattern, not a directory
        // sweep of other players' temporary files.
        try(DirectoryStream<Path> files=Files.newDirectoryStream(
                parent,prefix+"*.tmp")){
            for(Path file:files){
                if(into.size()>=MAX_TEMP_CANDIDATES)
                    throw new IOException(
                        "G21.99 TEMP_AUDIT_CAPACITY_NO_UNLINK");
                final String name=file.getFileName().toString();
                if(!name.startsWith(prefix)||
                   !name.endsWith(".tmp"))continue;
                BasicFileAttributes first=Files.readAttributes(
                    file,BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
                final Disposition state;
                final String fileKey=first.fileKey()==null?
                    null:first.fileKey().toString();
                if(!first.isRegularFile()||first.isSymbolicLink())
                    state=Disposition.NONREGULAR_OR_SYMLINK_KEEP;
                else if(first.fileKey()==null)
                    state=Disposition.MISSING_PHYSICAL_IDENTITY_KEEP;
                else{
                    boolean alias=false;
                    for(BasicFileAttributes evidence:before)
                        if(evidence!=null&&evidence.fileKey()!=null&&
                           first.fileKey().equals(evidence.fileKey()))
                            alias=true;
                    state=alias
                        ?Disposition.CANONICAL_HARDLINK_ALIAS_KEEP
                        :Disposition.POSSIBLY_ACTIVE_OR_ORPHAN_KEEP;
                }
                BasicFileAttributes second=Files.readAttributes(
                    file,BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
                if(!samePhysicalWitness(first,second))
                    throw new IOException(
                        "G21.99 TEMP_CHANGED_DURING_AUDIT_NO_UNLINK");
                into.add(new Candidate(
                    file.toAbsolutePath().normalize(),kind,state,fileKey,
                    first.size(),first.lastModifiedTime().toMillis()));
            }
        }catch(NoSuchFileException changed){
            throw new IOException(
                "G21.99 TEMP_VANISHED_DURING_AUDIT_NO_UNLINK",changed);
        }
    }

    private static BasicFileAttributes optionalRegularEvidence(Path p)
        throws IOException{
        try{
            BasicFileAttributes a=Files.readAttributes(
                p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(!a.isRegularFile()||a.isSymbolicLink()||
               a.fileKey()==null)
                throw new IOException(
                    "G21.99 CANONICAL_NONREGULAR_OR_UNKEYED_NO_UNLINK");
            return a;
        }catch(NoSuchFileException absent){
            return null;
        }
    }

    private static boolean samePhysicalWitness(
        BasicFileAttributes a,BasicFileAttributes b
    ){
        if(a==null||b==null)return a==null&&b==null;
        return a.isRegularFile()==b.isRegularFile()&&
            a.isSymbolicLink()==b.isSymbolicLink()&&
            Objects.equals(a.fileKey(),b.fileKey())&&
            a.size()==b.size()&&
            Objects.equals(a.lastModifiedTime(),b.lastModifiedTime());
    }
}
