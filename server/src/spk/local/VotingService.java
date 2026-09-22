package spk.local;

import java.util.*;

/**
 * Externally-verified Voting point ledger.
 *
 * Exact-current client evidence proves the visible provider set and a
 * ::vote -> ::redeem vote points -> shop-spend flow. Provider verification,
 * reward rate, browser actions, cooldowns, ticket conversion and persistence
 * remain external/server authority.
 */
final class VotingService {
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    enum Provider {
        TOPG,
        RUNELOCUS,
        RSPS_LIST
    }

    enum SpendState {
        RESERVED,
        SETTLED,
        CANCELLED
    }

    static final class SpendSnapshot {
        final String spendKey;
        final long points;
        final SpendState state;

        SpendSnapshot(Spend spend){
            this.spendKey=spend.spendKey;
            this.points=spend.points;
            this.state=spend.state;
        }

        boolean terminal(){
            return state!=SpendState.RESERVED;
        }
    }

    static final class PlayerSnapshot {
        final String playerRef;
        final long balance;
        final long reserved;
        final long available;
        final long lifetimeCredited;
        final long lifetimeSpent;
        final Map<Provider,Long> creditedByProvider;
        final List<SpendSnapshot> spends;
        final String presentationAuthority;

        PlayerSnapshot(PlayerState state){
            this.playerRef=state.playerRef;
            this.balance=state.balance;
            this.reserved=state.reserved;
            this.available=
                state.balance-
                    state.reserved;
            this.lifetimeCredited=
                state.lifetimeCredited;
            this.lifetimeSpent=
                state.lifetimeSpent;

            EnumMap<Provider,Long> providerCopy=
                new EnumMap<>(
                    Provider.class
                );

            for(Provider provider:
                    Provider.values())
                providerCopy.put(
                    provider,
                    state.creditedByProvider
                        .getOrDefault(
                            provider,
                            0L
                        )
                );

            this.creditedByProvider=
                Collections.unmodifiableMap(
                    providerCopy
                );

            ArrayList<SpendSnapshot> spendCopy=
                new ArrayList<>();

            for(Spend spend:
                    state.spends.values())
                spendCopy.add(
                    new SpendSnapshot(spend)
                );

            this.spends=
                Collections.unmodifiableList(
                    spendCopy
                );
            this.presentationAuthority=
                PRESENTATION_AUTHORITY;
        }

        long creditedBy(
            Provider provider
        ){
            return creditedByProvider
                .get(
                    Objects.requireNonNull(
                        provider,
                        "provider"
                    )
                );
        }

        SpendSnapshot spend(
            String spendKey
        ){
            String key=
                normalizeKey(
                    spendKey,
                    "spendKey"
                );

            for(SpendSnapshot spend:spends)
                if(spend.spendKey
                        .equals(key))
                    return spend;

            return null;
        }
    }

    static final class CreditResult {
        final boolean changed;
        final PlayerSnapshot player;

        CreditResult(
            boolean changed,
            PlayerSnapshot player
        ){
            this.changed=changed;
            this.player=
                Objects.requireNonNull(
                    player,
                    "player"
                );
        }
    }

    static final class SpendResult {
        final boolean changed;
        final PlayerSnapshot player;
        final SpendSnapshot spend;

        SpendResult(
            boolean changed,
            PlayerSnapshot player,
            SpendSnapshot spend
        ){
            this.changed=changed;
            this.player=
                Objects.requireNonNull(
                    player,
                    "player"
                );
            this.spend=
                Objects.requireNonNull(
                    spend,
                    "spend"
                );
        }
    }

    private static final class VerifiedReceipt {
        final String playerRef;
        final long points;
        final String verificationAuthority;

        VerifiedReceipt(
            String playerRef,
            long points,
            String verificationAuthority
        ){
            this.playerRef=playerRef;
            this.points=points;
            this.verificationAuthority=
                verificationAuthority;
        }
    }

