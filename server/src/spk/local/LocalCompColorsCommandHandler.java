package spk.local;

import java.io.IOException;

/**
 * Runtime executor for semantic completionist-cape color actions.
 *
 * Command parsing/range policy is content-owned. This class retains selector
 * mutation and current-session appearance publication.
 */
final class LocalCompColorsCommandHandler {
    private final PlayerState playerState;
    private final EquipmentState equipment;
    private final PlayerPresentationService playerPresentation;

    LocalCompColorsCommandHandler(
        PlayerState playerState,
        EquipmentState equipment,
        PlayerPresentationService playerPresentation
    ){
        this.playerState=java.util.Objects.requireNonNull(playerState,"playerState");
        this.equipment=java.util.Objects.requireNonNull(equipment,"equipment");
        this.playerPresentation=java.util.Objects.requireNonNull(playerPresentation,"playerPresentation");
    }

    Result apply(
        int[] selectors,
        String rawCommand,
        String username,
        ServerPacketWriter serverPackets
    )throws IOException{
        if(selectors==null||selectors.length!=6)
            return rejected(rawCommand);

        int[] copy=selectors.clone();

        for(int selector:copy)
            if(selector<0||selector>19)
                return rejected(rawCommand);

        if(!playerState.setCompSelectors(copy))
            return rejected(rawCommand);

        boolean equipped=
            BootstrapPackets.hasSpecialCompletionistCape(
                equipment.appearanceItems()
            );

        if(equipped)
            playerPresentation.refresh(
                username,
                equipment,
                playerState,
                serverPackets
            );

        return new Result(
            "V55_COMP_COLORS command="+rawCommand+
            " result=APPLIED selectors="+playerState.compSelectorSummary()+
            " capeEquipped="+equipped+
            " appearanceRefresh="+equipped,
            "COMP_COLORS"
        );
    }

    private static Result rejected(
        String rawCommand
    ){
        return new Result(
            "V54_COMP_COLORS command="+rawCommand+
            " result=REJECTED_SELECTOR_RANGE expected=0..19",
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
