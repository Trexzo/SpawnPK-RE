package spk.local;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.io.IOException;
import spk.content.api.ContentActionResult;
import spk.content.api.ContentInteractionResult;
import spk.content.api.ContentNpcOptionResult;
import spk.content.api.ContentProvenance;
import spk.content.api.ContentResult;
import spk.event.DomainEventBus;
import spk.plugin.api.Plugin;
import spk.plugin.api.PluginManager;
import spk.plugin.api.PluginHandle;

public final class KotlinPluginLoaderTest {
    public static final class ProbeEvent
        implements DomainEventBus.Cancellable {
        private boolean cancelled;

        @Override public boolean isCancelled(){
            return cancelled;
        }

        @Override public void cancel(){
            cancelled=true;
        }
    }

    public static void main(
        String[] args
    )throws Exception{
        if(args.length<4)
            throw new IllegalArgumentException(
                "expected healthy script, denied script, API JAR and Kotlin compile dependencies"
            );

        Path healthy=
            Paths.get(args[0])
                .toAbsolutePath()
                .normalize();
        Path denied=
            Paths.get(args[1])
                .toAbsolutePath()
                .normalize();
        Path apiJar=
            Paths.get(args[2])
                .toAbsolutePath()
                .normalize();

        ArrayList<Path> compileClasspath=
            new ArrayList<>();

        for(int i=3;i<args.length;i++)
            compileClasspath.add(
                Paths.get(args[i])
                    .toAbsolutePath()
                    .normalize()
            );

        Class<?> loaderType=
            Class.forName(
                "spk.local.KotlinPluginLoader"
            );
        Constructor<?> constructor=
            loaderType.getConstructor(
                Path.class,
                List.class
            );
        PluginLoader loader=
            (PluginLoader)
                constructor.newInstance(
                    apiJar,
                    compileClasspath
                );

        assertDisguisedServerDependencyRejected(
            constructor,
            apiJar,
            compileClasspath
        );
        assertUnexpectedDslDependencyRejected(
            constructor,
            apiJar,
            compileClasspath
        );
        assertForgedDslDependencyRejected(
            constructor,
            apiJar,
            compileClasspath
        );
        assertDependencyExtensionFence(
            constructor,
            apiJar,
            compileClasspath
        );
        assertManifestClasspathFenced(
            constructor,
            apiJar,
            compileClasspath
        );
        assertMultiReleaseClasspathFenced(
            constructor,
            apiJar,
            compileClasspath
        );
        assertClasspathIdentityPinned(
            constructor,
            healthy,
            apiJar,
            compileClasspath
        );
        assertClasspathRuntimeOwned(
            loader,
            healthy
        );
        assertPrivateSnapshotNames(
            constructor,
            healthy,
            apiJar,
            compileClasspath
        );
        assertClasspathCleanupDebt(
            loader,
            healthy
        );
        assertClasspathCaptureFailureDebt(
            loaderType,
            healthy,
            apiJar,
            compileClasspath
        );
        assertLazyDependencySnapshot(
            constructor,
            healthy,
            apiJar,
            compileClasspath
        );

        assertRealLoaderConsumesSnapshot(
            loader,
            healthy
        );
        assertPathOnlyExecutionDenied(
            loader,
            healthy
        );

        PluginSource healthySource=
            PluginSource.scriptSnapshot(
                healthy,
                new String(
                    Files.readAllBytes(
                        healthy
                    ),
                    java.nio.charset.StandardCharsets.UTF_8
                )
            );

        if(!loader.supports(
                healthySource))
            throw new AssertionError(
                "Kotlin loader rejected .kts source"
            );

        if(loader.supports(
                PluginSource.of(
                    healthy,
                    "fixture.Entry"
                )))
            throw new AssertionError(
                "Kotlin loader accepted entrypoint-bearing source"
            );

        PluginRuntime runtime=
            loader.load(
                healthySource
            );

        if(runtime==null)
            throw new AssertionError(
                "Kotlin loader returned null runtime"
            );

        ClassLoader callbackLoader=
            runtime.callbackClassLoader();

        if(Class.forName(
                "spk.plugin.api.Plugin",
                false,
                callbackLoader
            )!=Plugin.class)
            throw new AssertionError(
                "Kotlin script lost parent plugin API identity"
            );

        Class<?> sdkClass=
            Class.forName(
                "spk.plugin.kotlin.KotlinPluginDslKt"
            );

        if(Class.forName(
                "spk.plugin.kotlin.KotlinPluginDslKt",
                false,
                callbackLoader
            )!=sdkClass)
            throw new AssertionError(
                "Kotlin script lost parent DSL SDK identity"
            );

        if(callbackLoader.getResource(
                "spk/plugin/kotlin/KotlinPluginDslKt.class"
            )==null)
            throw new AssertionError(
                "Kotlin script DSL SDK resource missing"
            );

        assertServerInternalDenied(
            callbackLoader
        );

        World world=
            World.isolatedForTest(
                25L
            );
        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayerAndStart(
                player,
                "kotlin-script-player"
            );
        ServerPacketWriter writer=
            new ServerPacketWriter(
                new OutboundPacketQueue(),
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );
        PluginManager manager=
            world.plugins();

        Path failingScript=
            createEnableFailureScript();

        try{
            PluginHandle handle=
                manager.enable(
                    runtime
                );

            assertBinding(
                world.content()
                    .commandBinding(
                        "kscript"
                    ),
                "command"
            );
            assertBinding(
                world.content()
                    .npcOptionBinding(
                        301,
                        1
                    ),
                "npc"
            );
            assertBinding(
                world.content()
                    .itemOptionBinding(
                        201,
                        1
                    ),
                "item"
            );
            assertBinding(
                world.content()
                    .actionBinding(
                        "fixture.kotlin.button"
                    ),
                "action"
            );

            boolean ownedDuplicateRejected=
                false;

            try{
                manager.enable(
                    runtime
                );
            }catch(IllegalStateException expected){
                ownedDuplicateRejected=
                    expected.getMessage()!=null&&
                    expected.getMessage()
                        .contains(
                            "already enabled"
                        );
            }

            if(!ownedDuplicateRejected||
               !handle.enabled()||
               runtime.callbackClassLoader()!=
                    callbackLoader)
                throw new AssertionError(
                    "owned duplicate attempt terminalized live Kotlin runtime"
                );

            PluginRuntime duplicateRuntime=
                loader.load(
                    healthySource
                );

            boolean freshDuplicateRejected=
                false;

            try{
                manager.enable(
                    duplicateRuntime
                );
            }catch(IllegalStateException expected){
                freshDuplicateRejected=
                    expected.getMessage()!=null&&
                    expected.getMessage()
                        .contains(
                            "already enabled"
                        );
            }

            if(!freshDuplicateRejected)
                throw new AssertionError(
                    "fresh duplicate Kotlin runtime was accepted"
                );

            assertRuntimeReleased(
                duplicateRuntime,
                "fresh duplicate rejection"
            );

            if(manager.plugin(
                    "fixture.kotlin.script")!=
                        handle||
               !handle.enabled())
                throw new AssertionError(
                    "fresh duplicate rejection disturbed live Kotlin runtime"
                );

            final String[] commandResult=
                new String[1];
            final String[] npcAction=
                new String[1];
            final String[] itemOutcome=
                new String[1];
            final boolean[] buttonAllowed=
                new boolean[1];

            world.submitAndWait(
                player,
                generation,
                ()->{
                    ProbeEvent event=
                        new ProbeEvent();
                    event.cancel();

                    world.domainEvents()
                        .publish(
                            event
                        );

                    ContentResult command=
                        world.content()
                            .dispatchCommand(
                                player,
                                "kscript alpha beta",
                                writer
                            );
                    ContentNpcOptionResult npc=
                        world.content()
                            .dispatchNpcOption(
                                301,
                                1,
                                3200,
                                3200
                            );
                    ContentInteractionResult item=
                        world.content()
                            .dispatchItemOption(
                                201,
                                1
                            );
                    ContentActionResult button=
                        world.content()
                            .dispatchAction(
                                player,
                                "fixture.kotlin.button"
                            );

                    commandResult[0]=
                        command==null
                            ?null
                            :command.logText();
                    npcAction[0]=
                        npc==null
                            ?null
                            :npc.actionKey();
                    itemOutcome[0]=
                        item==null
                            ?null
                            :item.outcome();
                    buttonAllowed[0]=
                        button!=null&&
                        button.allowed();
                },
                5_000L
            );

            if(!"KOTLIN_SCRIPT_EVENTS=1;args=alpha,beta;tccl=true"
                    .equals(
                        commandResult[0]
                    ))
                throw new AssertionError(
                    "Kotlin script DSL command result mismatch: "+
                    commandResult[0]
                );

            if(!"fixture.kotlin.npc"
                    .equals(
                        npcAction[0]
                    ))
                throw new AssertionError(
                    "Kotlin script DSL NPC result mismatch: "+
                    npcAction[0]
                );

            if(!"FIXTURE_KOTLIN_ITEM_OK"
                    .equals(
                        itemOutcome[0]
                    ))
                throw new AssertionError(
                    "Kotlin script DSL item result mismatch: "+
                    itemOutcome[0]
                );

            if(!buttonAllowed[0])
                throw new AssertionError(
                    "Kotlin script DSL semantic button did not allow"
                );

            if(!manager.disable(
                    "fixture.kotlin.script"))
                throw new AssertionError(
                    "Kotlin script plugin disable failed"
                );

            if(world.content()
                    .commandBinding(
                        "kscript"
                    )!=null||
               world.content()
                    .npcOptionBinding(
                        301,
                        1
                    )!=null||
               world.content()
                    .itemOptionBinding(
                        201,
                        1
                    )!=null||
               world.content()
                    .actionBinding(
                        "fixture.kotlin.button"
                    )!=null)
                throw new AssertionError(
                    "Kotlin DSL registrations survived disable"
                );

            assertHandleReleasedLoader(
                handle
            );
            assertRuntimeReleased(
                runtime,
                "explicit disable"
            );

            PluginRuntime failingRuntime=
                loader.load(
                    snapshotSource(
                        failingScript
                    )
                );
            boolean enableFailureObserved=
                false;

            try{
                manager.enable(
                    failingRuntime
                );
            }catch(Exception expected){
                enableFailureObserved=
                    containsMessage(
                        expected,
                        "fixture-enable-failure"
                    );
            }

            if(!enableFailureObserved)
                throw new AssertionError(
                    "Kotlin enable failure was not propagated"
                );

            if(manager.plugin(
                    "fixture.kotlin.enablefail")!=
                        null)
                throw new AssertionError(
                    "failed Kotlin enable published plugin handle"
                );

            assertRuntimeReleased(
                failingRuntime,
                "enable failure"
            );

            boolean deniedCompilation=false;

            try{
                loader.load(
                    snapshotSource(
                        denied
                    )
                );
            }catch(IllegalArgumentException expected){
                String message=
                    expected.getMessage();

                deniedCompilation=
                    message!=null&&
                    (message.contains(
                        "Unresolved reference")||
                     message.contains(
                        "spk.local"));
            }

            if(!deniedCompilation)
                throw new AssertionError(
                    "Kotlin script compile boundary exposed spk.local"
                );

            world.close();
            world.close();
        }finally{
            if(!world.closed())
                world.close();

            Files.deleteIfExists(
                failingScript
            );
        }

        System.out.println(
            "KOTLIN_PLUGIN_LOADER_PASS "+
            "kts=true "+
            "apiOnlyCompile=true "+
            "dependencyNamespaceFence=true "+
            "kotlinClasspathExtensionBypassFenced=true "+
            "kotlinManifestClasspathFenced=true "+
            "kotlinMultiReleaseClasspathFenced=true "+
            "serverInternalDenied=true "+
            "pluginApiIdentity=true "+
            "scriptSdkIdentity=true "+
            "sourceSnapshot=true "+
            "pathOnlyExecutionDenied=true "+
            "kotlinClasspathIdentityPinned=true "+
            "kotlinClasspathRuntimeOwned=true "+
            "kotlinClasspathCleanupDebt=true "+
            "kotlinClasspathCaptureFailureDebt=true "+
            "kotlinClasspathPrivateNames=true "+
            "kotlinClasspathLazyResolution=true "+
            "eventCallback=true "+
            "commandDsl=true "+
            "commandPlayerArgsDsl=true "+
            "npcDsl=true "+
            "itemDsl=true "+
            "semanticButtonDsl=true "+
            "customProvenance=true "+
            "callbackTccl=true "+
            "disableRegistrationCleanup=true "+
            "worldThreadLifecycle=true "+
            "ownedDuplicateKeepsRuntime=true "+
            "freshDuplicateClosesRuntime=true "+
            "terminalHandleReleasesLoader=true "+
            "runtimeReferencesReleased=true "+
            "enableFailureClosesRuntime=true "+
            "worldCloseIdempotent=true "+
            "terminalRuntime=true"
        );
    }

