package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * R57 OFFLINE EVIDENCE ONLY: a durable-ish (forced staged file + atomic
 * replace) two-account trade intent, plus a conservative read-only reviewer.
 *
 * This class is deliberately NOT connected to TradeService, login, account
 * saves or automatic recovery. A journal must never be used as a substitute
 * for a coordinated two-account commit and login gate. Parent-directory fsync
 * and physical power-loss durability are NOT demonstrated here.
 */
final class R57TradePairJournal {
    private static final String FORMAT="SpawnPK-R57-TradePairIntent-v1";
    private static final String PHASE="PREPARED";
    private static final int MAX_INTENT_BYTES=8192;
    private static final Set<String> KEYS=new HashSet<>(Arrays.asList(
        "format","phase","transaction.id",
        "participant.a","participant.b",
        "a.before.sha256","a.after.sha256",
        "b.before.sha256","b.after.sha256"
    ));

    enum State {
        BOTH_BEFORE,
        ONLY_A_AFTER,
        ONLY_B_AFTER,
        BOTH_AFTER,
        UNRECOGNIZED
    }

    static final class Review {
        final UUID transaction;
        final State state;
        final boolean loginAllowed;
        final boolean automaticRecoveryAttempted;
        Review(UUID transaction,State state){
            this.transaction=transaction;
            this.state=state;
            // Even BOTH_AFTER cannot be treated as a committed transaction
            // without an authenticated and durable completion protocol.
            this.loginAllowed=false;
            this.automaticRecoveryAttempted=false;
        }
    }

    private static final class Intent {
        final UUID transaction;
        final String a,b,beforeA,afterA,beforeB,afterB;
        Intent(UUID transaction,String a,String b,
               String beforeA,String afterA,String beforeB,String afterB){
            this.transaction=transaction;
            this.a=a;this.b=b;
            this.beforeA=beforeA;this.afterA=afterA;
            this.beforeB=beforeB;this.afterB=afterB;
        }
    }

