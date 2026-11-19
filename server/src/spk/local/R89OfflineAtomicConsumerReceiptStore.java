package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * R89 OFFLINE TEST REFERENCE, NOT PRODUCTION GAMEPLAY STORAGE.
 *
 * Each individual consumer's numeric state AND operation receipt set are
 * serialized into ONE forced staged file and REQUIRED atomic replacement.
 * This is strictly a single-file state transition, NOT a transaction
 * covering objective/match/collection or the player's account.
 *
 * The operation token is an R86 source-proven *synthetic* fixture token;
 * it is not an authenticated restart-stable SpawnPK event identifier.
 * Review after restart is always READ ONLY / QUARANTINED.
 */
final class R89OfflineAtomicConsumerReceiptStore {
    private static final String FORMAT="SpawnPK-R89-OfflineConsumer-v1";
    private static final int MAX_BYTES=32768;

    enum Fault {
        NONE,
        BEFORE_ATOMIC_PUBLISH,
        AFTER_ATOMIC_PUBLISH_BEFORE_ACK
    }
    enum Result {
        APPLIED,
        DUPLICATE,
        REFUSED_UNVERIFIED,
        REFUSED_CAPACITY,
        REFUSED_CONFLICT,
        INDETERMINATE
    }
    enum Status {
        PRESENT_QUARANTINED,
        MISSING_BLOCKED,
        INDETERMINATE_BLOCKED
    }

    static final class Review {
        final Status status;
        final long observedValue;
        final int observedReceipts;
        final String reason;
        Review(Status status,long value,int count,String reason){
            this.status=status;
            this.observedValue=value;
            this.observedReceipts=count;
            this.reason=reason;
        }
        boolean replayAuthorized(){return false;}
        boolean crossConsumerCommitted(){return false;}
        boolean rewardAuthorized(){return false;}
        boolean loginAuthorized(){return false;}
    }

    private static final class State {
        final long value;
        final LinkedHashMap<UUID,String> receipts;
        State(long value,LinkedHashMap<UUID,String> receipts){
            this.value=value;
            this.receipts=receipts;
        }
    }

    private final Path root;
    private final String consumer;
    private final Path canonical;
    private final Path lockPath;
    private final R89OfflineVerifiedEmissionTokenIssuer issuer;
    private final int maxReceipts;

    R89OfflineAtomicConsumerReceiptStore(
        Path root,String consumer,
        R89OfflineVerifiedEmissionTokenIssuer issuer,int maxReceipts
    ){
        this.root=Objects.requireNonNull(root,"root")
            .toAbsolutePath().normalize();
        this.consumer=checkedConsumer(consumer);
        this.canonical=this.root.resolve(
            "r89-"+this.consumer+".properties"
        );
        this.lockPath=this.root.resolve(
            "r89-"+this.consumer+".lock"
        );
        this.issuer=Objects.requireNonNull(issuer,"issuer");
        if(maxReceipts<1||maxReceipts>128)
            throw new IllegalArgumentException("R89 invalid receipt capacity");
        this.maxReceipts=maxReceipts;
    }