    private static PluginSource snapshotSource(
        Path path
    )throws Exception{
        return PluginSource.scriptSnapshot(
            path,
            new String(
                Files.readAllBytes(
                    path
                ),
                java.nio.charset.StandardCharsets.UTF_8
            )
        );
    }


    private static void assertDependencyExtensionFence(
        Constructor<?> constructor,
        Path apiJar,
        List<Path> healthyClasspath
    )throws Exception{
        if(healthyClasspath.isEmpty())
            return;

        Path root=
            Files.createTempDirectory(
                "kotlin-extension-fence-"
            );
        Path healthyBin=
            root.resolve(
                "runtime.bin"
            );
        Path forbiddenBin=
            root.resolve(
                "forbidden.dat"
            );
        Path plain=
            root.resolve(
                "plain.classpath"
            );

        try{
            Files.copy(
                healthyClasspath.get(0),
                healthyBin
            );

            constructor.newInstance(
                apiJar,
                java.util.Collections
                    .singletonList(
                        healthyBin
                    )
            );

            replaceWithForbiddenJar(
                forbiddenBin,
                "spk/local/ExtensionBypass.class"
            );

            boolean forbiddenRejected=false;

            try{
                constructor.newInstance(
                    apiJar,
                    java.util.Collections
                        .singletonList(
                            forbiddenBin
                        )
                );
            }catch(InvocationTargetException expected){
                Throwable cause=
                    expected.getCause();

                forbiddenRejected=
                    cause instanceof
                        IllegalArgumentException&&
                    cause.getMessage()!=null&&
                    cause.getMessage()
                        .contains(
                            "outside the DSL allowlist"
                        );
            }

            if(!forbiddenRejected)
                throw new AssertionError(
                    "Kotlin dependency archive bypassed policy through non-.jar filename"
                );

            Files.write(
                plain,
                new byte[]{
                    1,2,3,4
                }
            );

            boolean plainRejected=false;

            try{
                constructor.newInstance(
                    apiJar,
                    java.util.Collections
                        .singletonList(
                            plain
                        )
                );
            }catch(InvocationTargetException expected){
                Throwable cause=
                    expected.getCause();

                plainRejected=
                    cause instanceof
                        java.util.zip.ZipException||
                    cause instanceof
                        java.io.IOException||
                    cause instanceof
                        IllegalArgumentException;
            }

            if(!plainRejected)
                throw new AssertionError(
                    "Kotlin loader accepted regular non-archive classpath file"
                );
        }finally{
            Files.deleteIfExists(
                plain
            );
            Files.deleteIfExists(
                forbiddenBin
            );
            Files.deleteIfExists(
                healthyBin
            );
            Files.deleteIfExists(
                root
            );
        }
    }


