package spk.local;

import java.io.*;

public final class CombatDamageRulesSeparationTest {
    public static void main(String[] args)throws Exception{
        int custom=
            attackOnce(
                new CombatEngine(
                    new CombatState(),
                    new DevAuthorityWorkbench(),
                    CombatDamageRules.localLabFallback()
                ),
                "CUSTOM_LOCALLAB_FLAT_10_V1",
                "CUSTOM_LOCALLAB"
            );

        if(custom!=10)
            throw new AssertionError(
                "custom LocalLab normal damage changed: "+
                custom
            );

        int fixture=
            attackOnce(
                new CombatEngine(
                    new CombatState(),
                    new DevAuthorityWorkbench(),
                    CombatDamageRules.dummyFixture()
                ),
                "LOCAL_M2_FIXED_DUMMY_HIT",
                "LOCAL_DEV_FIXTURE"
            );

        if(fixture!=200)
            throw new AssertionError(
                "historical PvM fixture changed: "+
                fixture
            );

        if(custom==fixture)
            throw new AssertionError(
                "production fallback still aliases dummy fixture"
            );

        if(!"CUSTOM_LOCALLAB".equals(
                CombatDamageRules.localLabFallback()
                    .authority()))
            throw new AssertionError(
                "LocalLab fallback provenance changed"
            );

        if(!"LOCAL_DEV_FIXTURE".equals(
                CombatDamageRules.dummyFixture()
                    .authority()))
            throw new AssertionError(
                "dummy fixture provenance changed"
            );

        System.out.println(
            "COMBAT_DAMAGE_RULES_SEPARATION_PASS "+
            "normalDamage="+custom+
            " normalFormula=CUSTOM_LOCALLAB_FLAT_10_V1 "+
            " fixtureDamage="+fixture+
            " fixtureFormula=LOCAL_M2_FIXED_DUMMY_HIT "+
            " sharedAttackPresentation=true"
        );
    }

    private static int attackOnce(
        CombatEngine combat,
        String expectedFormula,
        String expectedAuthority
    )throws Exception{
        MovementState movement=
            new MovementState();
        EquipmentState equipment=
            new EquipmentState();
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
                1489,
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
                "target not acquired: "+
                request
            );

        if(!request.contains(
                "formula="+expectedFormula)||
           !request.contains(
                "damageAuthority="+
                expectedAuthority))
            throw new AssertionError(
                "request provenance mismatch: "+
                request
            );

        String attack=
            combat.tick(
                movement,
                npcs,
                equipment,
                writer,
                1L,
                null,
                null
            );

        if(attack==null||
           !attack.startsWith(
                "M2_ATTACK_SENT"))
            throw new AssertionError(
                "attack not emitted: "+
                attack
            );

        if(!attack.contains(
                "damageFormula="+expectedFormula)||
           !attack.contains(
                "damageAuthority="+
                expectedAuthority))
            throw new AssertionError(
                "attack damage provenance mismatch: "+
                attack
            );

        int damage=
            combat.consumeLastDamage();

        if(damage<=0)
            throw new AssertionError(
                "attack applied no damage"
            );

        return damage;
    }
}
