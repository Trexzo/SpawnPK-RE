package spk.local;

import java.io.*;

public final class CombatSystemHooksTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer player=new WorldPlayer();

        ByteArrayOutputStream uiOut=
            new ByteArrayOutputStream();
        ServerPacketWriter uiWriter=
            new ServerPacketWriter(
                uiOut,
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );

        PrayerDefinitionRepository.Def thickSkin=
            PrayerDefinitionRepository.byWidget(
                5609
            );

        String prayer=
            player.prayers().click(
                thickSkin,
                player.playerState(),
                uiWriter
            );

        if(!prayer.contains(
                "enabled=true"))
            throw new AssertionError(
                "prayer fixture failed "+
                prayer
            );

        String magic=
            player.magic().switchBook(
                "ancient",
                uiWriter
            );

        if(!magic.contains(
                "ANCIENT"))
            throw new AssertionError(
                "magic fixture failed "+
                magic
            );

        player.playerState()
            .setSpecialEnergy(37);

        PlayerStatusService statuses=
            new PlayerStatusService(
                player
            );

        statuses.apply(
            PlayerStatusState.Type.POISON,
            7,
            20L,
            5L,
            "TEST_STATUS"
        );

        CombatSystemHooks hooks=
            CombatSystemHooks.forPlayer(
                player
            );

        CombatSystemHooks.Snapshot snapshot=
            hooks.beforeDamage(
                CombatContext.NPC_PVM,
                21566,
                10L
            );

        if(snapshot.activePrayerCount!=1)
            throw new AssertionError(
                "prayer state not exposed "+
                snapshot
            );

        if(!"NORMAL".equals(
                snapshot.prayerBook))
            throw new AssertionError(
                "prayer book not exposed "+
                snapshot
            );

        if(!"ANCIENT".equals(
                snapshot.magicBook))
            throw new AssertionError(
                "magic book not exposed "+
                snapshot
            );

        if(snapshot.specialEnergy!=37)
            throw new AssertionError(
                "special energy not exposed "+
                snapshot
            );

        if(snapshot.statuses.size()!=1||
           snapshot.statuses.get(0).type!=
                PlayerStatusState.Type.POISON)
            throw new AssertionError(
                "status state not exposed "+
                snapshot
            );

        if(snapshot.anyModifierApplied())
            throw new AssertionError(
                "unknown combat mechanics were applied "+
                snapshot
            );

        if(!"UNKNOWN_SERVER_AUTHORITY".equals(
                snapshot.prayerAuthority)||
           !"UNKNOWN_SERVER_AUTHORITY".equals(
                snapshot.magicAuthority)||
           !"UNKNOWN_SERVER_AUTHORITY".equals(
                snapshot.specialAuthority)||
           !"UNKNOWN_SERVER_AUTHORITY".equals(
                snapshot.statusAuthority))
            throw new AssertionError(
                "unknown-system provenance changed "+
                snapshot
            );

        testEngineIntegration(
            player,
            hooks
        );

        System.out.println(
            "COMBAT_SYSTEM_HOOKS_PASS "+
            "prayerStateVisible=true "+
            "magicStateVisible=true "+
            "specialEnergyVisible=true "+
            "statusStateVisible=true "+
            "unknownMechanicsApplied=false"
        );
    }

    private static void testEngineIntegration(
        WorldPlayer player,
        CombatSystemHooks hooks
    )throws Exception{
        CombatEngine combat=
            new CombatEngine(
                player.combatState(),
                new DevAuthorityWorkbench(),
                CombatDamageRules.localLabFallback(),
                hooks
            );

        MovementState movement=
            player.movement();

        EquipmentState equipment=
            player.equipment();
        equipment.setWeapon(21566);

        NpcRegistry npcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        ByteArrayOutputStream out=
            new ByteArrayOutputStream();

        ServerPacketWriter writer=
            new ServerPacketWriter(
                out,
                new IsaacCipher(
                    new int[]{4,3,2,1}
                )
            );

        NpcEntity target=
            npcs.spawnMirroredNpc(
                CombatTargetRepository.PVM_DUMMY_DEF,
                movement.x()+1,
                movement.y(),
                null,
                movement,
                writer
            );

        String request=
            combat.request(
                target,
                movement,
                equipment.weapon(),
                1000L
            );

        if(!request.startsWith(
                "TARGET_ACQUIRED"))
            throw new AssertionError(
                "target fixture failed "+
                request
            );

        String attack=
            combat.tick(
                movement,
                npcs,
                equipment,
                writer,
                10L,
                null,
                null
            );

        if(attack==null||
           !attack.startsWith(
                "M2_ATTACK_SENT"))
            throw new AssertionError(
                "combat hook integration attack missing "+
                attack
            );

        if(!attack.contains(
                "systemHooks=CombatSystemHooks{")||
           !attack.contains(
                "prayers=1@NORMAL")||
           !attack.contains(
                "magic=ANCIENT")||
           !attack.contains(
                "specialEnergy=37")||
           !attack.contains(
                "statuses=1")||
           !attack.contains(
                "modifierApplied=false")||
           !attack.contains(
                "UNKNOWN_SERVER_AUTHORITY"))
            throw new AssertionError(
                "combat hook snapshot missing from attack "+
                attack
            );

        if(combat.consumeLastDamage()!=10)
            throw new AssertionError(
                "hooks changed LocalLab fallback damage"
            );
    }
}
