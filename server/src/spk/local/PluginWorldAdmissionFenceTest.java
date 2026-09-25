package spk.local;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import spk.plugin.api.Plugin;
import spk.plugin.api.PluginApiVersion;
import spk.plugin.api.PluginContext;
import spk.plugin.api.PluginManifest;

public final class PluginWorldAdmissionFenceTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(600L);

        AtomicBoolean worldOpen=
            new AtomicBoolean(true);

        WorldPluginManager manager=
            new WorldPluginManager(
                world.content(),
                world.domainEvents(),
                world.clock(),
                world.events(),
                worldOpen::get,
                ()->true
            );

        AtomicInteger acceptedCalls=
            new AtomicInteger();
        AtomicInteger rejectedCalls=
            new AtomicInteger();

        Plugin accepted=
            plugin(
                "admission.accepted",
                acceptedCalls
            );

        Plugin rejected=
            plugin(
                "admission.rejected",
                rejectedCalls
            );

        try{
            manager.enable(accepted);

            if(acceptedCalls.get()!=1)
                throw new AssertionError(
                    "open admission did not execute plugin exactly once"
                );

            if(!manager.disable(
                    "admission.accepted"
                ))
                throw new AssertionError(
                    "accepted plugin did not disable"
                );

            worldOpen.set(false);

            boolean rejectedBeforeCode=false;

            try{
                manager.enable(rejected);
            }catch(IllegalStateException expected){
                rejectedBeforeCode=
                    "plugin manager closed".equals(
                        expected.getMessage()
                    );
            }

            if(!rejectedBeforeCode)
                throw new AssertionError(
                    "closed World admission was not rejected"
                );

            if(rejectedCalls.get()!=0)
                throw new AssertionError(
                    "plugin code executed after World admission closed"
                );

            if(manager.plugin(
                    "admission.rejected"
                )!=null)
                throw new AssertionError(
                    "rejected plugin retained enabled state"
                );

            if(!manager.enabled().isEmpty())
                throw new AssertionError(
                    "manager retained plugin after admission rejection"
                );

            manager.beginClose();
            manager.closeResources();

            System.out.println(
                "PLUGIN_WORLD_ADMISSION_FENCE_PASS "+
                "openEnableWorked=true "+
                "worldCloseRejectedBeforePluginCode=true "+
                "rejectedPluginNotRetained=true"
            );
        }finally{
            manager.close();
            world.close();
        }
    }

    private static Plugin plugin(
        String id,
        AtomicInteger enableCalls
    ){
        return new Plugin(){
            private final PluginManifest manifest=
                new PluginManifest(
                    id,
                    "1.0.0",
                    PluginApiVersion.CURRENT,
                    Collections.<String>emptyList()
                );

            @Override public PluginManifest manifest(){
                return manifest;
            }

            @Override public void enable(
                PluginContext context
            ){
                enableCalls.incrementAndGet();
            }
        };
    }

    private PluginWorldAdmissionFenceTest(){}
}
