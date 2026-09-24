package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.List;

public final class LocalCombatCommandHandlerTest {
    public static void main(String[] args)throws Exception{
        DevAuthorityWorkbench dev=
            new DevAuthorityWorkbench();
        WorldPlayer player=
            new WorldPlayer();
        NpcRegistry npcs=
            new NpcRegistry(dev);
        CombatEngine combat=
            new CombatEngine(dev);

        LocalPetRuntimeCommandHandler petRuntime=
            new LocalPetRuntimeCommandHandler(
                player.petState(),
                player.petEffects(),
                npcs,
                player.movement()
            );

        LocalCombatCommandHandler handler=
            new LocalCombatCommandHandler(
                combat,
                player.equipment(),
                player.combatStyles(),
                npcs,
                petRuntime
            );

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        ServerPacketWriter writer=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );

        String info=
            handler.devHitInfo();

        if(info==null||
           !info.contains(
               "V5128_type=6"))
            throw new AssertionError(
                "devhit info="+info
            );

        String manual=
            handler.devHitVariant(
                false
            );

        if(!manual.contains(
                "variantMode=manual"))
            throw new AssertionError(
                "devhit variant="+manual
            );

        String type=
            handler.devHitType(
                4
            );

        if(!type.contains(
                "type=4"))
            throw new AssertionError(
                "devhit type="+type
            );

        List<String> fixture=
            handler.fixture(
                37,
                "combatfixture 37",
                writer
            );

        if(fixture==null||
           fixture.size()!=1||
           !fixture.get(0).contains(
               "V59_COMBAT_FIXTURE command=combatfixture 37")||
           !fixture.get(0).contains(
               "REJECTED_NO_SELECTED_TARGET")){
            throw new AssertionError(
                "fixture effect lines="+fixture
            );
        }

        for(Method method:
                LocalCombatCommandHandler.class
                    .getDeclaredMethods())
            if("handle".equals(
                    method.getName()))
                throw new AssertionError(
                    "raw combat command parser remains"
                );

        System.out.println(
            "LOCAL_COMBAT_COMMAND_HANDLER_PASS "+
            "devhitEffects=true "+
            "fixtureEffect=true "+
            "rawParserAbsent=true"
        );
    }
}
