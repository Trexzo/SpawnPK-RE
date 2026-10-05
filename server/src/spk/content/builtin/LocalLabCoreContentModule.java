package spk.content.builtin;

import java.util.*;
import spk.content.api.*;

/**
 * Built-in LocalLab custom-content module.
 *
 * Nurse behavior and the Make-over Mage interaction trigger are explicitly
 * CUSTOM_LOCALLAB content. The core registry assigns that provenance externally
 * when installing this module; the exact client/cache identities used by the
 * runtime remain separate evidence from original-server policy.
 */
public final class LocalLabCoreContentModule
    implements ContentModule {

    public static final int MAKEOVER_MAGE_NPC=599;
    public static final String MAKEOVER_MAGE_ACTION=
        "locallab.makeover-mage";

    public static final int BANK_OBJECT=26972;
    public static final String BANK_OBJECT_SERVICE=
        "locallab.bank";

    public static final String COSMETIC_INFO_ACTION=
        "locallab.cosmetic.info";
    public static final String COSMETIC_REMOVE_ACTION=
        "locallab.cosmetic.remove";
    public static final String COSMETIC_HELP_ACTION=
        "locallab.cosmetic.help";

    public static final String COMP_COLORS_APPLY_ACTION_PREFIX=
        "locallab.compcolors.apply";

    public static final String MINIPET_STATUS_ACTION=
        "locallab.minipet.status";
    public static final String MINIPET_OFF_ACTION=
        "locallab.minipet.off";
    public static final String MINIPET_SET_ACTION_PREFIX=
        "locallab.minipet.set";
    public static final String MINIPET_HELP_ACTION=
        "locallab.minipet.help";

    public static final String PET_ACCESSORY_STATUS_ACTION=
        "locallab.petaccessory.status";
    public static final String PET_ACCESSORY_OFF_ACTION=
        "locallab.petaccessory.off";

    public static final String PET_SWITCH_COLOR_ACTION_PREFIX=
        "locallab.petswitchcolor.open";

    public static final String PET_STATUS_ACTION=
        "locallab.petruntime.status";
    public static final String PET_BOOST_ACTION=
        "locallab.petruntime.boost";
    public static final String PET_SCOPE_SNIPE_ACTION=
        "locallab.petruntime.scopesnipe";
    public static final String PET_PROC_ACTION=
        "locallab.petruntime.proc";
    public static final String PET_EVIL_WOLPER_PROC_ACTION_PREFIX=
        "locallab.petruntime.evil-wolper-proc";
    public static final String PET_TEMPOROSS_PROC_ACTION_PREFIX=
        "locallab.petruntime.tempoross-proc";
    public static final String PET_CHARGE_ACTION_PREFIX=
        "locallab.petruntime.charge";
    public static final String PET_DAMAGE_ACTION_PREFIX=
        "locallab.petruntime.damage";

    public static final String COMBAT_FIXTURE_ACTION_PREFIX=
        "locallab.combatfixture.hit";

    public static final String DEV_HIT_INFO_ACTION=
        "locallab.devhit.info";
    public static final String DEV_HIT_RESET_ACTION=
        "locallab.devhit.reset";
    public static final String DEV_HIT_DAMAGE_AUTO_ACTION=
        "locallab.devhit.damage-auto";
    public static final String DEV_HIT_DAMAGE_ACTION_PREFIX=
        "locallab.devhit.damage";
    public static final String DEV_HIT_SEQUENCE_OFF_ACTION=
        "locallab.devhit.sequence-off";
    public static final String DEV_HIT_SEQUENCE_ACTION_PREFIX=
        "locallab.devhit.sequence";
    public static final String DEV_HIT_VARIANT_AUTO_ACTION=
        "locallab.devhit.variant-auto";
    public static final String DEV_HIT_VARIANT_MANUAL_ACTION=
        "locallab.devhit.variant-manual";
    public static final String DEV_HIT_NEXT_ACTION=
        "locallab.devhit.next";
    public static final String DEV_HIT_PREV_ACTION=
        "locallab.devhit.prev";
    public static final String DEV_HIT_TYPE_ACTION_PREFIX=
        "locallab.devhit.type";
    public static final String DEV_HIT_STYLE_ICON_ACTION_PREFIX=
        "locallab.devhit.styleicon";
    public static final String DEV_HIT_PLACEMENT_PRIMARY_ACTION=
        "locallab.devhit.placement-primary";

    public static final String DEV_SESSION_INFO_ACTION=
        "locallab.dev.info";
    public static final String DEV_SESSION_RESET_ACTION=
        "locallab.dev.reset";

    public static final String PRAYER_ICON_ACTION_PREFIX=
        "locallab.prayericon.set";

    @Override public String id(){
        return "locallab-core";
    }

    @Override public void register(
        ContentRegistrar registrar
    ){
        registrar.command(
            "nurse",
            100,
            this::nurse
        );

        registrar.command(
            "appfixture",
            100,
            this::appFixture
        );

        registrar.command(
            "item",
            100,
            this::itemSpawn
        );

        registrar.command(
            "tabitem",
            100,
            this::itemSpawn
        );

        registrar.command(
            "prayerbook",
            100,
            this::prayerBook
        );

        registrar.command(
            "spellbook",
            100,
            this::spellBook
        );

        registrar.command(
            "prayeroff",
            100,
            this::prayerOff
        );

        registrar.command(
            "prayericon",
            100,
            this::prayerIcon
        );

        registrar.command(
            "cosmetic",
            100,
            this::cosmetic
        );

        registrar.command(
            "compcolors",
            100,
            this::compColors
        );

        registrar.command(
            "minipet",
            100,
            this::miniPet
        );

        registrar.command(
            "petaccessory",
            100,
            this::petAccessory
        );

        registrar.command(
            "petswitchcolor",
            100,
            this::petSwitchColor
        );

        registrar.command(
            "petstatus",
            100,
            context->
                ContentResult.action(
                    PET_STATUS_ACTION
                )
        );


        registrar.command(
            "petboost",
            100,
            context->
                ContentResult.action(
                    PET_BOOST_ACTION
                )
        );


        registrar.command(
            "scopesnipe",
            100,
            context->
                ContentResult.action(
                    PET_SCOPE_SNIPE_ACTION
                )
        );


        registrar.command(
            "petproc",
            100,
            context->
                ContentResult.action(
                    PET_PROC_ACTION
                )
        );

        registrar.command(
            "evilwolperproc",
            100,
            this::evilWolperProc
        );

        registrar.command(
            "temporossproc",
            100,
            this::temporossProc
        );

        registrar.command(
            "behemothcharge",
            100,
            this::petCharge
        );

        registrar.command(
            "petcharge",
            100,
            this::petCharge
        );

        registrar.command(
            "behemothhit",
            100,
            this::petDamage
        );

        registrar.command(
            "petdamage",
            100,
            this::petDamage
        );

        registrar.command(
            "combatfixture",
            100,
            this::combatFixture
        );

        registrar.command(
            "devhit",
            100,
            this::devHit
        );

        registrar.command(
            "dev",
            100,
            this::devSession
        );

        registrar.objectOption(
            BANK_OBJECT,
            1,
            100,
            context->
                ContentInteractionResult.handled(
                    BANK_OBJECT_SERVICE
                )
        );

        registrar.npcOption(
            MAKEOVER_MAGE_NPC,
            1,
            100,
            context->
                ContentNpcOptionResult.action(
                    MAKEOVER_MAGE_ACTION
                )
        );

        MakeoverMageDialogueContent makeover=
            new MakeoverMageDialogueContent();

        registrar.dialogue(
            MakeoverMageDialogueContent
                .DIALOGUE_KEY,
            100,
            makeover
        );

        registrar.action(
            MakeoverMageDialogueContent
                .ACTION_APPLY_CHARACTER_DESIGN,
            100,
            makeover::authorizeCharacterDesign
        );
    }

    private ContentResult appFixture(
        ContentCommandContext context
    ){
        String fixture=
            context.arguments().isEmpty()
                ?"help"
                :context.arguments().get(0);

        String result=
            context.presentation()
                .applicationFixture(
                    fixture
                );

        return ContentResult.handled(
            "R85_APP_FIXTURE "+
                result+
                " authority=LOCAL_DEV_FIXTURE clientProtocol=EXACT_CURRENT",
            null
        );
    }

    private ContentResult devSession(
        ContentCommandContext context
    ){
        String sub=
            context.arguments().isEmpty()
                ?"info"
                :context.arguments().get(0)
                    .toLowerCase(
                        Locale.ROOT
                    );

        if("panel".equals(sub))
            return null;

        if("reset".equals(sub))
            return ContentResult.action(
                DEV_SESSION_RESET_ACTION
            );

        return ContentResult.action(
            DEV_SESSION_INFO_ACTION
        );
    }

    private ContentResult devHit(
        ContentCommandContext context
    ){
        java.util.List<String> args=
            context.arguments();

        String sub=
            args.isEmpty()
                ?"info"
                :args.get(0)
                    .toLowerCase(
                        Locale.ROOT
                    );

        if("info".equals(sub))
            return ContentResult.action(
                DEV_HIT_INFO_ACTION
            );

        if("reset".equals(sub))
            return ContentResult.action(
                DEV_HIT_RESET_ACTION
            );

        if("damage".equals(sub)&&
           args.size()>=2){
            String token=
                args.get(1)
                    .toLowerCase(
                        Locale.ROOT
                    );

            if("auto".equals(token)||
               "off".equals(token)||
               "reset".equals(token))
                return ContentResult.action(
                    DEV_HIT_DAMAGE_AUTO_ACTION
                );

            int damage=
                parseInt(
                    token,
                    Integer.MIN_VALUE
                );

            if(damage<0||
               damage>255)
                return ContentResult.handled(
                    "V5128_DEVHIT_REJECTED damage=0..255|auto",
                    null
                );

            return ContentResult.action(
                DEV_HIT_DAMAGE_ACTION_PREFIX+
                ":"+
                damage
            );
        }

        if("sequence".equals(sub)&&
           args.size()>=2){
            String token=
                args.get(1)
                    .trim();

            if("off".equalsIgnoreCase(token)||
               "auto".equalsIgnoreCase(token)||
               "reset".equalsIgnoreCase(token))
                return ContentResult.action(
                    DEV_HIT_SEQUENCE_OFF_ACTION
                );

            String[] parts=
                token.split(
                    ","
                );

            if(parts.length<2||
               parts.length>16)
                return ContentResult.handled(
                    "V5128_DEVHIT_REJECTED sequence=comma-separated_2..16_values_0..255",
                    null
                );

            int[] sequence=
                new int[parts.length];

            for(int i=0;i<parts.length;i++){
                try{
                    sequence[i]=
                        Integer.parseInt(
                            parts[i].trim()
                        );
                }catch(Exception error){
                    return ContentResult.handled(
                        "V5128_DEVHIT_REJECTED sequence=example_37,100",
                        null
                    );
                }

                if(sequence[i]<0||
                   sequence[i]>255)
                    return ContentResult.handled(
                        "V5128_DEVHIT_REJECTED sequence=value_range_0..255",
                        null
                    );
            }

            StringBuilder action=
                new StringBuilder(
                    DEV_HIT_SEQUENCE_ACTION_PREFIX
                );

            for(int value:sequence)
                action.append(':')
                    .append(value);

            return ContentResult.action(
                action.toString()
            );
        }

        if("variant".equals(sub)&&
           args.size()>=2){
            String token=
                args.get(1)
                    .toLowerCase(
                        Locale.ROOT
                    );

            if("auto".equals(token))
                return ContentResult.action(
                    DEV_HIT_VARIANT_AUTO_ACTION
                );

            if("manual".equals(token))
                return ContentResult.action(
                    DEV_HIT_VARIANT_MANUAL_ACTION
                );

            return ContentResult.handled(
                "V5128_DEVHIT_REJECTED variant=auto|manual",
                null
            );
        }

        if("next".equals(sub))
            return ContentResult.action(
                DEV_HIT_NEXT_ACTION
            );

        if("prev".equals(sub))
            return ContentResult.action(
                DEV_HIT_PREV_ACTION
            );

        if("type".equals(sub)&&
           args.size()>=2){
            int type=
                parseInt(
                    args.get(1),
                    Integer.MIN_VALUE
                );

            if(type<0||
               type>255)
                return ContentResult.handled(
                    "V5128_DEVHIT_REJECTED type=0..255",
                    null
                );

            return ContentResult.action(
                DEV_HIT_TYPE_ACTION_PREFIX+
                ":"+
                type
            );
        }

        if("styleicon".equals(sub)&&
           args.size()>=2){
            int styleIcon=
                parseInt(
                    args.get(1),
                    Integer.MIN_VALUE
                );

            if(styleIcon<0||
               styleIcon>255)
                return ContentResult.handled(
                    "V5128_DEVHIT_REJECTED styleicon=0..255",
                    null
                );

            return ContentResult.action(
                DEV_HIT_STYLE_ICON_ACTION_PREFIX+
                ":"+
                styleIcon
            );
        }

        if("placement".equals(sub)&&
           args.size()>=2){
            String token=
                args.get(1)
                    .toLowerCase(
                        Locale.ROOT
                    );

            if("primary".equals(token))
                return ContentResult.action(
                    DEV_HIT_PLACEMENT_PRIMARY_ACTION
                );

            if("secondary".equals(token))
                return ContentResult.handled(
                    "V5128_DEVHIT_REJECTED placement=secondary reason=current_NPC_sync_encoder_only_certifies_primary_singleHit_mask",
                    null
                );

            return ContentResult.handled(
                "V5128_DEVHIT_REJECTED placement=primary|secondary",
                null
            );
        }

        return ContentResult.handled(
            "V5128_DEVHIT_HELP info | variant auto|manual | type <0..255> | next | prev | damage <0..255|auto> | sequence <a,b,...|off> | styleicon <0..255> | placement primary|secondary | reset",
            null
        );
    }

    private ContentResult petCharge(
        ContentCommandContext context
    ){
        int charge=
            context.arguments().isEmpty()
                ?-1
                :parseInt(
                    context.arguments().get(0),
                    -1
                );

        return ContentResult.action(
            PET_CHARGE_ACTION_PREFIX+
            ":"+
            charge
        );
    }

    private ContentResult evilWolperProc(
        ContentCommandContext context
    ){
        int state=
            context.arguments().isEmpty()
                ?1
                :parseInt(
                    context.arguments().get(0),
                    1
                );

        if(state<1||state>3)
            state=1;

        return ContentResult.action(
            PET_EVIL_WOLPER_PROC_ACTION_PREFIX+
            ":"+
            state
        );
    }

    private ContentResult temporossProc(
        ContentCommandContext context
    ){
        int state=
            context.arguments().isEmpty()
                ?1
                :parseInt(
                    context.arguments().get(0),
                    1
                );

        return ContentResult.action(
            PET_TEMPOROSS_PROC_ACTION_PREFIX+
            ":"+
            state
        );
    }

    private ContentResult petDamage(
        ContentCommandContext context
    ){
        int damage=
            context.arguments().isEmpty()
                ?0
                :parseInt(
                    context.arguments().get(0),
                    0
                );

        return ContentResult.action(
            PET_DAMAGE_ACTION_PREFIX+
            ":"+
            damage
        );
    }

    private ContentResult combatFixture(
        ContentCommandContext context
    ){
        int damage=
            context.arguments().isEmpty()
                ?0
                :parseInt(
                    context.arguments().get(0),
                    0
                );

        return ContentResult.action(
            COMBAT_FIXTURE_ACTION_PREFIX+
            ":"+
            damage
        );
    }

    private ContentResult petSwitchColor(
        ContentCommandContext context
    ){
        int requested=
            context.arguments().isEmpty()
                ?-1
                :parseInt(
                    context.arguments().get(0),
                    -1
                );

        return ContentResult.action(
            PET_SWITCH_COLOR_ACTION_PREFIX+
            ":"+
            requested
        );
    }

    private ContentResult petAccessory(
        ContentCommandContext context
    ){
        String sub=
            context.arguments().isEmpty()
                ?"status"
                :context.arguments().get(0)
                    .toLowerCase(
                        Locale.ROOT
                    );

        if("off".equals(sub)||
           "none".equals(sub)||
           "disable".equals(sub))
            return ContentResult.action(
                PET_ACCESSORY_OFF_ACTION
            );

        return ContentResult.action(
            PET_ACCESSORY_STATUS_ACTION
        );
    }

    private ContentResult miniPet(
        ContentCommandContext context
    ){
        String sub=
            context.arguments().isEmpty()
                ?"status"
                :context.arguments().get(0)
                    .toLowerCase(
                        Locale.ROOT
                    );

        if("status".equals(sub)||
           "info".equals(sub))
            return ContentResult.action(
                MINIPET_STATUS_ACTION
            );

        if("off".equals(sub)||
           "disable".equals(sub))
            return ContentResult.action(
                MINIPET_OFF_ACTION
            );

        if("set".equals(sub)&&
           context.arguments().size()>=2){
            int itemId=
                parseInt(
                    context.arguments().get(1),
                    -1
                );

            return ContentResult.action(
                MINIPET_SET_ACTION_PREFIX+
                ":"+
                itemId
            );
        }

        return ContentResult.action(
            MINIPET_HELP_ACTION
        );
    }

    private ContentResult compColors(
        ContentCommandContext context
    ){
        if(context.arguments().size()!=6)
            return ContentResult.handled(
                "V54_COMP_COLORS command="+
                    context.rawCommand()+
                    " result=REJECTED_SELECTOR_RANGE expected=0..19",
                null
            );

        int[] selectors=new int[6];

        for(int i=0;i<selectors.length;i++){
            selectors[i]=
                parseInt(
                    context.arguments().get(i),
                    -1
                );

            if(selectors[i]<0||
               selectors[i]>19)
                return ContentResult.handled(
                    "V54_COMP_COLORS command="+
                        context.rawCommand()+
                        " result=REJECTED_SELECTOR_RANGE expected=0..19",
                    null
                );
        }

        StringBuilder action=
            new StringBuilder(
                COMP_COLORS_APPLY_ACTION_PREFIX
            );

        for(int selector:selectors)
            action.append(':')
                .append(selector);

        return ContentResult.action(
            action.toString()
        );
    }

    private ContentResult cosmetic(
        ContentCommandContext context
    ){
        String sub=
            context.arguments().isEmpty()
                ?"info"
                :context.arguments().get(0)
                    .toLowerCase(
                        Locale.ROOT
                    );

        if("info".equals(sub)||
           "status".equals(sub))
            return ContentResult.action(
                COSMETIC_INFO_ACTION
            );

        if("off".equals(sub)||
           "remove".equals(sub))
            return ContentResult.action(
                COSMETIC_REMOVE_ACTION
            );

        return ContentResult.action(
            COSMETIC_HELP_ACTION
        );
    }

    private ContentResult prayerBook(
        ContentCommandContext context
    ){
        if(context.arguments().isEmpty())
            return null;

        String token=
            context.arguments().get(0)
                .toLowerCase(
                    Locale.ROOT
                );

        ContentPrayerBook book;

        if("normal".equals(token)||
           "prayer".equals(token))
            book=ContentPrayerBook.NORMAL;
        else if("curses".equals(token)||
                "curse".equals(token))
            book=ContentPrayerBook.CURSES;
        else
            return ContentResult.handled(
                "V510_PRAYER_BOOK command="+
                    cleanCommand(
                        context.rawCommand())+
                    " result=REJECTED_BOOK expected=normal|curses",
                null
            );

        return ContentResult.handled(
            "V510_PRAYER_BOOK command="+
                cleanCommand(
                    context.rawCommand())+
                " result="+
                context.player()
                    .switchPrayerBook(
                        book
                    ),
            null
        );
    }

    private ContentResult spellBook(
        ContentCommandContext context
    ){
        if(context.arguments().isEmpty())
            return null;

        String token=
            context.arguments().get(0)
                .toLowerCase(
                    Locale.ROOT
                );

        ContentSpellBook book;

        if("modern".equals(token)||
           "normal".equals(token))
            book=ContentSpellBook.MODERN;
        else if("ancient".equals(token)||
                "ancients".equals(token))
            book=ContentSpellBook.ANCIENT;
        else if("lunar".equals(token)||
                "lunars".equals(token))
            book=ContentSpellBook.LUNAR;
        else
            return ContentResult.handled(
                "V510_SPELL_BOOK command="+
                    cleanCommand(
                        context.rawCommand())+
                    " result=REJECTED_BOOK expected=modern|ancient|lunar",
                null
            );

        return ContentResult.handled(
            "V510_SPELL_BOOK command="+
                cleanCommand(
                    context.rawCommand())+
                " result="+
                context.player()
                    .switchSpellBook(
                        book
                    ),
            null
        );
    }

    private ContentResult prayerIcon(
        ContentCommandContext context
    ){
        if(context.arguments().isEmpty())
            return null;

        int icon=
            parseInt(
                context.arguments().get(0),
                Integer.MIN_VALUE
            );

        if(icon<-1||
           icon>20)
            return ContentResult.handled(
                "V510_PRAYER_ICON result=REJECTED_HEADICON_RANGE expected=-1..20",
                null
            );

        return ContentResult.action(
            PRAYER_ICON_ACTION_PREFIX+
            ":"+
            icon
        );
    }

    private ContentResult prayerOff(
        ContentCommandContext context
    ){
        return ContentResult.handled(
            "V510_PRAYER_OFF result="+
                context.player()
                    .deactivatePrayers(),
            null
        );
    }

    private static String cleanCommand(
        String rawCommand
    ){
        String clean=
            rawCommand==null
                ?""
                :rawCommand.trim();

        if(clean.startsWith("::"))
            clean=
                clean.substring(2);

        return clean;
    }

    private ContentResult itemSpawn(
        ContentCommandContext context
    ){
        if(context.arguments().isEmpty())
            return null;

        int itemId=
            parseInt(
                context.arguments().get(0),
                -1
            );

        int amount=
            context.arguments().size()>=2
                ?parseAmount(
                    context.arguments().get(1),
                    1
                )
                :1;

        String result=
            context.player().grantItem(
                itemId,
                amount
            );

        return ContentResult.handled(
            "V522_ITEM_COMMAND source="+
                context.commandName()
                    .toLowerCase(
                        Locale.ROOT
                    )+
                " command="+
                context.rawCommand()+
                " result="+result,
            "ITEM_SPAWN"
        );
    }

    private static int parseInt(
        String value,
        int fallback
    ){
        try{
            return Integer.parseInt(
                value
            );
        }catch(Exception ignored){
            return fallback;
        }
    }

    private static int parseAmount(
        String value,
        int fallback
    ){
        if(value==null)
            return fallback;

        String token=
            value.trim()
                .toLowerCase(
                    Locale.ROOT
                )
                .replace(
                    ",",
                    ""
                );

        long multiplier=1L;

        if(token.endsWith("k")){
            multiplier=1_000L;
            token=
                token.substring(
                    0,
                    token.length()-1
                );
        }else if(token.endsWith("m")){
            multiplier=1_000_000L;
            token=
                token.substring(
                    0,
                    token.length()-1
                );
        }else if(token.endsWith("b")){
            multiplier=1_000_000_000L;
            token=
                token.substring(
                    0,
                    token.length()-1
                );
        }

        try{
            long base=
                Long.parseLong(
                    token
                );
            long amount=
                Math.max(
                    1L,
                    Math.min(
                        1_000_000_000L,
                        base*multiplier
                    )
                );

            return (int)amount;
        }catch(Exception ignored){
            return fallback;
        }
    }

    private ContentResult nurse(
        ContentCommandContext context
    )throws Exception{
        ContentPlayer player=
            context.player();

        EnumSet<ContentSkill> changed=
            EnumSet.noneOf(
                ContentSkill.class
            );

        changed.addAll(
            player.restoreCombatSkillsAndSpecial()
        );
        player.clearTimedStatuses();
        player.setRunEnergy(100);
        changed.addAll(
            player.syncMaintainedPetEffects()
        );

        for(ContentSkill skill:
                ContentSkill.values())
            if(changed.contains(skill))
                context.presentation().skill(
                    skill,
                    player.skillExperience(skill),
                    player.skillLevel(skill)
                );

        context.presentation()
            .runEnergy(100);
        context.presentation()
            .specialEnergy(100);
        context.presentation()
            .animationAndGfx(
                10184,
                1310,
                0,
                0
            );

        boolean scopesightActive=
            player.maintainedPetEffectActive();

        String log=
            "V58_NURSE command="+
            context.rawCommand()+
            " hp="+
            player.skillLevel(
                ContentSkill.HITPOINTS
            )+
            " prayer="+
            player.skillLevel(
                ContentSkill.PRAYER
            )+
            " ranged="+
            player.skillLevel(
                ContentSkill.RANGED
            )+
            " magic="+
            player.skillLevel(
                ContentSkill.MAGIC
            )+
            " special="+
            player.specialEnergy()+
            " run="+player.runEnergy()+
            " poison="+player.poison()+
            " venom="+player.venom()+
            " sicken="+player.sicken()+
            " anim=10184 gfx=1310 gfxHeight=0 gfxDelay=0 scopesightActive="+
            scopesightActive;

        return ContentResult.handled(
            log,
            "NURSE"
        );
    }
}
