package spk.local;

import java.util.*;

/**
 * Protocol-independent ordinary + Bloodcore Lottery lifecycle.
 *
 * Exact-current client evidence proves two lottery surfaces and winner-history
 * capacities. Entry economics, RNG, draw cadence, prizes and refunds remain
 * external server authority.
 */
final class LotteryService {
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    enum Channel {
        ORDINARY(6),
        BLOODCORE(35);

        private final int historyCapacity;

        Channel(int historyCapacity){
            this.historyCapacity=
                historyCapacity;
        }

        int historyCapacity(){
            return historyCapacity;
        }
    }

    enum RoundState {
        OPEN,
        WINNER_RECORDED,
        SETTLED,
        CANCELLED
    }

    static final class ParticipantSnapshot {
        final String playerRef;
        final long entryUnits;

        ParticipantSnapshot(
            String playerRef,
            long entryUnits
        ){
            this.playerRef=playerRef;
            this.entryUnits=entryUnits;
        }
    }

    static final class WinnerRecord {
        final String roundKey;
        final Channel channel;
        final String winnerRef;
        final String drawReference;
        final String sourceAuthority;

        WinnerRecord(
            Round round
        ){
            this.roundKey=round.roundKey;
            this.channel=round.channel;
            this.winnerRef=round.winnerRef;
            this.drawReference=
                round.drawReference;
            this.sourceAuthority=
                round.sourceAuthority;
        }
    }

    static final class RoundSnapshot {
        final String roundKey;
        final Channel channel;
        final RoundState state;
        final String sourceAuthority;
        final List<ParticipantSnapshot>
            participants;
        final String winnerRef;
        final String drawReference;
        final long totalEntryUnits;
        final String presentationAuthority;

        RoundSnapshot(
            Round round
        ){
            this.roundKey=round.roundKey;
            this.channel=round.channel;
            this.state=round.state;
            this.sourceAuthority=
                round.sourceAuthority;
            this.winnerRef=
                round.winnerRef;
            this.drawReference=
                round.drawReference;
            this.presentationAuthority=
                PRESENTATION_AUTHORITY;

            ArrayList<ParticipantSnapshot>
                participantSnapshots=
                    new ArrayList<>();

            long total=0L;

            for(Map.Entry<String,Long> entry:
                    round.entryUnitsByPlayer
                        .entrySet()){
                participantSnapshots.add(
                    new ParticipantSnapshot(
                        entry.getKey(),
                        entry.getValue()
                    )
                );
                total=
                    Math.addExact(
                        total,
                        entry.getValue()
                    );
            }

            this.participants=
                Collections.unmodifiableList(
                    participantSnapshots
                );
            this.totalEntryUnits=total;
        }

        ParticipantSnapshot participant(
            String playerRef
        ){
            String player=
                normalizePlayer(
                    playerRef
                );

            for(ParticipantSnapshot participant:
                    participants)
                if(participant.playerRef
                        .equals(player))
                    return participant;

            return null;
        }

        boolean terminal(){
            return state==
                    RoundState.SETTLED||
                state==
                    RoundState.CANCELLED;
        }
    }

    static final class EntryResult {
        final boolean changed;
        final RoundSnapshot round;
        final ParticipantSnapshot participant;

        EntryResult(
            boolean changed,
            RoundSnapshot round,
            ParticipantSnapshot participant
        ){
            this.changed=changed;
            this.round=
                Objects.requireNonNull(
                    round,
                    "round"
                );
            this.participant=
                Objects.requireNonNull(
                    participant,
                    "participant"
                );
        }
    }

    static final class FinalizeResult {
        final boolean changed;
        final RoundSnapshot round;
        final WinnerRecord winnerRecord;

        FinalizeResult(
            boolean changed,
            RoundSnapshot round,
            WinnerRecord winnerRecord
        ){
            this.changed=changed;
            this.round=
                Objects.requireNonNull(
                    round,
                    "round"
                );
            this.winnerRecord=
                Objects.requireNonNull(
                    winnerRecord,
                    "winnerRecord"
                );
        }
    }

    private static final class Receipt {
        final String receiptKey;
        final String playerRef;
        final long entryUnits;

        Receipt(
            String receiptKey,
            String playerRef,
            long entryUnits
        ){
            this.receiptKey=receiptKey;
            this.playerRef=playerRef;
            this.entryUnits=entryUnits;
        }
    }

    private static final class Round {
        final String roundKey;
        final Channel channel;
        final String sourceAuthority;