    private static void assertManifestClasspathFenced(
        Constructor<?> constructor,
        Path apiJar,
        List<Path> healthyClasspath
    )throws Exception{
        if(healthyClasspath.isEmpty())
            return;

        Path root=
            Files.createTempDirectory(
                "kotlin-manifest-classpath-"
            );
        Path dependency=
            root.resolve(
                "manifest-dependency.jar"
            );
        Path api=
            root.resolve(
                "manifest-api.jar"
            );

        try{
            createManifestClasspathJar(
                dependency,
                "sibling-unvalidated.jar"
            );

            boolean dependencyRejected=false;

            try{
                constructor.newInstance(
                    apiJar,
                    java.util.Collections
                        .singletonList(
                            dependency
                        )
                );
            }catch(InvocationTargetException expected){
                Throwable cause=
                    expected.getCause();

                dependencyRejected=
                    cause instanceof
                        IllegalArgumentException&&
                    cause.getMessage()!=null&&
                    cause.getMessage()
                        .contains(
                            "manifest Class-Path is forbidden"
                        );
            }

            if(!dependencyRejected)
                throw new AssertionError(
                    "Kotlin dependency manifest Class-Path was accepted"
                );

            createManifestClasspathJar(
                api,
                "file:/tmp/absolute-unvalidated.jar"
            );

            boolean apiRejected=false;

            try{
                constructor.newInstance(
                    api,
                    healthyClasspath
                );
            }catch(InvocationTargetException expected){
                Throwable cause=
                    expected.getCause();

                apiRejected=
                    cause instanceof
                        IllegalArgumentException&&
                    cause.getMessage()!=null&&
                    cause.getMessage()
                        .contains(
                            "manifest Class-Path is forbidden"
                        );
            }

            if(!apiRejected)
                throw new AssertionError(
                    "Kotlin API manifest Class-Path was accepted"
                );
        }finally{
            Files.deleteIfExists(
                api
            );
            Files.deleteIfExists(
                dependency
            );
            Files.deleteIfExists(
                root
            );
        }
    }

    private static void createManifestClasspathJar(
        Path target,
        String classPath
    )throws Exception{
        Manifest manifest=
            new Manifest();
        Attributes attributes=
            manifest.getMainAttributes();
        attributes.put(
            Attributes.Name.MANIFEST_VERSION,
            "1.0"
        );
        attributes.put(
            Attributes.Name.CLASS_PATH,
            classPath
        );

        try(JarOutputStream out=
                new JarOutputStream(
                    Files.newOutputStream(
                        target
                    ),
                    manifest
                )){
        }
    }


