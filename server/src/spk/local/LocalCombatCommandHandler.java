package spk.local;

import java.io.IOException;
import java.util.ArrayList;
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
    private final NpcRegistry npcs;
    private final LocalPetRuntimeCommandHandler petRuntime;

    LocalCombatCommandHandler(
        CombatEngine combat,
        NpcRegistry npcs,
        LocalPetRuntimeCommandHandler petRuntime
    ){
        this.combat=java.util.Objects.requireNonNull(combat,"combat");
        this.npcs=java.util.Objects.requireNonNull(npcs,"npcs");
        this.petRuntime=java.util.Objects.requireNonNull(petRuntime,"petRuntime");
    }

    String devHitInfo(){
        return "V5128_"+
            combat.devHitInfo();
    }

    String devHitReset(){
        return "V5128_"+
            combat.devHitReset();
    }

    String devHitDamageAuto(){
        return "V5128_"+
            combat.devHitDamageAuto();
    }

    String devHitDamage(
        int damage
    ){
        return "V5128_"+
            combat.devHitDamage(
                damage
            );
    }

    String devHitSequenceOff(){
        return "V5128_"+
            combat.devHitSequenceOff();
    }

    String devHitSequence(
        int[] sequence
    ){
        return "V5128_"+
            combat.devHitSequence(
                sequence
            );
    }

    String devHitVariant(
        boolean auto
    ){
        return "V5128_"+
            combat.devHitVariant(
                auto
            );
    }

    String devHitNextType(){
        return "V5128_"+
            combat.devHitNextType();
    }

    String devHitPreviousType(){
        return "V5128_"+
            combat.devHitPreviousType();
    }

    String devHitType(
        int type
    ){
        return "V5128_"+
            combat.devHitType(
                type
            );
    }

    String devHitStyleIcon(
        int styleIcon
    ){
        return "V5128_"+
            combat.devHitStyleIcon(
                styleIcon
            );
    }

    String devHitPlacementPrimary(){
        return "V5128_"+
            combat.devHitPlacementPrimary();
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

        return java.util.Collections.unmodifiableList(
            lines
        );
    }


}