        final LinkedHashMap<String,Long>
            entryUnitsByPlayer=
                new LinkedHashMap<>();

        final LinkedHashMap<String,Receipt>
            receipts=
                new LinkedHashMap<>();

        RoundState state=
            RoundState.OPEN;
        String winnerRef;
        String drawReference;

        Round(
            String roundKey,
            Channel channel,
            String sourceAuthority
        ){
            this.roundKey=roundKey;
            this.channel=channel;
            this.sourceAuthority=
                sourceAuthority;
        }
    }

    private final LinkedHashMap<String,Round>
        rounds=
            new LinkedHashMap<>();

    private final EnumMap<Channel,String>
        activeRoundByChannel=
            new EnumMap<>(
                Channel.class
            );

    private final EnumMap<
        Channel,
        ArrayDeque<WinnerRecord>
    > winnerHistory=
        new EnumMap<>(
            Channel.class
        );

    LotteryService(){
        for(Channel channel:
                Channel.values())
            winnerHistory.put(
                channel,
                new ArrayDeque<
                    WinnerRecord
                >()
            );
    }

    synchronized RoundSnapshot openRound(
        String roundKey,
        Channel channel,
        String sourceAuthority
    ){
        String key=
            normalizeKey(
                roundKey,
                "roundKey"
            );
        Channel checkedChannel=
            Objects.requireNonNull(
                channel,
                "channel"
            );
        String authority=
            requireText(
                sourceAuthority,
                "sourceAuthority"
            );

        if(rounds.containsKey(key))
            throw new IllegalStateException(
                "duplicate Lottery round "+
                key
            );

        String active=
            activeRoundByChannel.get(
                checkedChannel
            );

        if(active!=null)
            throw new IllegalStateException(
                "Lottery channel already has active round "+
                checkedChannel+
                " round="+active
            );

        Round round=
            new Round(
                key,
                checkedChannel,
                authority
            );

        rounds.put(
            key,
            round
        );
        activeRoundByChannel.put(
            checkedChannel,
            key
        );

        return new RoundSnapshot(round);
    }

    /**
     * Call only after external entry/payment settlement succeeds.
     */
    synchronized EntryResult
        confirmEntrySettled(
            String roundKey,
            String playerRef,
            String receiptKey,
            long entryUnits
        ){
        if(entryUnits<=0L)
            throw new IllegalArgumentException(
                "entryUnits="+entryUnits
            );

        Round round=
            requireRound(
                roundKey
            );

        requireState(
            round,
            RoundState.OPEN,
            "confirmEntrySettled"
        );

        String player=
            normalizePlayer(
                playerRef
            );
        String receipt=
            normalizeKey(
                receiptKey,
                "receiptKey"
            );

        Receipt prior=
            round.receipts.get(
                receipt
            );

        if(prior!=null){
            if(!prior.playerRef
                    .equals(player)||
               prior.entryUnits!=
                    entryUnits)
                throw new IllegalStateException(
                    "Lottery receipt replay mismatch "+
                    receipt
                );

            RoundSnapshot snapshot=
                new RoundSnapshot(round);

            return new EntryResult(
                false,
                snapshot,
                snapshot.participant(
                    player
                )
            );
        }

        Long current=
            round.entryUnitsByPlayer
                .get(player);

        long next=
            Math.addExact(
                current==null
                    ?0L
                    :current.longValue(),
                entryUnits
            );

        round.entryUnitsByPlayer.put(
            player,
            Long.valueOf(next)
        );
        round.receipts.put(
            receipt,
            new Receipt(
                receipt,
                player,
                entryUnits
            )
        );

        RoundSnapshot snapshot=
            new RoundSnapshot(round);

        return new EntryResult(
            true,
            snapshot,
            snapshot.participant(
                player
            )
        );
    }

    /**
     * Caller owns RNG/draw/winner policy. This service only records the
     * authoritative result.
     */
    synchronized RoundSnapshot
        recordAuthoritativeWinner(
            String roundKey,
            String playerRef,
            String drawReference
        ){
        Round round=
            requireRound(
                roundKey
            );

        requireState(
            round,
            RoundState.OPEN,
            "recordAuthoritativeWinner"
        );

        String player=
            normalizePlayer(
                playerRef
            );

        Long units=
            round.entryUnitsByPlayer
                .get(player);

        if(units==null||
           units.longValue()<=0L)
            throw new IllegalArgumentException(
                "Lottery winner has no settled entry "+
                player
            );

        round.winnerRef=player;
        round.drawReference=
            requireText(
                drawReference,
                "drawReference"
            );
        round.state=
            RoundState.WINNER_RECORDED;

        return new RoundSnapshot(round);
    }