    private static void assertMultiReleaseClasspathFenced(
        Constructor<?> constructor,
        Path apiJar,
        List<Path> healthyClasspath
    )throws Exception{
        if(healthyClasspath.isEmpty())
            return;

        Path root=
            Files.createTempDirectory(
                "kotlin-multi-release-"
            );
        Path dependency=
            root.resolve(
                "multi-release-dependency.jar"
            );
        Path api=
            root.resolve(
                "multi-release-api.jar"
            );

        try{
            createMultiReleaseClassJar(
                dependency,
                "META-INF/versions/9/spk/local/HiddenDependency.class"
            );

            boolean dependencyRejected=false;

            try{
                constructor.newInstance(
                    apiJar,
                    java.util.Collections
                        .singletonList(
                            dependency
                        )
                );
            }catch(InvocationTargetException expected){
                Throwable cause=
                    expected.getCause();

                dependencyRejected=
                    cause instanceof
                        IllegalArgumentException&&
                    cause.getMessage()!=null&&
                    cause.getMessage()
                        .contains(
                            "forbidden multi-release class entry"
                        );
            }

            if(!dependencyRejected)
                throw new AssertionError(
                    "Kotlin dependency multi-release class was accepted"
                );

            createMultiReleaseClassJar(
                api,
                "META-INF/versions/9/spk/local/HiddenApi.class"
            );

            boolean apiRejected=false;

            try{
                constructor.newInstance(
                    api,
                    healthyClasspath
                );
            }catch(InvocationTargetException expected){
                Throwable cause=
                    expected.getCause();

                apiRejected=
                    cause instanceof
                        IllegalArgumentException&&
                    cause.getMessage()!=null&&
                    cause.getMessage()
                        .contains(
                            "forbidden multi-release class entry"
                        );
            }

            if(!apiRejected)
                throw new AssertionError(
                    "Kotlin API multi-release class was accepted"
                );
        }finally{
            Files.deleteIfExists(
                api
            );
            Files.deleteIfExists(
                dependency
            );
            Files.deleteIfExists(
                root
            );
        }
    }

    private static void createMultiReleaseClassJar(
        Path target,
        String entryName
    )throws Exception{
        Manifest manifest=
            new Manifest();
        Attributes attributes=
            manifest.getMainAttributes();
        attributes.put(
            Attributes.Name.MANIFEST_VERSION,
            "1.0"
        );
        attributes.putValue(
            "Multi-Release",
            "true"
        );

        try(JarOutputStream out=
                new JarOutputStream(
                    Files.newOutputStream(
                        target
                    ),
                    manifest
                )){
            out.putNextEntry(
                new JarEntry(
                    entryName
                )
            );
            out.write(
                new byte[]{0}
            );
            out.closeEntry();
        }
    }

    private static void assertClasspathIdentityPinned(
        Constructor<?> constructor,
        Path healthy,
        Path apiJar,
        List<Path> healthyClasspath
    )throws Exception{
        if(healthyClasspath.isEmpty())
            throw new AssertionError(
                "Kotlin classpath identity regression requires at least one runtime dependency"
            );

        Path root=
            Files.createTempDirectory(
                "kotlin-classpath-identity-"
            );
        Path api=
            root.resolve(
                "api.jar"
            );
        Path runtime=
            root.resolve(
                "runtime.jar"
            );

        try{
            Files.copy(
                apiJar,
                api
            );
            Files.copy(
                healthyClasspath.get(0),
                runtime
            );

            PluginLoader apiLoader=
                (PluginLoader)
                    constructor.newInstance(
                        api,
                        java.util.Collections
                            .singletonList(
                                runtime
                            )
                    );

            replaceWithForbiddenJar(
                api,
                "spk/local/ClasspathSwap.class"
            );

            assertLoadRejected(
                apiLoader,
                healthy,
                "non-public SpawnPK namespace",
                "replaced API artifact entered compiler authority"
            );

            Files.copy(
                apiJar,
                api,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );
            Files.copy(
                healthyClasspath.get(0),
                runtime,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );

            PluginLoader runtimeLoader=
                (PluginLoader)
                    constructor.newInstance(
                        api,
                        java.util.Collections
                            .singletonList(
                                runtime
                            )
                    );

            replaceWithForbiddenJar(
                runtime,
                "spk/local/RuntimeSwap.class"
            );

            assertLoadRejected(
                runtimeLoader,
                healthy,
                "outside the DSL allowlist",
                "replaced runtime artifact entered compiler authority"
            );

            Files.copy(
                healthyClasspath.get(0),
                runtime,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );

            PluginLoader forgedLoader=
                (PluginLoader)
                    constructor.newInstance(
                        api,
                        java.util.Collections
                            .singletonList(
                                runtime
                            )
                    );

            replaceWithForbiddenJar(
                runtime,
                "spk/plugin/kotlin/KotlinPluginDslKt.class"
            );

            assertLoadRejected(
                forgedLoader,
                healthy,
                "does not match server SDK",
                "forged DSL artifact entered compiler authority"
            );
        }finally{
            Files.deleteIfExists(
                runtime
            );
            Files.deleteIfExists(
                api
            );
            Files.deleteIfExists(
                root
            );
        }
    }

    private static void assertClasspathRuntimeOwned(
        PluginLoader loader,
        Path healthy
    )throws Exception{
        int debtBefore=
            KotlinClasspathCleanupDebt
                .count();
        PluginRuntime runtime=
            loader.load(
                snapshotSource(
                    healthy
                )
            );

        Field snapshotField=
            runtime.getClass()
                .getDeclaredField(
                    "classpathSnapshot"
                );
        snapshotField.setAccessible(
            true
        );
        Object snapshot=
            snapshotField.get(
                runtime
            );

        if(snapshot==null)
            throw new AssertionError(
                "live Kotlin runtime did not own classpath snapshot"
            );

        Field rootField=
            snapshot.getClass()
                .getDeclaredField(
                    "root"
                );
        rootField.setAccessible(
            true
        );
        Path root=
            (Path)
                rootField.get(
                    snapshot
                );

        Field filesField=
            snapshot.getClass()
                .getDeclaredField(
                    "files"
                );
        filesField.setAccessible(
            true
        );

        @SuppressWarnings("unchecked")
        List<java.io.File> files=
            (List<java.io.File>)
                filesField.get(
                    snapshot
                );

        if(root==null||
           !Files.isDirectory(
                root
           )||
           files.isEmpty())
            throw new AssertionError(
                "live Kotlin classpath snapshot missing"
            );

        for(java.io.File file:files)
            if(!file.isFile())
                throw new AssertionError(
                    "live Kotlin classpath artifact missing: "+
                    file
                );

        runtime.close();
        runtime.close();

        if(Files.exists(
                root))
            throw new AssertionError(
                "closed Kotlin runtime retained classpath snapshot root"
            );

        if(KotlinClasspathCleanupDebt
                .count()!=debtBefore)
            throw new AssertionError(
                "healthy Kotlin runtime close created cleanup debt"
            );

        assertRuntimeReleased(
            runtime,
            "classpath runtime ownership"
        );
    }


