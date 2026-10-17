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

    @Override public PluginRuntime load(
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
        return load(
            jar,
            entrypoint,
            parent,
            (source,snapshot)->{},
            (source,snapshot)->{}
        );
    }

    static LoadedPlugin load(
        Path jar,
        String entrypoint,
        ClassLoader parent,
        ArchiveAdmissionHook admissionHook
    )throws Exception{
        return load(
            jar,
            entrypoint,
            parent,
            admissionHook,
            (source,snapshot)->{}
        );
    }

    static LoadedPlugin load(
        Path jar,
        String entrypoint,
        ClassLoader parent,
        ArchiveAdmissionHook admissionHook,
        ArchiveSnapshotObserver snapshotObserver
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

        ArchiveSnapshot snapshot=
            ArchiveSnapshot.capture(
                path
            );
        IsolatedPluginClassLoader loader=null;

        try{
            Objects.requireNonNull(
                snapshotObserver,
                "snapshotObserver"
            ).snapshotCreated(
                path,
                snapshot.path()
            );

            validateArchive(
                snapshot.path(),
                main
            );

            Objects.requireNonNull(
                admissionHook,
                "admissionHook"
            ).afterValidation(
                path,
                snapshot.path()
            );

            IsolatedPluginClassLoader openedLoader=
                new IsolatedPluginClassLoader(
                    snapshot.path()
                        .toUri()
                        .toURL(),
                    Objects.requireNonNull(
                        parent,
                        "parent"
                    )
                );
            loader=openedLoader;

            Plugin delegate=
                PluginThreadContext.call(
                    openedLoader,
                    ()->{
                        Class<?> type=
                            Class.forName(
                                main,
                                true,
                                openedLoader
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
                main,
                snapshot
            );
        }catch(Throwable failure){
            if(loader!=null)
                try{
                    loader.close();
                }catch(Throwable cleanup){
                    preserveFailure(
                        failure,
                        cleanup
                    );
                }

            try{
                snapshot.close();
            }catch(Throwable cleanup){
                preserveFailure(
                    failure,
                    cleanup
                );
            }

            rethrow(failure);
            throw new AssertionError(
                "unreachable"
            );
        }
    }

    interface ArchiveAdmissionHook {
        void afterValidation(
            Path source,
            Path snapshot
        )throws Exception;
    }

    interface ArchiveSnapshotObserver {
        void snapshotCreated(
            Path source,
            Path snapshot
        )throws Exception;
    }

    interface ArchiveCaptureHook {
        void afterPrivatePathsCreated(
            Path root,
            Path snapshot
        )throws IOException;
    }

    static void captureArchiveForTest(
        Path source,
        ArchiveCaptureHook hook
    )throws Exception{
        ArchiveSnapshot snapshot=
            ArchiveSnapshot.capture(
                source,
                hook
            );

        snapshot.close();
    }

    static ClassLoader callbackClassLoader(
        Plugin plugin
    ){
        return PluginRuntimeSupport
            .callbackClassLoader(
                plugin
            );
    }

    static Throwable closePluginRuntime(
        Plugin plugin,
        Throwable primary
    ){
        return PluginRuntimeSupport
            .closePluginRuntime(
                plugin,
                primary
            );
    }

    static int archiveCleanupDebtCount(){
        synchronized(
            ArchiveCleanupDebt.DEBTS
        ){
            return ArchiveCleanupDebt
                .DEBTS.size();
        }
    }

    static Throwable retryArchiveCleanupDebtOnce(
        Throwable primary
    ){
        synchronized(
            ArchiveCleanupDebt.DEBTS
        ){
            Throwable aggregate=primary;

            java.util.Iterator<ArchiveCleanupDebt>
                iterator=
                    ArchiveCleanupDebt.DEBTS
                        .iterator();

            while(iterator.hasNext()){
                ArchiveCleanupDebt debt=
                    iterator.next();
                Throwable retry=
                    debt.retry();

                if(retry==null){
                    iterator.remove();
                    continue;
                }

                if(aggregate==null)
                    aggregate=retry;
                else
                    preserveFailure(
                        aggregate,
                        retry
                    );

                System.err.println(
                    "[plugins] Java archive cleanup debt retry failed root="+
                    debt.root+
                    " errorClass="+
                    retry.getClass().getName()
                );
            }

            return aggregate;
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
                    jar.toFile(),
                    false
                )){
            java.util.jar.Attributes mainAttributes=
                BoundedManifestMain
                    .readMainAttributes(
                        file,
                        jar
                    );

            if(mainAttributes!=null){
                String classPath=
                    mainAttributes
                        .getValue(
                            java.util.jar.Attributes
                                .Name.CLASS_PATH
                        );

                if(classPath!=null&&
                   !classPath.trim()
                        .isEmpty())
                    throw new IllegalArgumentException(
                        "plugin JAR manifest Class-Path is forbidden: "+
                        classPath
                    );
            }

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

                if("META-INF/INDEX.LIST"
                        .equals(name))
                    throw new IllegalArgumentException(
                        "plugin JAR index is forbidden: "+
                        name
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

    private static void preserveFailure(
        Throwable primary,
        Throwable cleanup
    ){
        if(primary==null||
           cleanup==null||
           primary==cleanup)
            return;

        for(Throwable existing:
                primary.getSuppressed())
            if(existing==cleanup)
                return;

        primary.addSuppressed(
            cleanup
        );
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
        implements PluginRuntime {

        private volatile Plugin delegate;
        private volatile IsolatedPluginClassLoader loader;
        private final Path source;
        private final String entrypoint;
        private volatile ArchiveSnapshot snapshot;
        private volatile boolean closed;

        LoadedPlugin(
            Plugin delegate,
            IsolatedPluginClassLoader loader,
            Path source,
            String entrypoint,
            ArchiveSnapshot snapshot
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
            this.snapshot=
                Objects.requireNonNull(
                    snapshot,
                    "snapshot"
                );
        }

        @Override public synchronized PluginManifest manifest(){
            Plugin current=
                requireDelegate();
            IsolatedPluginClassLoader
                currentLoader=
                    requireLoader();

            return PluginThreadContext
                .callUnchecked(
                    currentLoader,
                    current::manifest
                );
        }

        @Override public synchronized void enable(
            PluginContext context
        )throws Exception{
            Plugin current=
                requireDelegate();
            IsolatedPluginClassLoader
                currentLoader=
                    requireLoader();

            PluginThreadContext.run(
                currentLoader,
                ()->current.enable(
                    context
                )
            );
        }

        @Override public synchronized void disable()
            throws Exception{
            Plugin current=
                requireDelegate();
            IsolatedPluginClassLoader
                currentLoader=
                    requireLoader();

            PluginThreadContext.run(
                currentLoader,
                current::disable
            );
        }

        synchronized Plugin delegate(){
            return requireDelegate();
        }

        @Override public synchronized ClassLoader
            callbackClassLoader(){
            return requireLoader();
        }

        synchronized ClassLoader classLoader(){
            return requireLoader();
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

        synchronized Path snapshotPath(){
            ArchiveSnapshot current=
                snapshot;

            if(current==null)
                throw terminalFailure();

            return current.path();
        }

        private Plugin requireDelegate(){
            Plugin current=
                delegate;

            if(closed||
               current==null)
                throw terminalFailure();

            return current;
        }

        private IsolatedPluginClassLoader
            requireLoader(){
            IsolatedPluginClassLoader current=
                loader;

            if(closed||
               current==null)
                throw terminalFailure();

            return current;
        }

        private IllegalStateException
            terminalFailure(){
            return new IllegalStateException(
                "plugin classloader closed: "+
                entrypoint
            );
        }

        @Override public synchronized void close()
            throws Exception{
            if(closed)
                return;

            closed=true;

            IsolatedPluginClassLoader
                ownedLoader=loader;
            ArchiveSnapshot
                ownedSnapshot=snapshot;
            Throwable failure=null;

            try{
                if(ownedLoader!=null)
                    ownedLoader.close();
            }catch(Throwable cleanup){
                failure=cleanup;
            }

            try{
                if(ownedSnapshot!=null)
                    ownedSnapshot.close();
            }catch(Throwable cleanup){
                if(failure==null)
                    failure=cleanup;
                else
                    preserveFailure(
                        failure,
                        cleanup
                    );
            }finally{
                delegate=null;
                loader=null;
                snapshot=null;
            }

            if(failure!=null)
                rethrow(
                    failure
                );
        }
    }

    private static final class ArchiveSnapshot
        implements AutoCloseable {
        private final Path root;
        private final Path path;
        private boolean closed;

        private ArchiveSnapshot(
            Path root,
            Path path
        ){
            this.root=root;
            this.path=path;
        }

        static ArchiveSnapshot capture(
            Path source
        )throws Exception{
            return capture(
                source,
                (root,snapshot)->{}
            );
        }

        static ArchiveSnapshot capture(
            Path source,
            ArchiveCaptureHook hook
        )throws Exception{
            if(!Files.isRegularFile(source))
                throw new IllegalArgumentException(
                    "plugin JAR missing: "+
                    source
                );

            Path root=
                Files.createTempDirectory(
                    "spawnpk-plugin-archive-"
                );
            Path path=
                root.resolve(
                    "plugin.jar"
                );

            try{
                Files.createFile(
                    path
                );

                Objects.requireNonNull(
                    hook,
                    "hook"
                ).afterPrivatePathsCreated(
                    root,
                    path
                );

                try(java.io.InputStream input=
                        Files.newInputStream(
                            source
                        );
                    java.io.OutputStream output=
                        Files.newOutputStream(
                            path,
                            java.nio.file.StandardOpenOption.WRITE,
                            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
                        )){
                    byte[] buffer=
                        new byte[8192];
                    int read;

                    while((read=
                            input.read(
                                buffer
                            ))!=-1)
                        output.write(
                            buffer,
                            0,
                            read
                        );
                }

                return new ArchiveSnapshot(
                    root,
                    path
                );
            }catch(Throwable failure){
                Throwable cleanup=
                    ArchiveCleanupDebt.delete(
                        path,
                        root
                    );

                if(cleanup!=null){
                    ArchiveCleanupDebt.register(
                        path,
                        root
                    );
                    preserveFailure(
                        failure,
                        cleanup
                    );
                }

                rethrow(failure);
                throw new AssertionError(
                    "unreachable"
                );
            }
        }

        Path path(){
            return path;
        }

        @Override public synchronized void close()
            throws Exception{
            if(closed)
                return;

            closed=true;
            Throwable failure=
                ArchiveCleanupDebt.delete(
                    path,
                    root
                );

            if(failure!=null){
                ArchiveCleanupDebt.register(
                    path,
                    root
                );
                rethrow(
                    failure
                );
            }
        }
    }

    private static final class ArchiveCleanupDebt {
        private static final java.util.ArrayList<
            ArchiveCleanupDebt
        > DEBTS=
            new java.util.ArrayList<>();

        private final Path path;
        private final Path root;

        private ArchiveCleanupDebt(
            Path path,
            Path root
        ){
            this.path=path;
            this.root=root;
        }

        static void register(
            Path path,
            Path root
        ){
            synchronized(DEBTS){
                for(ArchiveCleanupDebt existing:
                        DEBTS)
                    if(existing.path.equals(path)&&
                       existing.root.equals(root))
                        return;

                DEBTS.add(
                    new ArchiveCleanupDebt(
                        path,
                        root
                    )
                );
            }
        }

        Throwable retry(){
            return delete(
                path,
                root
            );
        }

        static Throwable delete(
            Path path,
            Path root
        ){
            Throwable failure=null;

            try{
                Files.deleteIfExists(
                    path
                );
            }catch(Throwable cleanup){
                failure=cleanup;
            }

            try{
                Files.deleteIfExists(
                    root
                );
            }catch(Throwable cleanup){
                if(failure==null)
                    failure=cleanup;
                else
                    preserveFailure(
                        failure,
                        cleanup
                    );
            }

            return failure;
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
