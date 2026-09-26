package spk.local;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import spk.plugin.api.Plugin;
import spk.plugin.api.PluginContext;
import spk.plugin.api.PluginManifest;

final class PluginJarLoader implements PluginLoader {
    private static final PluginJarLoader INSTANCE=
        new PluginJarLoader();
    private static final String RESERVED_SERVER_ENTRY_PREFIX=
        "spk/";

    static PluginJarLoader instance(){
        return INSTANCE;
    }

    @Override public boolean supports(
        PluginSource source
    ){
        if(source==null||
           !source.hasEntrypoint())
            return false;

        Path fileName=
            source.path()
                .getFileName();

        return fileName!=null&&
            fileName.toString()
                .toLowerCase(
                    java.util.Locale.ROOT
                ).endsWith(
                    ".jar"
                );
    }

    @Override public Plugin load(
        PluginSource source
    )throws Exception{
        Objects.requireNonNull(
            source,
            "source"
        );

        if(!supports(source))
            throw new IllegalArgumentException(
                "unsupported JAR plugin source: "+
                source.path()
            );

        return load(
            source.path(),
            source.requireEntrypoint()
        );
    }
    static LoadedPlugin load(
        Path jar,
        String entrypoint
    )throws Exception{
        return load(
            jar,
            entrypoint,
            Plugin.class.getClassLoader()
        );
    }

    static LoadedPlugin load(
        Path jar,
        String entrypoint,
        ClassLoader parent
    )throws Exception{
        Path path=
            Objects.requireNonNull(
                jar,
                "jar"
            ).toAbsolutePath()
                .normalize();
        String main=
            requireEntrypoint(
                entrypoint
            );

        validateArchive(
            path,
            main
        );

        IsolatedPluginClassLoader loader=
            new IsolatedPluginClassLoader(
                path.toUri().toURL(),
                Objects.requireNonNull(
                    parent,
                    "parent"
                )
            );

        try{
            Plugin delegate=
                PluginThreadContext.call(
                    loader,
                    ()->{
                        Class<?> type=
                            Class.forName(
                                main,
                                true,
                                loader
                            );

                        if(!Plugin.class
                                .isAssignableFrom(
                                    type
                                ))
                            throw new IllegalArgumentException(
                                "plugin entrypoint does not implement Plugin: "+
                                main
                            );

                        Constructor<?> constructor=
                            type.getDeclaredConstructor();

                        if(!Modifier.isPublic(
                                type.getModifiers())||
                           !Modifier.isPublic(
                                constructor
                                    .getModifiers()))
                            throw new IllegalArgumentException(
                                "plugin entrypoint and no-arg constructor must be public: "+
                                main
                            );

                        return (Plugin)
                            constructor.newInstance();
                    }
                );

            return new LoadedPlugin(
                delegate,
                loader,
                path,
                main
            );
        }catch(Throwable failure){
            try{
                loader.close();
            }catch(Throwable cleanup){
                failure.addSuppressed(
                    cleanup
                );
            }

            rethrow(failure);
            throw new AssertionError(
                "unreachable"
            );
        }
    }

    static ClassLoader callbackClassLoader(
        Plugin plugin
    ){
        if(plugin==null)
            return PluginJarLoader.class
                .getClassLoader();

        if(plugin instanceof LoadedPlugin)
            return ((LoadedPlugin)plugin)
                .classLoader();

        return plugin.getClass()
            .getClassLoader();
    }

    static Throwable closePluginRuntime(
        Plugin plugin,
        Throwable primary
    ){
        if(!(plugin instanceof LoadedPlugin))
            return null;

        try{
            ((LoadedPlugin)plugin)
                .close();
            return null;
        }catch(Throwable cleanup){
            if(primary!=null)
                primary.addSuppressed(
                    cleanup
                );
            else
                System.err.println(
                    "[plugins] classloader close failed errorClass="+
                    cleanup.getClass()
                        .getName()
                );
            return cleanup;
        }
    }

