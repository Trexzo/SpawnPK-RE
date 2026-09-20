package spk.local;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;

public final class ContentNpcOptionPublicBoundaryTest {
    public static void main(String[] args)throws Exception{
        TreeSet<String> methods=
            new TreeSet<>();

        for(Method method:
                ContentNpcOptionContext.class
                    .getDeclaredMethods())
            methods.add(
                method.getName()
            );

        TreeSet<String> expected=
            new TreeSet<>(
                Arrays.asList(
                    "npcDefinitionId",
                    "option",
                    "worldX",
                    "worldY"
                )
            );

        if(!methods.equals(expected))
            throw new AssertionError(
                "public NPC option context surface="+
                methods
            );

        if(methods.contains("sceneIndex"))
            throw new AssertionError(
                "viewer-local scene index leaked into public content API"
            );

        World world=
            World.isolatedForTest(
                20L
            );

        WorldPlayer player=
            new WorldPlayer();

        AtomicReference<String> observed=
            new AtomicReference<>();

        try{
            world.content()
                .installCustom(
                    new ContentModule(){
                        @Override public String id(){
                            return "npc-public-boundary-test";
                        }

                        @Override public void register(
                            ContentRegistrar registrar
                        ){
                            registrar.npcOption(
                                12_345,
                                2,
                                500,
                                context -> {
                                    observed.set(
                                        context.npcDefinitionId()+
                                        ":"+
                                        context.option()+
                                        "@"+
                                        context.worldX()+
                                        ","+
                                        context.worldY()
                                    );

                                    return ContentNpcOptionResult
                                        .handled(
                                            ContentNpcService.TALK
                                        );
                                }
                            );
                        }
                    }
                );

            world.registerPlayer(
                player,
                "boundarytest"
            );

            world.start();

            AtomicReference<ContentNpcOptionResult>
                result=
                    new AtomicReference<>();

            world.submitAndWait(
                player,
                () -> result.set(
                    world.content()
                        .dispatchNpcOption(
                            12_345,
                            2,
                            3_200,
                            3_201
                        )
                ),
                5_000L
            );

            if(result.get()==null||
               result.get().service()!=
                    ContentNpcService.TALK)
                throw new AssertionError(
                    "semantic NPC option result="+
                    result.get()
                );

            if(!"12345:2@3200,3201"
                    .equals(observed.get()))
                throw new AssertionError(
                    "semantic context="+
                    observed.get()
                );

            System.out.println(
                "CONTENT_NPC_OPTION_PUBLIC_BOUNDARY_PASS "+
                "methods="+methods.size()+" "+
                "sceneIndex=false "+
                "semanticDispatch=true"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(
                    player
                );

            world.close();
        }
    }
}
