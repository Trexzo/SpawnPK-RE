package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.List;

public final class LocalCombatCommandHandlerTest {
    public static void main(String[] args)throws Exception{
        DevAuthorityWorkbench dev=new DevAuthorityWorkbench();
        WorldPlayer player=new WorldPlayer();
        NpcRegistry npcs=new NpcRegistry(dev);
        CombatEngine combat=new CombatEngine(dev);

        LocalPetRuntimeCommandHandler petRuntime=
            new LocalPetRuntimeCommandHandler(
                player.petState(),
                player.petEffects(),
                npcs,
                player.movement());

        LocalCombatCommandHandler handler=
            new LocalCombatCommandHandler(
                combat,
                player.equipment(),
                player.combatStyles(),
                npcs,
                petRuntime);

        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter writer=new ServerPacketWriter(
            wire,new IsaacCipher(new int[]{1,2,3,4}));

        List<String> devhit=handler.handle(
            new String[]{"devhit","info"},
            "devhit info",
            writer);

        assertOneContains(
            devhit,
            "V5128_type=6",
            "devhit");

        List<String> probe=handler.handle(
            new String[]{"combatprobe"},
            "combatprobe",
            writer);

        if(probe!=null)
            throw new AssertionError(
                "legacy combatprobe runtime route remains "+
                probe
            );

        List<String> fixture=handler.handle(
            new String[]{"combatfixture","37"},
            "combatfixture 37",
            writer);

        if(fixture==null||
           fixture.size()!=1||
           !fixture.get(0).contains(
               "V59_COMBAT_FIXTURE command=combatfixture 37")||
           !fixture.get(0).contains(
               "REJECTED_NO_SELECTED_TARGET")){
            throw new AssertionError(
                "fixture lines="+fixture);
        }

        if(handler.handle(
            new String[]{"petstatus"},
            "petstatus",
            writer)!=null){
            throw new AssertionError(
                "unrelated command consumed");
        }

        System.out.println(
            "LOCAL_COMBAT_COMMAND_HANDLER_PASS devhit=true probeLegacyRoute=false fixtureBoundary=true unrelatedRejected=true");
    }

    private static void assertOneContains(
        List<String> lines,
        String expected,
        String label
    ){
        if(lines==null||
           lines.size()!=1||
           !lines.get(0).contains(expected)){
            throw new AssertionError(
                label+" lines="+lines+
                " expected="+expected);
        }
    }
}
