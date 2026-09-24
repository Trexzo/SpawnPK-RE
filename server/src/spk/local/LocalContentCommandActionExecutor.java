package spk.local;

import java.io.IOException;
import spk.content.api.ContentResult;
import spk.content.builtin.LocalLabCoreContentModule;

/**
 * Fail-closed runtime allowlist for semantic effects requested by content
 * command results.
 */
final class LocalContentCommandActionExecutor {
    private final LocalCosmeticCommandHandler cosmetics;
    private final LocalCompColorsCommandHandler compColors;

    LocalContentCommandActionExecutor(
        LocalCosmeticCommandHandler cosmetics,
        LocalCompColorsCommandHandler compColors
    ){
        this.cosmetics=
            java.util.Objects.requireNonNull(
                cosmetics,
                "cosmetics"
            );
        this.compColors=
            java.util.Objects.requireNonNull(
                compColors,
                "compColors"
            );
    }

    ContentResult execute(
        String actionKey,
        String rawCommand,
        String username,
        ServerPacketWriter packets
    )throws IOException{
        if(actionKey==null)
            throw new NullPointerException(
                "actionKey"
            );

        LocalCosmeticCommandHandler.Result
            result;

        if(LocalLabCoreContentModule
                .COSMETIC_INFO_ACTION
                .equals(actionKey)){
            result=cosmetics.info();
        }else if(LocalLabCoreContentModule
                .COSMETIC_REMOVE_ACTION
                .equals(actionKey)){
            result=cosmetics.remove(
                username,
                packets
            );
        }else if(LocalLabCoreContentModule
                .COSMETIC_HELP_ACTION
                .equals(actionKey)){
            result=cosmetics.help();
        }else{
            int[] selectors=
                compColorSelectors(
                    actionKey
                );

            if(selectors!=null){
                LocalCompColorsCommandHandler.Result
                    compResult=
                        compColors.apply(
                            selectors,
                            rawCommand,
                            username,
                            packets
                        );

                return ContentResult.handled(
                    compResult.logText,
                    compResult.saveReason
                );
            }

            return ContentResult.handled(
                "CONTENT_COMMAND_ACTION key="+
                    actionKey+
                    " result=REJECTED_UNSUPPORTED",
                null
            );
        }

        return ContentResult.handled(
            result.logText,
            result.saveReason
        );
    }

    private static int[] compColorSelectors(
        String actionKey
    ){
        String prefix=
            LocalLabCoreContentModule
                .COMP_COLORS_APPLY_ACTION_PREFIX+
            ":";

        if(!actionKey.startsWith(prefix))
            return null;

        String[] tokens=
            actionKey.substring(
                prefix.length()
            ).split(
                ":",
                -1
            );

        if(tokens.length!=6)
            return null;

        int[] selectors=
            new int[6];

        for(int i=0;i<selectors.length;i++){
            try{
                selectors[i]=
                    Integer.parseInt(
                        tokens[i]
                    );
            }catch(Exception ignored){
                return null;
            }

            if(selectors[i]<0||
               selectors[i]>19)
                return null;
        }

        return selectors;
    }
}