    private static final class Spend {
        final String spendKey;
        final long points;
        SpendState state=
            SpendState.RESERVED;

        Spend(
            String spendKey,
            long points
        ){
            this.spendKey=spendKey;
            this.points=points;
        }
    }

    private static final class PlayerState {
        final String playerRef;
        long balance;
        long reserved;
        long lifetimeCredited;
        long lifetimeSpent;

        final EnumMap<Provider,Long>
            creditedByProvider=
                new EnumMap<>(
                    Provider.class
                );

        final LinkedHashMap<String,Spend>
            spends=
                new LinkedHashMap<>();

        PlayerState(String playerRef){
            this.playerRef=playerRef;
        }
    }

    private final LinkedHashMap<String,PlayerState>
        players=
            new LinkedHashMap<>();

    private final HashMap<String,VerifiedReceipt>
        verifiedReceipts=
            new HashMap<>();

    /**
     * Call only after external provider/backend verification has succeeded.
     */
    synchronized CreditResult confirmVerifiedVote(
        String playerRef,
        Provider provider,
        String receiptKey,
        long points,
        String verificationAuthority
    ){
        if(points<=0L)
            throw new IllegalArgumentException(
                "points="+points
            );

        String player=
            normalizePlayer(
                playerRef
            );
        Provider checkedProvider=
            Objects.requireNonNull(
                provider,
                "provider"
            );
        String receipt=
            normalizeKey(
                receiptKey,
                "receiptKey"
            );
        String authority=
            requireText(
                verificationAuthority,
                "verificationAuthority"
            );

        String receiptIdentity=
            checkedProvider.name()+
                ":"+
                receipt;

        VerifiedReceipt prior=
            verifiedReceipts.get(
                receiptIdentity
            );

        if(prior!=null){
            if(!prior.playerRef.equals(
                    player)||
               prior.points!=points||
               !prior.verificationAuthority
                    .equals(authority))
                throw new IllegalStateException(
                    "Voting receipt replay mismatch "+
                    receiptIdentity
                );

            return new CreditResult(
                false,
                new PlayerSnapshot(
                    requirePlayer(player)
                )
            );
        }

        PlayerState state=
            players.computeIfAbsent(
                player,
                PlayerState::new
            );

        long nextBalance=
            Math.addExact(
                state.balance,
                points
            );
        long nextLifetime=
            Math.addExact(
                state.lifetimeCredited,
                points
            );
        long providerCurrent=
            state.creditedByProvider
                .getOrDefault(
                    checkedProvider,
                    0L
                );
        long providerNext=
            Math.addExact(
                providerCurrent,
                points
            );

        state.balance=nextBalance;
        state.lifetimeCredited=
            nextLifetime;
        state.creditedByProvider.put(
            checkedProvider,
            providerNext
        );

        verifiedReceipts.put(
            receiptIdentity,
            new VerifiedReceipt(
                player,
                points,
                authority
            )
        );

        return new CreditResult(
            true,
            new PlayerSnapshot(state)
        );
    }

    synchronized SpendResult reserveExternalSpend(
        String playerRef,
        String spendKey,
        long points
    ){
        if(points<=0L)
            throw new IllegalArgumentException(
                "points="+points
            );

        PlayerState state=
            requirePlayer(
                normalizePlayer(
                    playerRef
                )
            );

        String key=
            normalizeKey(
                spendKey,
                "spendKey"
            );

        Spend prior=
            state.spends.get(key);

        if(prior!=null){
            if(prior.points!=points)
                throw new IllegalStateException(
                    "Voting spend replay mismatch "+
                    key
                );

            PlayerSnapshot snapshot=
                new PlayerSnapshot(state);

            return new SpendResult(
                false,
                snapshot,
                snapshot.spend(key)
            );
        }

        long available=
            state.balance-
                state.reserved;

        if(points>available)
            throw new IllegalStateException(
                "insufficient available vote points player="+
                state.playerRef+
                " requested="+points+
                " available="+available
            );

        long nextReserved=
            Math.addExact(
                state.reserved,
                points
            );

        Spend spend=
            new Spend(
                key,
                points
            );

        state.reserved=nextReserved;
        state.spends.put(
            key,
            spend
        );

        PlayerSnapshot snapshot=
            new PlayerSnapshot(state);

        return new SpendResult(
            true,
            snapshot,
            snapshot.spend(key)
        );
    }

