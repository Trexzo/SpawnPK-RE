package spk.local;

import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import spk.content.api.*;

public final class ContentPublicApiBoundaryTest {
    private static final Class<?>[] API_TYPES={
        ContentActionContext.class,
        ContentActionHandler.class,
        ContentActionResult.class,
        ContentCommandContext.class,
        ContentCommandHandler.class,
        ContentDialogueContext.class,
        ContentDialogueDefinition.class,
        ContentDialogueHandler.class,
        ContentDialogueNode.class,
        ContentDialogueIntent.class,
        ContentDialoguePresentation.class,
        ContentDialogueTransition.class,
        ContentInteractionResult.class,
        ContentItemOnGroundItemContext.class,
        ContentItemOnGroundItemHandler.class,
        ContentItemOnItemContext.class,
        ContentItemOnItemHandler.class,
        ContentItemOnNpcContext.class,
        ContentItemOnNpcHandler.class,
        ContentItemOnObjectContext.class,
        ContentItemOnObjectHandler.class,
        ContentItemOnPlayerContext.class,
        ContentItemOnPlayerHandler.class,
        ContentItemOptionContext.class,
        ContentItemOptionHandler.class,
        ContentModule.class,
        ContentNpcOptionContext.class,
        ContentNpcOptionHandler.class,
        ContentNpcOptionResult.class,
        ContentNpcService.class,
        ContentObjectOptionContext.class,
        ContentObjectOptionHandler.class,
        ContentPlayer.class,
        ContentPresentation.class,
        ContentPresentationException.class,
        ContentPrayerBook.class,
        ContentProvenance.class,
        ContentRegistrar.class,
        ContentRegistration.class,
        ContentResult.class,
        ContentSkill.class,
        ContentSpellBook.class
    };

    private static final Set<String> FORBIDDEN_SIMPLE_NAMES=
        new HashSet<>(
            Arrays.asList(
                "Socket",
                "IsaacCipher",
                "ClientPacketProbe",
                "ServerPacketWriter",
                "ClientRequest",
                "WorldPlayer",
                "NpcEntity",
                "NpcRegistry"
            )
        );

    public static void main(String[] args){
        ArrayList<String> violations=
            new ArrayList<>();

        assertReconstructionIdentityGuards(
            violations
        );
        assertApiTypeCoverage(violations);
        assertInheritedSurfaceGuard(violations);

        for(Class<?> api:API_TYPES){
            if(!Modifier.isPublic(api.getModifiers()))
                violations.add(
                    api.getName()+
                    " is not public"
                );

            inspectInheritedSurface(api,violations);

            for(Method method:
                    api.getDeclaredMethods()){
                if(!Modifier.isPublic(
                        method.getModifiers()))
                    continue;

                String location=
                    api.getName()+
                    "#"+
                    method.getName();

                if("sceneIndex".equals(
                        method.getName()))
                    violations.add(
                        location+
                        " exposes viewer-local scene identity"
                    );

                if("protocolIndex".equals(
                        method.getName())||
                   "playerIndex".equals(
                        method.getName()))
                    violations.add(
                        location+
                        " exposes raw protocol index"
                    );

                String methodName=
                    method.getName()
                        .toLowerCase(Locale.ROOT);

                if("percentageText".equals(
                        method.getName())||
                   methodName.contains("widget"))
                    violations.add(
                        location+
                        " exposes raw widget presentation identity"
                    );

                if(exposesRawInventorySlotIdentity(
                        methodName))
                    violations.add(
                        location+
                        " exposes raw inventory/container slot identity"
                    );

                if(exposesRawContainerIdentity(
                        methodName))
                    violations.add(
                        location+
                        " exposes raw container identity"
                    );

                if(exposesRawCacheIdentity(
                        methodName))
                    violations.add(
                        location+
                        " exposes raw cache/archive identity"
                    );

                if(methodName.contains("opcode")||
                   methodName.contains("schema")||
                   methodName.contains("packet"))
                    violations.add(
                        location+
                        " exposes raw transport identity"
                    );

                inspect(
                    method.getGenericReturnType(),
                    location+
                    " return",
                    violations
                );

                Type[] parameters=
                    method.getGenericParameterTypes();

                for(int i=0;i<parameters.length;i++)
                    inspect(
                        parameters[i],
                        location+
                        " param["+
                        i+
                        "]",
                        violations
                    );

                for(Class<?> exceptionType:
                        method.getExceptionTypes())
                    inspectClass(
                        exceptionType,
                        location+
                        " throws",
                        violations
                    );
            }

            for(Field field:
                    api.getDeclaredFields()){
                if(!Modifier.isPublic(
                        field.getModifiers()))
                    continue;

                String fieldName=
                    field.getName()
                        .toLowerCase(Locale.ROOT);

                if(exposesRawInventorySlotIdentity(
                        fieldName))
                    violations.add(
                        api.getName()+
                        "#"+
                        field.getName()+
                        " exposes raw inventory/container slot identity"
                    );

                if(exposesRawContainerIdentity(
                        fieldName))
                    violations.add(
                        api.getName()+
                        "#"+
                        field.getName()+
                        " exposes raw container identity"
                    );

                if(exposesRawCacheIdentity(
                        fieldName))
                    violations.add(
                        api.getName()+
                        "#"+
                        field.getName()+
                        " exposes raw cache/archive identity"
                    );

                inspect(
                    field.getGenericType(),
                    api.getName()+
                    "#"+
                    field.getName()+
                    " field",
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
                        " ctor param["+
                        i+
                        "]",
                        violations
                    );
            }
        }

        if(!violations.isEmpty())
            throw new AssertionError(
                "public content API boundary violations="+
                violations
            );

        System.out.println(
            "CONTENT_PUBLIC_API_BOUNDARY_PASS "+
            "types="+API_TYPES.length+" "+
            "spkLocalLeak=false "+
            "javaNetLeak=false "+
            "transportTypeLeak=false "+
            "sceneIndex=false "+
            "protocolIndex=false "+
            "widgetIdentity=false "+
            "inventorySlotIdentity=false "+
            "containerIdentity=false "+
            "cacheIdentity=false "+
            "apiCoverageComplete=true "+
            "inheritedSurface=true "+
            "javaIoLeak=false"
        );
    }