    private static void validateArchive(
        Path jar,
        String entrypoint
    )throws IOException{
        if(!Files.isRegularFile(jar))
            throw new IllegalArgumentException(
                "plugin JAR missing: "+
                jar
            );

        String entrypointClass=
            entrypoint.replace(
                '.',
                '/'
            )+
            ".class";
        boolean entrypointPresent=false;

        try(JarFile file=
                new JarFile(
                    jar.toFile()
                )){
            Enumeration<JarEntry> entries=
                file.entries();

            while(entries.hasMoreElements()){
                JarEntry entry=
                    entries.nextElement();

                if(entry.isDirectory())
                    continue;

                String name=
                    entry.getName()
                        .replace(
                            '\\',
                            '/'
                        );

                if(name.equals(
                        entrypointClass))
                    entrypointPresent=true;

                if(name.startsWith(
                        "META-INF/versions/")&&
                   name.endsWith(
                        ".class"))
                    throw new IllegalArgumentException(
                        "multi-release plugin classes are unsupported: "+
                        name
                    );

                if(name.startsWith(
                        RESERVED_SERVER_ENTRY_PREFIX))
                    throw new IllegalArgumentException(
                        "plugin JAR defines reserved server namespace: "+
                        name
                    );
            }
        }

        if(!entrypointPresent)
            throw new IllegalArgumentException(
                "plugin entrypoint class missing: "+
                entrypoint
            );
    }

    private static String requireEntrypoint(
        String value
    ){
        String clean=
            value==null
                ?""
                :value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                "plugin entrypoint"
            );

        if(clean.startsWith(
                "spk."))
            throw new IllegalArgumentException(
                "plugin entrypoint uses reserved server namespace: "+
                clean
            );

