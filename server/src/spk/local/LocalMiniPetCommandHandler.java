package spk.local;

import java.io.IOException;

/**
 * Command adapter for the configured mini-pet lifecycle.
 *
 * The actual lifecycle remains in MiniPetService. This class removes command
 * parsing/result formatting from LocalSession and explicitly reports whether
 * the existing account-save side effect is required.
 */
final class LocalMiniPetCommandHandler {
    private final MiniPetService miniPets;
    private final PetState petState;
    private final NpcRegistry npcs;
    private final MovementState movement;

    LocalMiniPetCommandHandler(
        MiniPetService miniPets,
        PetState petState,
        NpcRegistry npcs,
        MovementState movement
    ){
        this.miniPets=java.util.Objects.requireNonNull(miniPets,"miniPets");
        this.petState=java.util.Objects.requireNonNull(petState,"petState");
        this.npcs=java.util.Objects.requireNonNull(npcs,"npcs");
        this.movement=java.util.Objects.requireNonNull(movement,"movement");
    }

    Result handle(String[] p,ServerPacketWriter serverPackets)throws IOException{
        if(p==null||p.length==0||!p[0].equalsIgnoreCase("minipet"))return null;

        String sub=p.length>=2?p[1].toLowerCase(java.util.Locale.ROOT):"status";

        if(sub.equals("status")||sub.equals("info")){
            return new Result("V511_"+miniPets.status(petState,npcs),null);
        }

        if(sub.equals("off")||sub.equals("disable")){
            String r=miniPets.off(petState,npcs,serverPackets);
            return new Result("V511_"+r,"MINIPET_OFF");
        }

        if(sub.equals("set")&&p.length>=3){
            int item=parseInt(p[2],-1);
            String r=miniPets.configure(item,petState,npcs,movement,serverPackets);
            String save=r.startsWith("MINIPET_CONFIGURED")?"MINIPET_SET_DEV":null;
            return new Result("V511_"+r+" commandAuthority=LOCAL_DEV",save);
        }

        return new Result(
            "V511_MINIPET_HELP commands=status | set <itemId> | off nativeInventoryAction=Configure/C2S122",
            null
        );
    }

    static final class Result {
        final String logText;
        final String saveReason;

        Result(String logText,String saveReason){
            this.logText=logText;
            this.saveReason=saveReason;
        }
    }

    private static int parseInt(String s,int fallback){
        try{return Integer.parseInt(s);}catch(Exception e){return fallback;}
    }
}
