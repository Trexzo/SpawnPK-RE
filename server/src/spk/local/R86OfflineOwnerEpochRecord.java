package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
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
 * R86 OFFLINE MODEL ONLY. Unlike R84's single-owner immutable-origin record,
 * this independent V2 single-file fixture can advance a current owner epoch.
 * Its durable state includes the owner epoch, event identity, simulated HP,
 * projection counts, and checkpoints in ONE atomic file replacement.
 *
 * Cooperative writers use a permanent OS FileLock and expected owner token
 * (stream UUID + owner UUID + epoch + revision), checked WHILE locked.
 * A token is a compare-and-swap descriptor, NOT authentication or a lease.
 * Anybody with filesystem access can read/copy it; no real account/session,
 * live World, producer event, or automatic replay calls this code.
 *
 * No directory fsync, real process kill, multi-account atomicity, or
 * power-loss guarantee is claimed.
 */
final class R86OfflineOwnerEpochRecord {
    enum Consumer { OBJECTIVE, MATCH, COLLECTION }
    enum Fault { NONE, BEFORE_MOVE, AFTER_MOVE_BEFORE_ACK }

    private static final String FORMAT="R86_OFFLINE_OWNER_EPOCH_V1";
    private static final String RECORD="r86-offline-owner-record.txt";
    private static final String LOCK="r86-offline-writer.lock";
    private static final int MAX_BYTES=4096;

    /**
     * R87 OFFLINE TEST-ONLY process-kill coordination. Hook is installed
     * only in the isolated child JVM's invoking thread; never used by any
     * World, LocalSession, account or real combat producer.
     */
    @FunctionalInterface interface OfflineTakeoverCrashCutpoint {
        void reached(String cutpoint)throws IOException;
    }

    private static final ThreadLocal<OfflineTakeoverCrashCutpoint>
        TEST_TAKEOVER_CUTPOINT=new ThreadLocal<>();

    static void installOfflineTakeoverCutpointForTest(
        OfflineTakeoverCrashCutpoint hook){
        Objects.requireNonNull(hook,"hook");
        if(TEST_TAKEOVER_CUTPOINT.get()!=null)
            throw new IllegalStateException(
                "R87_CRASH_PROBE_DENIED already installed");
        TEST_TAKEOVER_CUTPOINT.set(hook);
    }

    static void clearOfflineTakeoverCutpointForTest(){
        TEST_TAKEOVER_CUTPOINT.remove();
    }

    private static void signalTakeoverCutpoint(String stage)
        throws IOException{
        OfflineTakeoverCrashCutpoint hook=TEST_TAKEOVER_CUTPOINT.get();
        if(hook!=null)hook.reached(stage);
    }

    @FunctionalInterface private interface Checked<T> {
        T run()throws IOException;
    }
    @FunctionalInterface private interface Change {
        State apply(State state)throws IOException;
    }

    static final class Token {
        final UUID stream;
        final UUID owner;
        final long epoch;
        final long revision;

        Token(UUID stream,UUID owner,long epoch,long revision){
            this.stream=Objects.requireNonNull(stream,"stream");
            this.owner=Objects.requireNonNull(owner,"owner");
            this.epoch=epoch;this.revision=revision;
            if(epoch<1||revision<0)
                throw new IllegalArgumentException("R86 invalid owner token");
        }

        String wire(){
            return stream+","+owner+","+epoch+","+revision;
        }

        static Token parse(String wire){
            Objects.requireNonNull(wire,"wire");
            String[] f=wire.split(",",-1);
            if(f.length!=4)
                throw new IllegalArgumentException("R86 malformed token");
            Token token=new Token(UUID.fromString(f[0]),
                UUID.fromString(f[1]),Long.parseLong(f[2]),
                Long.parseLong(f[3]));
            if(!token.wire().equals(wire))
                throw new IllegalArgumentException("R86 noncanonical token");
            return token;
        }

        boolean matches(State state){
            return stream.equals(state.stream)&&
                owner.equals(state.owner)&&epoch==state.ownerEpoch&&
                revision==state.revision;
        }
    }

    static final class State {
        final UUID stream;
        final long originEpoch;   // immutable identity provenance
        final UUID owner;
        final long ownerEpoch;    // current cooperative writer epoch
        final long revision;      // monotone per canonical replacement
        final long sequence;
        final int hp;
        final String killId,deathId;
        final long objective,match,collection;
        final boolean objectiveCp,matchCp,collectionCp;

