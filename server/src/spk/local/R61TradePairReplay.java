package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * R61 OFFLINE crash/restart roll-forward simulator.
 *
 * A R57 PREPARED intent is NOT sufficient to modify an account. Only after
 * both post-state payload snapshots have been independently saved and checked
 * is a distinct COMMIT decision published. A later (even separate JVM) replay
 * runs under R60's cooperative OS lock, validates journal/index/decision and
 * both canonical account snapshots, and publishes only missing post-states.
 *
 * IMPORTANT: no live TradeService/LocalSession/WorldPlayerPersistence hooks.
 * The account path arguments MUST be test-owned isolated directories. OS
 * power-loss durability, malicious filesystem writers and safe login release
 * are not proved. Pending intents and the index are NEVER removed.
 */
final class R61TradePairReplay {
    private static final String FORMAT="SpawnPK-R61-PairedCommit-v1";
    private static final String PHASE="COMMIT";
    private static final int MAX_DECISION_BYTES=4096;
    private static final Set<String> DECISION_KEYS=new HashSet<>(
        Arrays.asList(
            "format","phase","transaction.id","participant.a",
            "participant.b","a.after.sha256","b.after.sha256"
        )
    );

    static final class Outcome {
        final R57TradePairJournal.State before;
        final int publishedAccounts;
        final boolean bothPostStatesVerified;
        final boolean unresolvedIntentStillBlocksLogin;
        Outcome(R57TradePairJournal.State state,int published){
            before=state;
            publishedAccounts=published;
            bothPostStatesVerified=true;
            unresolvedIntentStillBlocksLogin=true;
        }
    }

    private static final class Decision {
        final UUID id;
        final String a,b,hashA,hashB;
        Decision(UUID id,String a,String b,String hashA,String hashB){
            this.id=id;this.a=a;this.b=b;
            this.hashA=hashA;this.hashB=hashB;
        }
    }

    /** Offline fixture: both post snapshots are persisted BEFORE COMMIT. */
    static void publishCommitDecision(
        Path journalRoot,Path payloadRoot,UUID id,
        PlayerSnapshot beforeA,PlayerSnapshot afterA,
        PlayerSnapshot beforeB,PlayerSnapshot afterB
    )throws IOException{
        Objects.requireNonNull(id,"id");
        Objects.requireNonNull(beforeA,"beforeA");
        Objects.requireNonNull(afterA,"afterA");
        Objects.requireNonNull(beforeB,"beforeB");
        Objects.requireNonNull(afterB,"afterB");
        R60TradePairCoordinator.withExclusive(journalRoot,false,()->{
            Path storage=checkedDirectory(payloadRoot);
            requirePairIndexed(journalRoot,id,
                beforeA.username(),beforeB.username());

            Path intent=intentPath(journalRoot,id);
            if(R57TradePairJournal.review(intent,beforeA,beforeB).state!=
                    R57TradePairJournal.State.BOTH_BEFORE ||
               R57TradePairJournal.review(intent,afterA,afterB).state!=
                    R57TradePairJournal.State.BOTH_AFTER)
                throw new IOException(
                    "R61_COMMIT_DENIED snapshots differ from PREPARED intent"
                );
            Path decision=decisionPath(storage,id);
            if(Files.exists(decision,LinkOption.NOFOLLOW_LINKS))
                throw new IOException(
                    "R61_COMMIT_DENIED immutable decision already exists"
                );
            Path immutablePayloadDir=storage.resolve(
                "trade-"+id.toString()
            );
            Files.createDirectory(immutablePayloadDir);
            FilePlayerRepository writer=new FilePlayerRepository(
                account->immutablePayloadDir.resolve(
                    account+".properties"
                )
            );
            // The two stored post snapshots are durable-ish individually,
            // and remain unusable for replay until the COMMIT record exists.
            writer.save(afterA);
            writer.save(afterB);
            requireDigest(
                writer.load(afterA.username()),afterA.username(),
                R57TradePairJournal.digest(afterA)
            );
            requireDigest(
                writer.load(afterB.username()),afterB.username(),
                R57TradePairJournal.digest(afterB)
            );

            Properties properties=new Properties();
            properties.setProperty("format",FORMAT);
            properties.setProperty("phase",PHASE);
            properties.setProperty("transaction.id",id.toString());
            properties.setProperty("participant.a",afterA.username());
            properties.setProperty("participant.b",afterB.username());
            properties.setProperty("a.after.sha256",
                R57TradePairJournal.digest(afterA));
            properties.setProperty("b.after.sha256",
                R57TradePairJournal.digest(afterB));
            AtomicAccountPublisher.publish(
                decision,properties,"R61 offline paired COMMIT evidence"
            );
            return null;
        });
    }

