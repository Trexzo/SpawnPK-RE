package spk.local;

import java.io.IOException;

/**
 * Command adapter for completionist-cape color selectors.
 *
 * Selector state remains owned by PlayerState and appearance publication by
 * PlayerPresentationService. This adapter only owns parsing/range validation
 * and reports the existing persistence side effect.
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

    Result handle(
        String[] p,
        String rawCommand,
        String username,
        ServerPacketWriter serverPackets
    )throws IOException{
        if(p==null||p.length==0||!p[0].equalsIgnoreCase("compcolors"))return null;

        if(p.length!=7){
            return new Result(
                "V54_COMP_COLORS command="+rawCommand+
                " result=REJECTED_SELECTOR_RANGE expected=0..19",
                null
            );
        }

        int[] selectors=new int[6];
        boolean valid=true;
        for(int i=0;i<6;i++){
            selectors[i]=parseInt(p[i+1],-1);
            if(selectors[i]<0||selectors[i]>19)valid=false;
        }

        if(!valid||!playerState.setCompSelectors(selectors)){
            return new Result(
                "V54_COMP_COLORS command="+rawCommand+
                " result=REJECTED_SELECTOR_RANGE expected=0..19",
                null
            );
        }

        boolean equipped=BootstrapPackets.hasSpecialCompletionistCape(equipment.appearanceItems());
        if(equipped)playerPresentation.refresh(username,equipment,playerState,serverPackets);

        return new Result(
            "V55_COMP_COLORS command="+rawCommand+
            " result=APPLIED selectors="+playerState.compSelectorSummary()+
            " capeEquipped="+equipped+" appearanceRefresh="+equipped,
            "COMP_COLORS"
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
