package spk.local;

import java.io.*;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;

public final class ContentRegistryTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(20L);
        WorldPlayer player=new WorldPlayer();

        try{
            ContentRegistry registry=
                world.content();

            ContentRegistry.BindingInfo nurse=
                registry.commandBinding(
                    "nurse"
                );

            if(nurse==null||
               !"locallab-core".equals(
                    nurse.moduleId
               )||
               nurse.priority!=100||
               nurse.provenance!=
                    ContentProvenance.CUSTOM_LOCALLAB)
                throw new AssertionError(
                    "builtin nurse metadata "+
                    nurse
                );

            registry.installCustom(
                module(
                    "low-priority",
                    "priorityproof",
                    10,
                    "LOW"
                )
            );
            registry.installCustom(
                module(
                    "high-priority",
                    "priorityproof",
                    20,
                    "HIGH"
                )
            );
            registry.installCustom(
                module(
                    "ignored-lower",
                    "priorityproof",
                    5,
                    "IGNORED"
                )
            );

            ContentRegistry.BindingInfo selected=
                registry.commandBinding(
                    "priorityproof"
                );

            if(selected==null||
               !"high-priority".equals(
                    selected.moduleId
               )||
               selected.priority!=20)
                throw new AssertionError(
                    "priority selection "+
                    selected
                );

            boolean conflict=false;
            try{
                registry.installCustom(
                    module(
                        "equal-priority",
                        "priorityproof",
                        20,
                        "CONFLICT"
                    )
                );
            }catch(IllegalStateException expected){
                conflict=
                    expected.getMessage()
                        .contains(
                            "content binding conflict"
                        );
            }

            if(!conflict)
                throw new AssertionError(
                    "equal priority conflict accepted"
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

            boolean offThreadRejected=false;
            try{
                registry.dispatchCommand(
                    player,
                    "::priorityproof",
                    writer
                );
            }catch(IllegalStateException expected){
                offThreadRejected=
                    expected.getMessage()
                        .contains(
                            "World execution context"
                        );
            }

            if(!offThreadRejected)
                throw new AssertionError(
                    "off-thread content dispatch accepted"
                );

            world.registerPlayer(
                player,
                "testprofile"
            );
            world.start();

            AtomicReference<ContentResult> result=
                new AtomicReference<>();

            world.submitAndWait(
                player,
                ()->result.set(
                    registry.dispatchCommand(
                        player,
                        "::priorityproof arg1 arg2",
                        writer
                    )
                ),
                5_000L
            );

            if(result.get()==null||
               !"HIGH".equals(
                    result.get().logText()
               ))
                throw new AssertionError(
                    "selected handler did not run "+
                    result.get()
                );

            String diagnostics=
                registry.summary();

            if(!diagnostics.contains(
                    "module=locallab-core")||
               !diagnostics.contains(
                    "provenance=CUSTOM_LOCALLAB")||
               !diagnostics.contains(
                    "module=high-priority"))
                throw new AssertionError(
                    "provenance not queryable "+
                    diagnostics
                );

            System.out.println(
                "CONTENT_REGISTRY_PASS "+
                "staticModule=true "+
                "priorityWinner=high-priority "+
                "equalPriorityConflict=true "+
                "offThreadRejected=true "+
                "provenanceQueryable=true"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(player);
            world.close();
        }
    }

    private static ContentModule module(
        String id,
        String command,
        int priority,
        String result
    ){
        return new ContentModule(){
            @Override public String id(){
                return id;
            }

            @Override public void register(
                ContentRegistrar registrar
            ){
                registrar.command(
                    command,
                    priority,
                    context->
                        ContentResult.handled(
                            result,
                            null
                        )
                );
            }
        };
    }
}