    private static Object classpathSnapshot(
        PluginRuntime runtime
    )throws Exception{
        Field snapshotField=
            runtime.getClass()
                .getDeclaredField(
                    "classpathSnapshot"
                );
        snapshotField.setAccessible(
            true
        );
        return snapshotField.get(
            runtime
        );
    }

    private static Path snapshotRoot(
        Object snapshot
    )throws Exception{
        Field rootField=
            snapshot.getClass()
                .getDeclaredField(
                    "root"
                );
        rootField.setAccessible(
            true
        );
        return (Path)rootField.get(
            snapshot
        );
    }

    @SuppressWarnings("unchecked")
    private static List<java.io.File> snapshotFiles(
        Object snapshot
    )throws Exception{
        Field filesField=
            snapshot.getClass()
                .getDeclaredField(
                    "files"
                );
        filesField.setAccessible(
            true
        );
        return (List<java.io.File>)
            filesField.get(
                snapshot
            );
    }

    private static void assertPrivateSnapshotNames(
        Constructor<?> constructor,
        Path healthy,
        Path apiJar,
        List<Path> healthyClasspath
    )throws Exception{
        if(healthyClasspath.isEmpty())
            return;

        Path root=
            Files.createTempDirectory(
                "kotlin-private-name-"
            );
        String callerName=
            "caller-controlled-dependency-name-that-must-not-be-copied-"+
            "abcdefghijklmnopqrstuvwxyz0123456789.jar";
        Path dependency=
            root.resolve(
                callerName
            );

        PluginRuntime runtime=null;

        try{
            Files.copy(
                healthyClasspath.get(0),
                dependency
            );

            PluginLoader loader=
                (PluginLoader)
                    constructor.newInstance(
                        apiJar,
                        java.util.Collections
                            .singletonList(
                                dependency
                            )
                    );

            runtime=
                loader.load(
                    snapshotSource(
                        healthy
                    )
                );

            Object snapshot=
                classpathSnapshot(
                    runtime
                );
            List<java.io.File> files=
                snapshotFiles(
                    snapshot
                );

            if(files.size()!=2||
               !"000-api.jar".equals(
                    files.get(0).getName()
               )||
               !"001-dependency.jar".equals(
                    files.get(1).getName()
               ))
                throw new AssertionError(
                    "Kotlin private classpath names are not fixed ordinal authority: "+
                    files
                );

            for(java.io.File file:files)
                if(file.getName()
                        .contains(
                            callerName
                        ))
                    throw new AssertionError(
                        "private classpath name retained caller basename: "+
                        file
                    );
        }finally{
            if(runtime!=null)
                runtime.close();

            Files.deleteIfExists(
                dependency
            );
            Files.deleteIfExists(
                root
            );
        }
    }

    private static void assertClasspathCleanupDebt(
        PluginLoader loader,
        Path healthy
    )throws Exception{
        int debtBefore=
            KotlinClasspathCleanupDebt
                .count();
        PluginRuntime runtime=
            loader.load(
                snapshotSource(
                    healthy
                )
            );
        Object snapshot=
            classpathSnapshot(
                runtime
            );
        Path root=
            snapshotRoot(
                snapshot
            );
        Path sentinel=
            root.resolve(
                "retirement-blocker.sentinel"
            );

        Files.write(
            sentinel,
            new byte[]{1}
        );

        Throwable closeFailure=null;

        try{
            runtime.close();
        }catch(Throwable failure){
            closeFailure=failure;
        }

        if(closeFailure==null)
            throw new AssertionError(
                "Kotlin classpath retirement blocker did not surface"
            );

        assertRuntimeReleased(
            runtime,
            "classpath cleanup debt"
        );

        if(KotlinClasspathCleanupDebt
                .count()!=debtBefore+1)
            throw new AssertionError(
                "failed Kotlin classpath retirement did not register exactly one debt"
            );

        Throwable retryFailure=
            KotlinClasspathCleanupDebt
                .retryOnce(
                    null
                );

        if(retryFailure==null||
           KotlinClasspathCleanupDebt
                .count()!=debtBefore+1)
            throw new AssertionError(
                "Kotlin cleanup debt retry spun through or lost blocked debt"
            );

        runtime.close();

        if(KotlinClasspathCleanupDebt
                .count()!=debtBefore+1)
            throw new AssertionError(
                "duplicate runtime close changed Kotlin cleanup debt"
            );

        Files.delete(
            sentinel
        );

        Throwable drained=
            KotlinClasspathCleanupDebt
                .retryOnce(
                    null
                );

        if(drained!=null||
           KotlinClasspathCleanupDebt
                .count()!=debtBefore||
           Files.exists(
                root
           ))
            throw new AssertionError(
                "Kotlin cleanup debt did not retire after blocker removal"
            );
    }


    private static void assertClasspathCaptureFailureDebt(
        Class<?> loaderType,
        Path healthy,
        Path apiJar,
        List<Path> healthyClasspath
    )throws Exception{
        if(healthyClasspath.isEmpty())
            return;

        Constructor<?> constructor=
            loaderType.getConstructor(
                Path.class,
                List.class,
                KotlinClasspathCaptureHook.class
            );
        final Path[] blockedRoot=
            new Path[1];
        final Path[] sentinel=
            new Path[1];

        KotlinClasspathCaptureHook hook=
            (root,target,index)->{
                if(index!=0)
                    return;

                blockedRoot[0]=root;
                sentinel[0]=
                    root.resolve(
                        "capture-blocker.sentinel"
                    );
                Files.write(
                    sentinel[0],
                    new byte[]{1}
                );
                throw new IOException(
                    "fixture-kotlin-capture-primary"
                );
            };

        PluginLoader loader=
            (PluginLoader)
                constructor.newInstance(
                    apiJar,
                    healthyClasspath,
                    hook
                );
        int debtBefore=
            KotlinClasspathCleanupDebt
                .count();
        Throwable failure=null;

        try{
            loader.load(
                snapshotSource(
                    healthy
                )
            );
        }catch(Throwable expected){
            failure=expected;
        }

        if(failure==null||
           !containsMessage(
                failure,
                "fixture-kotlin-capture-primary"
           ))
            throw new AssertionError(
                "Kotlin classpath capture primary failure was not preserved"
            );

        if(failure.getSuppressed().length==0)
            throw new AssertionError(
                "Kotlin classpath capture cleanup failure was not suppressed"
            );

        if(blockedRoot[0]==null||
           sentinel[0]==null||
           !Files.exists(
               blockedRoot[0]
           )||
           KotlinClasspathCleanupDebt
                .count()!=debtBefore+1)
            throw new AssertionError(
                "pre-runtime Kotlin classpath capture failure did not transfer path-only debt"
            );

        Throwable retry=
            KotlinClasspathCleanupDebt
                .retryOnce(
                    null
                );

        if(retry==null||
           KotlinClasspathCleanupDebt
                .count()!=debtBefore+1)
            throw new AssertionError(
                "blocked pre-runtime Kotlin cleanup debt was not retained for one bounded retry"
            );

        Files.delete(
            sentinel[0]
        );

        Throwable drained=
            KotlinClasspathCleanupDebt
                .retryOnce(
                    null
                );

        if(drained!=null||
           KotlinClasspathCleanupDebt
                .count()!=debtBefore||
           Files.exists(
               blockedRoot[0]
           ))
            throw new AssertionError(
                "pre-runtime Kotlin cleanup debt did not drain after blocker removal"
            );
    }

