package spk.local;

import java.lang.reflect.*;
import java.util.*;
import spk.content.api.*;
import spk.content.builtin.RuntimeProvenNpcInteractionModule;

public final class ContentProvenanceAuthorityBoundaryTest {
    public interface InheritedProvenanceAssignmentParent {
        void assign(ContentProvenance provenance);
    }

    public interface InheritedProvenanceAssignmentChild
        extends InheritedProvenanceAssignmentParent {
    }

    public static void main(String[] args)throws Exception{
        assertPublicApiCannotAssignProvenance();
        assertInheritedProvenanceAssignmentIsDetected();
        assertTrustedInstallIsCoreInternal();
        assertCustomRegistrationsAreForcedCustom();

        System.out.println(
            "CONTENT_PROVENANCE_AUTHORITY_BOUNDARY_PASS "+
            "publicAssignment=false "+
            "inheritedAssignmentGuard=true "+
            "trustedInstallPublic=false "+
            "customCommand=CUSTOM_LOCALLAB "+
            "customObject=CUSTOM_LOCALLAB "+
            "customItem=CUSTOM_LOCALLAB "+
            "customItemOnGroundItem=CUSTOM_LOCALLAB "+
            "customItemOnItem=CUSTOM_LOCALLAB "+
            "customItemOnNpc=CUSTOM_LOCALLAB "+
            "customItemOnObject=CUSTOM_LOCALLAB "+
            "customItemOnPlayer=CUSTOM_LOCALLAB "+
            "customNpc=CUSTOM_LOCALLAB "+
            "trustedRuntimeProven=true"
        );
    }

    private static void assertPublicApiCannotAssignProvenance(){
        List<String> violations=
            provenanceAssignmentViolations(
                ContentModule.class,
                ContentRegistrar.class
            );

        if(!violations.isEmpty())
            throw new AssertionError(
                "content provenance assignment leaked into public API "+
                violations
            );
    }

    private static void assertInheritedProvenanceAssignmentIsDetected(){
        List<String> violations=
            provenanceAssignmentViolations(
                InheritedProvenanceAssignmentChild.class
            );

        if(violations.size()!=1||
           !violations.get(0).contains(
               "#assign accepts ContentProvenance"))
            throw new AssertionError(
                "inherited provenance assignment escaped guard "+
                violations
            );
    }

    private static List<String> provenanceAssignmentViolations(
        Class<?>... apiTypes
    ){
        ArrayList<String> violations=
            new ArrayList<>();

        for(Class<?> api:apiTypes){
            for(Method method:
                    api.getMethods()){
                if(!Modifier.isPublic(
                        method.getModifiers()))
                    continue;

                if(method.getReturnType()==
                        ContentProvenance.class)
                    violations.add(
                        api.getName()+
                        "#"+method.getName()+
                        " returns ContentProvenance"
                    );

                for(Class<?> parameter:
                        method.getParameterTypes())
                    if(parameter==
                            ContentProvenance.class)
                        violations.add(
                            api.getName()+
                            "#"+method.getName()+
                            " accepts ContentProvenance"
                        );
            }
        }

        return violations;
    }

    private static void assertTrustedInstallIsCoreInternal()
        throws Exception{
        if(Modifier.isPublic(
                ContentRegistry.class
                    .getModifiers()))
            throw new AssertionError(
                "ContentRegistry became public plugin surface"
            );

        Method trusted=
            ContentRegistry.class
                .getDeclaredMethod(
                    "installTrusted",
                    ContentModule.class,
                    ContentProvenance.class
                );

        if(Modifier.isPublic(
                trusted.getModifiers()))
            throw new AssertionError(
                "installTrusted became public"
            );
    }

