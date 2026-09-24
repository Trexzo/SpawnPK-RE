package spk.local;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * LocalLab combat developer command adapter.
 *
 * This centralizes inspection/fixture commands only. It does not reconstruct
 * or invent SpawnPK combat formulas; CombatEngine's current fixture authority
 * remains unchanged.
 */
final class LocalCombatCommandHandler {
    private final CombatEngine combat;
    private final EquipmentState equipment;
    private final CombatStyleState combatStyles;
    private final NpcRegistry npcs;
    private final LocalPetRuntimeCommandHandler petRuntime;

    LocalCombatCommandHandler(
        CombatEngine combat,
        EquipmentState equipment,
        CombatStyleState combatStyles,
        NpcRegistry npcs,
        LocalPetRuntimeCommandHandler petRuntime
    ){
        this.combat=java.util.Objects.requireNonNull(combat,"combat");
        this.equipment=java.util.Objects.requireNonNull(equipment,"equipment");
        this.combatStyles=java.util.Objects.requireNonNull(combatStyles,"combatStyles");
        this.npcs=java.util.Objects.requireNonNull(npcs,"npcs");
        this.petRuntime=java.util.Objects.requireNonNull(petRuntime,"petRuntime");
    }

    List<String> handle(
        String[] p,
        String rawCommand,
        ServerPacketWriter serverPackets
    )throws IOException{
        if(p==null||p.length<1)return null;

        if(p[0].equalsIgnoreCase("devhit")){
            return one("V5128_"+combat.devHitCommand(p));
        }

        return null;
    }

    List<String> fixture(
        int damage,
        String rawCommand,
        ServerPacketWriter serverPackets
    )throws IOException{
        String fixture=
            combat.fixtureHit(
                damage,
                npcs,
                serverPackets
            );
        int dealt=
            combat.consumeLastDamage();

        ArrayList<String> lines=
            new ArrayList<>();

        if(dealt>0){
            String petDamage=
                petRuntime.applyDamage(
                    dealt,
                    System.currentTimeMillis(),
                    serverPackets,
                    "COMBAT_FIXTURE"
                );

            if(petDamage!=null)
                lines.add(
                    petDamage
                );
        }

        lines.add(
            "V59_COMBAT_FIXTURE command="+
                rawCommand+
                " result="+
                fixture
        );

        return Collections.unmodifiableList(
            lines
        );
    }

    private static List<String> one(String line){
        return Collections.singletonList(line);
    }

}
