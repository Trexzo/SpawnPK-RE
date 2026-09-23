package spk.plugin.api;

import java.io.File;
import java.lang.reflect.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

public final class PluginPublicApiBoundaryTest {
    private static final Class<?>[] API_TYPES={
        Plugin.class,
        PluginApiVersion.class,
        PluginContext.class,
        PluginEvents.class,
        PluginHandle.class,
        PluginManager.class,
        PluginManifest.class,
        PluginScheduler.class,
        PluginTask.class
    };

    private static final Set<String> FORBIDDEN_SIMPLE_NAMES=
        new HashSet<>(
            Arrays.asList(
                "Socket",
                "ServerSocket",
                "IsaacCipher",
                "ClientPacketProbe",
                "ClientRequest",
                "ClientRequestMetadata",
                "ServerPacketWriter",
                "World",
                "WorldPlayer",
                "WorldEventQueue",
                "GameClock",
                "NpcEntity",
                "NpcRegistry",
                "Thread",
                "Executor",
                "ExecutorService",
                "Future",
                "Timer"
            )
        );

    public static void main(String[] args){
        ArrayList<String> violations=
            new ArrayList<>();

        assertApiTypeCoverage(violations);

        for(Class<?> api:API_TYPES){
            if(!Modifier.isPublic(
                    api.getModifiers()))
                violations.add(
                    api.getName()+
                    " is not public"
                );

            for(Method method:
                    api.getDeclaredMethods()){
                if(!Modifier.isPublic(
                        method.getModifiers()))
                    continue;

                String location=
                    api.getName()+
                    "#"+
                    method.getName();

                inspectName(
                    method.getName(),
                    location,
                    violations
                );
                inspect(
                    method.getGenericReturnType(),
                    location+" return",
                    violations
                );

                Type[] parameters=
                    method.getGenericParameterTypes();

                for(int i=0;i<parameters.length;i++)
                    inspect(
                        parameters[i],
                        location+
                        " param["+i+"]",
                        violations
                    );

                for(Class<?> exceptionType:
                        method.getExceptionTypes())
                    inspectClass(
                        exceptionType,
                        location+" throws",
                        violations
                    );
            }

            for(Constructor<?> constructor:
                    api.getDeclaredConstructors()){
                if(!Modifier.isPublic(
                        constructor.getModifiers()))
                    continue;

                Type[] parameters=
                    constructor
                        .getGenericParameterTypes();

                for(int i=0;i<parameters.length;i++)
                    inspect(
                        parameters[i],
                        api.getName()+
                        " ctor param["+i+"]",
                        violations
                    );
            }

            for(Field field:
                    api.getDeclaredFields()){
                if(!Modifier.isPublic(
                        field.getModifiers()))
                    continue;

                inspectName(
                    field.getName(),
                    api.getName()+
                    "#"+field.getName(),
                    violations
                );
                inspect(
                    field.getGenericType(),
                    api.getName()+
                    "#"+field.getName()+
                    " field",
                    violations
                );
            }
        }

        if(!violations.isEmpty())
            throw new AssertionError(
                "public plugin API boundary violations="+
                violations
            );

        System.out.println(
            "PLUGIN_PUBLIC_API_BOUNDARY_PASS "+
            "types="+API_TYPES.length+" "+
            "spkLocalLeak=false "+
            "networkLeak=false "+
            "transportTypeLeak=false "+
            "opcodeIdentity=false "+
            "widgetIdentity=false "+
            "sceneIndex=false "+
            "registryLeak=false "+
            "apiCoverageComplete=true"
        );
    }

    private static void assertApiTypeCoverage(
        List<String> violations
    ){
        TreeSet<String> expected=
            new TreeSet<>();

        for(Class<?> api:API_TYPES)
            expected.add(api.getName());

        Set<String> discovered=
            discoverPublicPluginTypes();

        for(String name:discovered)
            if(!expected.contains(name))
                violations.add(
                    name+
                    " public plugin API type missing from boundary audit"
                );

        for(String name:expected)
            if(!discovered.contains(name))
                violations.add(
                    name+
                    " boundary audit entry is not a public top-level plugin API type"
                );
    }

    private static Set<String>
        discoverPublicPluginTypes(){
        TreeSet<String> names=
            new TreeSet<>();

        try{
            URL location=
                Plugin.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation();

            if(location==null)
                throw new IllegalStateException(
                    "plugin API code source unavailable"
                );

            File source=
                new File(
                    location.toURI()
                );

            if(source.isDirectory())
                discoverDirectory(
                    source,
                    names
                );
            else
                discoverJar(
                    source,
                    names
                );
        }catch(Exception error){
            throw new AssertionError(
                "could not discover public plugin API types",
                error
            );
        }

        if(names.isEmpty())
            throw new AssertionError(
                "no public spk.plugin.api types discovered"
            );

        return names;
    }