    Result apply(
        CombatOutcome fact,R89OfflineVerifiedEmissionTokenIssuer.Token token,
        long amount,Fault fault
    ){
        Objects.requireNonNull(fault,"fault");
        if(!issuer.isCurrent(fact,token)||
           fact.type()!=CombatOutcomeType.PLAYER_KILL||
           fact.context()!=CombatOutcomeContext.PLAYER_PVP||
           amount<=0L)
            return Result.REFUSED_UNVERIFIED;
        try{
            Files.createDirectories(root);
            if(Files.isSymbolicLink(root)||
               !Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))
                throw new IOException("R89 invalid fixture root");
            // This lock serializes other cooperative R89 writers.
            // It is not authentication of file contents.
            try(FileChannel mutex=FileChannel.open(
                    lockPath,StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
                FileLock lock=mutex.tryLock()){
                if(lock==null)
                    throw new IOException("R89 cooperative lock busy");
                AtomicAccountPublisher.requireNoAmbiguousStage(
                    canonical
                );
                State before=read(canonical,consumer,maxReceipts);
                UUID operation=token.syntheticIdForAudit();
                String payload=fingerprint(fact,amount);
                String old=before.receipts.get(operation);
                if(old!=null)
                    return old.equals(payload)
                        ?Result.DUPLICATE
                        :Result.REFUSED_CONFLICT;
                if(before.receipts.size()>=maxReceipts)
                    return Result.REFUSED_CAPACITY;
                long nextValue;
                try{
                    nextValue=Math.addExact(before.value,amount);
                }catch(ArithmeticException overflow){
                    return Result.REFUSED_CONFLICT;
                }
                if(fault==Fault.BEFORE_ATOMIC_PUBLISH)
                    throw new IOException(
                        "R89_INJECTED_BEFORE_ATOMIC_PUBLISH"
                    );
                LinkedHashMap<UUID,String> updated=
                    new LinkedHashMap<>(before.receipts);
                updated.put(operation,payload);
                // Numeric projection AND matching operation ID are
                // ONE canonical record, never two separate writes.
                Properties next=encode(
                    new State(nextValue,updated),consumer
                );
                AtomicAccountPublisher.publish(
                    canonical,next,
                    "R89 TEST_ONLY atomic single-consumer receipt+value"
                );
                if(fault==Fault.AFTER_ATOMIC_PUBLISH_BEFORE_ACK)
                    throw new IOException(
                        "R89_INJECTED_AFTER_ATOMIC_PUBLISH_BEFORE_ACK"
                    );
                return Result.APPLIED;
            }
        }catch(IOException|RuntimeException failure){
            // On an ambiguous write, do not infer whether the atomic
            // replacement happened; never auto-retry or roll back.
            return Result.INDETERMINATE;
        }
    }

    static Review review(Path root,String consumer){
        String checked=checkedConsumer(consumer);
        Path normalized=Objects.requireNonNull(
            root,"root").toAbsolutePath().normalize();
        Path file=normalized.resolve("r89-"+checked+".properties");
        try{
            if(Files.notExists(normalized,LinkOption.NOFOLLOW_LINKS))
                return new Review(Status.MISSING_BLOCKED,0L,0,
                    "MISSING_ROOT");
            if(Files.isSymbolicLink(normalized)||
               !Files.isDirectory(normalized,LinkOption.NOFOLLOW_LINKS))
                throw new IOException("untrusted root");
            AtomicAccountPublisher.requireNoAmbiguousStage(file);
            if(Files.notExists(file,LinkOption.NOFOLLOW_LINKS))
                return new Review(Status.MISSING_BLOCKED,0L,0,
                    "MISSING_CONSUMER_STATE");
            State data=read(file,checked,128);
            return new Review(
                Status.PRESENT_QUARANTINED,data.value,
                data.receipts.size(),"OBSERVED_NOT_AUTHORIZED"
            );
        }catch(IOException|RuntimeException failure){
            return new Review(
                Status.INDETERMINATE_BLOCKED,0L,0,
                "UNTRUSTED_OR_AMBIGUOUS_STATE"
            );
        }
    }

    Path canonicalForTest(){return canonical;}
    Path rootForTest(){return root;}

    private static String checkedConsumer(String consumer){
        if(!"objective".equals(consumer)&&
           !"match".equals(consumer)&&
           !"collection".equals(consumer))
            throw new IllegalArgumentException(
                "R89 invalid test-only consumer"
            );
        return consumer;
    }

    private static State read(
        Path file,String expectedConsumer,int maxReceipts
    )throws IOException{
        if(Files.notExists(file,LinkOption.NOFOLLOW_LINKS))
            return new State(0L,new LinkedHashMap<>());
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||
           Files.size(file)<=0||Files.size(file)>MAX_BYTES)
            throw new IOException("R89 malformed consumer file");
        Properties p=new Properties();
        try(InputStream in=Files.newInputStream(
                file,StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS)){
            p.load(in);
        }
        if(!FORMAT.equals(p.getProperty("format"))||
           !expectedConsumer.equals(p.getProperty("consumer")))
            throw new IOException("R89 schema or consumer mismatch");