    private static void assertApiTypeCoverage(
        List<String> violations
    ){
        Set<String> declared=
            new TreeSet<>();

        for(Class<?> api:API_TYPES)
            declared.add(api.getName());

        Set<String> discovered=
            discoverPublicApiTypes();

        for(String name:discovered)
            if(!declared.contains(name))
                violations.add(
                    name+
                    " public content API type missing from boundary audit"
                );

        for(String name:declared)
            if(!discovered.contains(name))
                violations.add(
                    name+
                    " boundary audit entry is not a public top-level content API type"
                );
    }

    private static Set<String> discoverPublicApiTypes(){
        TreeSet<String> names=
            new TreeSet<>();

        try{
            URL location=
                ContentRegistrar.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation();

            if(location==null)
                throw new IllegalStateException(
                    "content API code source unavailable"
                );

            File source=
                new File(location.toURI());

            if(source.isDirectory())
                discoverDirectoryApiTypes(
                    source,
                    names
                );
            else
                discoverJarApiTypes(
                    source,
                    names
                );
        }catch(Exception error){
            throw new AssertionError(
                "could not discover public content API types",
                error
            );
        }

        if(names.isEmpty())
            throw new AssertionError(
                "no public spk.content.api types discovered"
            );

        return names;
    }

    private static void discoverDirectoryApiTypes(
        File root,
        Set<String> names
    )throws Exception{
        Path directory=
            root.toPath()
                .resolve("spk")
                .resolve("content")
                .resolve("api");

        if(!Files.isDirectory(directory))
            throw new IllegalStateException(
                "content API directory missing: "+
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

                String simpleName=
                    fileName.substring(
                        0,
                        fileName.length()-6
                    );

                addIfPublicTopLevel(
                    "spk.content.api."+
                    simpleName,
                    names
                );
            }
        }
    }

    private static void discoverJarApiTypes(
        File source,
        Set<String> names
    )throws Exception{
        if(!source.isFile())
            throw new IllegalStateException(
                "content API code source is not a directory or jar: "+
                source
            );

        final String prefix=
            "spk/content/api/";

        try(JarFile jar=new JarFile(source)){
            Enumeration<JarEntry> entries=
                jar.entries();

            while(entries.hasMoreElements()){
                JarEntry entry=
                    entries.nextElement();

                if(entry.isDirectory())
                    continue;

                String name=entry.getName();

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
                    "spk.content.api."+
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
                ContentRegistrar.class
                    .getClassLoader()
            );

        if(Modifier.isPublic(type.getModifiers())&&
           type.getEnclosingClass()==null&&
           !type.isSynthetic())
            names.add(binaryName);
    }

    private static boolean exposesRawInventorySlotIdentity(
        String lower
    ){
        return "slot".equals(lower)||
            "inventoryslot".equals(lower)||
            "containerslot".equals(lower)||
            "itemslot".equals(lower)||
            "selectedslot".equals(lower)||
            "targetslot".equals(lower)||
            "sourceslot".equals(lower)||
            "destinationslot".equals(lower);
    }

    private static boolean exposesRawContainerIdentity(
        String lower
    ){
        return lower.contains("containerid")||
            "sourcecontainer".equals(lower)||
            "targetcontainer".equals(lower)||
            "selectedcontainer".equals(lower)||
            "destinationcontainer".equals(lower);
    }

    private static boolean exposesRawCacheIdentity(
        String lower
    ){
        return lower.contains("cacheoffset")||
            lower.contains("archiveoffset");
    }

    private static void assertReconstructionIdentityGuards(
        List<String> violations
    ){
        for(String raw:
                Arrays.asList(
                    "containerid",
                    "sourcecontainer",
                    "targetcontainerid",
                    "destinationcontainer",
                    "cacheoffset",
                    "archiveoffset"
                ))
            if(!exposesRawContainerIdentity(raw)&&
               !exposesRawCacheIdentity(raw))
                violations.add(
                    "raw reconstruction identity guard missed "+
                    raw
                );

        for(String semantic:
                Arrays.asList(
                    "itemid",
                    "objectid",
                    "npcdefinitionid",
                    "worldx",
                    "commandname"
                ))
            if(exposesRawContainerIdentity(semantic)||
               exposesRawCacheIdentity(semantic))
                violations.add(
                    "semantic API name falsely rejected "+
                    semantic
                );
    }


    private static void assertInheritedSurfaceGuard(
        List<String> violations
    ){
        ArrayList<String> inheritedMethodProbe=
            new ArrayList<>();

        inspectInheritedSurface(
            InheritedTransportProbe.class,
            inheritedMethodProbe
        );

        if(inheritedMethodProbe.isEmpty())
            violations.add(
                "inherited public method transport leak guard is inactive"
            );

        ArrayList<String> genericSuperProbe=
            new ArrayList<>();

        inspectInheritedSurface(
            InheritedGenericProbe.class,
            genericSuperProbe
        );

        if(genericSuperProbe.isEmpty())
            violations.add(
                "generic supertype transport leak guard is inactive"
            );

        ArrayList<String> nestedGenericProbe=
            new ArrayList<>();

        inspectInheritedSurface(
            InheritedNestedTransportProbe.class,
            nestedGenericProbe
        );

        if(nestedGenericProbe.isEmpty())
            violations.add(
                "recursive generic supertype transport leak guard is inactive"
            );
    }

    private static void inspectInheritedSurface(
        Class<?> api,
        List<String> violations
    ){
        Type superType=
            api.getGenericSuperclass();

        if(superType!=null&&
           superType!=Object.class)
            inspectInheritedTypeHierarchy(
                superType,
                api.getName()+" generic superclass",
                violations
            );

        Type[] interfaces=
            api.getGenericInterfaces();

        for(int i=0;i<interfaces.length;i++)
            inspectInheritedTypeHierarchy(
                interfaces[i],
                api.getName()+
                " generic interface["+
                i+
                "]",
                violations
            );

        for(Method method:api.getMethods()){
            Class<?> declaring=
                method.getDeclaringClass();

            if(declaring==api||
               declaring.getName().startsWith("java.")||
               !Modifier.isPublic(
                    method.getModifiers()))
                continue;

            String location=
                api.getName()+
                " inherited "+
                method.getDeclaringClass().getName()+
                "#"+
                method.getName();

            inspectInheritedName(
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
                    " param["+
                    i+
                    "]",
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
    }

    private static void inspectInheritedName(
        String name,
        String location,
        List<String> violations
    ){
        String lower=
            name.toLowerCase(Locale.ROOT);

        if("sceneIndex".equals(name)||
           "protocolIndex".equals(name)||
           "playerIndex".equals(name)||
           "percentageText".equals(name)||
           lower.contains("widget")||
           exposesRawInventorySlotIdentity(lower)||
           exposesRawContainerIdentity(lower)||
           exposesRawCacheIdentity(lower)||
           lower.contains("opcode")||
           lower.contains("schema")||
           lower.contains("packet"))
            violations.add(
                location+
                " exposes raw transport/presentation identity"
            );
    }

    private interface InheritedTransportContract {
        Socket socket();
    }

    private abstract static class InheritedTransportProbe
            implements InheritedTransportContract {}

    private static class InheritedGenericBase<T> {}

    private static final class InheritedGenericProbe
            extends InheritedGenericBase<Socket> {}

    private interface InheritedGenericCarrier<T> {}

    private interface InheritedNestedTransportContract
            extends InheritedGenericCarrier<Socket> {}

    private abstract static class InheritedNestedTransportProbe
            implements InheritedNestedTransportContract {}

    private static void inspectInheritedTypeHierarchy(
        Type type,
        String location,
        List<String> violations
    ){
        Set<Type> visiting=
            Collections.newSetFromMap(
                new IdentityHashMap<Type,Boolean>()
            );

        inspectInheritedTypeHierarchy(
            type,
            location,
            violations,
            visiting
        );
    }

    private static void inspectInheritedTypeHierarchy(
        Type type,
        String location,
        List<String> violations,
        Set<Type> visiting
    ){
        if(type==null||
           !visiting.add(type))
            return;

        try{
            inspect(
                type,
                location,
                violations
            );

            Class<?> rawType=null;

            if(type instanceof Class<?>)
                rawType=(Class<?>)type;
            else if(type instanceof ParameterizedType){
                Type raw=
                    ((ParameterizedType)type)
                        .getRawType();

                if(raw instanceof Class<?>)
                    rawType=(Class<?>)raw;
            }

            if(rawType==null||
               rawType.getName().startsWith("java."))
                return;

            Type parent=
                rawType.getGenericSuperclass();

            if(parent!=null&&
               parent!=Object.class)
                inspectInheritedTypeHierarchy(
                    parent,
                    location+
                    " -> generic superclass",
                    violations,
                    visiting
                );

            Type[] parents=
                rawType.getGenericInterfaces();

            for(int i=0;i<parents.length;i++)
                inspectInheritedTypeHierarchy(
                    parents[i],
                    location+
                    " -> generic interface["+
                    i+
                    "]",
                    violations,
                    visiting
                );
        }finally{
            visiting.remove(type);
        }
    }

    private static void inspect(
        Type type,
        String location,
        List<String> violations
    ){
        Set<Type> visiting=
            Collections.newSetFromMap(
                new IdentityHashMap<Type,Boolean>()
            );

        inspect(
            type,
            location,
            violations,
            visiting
        );
    }

    private static void inspect(
        Type type,
        String location,
        List<String> violations,
        Set<Type> visiting
    ){
        if(type==null||
           !visiting.add(type))
            return;

        try{
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
                    violations,
                    visiting
                );

                for(Type argument:
                        parameterized
                            .getActualTypeArguments())
                    inspect(
                        argument,
                        location,
                        violations,
                        visiting
                    );

                return;
            }

            if(type instanceof GenericArrayType){
                inspect(
                    ((GenericArrayType)type)
                        .getGenericComponentType(),
                    location,
                    violations,
                    visiting
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
                        violations,
                        visiting
                    );

                for(Type lower:
                        wildcard.getLowerBounds())
                    inspect(
                        lower,
                        location,
                        violations,
                        visiting
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
                        violations,
                        visiting
                    );
        }finally{
            visiting.remove(type);
        }
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
        String simple=type.getSimpleName();

        if(name.startsWith("spk.local."))
            violations.add(
                location+
                " -> internal runtime type "+
                name
            );

        if(name.startsWith("java.net.")||
           name.startsWith("java.nio.channels."))
            violations.add(
                location+
                " -> network type "+
                name
            );

        if(name.startsWith("java.io."))
            violations.add(
                location+
                " -> transport I/O type "+
                name
            );

        if(FORBIDDEN_SIMPLE_NAMES.contains(
                simple))
            violations.add(
                location+
                " -> forbidden transport/runtime type "+
                name
            );
    }

    private ContentPublicApiBoundaryTest(){}
}
