package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * R59 offline-only whole-directory account-to-pair index.
 *
 * One atomically replaced file indexes all unresolved PREPARED R57 intents,
 * with a revision guard and a read-only completeness audit against the same
 * explicit directory. A journal not yet registered (or a damaged index)
 * causes GLOBAL refusal; an unindexed pending pair must never look clear.
 *
 * There is NO live login/trade/save hook, cross-process writer lock,
 * durable COMMITTED state or automatic reconciliation. Revision comparison
 * is a single-JVM guard, NOT an atomic cross-process compare-and-swap.
 */
final class R59TradePairIntentIndex {
    static final String INDEX_FILE="paired-intents.index.properties";
    static final int MAX_ENTRIES=256;
    private static final int MAX_INDEX_BYTES=65536;
    private static final String FORMAT="SpawnPK-R59-PairIndex-v1";
    private static final Pattern USER=Pattern.compile(
        "[a-z][a-z0-9_]{0,31}"
    );
    private static final Pattern INTENT=Pattern.compile(
        "trade-r57-([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"+
        "[0-9a-f]{4}-[0-9a-f]{12})\\.properties"
    );

    enum Status{
        NO_INDEXED_PENDING_PAIR,
        BLOCK_INDEXED_PAIR,
        BLOCK_INDETERMINATE_INDEX
    }

    static final class Result {
        final Status status;
        final long revision;
        final List<UUID> transactions;
        final String reason;

        Result(Status status,long revision,List<UUID> transactions,String reason){
            this.status=status;
            this.revision=revision;
            this.transactions=Collections.unmodifiableList(
                new ArrayList<>(transactions)
            );
            this.reason=reason;
        }
        boolean canProceedToOtherLoginChecks(){
            return status==Status.NO_INDEXED_PENDING_PAIR;
        }
        boolean loginAuthorized(){return false;}
        boolean automaticRecoveryAttempted(){return false;}
    }

    private static final class Pair {
        final String a,b,manifestDigest;
        Pair(String a,String b,String manifestDigest){
            this.a=a;this.b=b;this.manifestDigest=manifestDigest;
        }
        boolean includes(String account){
            return a.equals(account)||b.equals(account);
        }
    }

    private static final class Index {
        final long revision;
        final TreeMap<String,Pair> entries;
        Index(long revision,TreeMap<String,Pair> entries){
            this.revision=revision;this.entries=entries;
        }
    }

