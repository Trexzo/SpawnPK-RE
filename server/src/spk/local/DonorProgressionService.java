package spk.local;

import java.util.*;

/**
 * Server-owned donor-promotion progression ledger.
 *
 * Exact-current client evidence distinguishes externally verified payment
 * progress from bond-opening progress, but live thresholds, exchange values,
 * payment verification, perks and rewards remain caller/server authority.
 */
final class DonorProgressionService {
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    enum CreditSource {
        VERIFIED_PAYMENT,
        BOND_OPENED
    }

    static final class TierDefinition {
        final String tierKey;
        final long requiredPromotionUnits;

        TierDefinition(
            String tierKey,
            long requiredPromotionUnits
        ){
            this.tierKey=
                MatchRules.normalizeKey(
                    tierKey,
                    "tierKey"
                );

            if(requiredPromotionUnits<=0L)
                throw new IllegalArgumentException(
                    "requiredPromotionUnits="+
                    requiredPromotionUnits
                );

            this.requiredPromotionUnits=
                requiredPromotionUnits;
        }
    }

    static final class PlayerSnapshot {
        final String playerRef;
        final long lifetimePromotionUnits;
        final String currentTierKey;
        final Long currentTierThreshold;
        final String nextTierKey;
        final Long nextTierThreshold;
        final Long unitsUntilNextTier;
        final Map<CreditSource,Long>
            creditedBySource;
        final String presentationAuthority;

        PlayerSnapshot(
            PlayerState state,
            List<TierDefinition> tiers
        ){
            this.playerRef=
                state.playerRef;
            this.lifetimePromotionUnits=
                state.lifetimePromotionUnits;

            TierDefinition current=null;
            TierDefinition next=null;

            for(TierDefinition tier:tiers){
                if(state.lifetimePromotionUnits>=
                        tier.requiredPromotionUnits)
                    current=tier;
                else{
                    next=tier;
                    break;
                }
            }

            this.currentTierKey=
                current==null
                    ?null
                    :current.tierKey;
            this.currentTierThreshold=
                current==null
                    ?null
                    :Long.valueOf(
                        current
                            .requiredPromotionUnits
                    );
            this.nextTierKey=
                next==null
                    ?null
                    :next.tierKey;
            this.nextTierThreshold=
                next==null
                    ?null
                    :Long.valueOf(
                        next.requiredPromotionUnits
                    );
            this.unitsUntilNextTier=
                next==null
                    ?null
                    :Long.valueOf(
                        next.requiredPromotionUnits-
                        state.lifetimePromotionUnits
                    );

            EnumMap<CreditSource,Long> totals=
                new EnumMap<>(
                    CreditSource.class
                );

            for(CreditSource source:
                    CreditSource.values())
                totals.put(
                    source,
                    state.creditedBySource
                        .getOrDefault(
                            source,
                            0L
                        )
                );

            this.creditedBySource=
                Collections.unmodifiableMap(
                    totals
                );
            this.presentationAuthority=
                PRESENTATION_AUTHORITY;
        }

        boolean promoted(){
            return currentTierKey!=null;
        }

        boolean maxTier(){
            return nextTierKey==null;
        }

        long creditedBy(
            CreditSource source
        ){
            return creditedBySource.get(
                Objects.requireNonNull(
                    source,
                    "source"
                )
            );
        }
    }

    static final class CreditResult {
        final boolean changed;
        final boolean tierChanged;
        final String previousTierKey;
        final PlayerSnapshot player;

        CreditResult(
            boolean changed,
            boolean tierChanged,
            String previousTierKey,
            PlayerSnapshot player
        ){
            this.changed=changed;
            this.tierChanged=tierChanged;
            this.previousTierKey=
                previousTierKey;
            this.player=
                Objects.requireNonNull(
                    player,
                    "player"
                );
        }
    }

    private static final class Receipt {
        final String playerRef;
        final long promotionUnits;
        final String verificationAuthority;

        Receipt(
            String playerRef,
            long promotionUnits,
            String verificationAuthority
        ){
            this.playerRef=playerRef;
            this.promotionUnits=
                promotionUnits;
            this.verificationAuthority=
                verificationAuthority;
        }
    }

    private static final class PlayerState {
        final String playerRef;
        long lifetimePromotionUnits;

        final EnumMap<CreditSource,Long>
            creditedBySource=
                new EnumMap<>(
                    CreditSource.class
                );

        PlayerState(String playerRef){
            this.playerRef=playerRef;
        }
    }