    private static void assertLazyDependencySnapshot(
        Constructor<?> constructor,
        Path healthy,
        Path apiJar,
        List<Path> healthyClasspath
    )throws Exception{
        if(healthyClasspath.isEmpty())
            return;

        Path root=
            Files.createTempDirectory(
                "kotlin-lazy-dependency-"
            );
        Path helperJar=
            root.resolve(
                "lazy-helper.jar"
            );
        Path script=
            root.resolve(
                "lazy.kts"
            );
        PluginRuntime runtime=null;

        try{
            createLazyHelperJar(
                root,
                helperJar
            );

            Files.write(
                script,
                java.util.Arrays.asList(
                    "import lazy.fixture.LazyHelper",
                    "import spk.plugin.api.Plugin",
                    "import spk.plugin.api.PluginApiVersion",
                    "import spk.plugin.api.PluginContext",
                    "import spk.plugin.api.PluginManifest",
                    "",
                    "object : Plugin {",
                    "    override fun manifest(): PluginManifest =",
                    "        PluginManifest(",
                    "            LazyHelper.id(),",
                    "            \"1.0\",",
                    "            PluginApiVersion.CURRENT,",
                    "            emptyList<String>()",
                    "        )",
                    "    override fun enable(context: PluginContext) {}",
                    "}"
                ),
                java.nio.charset.StandardCharsets.UTF_8
            );

            ArrayList<Path> dependencies=
                new ArrayList<>(
                    healthyClasspath
                );
            dependencies.add(
                helperJar
            );

            PluginLoader lazyLoader=
                (PluginLoader)
                    constructor.newInstance(
                        apiJar,
                        dependencies
                    );

            runtime=
                lazyLoader.load(
                    snapshotSource(
                        script
                    )
                );

            Field dependencyLoaderField=
                runtime.getClass()
                    .getDeclaredField(
                        "dependencyLoader"
                    );
            dependencyLoaderField
                .setAccessible(
                    true
                );
            ClassLoader owned=
                (ClassLoader)
                    dependencyLoaderField
                        .get(
                            runtime
                        );

            Files.delete(
                helperJar
            );

            if(!"fixture.kotlin.lazy"
                    .equals(
                        runtime.manifest()
                            .id()
                    ))
                throw new AssertionError(
                    "lazy Kotlin dependency did not resolve from private snapshot"
                );

            Class<?> helper=
                Class.forName(
                    "lazy.fixture.LazyHelper",
                    false,
                    runtime.callbackClassLoader()
                );

            if(helper.getClassLoader()!=
                    owned)
                throw new AssertionError(
                    "lazy dependency resolved outside owned Kotlin dependency loader"
                );
        }finally{
            if(runtime!=null)
                runtime.close();

            Files.deleteIfExists(
                helperJar
            );
            Files.deleteIfExists(
                script
            );
            deleteTree(
                root.resolve(
                    "lazy-src"
                )
            );
            deleteTree(
                root.resolve(
                    "lazy-classes"
                )
            );
            Files.deleteIfExists(
                root
            );
        }
    }

    private static void createLazyHelperJar(
        Path root,
        Path jar
    )throws Exception{
        JavaCompiler compiler=
            ToolProvider.getSystemJavaCompiler();

        if(compiler==null)
            throw new AssertionError(
                "system Java compiler unavailable"
            );

        Path sourceRoot=
            root.resolve(
                "lazy-src"
            );
        Path packageDir=
            sourceRoot.resolve(
                "lazy/fixture"
            );
        Path classes=
            root.resolve(
                "lazy-classes"
            );
        Files.createDirectories(
            packageDir
        );
        Files.createDirectories(
            classes
        );

        Path source=
            packageDir.resolve(
                "LazyHelper.java"
            );
        Files.write(
            source,
            java.util.Arrays.asList(
                "package lazy.fixture;",
                "public final class LazyHelper {",
                "  public static String id(){ return \"fixture.kotlin.lazy\"; }",
                "  private LazyHelper(){}",
                "}"
            ),
            java.nio.charset.StandardCharsets.UTF_8
        );

        int result=
            compiler.run(
                null,
                null,
                null,
                "-d",
                classes.toString(),
                source.toString()
            );

        if(result!=0)
            throw new AssertionError(
                "lazy dependency fixture javac failed: "+
                result
            );

        Path classFile=
            classes.resolve(
                "lazy/fixture/LazyHelper.class"
            );

        try(JarOutputStream out=
                new JarOutputStream(
                    Files.newOutputStream(
                        jar
                    )
                )){
            out.putNextEntry(
                new JarEntry(
                    "lazy/fixture/LazyHelper.class"
                )
            );
            Files.copy(
                classFile,
                out
            );
            out.closeEntry();
        }
    }

    private static void deleteTree(
        Path root
    )throws IOException{
        if(root==null||
           !Files.exists(
               root
           ))
            return;

        try(java.util.stream.Stream<Path> stream=
                Files.walk(
                    root
                )){
            java.util.List<Path> paths=
                stream.sorted(
                    java.util.Comparator.reverseOrder()
                ).collect(
                    java.util.stream.Collectors.toList()
                );

            for(Path path:paths)
                Files.deleteIfExists(
                    path
                );
        }
    }

