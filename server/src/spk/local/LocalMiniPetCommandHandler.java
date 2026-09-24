package spk.local;

import java.io.IOException;

/**
 * Runtime executor for semantic mini-pet command effects.
 *
 * Command/subcommand policy is content-owned. This class retains the configured
 * mini-pet lifecycle, NPC publication and movement-dependent runtime work.
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

    Result status(){
        return new Result(
            "V511_"+miniPets.status(
                petState,
                npcs
            ),
            null
        );
    }

    Result off(
        ServerPacketWriter serverPackets
    )throws IOException{
        String result=
            miniPets.off(
                petState,
                npcs,
                serverPackets
            );

        return new Result(
            "V511_"+result,
            "MINIPET_OFF"
        );
    }

    Result configure(
        int itemId,
        ServerPacketWriter serverPackets
    )throws IOException{
        String result=
            miniPets.configure(
                itemId,
                petState,
                npcs,
                movement,
                serverPackets
            );

        String saveReason=
            result.startsWith(
                "MINIPET_CONFIGURED"
            )
                ?"MINIPET_SET_DEV"
                :null;

        return new Result(
            "V511_"+result+
            " commandAuthority=LOCAL_DEV",
            saveReason
        );
    }

    Result help(){
        return new Result(
            "V511_MINIPET_HELP commands=status | set <itemId> | off nativeInventoryAction=Configure/C2S122",
            null
        );
    }

    static final class Result {
        final String logText;
        final String saveReason;

        Result(
            String logText,
            String saveReason
        ){
            this.logText=logText;
            this.saveReason=saveReason;
        }
    }
}