    /** Replay is idempotent for each of the FOUR known R56 crash states. */
    static Outcome replayCommitted(
        Path journalRoot,Path accountRoot,Path payloadRoot,UUID id,
        boolean injectFailureAfterOneSave
    )throws IOException{
        Objects.requireNonNull(id,"id");
        return R60TradePairCoordinator.withExclusive(journalRoot,false,()->{
            Path accounts=checkedDirectory(accountRoot);
            Path storage=checkedDirectory(payloadRoot);
            Decision decision=readDecision(
                decisionPath(storage,id)
            );
            if(!decision.id.equals(id))
                throw new IOException(
                    "R61_REPLAY_DENIED mismatched transaction ID"
                );

            requirePairIndexed(journalRoot,id,decision.a,decision.b);
            Path payloadDirectory=checkedDirectory(
                storage.resolve("trade-"+id.toString())
            );
            FilePlayerRepository immutable=new FilePlayerRepository(
                account->payloadDirectory.resolve(account+".properties")
            );
            PlayerSnapshot afterA=requireDigest(
                immutable.load(decision.a),decision.a,decision.hashA
            );
            PlayerSnapshot afterB=requireDigest(
                immutable.load(decision.b),decision.b,decision.hashB
            );
            Path manifest=intentPath(journalRoot,id);
            if(R57TradePairJournal.review(manifest,afterA,afterB).state!=
                    R57TradePairJournal.State.BOTH_AFTER)
                throw new IOException(
                    "R61_REPLAY_DENIED COMMIT payload contradicts PREPARED intent"
                );

            FilePlayerRepository canonical=new FilePlayerRepository(
                account->accounts.resolve(account+".properties")
            );
            // Both canonical files must already be present and known BEFORE
            // changing EITHER file. A missing or unexpected post-trade
            // mutation is NEVER replaced with a guessed/default snapshot.
            PlayerSnapshot currentA=canonical.load(decision.a).orElseThrow(
                ()->new IOException(
                    "R61_REPLAY_DENIED first canonical profile absent"
                )
            );
            PlayerSnapshot currentB=canonical.load(decision.b).orElseThrow(
                ()->new IOException(
                    "R61_REPLAY_DENIED second canonical profile absent"
                )
            );
            R57TradePairJournal.State observed=
                R57TradePairJournal.review(manifest,currentA,currentB).state;
            if(observed==R57TradePairJournal.State.UNRECOGNIZED)
                throw new IOException(
                    "R61_REPLAY_DENIED account snapshot outside transaction"
                );

            int published=0;
            if(observed==R57TradePairJournal.State.BOTH_BEFORE||
               observed==R57TradePairJournal.State.ONLY_B_AFTER){
                canonical.save(afterA);
                published++;
                if(injectFailureAfterOneSave)
                    throw new IOException(
                        "R61_INJECTED_FAILURE_AFTER_FIRST_ACCOUNT"
                    );
            }
            if(observed==R57TradePairJournal.State.BOTH_BEFORE||
               observed==R57TradePairJournal.State.ONLY_A_AFTER){
                canonical.save(afterB);
                published++;
                if(injectFailureAfterOneSave)
                    throw new IOException(
                        "R61_INJECTED_FAILURE_AFTER_FIRST_ACCOUNT"
                    );
            }
            // Reopen from disk; do not trust a save callback alone.
            PlayerSnapshot savedA=canonical.load(decision.a).orElseThrow(
                ()->new IOException(
                    "R61_REPLAY_DENIED first saved profile absent"
                )
            );
            PlayerSnapshot savedB=canonical.load(decision.b).orElseThrow(
                ()->new IOException(
                    "R61_REPLAY_DENIED second saved profile absent"
                )
            );
            if(R57TradePairJournal.review(manifest,savedA,savedB).state!=
                    R57TradePairJournal.State.BOTH_AFTER)
                throw new IOException(
                    "R61_REPLAY_DENIED final pair not fully published"
                );
            // There is no durable COMPLETED marker or retirement protocol;
            // both users stay quarantined even after both files match.
            requirePairIndexed(journalRoot,id,decision.a,decision.b);
            return new Outcome(observed,published);
        });
    }

