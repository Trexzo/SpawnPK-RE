package spk.local;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import spk.content.api.ContentResult;
import spk.content.builtin.LocalLabCoreContentModule;

/**
 * Fail-closed runtime allowlist for semantic effects requested by content
 * command results.
 */
final class LocalContentCommandActionExecutor {
    static final class Outcome {
        final ContentResult contentResult;
        final LocalPetInventoryDialogHandler.Result dialogResult;
        final List<String> logLines;

        private Outcome(
            ContentResult contentResult,
            LocalPetInventoryDialogHandler.Result dialogResult,
            List<String> logLines
        ){
            this.contentResult=contentResult;
            this.dialogResult=dialogResult;
            this.logLines=logLines;
        }

        static Outcome content(
            ContentResult result
        ){
            return new Outcome(
                java.util.Objects.requireNonNull(
                    result,
                    "result"
                ),
                null,
                null
            );
        }

        static Outcome dialog(
            LocalPetInventoryDialogHandler.Result result
        ){
            return new Outcome(
                null,
                java.util.Objects.requireNonNull(
                    result,
                    "result"
                ),
                null
            );
        }

        static Outcome lines(
            List<String> lines
        ){
            return new Outcome(
                null,
                null,
                Collections.unmodifiableList(
                    new ArrayList<>(
                        java.util.Objects.requireNonNull(
                            lines,
                            "lines"
                        )
                    )
                )
            );
        }
    }

    private final LocalCosmeticCommandHandler cosmetics;
    private final LocalCompColorsCommandHandler compColors;
    private final LocalMiniPetCommandHandler miniPets;
    private final LocalPetCompatibilityCommandHandler petCompatibility;
    private final LocalCombatCommandHandler combat;

    LocalContentCommandActionExecutor(
        LocalCosmeticCommandHandler cosmetics,
        LocalCompColorsCommandHandler compColors,
        LocalMiniPetCommandHandler miniPets,
        LocalPetCompatibilityCommandHandler petCompatibility
    ){
        this(
            cosmetics,
            compColors,
            miniPets,
            petCompatibility,
            null
        );
    }

    LocalContentCommandActionExecutor(
        LocalCosmeticCommandHandler cosmetics,
        LocalCompColorsCommandHandler compColors,
        LocalMiniPetCommandHandler miniPets,
        LocalPetCompatibilityCommandHandler petCompatibility,
        LocalCombatCommandHandler combat
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
        this.miniPets=
            java.util.Objects.requireNonNull(
                miniPets,
                "miniPets"
            );
        this.petCompatibility=
            java.util.Objects.requireNonNull(
                petCompatibility,
                "petCompatibility"
            );
        this.combat=combat;
    }

