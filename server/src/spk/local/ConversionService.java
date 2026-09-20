package spk.local;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Protocol-independent recipe/conversion attempt lifecycle.
 *
 * Inventory/bank mutation and transaction commit/cancel remain external. This
 * service only validates and references #159 escrow state.
 */
final class ConversionService {
    enum AttemptState {
        CREATED,
        RESERVED,
        RESOLVED,
        SETTLED,
        CANCELLED
    }

    enum OutcomeKind {
        SUCCESS,
        FAILURE
    }

    enum InputSettlement {
        COMMIT_RESERVED_INPUTS,
        CANCEL_RESERVED_INPUTS
    }

    static final class RecipeAttemptId {
        final long value;

        RecipeAttemptId(long value){
            if(value<=0)
                throw new IllegalArgumentException("value="+value);
            this.value=value;
        }

        @Override public boolean equals(Object other){
            return other instanceof RecipeAttemptId&&
                ((RecipeAttemptId)other).value==value;
        }

        @Override public int hashCode(){
            return Long.hashCode(value);
        }

        @Override public String toString(){
            return "recipe-attempt-"+
                Long.toUnsignedString(value);
        }
    }

    static final class EligibilityDecision {
        final boolean allowed;
        final String reason;

        private EligibilityDecision(
            boolean allowed,
            String reason
        ){
            this.allowed=allowed;
            this.reason=reason==null?"":reason.trim();
        }

        static EligibilityDecision allow(){
            return new EligibilityDecision(true,"");
        }

        static EligibilityDecision deny(String reason){
            if(reason==null||reason.trim().isEmpty())
                throw new IllegalArgumentException(
                    "denial reason"
                );
            return new EligibilityDecision(false,reason);
        }
    }

    interface EligibilityValidator {
        EligibilityDecision validate(
            String actorRef,
            RecipeDefinition recipe
        );
    }

    static final class OutcomeResolution {
        final OutcomeKind outcome;
        final InputSettlement inputSettlement;

        OutcomeResolution(
            OutcomeKind outcome,
            InputSettlement inputSettlement
        ){
            this.outcome=
                Objects.requireNonNull(
                    outcome,
                    "outcome"
                );
            this.inputSettlement=
                Objects.requireNonNull(
                    inputSettlement,
                    "inputSettlement"
                );
        }

        static OutcomeResolution success(
            InputSettlement inputSettlement
        ){
            return new OutcomeResolution(
                OutcomeKind.SUCCESS,
                inputSettlement
            );
        }

        static OutcomeResolution failure(
            InputSettlement inputSettlement
        ){
            return new OutcomeResolution(
                OutcomeKind.FAILURE,
                inputSettlement
            );
        }
    }

    interface RecipeOutcomeResolver {
        OutcomeResolution resolve(
            String actorRef,
            RecipeDefinition recipe,
            Snapshot attempt
        );
    }

    static final class Snapshot {
        final RecipeAttemptId attemptId;
        final RecipeId recipeId;
        final String actorRef;
        final AttemptState state;
        final AtomicTransactionService.TransactionId transactionId;
        final OutcomeKind outcome;
        final InputSettlement inputSettlement;
        final List<RecipeDefinition.RecipeOutput> outputs;
        final AtomicTransactionService.SourceAuthority sourceAuthority;

        Snapshot(Attempt attempt){
            this.attemptId=attempt.id;
            this.recipeId=attempt.recipe.id;
            this.actorRef=attempt.actorRef;
            this.state=attempt.state;
            this.transactionId=attempt.transactionId;
            this.outcome=attempt.outcome;
            this.inputSettlement=attempt.inputSettlement;
            this.outputs=
                attempt.outcome==OutcomeKind.SUCCESS
                    ?attempt.recipe.outputs
                    :Collections.emptyList();
            this.sourceAuthority=
                attempt.recipe.sourceAuthority;
        }

        boolean terminal(){
            return state==AttemptState.SETTLED||
                state==AttemptState.CANCELLED;
        }

        @Override public String toString(){
            return "ConversionAttempt{"+
                "attemptId="+attemptId+
                ",recipeId="+recipeId+
                ",actorRef="+actorRef+
                ",state="+state+
                ",transactionId="+transactionId+
                ",outcome="+outcome+
                ",inputSettlement="+inputSettlement+
                ",sourceAuthority="+sourceAuthority+
                "}";
        }
    }

    private static final class Attempt {
        final RecipeAttemptId id;
        final RecipeDefinition recipe;
        final String actorRef;