    private static void requirePairIndexed(
        Path journalRoot,UUID id,String a,String b
    )throws IOException{
        for(String account:new String[]{a,b}){
            R59TradePairIntentIndex.Result result=
                R59TradePairIntentIndex.inspect(journalRoot,account);
            if(result.status!=
                    R59TradePairIntentIndex.Status.BLOCK_INDEXED_PAIR ||
               result.transactions.size()!=1 ||
               !id.equals(result.transactions.get(0)))
                throw new IOException(
                    "R61_PAIR_DENIED multiple/incorrect pending transactions"
                );
        }
    }

    private static Path intentPath(Path root,UUID id){
        return root.toAbsolutePath().normalize().resolve(
            "trade-r57-"+id.toString()+".properties"
        );
    }

    private static Path decisionPath(Path root,UUID id){
        return root.resolve("decision-"+id.toString()+".properties");
    }

    private static Path checkedDirectory(Path root)throws IOException{
        if(root==null)
            throw new IOException("R61_PATH_DENIED null directory");
        Path path=root.toAbsolutePath().normalize();
        for(Path parent=path;parent!=null;parent=parent.getParent()){
            if(Files.isSymbolicLink(parent))
                throw new IOException(
                    "R61_PATH_DENIED symlink ancestor"
                );
        }
        BasicFileAttributes attrs=Files.readAttributes(
            path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
        );
        if(!attrs.isDirectory())
            throw new IOException("R61_PATH_DENIED not a directory");
        return path;
    }

    private static PlayerSnapshot requireDigest(
        Optional<PlayerSnapshot> source,String account,String expected
    )throws IOException{
        PlayerSnapshot snapshot=source.orElseThrow(
            ()->new IOException("R61_PAYLOAD_DENIED missing "+account)
        );
        if(!account.equals(snapshot.username())||
           snapshot.version()!=PlayerSnapshot.CURRENT_VERSION||
           !expected.equals(R57TradePairJournal.digest(snapshot)))
            throw new IOException(
                "R61_PAYLOAD_DENIED incorrect content "+account
            );
        return snapshot;
    }

    private static Decision readDecision(Path path)throws IOException{
        AtomicAccountPublisher.requireNoAmbiguousStage(path);
        BasicFileAttributes attrs=Files.readAttributes(
            path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
        );
        if(!attrs.isRegularFile()||attrs.size()<1||
           attrs.size()>MAX_DECISION_BYTES)
            throw new IOException(
                "R61_COMMIT_DENIED malformed decision file"
            );
        Properties props=new Properties();
        try(InputStream input=Files.newInputStream(
                path,LinkOption.NOFOLLOW_LINKS)){
            props.load(input);
        }
        if(!props.stringPropertyNames().equals(DECISION_KEYS)||
           !FORMAT.equals(props.getProperty("format"))||
           !PHASE.equals(props.getProperty("phase")))
            throw new IOException(
                "R61_COMMIT_DENIED malformed phase/schema"
            );
        UUID id;
        String raw=props.getProperty("transaction.id");
        try{
            id=UUID.fromString(raw);
            if(!id.toString().equals(raw))
                throw new IllegalArgumentException("noncanonical");
        }catch(IllegalArgumentException problem){
            throw new IOException(
                "R61_COMMIT_DENIED invalid transaction UUID",problem
            );
        }
        String a=props.getProperty("participant.a");
        String b=props.getProperty("participant.b");
        if(a==null||b==null||
           !a.matches("[a-z][a-z0-9_]{0,31}")||
           !b.matches("[a-z][a-z0-9_]{0,31}")||
           a.compareTo(b)>=0)
            throw new IOException(
                "R61_COMMIT_DENIED invalid participant identity"
            );
        String hashA=props.getProperty("a.after.sha256");
        String hashB=props.getProperty("b.after.sha256");
        if(hashA==null||!hashA.matches("[0-9a-f]{64}")||
           hashB==null||!hashB.matches("[0-9a-f]{64}"))
            throw new IOException(
                "R61_COMMIT_DENIED malformed snapshot hash"
            );
        return new Decision(id,a,b,hashA,hashB);
    }

    private R61TradePairReplay(){}
}