        State(UUID stream,long originEpoch,UUID owner,long ownerEpoch,
              long revision,long sequence,int hp,String killId,
              String deathId,long objective,long match,long collection,
              boolean objectiveCp,boolean matchCp,boolean collectionCp){
            this.stream=Objects.requireNonNull(stream,"stream");
            this.originEpoch=originEpoch;
            this.owner=Objects.requireNonNull(owner,"owner");
            this.ownerEpoch=ownerEpoch;this.revision=revision;
            this.sequence=sequence;this.hp=hp;
            this.killId=Objects.requireNonNull(killId,"killId");
            this.deathId=Objects.requireNonNull(deathId,"deathId");
            this.objective=objective;this.match=match;
            this.collection=collection;
            this.objectiveCp=objectiveCp;this.matchCp=matchCp;
            this.collectionCp=collectionCp;
            validate();
        }

        private void validate(){
            if(originEpoch<1||ownerEpoch<originEpoch||
               revision<0||sequence<0||sequence>1)
                throw new IllegalArgumentException("R86 epoch/revision/seq");
            if(sequence==0){
                if(hp!=10||!"-".equals(killId)||
                   !"-".equals(deathId)||objective!=0||match!=0||
                   collection!=0||objectiveCp||matchCp||collectionCp)
                    throw new IllegalArgumentException(
                        "R86 source uncommitted invariant");
            }else{
                String stem=stream+"/"+originEpoch+"/1";
                if(hp!=0 || !killId.equals(stem+"/KILL") ||
                   !deathId.equals(stem+"/DEATH") ||
                   objective!=(objectiveCp?1L:0L)||
                   match!=(matchCp?2L:0L)||
                   collection!=(collectionCp?7L:0L))
                    throw new IllegalArgumentException(
                        "R86 event/checkpoint invariant");
            }
        }

        Token token(){return new Token(stream,owner,ownerEpoch,revision);}

        String serialize(){
            String body=String.join("\n",
                FORMAT,stream.toString(),Long.toString(originEpoch),
                owner.toString(),Long.toString(ownerEpoch),
                Long.toString(revision),Long.toString(sequence),
                Integer.toString(hp),killId,deathId,
                Long.toString(objective),Long.toString(match),
                Long.toString(collection),
                Boolean.toString(objectiveCp),
                Boolean.toString(matchCp),
                Boolean.toString(collectionCp))+"\n";
            return body+"sha256="+sha256(body)+"\n";
        }

        static State parse(String raw)throws IOException{
            try{
                if(!raw.endsWith("\n"))
                    throw new IllegalArgumentException("R86 missing LF");
                String[] f=raw.split("\n",-1);
                if(f.length!=18 || !f[17].isEmpty() ||
                   !FORMAT.equals(f[0]) || !f[16].startsWith("sha256="))
                    throw new IllegalArgumentException("R86 bad schema");
                String body=String.join("\n",Arrays.copyOf(f,16))+"\n";
                if(!f[16].equals("sha256="+sha256(body)))
                    throw new IllegalArgumentException("R86 digest mismatch");
                State state=new State(UUID.fromString(f[1]),
                    Long.parseLong(f[2]),UUID.fromString(f[3]),
                    Long.parseLong(f[4]),Long.parseLong(f[5]),
                    Long.parseLong(f[6]),Integer.parseInt(f[7]),
                    f[8],f[9],Long.parseLong(f[10]),
                    Long.parseLong(f[11]),Long.parseLong(f[12]),
                    strictBoolean(f[13]),strictBoolean(f[14]),
                    strictBoolean(f[15]));
                if(!state.serialize().equals(raw))
                    throw new IllegalArgumentException(
                        "R86 noncanonical bytes");
                return state;
            }catch(IllegalArgumentException invalid){
                throw new IOException(
                    "R86_RECORD_QUARANTINED invalid or torn canonical state",
                    invalid);
            }
        }

        private static boolean strictBoolean(String value){
            if(!"true".equals(value)&&!"false".equals(value))
                throw new IllegalArgumentException("R86 invalid boolean");
            return Boolean.parseBoolean(value);
        }

        State lethal(){
            if(sequence!=0)
                throw new IllegalStateException("R86_SOURCE_DENIED already committed");
            String stem=stream+"/"+originEpoch+"/1";
            return new State(stream,originEpoch,owner,ownerEpoch,
                revision+1L,1L,0,stem+"/KILL",stem+"/DEATH",
                0,0,0,false,false,false);
        }

