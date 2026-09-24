package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalDiagnosticCommandHandlerTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(
                50L
            );

        try{
            WorldPlayer player=
                new WorldPlayer();

            LocalDiagnosticCommandHandler h=
                new LocalDiagnosticCommandHandler(
                    world,
                    player.equipment(),
                    new NativeItemLibraryService()
                );

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();

            ServerPacketWriter w=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(
                        new int[]{1,2,3,4}
                    )
                );

            String[][] migrated={
                {"worldauth"},
                {"collisionauth"},
                {"prayerinfo"},
                {"magicinfo"},
                {"styleinfo"},
                {"combatprobe"},
                {"engine"},
                {"equipstr","-1"}
            };

            for(String[] command:migrated)
                if(h.handle(
                        command,
                        w,
                        "[diag-test] ",
                        "opensrc",
                        "localtest",
                        true,
                        null))
                    throw new AssertionError(
                        "migrated diagnostic still handled "+
                        java.util.Arrays.toString(
                            command
                        )
                    );

            int before=
                wire.size();

            String equip=
                h.equipStr(
                    -1,
                    w
                );

            if(!equip.contains(
                    "V5181_EQUIPSTR_FAIL_CLOSED item=-1 known=false")||
               !equip.contains(
                    "numeric14=UNRESOLVED_SERVER_AUTHORITY"))
                throw new AssertionError(
                    "equipstr effect="+equip
                );

            if(wire.size()<=before)
                throw new AssertionError(
                    "equipstr effect did not emit fail-closed reset packet"
                );

            if(h.handle(
                    new String[]{
                        "regionload",
                        "12850"
                    },
                    w,
                    "[diag-test] ",
                    "opensrc",
                    "localtest",
                    true,
                    null))
                throw new AssertionError(
                    "mutating regionload must stay outside diagnostic handler"
                );

            String engine=
                h.engineSummary(
                    "opensrc",
                    "localtest",
                    true,
                    null
                );

            if(!engine.startsWith(
                    "V5123_ENGINE ")||
               !engine.contains(
                    "account=opensrc")||
               !engine.contains(
                    "loginAlias=localtest")||
               !engine.contains(
                    "persistent=true")||
               !engine.contains(
                    "sceneBase=none"))
                throw new AssertionError(
                    "engine effect="+engine
                );

            System.out.println(
                "LOCAL_DIAGNOSTIC_COMMAND_HANDLER_PASS "+
                "migratedRoutesRejected=true "+
                "equipstrRuntimeEffect=true "+
                "engineEffect=true "+
                "mutatingCommandRejected=true"
            );
        }finally{
            world.close();
        }
    }
}
