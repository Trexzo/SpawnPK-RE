package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * World-side exactly-once PvP death settlement.
 *
 * Prepare resolves policy/attribution and freezes the ground batch without
 * mutating world state. Commit runs only after the enclosing session/world-tick
 * packet batch has succeeded.
 */
final class PlayerDeathWorldSettlementService {
    static final class GroundLine {
        final long groundItemId;
        final int itemId;
        final int addedAmount;
        final int totalAmount;
        final Tile tile;
        final String owner;
        final boolean created;

        private GroundLine(
            GroundItemRegistry.BatchMutation mutation
        ){
            this.groundItemId=mutation.groundItemId;
            this.itemId=mutation.itemId;
            this.addedAmount=mutation.addedAmount;
            this.totalAmount=mutation.newAmount;
            this.tile=mutation.tile;
            this.owner=mutation.owner;
            this.created=mutation.created();
        }
    }

    static final class Receipt {
        final EntityId victimId;
        final long victimGeneration;
        final EntityId attackerId;
        final long attackerGeneration;
        final long deathTick;
        final long deathSequence;
        final Tile deathTile;
        final String carriedPolicyAuthority;
        final String groundPolicyAuthority;
        final List<GroundLine> ground;

        private Receipt(
            PlayerDeathAttributionRegistry.Attribution attribution,
            PlayerDeathCarriedSettlementService.Receipt carried,
            String groundPolicyAuthority,
            List<GroundItemRegistry.BatchMutation> mutations
        ){
            this.victimId=attribution.victimId;
            this.victimGeneration=attribution.victimGeneration;
            this.attackerId=attribution.attackerId;
            this.attackerGeneration=attribution.attackerGeneration;
            this.deathTick=carried.deathTick;
            this.deathSequence=carried.deathSequence;
            this.deathTile=attribution.deathTile;
            this.carriedPolicyAuthority=carried.policyAuthority;
            this.groundPolicyAuthority=groundPolicyAuthority;

            ArrayList<GroundLine> rows=new ArrayList<>();
            for(GroundItemRegistry.BatchMutation mutation:mutations)
                rows.add(new GroundLine(mutation));

            this.ground=Collections.unmodifiableList(rows);
        }
    }

    static final class Prepared {
        final PlayerDeathCarriedSettlementService.Prepared carried;
        final PlayerDeathAttributionRegistry.Attribution attribution;
        final String owner;
        final boolean devOwned;
        final List<GroundItemRegistry.AddRequest> requests;
        final Receipt replayReceipt;

        private Prepared(Receipt replayReceipt){
            this.carried=null;
            this.attribution=null;
            this.owner=null;
            this.devOwned=false;
            this.requests=Collections.emptyList();
            this.replayReceipt=replayReceipt;
        }

        private Prepared(
            PlayerDeathCarriedSettlementService.Prepared carried,
            PlayerDeathAttributionRegistry.Attribution attribution,
            String owner,
            boolean devOwned,
            List<GroundItemRegistry.AddRequest> requests
        ){
            this.carried=carried;
            this.attribution=attribution;
            this.owner=owner;
            this.devOwned=devOwned;
            this.requests=
                Collections.unmodifiableList(
                    new ArrayList<>(requests)
                );
            this.replayReceipt=null;
        }

        boolean replay(){
            return replayReceipt!=null;
        }

        long deathSequence(){
            return replay()
                ?replayReceipt.deathSequence
                :carried.receipt.deathSequence;
        }
    }

    private final World world;
    private final WorldPlayer victim;
    private final PlayerDeathCarriedSettlementService carried;
    private final PlayerDeathGroundDropPolicy groundPolicy;
    private final String groundPolicyAuthority;
    private final LinkedHashMap<Long,Receipt> receipts=
        new LinkedHashMap<>();

    PlayerDeathWorldSettlementService(
        World world,
        WorldPlayer victim,
        PlayerDeathDispositionPolicy dispositionPolicy,
        PlayerDeathGroundDropPolicy groundPolicy
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.victim=Objects.requireNonNull(victim,"victim");
        this.carried=
            new PlayerDeathCarriedSettlementService(
                victim,
                Objects.requireNonNull(
                    dispositionPolicy,
                    "dispositionPolicy"
                )
            );
        this.groundPolicy=
            Objects.requireNonNull(
                groundPolicy,
                "groundPolicy"
            );
        this.groundPolicyAuthority=
            requireGameplayAuthority(
                groundPolicy.authority()
            );
    }

