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

        if(p[0].equalsIgnoreCase("combatprobe")){
            CombatWeaponProfile profile=
                CombatWeaponRepository.resolve(equipment.weapon());
            int combatRoot=
                CombatInterfaceRepository.forWeapon(equipment.weapon());

            return one(
                "V56_COMBAT_PROBE weapon="+equipment.weapon()+
                " profile="+profile+
                " style={"+combatStyles.summary(combatRoot)+"}"+
                " targetScene="+combat.state().targetSceneIndex+
                " targetDef="+combat.state().targetDefinitionId+
                " context="+combat.state().context+
                " formula=UNRESOLVED_NO_DAMAGE_GUESS");
        }

        if(p[0].equalsIgnoreCase("combatfixture")){
            int damage=p.length>=2?parseInt(p[1],0):0;
            String fixture=combat.fixtureHit(
                damage,npcs,serverPackets);
            int dealt=combat.consumeLastDamage();

            ArrayList<String> lines=new ArrayList<>();
            if(dealt>0){
                String petDamage=petRuntime.applyDamage(
                    dealt,
                    System.currentTimeMillis(),
                    serverPackets,
                    "COMBAT_FIXTURE");
                if(petDamage!=null)lines.add(petDamage);
            }

            lines.add(
                "V59_COMBAT_FIXTURE command="+rawCommand+
                " result="+fixture);

            return Collections.unmodifiableList(lines);
        }

        return null;
    }

    private static List<String> one(String line){
        return Collections.singletonList(line);
    }

    private static int parseInt(String value,int fallback){
        try{
            return Integer.parseInt(value);
        }catch(Exception e){
            return fallback;
        }
    }
}
