package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Transactional victim-side container refresh for settled PvP deaths.
 *
 * Packet bytes are staged through the caller-owned ServerPacketWriter batch.
 * Runtime debt is cleared only after the outer session confirms that batch
 * committed.
 */
final class PvpDeathPresentationService {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB";

    private final PvpDeathSettlementRuntime runtime;
    private final WorldPlayer player;
    private final EquipmentState equipment;
    private PvpDeathSettlementRuntime.PresentationDebt staged;

    PvpDeathPresentationService(
        PvpDeathSettlementRuntime runtime,
        WorldPlayer player,
        EquipmentState equipment
    ){
        this.runtime=
            Objects.requireNonNull(
                runtime,
                "runtime"
            );
        this.player=
            Objects.requireNonNull(
                player,
                "player"
            );
        this.equipment=
            Objects.requireNonNull(
                equipment,
                "equipment"
            );
    }

    PvpDeathSettlementRuntime.PresentationDebt stage(
        ServerPacketWriter writer
    )throws IOException{
        Objects.requireNonNull(
            writer,
            "writer"
        );

        if(staged!=null)
            throw new IllegalStateException(
                "PvP death presentation attempt already staged sequence="+
                staged.deathSequence
            );

        PvpDeathSettlementRuntime.PresentationDebt debt=
            runtime.presentationDebt(
                player
            );

        if(debt==null)
            return null;

        if(debt.victim!=player||
           debt.victimGeneration!=
                player.generation())
            throw new IllegalStateException(
                "stale PvP death presentation ownership player="+
                player.id()
            );

        /*
         * Both authoritative postimages are written into the caller's existing
         * world-tick packet batch. If either write fails, staged remains null
         * and the runtime debt is untouched.
         */
        player.bank().sendNormalInventory(
            writer
        );
        player.bank().sendEquipment(
            writer,
            equipment
        );

        staged=debt;
        return debt;
    }

    void commitStaged(){
        PvpDeathSettlementRuntime.PresentationDebt debt=
            staged;

        if(debt==null)
            return;

        runtime.markPresentationCommitted(
            player,
            debt.victimGeneration,
            debt.deathSequence
        );

        staged=null;
    }

    void abortStaged(){
        staged=null;
    }

    boolean staged(){
        return staged!=null;
    }

    long stagedDeathSequence(){
        return staged==null
            ?-1L
            :staged.deathSequence;
    }
}