    synchronized Prepared prepareCurrentPvpDeath(){
        PlayerDeathCarriedSettlementService.Prepared carriedPrepared=
            carried.prepareCurrentDeath();

        Receipt existing=
            receipts.get(
                carriedPrepared.receipt.deathSequence
            );
        if(existing!=null)
            return new Prepared(existing);

        if(carriedPrepared.replay)
            throw new IllegalStateException(
                "carried death was already committed without world receipt victim="+
                victim.id()+
                " deathSequence="+
                carriedPrepared.receipt.deathSequence
            );

        PlayerDeathAttributionRegistry.Attribution attribution=
            world.playerDeathAttributions().get(
                victim.id(),
                carriedPrepared.receipt.deathSequence
            );

        requireAttribution(
            attribution,
            carriedPrepared.receipt
        );

        String owner=
            normalizeOwner(
                groundPolicy.ownerRef(
                    attribution,
                    carriedPrepared.receipt
                )
            );
        boolean devOwned=
            groundPolicy.devOwned();

        ArrayList<GroundItemRegistry.AddRequest> requests=
            new ArrayList<>();

        for(PlayerDeathCarriedSettlementService.LostLine lost:
                carriedPrepared.receipt.lost)
            requests.add(
                new GroundItemRegistry.AddRequest(
                    lost.itemId,
                    lost.amount,
                    attribution.deathTile,
                    owner,
                    carriedPrepared.receipt.deathTick,
                    devOwned
                )
            );

        return new Prepared(
            carriedPrepared,
            attribution,
            owner,
            devOwned,
            requests
        );
    }

    synchronized Receipt commitPrepared(
        Prepared prepared
    ){
        Prepared checked=
            Objects.requireNonNull(
                prepared,
                "prepared"
            );

        Receipt existing=
            receipts.get(
                checked.deathSequence()
            );
        if(existing!=null)
            return existing;

        if(checked.replay())
            return checked.replayReceipt;

        @SuppressWarnings("unchecked")
        final List<GroundItemRegistry.BatchMutation>[] committed=
            new List[]{null};

        PlayerDeathCarriedSettlementService.Receipt carriedReceipt=
            carried.commitPreparedAfterValidation(
                checked.carried,
                ()->committed[0]=
                    world.groundItems()
                        .addBatchDetailed(
                            checked.requests
                        )
            );

        List<GroundItemRegistry.BatchMutation> mutations=
            committed[0];

        if(mutations==null)
            throw new IllegalStateException(
                "ground settlement callback did not execute victim="+
                victim.id()+
                " deathSequence="+
                carriedReceipt.deathSequence
            );

        Receipt receipt=
            new Receipt(
                checked.attribution,
                carriedReceipt,
                groundPolicyAuthority,
                mutations
            );

        receipts.put(
            carriedReceipt.deathSequence,
            receipt
        );

        publishLiveOwnerScene(
            checked.attribution,
            mutations
        );

        return receipt;
    }

    synchronized Receipt settleCurrentPvpDeath(){
        return commitPrepared(
            prepareCurrentPvpDeath()
        );
    }

    synchronized Receipt get(
        long deathSequence
    ){
        return receipts.get(deathSequence);
    }

    synchronized int size(){
        return receipts.size();
    }

    private void requireAttribution(
        PlayerDeathAttributionRegistry.Attribution attribution,
        PlayerDeathCarriedSettlementService.Receipt carriedReceipt
    ){
        if(attribution==null)
            throw new IllegalStateException(
                "missing typed PvP death attribution victim="+
                victim.id()+
                " deathSequence="+
                carriedReceipt.deathSequence
            );

        if(!attribution.victimId.equals(victim.id())||
           attribution.victimGeneration!=victim.generation()||
           attribution.deathTick!=carriedReceipt.deathTick||
           attribution.deathSequence!=carriedReceipt.deathSequence)
            throw new IllegalStateException(
                "PvP death attribution identity mismatch victim="+
                victim.id()+
                " deathSequence="+
                carriedReceipt.deathSequence
            );
    }

    private void publishLiveOwnerScene(
        PlayerDeathAttributionRegistry.Attribution attribution,
        List<GroundItemRegistry.BatchMutation> mutations
    ){
        WorldPlayer recipient=
            world.players().byName(
                attribution.attackerRef
            );

        if(recipient==null||
           !recipient.id().equals(
                attribution.attackerId)||
           recipient.generation()!=
                attribution.attackerGeneration||
           !world.players().owns(
                recipient,
                attribution.attackerGeneration))
            return;

        long now=System.currentTimeMillis();

        for(GroundItemRegistry.BatchMutation mutation:mutations)
            if(mutation.created())
                world.groundItemPresentationEvents()
                    .enqueueSpawn(
                        now,
                        mutation,
                        recipient,
                        attribution.attackerGeneration
                    );
            else
                world.groundItemPresentationEvents()
                    .enqueueAmount(
                        now,
                        mutation,
                        recipient,
                        attribution.attackerGeneration
                    );
    }

    private static String normalizeOwner(
        String value
    ){
        if(value==null)
            return null;

        String clean=value.trim();
        return clean.isEmpty()?null:clean;
    }

    private static String requireGameplayAuthority(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "ground policy authority"
            );

        String clean=value.trim();
        if(clean.isEmpty())
            throw new IllegalArgumentException(
                "ground policy authority blank"
            );

        if("EXACT_CURRENT_CLIENT".equals(clean)||
           "UNKNOWN_SERVER_AUTHORITY".equals(clean))
            throw new IllegalArgumentException(
                "client/unknown authority cannot define death ground policy actual="+
                clean
            );

        return clean;
    }
}
