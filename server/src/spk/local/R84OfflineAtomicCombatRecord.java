package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * R84 OFFLINE ONLY. Tiny single-record, disposable-file crash-ordering
 * experiment. Deliberately NOT a game/persistence adapter, outbox, event
 * envelope, or automatic replay engine. Never install in World/LocalSession.
 *
 * A producer-owned *model* creates an ID before a simulated lethal state
 * transition. Both IDs, HP, and each consumer value+checkpoint are in one
 * atomic replacement file. This does NOT imply power-loss durability (no
 * directory fsync), cross-file atomicity, or production authentication.
 *
 * Callers must reopen after any failed/ambiguous commit. Even a successful
 * callback/rename is NOT authority to retry real projection observers.
 */
final class R84OfflineAtomicCombatRecord {
    enum Consumer { OBJECTIVE, MATCH, COLLECTION }
    enum Fault { NONE, BEFORE_ATOMIC_MOVE, AFTER_ATOMIC_MOVE_BEFORE_ACK }

    private static final String VERSION="R84_OFFLINE_SINGLE_FILE_V1";
    private static final String RECORD="r84-offline-record.txt";
    private final Path root;
    private final Path canonical;
    private final long expectedOwnerEpoch;
    private State current;
    private boolean uncertain;

    static final class State {
        final String producerStream;
        final long ownerEpoch;
        final long sequence;
        final int hp;
        final String killId;
        final String deathId;
        final long objectives;
        final long match;
        final long collection;
        final boolean objectiveCheckpoint;
        final boolean matchCheckpoint;
        final boolean collectionCheckpoint;

        State(String stream,long epoch,long seq,int hp,
              String killId,String deathId,long objectives,long match,
              long collection,boolean o,boolean m,boolean c){
            this.producerStream=stream;
            this.ownerEpoch=epoch;this.sequence=seq;this.hp=hp;
            this.killId=killId;this.deathId=deathId;
            this.objectives=objectives;this.match=match;
            this.collection=collection;
            this.objectiveCheckpoint=o;
            this.matchCheckpoint=m;
            this.collectionCheckpoint=c;
            validate();
        }

        private void validate(){
            try {
                if(!UUID.fromString(producerStream).toString()
                        .equals(producerStream))
                    throw new IllegalArgumentException("producer stream");
            }catch(IllegalArgumentException invalid){
                throw new IllegalArgumentException(
                    "R84 invalid producer stream",invalid);
            }
            if(ownerEpoch<1 || sequence<0 || sequence>1)
                throw new IllegalArgumentException("R84 sequence/epoch");
            if(sequence==0){
                if(hp!=10 || !"-".equals(killId) ||
                   !"-".equals(deathId) || objectives!=0 ||
                   match!=0 || collection!=0 ||
                   objectiveCheckpoint || matchCheckpoint ||
                   collectionCheckpoint)
                    throw new IllegalArgumentException(
                        "R84 uncommitted state mismatch");
            }else{
                if(hp!=0 || !killId.equals(expectedId("KILL")) ||
                   !deathId.equals(expectedId("DEATH")) ||
                   objectives!=(objectiveCheckpoint?1:0) ||
                   match!=(matchCheckpoint?2:0) ||
                   collection!=(collectionCheckpoint?7:0))
                    throw new IllegalArgumentException(
                        "R84 event and checkpoint mismatch");
            }
        }

        private String expectedId(String role){
            return producerStream+"/"+ownerEpoch+"/"+sequence+"/"+role;
        }

        String serialize(){
            String body=String.join("\n",VERSION,producerStream,
                Long.toString(ownerEpoch),Long.toString(sequence),
                Integer.toString(hp),killId,deathId,
                Long.toString(objectives),Long.toString(match),
                Long.toString(collection),
                Boolean.toString(objectiveCheckpoint),
                Boolean.toString(matchCheckpoint),
                Boolean.toString(collectionCheckpoint))+"\n";
            return body+"sha256="+sha256(body)+"\n";
        }

        static State parse(String text)throws IOException {
            try{
                if(!text.endsWith("\n"))
                    throw new IllegalArgumentException("record missing LF");
                String[] l=text.split("\n");
                if(l.length!=14 || !VERSION.equals(l[0]) ||
                   !l[13].startsWith("sha256="))
                    throw new IllegalArgumentException("invalid schema");
                String body=String.join("\n",Arrays.copyOf(l,13))+"\n";
                if(!l[13].equals("sha256="+sha256(body)))
                    throw new IllegalArgumentException("digest mismatch");
                State s=new State(l[1],Long.parseLong(l[2]),
                    Long.parseLong(l[3]),Integer.parseInt(l[4]),
                    l[5],l[6],Long.parseLong(l[7]),
                    Long.parseLong(l[8]),Long.parseLong(l[9]),
                    strictBoolean(l[10]),strictBoolean(l[11]),
                    strictBoolean(l[12]));
                if(!s.serialize().equals(text))
                    throw new IllegalArgumentException("noncanonical record");
                return s;
            }catch(IllegalArgumentException invalid){
                throw new IOException(
                    "R84_RECORD_QUARANTINED invalid or incomplete record",
                    invalid);
            }
        }

        private static boolean strictBoolean(String s){
            if(!"true".equals(s)&&!"false".equals(s))
                throw new IllegalArgumentException("invalid boolean");
            return Boolean.parseBoolean(s);
        }
    }