        final int n;
        final long value;
        try{
            n=Integer.parseInt(p.getProperty("receipt.count"));
            value=Long.parseLong(p.getProperty("state.value"));
        }catch(RuntimeException error){
            throw new IOException("R89 malformed state/count",error);
        }
        if(n<0||n>maxReceipts||value<0L)
            throw new IOException("R89 invalid persisted state bound");
        Set<String> keys=new HashSet<>(Arrays.asList(
            "format","consumer","receipt.count","state.value",
            "evidence.sha256"
        ));
        LinkedHashMap<UUID,String> receipts=new LinkedHashMap<>();
        for(int i=0;i<n;i++){
            String idKey="receipt."+i+".id";
            String fpKey="receipt."+i+".fingerprint";
            keys.add(idKey);
            keys.add(fpKey);
            try{
                String raw=p.getProperty(idKey);
                UUID id=UUID.fromString(raw);
                if(!id.toString().equals(raw))
                    throw new IllegalArgumentException("noncanonical UUID");
                String fp=p.getProperty(fpKey);
                if(fp==null||!fp.matches("[0-9a-f]{64}")||
                   receipts.put(id,fp)!=null)
                    throw new IllegalArgumentException(
                        "invalid or duplicate receipt"
                    );
            }catch(RuntimeException failure){
                throw new IOException(
                    "R89 invalid indexed receipt",failure
                );
            }
        }
        if(!keys.equals(p.stringPropertyNames())||
           !digest(p,n).equals(p.getProperty("evidence.sha256")))
            throw new IOException("R89 receipt checksum/schema mismatch");
        return new State(value,receipts);
    }

    private static Properties encode(
        State state,String consumer
    )throws IOException{
        Properties p=new Properties();
        p.setProperty("format",FORMAT);
        p.setProperty("consumer",consumer);
        p.setProperty("receipt.count",
            Integer.toString(state.receipts.size()));
        p.setProperty("state.value",Long.toString(state.value));
        int i=0;
        for(Map.Entry<UUID,String> row:state.receipts.entrySet()){
            p.setProperty("receipt."+i+".id",row.getKey().toString());
            p.setProperty("receipt."+i+".fingerprint",row.getValue());
            i++;
        }
        p.setProperty("evidence.sha256",digest(p,i));
        return p;
    }

    private static String fingerprint(
        CombatOutcome fact,long amount
    )throws IOException{
        return sha256(
            fact.attacker()+"\n"+fact.victim()+"\n"+
            fact.type().name()+"\n"+fact.context().name()+"\n"+
            fact.worldTick()+"\n"+fact.sourceAuthority()+"\n"+
            amount+"\n"
        );
    }

    private static String digest(Properties p,int n)throws IOException{
        ArrayList<String> fields=new ArrayList<>(Arrays.asList(
            "format","consumer","receipt.count","state.value"
        ));
        for(int i=0;i<n;i++){
            fields.add("receipt."+i+".id");
            fields.add("receipt."+i+".fingerprint");
        }
        StringBuilder data=new StringBuilder();
        for(String key:fields){
            String value=p.getProperty(key);
            if(value==null)throw new IOException(
                "R89 missing evidence key "+key
            );
            data.append(key).append('=').append(value).append('\n');
        }
        return sha256(data.toString());
    }

    private static String sha256(String text)throws IOException{
        try{
            byte[] hash=MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder(64);
            for(byte b:hash)
                hex.append(String.format("%02x",b&0xff));
            return hex.toString();
        }catch(NoSuchAlgorithmException unsupported){
            throw new IOException("SHA-256 unavailable",unsupported);
        }
    }
}
