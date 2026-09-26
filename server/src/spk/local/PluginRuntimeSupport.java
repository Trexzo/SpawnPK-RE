package spk.local;

import spk.plugin.api.Plugin;

/** Loader-neutral lifecycle access for managed plugin runtimes. */
final class PluginRuntimeSupport {
    static ClassLoader callbackClassLoader(
        Plugin plugin
    ){
        if(plugin==null)
            return PluginRuntimeSupport.class
                .getClassLoader();

        if(plugin instanceof PluginRuntime)
            return ((PluginRuntime)plugin)
                .callbackClassLoader();

        return plugin.getClass()
            .getClassLoader();
    }

    static Throwable closePluginRuntime(
        Plugin plugin,
        Throwable primary
    ){
        if(!(plugin instanceof PluginRuntime))
            return null;

        try{
            ((PluginRuntime)plugin).close();
            return null;
        }catch(Throwable cleanup){
            if(primary!=null)
                primary.addSuppressed(
                    cleanup
                );
            else
                System.err.println(
                    "[plugins] runtime close failed errorClass="+
                    cleanup.getClass()
                        .getName()
                );
            return cleanup;
        }
    }

    private PluginRuntimeSupport(){}
}
