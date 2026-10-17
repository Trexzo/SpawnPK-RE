package spk.local;

import java.util.*;

/**
 * Legendary Pet Fusing application over RecipeCatalog + ConversionService.
 *
 * Exact-current client evidence proves a single visible fusion offering and
 * a Fuse action. The legacy static recipe/date are presentation evidence only,
 * not live server recipe authority.
 */
final class LegendaryPetFusionService {
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    enum PresentationState {
        IDLE,
        PREPARATION,
        SUCCESS,
        FAILURE
    }

    static final class Offering {
        final String offeringKey;
        final String displayName;
        final RecipeId recipeId;
        final String statusText;
        final String availabilityText;
        final AtomicTransactionService.SourceAuthority
            sourceAuthority;

        Offering(
            String offeringKey,
            String displayName,
            RecipeId recipeId,
            String statusText,
            String availabilityText,
            AtomicTransactionService.SourceAuthority
                sourceAuthority
        ){
            this.offeringKey=
                normalizeKey(
                    offeringKey,
                    "offeringKey"
                );
            this.displayName=
                requireText(
                    displayName,
                    "displayName"
                );
            this.recipeId=
                Objects.requireNonNull(
                    recipeId,
                    "recipeId"
                );
            this.statusText=
                requireText(
                    statusText,
                    "statusText"
                );
            this.availabilityText=
                availabilityText==null
                    ?null
                    :availabilityText.trim();
            this.sourceAuthority=
                Objects.requireNonNull(
                    sourceAuthority,
                    "sourceAuthority"
                );
        }
    }

    static final class AttemptSnapshot {
        final ConversionService.RecipeAttemptId
            attemptId;
        final String playerRef;
        final String offeringKey;
        final RecipeId recipeId;
        final ConversionService.Snapshot conversion;
        final PresentationState presentationState;

        AttemptSnapshot(
            AttemptBinding binding,
            ConversionService.Snapshot conversion
        ){
            this.attemptId=
                binding.attemptId;
            this.playerRef=
                binding.playerRef;
            this.offeringKey=
                binding.offeringKey;
            this.recipeId=
                binding.recipeId;
            this.conversion=
                Objects.requireNonNull(
                    conversion,
                    "conversion"
                );
            this.presentationState=
                presentationState(
                    conversion
                );
        }
    }

    static final class PlayerSnapshot {
        final String playerRef;
        final ConversionService.RecipeAttemptId
            currentAttemptId;
        final String currentAttemptOfferingKey;
        final RecipeId currentAttemptRecipeId;
        final PresentationState presentationState;

        PlayerSnapshot(
            PlayerState state,
            AttemptSnapshot current
        ){
            this.playerRef=
                state.playerRef;
            this.currentAttemptId=
                current==null
                    ?null
                    :current.attemptId;
            this.currentAttemptOfferingKey=
                current==null
                    ?null
                    :current.offeringKey;
            this.currentAttemptRecipeId=
                current==null
                    ?null
                    :current.recipeId;
            this.presentationState=
                current==null
                    ?PresentationState.IDLE
                    :current.presentationState;
        }
    }

    static final class Snapshot {
        final Offering currentOffering;
        final List<PlayerSnapshot> players;
        final String presentationAuthority;