    private R84OfflineAtomicCombatRecord(
        Path root,long ownerEpoch,State state){
        this.root=root;this.canonical=root.resolve(RECORD);
        this.expectedOwnerEpoch=ownerEpoch;this.current=state;
    }

    static R84OfflineAtomicCombatRecord create(
        Path root,long ownerEpoch)throws IOException{
        Objects.requireNonNull(root,"root");
        if(ownerEpoch<1)throw new IllegalArgumentException("ownerEpoch");
        Files.createDirectories(root);
        Path canonical=root.resolve(RECORD);
        if(Files.exists(canonical,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("R84_RECORD_DENIED already initialized");
        State initial=new State(UUID.randomUUID().toString(),
            ownerEpoch,0L,10,"-","-",0L,0L,0L,false,false,false);
        R84OfflineAtomicCombatRecord record=
            new R84OfflineAtomicCombatRecord(root,ownerEpoch,initial);
        // Creation is only safe for a fresh disposable fixture directory.
        record.replace(initial,Fault.NONE);
        return record;
    }

    static R84OfflineAtomicCombatRecord reopen(
        Path root,long expectedOwnerEpoch)throws IOException{
        Objects.requireNonNull(root,"root");
        Path canonical=root.resolve(RECORD);
        if(!Files.isRegularFile(canonical,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("R84_RECORD_QUARANTINED missing canonical record");
        State state=State.parse(Files.readString(canonical,StandardCharsets.UTF_8));
        if(state.ownerEpoch!=expectedOwnerEpoch)
            throw new IOException("R84_RECORD_DENIED stale owner epoch");
        return new R84OfflineAtomicCombatRecord(root,expectedOwnerEpoch,state);
    }

    State snapshot(){
        requireUsable();
        return current;
    }

    void commitLethal(Fault fault)throws IOException{
        requireUsable();
        if(current.sequence!=0L)
            throw new IOException("R84_RECORD_DENIED event already committed");
        long seq=current.sequence+1L;
        String stem=current.producerStream+"/"+expectedOwnerEpoch+"/"+seq;
        // ID allocation and the simulated HP commit are in ONE record.
        // Real PlayerCombatResolutionService does NOT do this today.
        State next=new State(current.producerStream,expectedOwnerEpoch,
            seq,0,stem+"/KILL",stem+"/DEATH",
            0L,0L,0L,false,false,false);
        replace(next,fault);
    }

    void commitConsumer(Consumer consumer,Fault fault)throws IOException{
        requireUsable();
        Objects.requireNonNull(consumer,"consumer");
        if(current.sequence!=1L)
            throw new IOException("R84_RECORD_DENIED no committed source event");
        boolean o=current.objectiveCheckpoint;
        boolean m=current.matchCheckpoint;
        boolean c=current.collectionCheckpoint;
        if((consumer==Consumer.OBJECTIVE&&o) ||
           (consumer==Consumer.MATCH&&m) ||
           (consumer==Consumer.COLLECTION&&c))
            return; // Durable model's checkpoint+value are already paired.
        State next=new State(current.producerStream,current.ownerEpoch,
            current.sequence,current.hp,current.killId,current.deathId,
            consumer==Consumer.OBJECTIVE?1L:current.objectives,
            consumer==Consumer.MATCH?2L:current.match,
            consumer==Consumer.COLLECTION?7L:current.collection,
            o||consumer==Consumer.OBJECTIVE,
            m||consumer==Consumer.MATCH,
            c||consumer==Consumer.COLLECTION);
        replace(next,fault);
    }

    private void replace(State next,Fault fault)throws IOException{
        Objects.requireNonNull(fault,"fault");
        requireUsable();
        if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("R84_RECORD_DENIED invalid root");
        Path stage=Files.createTempFile(root,"r84-stage-",".tmp");
        boolean committed=false;
        try{
            byte[] bytes=next.serialize().getBytes(StandardCharsets.UTF_8);
            try(FileChannel file=FileChannel.open(stage,
                    StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){
                ByteBuffer b=ByteBuffer.wrap(bytes);
                while(b.hasRemaining())file.write(b);
                file.force(true);
            }
            if(fault==Fault.BEFORE_ATOMIC_MOVE)
                throw new IOException("R84_INJECTED before atomic move");
            try{
                Files.move(stage,canonical,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            }catch(AtomicMoveNotSupportedException unavailable){
                throw new IOException(
                    "R84_RECORD_DENIED atomic move not supported",unavailable);
            }
            committed=true;
            if(fault==Fault.AFTER_ATOMIC_MOVE_BEFORE_ACK)
                throw new IOException("R84_INJECTED after atomic move before ack");
            current=next;
        }catch(IOException failure){
            // Never make an ambiguous second commit through the same instance.
            // Must reopen and inspect canonical bytes (still not power-loss proof).
            uncertain=true;
            throw failure;
        }finally{
            Files.deleteIfExists(stage);
        }
    }

    private void requireUsable(){
        if(uncertain)
            throw new IllegalStateException(
                "R84_RECORD_DENIED ambiguous commit: reopen before actions");
    }

    private static String sha256(String input){
        try{
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(
                input.getBytes(StandardCharsets.UTF_8));
            StringBuilder b=new StringBuilder(64);
            for(byte value:bytes)
                b.append(String.format("%02x",value&0xff));
            return b.toString();
        }catch(NoSuchAlgorithmException unavailable){
            throw new IllegalStateException("SHA-256 unavailable",unavailable);
        }
    }
}
