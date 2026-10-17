package spk.local;

import java.nio.file.Path;
import java.nio.file.Paths;
import spk.plugin.api.Plugin;
import spk.plugin.api.PluginManifest;

public final class PluginLoaderAbstractionTest {
    private static final String ENTRYPOINT=
        "fixture.plugin.IsolationPlugin";

    public static void main(
        String[] args
    )throws Exception{
        if(args.length!=1)
            throw new IllegalArgumentException(
                "expected isolation fixture JAR path"
            );

        Path jar=
            Paths.get(args[0])
                .toAbsolutePath()
                .normalize();

        PluginLoader loader=
            PluginJarLoader.instance();
        PluginSource source=
            PluginSource.of(
                jar,
                ENTRYPOINT
            );

        if(!loader.supports(source))
            throw new AssertionError(
                "JAR loader rejected JAR source"
            );

        PluginRuntime runtime=
            loader.load(source);
        Plugin plugin=runtime;

        if(!(plugin instanceof
                PluginJarLoader.LoadedPlugin))
            throw new AssertionError(
                "loader abstraction changed JAR runtime wrapper"
            );

        PluginJarLoader.LoadedPlugin loaded=
            (PluginJarLoader.LoadedPlugin)plugin;

        try{
            PluginManifest manifest=
                loaded.manifest();

            if(!"isolation.a".equals(
                    manifest.id()))
                throw new AssertionError(
                    "fixture manifest mismatch: "+
                    manifest.id()
                );

            if(loaded.classLoader()==null)
                throw new AssertionError(
                    "JAR loader lost isolated classloader"
                );
            if(runtime.callbackClassLoader()!=
                    loaded.classLoader())
                throw new AssertionError(
                    "generic runtime lost callback classloader identity"
                );

            if(PluginRuntimeSupport
                    .callbackClassLoader(
                        plugin
                    )!=
                    loaded.classLoader())
                throw new AssertionError(
                    "runtime support lost callback classloader identity"
                );

            if(!loaded.source().equals(jar))
                throw new AssertionError(
                    "normalized source mismatch expected="+
                    jar+
                    " actual="+
                    loaded.source()
                );
        }finally{
            Throwable closeFailure=
                PluginRuntimeSupport.closePluginRuntime(
                    runtime,
                    null
                );

            if(closeFailure!=null)
                throw new AssertionError(
                    "JAR loader close failed",
                    closeFailure
                );
        }

        if(!loaded.closed())
            throw new AssertionError(
                "JAR loader abstraction did not preserve close ownership"
            );

        PluginSource script=
            PluginSource.script(
                jar.resolveSibling(
                    "example.kts"
                )
            );

        if(loader.supports(script))
            throw new AssertionError(
                "JAR loader claimed Kotlin script source"
            );

        boolean scriptRejected=false;

        try{
            loader.load(script);
        }catch(IllegalArgumentException expected){
            scriptRejected=true;
        }

        if(!scriptRejected)
            throw new AssertionError(
                "JAR loader accepted Kotlin script source"
            );

        PluginSource missingEntrypoint=
            PluginSource.of(
                jar,
                null
            );

        if(loader.supports(
                missingEntrypoint))
            throw new AssertionError(
                "JAR loader accepted missing entrypoint"
            );

        System.out.println(
            "PLUGIN_LOADER_ABSTRACTION_PASS "+
            "jarAdapter=true "+
            "staticCompatibility=true "+
            "isolatedWrapper=true "+
            "genericRuntimeOwnership=true "+
            "closeOwnership=true "+
            "scriptReservedForKotlinLoader=true"
        );
    }

    private PluginLoaderAbstractionTest(){}
}