        State consumer(Consumer consumer){
            if(sequence!=1)
                throw new IllegalStateException(
                    "R86_CONSUMER_DENIED no committed source");
            if((consumer==Consumer.OBJECTIVE&&objectiveCp)||
               (consumer==Consumer.MATCH&&matchCp)||
               (consumer==Consumer.COLLECTION&&collectionCp))
                return this;
            return new State(stream,originEpoch,owner,ownerEpoch,
                revision+1L,sequence,hp,killId,deathId,
                consumer==Consumer.OBJECTIVE?1L:objective,
                consumer==Consumer.MATCH?2L:match,
                consumer==Consumer.COLLECTION?7L:collection,
                objectiveCp||consumer==Consumer.OBJECTIVE,
                matchCp||consumer==Consumer.MATCH,
                collectionCp||consumer==Consumer.COLLECTION);
        }

        State successor(){
            // Only model handoff of an ALREADY committed combat event.
            // Transfers before event creation need a different producer
            // origin-epoch allocation rule and are not modeled in R86.
            if(sequence!=1L)
                throw new IllegalStateException(
                    "R86_OWNER_DENIED source not committed");
            if(ownerEpoch==Long.MAX_VALUE||revision==Long.MAX_VALUE)
                throw new IllegalStateException("R86_OWNER_DENIED overflow");
            return new State(stream,originEpoch,UUID.randomUUID(),
                ownerEpoch+1L,revision+1L,sequence,hp,killId,deathId,
                objective,match,collection,objectiveCp,matchCp,collectionCp);
        }
    }

    static final class Session {
        private final Path root;
        private Token expected;
        private State cached;
        private boolean poisoned;

        Session(Path root,State initial){
            this.root=root;this.expected=initial.token();this.cached=initial;
        }

        synchronized Token token(){
            requireUsable();
            return expected;
        }

        synchronized State snapshot(){
            requireUsable();
            return cached;
        }

        synchronized void lethal(Fault fault)throws IOException{
            mutate(State::lethal,fault);
        }

        synchronized void consumer(Consumer consumer,Fault fault)
            throws IOException{
            Objects.requireNonNull(consumer,"consumer");
            mutate(s->s.consumer(consumer),fault);
        }

        private void mutate(Change action,Fault fault)throws IOException{
            requireUsable();
            Objects.requireNonNull(fault,"fault");
            withLock(root,()->{
                try{
                    State before=read(root);
                    if(!expected.matches(before)){
                        poisoned=true;
                        throw new IOException(
                            "R86_STALE_OWNER expected="+expected.wire()+
                            " actual="+before.token().wire());
                    }
                    State after=action.apply(before);
                    if(after!=before)
                        atomicReplace(root,after,fault);
                    // Read back the exact committed canonical state while
                    // still holding the cooperative OS lock.
                    State verified=read(root);
                    if(!Arrays.equals(
                            verified.serialize().getBytes(StandardCharsets.UTF_8),
                            after.serialize().getBytes(StandardCharsets.UTF_8)))
                        throw new IOException(
                            "R86_COMMIT_DENIED unexpected canonical record");
                    expected=verified.token();
                    cached=verified;
                    return null;
                }catch(IOException|RuntimeException failure){
                    poisoned=true;
                    throw failure;
                }
            });
        }

        private void requireUsable(){
            if(poisoned)
                throw new IllegalStateException(
                    "R86_REOPEN_REQUIRED stale or ambiguous operation");
        }
    }

    static Session create(Path root,long initialEpoch)throws IOException{
        Objects.requireNonNull(root,"root");
        if(initialEpoch<1)
            throw new IllegalArgumentException("R86 initial epoch");
        if(Files.isSymbolicLink(root))
            throw new IOException("R86_LOCK_DENIED symbolic fixture root");
        Files.createDirectories(root);
        return withLock(root,()->{
            Path canonical=root.resolve(RECORD);
            if(Files.exists(canonical,LinkOption.NOFOLLOW_LINKS))
                throw new IOException("R86_OWNER_DENIED already initialized");
            requireNoOrphanStage(root);
            State initial=new State(UUID.randomUUID(),initialEpoch,
                UUID.randomUUID(),initialEpoch,0L,0L,10,"-","-",
                0,0,0,false,false,false);
            atomicReplace(root,initial,Fault.NONE);
            return new Session(root,read(root));
        });
    }

    static Session reopen(Path root,Token token)throws IOException{
        Objects.requireNonNull(token,"token");
        return withLock(root,()->{
            State current=read(root);
            if(!token.matches(current))
                throw new IOException("R86_STALE_OWNER reopen token rejected");
            return new Session(root,current);
        });
    }

