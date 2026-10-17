package spk.local;

import java.util.*;

/**
 * Exact-current Item Enchantment Chest application over RecipeCatalog and
 * ConversionService.
 *
 * The client proves categories/search/selection/attempt presentation and
 * result states. Recipe contents, search matching, RNG, costs, settlement and
 * transport remain caller/server authority.
 */
final class ItemEnchantmentService {
    static final int VISIBLE_ROW_LIMIT=16;
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    enum Category {
        ARMOR,
        WEAPONS,
        CAPES,
        TRINKETS,
        PETS,
        COSMETICS,
        MISC
    }

    enum PresentationState {
        IDLE,
        PREPARATION,
        SUCCESS,
        FAILURE
    }

    interface SearchMatcher {
        boolean matches(
            Entry entry,
            String normalizedQuery
        );
    }

    static final class Entry {
        final String enchantmentKey;
        final String displayName;
        final Category category;
        final RecipeId recipeId;
        final List<String> searchTerms;
        final AtomicTransactionService.SourceAuthority
            sourceAuthority;

        Entry(
            String enchantmentKey,
            String displayName,
            Category category,
            RecipeId recipeId,
            Collection<String> searchTerms,
            AtomicTransactionService.SourceAuthority
                sourceAuthority
        ){
            this.enchantmentKey=
                normalizeKey(
                    enchantmentKey,
                    "enchantmentKey"
                );
            this.displayName=
                requireText(
                    displayName,
                    "displayName"
                );
            this.category=
                Objects.requireNonNull(
                    category,
                    "category"
                );
            this.recipeId=
                Objects.requireNonNull(
                    recipeId,
                    "recipeId"
                );
            this.sourceAuthority=
                Objects.requireNonNull(
                    sourceAuthority,
                    "sourceAuthority"
                );

            Objects.requireNonNull(
                searchTerms,
                "searchTerms"
            );

            ArrayList<String> terms=
                new ArrayList<>();

            for(String term:searchTerms)
                terms.add(
                    normalizeSearch(
                        term
                    )
                );

            this.searchTerms=
                Collections.unmodifiableList(
                    terms
                );
        }
    }

    static final class AttemptSnapshot {
        final ConversionService.RecipeAttemptId
            attemptId;
        final String playerRef;
        final String enchantmentKey;
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
            this.enchantmentKey=
                binding.enchantmentKey;
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
        final Category selectedCategory;
        final String searchQuery;
        final String selectedEnchantmentKey;
        final RecipeId selectedRecipeId;
        final PresentationState presentationState;
        final ConversionService.RecipeAttemptId
            currentAttemptId;
        final String presentationAuthority;

        PlayerSnapshot(
            PlayerState state,
            Entry selected,
            AttemptSnapshot current
        ){
            this.playerRef=
                state.playerRef;
            this.selectedCategory=
                state.selectedCategory;
            this.searchQuery=
                state.searchQuery;
            this.selectedEnchantmentKey=
                selected==null
                    ?null
                    :selected.enchantmentKey;
            this.selectedRecipeId=
                selected==null
                    ?null
                    :selected.recipeId;
            this.currentAttemptId=
                current==null
                    ?null
                    :current.attemptId;
            this.presentationState=
                current==null
                    ?PresentationState.IDLE
                    :current.presentationState;
            this.presentationAuthority=
                PRESENTATION_AUTHORITY;
        }
    }

    static final class Snapshot {
        final List<Entry> catalog;
        final List<PlayerSnapshot> players;

