package spk.local;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Per-session adapter from World ground-item presentation events to exact scene
 * packets. Canonical world state remains in GroundItemRegistry.
 */
final class LocalGroundItemPresentationRelay {
    private final World world;
    private final WorldPlayer viewer;
    private final MovementState movement;

    private static final class PendingDeliveryAck {
        final long sequence;
        final EntityId recipientId;
        final long recipientGeneration;

        PendingDeliveryAck(
            long sequence,
            EntityId recipientId,
            long recipientGeneration
        ){
            this.sequence=sequence;
            this.recipientId=Objects.requireNonNull(
                recipientId,
                "recipientId"
            );
            this.recipientGeneration=
                recipientGeneration;
        }
    }

    /*
     * These acknowledgements correspond to bytes staged in the caller-owned
     * LocalSession world-tick packet batch. They are not canonical delivery
     * authority until that outer batch commits.
     */
    private final ArrayList<PendingDeliveryAck>
        stagedDeliveryAcks=
            new ArrayList<>();
    private final HashSet<Long>
        stagedSequences=
            new HashSet<>();

    LocalGroundItemPresentationRelay(
        World world,
        WorldPlayer viewer,
        MovementState movement
    ){
        this.world=Objects.requireNonNull(
            world,
            "world"
        );
        this.viewer=Objects.requireNonNull(
            viewer,
            "viewer"
        );
        this.movement=Objects.requireNonNull(
            movement,
            "movement"
        );
    }

    int publishPending(
        long now,
        SceneUpdatePublisher publisher
    )throws IOException{
        Objects.requireNonNull(
            publisher,
            "publisher"
        );

        long generation=
            viewer.generation();

        if(!world.players().owns(
                viewer,
                generation
            ))
            return 0;

        List<WorldGroundItemPresentationEvents.Event>
            pending=
                world.groundItemPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        generation,
                        now
                    );

        int published=0;

        for(WorldGroundItemPresentationEvents.Event event:
                pending){
            if(stagedSequences.contains(
                    event.sequence))
                continue;

            if(event.tile.plane!=
                    movement.plane()||
               !movement.insideCurrentLoadedRegion(
                    event.tile.x,
                    event.tile.y
                )){
                world.groundItemPresentationEvents()
                    .markDelivered(
                        event.sequence,
                        viewer.id(),
                        generation,
                        now
                    );
                continue;
            }

            if(event.kind==
                    WorldGroundItemPresentationEvents.Kind.SPAWN)
                publisher.groundSpawn(
                    event.itemId,
                    event.newAmount,
                    event.tile
                );
            else if(event.kind==
                    WorldGroundItemPresentationEvents.Kind.AMOUNT)
                publisher.groundAmount(
                    event.itemId,
                    event.oldAmount,
                    event.newAmount,
                    event.tile
                );
            else if(event.kind==
                    WorldGroundItemPresentationEvents.Kind.REMOVE)
                publisher.groundRemove(
                    event.itemId,
                    event.tile
                );
            else
                throw new IllegalStateException(
                    "unsupported ground presentation kind "+
                    event.kind
                );

            if(!stagedSequences.add(
                    event.sequence))
                throw new IllegalStateException(
                    "ground presentation event already staged sequence="+
                    event.sequence
                );

            stagedDeliveryAcks.add(
                new PendingDeliveryAck(
                    event.sequence,
                    viewer.id(),
                    generation
                )
            );

            published++;
        }

        return published;
    }

    int commitStagedDeliveries(
        long now
    ){
        int committed=0;

        try{
            for(PendingDeliveryAck ack:
                    stagedDeliveryAcks){
                if(!world.players().owns(
                        viewer,
                        ack.recipientGeneration))
                    throw new IllegalStateException(
                        "ground presentation viewer generation changed before batch commit viewer="+
                        ack.recipientId+
                        " expectedGeneration="+
                        ack.recipientGeneration
                    );

                if(!world.groundItemPresentationEvents()
                        .markDelivered(
                            ack.sequence,
                            ack.recipientId,
                            ack.recipientGeneration,
                            now
                        ))
                    throw new IllegalStateException(
                        "ground presentation delivery identity changed before batch commit sequence="+
                        ack.sequence
                    );

                committed++;
            }

            return committed;
        }finally{
            stagedDeliveryAcks.clear();
            stagedSequences.clear();
        }
    }

    int abortStagedDeliveries(){
        int aborted=
            stagedDeliveryAcks.size();

        stagedDeliveryAcks.clear();
        stagedSequences.clear();
        return aborted;
    }

    int stagedDeliveryCount(){
        return stagedDeliveryAcks.size();
    }

    int publishPendingIfSceneReady(
        long now,
        SceneUpdatePublisher publisher,
        boolean regionLoadPending
    )throws IOException{
        if(regionLoadPending)
            return 0;

        return publishPending(
            now,
            publisher
        );
    }

    int consumeSnapshotCoveredAfterSnapshot(
        long now,
        boolean groundSnapshotPublished
    ){
        if(!groundSnapshotPublished)
            return 0;

        return consumeSnapshotCovered(
            now
        );
    }

    /*
     * The method name is retained for the existing region-load call site, but
     * snapshot-covered events are only staged here. The enclosing LocalSession
     * world-tick batch owns the actual delivery commit through
     * commitStagedDeliveries(...).
     */
    int consumeSnapshotCovered(
        long now
    ){
        long generation=
            viewer.generation();

        if(!world.players().owns(
                viewer,
                generation
            ))
            return 0;

        List<WorldGroundItemPresentationEvents.Event>
            pending=
                world.groundItemPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        generation,
                        now
                    );

        int staged=0;

        for(WorldGroundItemPresentationEvents.Event event:
                pending){
            if(stagedSequences.contains(
                    event.sequence))
                continue;

            if(event.tile.plane!=
                    movement.plane()||
               !movement.insideCurrentLoadedRegion(
                    event.tile.x,
                    event.tile.y
                ))
                continue;

            if(!stagedSequences.add(
                    event.sequence))
                throw new IllegalStateException(
                    "ground snapshot event already staged sequence="+
                    event.sequence
                );

            stagedDeliveryAcks.add(
                new PendingDeliveryAck(
                    event.sequence,
                    viewer.id(),
                    generation
                )
            );

            staged++;
        }

        return staged;
    }
}