    private static void assertLoadRejected(
        PluginLoader loader,
        Path healthy,
        String expected,
        String failureMessage
    )throws Exception{
        int debtBefore=
            KotlinClasspathCleanupDebt
                .count();
        boolean rejected=false;

        try{
            loader.load(
                snapshotSource(
                    healthy
                )
            );
        }catch(IllegalArgumentException failure){
            rejected=
                failure.getMessage()!=null&&
                failure.getMessage()
                    .contains(
                        expected
                    );
        }

        if(!rejected)
            throw new AssertionError(
                failureMessage
            );

        if(KotlinClasspathCleanupDebt
                .count()!=debtBefore)
            throw new AssertionError(
                "rejected Kotlin classpath snapshot leaked cleanup debt"
            );
    }

    private static void replaceWithForbiddenJar(
        Path target,
        String entry
    )throws Exception{
        Files.deleteIfExists(
            target
        );

        try(JarOutputStream out=
                new JarOutputStream(
                    Files.newOutputStream(
                        target
                    )
                )){
            out.putNextEntry(
                new JarEntry(
                    entry
                )
            );
            out.write(
                new byte[]{0}
            );
            out.closeEntry();
        }
    }

    private static void assertPathOnlyExecutionDenied(
        PluginLoader loader,
        Path healthy
    )throws Exception{
        PluginSource pathOnly=
            PluginSource.script(
                healthy
            );

        if(!loader.supports(
                pathOnly))
            throw new AssertionError(
                "Kotlin loader routing no longer recognizes .kts path"
            );

        boolean denied=false;

        try{
            loader.load(
                pathOnly
            );
        }catch(IllegalArgumentException expected){
            denied=
                expected.getMessage()!=null&&
                expected.getMessage()
                    .contains(
                        "source snapshot is required"
                    );
        }

        if(!denied)
            throw new AssertionError(
                "Kotlin loader reopened path-only script source"
            );
    }

    private static void assertRealLoaderConsumesSnapshot(
        PluginLoader loader,
        Path healthy
    )throws Exception{
        String text=
            new String(
                Files.readAllBytes(
                    healthy
                ),
                java.nio.charset.StandardCharsets.UTF_8
            );
        Path temp=
            Files.createTempFile(
                "kotlin-loader-snapshot-",
                ".kts"
            );

        try{
            Files.write(
                temp,
                "// path mutation B must be ignored\n"
                    .getBytes(
                        java.nio.charset.StandardCharsets.UTF_8
                    )
            );

            PluginSource snapshot=
                PluginSource.scriptSnapshot(
                    temp,
                    text
                );

            Files.delete(
                temp
            );

            PluginRuntime detached=
                loader.load(
                    snapshot
                );

            try{
                if(!"fixture.kotlin.script"
                        .equals(
                            detached.manifest()
                                .id()
                        ))
                    throw new AssertionError(
                        "real Kotlin loader did not evaluate captured source snapshot"
                    );
            }finally{
                detached.close();
            }
        }finally{
            Files.deleteIfExists(
                temp
            );
        }
    }

    private static void assertBinding(
        ContentRegistry.BindingInfo binding,
        String label
    ){
        if(binding==null||
           !"plugin:fixture.kotlin.script"
                .equals(
                    binding.moduleId
                )||
           binding.provenance!=
                ContentProvenance.CUSTOM_LOCALLAB)
            throw new AssertionError(
                "Kotlin DSL "+
                label+
                " binding mismatch: "+
                binding
            );
    }

    private static Path createEnableFailureScript()
        throws Exception{
        Path script=
            Files.createTempFile(
                "kotlin-plugin-enable-failure-",
                ".kts"
            );

        Files.write(
            script,
            java.util.Arrays.asList(
                "import spk.plugin.api.Plugin",
                "import spk.plugin.api.PluginApiVersion",
                "import spk.plugin.api.PluginContext",
                "import spk.plugin.api.PluginManifest",
                "",
                "object : Plugin {",
                "    override fun manifest(): PluginManifest =",
                "        PluginManifest(",
                "            \"fixture.kotlin.enablefail\",",
                "            \"1.0\",",
                "            PluginApiVersion.CURRENT,",
                "            emptyList<String>()",
                "        )",
                "",
                "    override fun enable(context: PluginContext) {",
                "        throw IllegalStateException(\"fixture-enable-failure\")",
                "    }",
                "}"
            ),
            java.nio.charset.StandardCharsets.UTF_8
        );

        return script;
    }

    private static boolean containsMessage(
        Throwable failure,
        String expected
    ){
        for(Throwable current=failure;
            current!=null;
            current=current.getCause()){
            String message=
                current.getMessage();

            if(message!=null&&
               message.contains(
                    expected
               ))
                return true;
        }

        return false;
    }

    private static void assertRuntimeReleased(
        PluginRuntime runtime,
        String phase
    )throws Exception{
        boolean terminal=true;

        try{
            runtime.callbackClassLoader();
            terminal=false;
        }catch(IllegalStateException expected){
        }

        if(!terminal)
            throw new AssertionError(
                phase+
                " retained public callback loader"
            );

        for(String fieldName:
                new String[]{
                    "delegate",
                    "callbackLoader",
                    "baseLoader",
                    "dependencyLoader",
                    "classpathSnapshot"
                }){
            Field field=
                runtime.getClass()
                    .getDeclaredField(
                        fieldName
                    );
            field.setAccessible(
                true
            );

            if(field.get(runtime)!=null)
                throw new AssertionError(
                    phase+
                    " retained runtime field "+
                    fieldName
                );
        }
    }

    private static void assertHandleReleasedLoader(
        PluginHandle handle
    )throws Exception{
        Field pluginField=
            handle.getClass()
                .getDeclaredField(
                    "plugin"
                );
        pluginField.setAccessible(
            true
        );

        if(pluginField.get(handle)!=null)
            throw new AssertionError(
                "disabled Kotlin PluginHandle retained plugin instance"
            );

        Field tasksField=
            handle.getClass()
                .getDeclaredField(
                    "tasks"
                );
        tasksField.setAccessible(
            true
        );
        Object tasks=
            tasksField.get(handle);

        Field loaderField=
            tasks.getClass()
                .getDeclaredField(
                    "callbackLoader"
                );
        loaderField.setAccessible(
            true
        );

        if(loaderField.get(tasks)!=null)
            throw new AssertionError(
                "disabled Kotlin PluginHandle retained callback classloader"
            );
    }

