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
        return execute(
            actionKey,
            username,
            null,
            packets,
            null
        );
    }

    ContentResult execute(
        String actionKey,
        String username,
        String sourceCommand,
        ServerPacketWriter packets,
        LocalCommandDispatcher.SessionBridge bridge
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
        }else if(LocalLabCoreContentModule
                .DEV_PANEL_OPEN_ACTION
                .equals(actionKey)){
            if(bridge==null||
               sourceCommand==null||
               sourceCommand.isEmpty())
                return ContentResult.handled(
                    "CONTENT_COMMAND_ACTION key="+
                        actionKey+
                        " result=REJECTED_RUNTIME_BRIDGE_UNAVAILABLE",
                    null
                );

            bridge.openDevPanel(
                packets
            );

            return ContentResult.handled(
                "V5172_DEV_PANEL_OPEN route="+
                    sourceCommand+
                    " authority="+
                    ContentAuthorityRepository.summary()+
                    " runtimeWeaponProfiles="+
                    V913WeaponRuntimeAuthority.count(),
                null
            );
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
