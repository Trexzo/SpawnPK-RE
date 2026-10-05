package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * World-side exactly-once PvP death settlement.
 *
 * Order:
 * 1. prepare/validate the victim carried postimage;
 * 2. resolve typed killer attribution and immutable death tile;
 * 3. while the victim preimage remains locked, commit the ground-item batch;
 * 4. apply the already-built carried postimage;
 * 5. retain one receipt for the deathSequence.
 *
 * No client packet value participates in keep/drop economics or ownership.
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

    synchronized Receipt settleCurrentPvpDeath(){
        PlayerDeathCarriedSettlementService.Prepared prepared=
            carried.prepareCurrentDeath();

        Receipt existing=
            receipts.get(
                prepared.receipt.deathSequence
            );
        if(existing!=null)
            return existing;

        if(prepared.replay)
            throw new IllegalStateException(
                "carried death was already committed without world receipt victim="+
                victim.id()+
                " deathSequence="+
                prepared.receipt.deathSequence
            );

        PlayerDeathAttributionRegistry.Attribution attribution=
            world.playerDeathAttributions().get(
                victim.id(),
                prepared.receipt.deathSequence
            );

        requireAttribution(
            attribution,
            prepared.receipt
        );

        String owner=
            normalizeOwner(
                groundPolicy.ownerRef(
                    attribution,
                    prepared.receipt
                )
            );
        boolean devOwned=
            groundPolicy.devOwned();

        ArrayList<GroundItemRegistry.AddRequest> requests=
            new ArrayList<>();

        for(PlayerDeathCarriedSettlementService.LostLine lost:
                prepared.receipt.lost)
            requests.add(
                new GroundItemRegistry.AddRequest(
                    lost.itemId,
                    lost.amount,
                    attribution.deathTile,
                    owner,
                    prepared.receipt.deathTick,
                    devOwned
                )
            );

        @SuppressWarnings("unchecked")
        final List<GroundItemRegistry.BatchMutation>[] committed=
            new List[]{null};

        PlayerDeathCarriedSettlementService.Receipt carriedReceipt=
            carried.commitPreparedAfterValidation(
                prepared,
                ()->committed[0]=
                    world.groundItems()
                        .addBatchDetailed(
                            requests
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
                attribution,
                carriedReceipt,
                groundPolicyAuthority,
                mutations
            );

        receipts.put(
            carriedReceipt.deathSequence,
            receipt
        );

        publishLiveOwnerScene(
            attribution,
            mutations
        );

        return receipt;
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