    /** Safe only with one cooperative writer. No cross-process CAS claimed. */
    static synchronized long publishRevision(
        Path root,long expectedRevision,List<UUID> includedTransactions
    )throws IOException{
        Path directory=checkedDirectory(root);
        Objects.requireNonNull(includedTransactions,"includedTransactions");
        if(expectedRevision<0||expectedRevision==Long.MAX_VALUE)
            throw new IOException("R59_INDEX_PUBLISH_DENIED revision range");

        Path target=directory.resolve(INDEX_FILE);
        Index previous=null;
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))
            previous=readIndex(target);
        long actual=previous==null?0L:previous.revision;
        if(actual!=expectedRevision)
            throw new IOException("R59_INDEX_PUBLISH_DENIED stale revision");

        if(includedTransactions.size()>MAX_ENTRIES)
            throw new IOException("R59_INDEX_PUBLISH_DENIED too many intents");

        TreeMap<String,Pair> complete=new TreeMap<>();
        for(UUID id:includedTransactions){
            if(id==null)
                throw new IOException("R59_INDEX_PUBLISH_DENIED null ID");
            String key=id.toString();
            if(complete.containsKey(key))
                throw new IOException("R59_INDEX_PUBLISH_DENIED repeated ID");
            Path manifest=directory.resolve(intentName(id));
            R57TradePairJournal.Participants p=
                R57TradePairJournal.readParticipants(manifest);
            if(!id.equals(p.transaction))
                throw new IOException("R59_INDEX_PUBLISH_DENIED transaction mismatch");
            // An unresolved intent is immutable in the registry. A later
            // manifest rewrite must never silently transfer its ownership
            // to different account names during a revision update.
            Pair old=previous==null?null:previous.entries.get(key);
            if(old!=null&&
               (!old.a.equals(p.first)||!old.b.equals(p.second)))
                throw new IOException(
                    "R59_INDEX_PUBLISH_DENIED existing intent identity drift");
            String manifestDigest=digestManifest(manifest);
            if(old!=null&&!old.manifestDigest.equals(manifestDigest))
                throw new IOException(
                    "R59_INDEX_PUBLISH_DENIED existing intent digest drift");
            complete.put(key,new Pair(
                p.first,p.second,manifestDigest
            ));
        }

        // A pending pair must not disappear just because a writer submits
        // a smaller registry. Retiring intents needs a future COMMITTED and
        // replay protocol; R59 has neither.
        if(previous!=null &&
           !complete.keySet().containsAll(previous.entries.keySet()))
            throw new IOException("R59_INDEX_PUBLISH_DENIED unresolved intent removed");

        Index next=new Index(expectedRevision+1,complete);
        verifyComplete(directory,next);

        Properties props=new Properties();
        props.setProperty("format",FORMAT);
        props.setProperty("revision",Long.toString(next.revision));
        props.setProperty("count",Integer.toString(complete.size()));
        int i=0;
        for(Map.Entry<String,Pair> e:complete.entrySet()){
            String prefix="entry."+i+++".";
            props.setProperty(prefix+"id",e.getKey());
            props.setProperty(prefix+"a",e.getValue().a);
            props.setProperty(prefix+"b",e.getValue().b);
            props.setProperty(prefix+"sha256",
                e.getValue().manifestDigest);
        }
        // The R16 publisher rejects ambiguous staging bytes and does an
        // atomic move of forced file bytes. It is NOT an atomic CAS across
        // processes or a parent-directory power-loss durability guarantee.
        AtomicAccountPublisher.publish(
            target,props,"R59 offline paired intent index"
        );
        return next.revision;
    }

    static Result inspect(Path root,String requestedUsername){
        if(requestedUsername==null)
            return blocked("INVALID_USERNAME");
        String username=requestedUsername.trim().toLowerCase(Locale.ROOT);
        if(!USER.matcher(username).matches())
            return blocked("INVALID_USERNAME");
        try{
            Path directory=checkedDirectory(root);
            Index index=readIndex(directory.resolve(INDEX_FILE));
            verifyComplete(directory,index);
            List<UUID> matches=new ArrayList<>();
            for(Map.Entry<String,Pair> entry:index.entries.entrySet()){
                if(entry.getValue().includes(username))
                    matches.add(UUID.fromString(entry.getKey()));
            }
            return new Result(
                matches.isEmpty()?Status.NO_INDEXED_PENDING_PAIR:
                    Status.BLOCK_INDEXED_PAIR,
                index.revision,matches,
                matches.isEmpty()?"VALIDATED_NO_MATCH":
                    "UNRESOLVED_PREPARED_PAIR"
            );
        }catch(IOException|RuntimeException failure){
            return blocked("INDEX_OR_JOURNAL_INDETERMINATE");
        }
    }

    private static Result blocked(String why){
        return new Result(
            Status.BLOCK_INDETERMINATE_INDEX,-1L,
            Collections.emptyList(),why
        );
    }

    private static Path checkedDirectory(Path root)throws IOException{
        if(root==null)
            throw new IOException("R59_INDEX_DENIED null directory");
        Path directory=root.toAbsolutePath().normalize();
        for(Path p=directory;p!=null;p=p.getParent()){
            if(Files.isSymbolicLink(p))
                throw new IOException("R59_INDEX_DENIED symlink directory");
        }
        BasicFileAttributes attrs=Files.readAttributes(
            directory,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
        );
        if(!attrs.isDirectory())
            throw new IOException("R59_INDEX_DENIED non-directory");
        return directory;
    }

    private static String intentName(UUID id){
        return "trade-r57-"+id.toString()+".properties";
    }

    private static Index readIndex(Path target)throws IOException{
        AtomicAccountPublisher.requireNoAmbiguousStage(target);
        BasicFileAttributes attrs=Files.readAttributes(
            target,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
        );
        if(!attrs.isRegularFile()||attrs.size()<=0||
           attrs.size()>MAX_INDEX_BYTES)
            throw new IOException("R59_INDEX_DENIED invalid index file");
        Properties p=new Properties();
        try(InputStream stream=Files.newInputStream(
                target,LinkOption.NOFOLLOW_LINKS)){
            p.load(stream);
        }
        if(!FORMAT.equals(p.getProperty("format")))
            throw new IOException("R59_INDEX_DENIED unknown format");

        long revision=parseCanonicalPositiveLong(p.getProperty("revision"));
        int count=parseCanonicalNonnegativeInt(p.getProperty("count"));
        if(count>MAX_ENTRIES)
            throw new IOException("R59_INDEX_DENIED count too large");

        Set<String> expected=new HashSet<>(Arrays.asList(
            "format","revision","count"
        ));
        TreeMap<String,Pair> entries=new TreeMap<>();
        for(int i=0;i<count;i++){
            String prefix="entry."+i+".";
            expected.add(prefix+"id");
            expected.add(prefix+"a");
            expected.add(prefix+"b");
            expected.add(prefix+"sha256");
            String raw=p.getProperty(prefix+"id");
            UUID id=canonicalUUID(raw);
            String a=p.getProperty(prefix+"a");
            String b=p.getProperty(prefix+"b");
            checkNames(a,b);
            String manifestDigest=p.getProperty(prefix+"sha256");
            if(manifestDigest==null||
               !manifestDigest.matches("[0-9a-f]{64}"))
                throw new IOException("R59_INDEX_DENIED invalid manifest digest");
            if(entries.put(id.toString(),
                new Pair(a,b,manifestDigest))!=null)
                throw new IOException("R59_INDEX_DENIED duplicate transaction");
        }
        if(!p.stringPropertyNames().equals(expected))
            throw new IOException("R59_INDEX_DENIED unexpected/missing keys");
        return new Index(revision,entries);
    }

    private static void verifyComplete(Path directory,Index index)
            throws IOException{
        Set<String> seen=new HashSet<>();
        int count=0;
        try(DirectoryStream<Path> files=Files.newDirectoryStream(directory)){
            for(Path file:files){
                String filename=file.getFileName().toString();
                if(INDEX_FILE.equals(filename))
                    continue;
                if(++count>MAX_ENTRIES)
                    throw new IOException("R59_INDEX_DENIED directory limit");
                Matcher match=INTENT.matcher(filename);
                if(!match.matches())
                    throw new IOException(
                        "R59_INDEX_DENIED unknown or staged journal entry"
                    );
                UUID id=canonicalUUID(match.group(1));
                String key=id.toString();
                Pair indexed=index.entries.get(key);
                if(indexed==null)
                    throw new IOException("R59_INDEX_DENIED unindexed pending intent");
                R57TradePairJournal.Participants actual=
                    R57TradePairJournal.readParticipants(file);
                if(!id.equals(actual.transaction)||
                   !indexed.a.equals(actual.first)||
                   !indexed.b.equals(actual.second)||
                   !indexed.manifestDigest.equals(digestManifest(file)))
                    throw new IOException("R59_INDEX_DENIED manifest conflict");
                if(!seen.add(key))
                    throw new IOException("R59_INDEX_DENIED duplicate manifest");
            }
        }
        if(!seen.equals(index.entries.keySet()))
            throw new IOException("R59_INDEX_DENIED missing indexed intent");
    }

    /**
     * Bind the index to exact immutable manifest bytes. This detects
     * accidental or non-cooperative rewrites between published revisions;
     * it is NOT authentication against someone who can rewrite both files.
     */
    private static String digestManifest(Path file)throws IOException{
        BasicFileAttributes attributes=Files.readAttributes(
            file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
        );
        if(!attributes.isRegularFile()||attributes.size()<=0||
           attributes.size()>8192)
            throw new IOException("R59_INDEX_DENIED invalid manifest size/type");
        byte[] bytes=Files.readAllBytes(file);
        if(bytes.length!=attributes.size())
            throw new IOException("R59_INDEX_DENIED changed manifest size");
        try{
            MessageDigest sha=MessageDigest.getInstance("SHA-256");
            byte[] hash=sha.digest(bytes);
            StringBuilder hex=new StringBuilder(64);
            for(byte one:hash){
                int value=one&255;
                hex.append(Character.forDigit(value>>>4,16));
                hex.append(Character.forDigit(value&15,16));
            }
            return hex.toString();
        }catch(NoSuchAlgorithmException impossible){
            throw new IllegalStateException("SHA-256 unavailable",impossible);
        }
    }

    private static UUID canonicalUUID(String raw)throws IOException{
        if(raw==null)
            throw new IOException("R59_INDEX_DENIED missing ID");
        try{
            UUID parsed=UUID.fromString(raw);
            if(!parsed.toString().equals(raw))
                throw new IOException("R59_INDEX_DENIED noncanonical ID");
            return parsed;
        }catch(IllegalArgumentException e){
            throw new IOException("R59_INDEX_DENIED malformed ID",e);
        }
    }

    private static long parseCanonicalPositiveLong(String raw)
            throws IOException{
        if(raw==null||!raw.matches("[1-9][0-9]*"))
            throw new IOException("R59_INDEX_DENIED invalid revision");
        try{return Long.parseLong(raw);}
        catch(NumberFormatException e){
            throw new IOException("R59_INDEX_DENIED revision overflow",e);
        }
    }

    private static int parseCanonicalNonnegativeInt(String raw)
            throws IOException{
        if(raw==null||!raw.matches("0|[1-9][0-9]*"))
            throw new IOException("R59_INDEX_DENIED invalid count");
        try{return Integer.parseInt(raw);}
        catch(NumberFormatException e){
            throw new IOException("R59_INDEX_DENIED count overflow",e);
        }
    }

    private static void checkNames(String a,String b)throws IOException{
        if(a==null||b==null||
           !USER.matcher(a).matches()||!USER.matcher(b).matches()||
           a.compareTo(b)>=0)
            throw new IOException("R59_INDEX_DENIED invalid participants");
    }

    private R59TradePairIntentIndex(){}
}