    private static void discoverDirectory(
        File root,
        Set<String> names
    )throws Exception{
        Path directory=
            root.toPath()
                .resolve("spk")
                .resolve("plugin")
                .resolve("api");

        if(!Files.isDirectory(directory))
            throw new IllegalStateException(
                "plugin API directory missing: "+
                directory
            );

        try(DirectoryStream<Path> entries=
                Files.newDirectoryStream(
                    directory,
                    "*.class"
                )){
            for(Path entry:entries){
                String fileName=
                    entry.getFileName()
                        .toString();

                if(fileName.indexOf('$')>=0)
                    continue;

                addIfPublicTopLevel(
                    "spk.plugin.api."+
                    fileName.substring(
                        0,
                        fileName.length()-6
                    ),
                    names
                );
            }
        }
    }

    private static void discoverJar(
        File source,
        Set<String> names
    )throws Exception{
        final String prefix=
            "spk/plugin/api/";

        try(JarFile jar=
                new JarFile(source)){
            Enumeration<JarEntry> entries=
                jar.entries();

            while(entries.hasMoreElements()){
                JarEntry entry=
                    entries.nextElement();

                if(entry.isDirectory())
                    continue;

                String name=
                    entry.getName();

                if(!name.startsWith(prefix)||
                   !name.endsWith(".class"))
                    continue;

                String remainder=
                    name.substring(
                        prefix.length(),
                        name.length()-6
                    );

                if(remainder.isEmpty()||
                   remainder.indexOf('/')>=0||
                   remainder.indexOf('$')>=0)
                    continue;

                addIfPublicTopLevel(
                    "spk.plugin.api."+
                    remainder,
                    names
                );
            }
        }
    }

    private static void addIfPublicTopLevel(
        String binaryName,
        Set<String> names
    )throws Exception{
        Class<?> type=
            Class.forName(
                binaryName,
                false,
                Plugin.class
                    .getClassLoader()
            );

        if(Modifier.isPublic(
                type.getModifiers())&&
           type.getEnclosingClass()==null&&
           !type.isSynthetic())
            names.add(binaryName);
    }

    private static void inspectName(
        String name,
        String location,
        List<String> violations
    ){
        String lower=
            name.toLowerCase(
                Locale.ROOT
            );

        if(lower.contains("opcode")||
           lower.contains("packet")||
           lower.contains("isaac")||
           lower.contains("socket")||
           lower.contains("widget")||
           lower.contains("sceneindex")||
           lower.contains("playerindex")||
           lower.contains("protocolindex")||
           lower.contains("registry"))
            violations.add(
                location+
                " exposes internal transport/presentation identity"
            );
    }

    private static void inspect(
        Type type,
        String location,
        List<String> violations
    ){
        if(type instanceof Class<?>){
            inspectClass(
                (Class<?>)type,
                location,
                violations
            );
            return;
        }

        if(type instanceof ParameterizedType){
            ParameterizedType parameterized=
                (ParameterizedType)type;

            inspect(
                parameterized.getRawType(),
                location,
                violations
            );

            for(Type argument:
                    parameterized
                        .getActualTypeArguments())
                inspect(
                    argument,
                    location,
                    violations
                );
            return;
        }

        if(type instanceof GenericArrayType){
            inspect(
                ((GenericArrayType)type)
                    .getGenericComponentType(),
                location,
                violations
            );
            return;
        }

        if(type instanceof WildcardType){
            WildcardType wildcard=
                (WildcardType)type;

            for(Type upper:
                    wildcard.getUpperBounds())
                inspect(
                    upper,
                    location,
                    violations
                );

            for(Type lower:
                    wildcard.getLowerBounds())
                inspect(
                    lower,
                    location,
                    violations
                );
            return;
        }

        if(type instanceof TypeVariable<?>)
            for(Type bound:
                    ((TypeVariable<?>)type)
                        .getBounds())
                inspect(
                    bound,
                    location,
                    violations
                );
    }

    private static void inspectClass(
        Class<?> type,
        String location,
        List<String> violations
    ){
        if(type.isArray()){
            inspectClass(
                type.getComponentType(),
                location,
                violations
            );
            return;
        }

        if(type.isPrimitive()||
           type==Void.TYPE)
            return;

        String name=type.getName();

        if(name.startsWith("spk.local."))
            violations.add(
                location+
                " -> internal runtime type "+
                name
            );

        if(name.startsWith("java.net.")||
           name.startsWith("java.io.")||
           name.startsWith(
               "java.nio.channels."))
            violations.add(
                location+
                " -> transport/network type "+
                name
            );

        if(FORBIDDEN_SIMPLE_NAMES.contains(
                type.getSimpleName()))
            violations.add(
                location+
                " -> forbidden runtime/transport type "+
                name
            );
    }

    private PluginPublicApiBoundaryTest(){}
}