    static void writePrepared(
        Path file,
        UUID transaction,
        PlayerSnapshot beforeA,
        PlayerSnapshot afterA,
        PlayerSnapshot beforeB,
        PlayerSnapshot afterB
    )throws IOException{
        Objects.requireNonNull(file,"file");
        Objects.requireNonNull(transaction,"transaction");
        Objects.requireNonNull(beforeA,"beforeA");
        Objects.requireNonNull(afterA,"afterA");
        Objects.requireNonNull(beforeB,"beforeB");
        Objects.requireNonNull(afterB,"afterB");

        String a=beforeA.username(),b=beforeB.username();
        requireIdentities(a,b);
        if(!a.equals(afterA.username())||!b.equals(afterB.username())||
           beforeA.version()!=PlayerSnapshot.CURRENT_VERSION||
           beforeB.version()!=PlayerSnapshot.CURRENT_VERSION||
           afterA.version()!=PlayerSnapshot.CURRENT_VERSION||
           afterB.version()!=PlayerSnapshot.CURRENT_VERSION)
            throw new IOException("R57_INTENT_DENIED snapshot identity/version");

        String oldA=digest(beforeA),newA=digest(afterA);
        String oldB=digest(beforeB),newB=digest(afterB);
        if(oldA.equals(newA)||oldB.equals(newB))
            throw new IOException("R57_INTENT_DENIED no change for participant");

        Path canonical=file.toAbsolutePath().normalize();
        if(Files.exists(canonical,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("R57_INTENT_DENIED existing intent is immutable");
        // R16 publisher forces a unique adjacent stage and requires an atomic
        // filesystem move. This is NOT a proof that power-loss survived an
        // unforced parent-directory metadata update.
        Properties properties=new Properties();
        properties.setProperty("format",FORMAT);
        properties.setProperty("phase",PHASE);
        properties.setProperty("transaction.id",transaction.toString());
        properties.setProperty("participant.a",a);
        properties.setProperty("participant.b",b);
        properties.setProperty("a.before.sha256",oldA);
        properties.setProperty("a.after.sha256",newA);
        properties.setProperty("b.before.sha256",oldB);
        properties.setProperty("b.after.sha256",newB);
        AtomicAccountPublisher.publish(
            canonical,properties,"R57 offline paired trade intent"
        );
    }

    /**
     * Review exact snapshot contents. No canonical file is opened here,
     * changed, adopted, deleted or promoted. Caller must supply both parsed
     * snapshots in an offline fixture. Login admission is ALWAYS denied.
     */
    static Review review(
        Path intentFile,PlayerSnapshot actualA,PlayerSnapshot actualB
    )throws IOException{
        Intent intent=read(intentFile);
        Objects.requireNonNull(actualA,"actualA");
        Objects.requireNonNull(actualB,"actualB");
        if(!intent.a.equals(actualA.username())||
           !intent.b.equals(actualB.username())||
           actualA.version()!=PlayerSnapshot.CURRENT_VERSION||
           actualB.version()!=PlayerSnapshot.CURRENT_VERSION)
            throw new IOException("R57_REVIEW_BLOCKED participant identity/version");

        String a=digest(actualA),b=digest(actualB);
        boolean aOld=a.equals(intent.beforeA);
        boolean aNew=a.equals(intent.afterA);
        boolean bOld=b.equals(intent.beforeB);
        boolean bNew=b.equals(intent.afterB);

        State state;
        if(aOld&&bOld)state=State.BOTH_BEFORE;
        else if(aNew&&bOld)state=State.ONLY_A_AFTER;
        else if(aOld&&bNew)state=State.ONLY_B_AFTER;
        else if(aNew&&bNew)state=State.BOTH_AFTER;
        else state=State.UNRECOGNIZED;
        return new Review(intent.transaction,state);
    }

    private static Intent read(Path file)throws IOException{
        Objects.requireNonNull(file,"file");
        Path canonical=file.toAbsolutePath().normalize();
        Path parent=canonical.getParent();
        if(parent==null)
            throw new IOException("R57_REVIEW_BLOCKED no parent");
        for(Path p=parent;p!=null;p=p.getParent()){
            if(Files.isSymbolicLink(p))
                throw new IOException("R57_REVIEW_BLOCKED symlink ancestor");
        }
        // A current or abandoned R16 staging file is ambiguous, regardless
        // of timestamp. Preserve its bytes and decline to review.
        AtomicAccountPublisher.requireNoAmbiguousStage(canonical);
        BasicFileAttributes attrs=Files.readAttributes(
            canonical,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
        );
        if(!attrs.isRegularFile()||attrs.size()<=0||
           attrs.size()>MAX_INTENT_BYTES)
            throw new IOException("R57_REVIEW_BLOCKED invalid intent file");

        Properties p=new Properties();
        try(InputStream input=Files.newInputStream(
            canonical,LinkOption.NOFOLLOW_LINKS)){
            p.load(input);
        }
        if(!p.stringPropertyNames().equals(KEYS)||
           !FORMAT.equals(p.getProperty("format"))||
           !PHASE.equals(p.getProperty("phase")))
            throw new IOException("R57_REVIEW_BLOCKED invalid format/keys/phase");

        UUID id;
        try{
            id=UUID.fromString(p.getProperty("transaction.id"));
        }catch(IllegalArgumentException e){
            throw new IOException("R57_REVIEW_BLOCKED invalid transaction id",e);
        }
        if(!id.toString().equals(p.getProperty("transaction.id")))
            throw new IOException("R57_REVIEW_BLOCKED noncanonical transaction id");

        String a=p.getProperty("participant.a");
        String b=p.getProperty("participant.b");
        requireIdentities(a,b);
        String oldA=p.getProperty("a.before.sha256");
        String newA=p.getProperty("a.after.sha256");
        String oldB=p.getProperty("b.before.sha256");
        String newB=p.getProperty("b.after.sha256");
        for(String hash:new String[]{oldA,newA,oldB,newB}){
            if(hash==null||!hash.matches("[0-9a-f]{64}"))
                throw new IOException("R57_REVIEW_BLOCKED invalid digest");
        }
        if(oldA.equals(newA)||oldB.equals(newB))
            throw new IOException("R57_REVIEW_BLOCKED unchanged participant");
        return new Intent(id,a,b,oldA,newA,oldB,newB);
    }

    private static void requireIdentities(String a,String b)throws IOException{
        if(a==null||b==null||
           !a.matches("[a-z][a-z0-9_]{0,31}")||
           !b.matches("[a-z][a-z0-9_]{0,31}")||
           a.compareTo(b)>=0)
            throw new IOException("R57_INTENT_DENIED unordered/unsafe participants");
    }

    /**
     * Length-prefixed, canonical UTF-8 encoding, independent of Java
     * Properties key order, comments or per-save timestamps. Snapshot
     * values are hashed, NEVER stored in the journal.
     */
    static String digest(PlayerSnapshot snapshot){
        Objects.requireNonNull(snapshot,"snapshot");
        try{
            MessageDigest sha=MessageDigest.getInstance("SHA-256");
            feed(sha,Integer.toString(snapshot.version()));
            feed(sha,snapshot.username());
            feed(sha,Integer.toString(snapshot.values().size()));
            for(Map.Entry<String,String> entry:snapshot.values().entrySet()){
                feed(sha,entry.getKey());
                feed(sha,entry.getValue());
            }
            byte[] raw=sha.digest();
            StringBuilder hex=new StringBuilder(64);
            for(byte n:raw){
                int v=n&255;
                hex.append(Character.forDigit(v>>>4,16));
                hex.append(Character.forDigit(v&15,16));
            }
            return hex.toString();
        }catch(NoSuchAlgorithmException impossible){
            throw new IllegalStateException("SHA-256 unavailable",impossible);
        }
    }

    private static void feed(MessageDigest md,String value){
        byte[] utf8=value.getBytes(StandardCharsets.UTF_8);
        byte[] length=ByteBuffer.allocate(4).putInt(utf8.length).array();
        md.update(length);
        md.update(utf8);
    }

    private R57TradePairJournal(){}
}