        AttemptState state=AttemptState.CREATED;
        AtomicTransactionService.TransactionId transactionId;
        OutcomeKind outcome;
        InputSettlement inputSettlement;

        Attempt(
            RecipeAttemptId id,
            RecipeDefinition recipe,
            String actorRef
        ){
            this.id=id;
            this.recipe=recipe;
            this.actorRef=actorRef;
        }

        Snapshot snapshot(){
            return new Snapshot(this);
        }
    }

    private final RecipeCatalog catalog;
    private final AtomicTransactionService transactions;
    private final EligibilityValidator eligibilityValidator;
    private final RecipeOutcomeResolver outcomeResolver;
    private final AtomicLong sequence=new AtomicLong();
    private final LinkedHashMap<RecipeAttemptId,Attempt> attempts=
        new LinkedHashMap<>();

    ConversionService(
        RecipeCatalog catalog,
        AtomicTransactionService transactions,
        EligibilityValidator eligibilityValidator,
        RecipeOutcomeResolver outcomeResolver
    ){
        this.catalog=Objects.requireNonNull(catalog,"catalog");
        this.transactions=
            Objects.requireNonNull(
                transactions,
                "transactions"
            );
        this.eligibilityValidator=
            Objects.requireNonNull(
                eligibilityValidator,
                "eligibilityValidator"
            );
        this.outcomeResolver=
            Objects.requireNonNull(
                outcomeResolver,
                "outcomeResolver"
            );
    }

    synchronized RecipeAttemptId createAttempt(
        String actorRef,
        RecipeId recipeId
    ){
        String actor=requireText(actorRef,"actorRef");
        RecipeDefinition recipe=
            catalog.require(
                Objects.requireNonNull(
                    recipeId,
                    "recipeId"
                )
            );

        EligibilityDecision decision=
            Objects.requireNonNull(
                eligibilityValidator.validate(
                    actor,
                    recipe
                ),
                "eligibility decision"
            );

        if(!decision.allowed)
            throw new IllegalStateException(
                "recipe ineligible "+
                recipe.id+
                " actor="+actor+
                " reason="+decision.reason
            );

        RecipeAttemptId id=
            new RecipeAttemptId(
                sequence.incrementAndGet()
            );

        attempts.put(
            id,
            new Attempt(
                id,
                recipe,
                actor
            )
        );

        return id;
    }

    synchronized Snapshot reserveInputs(
        RecipeAttemptId attemptId,
        AtomicTransactionService.TransactionId transactionId
    ){
        Attempt attempt=require(attemptId);

        if(attempt.state!=AttemptState.CREATED)
            throw invalid(attempt,"reserveInputs");

        AtomicTransactionService.Snapshot transaction=
            transactions.snapshot(
                Objects.requireNonNull(
                    transactionId,
                    "transactionId"
                )
            );

        if(transaction.state!=
                AtomicTransactionService.TransactionState.RESERVED)
            throw new IllegalStateException(
                "input transaction must be RESERVED "+
                transaction.transactionId+
                " state="+transaction.state
            );

        if(!attempt.actorRef.equals(
                transaction.ownerRef))
            throw new IllegalStateException(
                "transaction owner mismatch "+
                transaction.transactionId
            );

        if(!exactInputCoverage(
                attempt.recipe,
                attempt.actorRef,
                transaction))
            throw new IllegalStateException(
                "transaction does not exactly cover recipe inputs "+
                attempt.recipe.id
            );

        attempt.transactionId=transaction.transactionId;
        attempt.state=AttemptState.RESERVED;
        return attempt.snapshot();
    }

    synchronized Snapshot resolveOutcome(
        RecipeAttemptId attemptId
    ){
        Attempt attempt=require(attemptId);

        if(attempt.state!=AttemptState.RESERVED)
            throw invalid(attempt,"resolveOutcome");

        AtomicTransactionService.Snapshot transaction=
            transactions.snapshot(
                attempt.transactionId
            );

        if(transaction.state!=
                AtomicTransactionService.TransactionState.RESERVED)
            throw new IllegalStateException(
                "input transaction changed before outcome "+
                transaction.transactionId+
                " state="+transaction.state
            );

        OutcomeResolution resolution=
            Objects.requireNonNull(
                outcomeResolver.resolve(
                    attempt.actorRef,
                    attempt.recipe,
                    attempt.snapshot()
                ),
                "outcome resolution"
            );

        attempt.outcome=resolution.outcome;
        attempt.inputSettlement=
            resolution.inputSettlement;
        attempt.state=AttemptState.RESOLVED;

        return attempt.snapshot();
    }

