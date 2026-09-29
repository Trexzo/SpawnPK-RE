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
                suppressIfDistinct(
                    primary,
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

    static void suppressIfDistinct(
        Throwable primary,
        Throwable cleanup
    ){
        if(primary!=null&&
           cleanup!=null&&
           primary!=cleanup)
            primary.addSuppressed(
                cleanup
            );
    }

    static void transferSuppressedDistinct(
        Throwable source,
        Throwable target
    ){
        if(source==null||
           target==null||
           source==target)
            return;

        for(Throwable cleanup:
                source.getSuppressed()){
            if(cleanup==null||
               cleanup==target||
               alreadySuppressedByIdentity(
                   target,
                   cleanup
               ))
                continue;

            target.addSuppressed(
                cleanup
            );
        }
    }

    private static boolean alreadySuppressedByIdentity(
        Throwable primary,
        Throwable cleanup
    ){
        for(Throwable existing:
                primary.getSuppressed())
            if(existing==cleanup)
                return true;

        return false;
    }

    private PluginRuntimeSupport(){}
}
