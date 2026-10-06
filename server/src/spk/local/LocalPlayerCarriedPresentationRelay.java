package spk.local;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Victim-session adapter for immutable carried-state presentation events.
 * Bytes are staged in the caller-owned LocalSession world-tick batch; event
 * delivery is committed only after that outer packet batch commits.
 */
final class LocalPlayerCarriedPresentationRelay {
    interface AppearancePublisher {
        void publish(
            int[] appearanceItems,
            ServerPacketWriter writer
        )throws IOException;
    }

    private static final class PendingAck {
        final long sequence;
        final EntityId recipientId;
        final long generation;

        PendingAck(
            long sequence,
            EntityId recipientId,
            long generation
        ){
            this.sequence=sequence;
            this.recipientId=recipientId;
            this.generation=generation;
        }
    }

    private final World world;
    private final WorldPlayer viewer;
    private final ArrayList<PendingAck> stagedAcks=
        new ArrayList<>();
    private final HashSet<Long> stagedSequences=
        new HashSet<>();

    LocalPlayerCarriedPresentationRelay(
        World world,
        WorldPlayer viewer
    ){
        this.world=Objects.requireNonNull(
            world,
            "world"
        );
        this.viewer=Objects.requireNonNull(
            viewer,
            "viewer"
        );
    }

    int publishPending(
        long now,
        ServerPacketWriter writer,
        AppearancePublisher appearance
    )throws IOException{
        Objects.requireNonNull(
            writer,
            "writer"
        );
        Objects.requireNonNull(
            appearance,
            "appearance"
        );

        long generation=
            viewer.generation();

        if(!world.players().owns(
                viewer,
                generation
            ))
            return 0;

        List<WorldPlayerCarriedPresentationEvents.Event>
            pending=
                world.playerCarriedPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        generation,
                        now
                    );

        int published=0;

        for(WorldPlayerCarriedPresentationEvents.Event event:
                pending){
            if(stagedSequences.contains(
                    event.sequence))
                continue;

            writer.varShort(
                53,
                BootstrapPackets.itemContainer53(
                    BankState.NORMAL_INVENTORY_CONTAINER,
                    event.inventoryItems,
                    event.inventoryQuantities
                )
            );

            writer.varShort(
                53,
                BootstrapPackets.equipmentContainer53(
                    event.equipmentItems,
                    event.equipmentQuantities
                )
            );

            appearance.publish(
                event.appearanceItems.clone(),
                writer
            );

            if(!stagedSequences.add(
                    event.sequence))
                throw new IllegalStateException(
                    "carried presentation already staged sequence="+
                    event.sequence
                );

            stagedAcks.add(
                new PendingAck(
                    event.sequence,
                    viewer.id(),
                    generation
                )
            );

            published++;
        }

        return published;
    }

    int commitStaged(
        long now
    ){
        int committed=0;

        try{
            for(PendingAck ack:stagedAcks){
                if(!world.players().owns(
                        viewer,
                        ack.generation))
                    throw new IllegalStateException(
                        "carried presentation generation changed before commit player="+
                        ack.recipientId+
                        " expectedGeneration="+
                        ack.generation
                    );

                if(!world.playerCarriedPresentationEvents()
                        .markDelivered(
                            ack.sequence,
                            ack.recipientId,
                            ack.generation,
                            now
                        ))
                    throw new IllegalStateException(
                        "carried presentation identity changed before commit sequence="+
                        ack.sequence
                    );

                committed++;
            }

            return committed;
        }finally{
            stagedAcks.clear();
            stagedSequences.clear();
        }
    }

    int abortStaged(){
        int aborted=stagedAcks.size();
        stagedAcks.clear();
        stagedSequences.clear();
        return aborted;
    }

    int stagedCount(){
        return stagedAcks.size();
    }
}