    Outcome executeOutcome(
        String actionKey,
        String rawCommand,
        String username,
        ServerPacketWriter packets
    )throws IOException{
        Integer requested=
            petSwitchColorRequested(
                actionKey
            );

        if(requested!=null)
            return Outcome.dialog(
                petCompatibility.switchColor(
                    requested,
                    packets
                )
            );

        Integer damage=
            combatFixtureDamage(
                actionKey
            );

        if(damage!=null&&
           combat!=null)
            return Outcome.lines(
                combat.fixture(
                    damage,
                    rawCommand,
                    packets
                )
            );

        return Outcome.content(
            execute(
                actionKey,
                rawCommand,
                username,
                packets
            )
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

            LocalMiniPetCommandHandler.Result
                miniResult=
                    miniPetResult(
                        actionKey,
                        packets
                    );

            if(miniResult!=null)
                return ContentResult.handled(
                    miniResult.logText,
                    miniResult.saveReason
                );

            LocalPetCompatibilityCommandHandler.Outcome
                accessoryResult=
                    petAccessoryResult(
                        actionKey,
                        packets
                    );

            if(accessoryResult!=null)
                return ContentResult.handled(
                    accessoryResult.logText,
                    accessoryResult.saveReason
                );

            ContentResult devHit=
                devHitResult(
                    actionKey
                );

            if(devHit!=null)
                return devHit;

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

    private ContentResult devHitResult(
        String actionKey
    ){
        if(combat==null)
            return null;

        if(LocalLabCoreContentModule
                .DEV_HIT_INFO_ACTION
                .equals(actionKey))
            return ContentResult.handled(
                combat.devHitInfo(),
                null
            );

        if(LocalLabCoreContentModule
                .DEV_HIT_RESET_ACTION
                .equals(actionKey))
            return ContentResult.handled(
                combat.devHitReset(),
                null
            );

        if(LocalLabCoreContentModule
                .DEV_HIT_DAMAGE_AUTO_ACTION
                .equals(actionKey))
            return ContentResult.handled(
                combat.devHitDamageAuto(),
                null
            );

        if(LocalLabCoreContentModule
                .DEV_HIT_SEQUENCE_OFF_ACTION
                .equals(actionKey))
            return ContentResult.handled(
                combat.devHitSequenceOff(),
                null
            );

        if(LocalLabCoreContentModule
                .DEV_HIT_VARIANT_AUTO_ACTION
                .equals(actionKey))
            return ContentResult.handled(
                combat.devHitVariant(
                    true
                ),
                null
            );

        if(LocalLabCoreContentModule
                .DEV_HIT_VARIANT_MANUAL_ACTION
                .equals(actionKey))
            return ContentResult.handled(
                combat.devHitVariant(
                    false
                ),
                null
            );

        if(LocalLabCoreContentModule
                .DEV_HIT_NEXT_ACTION
                .equals(actionKey))
            return ContentResult.handled(
                combat.devHitNextType(),
                null
            );

        if(LocalLabCoreContentModule
                .DEV_HIT_PREV_ACTION
                .equals(actionKey))
            return ContentResult.handled(
                combat.devHitPreviousType(),
                null
            );

        if(LocalLabCoreContentModule
                .DEV_HIT_PLACEMENT_PRIMARY_ACTION
                .equals(actionKey))
            return ContentResult.handled(
                combat.devHitPlacementPrimary(),
                null
            );

        Integer damage=
            scalarDevHitValue(
                actionKey,
                LocalLabCoreContentModule
                    .DEV_HIT_DAMAGE_ACTION_PREFIX
            );

        if(damage!=null)
            return ContentResult.handled(
                combat.devHitDamage(
                    damage
                ),
                null
            );

        Integer type=
            scalarDevHitValue(
                actionKey,
                LocalLabCoreContentModule
                    .DEV_HIT_TYPE_ACTION_PREFIX
            );

        if(type!=null)
            return ContentResult.handled(
                combat.devHitType(
                    type
                ),
                null
            );

        Integer styleIcon=
            scalarDevHitValue(
                actionKey,
                LocalLabCoreContentModule
                    .DEV_HIT_STYLE_ICON_ACTION_PREFIX
            );

        if(styleIcon!=null)
            return ContentResult.handled(
                combat.devHitStyleIcon(
                    styleIcon
                ),
                null
            );

        int[] sequence=
            devHitSequence(
                actionKey
            );

        if(sequence!=null)
            return ContentResult.handled(
                combat.devHitSequence(
                    sequence
                ),
                null
            );

        return null;
    }

    private static Integer scalarDevHitValue(
        String actionKey,
        String actionPrefix
    ){
        String prefix=
            actionPrefix+
            ":";

        if(actionKey==null||
           !actionKey.startsWith(prefix))
            return null;

        String token=
            actionKey.substring(
                prefix.length()
            );

        if(token.isEmpty()||
           token.indexOf(':')>=0)
            return null;

        try{
            int value=
                Integer.parseInt(
                    token
                );

            return value>=0&&
                value<=255
                    ?Integer.valueOf(value)
                    :null;
        }catch(NumberFormatException ignored){
            return null;
        }
    }

    private static int[] devHitSequence(
        String actionKey
    ){
        String prefix=
            LocalLabCoreContentModule
                .DEV_HIT_SEQUENCE_ACTION_PREFIX+
            ":";

        if(actionKey==null||
           !actionKey.startsWith(prefix))
            return null;

        String[] tokens=
            actionKey.substring(
                prefix.length()
            ).split(
                ":",
                -1
            );

        if(tokens.length<2||
           tokens.length>16)
            return null;

        int[] sequence=
            new int[tokens.length];

        for(int i=0;i<tokens.length;i++){
            try{
                sequence[i]=
                    Integer.parseInt(
                        tokens[i]
                    );
            }catch(NumberFormatException ignored){
                return null;
            }

            if(sequence[i]<0||
               sequence[i]>255)
                return null;
        }

        return sequence;
    }

    private static Integer combatFixtureDamage(
        String actionKey
    ){
        String prefix=
            LocalLabCoreContentModule
                .COMBAT_FIXTURE_ACTION_PREFIX+
            ":";

        if(actionKey==null||
           !actionKey.startsWith(prefix))
            return null;

        String token=
            actionKey.substring(
                prefix.length()
            );

        if(token.isEmpty()||
           token.indexOf(':')>=0)
            return null;

        try{
            return Integer.valueOf(
                token
            );
        }catch(NumberFormatException ignored){
            return null;
        }
    }

    private static Integer petSwitchColorRequested(
        String actionKey
    ){
        String prefix=
            LocalLabCoreContentModule
                .PET_SWITCH_COLOR_ACTION_PREFIX+
            ":";

        if(actionKey==null||
           !actionKey.startsWith(prefix))
            return null;

        String token=
            actionKey.substring(
                prefix.length()
            );

        if(token.isEmpty()||
           token.indexOf(':')>=0)
            return null;

        try{
            return Integer.valueOf(
                token
            );
        }catch(NumberFormatException ignored){
            return null;
        }
    }

    private LocalPetCompatibilityCommandHandler.Outcome
        petAccessoryResult(
            String actionKey,
            ServerPacketWriter packets
        )throws IOException{
        if(LocalLabCoreContentModule
                .PET_ACCESSORY_STATUS_ACTION
                .equals(actionKey))
            return petCompatibility
                .accessoryStatus();

        if(LocalLabCoreContentModule
                .PET_ACCESSORY_OFF_ACTION
                .equals(actionKey))
            return petCompatibility
                .accessoryOff(
                    packets
                );

        return null;
    }

    private LocalMiniPetCommandHandler.Result
        miniPetResult(
            String actionKey,
            ServerPacketWriter packets
        )throws IOException{
        if(LocalLabCoreContentModule
                .MINIPET_STATUS_ACTION
                .equals(actionKey))
            return miniPets.status();

        if(LocalLabCoreContentModule
                .MINIPET_OFF_ACTION
                .equals(actionKey))
            return miniPets.off(
                packets
            );

        if(LocalLabCoreContentModule
                .MINIPET_HELP_ACTION
                .equals(actionKey))
            return miniPets.help();

        String prefix=
            LocalLabCoreContentModule
                .MINIPET_SET_ACTION_PREFIX+
            ":";

        if(!actionKey.startsWith(prefix))
            return null;

        String token=
            actionKey.substring(
                prefix.length()
            );

        if(token.isEmpty()||
           token.indexOf(':')>=0)
            return null;

        try{
            return miniPets.configure(
                Integer.parseInt(
                    token
                ),
                packets
            );
        }catch(NumberFormatException ignored){
            return null;
        }
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
