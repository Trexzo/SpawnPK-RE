package spk.local;

import java.io.IOException;

/**
 * Typed item-on-NPC adapter for the currently recovered semantic surface.
 *
 * Unknown item/NPC combinations remain decoded but fail closed. The one active
 * semantic here is the evidence-backed reusable pet-accessory application path.
 */
final class LocalItemOnNpcHandler {
    private final BankState bank;
    private final NpcRegistry npcs;
    private final MovementState movement;
    private final PetAccessoryState accessoryState;

    LocalItemOnNpcHandler(
        BankState bank,
        NpcRegistry npcs,
        MovementState movement,
        PetAccessoryState accessoryState
    ){
        this.bank=java.util.Objects.requireNonNull(bank,"bank");
        this.npcs=java.util.Objects.requireNonNull(npcs,"npcs");
        this.movement=java.util.Objects.requireNonNull(movement,"movement");
        this.accessoryState=java.util.Objects.requireNonNull(
            accessoryState,"accessoryState");
    }

    Result handle(
        ItemOnNpcAction action,
        ServerPacketWriter serverPackets
    )throws IOException{
        if(action==null)return null;

        NpcEntity target=npcs.scene(action.targetNpcIndex);
        BankState.Stack source=
            action.widgetId==BankState.NORMAL_INVENTORY_CONTAINER
                ?bank.inventoryAt(action.slot)
                :null;

        if(source==null||source.itemId!=action.itemId){
            return Result.log(
                "V5128_ITEM_ON_NPC "+action+
                " result=REJECTED_SOURCE_INVENTORY_MISMATCH"
            );
        }

        if(PetAccessoryAuthority.isAccessory(action.itemId) &&
           target!=null &&
           target==npcs.pet()){
            Integer selector=PetAccessoryAuthority.selector(action.itemId);

            // Preserve current ordering: semantic selection first, presentation
            // second. If publication fails, in-memory state matches R8.5 behavior.
            accessoryState.setActiveItem(action.itemId);
            String visual=npcs.devSetParticleSelector(
                selector,movement,serverPackets);

            return new Result(
                "V5129_PET_ACCESSORY_USE_ON_PET "+action+
                " targetDef="+target.definitionId+
                " result=ATTACHED selector="+selector+
                " visual={"+visual+"} provenance="+
                PetAccessoryAuthority.selectorAuthority(action.itemId),
                "PET_ACCESSORY_USE_ON_PET"
            );
        }

        return Result.log(
            "V5128_ITEM_ON_NPC "+action+
            " target="+target+
            " result=DECODED_NO_SEMANTIC_HANDLER"
        );
    }

    static final class Result {
        final String logText;
        final String saveReason;

        Result(String logText,String saveReason){
            this.logText=logText;
            this.saveReason=saveReason;
        }

        static Result log(String text){
            return new Result(text,null);
        }
    }
}