    synchronized boolean acknowledgeSettlement(
        RecipeAttemptId attemptId
    ){
        Attempt attempt=require(attemptId);

        if(attempt.state==AttemptState.SETTLED)
            return false;

        if(attempt.state!=AttemptState.RESOLVED)
            throw invalid(
                attempt,
                "acknowledgeSettlement"
            );

        AtomicTransactionService.Snapshot transaction=
            transactions.snapshot(
                attempt.transactionId
            );

        AtomicTransactionService.TransactionState expected=
            attempt.inputSettlement==
                InputSettlement.COMMIT_RESERVED_INPUTS
                    ?AtomicTransactionService.TransactionState.COMMITTED
                    :AtomicTransactionService.TransactionState.CANCELLED;

        if(transaction.state!=expected)
            throw new IllegalStateException(
                "settlement transaction "+
                transaction.transactionId+
                " expected="+expected+
                " actual="+transaction.state
            );

        attempt.state=AttemptState.SETTLED;
        return true;
    }

    synchronized boolean cancelAttempt(
        RecipeAttemptId attemptId
    ){
        Attempt attempt=require(attemptId);

        if(attempt.state==AttemptState.CANCELLED)
            return false;

        if(attempt.state==AttemptState.SETTLED||
           attempt.state==AttemptState.RESOLVED)
            throw invalid(attempt,"cancelAttempt");

        if(attempt.state==AttemptState.RESERVED){
            AtomicTransactionService.Snapshot transaction=
                transactions.snapshot(
                    attempt.transactionId
                );

            if(transaction.state!=
                    AtomicTransactionService.TransactionState.CANCELLED)
                throw new IllegalStateException(
                    "reserved input transaction must be externally cancelled first "+
                    transaction.transactionId
                );
        }

        attempt.state=AttemptState.CANCELLED;
        return true;
    }

    synchronized Snapshot get(
        RecipeAttemptId attemptId
    ){
        Attempt attempt=attempts.get(
            Objects.requireNonNull(
                attemptId,
                "attemptId"
            )
        );

        return attempt==null?null:attempt.snapshot();
    }

    synchronized int size(){
        return attempts.size();
    }

    synchronized List<Snapshot> snapshot(){
        ArrayList<Snapshot> out=new ArrayList<>();

        for(Attempt attempt:attempts.values())
            out.add(attempt.snapshot());

        return Collections.unmodifiableList(out);
    }

    private Attempt require(
        RecipeAttemptId attemptId
    ){
        RecipeAttemptId id=
            Objects.requireNonNull(
                attemptId,
                "attemptId"
            );

        Attempt attempt=attempts.get(id);

        if(attempt==null)
            throw new IllegalArgumentException(
                "unknown recipe attempt "+id
            );

        return attempt;
    }

    private static boolean exactInputCoverage(
        RecipeDefinition recipe,
        String actorRef,
        AtomicTransactionService.Snapshot transaction
    ){
        LinkedHashMap<String,Long> expected=
            new LinkedHashMap<>();

        for(RecipeDefinition.RecipeIngredient input:
                recipe.inputs)
            expected.put(
                input.identityKey(),
                input.quantity
            );

        LinkedHashMap<String,Long> actual=
            new LinkedHashMap<>();

        for(AtomicTransactionService.Reservation reservation:
                transaction.reservations){
            EscrowAsset asset=reservation.asset;

            if(!actorRef.equals(asset.ownerRef))
                return false;

            RecipeDefinition.AssetKind kind=
                RecipeDefinition.AssetKind.valueOf(
                    asset.kind.name()
                );

            String key=
                kind.name()+":"+
                RecipeDefinition.normalizeAssetKey(
                    asset.semanticKey
                );

            long next;

            try{
                next=Math.addExact(
                    actual.getOrDefault(key,0L),
                    asset.quantity
                );
            }catch(ArithmeticException error){
                throw new IllegalStateException(
                    "reserved input quantity overflow",
                    error
                );
            }

            actual.put(key,next);
        }

        return expected.equals(actual);
    }

    private static IllegalStateException invalid(
        Attempt attempt,
        String operation
    ){
        return new IllegalStateException(
            operation+
            " invalid from "+
            attempt.state+
            " for "+
            attempt.id
        );
    }

    private static String requireText(
        String value,
        String field
    ){
        if(value==null)
            throw new NullPointerException(field);

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                field+" blank"
            );

        return clean;
    }
}
