package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;

public final class AppFixtureContentOwnershipTest {
    public static void main(String[] args)throws Exception{
        defaultPresentationFailsClosed();
        contentWireParity();
        helpParity();

        System.out.println(
            "APPFIXTURE_CONTENT_OWNERSHIP_PASS "+
            "bindingOwned=true "+
            "wireParity=true "+
            "logParity=true "+
            "helpParity=true "+
            "rawPacketAccess=false "+
            "defaultFailsClosed=true"
        );
    }

    private static void defaultPresentationFailsClosed(){
        ContentPresentation alternate=
            new ContentPresentation(){
                @Override public void skill(
                    ContentSkill skill,
                    int experience,
                    int currentLevel
                ){}

                @Override public void runEnergy(
                    int energy
                ){}

                @Override public void specialEnergy(
                    int percent
                ){}

                @Override public void animationAndGfx(
                    int animationId,
                    int gfxId,
                    int gfxHeight,
                    int gfxDelay
                ){}
            };

        boolean rejected=false;

        try{
            alternate.applicationFixture(
                "makex"
            );
        }catch(UnsupportedOperationException expected){
            rejected=
                expected.getMessage()!=null&&
                expected.getMessage().contains(
                    "application fixture presentation unavailable"
                );
        }

        require(
            rejected,
            "alternate presentation did not fail closed"
        );
    }

    private static void contentWireParity()
        throws Exception
    {
        World world=
            World.isolatedForTest(20L);
        WorldPlayer player=
            new WorldPlayer();

        try{
            ContentRegistry.BindingInfo binding=
                world.content()
                    .commandBinding(
                        "appfixture"
                    );

            require(
                binding!=null&&
                "locallab-core".equals(
                    binding.moduleId)&&
                binding.priority==100&&
                binding.provenance==
                    ContentProvenance.CUSTOM_LOCALLAB,
                "appfixture binding="+binding
            );

            world.registerPlayer(
                player,
                "appfixture-content-owner"
            );
            world.start();

            ByteArrayOutputStream actualWire=
                new ByteArrayOutputStream();
            ByteArrayOutputStream expectedWire=
                new ByteArrayOutputStream();

            ServerPacketWriter actual=
                writer(actualWire);
            ServerPacketWriter expected=
                writer(expectedWire);

            AtomicReference<ContentResult> result=
                new AtomicReference<>();
            AtomicReference<Throwable> failure=
                new AtomicReference<>();

            world.submitAndWait(
                player,
                ()->{
                    try{
                        result.set(
                            world.content()
                                .dispatchCommand(
                                    player,
                                    "::appfixture makex ignored-extra",
                                    actual
                                )
                        );
                    }catch(Throwable error){
                        failure.set(error);
                    }
                },
                5_000L
            );

            if(failure.get()!=null)
                throw new AssertionError(
                    "content appfixture dispatch failed",
                    failure.get()
                );

            String expectedFixture=
                ApplicationUiFixtureService.run(
                    "makex",
                    expected
                );

            actual.flush();
            expected.flush();

            ContentResult content=result.get();

            require(
                content!=null&&
                content.saveReason()==null&&
                (
                    "R85_APP_FIXTURE "+
                    expectedFixture+
                    " authority=LOCAL_DEV_FIXTURE clientProtocol=EXACT_CURRENT"
                ).equals(
                    content.logText()),
                "content appfixture result="+content
            );

            require(
                Arrays.equals(
                    actualWire.toByteArray(),
                    expectedWire.toByteArray()
                ),
                "appfixture makex wire mismatch"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(player);
            world.close();
        }
    }

    private static void helpParity()
        throws Exception
    {
        World world=
            World.isolatedForTest(20L);
        WorldPlayer player=
            new WorldPlayer();

        try{
            world.registerPlayer(
                player,
                "appfixture-help-owner"
            );
            world.start();

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter packets=
                writer(wire);
            AtomicReference<ContentResult> result=
                new AtomicReference<>();

            world.submitAndWait(
                player,
                ()->{
                    try{
                        result.set(
                            world.content()
                                .dispatchCommand(
                                    player,
                                    "::appfixture",
                                    packets
                                )
                        );
                    }catch(Exception failure){
                        throw new RuntimeException(
                            failure
                        );
                    }
                },
                5_000L
            );

            packets.flush();

            require(
                result.get()!=null&&
                result.get().logText()!=null&&
                result.get().logText().contains(
                    "usage: ::appfixture")&&
                result.get().logText().contains(
                    "authority=LOCAL_DEV_FIXTURE")&&
                wire.size()==0,
                "appfixture help parity result="+
                result.get()+
                " wire="+wire.size()
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(player);
            world.close();
        }
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream wire
    ){
        return new ServerPacketWriter(
            wire,
            new IsaacCipher(
                new int[]{131,132,133,134}
            )
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private AppFixtureContentOwnershipTest(){}
}
