package spk.local;

import java.util.*;

/**
 * Server-wide Well of Good Will contribution campaign.
 *
 * Exact-current client evidence proves contribution/progress/reward presentation,
 * but actual contribution assets, goal, reward formula, duration, reset cadence
 * and persistence remain caller/server authority.
 */
final class GoodwillWellService {
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    enum State {
        OPEN,
        FUNDED,
        REWARD_CONFIRMED,
        CANCELLED
    }

    static final class ParticipantSnapshot {
        final String playerRef;
        final long contributed;

        ParticipantSnapshot(
            String playerRef,
            long contributed
        ){
            this.playerRef=playerRef;
            this.contributed=contributed;
        }
    }

    static final class Snapshot {
        final String campaignKey;
        final long goal;
        final long progress;
        final long rawContributed;
        final State state;
        final String sourceAuthority;
        final String rewardActivationReference;
        final List<ParticipantSnapshot> participants;
        final String presentationAuthority;

        Snapshot(Campaign campaign){
            this.campaignKey=
                campaign.campaignKey;
            this.goal=campaign.goal;
            this.rawContributed=
                campaign.rawContributed;
            this.progress=
                Math.min(
                    campaign.rawContributed,
                    campaign.goal
                );
            this.state=campaign.state;
            this.sourceAuthority=
                campaign.sourceAuthority;
            this.rewardActivationReference=
                campaign.rewardActivationReference;
            this.presentationAuthority=
                PRESENTATION_AUTHORITY;

            ArrayList<ParticipantSnapshot> out=
                new ArrayList<>();

            for(Map.Entry<String,Long> entry:
                    campaign.contributedByPlayer
                        .entrySet())
                out.add(
                    new ParticipantSnapshot(
                        entry.getKey(),
                        entry.getValue()
                    )
                );

            out.sort(
                Comparator.comparing(
                    value->value.playerRef
                )
            );

            this.participants=
                Collections.unmodifiableList(
                    out
                );
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
                    State.REWARD_CONFIRMED||
                state==
                    State.CANCELLED;
        }
    }

    static final class ContributionResult {
        final boolean changed;
        final boolean fundedNow;
        final Snapshot campaign;
        final ParticipantSnapshot participant;

        ContributionResult(
            boolean changed,
            boolean fundedNow,
            Snapshot campaign,
            ParticipantSnapshot participant
        ){
            this.changed=changed;
            this.fundedNow=fundedNow;
            this.campaign=
                Objects.requireNonNull(
                    campaign,
                    "campaign"
                );
            this.participant=
                Objects.requireNonNull(
                    participant,
                    "participant"
                );
        }
    }

    static final class RewardConfirmationResult {
        final boolean changed;
        final Snapshot campaign;

        RewardConfirmationResult(
            boolean changed,
            Snapshot campaign
        ){
            this.changed=changed;
            this.campaign=
                Objects.requireNonNull(
                    campaign,
                    "campaign"
                );
        }
    }

    private static final class Receipt {
        final String playerRef;
        final long amount;

        Receipt(
            String playerRef,
            long amount
        ){
            this.playerRef=playerRef;
            this.amount=amount;
        }
    }

    private static final class Campaign {
        final String campaignKey;
        final long goal;
        final String sourceAuthority;

        final LinkedHashMap<String,Long>
            contributedByPlayer=
                new LinkedHashMap<>();

        final HashMap<String,Receipt>
            receipts=
                new HashMap<>();

        long rawContributed;
        State state=State.OPEN;
        String rewardActivationReference;

        Campaign(
            String campaignKey,
            long goal,
            String sourceAuthority
        ){
            this.campaignKey=
                campaignKey;
            this.goal=goal;
            this.sourceAuthority=
                sourceAuthority;
        }
    }

    private final LinkedHashMap<String,Campaign>
        campaigns=
            new LinkedHashMap<>();

    private String currentCampaignKey;

    synchronized Snapshot openCampaign(
        String campaignKey,
        long goal,
        String sourceAuthority
    ){
        String key=
            normalizeKey(
                campaignKey,
                "campaignKey"
            );

        if(goal<=0L)
            throw new IllegalArgumentException(
                "goal="+goal
            );

        String authority=
            requireText(
                sourceAuthority,
                "sourceAuthority"
            );

        if(campaigns.containsKey(key))
            throw new IllegalStateException(
                "duplicate Goodwill campaign "+
                key
            );

        if(currentCampaignKey!=null){
            Campaign current=
                requireCampaign(
                    currentCampaignKey
                );

            if(!new Snapshot(current)
                    .terminal())
                throw new IllegalStateException(
                    "Goodwill campaign already current "+
                    currentCampaignKey
                );
        }

        Campaign campaign=
            new Campaign(
                key,
                goal,
                authority
            );

        campaigns.put(
            key,
            campaign
        );
        currentCampaignKey=key;

        return new Snapshot(campaign);
    }

