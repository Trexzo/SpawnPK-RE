package spk.local;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class NurseContentOwnershipTest {
    public static void main(String[] args)throws Exception{
        noLegacyRuntimeDependency();
        contentBindingOwnsNurse();

        System.out.println(
            "NURSE_CONTENT_OWNERSHIP_PASS "+
            "legacyDispatcherDependency=false "+
            "legacyBridgeScopesightHook=false "+
            "module=locallab-core "+
            "provenance=CUSTOM_LOCALLAB "+
            "legacyParityOracleRetained=true"
        );
    }

    private static void noLegacyRuntimeDependency(){
        for(Field field:
                LocalCommandDispatcher.class
                    .getDeclaredFields())
            if(field.getType()==
                    LocalNurseCommandHandler.class)
                throw new AssertionError(
                    "runtime dispatcher still owns legacy Nurse field "+
                    field.getName()
                );

        for(Constructor<?> constructor:
                LocalCommandDispatcher.class
                    .getDeclaredConstructors())
            for(Class<?> parameter:
                    constructor.getParameterTypes())
                if(parameter==
                        LocalNurseCommandHandler.class)
                    throw new AssertionError(
                        "runtime dispatcher still requires legacy Nurse constructor dependency"
                    );

        for(Method method:
                LocalCommandDispatcher
                    .SessionBridge.class
                    .getDeclaredMethods())
            if(method.getName().equals(
                    "scopesightActive"))
                throw new AssertionError(
                    "Nurse-only scopesight bridge hook still exposed"
                );
    }

    private static void contentBindingOwnsNurse()
        throws Exception
    {
        World world=
            World.isolatedForTest(20L);

        try{
            world.start();

            ContentRegistry.BindingInfo binding=
                world.content()
                    .commandBinding(
                        "nurse"
                    );

            if(binding==null)
                throw new AssertionError(
                    "content Nurse binding missing"
                );

            if(!"command".equals(
                    binding.kind))
                throw new AssertionError(
                    "Nurse binding kind="+
                    binding.kind
                );

            if(!"nurse".equals(
                    binding.key))
                throw new AssertionError(
                    "Nurse binding key="+
                    binding.key
                );

            if(!"locallab-core".equals(
                    binding.moduleId))
                throw new AssertionError(
                    "Nurse module="+
                    binding.moduleId
                );

            if(binding.priority!=100)
                throw new AssertionError(
                    "Nurse priority="+
                    binding.priority
                );

            if(!"CUSTOM_LOCALLAB".equals(
                    binding.provenance.name()))
                throw new AssertionError(
                    "Nurse provenance="+
                    binding.provenance
                );
        }finally{
            world.close();
        }
    }

    private NurseContentOwnershipTest(){}
}
