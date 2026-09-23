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
        boolean externalOperationInFlight;
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

    RecipeAttemptId createAttempt(
        String actorRef,
        RecipeId recipeId
    ){
        String actor=
            requireText(
                actorRef,
                "actorRef"
            );
        RecipeDefinition recipe=
            catalog.require(
                Objects.requireNonNull(
                    recipeId,
                    "recipeId"
                )
            );

        /*
         * Eligibility is caller-owned policy. It may re-enter Conversion or
         * unrelated services, so never execute it under the Conversion lock.
         */
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

        synchronized(this){
            attempts.put(
                id,
                new Attempt(
                    id,
                    recipe,
                    actor
                )
            );
        }

        return id;
    }

    Snapshot reserveInputs(
        RecipeAttemptId attemptId,
        AtomicTransactionService.TransactionId transactionId
    ){
        final Attempt live;
        final AtomicTransactionService.TransactionId requested=
            Objects.requireNonNull(
                transactionId,
                "transactionId"
            );

        synchronized(this){
            live=require(attemptId);

            if(live.state!=AttemptState.CREATED)
                throw invalid(
                    live,
                    "reserveInputs"
                );

            reserveExternalOperation(live);
        }

        AtomicTransactionService.Snapshot transaction=null;
        RuntimeException failure=null;

        try{
            transaction=
                transactions.snapshot(
                    requested
                );

            if(transaction.state!=
                    AtomicTransactionService.TransactionState.RESERVED)
                throw new IllegalStateException(
                    "input transaction must be RESERVED "+
                    transaction.transactionId+
                    " state="+transaction.state
                );

            if(!live.actorRef.equals(
                    transaction.ownerRef))
                throw new IllegalStateException(
                    "transaction owner mismatch "+
                    transaction.transactionId
                );

            if(!exactInputCoverage(
                    live.recipe,
                    live.actorRef,
                    transaction))
                throw new IllegalStateException(
                    "transaction does not exactly cover recipe inputs "+
                    live.recipe.id
                );
        }catch(RuntimeException error){
            failure=error;
        }

        final Snapshot result;

        synchronized(this){
            Attempt current=require(attemptId);

            if(current!=live)
                throw new IllegalStateException(
                    "recipe attempt identity changed "+
                    attemptId
                );

            try{
                if(failure==null){
                    if(current.state!=AttemptState.CREATED)
                        throw invalid(
                            current,
                            "reserveInputs"
                        );

                    current.transactionId=
                        transaction.transactionId;
                    current.state=
                        AttemptState.RESERVED;
                }

                result=current.snapshot();
            }finally{
                current.externalOperationInFlight=false;
            }
        }

        if(failure!=null)
            throw failure;

        return result;
    }

    Snapshot resolveOutcome(
        RecipeAttemptId attemptId
    ){
        final Attempt live;
        final Snapshot before;

        synchronized(this){
            live=require(attemptId);

            if(live.state!=AttemptState.RESERVED)
                throw invalid(
                    live,
                    "resolveOutcome"
                );

            reserveExternalOperation(live);
            before=live.snapshot();
        }

        AtomicTransactionService.Snapshot transaction=null;
        OutcomeResolution resolution=null;
        RuntimeException failure=null;

        try{
            transaction=
                transactions.snapshot(
                    live.transactionId
                );

            if(transaction.state!=
                    AtomicTransactionService.TransactionState.RESERVED)
                throw new IllegalStateException(
                    "input transaction changed before outcome "+
                    transaction.transactionId+
                    " state="+transaction.state
                );

            resolution=
                Objects.requireNonNull(
                    outcomeResolver.resolve(
                        live.actorRef,
                        live.recipe,
                        before
                    ),
                    "outcome resolution"
                );
        }catch(RuntimeException error){
            failure=error;
        }

        final Snapshot result;

        synchronized(this){
            Attempt current=require(attemptId);

            if(current!=live)
                throw new IllegalStateException(
                    "recipe attempt identity changed "+
                    attemptId
                );

            try{
                if(failure==null){
                    if(current.state!=AttemptState.RESERVED)
                        throw invalid(
                            current,
                            "resolveOutcome"
                        );

                    current.outcome=
                        resolution.outcome;
                    current.inputSettlement=
                        resolution.inputSettlement;
                    current.state=
                        AttemptState.RESOLVED;
                }

                result=current.snapshot();
            }finally{
                current.externalOperationInFlight=false;
            }
        }

        if(failure!=null)
            throw failure;

        return result;
    }

    boolean acknowledgeSettlement(
        RecipeAttemptId attemptId
    ){
        final Attempt live;
        final AtomicTransactionService.TransactionId
            transactionId;
        final AtomicTransactionService.TransactionState
            expected;

        synchronized(this){
            live=require(attemptId);

            if(live.state==AttemptState.SETTLED)
                return false;

            if(live.state!=AttemptState.RESOLVED)
                throw invalid(
                    live,
                    "acknowledgeSettlement"
                );

            reserveExternalOperation(live);

            transactionId=
                live.transactionId;
            expected=
                live.inputSettlement==
                    InputSettlement.COMMIT_RESERVED_INPUTS
                        ?AtomicTransactionService.TransactionState.COMMITTED
                        :AtomicTransactionService.TransactionState.CANCELLED;
        }

        AtomicTransactionService.Snapshot transaction=null;
        RuntimeException failure=null;

        try{
            transaction=
                transactions.snapshot(
                    transactionId
                );

            if(transaction.state!=expected)
                throw new IllegalStateException(
                    "settlement transaction "+
                    transaction.transactionId+
                    " expected="+expected+
                    " actual="+transaction.state
                );
        }catch(RuntimeException error){
            failure=error;
        }

        synchronized(this){
            Attempt current=require(attemptId);

            if(current!=live)
                throw new IllegalStateException(
                    "recipe attempt identity changed "+
                    attemptId
                );

            try{
                if(failure==null){
                    if(current.state!=AttemptState.RESOLVED)
                        throw invalid(
                            current,
                            "acknowledgeSettlement"
                        );

                    current.state=
                        AttemptState.SETTLED;
                }
            }finally{
                current.externalOperationInFlight=false;
            }
        }

        if(failure!=null)
            throw failure;

        return true;
    }

    boolean cancelAttempt(
        RecipeAttemptId attemptId
    ){
        final Attempt live;
        final AtomicTransactionService.TransactionId
            transactionId;

        synchronized(this){
            live=require(attemptId);

            if(live.externalOperationInFlight)
                throw new IllegalStateException(
                    "recipe attempt external operation already in flight "+
                    live.id
                );

            if(live.state==AttemptState.CANCELLED)
                return false;

            if(live.state==AttemptState.SETTLED||
               live.state==AttemptState.RESOLVED)
                throw invalid(
                    live,
                    "cancelAttempt"
                );

            if(live.state==AttemptState.CREATED){
                live.state=
                    AttemptState.CANCELLED;
                return true;
            }

            reserveExternalOperation(live);
            transactionId=
                live.transactionId;
        }

        AtomicTransactionService.Snapshot transaction=null;
        RuntimeException failure=null;

        try{
            transaction=
                transactions.snapshot(
                    transactionId
                );

            if(transaction.state!=
                    AtomicTransactionService.TransactionState.CANCELLED)
                throw new IllegalStateException(
                    "reserved input transaction must be externally cancelled first "+
                    transaction.transactionId
                );
        }catch(RuntimeException error){
            failure=error;
        }

        synchronized(this){
            Attempt current=require(attemptId);

            if(current!=live)
                throw new IllegalStateException(
                    "recipe attempt identity changed "+
                    attemptId
                );

            try{
                if(failure==null){
                    if(current.state!=AttemptState.RESERVED)
                        throw invalid(
                            current,
                            "cancelAttempt"
                        );

                    current.state=
                        AttemptState.CANCELLED;
                }
            }finally{
                current.externalOperationInFlight=false;
            }
        }

        if(failure!=null)
            throw failure;

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

    private static void reserveExternalOperation(
        Attempt attempt
    ){
        if(attempt.externalOperationInFlight)
            throw new IllegalStateException(
                "recipe attempt external operation already in flight "+
                attempt.id
            );

        attempt.externalOperationInFlight=true;
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