        Snapshot(
            Collection<Entry> catalog,
            Collection<PlayerSnapshot> players
        ){
            this.catalog=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        catalog
                    )
                );
            this.players=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        players
                    )
                );
        }
    }

    private static final class AttemptBinding {
        final ConversionService.RecipeAttemptId
            attemptId;
        final String playerRef;
        final String enchantmentKey;
        final RecipeId recipeId;

        AttemptBinding(
            ConversionService.RecipeAttemptId attemptId,
            String playerRef,
            String enchantmentKey,
            RecipeId recipeId
        ){
            this.attemptId=attemptId;
            this.playerRef=playerRef;
            this.enchantmentKey=
                enchantmentKey;
            this.recipeId=recipeId;
        }
    }

    private static final class PlayerState {
        final String playerRef;
        Category selectedCategory;
        String searchQuery="";
        String selectedEnchantmentKey;
        ConversionService.RecipeAttemptId
            currentAttemptId;

        PlayerState(String playerRef){
            this.playerRef=playerRef;
        }
    }

    private final RecipeCatalog catalog;
    private final ConversionService conversions;
    private final SearchMatcher searchMatcher;
    private final AtomicTransactionService.SourceAuthority
        policyAuthority;

    private final LinkedHashMap<String,Entry>
        entries=
            new LinkedHashMap<>();

    private final HashMap<RecipeId,String>
        entryByRecipe=
            new HashMap<>();

    private final LinkedHashMap<String,PlayerState>
        players=
            new LinkedHashMap<>();

    private final LinkedHashMap<
        ConversionService.RecipeAttemptId,
        AttemptBinding
    > attempts=
        new LinkedHashMap<>();

    ItemEnchantmentService(
        RecipeCatalog catalog,
        ConversionService conversions,
        SearchMatcher searchMatcher,
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
        this.searchMatcher=
            Objects.requireNonNull(
                searchMatcher,
                "searchMatcher"
            );
        this.policyAuthority=
            Objects.requireNonNull(
                policyAuthority,
                "policyAuthority"
            );
    }

    synchronized Entry registerEntry(
        Entry entry
    ){
        Entry checked=
            Objects.requireNonNull(
                entry,
                "entry"
            );

        if(entries.containsKey(
                checked.enchantmentKey))
            throw new IllegalStateException(
                "duplicate enchantment key "+
                checked.enchantmentKey
            );

        if(entryByRecipe.containsKey(
                checked.recipeId))
            throw new IllegalStateException(
                "recipe already bound to enchantment "+
                checked.recipeId
            );

        RecipeDefinition recipe=
            catalog.require(
                checked.recipeId
            );

        if(checked.sourceAuthority!=
                policyAuthority||
           recipe.sourceAuthority!=
                policyAuthority)
            throw new IllegalArgumentException(
                "Item Enchantment authority mismatch "+
                checked.enchantmentKey
            );

        entries.put(
            checked.enchantmentKey,
            checked
        );
        entryByRecipe.put(
            checked.recipeId,
            checked.enchantmentKey
        );

        return checked;
    }

    synchronized PlayerSnapshot selectCategory(
        String playerRef,
        Category category
    ){
        PlayerState state=
            state(playerRef);

        state.selectedCategory=
            Objects.requireNonNull(
                category,
                "category"
            );

        if(state.selectedEnchantmentKey!=null&&
           !isVisible(
                state,
                state.selectedEnchantmentKey))
            state.selectedEnchantmentKey=
                null;

        return snapshotOf(state);
    }

    synchronized PlayerSnapshot clearCategory(
        String playerRef
    ){
        PlayerState state=
            state(playerRef);
        state.selectedCategory=null;

        if(state.selectedEnchantmentKey!=null&&
           !isVisible(
                state,
                state.selectedEnchantmentKey))
            state.selectedEnchantmentKey=
                null;

        return snapshotOf(state);
    }

    synchronized PlayerSnapshot setSearchQuery(
        String playerRef,
        String query
    ){
        PlayerState state=
            state(playerRef);
        state.searchQuery=
            normalizeSearch(
                query
            );

        if(state.selectedEnchantmentKey!=null&&
           !isVisible(
                state,
                state.selectedEnchantmentKey))
            state.selectedEnchantmentKey=
                null;

        return snapshotOf(state);
    }

    synchronized PlayerSnapshot clearSearchQuery(
        String playerRef
    ){
        return setSearchQuery(
            playerRef,
            ""
        );
    }

    synchronized List<Entry> visibleEntries(
        String playerRef
    ){
        return Collections.unmodifiableList(
            visibleEntries(
                state(playerRef)
            )
        );
    }

    synchronized PlayerSnapshot selectEntry(
        String playerRef,
        String enchantmentKey
    ){
        PlayerState state=
            state(playerRef);
        String key=
            normalizeKey(
                enchantmentKey,
                "enchantmentKey"
            );

        Entry entry=
            entries.get(key);

        if(entry==null)
            throw new IllegalArgumentException(
                "unknown enchantment "+
                key
            );

        if(!isVisible(
                state,
                key))
            throw new IllegalStateException(
                "enchantment not visible in current projection "+
                key+
                " player="+
                state.playerRef
            );

        state.selectedEnchantmentKey=
            key;

        return snapshotOf(state);
    }

    synchronized ConversionService.RecipeAttemptId
        beginAttempt(
            String playerRef
        ){
        PlayerState state=
            state(playerRef);

        if(state.selectedEnchantmentKey==null)
            throw new IllegalStateException(
                "Item Enchantment selection missing player="+
                state.playerRef
            );

        if(state.currentAttemptId!=null){
            ConversionService.Snapshot prior=
                conversions.get(
                    state.currentAttemptId
                );

            if(prior==null)
                throw new IllegalStateException(
                    "Item Enchantment current attempt disappeared "+
                    state.currentAttemptId
                );

            if(!prior.terminal())
                throw new IllegalStateException(
                    "Item Enchantment attempt already active "+
                    state.currentAttemptId
                );
        }

        Entry selected=
            requireEntry(
                state.selectedEnchantmentKey
            );

        ConversionService.RecipeAttemptId attemptId=
            conversions.createAttempt(
                state.playerRef,
                selected.recipeId
            );

        AttemptBinding binding=
            new AttemptBinding(
                attemptId,
                state.playerRef,
                selected.enchantmentKey,
                selected.recipeId
            );

        attempts.put(
            attemptId,
            binding
        );
        state.currentAttemptId=
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

        ConversionService.Snapshot result=
            conversions.reserveInputs(
                binding.attemptId,
                transactionId
            );

        return new AttemptSnapshot(
            binding,
            result
        );
    }

    synchronized AttemptSnapshot resolveOutcome(
        String playerRef
    ){
        AttemptBinding binding=
            requireCurrentBinding(
                playerRef
            );

        ConversionService.Snapshot result=
            conversions.resolveOutcome(
                binding.attemptId
            );

        return new AttemptSnapshot(
            binding,
            result
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

    synchronized boolean cancelAttempt(
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
        PlayerState state=
            state(playerRef);

        if(state.currentAttemptId==null)
            return snapshotOf(state);

        ConversionService.Snapshot attempt=
            conversions.get(
                state.currentAttemptId
            );

        if(attempt==null)
            throw new IllegalStateException(
                "Item Enchantment current attempt disappeared "+
                state.currentAttemptId
            );

        if(!attempt.terminal())
            throw new IllegalStateException(
                "cannot reset non-terminal Item Enchantment attempt "+
                state.currentAttemptId
            );

        state.currentAttemptId=null;

        return snapshotOf(state);
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
                "Item Enchantment attempt disappeared "+
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
        PlayerState state=
            players.get(
                normalizePlayer(
                    playerRef
                )
            );

        return state==null
            ?null
            :snapshotOf(state);
    }

    synchronized Snapshot snapshot(){
        ArrayList<PlayerSnapshot>
            playerSnapshots=
                new ArrayList<>();

        ArrayList<PlayerState>
            ordered=
                new ArrayList<>(
                    players.values()
                );

        ordered.sort(
            Comparator.comparing(
                value->value.playerRef
            )
        );

        for(PlayerState state:ordered)
            playerSnapshots.add(
                snapshotOf(state)
            );

        return new Snapshot(
            entries.values(),
            playerSnapshots
        );
    }

    synchronized int catalogSize(){
        return entries.size();
    }

    synchronized int attemptCount(){
        return attempts.size();
    }

    private PlayerSnapshot snapshotOf(
        PlayerState state
    ){
        Entry selected=
            state.selectedEnchantmentKey==null
                ?null
                :entries.get(
                    state.selectedEnchantmentKey
                );

        AttemptSnapshot current=null;

        if(state.currentAttemptId!=null)
            current=getAttempt(
                state.currentAttemptId
            );

        return new PlayerSnapshot(
            state,
            selected,
            current
        );
    }

    private ArrayList<Entry> visibleEntries(
        PlayerState state
    ){
        ArrayList<Entry> out=
            new ArrayList<>();

        for(Entry entry:entries.values()){
            if(state.selectedCategory!=null&&
               entry.category!=
                    state.selectedCategory)
                continue;

            if(!state.searchQuery.isEmpty()&&
               !searchMatcher.matches(
                    entry,
                    state.searchQuery))
                continue;

            out.add(entry);

            if(out.size()>=
                    VISIBLE_ROW_LIMIT)
                break;
        }

        return out;
    }

    private boolean isVisible(
        PlayerState state,
        String enchantmentKey
    ){
        for(Entry entry:
                visibleEntries(state))
            if(entry.enchantmentKey
                    .equals(
                        enchantmentKey))
                return true;

        return false;
    }

    private AttemptBinding requireCurrentBinding(
        String playerRef
    ){
        PlayerState state=
            state(playerRef);

        if(state.currentAttemptId==null)
            throw new IllegalStateException(
                "Item Enchantment attempt missing player="+
                state.playerRef
            );

        AttemptBinding binding=
            attempts.get(
                state.currentAttemptId
            );

        if(binding==null)
            throw new IllegalStateException(
                "Item Enchantment application binding missing "+
                state.currentAttemptId
            );

        if(!binding.playerRef.equals(
                state.playerRef))
            throw new IllegalStateException(
                "Item Enchantment attempt owner drift "+
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

    private Entry requireEntry(
        String enchantmentKey
    ){
        Entry entry=
            entries.get(
                enchantmentKey
            );

        if(entry==null)
            throw new IllegalArgumentException(
                "unknown enchantment "+
                enchantmentKey
            );

        return entry;
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
                    "resolved Item Enchantment attempt missing outcome "+
                    conversion.attemptId
                );

            case CANCELLED:
                return PresentationState.IDLE;

            default:
                throw new IllegalStateException(
                    "unknown Item Enchantment conversion state "+
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

    private static String normalizeSearch(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "search"
            );

        return value.trim()
            .toLowerCase(
                Locale.ROOT
            );
    }

    private static String requireText(
        String value,
        String field
    ){
        if(value==null)
            throw new NullPointerException(
                field
            );

        String clean=
            value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                field+" blank"
            );

        return clean;
    }
}