    private static void assertServerInternalDenied(
        ClassLoader loader
    )throws Exception{
        boolean denied=false;

        try{
            Class.forName(
                "spk.local.World",
                false,
                loader
            );
        }catch(ClassNotFoundException expected){
            denied=true;
        }

        if(!denied)
            throw new AssertionError(
                "Kotlin script callback loader exposed spk.local.World"
            );

        if(loader.getResource(
                "spk/local/World.class"
            )!=null)
            throw new AssertionError(
                "Kotlin script callback loader exposed spk.local.World resource"
            );

        if(loader.getResources(
                "spk/local/World.class"
            ).hasMoreElements())
            throw new AssertionError(
                "Kotlin script callback loader exposed spk.local.World resource enumeration"
            );

        boolean widgetDenied=false;

        try{
            Class.forName(
                "spk.local.WidgetActionClientRequest",
                false,
                loader
            );
        }catch(ClassNotFoundException expected){
            widgetDenied=true;
        }

        if(!widgetDenied)
            throw new AssertionError(
                "Kotlin script callback loader exposed raw widget transport"
            );

        if(loader.getResource(
                "spk/local/WidgetActionClientRequest.class"
            )!=null)
            throw new AssertionError(
                "Kotlin script callback loader exposed raw widget resource"
            );

        if(loader.getResources(
                "spk/local/WidgetActionClientRequest.class"
            ).hasMoreElements())
            throw new AssertionError(
                "Kotlin script callback loader exposed raw widget resource enumeration"
            );
    }

    private static void
        assertDisguisedServerDependencyRejected(
            Constructor<?> constructor,
            Path apiJar,
            List<Path> healthyClasspath
        )throws Exception{
        Path fake=
            Files.createTempFile(
                "kotlin-script-server-leak-",
                ".jar"
            );

        try{
            try(JarOutputStream out=
                    new JarOutputStream(
                        Files.newOutputStream(
                            fake
                        )
                    )){
                out.putNextEntry(
                    new JarEntry(
                        "spk/local/Fake.class"
                    )
                );
                out.write(
                    new byte[]{0}
                );
                out.closeEntry();
            }

            ArrayList<Path> poisoned=
                new ArrayList<>(
                    healthyClasspath
                );
            poisoned.add(fake);

            boolean rejected=false;

            try{
                constructor.newInstance(
                    apiJar,
                    poisoned
                );
            }catch(InvocationTargetException expected){
                Throwable cause=
                    expected.getCause();

                rejected=
                    cause instanceof
                        IllegalArgumentException&&
                    cause.getMessage()!=null&&
                    cause.getMessage()
                        .contains(
                            "contains SpawnPK"
                        );
            }

            if(!rejected)
                throw new AssertionError(
                    "Kotlin loader accepted disguised server dependency"
                );
        }finally{
            Files.deleteIfExists(
                fake
            );
        }
    }

    private static void
        assertUnexpectedDslDependencyRejected(
            Constructor<?> constructor,
            Path apiJar,
            List<Path> healthyClasspath
        )throws Exception{
        Path directory=
            Files.createTempDirectory(
                "kotlin-script-dsl-fence-"
            );
        Path fake=
            directory.resolve(
                "SpawnPKKotlinScriptRuntime.jar"
            );

        try{
            try(JarOutputStream out=
                    new JarOutputStream(
                        Files.newOutputStream(
                            fake
                        )
                    )){
                out.putNextEntry(
                    new JarEntry(
                        "spk/plugin/kotlin/Unexpected.class"
                    )
                );
                out.write(
                    new byte[]{0}
                );
                out.closeEntry();
            }

            ArrayList<Path> poisoned=
                new ArrayList<>(
                    healthyClasspath
                );
            poisoned.add(fake);

            boolean rejected=false;

            try{
                constructor.newInstance(
                    apiJar,
                    poisoned
                );
            }catch(InvocationTargetException expected){
                Throwable cause=
                    expected.getCause();

                rejected=
                    cause instanceof
                        IllegalArgumentException&&
                    cause.getMessage()!=null&&
                    cause.getMessage()
                        .contains(
                            "outside the DSL allowlist"
                        );
            }

            if(!rejected)
                throw new AssertionError(
                    "Kotlin loader accepted unexpected class in DSL namespace"
                );
        }finally{
            Files.deleteIfExists(
                fake
            );
            Files.deleteIfExists(
                directory
            );
        }
    }

    private static void
        assertForgedDslDependencyRejected(
            Constructor<?> constructor,
            Path apiJar,
            List<Path> healthyClasspath
        )throws Exception{
        Path directory=
            Files.createTempDirectory(
                "kotlin-script-forged-dsl-"
            );
        Path fake=
            directory.resolve(
                "renamed-runtime.jar"
            );

        try{
            try(JarOutputStream out=
                    new JarOutputStream(
                        Files.newOutputStream(
                            fake
                        )
                    )){
                out.putNextEntry(
                    new JarEntry(
                        "spk/plugin/kotlin/KotlinPluginDslKt.class"
                    )
                );
                byte[] hostile=
                    new byte[8192];
                java.util.Arrays.fill(
                    hostile,
                    (byte)0x5a
                );

                for(int i=0;i<128;i++)
                    out.write(
                        hostile
                    );

                out.closeEntry();
            }

            ArrayList<Path> poisoned=
                new ArrayList<>(
                    healthyClasspath
                );
            poisoned.add(fake);

            boolean rejected=false;

            try{
                constructor.newInstance(
                    apiJar,
                    poisoned
                );
            }catch(InvocationTargetException expected){
                Throwable cause=
                    expected.getCause();

                rejected=
                    cause instanceof
                        IllegalArgumentException&&
                    cause.getMessage()!=null&&
                    cause.getMessage()
                        .contains(
                            "does not match server SDK"
                        );
            }

            if(!rejected)
                throw new AssertionError(
                    "Kotlin loader accepted forged DSL SDK class"
                );
        }finally{
            Files.deleteIfExists(
                fake
            );
            Files.deleteIfExists(
                directory
            );
        }
    }

    private KotlinPluginLoaderTest(){}
}
