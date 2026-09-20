package spk.local;

import java.io.IOException;

/**
 * Small compatibility/admin pet command adapter.
 *
 * This keeps the current fail-closed accessory mapping and the LocalLab Scooby
 * colour compatibility dialog without promoting either into recovered server
 * mechanics.
 */
final class LocalPetCompatibilityCommandHandler {
    static final class Outcome {
        final String logText;
        final String saveReason;
        final LocalPetInventoryDialogHandler.Result dialogResult;

        Outcome(
            String logText,
            String saveReason,
            LocalPetInventoryDialogHandler.Result dialogResult
        ){
            this.logText=logText;
            this.saveReason=saveReason;
            this.dialogResult=dialogResult;
        }

        static Outcome log(String logText,String saveReason){
            return new Outcome(logText,saveReason,null);
        }

        static Outcome dialog(
            LocalPetInventoryDialogHandler.Result result
        ){
            return new Outcome(null,null,result);
        }
    }

    private final PetAccessoryState petAccessoryState;
    private final NpcRegistry npcs;
    private final MovementState movement;
    private final LocalPetInventoryDialogHandler petDialogs;

    LocalPetCompatibilityCommandHandler(
        PetAccessoryState petAccessoryState,
        NpcRegistry npcs,
        MovementState movement,
        LocalPetInventoryDialogHandler petDialogs
    ){
        this.petAccessoryState=java.util.Objects.requireNonNull(
            petAccessoryState,"petAccessoryState");
        this.npcs=java.util.Objects.requireNonNull(npcs,"npcs");
        this.movement=java.util.Objects.requireNonNull(
            movement,"movement");
        this.petDialogs=java.util.Objects.requireNonNull(
            petDialogs,"petDialogs");
    }

    Outcome handle(
        String[] p,
        ServerPacketWriter serverPackets
    )throws IOException{
        if(p==null||p.length<1)return null;

        if(p[0].equalsIgnoreCase("petaccessory")){
            String sub=
                p.length>=2
                    ?p[1].toLowerCase(java.util.Locale.ROOT)
                    :"status";

            if(sub.equals("off")||
               sub.equals("none")||
               sub.equals("disable")){
                petAccessoryState.clear();

                String visual=npcs.devSetParticleSelector(
                    null,
                    movement,
                    serverPackets);

                return Outcome.log(
                    "V5128_PET_ACCESSORY active=NONE visual={"+
                    visual+
                    "}",
                    "PET_ACCESSORY_DEV_OFF"
                );
            }

            return Outcome.log(
                "V5128_PET_ACCESSORY active="+
                (petAccessoryState.activeItem()==0
                    ?"NONE"
                    :petAccessoryState.activeItem()+
                        "/"+
                        PetAccessoryAuthority.name(
                            petAccessoryState.activeItem()))+
                " visualSelectorMapping=UNRESOLVED_FAIL_CLOSED",
                null
            );
        }

        if(p[0].equalsIgnoreCase("petswitchcolor")){
            int requested=
                p.length>=2
                    ?parseInt(p[1],-1)
                    :-1;

            return Outcome.dialog(
                petDialogs.openScoobyColorCompat(
                    requested,
                    serverPackets)
            );
        }

        return null;
    }

    private static int parseInt(String value,int fallback){
        try{
            return Integer.parseInt(value);
        }catch(Exception e){
            return fallback;
        }
    }
}
