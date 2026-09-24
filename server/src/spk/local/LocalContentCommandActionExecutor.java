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

    LocalContentCommandActionExecutor(
        LocalCosmeticCommandHandler cosmetics
    ){
        this.cosmetics=
            java.util.Objects.requireNonNull(
                cosmetics,
                "cosmetics"
            );
    }

    ContentResult execute(
        String actionKey,
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
}