    private final List<TierDefinition> tiers;

    private final LinkedHashMap<String,PlayerState>
        players=
            new LinkedHashMap<>();

    private final HashMap<String,Receipt>
        verifiedReceipts=
            new HashMap<>();

    DonorProgressionService(
        Collection<TierDefinition> tiers
    ){
        Objects.requireNonNull(
            tiers,
            "tiers"
        );

        if(tiers.isEmpty())
            throw new IllegalArgumentException(
                "donor tiers empty"
            );

        ArrayList<TierDefinition> checked=
            new ArrayList<>();

        HashSet<String> keys=
            new HashSet<>();

        long priorThreshold=0L;

        for(TierDefinition tier:tiers){
            TierDefinition value=
                Objects.requireNonNull(
                    tier,
                    "tier"
                );

            if(!keys.add(
                    value.tierKey))
                throw new IllegalArgumentException(
                    "duplicate donor tier "+
                    value.tierKey
                );

            if(value.requiredPromotionUnits<=
                    priorThreshold)
                throw new IllegalArgumentException(
                    "donor tier thresholds must strictly increase "+
                    value.tierKey+
                    " threshold="+
                    value.requiredPromotionUnits+
                    " prior="+
                    priorThreshold
                );

            checked.add(value);
            priorThreshold=
                value.requiredPromotionUnits;
        }

        this.tiers=
            Collections.unmodifiableList(
                checked
            );
    }

    /**
     * Call only after the external payment/bond promotion event has been
     * verified and settled by the authoritative external system.
     */
    synchronized CreditResult confirmPromotionCredit(
        String playerRef,
        CreditSource source,
        String receiptKey,
        long promotionUnits,
        String verificationAuthority
    ){
        if(promotionUnits<=0L)
            throw new IllegalArgumentException(
                "promotionUnits="+
                promotionUnits
            );

        String player=
            normalizePlayer(
                playerRef
            );
        CreditSource checkedSource=
            Objects.requireNonNull(
                source,
                "source"
            );
        String receipt=
            MatchRules.normalizeKey(
                receiptKey,
                "receiptKey"
            );
        String authority=
            MatchRules.requireText(
                verificationAuthority,
                "verificationAuthority"
            );

        String receiptIdentity=
            checkedSource.name()+
                ":"+
                receipt;

        Receipt prior=
            verifiedReceipts.get(
                receiptIdentity
            );

        if(prior!=null){
            if(!prior.playerRef
                    .equals(player)||
               prior.promotionUnits!=
                    promotionUnits||
               !prior.verificationAuthority
                    .equals(authority))
                throw new IllegalStateException(
                    "donor promotion receipt replay mismatch "+
                    receiptIdentity
                );

            PlayerSnapshot snapshot=
                new PlayerSnapshot(
                    requirePlayer(player),
                    tiers
                );

            return new CreditResult(
                false,
                false,
                snapshot.currentTierKey,
                snapshot
            );
        }

        PlayerState state=
            players.computeIfAbsent(
                player,
                PlayerState::new
            );

        PlayerSnapshot before=
            new PlayerSnapshot(
                state,
                tiers
            );

        long nextLifetime=
            Math.addExact(
                state.lifetimePromotionUnits,
                promotionUnits
            );
        long sourceCurrent=
            state.creditedBySource
                .getOrDefault(
                    checkedSource,
                    0L
                );
        long sourceNext=
            Math.addExact(
                sourceCurrent,
                promotionUnits
            );

        state.lifetimePromotionUnits=
            nextLifetime;
        state.creditedBySource.put(
            checkedSource,
            sourceNext
        );

        verifiedReceipts.put(
            receiptIdentity,
            new Receipt(
                player,
                promotionUnits,
                authority
            )
        );

        PlayerSnapshot after=
            new PlayerSnapshot(
                state,
                tiers
            );

        return new CreditResult(
            true,
            !Objects.equals(
                before.currentTierKey,
                after.currentTierKey
            ),
            before.currentTierKey,
            after
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
            :new PlayerSnapshot(
                state,
                tiers
            );
    }

    synchronized List<TierDefinition> tiers(){
        return tiers;
    }

    synchronized int playerCount(){
        return players.size();
    }

    private PlayerState requirePlayer(
        String playerRef
    ){
        PlayerState state=
            players.get(playerRef);

        if(state==null)
            throw new IllegalArgumentException(
                "unknown donor player "+
                playerRef
            );

        return state;
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
}
