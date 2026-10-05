package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Authoritative playability bridge from versioned semantic loadouts to one
 * live player's canonical inventory/equipment containers.
 *
 * Lock order is LoadoutService -> WorldPlayer mutation lock. No other runtime
 * path currently takes the inverse order because the semantic loadout registry
 * is otherwise detached from live player state.
 */
final class PlayabilityLoadoutApplyService {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_PLAYABILITY";

    static final class Result {
        final PlayerLoadoutId loadoutId;
        final PlayerLoadoutVersion version;
        final int inventorySlots;
        final int equipmentSlots;
        final boolean newlyAcknowledged;
        final String authority;

        Result(
            PlayerLoadout loadout,
            PlayabilityLoadoutMaterializer.Postimage postimage,
            boolean newlyAcknowledged
        ){
            this.loadoutId=loadout.id;
            this.version=loadout.version;
            this.inventorySlots=
                postimage.inventorySlots();
            this.equipmentSlots=
                postimage.equipmentSlots();
            this.newlyAcknowledged=
                newlyAcknowledged;
            this.authority=AUTHORITY;
        }

        @Override public String toString(){
            return "LoadoutApply{"+
                "id="+loadoutId+
                ",version="+version+
                ",inventorySlots="+inventorySlots+
                ",equipmentSlots="+equipmentSlots+
                ",newlyAcknowledged="+
                newlyAcknowledged+
                ",authority="+authority+
                "}";
        }
    }

    private final WorldPlayer player;
    private final LoadoutService loadouts;
    private final String ownerRef;

    PlayabilityLoadoutApplyService(
        WorldPlayer player,
        LoadoutService loadouts,
        String ownerRef
    ){
        this.player=
            Objects.requireNonNull(
                player,
                "player"
            );
        this.loadouts=
            Objects.requireNonNull(
                loadouts,
                "loadouts"
            );
        this.ownerRef=
            PlayerLoadout.requireText(
                ownerRef,
                "ownerRef"
            );
    }

    Result apply(
        PlayerLoadoutId loadoutId,
        ServerPacketWriter writer
    )throws IOException{
        Objects.requireNonNull(
            loadoutId,
            "loadoutId"
        );
        Objects.requireNonNull(
            writer,
            "writer"
        );

        /*
         * Keep the semantic revision stable from plan through live commit and
         * acknowledgement. LoadoutService mutation methods synchronize on the
         * same monitor, so replace/create cannot interleave this apply.
         */
        synchronized(loadouts){
            LoadoutService.LoadoutApplyPlan plan=
                loadouts.planApply(
                    ownerRef,
                    loadoutId,
                    PlayabilityLoadoutMaterializer::validate
                );

            PlayabilityLoadoutMaterializer.Postimage
                postimage=
                    PlayabilityLoadoutMaterializer
                        .materialize(
                            plan.loadout
                        );

            synchronized(player.mutationLock()){
                player.bank()
                    .applyInventoryEquipmentPostimage(
                        postimage.inventoryItemIds,
                        postimage.inventoryQuantities,
                        postimage.equipmentItemIds,
                        postimage.equipmentQuantities,
                        player.equipment(),
                        writer
                    );
            }

            boolean newlyAcknowledged=
                loadouts.acknowledgeApplied(
                    plan
                );

            return new Result(
                plan.loadout,
                postimage,
                newlyAcknowledged
            );
        }
    }

    String ownerRef(){
        return ownerRef;
    }
}
