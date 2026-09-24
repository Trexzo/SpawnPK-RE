package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;
import spk.content.builtin.LocalLabCoreContentModule;

public final class BankObjectContentOwnershipTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();

        try{
            ContentRegistry registry=
                world.content();

            if(LocalLabCoreContentModule.BANK_OBJECT!=
                    BankState.BANK_OBJECT_ID)
                throw new AssertionError(
                    "content/runtime bank object identity drift content="+
                    LocalLabCoreContentModule.BANK_OBJECT+
                    " runtime="+
                    BankState.BANK_OBJECT_ID
                );

            assertBinding(
                registry.objectOptionBinding(
                    LocalLabCoreContentModule.BANK_OBJECT,
                    1
                ),
                "locallab-core",
                100
            );

            AtomicReference<ContentRegistration>
                override=new AtomicReference<>();

            registry.installCustom(
                new ContentModule(){
                    @Override public String id(){
                        return "bank-object-blocker";
                    }

                    @Override public void register(
                        ContentRegistrar registrar
                    ){
                        override.set(
                            registrar.objectOption(
                                LocalLabCoreContentModule.BANK_OBJECT,
                                1,
                                200,
                                context->
                                    ContentInteractionResult.handled(
                                        "blocked"
                                    )
                            )
                        );
                    }
                }
            );

            assertBinding(
                registry.objectOptionBinding(
                    LocalLabCoreContentModule.BANK_OBJECT,
                    1
                ),
                "bank-object-blocker",
                200
            );

            world.registerPlayer(
                player,
                "bank-object-content-owner"
            );
            world.start();

            BankState bank=
                player.bank();
            MovementState movement=
                player.movement();

            LocalBankObjectInteractionHandler handler=
                new LocalBankObjectInteractionHandler(
                    bank,
                    movement,
                    registry
                );

            ObjectInteraction bankObject=
                new ObjectInteraction(
                    132,
                    LocalLabCoreContentModule.BANK_OBJECT,
                    movement.x()+1,
                    movement.y()
                );

            ByteArrayOutputStream blockedWire=
                new ByteArrayOutputStream();
            ServerPacketWriter blockedPackets=
                writer(
                    blockedWire
                );

            String blocked=
                onWorld(
                    world,
                    player,
                    ()->handler.handle(
                        bankObject,
                        blockedPackets
                    )
                );

            if(blocked==null||
               !blocked.contains(
                   "action=CONTENT_HANDLED_FAIL_CLOSED")||
               !blocked.contains(
                   "outcome=blocked"))
                throw new AssertionError(
                    "higher-priority content override did not block bank route="+
                    blocked
                );

            if(bank.isOpen()||
               blockedWire.size()!=0)
                throw new AssertionError(
                    "blocked content mutated/opened bank wire="+
                    blockedWire.size()
                );

            ContentRegistration handle=
                override.get();

            if(handle==null||
               !handle.unregister())
                throw new AssertionError(
                    "override unregister failed"
                );

            assertBinding(
                registry.objectOptionBinding(
                    LocalLabCoreContentModule.BANK_OBJECT,
                    1
                ),
                "locallab-core",
                100
            );

            ByteArrayOutputStream wrongOpcodeWire=
                new ByteArrayOutputStream();

            String wrongOpcode=
                onWorld(
                    world,
                    player,
                    ()->handler.handle(
                        new ObjectInteraction(
                            70,
                            LocalLabCoreContentModule.BANK_OBJECT,
                            movement.x()+1,
                            movement.y()
                        ),
                        writer(
                            wrongOpcodeWire
                        )
                    )
                );

            if(wrongOpcode==null||
               !wrongOpcode.contains(
                   "action=DECODED_NOT_IMPLEMENTED")||
               bank.isOpen()||
               wrongOpcodeWire.size()!=0)
                throw new AssertionError(
                    "non-exact object opcode escaped fail-closed route="+
                    wrongOpcode+
                    " wire="+
                    wrongOpcodeWire.size()
                );

            ByteArrayOutputStream actualWire=
                new ByteArrayOutputStream();
            ServerPacketWriter actualPackets=
                writer(
                    actualWire
                );

            String opened=
                onWorld(
                    world,
                    player,
                    ()->handler.handle(
                        bankObject,
                        actualPackets
                    )
                );

            if(opened==null||
               !opened.contains(
                   "V5_BANK_OPEN")||
               !opened.contains(
                   "action=OPENED_ADJACENT_IMMEDIATE")||
               !bank.isOpen())
                throw new AssertionError(
                    "restored content bank route="+
                    opened
                );

            BankState expectedBank=
                new BankState();
            ByteArrayOutputStream expectedWire=
                new ByteArrayOutputStream();
            ServerPacketWriter expectedPackets=
                writer(
                    expectedWire
                );

            expectedBank.open(
                expectedPackets
            );

            if(!Arrays.equals(
                    actualWire.toByteArray(),
                    expectedWire.toByteArray()))
                throw new AssertionError(
                    "bank-open wire changed actual="+
                    actualWire.size()+
                    " expected="+
                    expectedWire.size()
                );

            ByteArrayOutputStream nonBankWire=
                new ByteArrayOutputStream();

            String nonBank=
                onWorld(
                    world,
                    player,
                    ()->handler.handle(
                        new ObjectInteraction(
                            132,
                            12345,
                            movement.x(),
                            movement.y()
                        ),
                        writer(
                            nonBankWire
                        )
                    )
                );

            if(nonBank==null||
               !nonBank.contains(
                   "action=DECODED_NOT_IMPLEMENTED")||
               nonBankWire.size()!=0)
                throw new AssertionError(
                    "unregistered object did not fail closed="+
                    nonBank
                );

            System.out.println(
                "BANK_OBJECT_CONTENT_OWNERSHIP_PASS "+
                "binding=true "+
                "priorityOverride=true "+
                "restore=true "+
                "exactOpcode132=true "+
                "nonExactOpcodeFailClosed=true "+
                "wireParity=true "+
                "pathingRuntimeOwned=true "+
                "bankPresentationRuntimeOwned=true"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(
                    player
                );
            world.close();
        }
    }

    private static void assertBinding(
        ContentRegistry.BindingInfo binding,
        String module,
        int priority
    ){
        if(binding==null||
           !module.equals(
               binding.moduleId)||
           binding.priority!=priority||
           binding.provenance!=
               ContentProvenance.CUSTOM_LOCALLAB)
            throw new AssertionError(
                "bank object binding="+
                binding+
                " expectedModule="+
                module+
                " expectedPriority="+
                priority
            );
    }

    private static String onWorld(
        World world,
        WorldPlayer player,
        ThrowingString action
    )throws Exception{
        AtomicReference<String>
            result=new AtomicReference<>();
        AtomicReference<Throwable>
            failure=new AtomicReference<>();

        world.submitAndWait(
            player,
            ()->{
                try{
                    result.set(
                        action.run()
                    );
                }catch(Throwable error){
                    failure.set(error);
                }
            },
            5_000L
        );

        if(failure.get()!=null)
            throw new AssertionError(
                "world action failed",
                failure.get()
            );

        return result.get();
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream wire
    ){
        return new ServerPacketWriter(
            wire,
            new IsaacCipher(
                new int[]{171,172,173,174}
            )
        );
    }

    @FunctionalInterface
    private interface ThrowingString {
        String run()throws Exception;
    }

    private BankObjectContentOwnershipTest(){}
}