    static Session takeover(Path root,Token predecessor,Fault fault)
        throws IOException{
        Objects.requireNonNull(predecessor,"predecessor");
        Objects.requireNonNull(fault,"fault");
        return withLock(root,()->{
            State before=read(root);
            if(!predecessor.matches(before))
                throw new IOException("R86_STALE_OWNER predecessor rejected");
            State successor=before.successor();
            atomicReplace(root,successor,fault,true);
            State verified=read(root);
            if(!verified.token().matches(successor))
                throw new IOException(
                    "R86_OWNER_DENIED takeover verification mismatch");
            return new Session(root,verified);
        });
    }

    /** Observational test hook only: never grants login or a writer token. */
    static State inspectForTest(Path root)throws IOException{
        return withLock(root,()->read(root));
    }

    private static State read(Path root)throws IOException{
        // R87: after a real process kill, an incomplete staged record
        // is evidence of ambiguous publication. Never delete it or permit
        // silent automatic recovery, even if canonical bytes parse.
        requireNoOrphanStage(root);
        Path canonical=root.resolve(RECORD);
        if(!Files.isRegularFile(canonical,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("R86_RECORD_QUARANTINED missing record");
        if(Files.size(canonical)>MAX_BYTES)
            throw new IOException("R86_RECORD_QUARANTINED oversized record");
        return State.parse(Files.readString(canonical,StandardCharsets.UTF_8));
    }

    private static void requireNoOrphanStage(Path root)
        throws IOException{
        try(java.nio.file.DirectoryStream<Path> stages=
                Files.newDirectoryStream(root,"r86-stage-*.tmp")){
            if(stages.iterator().hasNext())
                throw new IOException(
                    "R87_STAGE_QUARANTINED orphaned staged takeover record");
        }
    }

    private static void atomicReplace(
        Path root,State state,Fault fault)throws IOException{
        atomicReplace(root,state,fault,false);
    }

    private static void atomicReplace(
        Path root,State state,Fault fault,boolean takeover)
        throws IOException{
        Path tmp=Files.createTempFile(root,"r86-stage-",".tmp");
        try{
            byte[] bytes=state.serialize().getBytes(StandardCharsets.UTF_8);
            try(FileChannel file=FileChannel.open(tmp,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)){
                ByteBuffer buffer=ByteBuffer.wrap(bytes);
                while(buffer.hasRemaining())file.write(buffer);
                file.force(true);
            }
            if(takeover)
                signalTakeoverCutpoint("STAGED_BEFORE_MOVE");
            if(fault==Fault.BEFORE_MOVE)
                throw new IOException("R86_INJECTED before atomic move");
            try{
                Files.move(tmp,root.resolve(RECORD),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            }catch(AtomicMoveNotSupportedException error){
                throw new IOException(
                    "R86_OWNER_DENIED no atomic replacement support",error);
            }
            if(takeover)
                signalTakeoverCutpoint("REPLACED_BEFORE_ACK");
            if(fault==Fault.AFTER_MOVE_BEFORE_ACK)
                throw new IOException(
                    "R86_INJECTED after atomic move before ack");
        }finally{
            Files.deleteIfExists(tmp);
        }
    }

    private static <T> T withLock(Path root,Checked<T> callback)
        throws IOException{
        Objects.requireNonNull(root,"root");
        if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)||
           Files.isSymbolicLink(root))
            throw new IOException("R86_LOCK_DENIED invalid root");
        Path lock=root.resolve(LOCK);
        if(Files.isSymbolicLink(lock))
            throw new IOException("R86_LOCK_DENIED symbolic lock");
        // Never unlink this lock file, including during takeover. An unlinked
        // old inode could permit two simultaneously-held lock domains.
        try(FileChannel channel=FileChannel.open(lock,
                StandardOpenOption.CREATE,StandardOpenOption.WRITE)){
            if(Files.isSymbolicLink(lock))
                throw new IOException("R86_LOCK_DENIED symbolic lock");
            try(FileLock held=channel.tryLock()){
                if(held==null)
                    throw new IOException(
                        "R86_LOCK_DENIED another process holds lock");
                return callback.run();
            }catch(OverlappingFileLockException error){
                throw new IOException(
                    "R86_LOCK_DENIED same JVM holds lock",error);
            }
        }
    }

    private static String sha256(String value){
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(
                value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder(64);
            for(byte b:digest)hex.append(String.format("%02x",b&0xff));
            return hex.toString();
        }catch(NoSuchAlgorithmException impossible){
            throw new IllegalStateException("R86 SHA256 unavailable",impossible);
        }
    }

    private R86OfflineOwnerEpochRecord(){}
}