        Snapshot(
            Offering currentOffering,
            Collection<PlayerSnapshot> players
        ){
            this.currentOffering=
                currentOffering;
            this.players=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        players
                    )
                );
            this.presentationAuthority=
                PRESENTATION_AUTHORITY;
        }
    }

    private static final class AttemptBinding {
        final ConversionService.RecipeAttemptId
            attemptId;
        final String playerRef;
        final String offeringKey;
        final RecipeId recipeId;

        AttemptBinding(
            ConversionService.RecipeAttemptId attemptId,
            String playerRef,
            String offeringKey,
            RecipeId recipeId
        ){
            this.attemptId=attemptId;
            this.playerRef=playerRef;
            this.offeringKey=offeringKey;
            this.recipeId=recipeId;
        }
    }

    private static final class PlayerState {
        final String playerRef;
        ConversionService.RecipeAttemptId
            currentAttemptId;

        PlayerState(String playerRef){
            this.playerRef=playerRef;
        }
    }

    private final RecipeCatalog catalog;
    private final ConversionService conversions;
    private final AtomicTransactionService.SourceAuthority
        policyAuthority;

    private Offering currentOffering;

    private final LinkedHashMap<String,PlayerState>
        players=
            new LinkedHashMap<>();

    private final LinkedHashMap<
        ConversionService.RecipeAttemptId,
        AttemptBinding
    > attempts=
        new LinkedHashMap<>();

    LegendaryPetFusionService(
        RecipeCatalog catalog,
        ConversionService conversions,
        AtomicTransactionService.SourceAuthority
            policyAuthority
    ){
        this.catalog=
            Objects.requireNonNull(
                catalog,
                "catalog"
            );
        this.conversions=
            Objects.requireNonNull(
                conversions,
                "conversions"
            );
        this.policyAuthority=
            Objects.requireNonNull(
                policyAuthority,
                "policyAuthority"
            );
    }

    synchronized Snapshot replaceOffering(
        Offering offering
    ){
        Offering checked=
            validateOffering(
                Objects.requireNonNull(
                    offering,
                    "offering"
                )
            );

        requireCurrentOfferingNotActive(
            "replaceOffering"
        );

        currentOffering=checked;

        return snapshot();
    }

    synchronized Snapshot clearOffering(){
        requireCurrentOfferingNotActive(
            "clearOffering"
        );

        currentOffering=null;

        return snapshot();
    }

    synchronized ConversionService.RecipeAttemptId
        beginFusion(
            String playerRef
        ){
        if(currentOffering==null)
            throw new IllegalStateException(
                "Legendary Pet Fusion offering missing"
            );

        PlayerState player=
            state(playerRef);

        if(player.currentAttemptId!=null){
            ConversionService.Snapshot prior=
                conversions.get(
                    player.currentAttemptId
                );

            if(prior==null)
                throw new IllegalStateException(
                    "Legendary Pet Fusion attempt disappeared "+
                    player.currentAttemptId
                );

            if(!prior.terminal())
                throw new IllegalStateException(
                    "Legendary Pet Fusion attempt already active "+
                    player.currentAttemptId
                );
        }

        Offering offering=
            currentOffering;

        ConversionService.RecipeAttemptId attemptId=
            conversions.createAttempt(
                player.playerRef,
                offering.recipeId
            );

        AttemptBinding binding=
            new AttemptBinding(
                attemptId,
                player.playerRef,
                offering.offeringKey,
                offering.recipeId
            );

        attempts.put(
            attemptId,
            binding
        );
        player.currentAttemptId=
            attemptId;

        return attemptId;
    }

    synchronized AttemptSnapshot reserveInputs(
        String playerRef,
        AtomicTransactionService.TransactionId
            transactionId
    ){
        AttemptBinding binding=
            requireCurrentBinding(
                playerRef
            );

        return new AttemptSnapshot(
            binding,
            conversions.reserveInputs(
                binding.attemptId,
                transactionId
            )
        );
    }

    synchronized AttemptSnapshot resolveOutcome(
        String playerRef
    ){
        AttemptBinding binding=
            requireCurrentBinding(
                playerRef
            );

        return new AttemptSnapshot(
            binding,
            conversions.resolveOutcome(
                binding.attemptId
            )
        );
    }

    synchronized boolean acknowledgeSettlement(
        String playerRef
    ){
        AttemptBinding binding=
            requireCurrentBinding(
                playerRef
            );

        return conversions.acknowledgeSettlement(
            binding.attemptId
        );
    }

    synchronized boolean cancelFusion(
        String playerRef
    ){
        AttemptBinding binding=
            requireCurrentBinding(
                playerRef
            );

        return conversions.cancelAttempt(
            binding.attemptId
        );
    }

    synchronized PlayerSnapshot resetResult(
        String playerRef
    ){
        PlayerState player=
            state(playerRef);

        if(player.currentAttemptId==null)
            return snapshotOf(player);

        ConversionService.Snapshot attempt=
            conversions.get(
                player.currentAttemptId
            );

        if(attempt==null)
            throw new IllegalStateException(
                "Legendary Pet Fusion attempt disappeared "+
                player.currentAttemptId
            );

        if(!attempt.terminal())
            throw new IllegalStateException(
                "cannot reset non-terminal Legendary Pet Fusion "+
                player.currentAttemptId
            );

        player.currentAttemptId=null;

        return snapshotOf(player);
    }

    synchronized AttemptSnapshot getAttempt(
        ConversionService.RecipeAttemptId
            attemptId
    ){
        AttemptBinding binding=
            attempts.get(
                Objects.requireNonNull(
                    attemptId,
                    "attemptId"
                )
            );

        if(binding==null)
            return null;

        ConversionService.Snapshot conversion=
            conversions.get(
                binding.attemptId
            );

        if(conversion==null)
            throw new IllegalStateException(
                "Legendary Pet Fusion conversion disappeared "+
                binding.attemptId
            );

        return new AttemptSnapshot(
            binding,
            conversion
        );
    }

    synchronized PlayerSnapshot getPlayer(
        String playerRef
    ){
        PlayerState player=
            players.get(
                normalizePlayer(
                    playerRef
                )
            );

        return player==null
            ?null
            :snapshotOf(player);
    }

    synchronized Snapshot snapshot(){
        ArrayList<PlayerState> ordered=
            new ArrayList<>(
                players.values()
            );

        ordered.sort(
            Comparator.comparing(
                value->value.playerRef
            )
        );

        ArrayList<PlayerSnapshot> out=
            new ArrayList<>();

        for(PlayerState player:ordered)
            out.add(
                snapshotOf(player)
            );

        return new Snapshot(
            currentOffering,
            out
        );
    }

    synchronized int attemptCount(){
        return attempts.size();
    }

    private Offering validateOffering(
        Offering offering
    ){
        RecipeDefinition recipe=
            catalog.require(
                offering.recipeId
            );

        if(offering.sourceAuthority!=
                policyAuthority||
           recipe.sourceAuthority!=
                policyAuthority)
            throw new IllegalArgumentException(
                "Legendary Pet Fusion authority mismatch "+
                offering.offeringKey
            );

        return offering;
    }

    private void requireCurrentOfferingNotActive(
        String operation
    ){
        if(currentOffering==null)
            return;

        for(AttemptBinding binding:
                attempts.values()){
            if(!binding.offeringKey.equals(
                    currentOffering.offeringKey))
                continue;

            ConversionService.Snapshot conversion=
                conversions.get(
                    binding.attemptId
                );

            if(conversion==null)
                throw new IllegalStateException(
                    "Legendary Pet Fusion conversion disappeared "+
                    binding.attemptId
                );

            if(!conversion.terminal())
                throw new IllegalStateException(
                    operation+
                    " blocked by active Legendary Pet Fusion "+
                    binding.attemptId
                );
        }
    }

    private PlayerSnapshot snapshotOf(
        PlayerState player
    ){
        AttemptSnapshot current=
            player.currentAttemptId==null
                ?null
                :getAttempt(
                    player.currentAttemptId
                );

        return new PlayerSnapshot(
            player,
            current
        );
    }

    private AttemptBinding requireCurrentBinding(
        String playerRef
    ){
        PlayerState player=
            state(playerRef);

        if(player.currentAttemptId==null)
            throw new IllegalStateException(
                "Legendary Pet Fusion attempt missing player="+
                player.playerRef
            );

        AttemptBinding binding=
            attempts.get(
                player.currentAttemptId
            );

        if(binding==null)
            throw new IllegalStateException(
                "Legendary Pet Fusion binding missing "+
                player.currentAttemptId
            );

        if(!binding.playerRef.equals(
                player.playerRef))
            throw new IllegalStateException(
                "Legendary Pet Fusion owner drift "+
                binding.attemptId
            );

        return binding;
    }

    private PlayerState state(
        String playerRef
    ){
        String player=
            normalizePlayer(
                playerRef
            );

        return players.computeIfAbsent(
            player,
            PlayerState::new
        );
    }

    private static PresentationState
        presentationState(
            ConversionService.Snapshot conversion
        ){
        switch(conversion.state){
            case CREATED:
            case RESERVED:
                return PresentationState.PREPARATION;

            case RESOLVED:
            case SETTLED:
                if(conversion.outcome==
                        ConversionService
                            .OutcomeKind.SUCCESS)
                    return PresentationState.SUCCESS;

                if(conversion.outcome==
                        ConversionService
                            .OutcomeKind.FAILURE)
                    return PresentationState.FAILURE;

                throw new IllegalStateException(
                    "Legendary Pet Fusion resolved without outcome "+
                    conversion.attemptId
                );

            case CANCELLED:
                return PresentationState.IDLE;

            default:
                throw new IllegalStateException(
                    "unknown Legendary Pet Fusion state "+
                    conversion.state
                );
        }
    }

    private static String normalizePlayer(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "playerRef"
            );

        String normalized=
            value.trim()
                .toLowerCase(
                    Locale.ROOT
                );

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                "playerRef blank"
            );

        return normalized;
    }

    private static String normalizeKey(
        String value,
        String field
    ){
        String normalized=
            requireText(
                value,
                field
            ).toLowerCase(
                Locale.ROOT
            );

        for(int i=0;i<
                normalized.length();i++){
            char c=
                normalized.charAt(i);

            boolean ok=
                c>='a'&&c<='z'||
                c>='0'&&c<='9'||
                c=='.'||
                c=='_'||
                c=='-'||
                c==':';

            if(!ok)
                throw new IllegalArgumentException(
                    field+" invalid="+
                    value
                );
        }

        return normalized;
    }

    private static String requireText(
        String value,
        String field
    ){
        if(value==null)
            throw new NullPointerException(
                field
            );

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                field+" blank"
            );

        return clean;
    }
}