        return clean;
    }

    private static void rethrow(
        Throwable failure
    )throws Exception{
        if(failure instanceof Exception)
            throw (Exception)failure;

        if(failure instanceof Error)
            throw (Error)failure;

        throw new RuntimeException(
            failure
        );
    }

    static final class LoadedPlugin
        implements Plugin,AutoCloseable {

        private final Plugin delegate;
        private final IsolatedPluginClassLoader loader;
        private final Path source;
        private final String entrypoint;
        private volatile boolean closed;

        LoadedPlugin(
            Plugin delegate,
            IsolatedPluginClassLoader loader,
            Path source,
            String entrypoint
        ){
            this.delegate=
                Objects.requireNonNull(
                    delegate,
                    "delegate"
                );
            this.loader=
                Objects.requireNonNull(
                    loader,
                    "loader"
                );
            this.source=
                source;
            this.entrypoint=
                entrypoint;
        }

        @Override public PluginManifest manifest(){
            requireOpen();

            return PluginThreadContext
                .callUnchecked(
                    loader,
                    delegate::manifest
                );
        }

        @Override public void enable(
            PluginContext context
        )throws Exception{
            requireOpen();

            PluginThreadContext.run(
                loader,
                ()->delegate.enable(
                    context
                )
            );
        }

        @Override public void disable()
            throws Exception{
            requireOpen();

            PluginThreadContext.run(
                loader,
                delegate::disable
            );
        }

        Plugin delegate(){
            return delegate;
        }

        ClassLoader classLoader(){
            return loader;
        }

        Path source(){
            return source;
        }

        String entrypoint(){
            return entrypoint;
        }

        boolean closed(){
            return closed;
        }

        private void requireOpen(){
            if(closed)
                throw new IllegalStateException(
                    "plugin classloader closed: "+
                    entrypoint
                );
        }

        @Override public synchronized void close()
            throws IOException{
            if(closed)
                return;

            closed=true;
            loader.close();
        }
    }

    private static final class
        IsolatedPluginClassLoader
        extends URLClassLoader {

        IsolatedPluginClassLoader(
            URL jar,
            ClassLoader parent
        ){
            super(
                new URL[]{jar},
                parent
            );
        }

        @Override public URL getResource(
            String name
        ){
            if(exportedServerResource(
                    name))
                return parentResource(
                    name
                );

            if(serverResource(name))
                return null;

            URL own=
                findResource(name);

            return own!=null
                ?own
                :parentResource(name);
        }

        @Override public Enumeration<URL> getResources(
            String name
        )throws IOException{
            if(exportedServerResource(
                    name))
                return parentResources(
                    name
                );

            if(serverResource(name))
                return java.util.Collections
                    .emptyEnumeration();

            java.util.List<URL> own=
                java.util.Collections.list(
                    findResources(name)
                );

            return !own.isEmpty()
                ?java.util.Collections
                    .enumeration(own)
                :parentResources(name);
        }

        @Override protected Class<?> loadClass(
            String name,
            boolean resolve
        )throws ClassNotFoundException{
            if(parentOnly(name))
                return loadParentOnly(
                    name
                );

            synchronized(
                getClassLoadingLock(name)
            ){
                Class<?> loaded=
                    findLoadedClass(name);

                if(loaded==null)
                    try{
                        loaded=findClass(name);
                    }catch(ClassNotFoundException childMiss){
                        if(name.startsWith(
                                "spk."))
                            throw new ClassNotFoundException(
                                "server namespace is not exported to plugins: "+
                                name,
                                childMiss
                            );

                        loaded=super.loadClass(
                            name,
                            false
                        );
                    }

                if(resolve&&
                   loaded.getClassLoader()==
                        this)
                    resolveClass(loaded);

                return loaded;
            }
        }

        private URL parentResource(
            String name
        ){
            ClassLoader parent=
                getParent();

            return parent!=null
                ?parent.getResource(name)
                :ClassLoader.getSystemResource(
                    name
                );
        }

        private Enumeration<URL> parentResources(
            String name
        )throws IOException{
            ClassLoader parent=
                getParent();

            return parent!=null
                ?parent.getResources(name)
                :ClassLoader.getSystemResources(
                    name
                );
        }

        private static boolean exportedServerResource(
            String name
        ){
            return name.startsWith(
                    "spk/plugin/api/")||
                name.startsWith(
                    "spk/content/api/")||
                exportedEventResource(
                    name
                );
        }

        private static boolean exportedEventResource(
            String name
        ){
            return "spk/event/DomainEventBus.class"
                    .equals(name)||
                "spk/event/DomainEventBus$Event.class"
                    .equals(name)||
                "spk/event/DomainEventBus$Cancellable.class"
                    .equals(name)||
                "spk/event/DomainEventBus$Priority.class"
                    .equals(name)||
                "spk/event/DomainEventBus$Listener.class"
                    .equals(name)||
                "spk/event/DomainEventBus$Subscription.class"
                    .equals(name);
        }

        private static boolean serverResource(
            String name
        ){
            return name.startsWith(
                "spk/"
            );
        }

        private Class<?> loadParentOnly(
            String name
        )throws ClassNotFoundException{
            ClassLoader parent=
                getParent();

            if(parent!=null)
                return parent.loadClass(
                    name
                );

            return Class.forName(
                name,
                false,
                null
            );
        }

        private static boolean exportedEventClass(
            String name
        ){
            return "spk.event.DomainEventBus"
                    .equals(name)||
                "spk.event.DomainEventBus$Event"
                    .equals(name)||
                "spk.event.DomainEventBus$Cancellable"
                    .equals(name)||
                "spk.event.DomainEventBus$Priority"
                    .equals(name)||
                "spk.event.DomainEventBus$Listener"
                    .equals(name)||
                "spk.event.DomainEventBus$Subscription"
                    .equals(name);
        }

        private static boolean parentOnly(
            String name
        ){
            return name.startsWith(
                    "java.")||
                name.startsWith(
                    "javax.")||
                name.startsWith(
                    "jdk.")||
                name.startsWith(
                    "sun.")||
                name.startsWith(
                    "com.sun.")||
                name.startsWith(
                    "org.w3c.")||
                name.startsWith(
                    "org.xml.")||
                name.startsWith(
                    "org.ietf.jgss.")||
                name.startsWith(
                    "spk.plugin.api.")||
                name.startsWith(
                    "spk.content.api.")||
                exportedEventClass(
                    name
                );
        }
    }

    private PluginJarLoader(){}
}