    /**
     * Call only after the external contribution asset/currency settlement
     * succeeded.
     */
    synchronized ContributionResult
        confirmContributionSettled(
            String campaignKey,
            String playerRef,
            String receiptKey,
            long amount
        ){
        if(amount<=0L)
            throw new IllegalArgumentException(
                "amount="+amount
            );

        Campaign campaign=
            requireCampaign(
                campaignKey
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
            campaign.receipts.get(
                receipt
            );

        if(prior!=null){
            if(!prior.playerRef
                    .equals(player)||
               prior.amount!=amount)
                throw new IllegalStateException(
                    "Goodwill receipt replay mismatch "+
                    receipt
                );

            Snapshot snapshot=
                new Snapshot(campaign);

            return new ContributionResult(
                false,
                false,
                snapshot,
                snapshot.participant(
                    player
                )
            );
        }

        if(campaign.state!=State.OPEN)
            throw new IllegalStateException(
                "Goodwill contribution invalid from "+
                campaign.state+
                " campaign="+
                campaign.campaignKey
            );

        long playerCurrent=
            campaign.contributedByPlayer
                .getOrDefault(
                    player,
                    0L
                );

        long playerNext=
            Math.addExact(
                playerCurrent,
                amount
            );

        long totalNext=
            Math.addExact(
                campaign.rawContributed,
                amount
            );

        campaign.contributedByPlayer.put(
            player,
            playerNext
        );
        campaign.rawContributed=
            totalNext;
        campaign.receipts.put(
            receipt,
            new Receipt(
                player,
                amount
            )
        );

        boolean fundedNow=false;

        if(campaign.rawContributed>=
                campaign.goal){
            campaign.state=State.FUNDED;
            fundedNow=true;
        }

        Snapshot snapshot=
            new Snapshot(campaign);

        return new ContributionResult(
            true,
            fundedNow,
            snapshot,
            snapshot.participant(
                player
            )
        );
    }

    /**
     * Call only after the external server-wide reward/effect activation
     * succeeded.
     */
    synchronized RewardConfirmationResult
        confirmServerRewardActivated(
            String campaignKey,
            String activationReference
        ){
        Campaign campaign=
            requireCampaign(
                campaignKey
            );

        String reference=
            requireText(
                activationReference,
                "activationReference"
            );

        if(campaign.state==
                State.REWARD_CONFIRMED){
            if(!reference.equals(
                    campaign
                        .rewardActivationReference))
                throw new IllegalStateException(
                    "Goodwill reward activation replay mismatch "+
                    campaign.campaignKey
                );

            return new RewardConfirmationResult(
                false,
                new Snapshot(campaign)
            );
        }

        if(campaign.state!=
                State.FUNDED)
            throw new IllegalStateException(
                "Goodwill reward activation invalid from "+
                campaign.state+
                " campaign="+
                campaign.campaignKey
            );

        campaign.rewardActivationReference=
            reference;
        campaign.state=
            State.REWARD_CONFIRMED;

        if(campaign.campaignKey.equals(
                currentCampaignKey))
            currentCampaignKey=null;

        return new RewardConfirmationResult(
            true,
            new Snapshot(campaign)
        );
    }

    synchronized Snapshot cancelCampaign(
        String campaignKey
    ){
        Campaign campaign=
            requireCampaign(
                campaignKey
            );

        if(campaign.state==
                State.CANCELLED)
            return new Snapshot(campaign);

        if(campaign.state!=State.OPEN)
            throw new IllegalStateException(
                "Goodwill cancel invalid from "+
                campaign.state+
                " campaign="+
                campaign.campaignKey
            );

        campaign.state=
            State.CANCELLED;

        if(campaign.campaignKey.equals(
                currentCampaignKey))
            currentCampaignKey=null;

        return new Snapshot(campaign);
    }

    synchronized Snapshot get(
        String campaignKey
    ){
        Campaign campaign=
            campaigns.get(
                normalizeKey(
                    campaignKey,
                    "campaignKey"
                )
            );

        return campaign==null
            ?null
            :new Snapshot(campaign);
    }

    synchronized Snapshot current(){
        if(currentCampaignKey==null)
            return null;

        return new Snapshot(
            requireCampaign(
                currentCampaignKey
            )
        );
    }

    synchronized int size(){
        return campaigns.size();
    }

    private Campaign requireCampaign(
        String campaignKey
    ){
        String key=
            normalizeKey(
                campaignKey,
                "campaignKey"
            );

        Campaign campaign=
            campaigns.get(key);

        if(campaign==null)
            throw new IllegalArgumentException(
                "unknown Goodwill campaign "+
                key
            );

        return campaign;
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
