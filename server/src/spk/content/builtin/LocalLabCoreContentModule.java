package spk.content.builtin;

import java.util.*;
import spk.content.api.*;

/**
 * First static Java module proving the content API boundary.
 *
 * Nurse is explicitly CUSTOM_LOCALLAB behavior; the core registry assigns that
 * provenance externally when installing this module.
 */
public final class LocalLabCoreContentModule
    implements ContentModule {

    public static final int MAKEOVER_MAGE_NPC=599;
    public static final String MAKEOVER_MAGE_ACTION=
        "locallab.makeover-mage";

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

        registrar.npcOption(
            MAKEOVER_MAGE_NPC,
            1,
            100,
            context->
                ContentNpcOptionResult.action(
                    MAKEOVER_MAGE_ACTION
                )
        );
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