    /**
     * Call only after external prize settlement succeeds.
     */
    synchronized FinalizeResult
        confirmPrizeSettledAndFinalize(
            String roundKey
        ){
        Round round=
            requireRound(
                roundKey
            );

        if(round.state==
                RoundState.SETTLED){
            WinnerRecord existing=
                findHistory(
                    round.channel,
                    round.roundKey
                );

            if(existing==null)
                throw new IllegalStateException(
                    "settled Lottery round missing history "+
                    round.roundKey
                );

            return new FinalizeResult(
                false,
                new RoundSnapshot(round),
                existing
            );
        }

        if(round.state==
                RoundState.CANCELLED)
            throw new IllegalStateException(
                "cancelled Lottery round cannot finalize "+
                round.roundKey
            );

        requireState(
            round,
            RoundState.WINNER_RECORDED,
            "confirmPrizeSettledAndFinalize"
        );

        round.state=
            RoundState.SETTLED;

        WinnerRecord record=
            new WinnerRecord(round);

        ArrayDeque<WinnerRecord> history=
            winnerHistory.get(
                round.channel
            );

        history.addLast(record);

        while(history.size()>
                round.channel
                    .historyCapacity())
            history.removeFirst();

        activeRoundByChannel.remove(
            round.channel,
            round.roundKey
        );

        return new FinalizeResult(
            true,
            new RoundSnapshot(round),
            record
        );
    }

    synchronized RoundSnapshot cancelRound(
        String roundKey
    ){
        Round round=
            requireRound(
                roundKey
            );

        if(round.state==
                RoundState.CANCELLED)
            return new RoundSnapshot(
                round
            );

        if(round.state!=
                RoundState.OPEN)
            throw new IllegalStateException(
                "Lottery cancel invalid from "+
                round.state+
                " round="+
                round.roundKey
            );

        round.state=
            RoundState.CANCELLED;

        activeRoundByChannel.remove(
            round.channel,
            round.roundKey
        );

        return new RoundSnapshot(round);
    }

    synchronized RoundSnapshot get(
        String roundKey
    ){
        Round round=
            rounds.get(
                normalizeKey(
                    roundKey,
                    "roundKey"
                )
            );

        return round==null
            ?null
            :new RoundSnapshot(round);
    }

    synchronized RoundSnapshot activeRound(
        Channel channel
    ){
        Channel checked=
            Objects.requireNonNull(
                channel,
                "channel"
            );

        String key=
            activeRoundByChannel.get(
                checked
            );

        return key==null
            ?null
            :new RoundSnapshot(
                requireRound(key)
            );
    }

    synchronized List<WinnerRecord> history(
        Channel channel
    ){
        ArrayDeque<WinnerRecord> history=
            winnerHistory.get(
                Objects.requireNonNull(
                    channel,
                    "channel"
                )
            );

        return Collections.unmodifiableList(
            new ArrayList<>(
                history
            )
        );
    }

    synchronized int size(){
        return rounds.size();
    }

    private WinnerRecord findHistory(
        Channel channel,
        String roundKey
    ){
        for(WinnerRecord record:
                winnerHistory.get(channel))
            if(record.roundKey.equals(
                    roundKey))
                return record;

        return null;
    }

    private Round requireRound(
        String roundKey
    ){
        String key=
            normalizeKey(
                roundKey,
                "roundKey"
            );

        Round round=
            rounds.get(key);

        if(round==null)
            throw new IllegalArgumentException(
                "unknown Lottery round "+
                key
            );

        return round;
    }

    private static void requireState(
        Round round,
        RoundState required,
        String operation
    ){
        if(round.state!=required)
            throw new IllegalStateException(
                operation+
                " invalid from "+
                round.state+
                " round="+
                round.roundKey
            );
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
        String clean=
            requireText(
                value,
                field
            ).toLowerCase(
                Locale.ROOT
            );

        for(int i=0;i<clean.length();i++){
            char c=clean.charAt(i);

            boolean ok=
                c>='a'&&c<='z'||
                c>='0'&&c<='9'||
                c=='.'||
                c=='_'||
                c=='-'||
                c==':';

            if(!ok)
                throw new IllegalArgumentException(
                    field+" invalid="+value
                );
        }

        return clean;
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