    /**
     * Call only after the external shop/reward settlement succeeds.
     */
    synchronized SpendResult confirmExternalSpendSettled(
        String playerRef,
        String spendKey
    ){
        PlayerState state=
            requirePlayer(
                normalizePlayer(
                    playerRef
                )
            );
        String key=
            normalizeKey(
                spendKey,
                "spendKey"
            );
        Spend spend=
            requireSpend(
                state,
                key
            );

        if(spend.state==
                SpendState.SETTLED){
            PlayerSnapshot snapshot=
                new PlayerSnapshot(state);

            return new SpendResult(
                false,
                snapshot,
                snapshot.spend(key)
            );
        }

        if(spend.state==
                SpendState.CANCELLED)
            throw new IllegalStateException(
                "cancelled Voting spend cannot settle "+
                key
            );

        if(state.reserved<
                spend.points||
           state.balance<
                spend.points)
            throw new IllegalStateException(
                "Voting spend reservation drift "+
                key
            );

        state.reserved=
            Math.subtractExact(
                state.reserved,
                spend.points
            );
        state.balance=
            Math.subtractExact(
                state.balance,
                spend.points
            );
        state.lifetimeSpent=
            Math.addExact(
                state.lifetimeSpent,
                spend.points
            );
        spend.state=
            SpendState.SETTLED;

        PlayerSnapshot snapshot=
            new PlayerSnapshot(state);

        return new SpendResult(
            true,
            snapshot,
            snapshot.spend(key)
        );
    }

    synchronized SpendResult cancelExternalSpend(
        String playerRef,
        String spendKey
    ){
        PlayerState state=
            requirePlayer(
                normalizePlayer(
                    playerRef
                )
            );
        String key=
            normalizeKey(
                spendKey,
                "spendKey"
            );
        Spend spend=
            requireSpend(
                state,
                key
            );

        if(spend.state==
                SpendState.CANCELLED){
            PlayerSnapshot snapshot=
                new PlayerSnapshot(state);

            return new SpendResult(
                false,
                snapshot,
                snapshot.spend(key)
            );
        }

        if(spend.state==
                SpendState.SETTLED)
            throw new IllegalStateException(
                "settled Voting spend cannot cancel "+
                key
            );

        if(state.reserved<
                spend.points)
            throw new IllegalStateException(
                "Voting spend reservation drift "+
                key
            );

        state.reserved=
            Math.subtractExact(
                state.reserved,
                spend.points
            );
        spend.state=
            SpendState.CANCELLED;

        PlayerSnapshot snapshot=
            new PlayerSnapshot(state);

        return new SpendResult(
            true,
            snapshot,
            snapshot.spend(key)
        );
    }

    synchronized PlayerSnapshot get(
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
            :new PlayerSnapshot(state);
    }

    synchronized int playerCount(){
        return players.size();
    }

    private PlayerState requirePlayer(
        String player
    ){
        PlayerState state=
            players.get(player);

        if(state==null)
            throw new IllegalArgumentException(
                "unknown Voting player "+
                player
            );

        return state;
    }

    private static Spend requireSpend(
        PlayerState state,
        String spendKey
    ){
        Spend spend=
            state.spends.get(
                spendKey
            );

        if(spend==null)
            throw new IllegalArgumentException(
                "unknown Voting spend "+
                spendKey+
                " player="+
                state.playerRef
            );

        return spend;
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