    private static void assertCustomRegistrationsAreForcedCustom()
        throws Exception{
        World world=
            World.isolatedForTest(
                20L
            );

        try{
            final String misleadingModuleId=
                "EXACT_CURRENT_CLIENT";

            world.content()
                .installCustom(
                    new ContentModule(){
                        @Override public String id(){
                            return misleadingModuleId;
                        }

                        @Override public void register(
                            ContentRegistrar registrar
                        ){
                            registrar.command(
                                "provenanceguard",
                                321,
                                context->
                                    ContentResult.handled(
                                        "CUSTOM_ONLY",
                                        null
                                    )
                            );

                            registrar.objectOption(
                                54_321,
                                2,
                                321,
                                context->
                                    ContentInteractionResult
                                        .handled(
                                            "CUSTOM_ONLY"
                                        )
                            );

                            registrar.itemOption(
                                65_432,
                                1,
                                321,
                                context->
                                    ContentInteractionResult
                                        .handled(
                                            "CUSTOM_ONLY"
                                        )
                            );

                            registrar.itemOnGroundItem(
                                65_436,
                                65_437,
                                321,
                                context->
                                    ContentInteractionResult
                                        .handled(
                                            "CUSTOM_ONLY"
                                        )
                            );

                            registrar.itemOnItem(
                                65_438,
                                65_439,
                                321,
                                context->
                                    ContentInteractionResult
                                        .handled(
                                            "CUSTOM_ONLY"
                                        )
                            );

                            registrar.itemOnNpc(
                                65_433,
                                12_346,
                                321,
                                context->
                                    ContentInteractionResult
                                        .handled(
                                            "CUSTOM_ONLY"
                                        )
                            );

                            registrar.itemOnObject(
                                65_434,
                                54_322,
                                321,
                                context->
                                    ContentInteractionResult
                                        .handled(
                                            "CUSTOM_ONLY"
                                        )
                            );

                            registrar.itemOnPlayer(
                                65_435,
                                321,
                                context->
                                    ContentInteractionResult
                                        .handled(
                                            "CUSTOM_ONLY"
                                        )
                            );

                            registrar.npcOption(
                                12_345,
                                4,
                                321,
                                context->
                                    ContentNpcOptionResult
                                        .handled(
                                            ContentNpcService.TALK
                                        )
                            );
                        }
                    }
                );

            assertCustom(
                "command",
                world.content()
                    .commandBinding(
                        "provenanceguard"
                    ),
                misleadingModuleId
            );

            assertCustom(
                "object",
                world.content()
                    .objectOptionBinding(
                        54_321,
                        2
                    ),
                misleadingModuleId
            );

            assertCustom(
                "item",
                world.content()
                    .itemOptionBinding(
                        65_432,
                        1
                    ),
                misleadingModuleId
            );

            assertCustom(
                "itemOnGroundItem",
                world.content()
                    .itemOnGroundItemBinding(
                        65_436,
                        65_437
                    ),
                misleadingModuleId
            );

            assertCustom(
                "itemOnItem",
                world.content()
                    .itemOnItemBinding(
                        65_438,
                        65_439
                    ),
                misleadingModuleId
            );

            assertCustom(
                "itemOnNpc",
                world.content()
                    .itemOnNpcBinding(
                        65_433,
                        12_346
                    ),
                misleadingModuleId
            );

            assertCustom(
                "itemOnObject",
                world.content()
                    .itemOnObjectBinding(
                        65_434,
                        54_322
                    ),
                misleadingModuleId
            );

            assertCustom(
                "itemOnPlayer",
                world.content()
                    .itemOnPlayerBinding(
                        65_435
                    ),
                misleadingModuleId
            );

            assertCustom(
                "npc",
                world.content()
                    .npcOptionBinding(
                        12_345,
                        4
                    ),
                misleadingModuleId
            );

            ContentRegistry.BindingInfo trusted=
                world.content()
                    .npcOptionBinding(
                        RuntimeProvenNpcInteractionModule
                            .BANKER_7605,
                        1
                    );

            if(trusted==null||
               trusted.provenance!=
                    ContentProvenance
                        .LOCAL_RUNTIME_PROVEN)
                throw new AssertionError(
                    "core trusted provenance changed "+
                    trusted
                );
        }finally{
            world.close();
        }
    }

    private static void assertCustom(
        String kind,
        ContentRegistry.BindingInfo binding,
        String expectedModuleId
    ){
        if(binding==null)
            throw new AssertionError(
                kind+" binding missing"
            );

        if(!expectedModuleId.equals(
                binding.moduleId))
            throw new AssertionError(
                kind+" module id changed "+
                binding
            );

        if(binding.provenance!=
                ContentProvenance.CUSTOM_LOCALLAB)
            throw new AssertionError(
                kind+
                " custom module self-promoted authority "+
                binding
            );
    }
}
