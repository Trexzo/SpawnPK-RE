package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalPetInventoryDialogHandlerTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer player=new WorldPlayer();
        BankState bank=player.bank();
        PetState petState=player.petState();
        MiniPetService miniPets=player.miniPets();
        MovementState movement=player.movement();
        NpcRegistry npcs=new NpcRegistry(new DevAuthorityWorkbench());
        PetAccessoryState accessory=new PetAccessoryState();

        LocalPetInventoryDialogHandler handler=
            new LocalPetInventoryDialogHandler(
                bank,
                miniPets,
                petState,
                npcs,
                movement,
                accessory
            );

        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter writer=new ServerPacketWriter(
            wire,new IsaacCipher(new int[]{1,2,3,4}));

        bank.spawnItem(22088,1,writer);
        int miniSlot=findSlot(bank,22088);
        if(miniSlot<0)throw new AssertionError("mini item not spawned");

        ItemContainerAction miniRead=new ItemContainerAction(
            122,
            BankState.NORMAL_INVENTORY_CONTAINER,
            miniSlot,
            22088,
            0,
            "INVENTORY_OPTION_1"
        );

        LocalPetInventoryDialogHandler.Result miniOpen=
            handler.handleItemAction(miniRead,writer);

        if(miniOpen==null||
           miniOpen.keyAction!=
               LocalPetInventoryDialogHandler.KeyAction.PUBLISH_2482_2485||
           !miniOpen.logText.contains("V5127_MINIPET_CONFIGURE"))
            throw new AssertionError("mini open="+
                (miniOpen==null?null:miniOpen.logText));

        LocalPetInventoryDialogHandler.Result miniActivate=
            handler.handleWidget(2482,writer);

        if(miniActivate==null||
           !"MINIPET_CONFIGURE".equals(miniActivate.saveReason)||
           miniActivate.keyAction!=
               LocalPetInventoryDialogHandler.KeyAction.CLEAR_AFTER_LOG||
           !petState.miniConfigured()||
           petState.miniItemId()!=22088)
            throw new AssertionError("mini activate failure");

        bank.spawnItem(24016,1,writer);
        int colorSlot=findSlot(bank,24016);
        if(colorSlot<0)throw new AssertionError("color item not spawned");

        ItemContainerAction colorAction=new ItemContainerAction(
            75,
            BankState.NORMAL_INVENTORY_CONTAINER,
            colorSlot,
            24016,
            0,
            "INVENTORY_OPTION_3"
        );

        LocalPetInventoryDialogHandler.Result colorOpen=
            handler.handleItemAction(colorAction,writer);

        if(colorOpen==null||
           !colorOpen.logText.contains(
               "family=SCOOBY_BEHEMOTH")||
           colorOpen.keyAction!=
               LocalPetInventoryDialogHandler.KeyAction.PUBLISH_2482_2485)
            throw new AssertionError("color open="+
                (colorOpen==null?null:colorOpen.logText));

        LocalPetInventoryDialogHandler.Result colorSwitch=
            handler.handleWidget(2483,writer);

        if(colorSwitch==null||
           !"PET_SWITCH_COLOR".equals(colorSwitch.saveReason)||
           bank.inventoryAt(colorSlot)==null||
           bank.inventoryAt(colorSlot).itemId!=24017)
            throw new AssertionError("color switch="+
                (colorSwitch==null?null:colorSwitch.logText));

        LocalPetInventoryDialogHandler.Result compat=
            handler.openScoobyColorCompat(24017,writer);

        if(compat==null||
           !compat.logText.contains("result=DIALOG_OPEN")||
           !handler.hasAnyOpen())
            throw new AssertionError("compat color open");

        LocalPetInventoryDialogHandler.CloseState close=
            handler.clearAll();

        if(!close.petColorWasOpen||
           close.miniConfigWasOpen||
           close.petAccessoryWasOpen||
           handler.hasAnyOpen())
            throw new AssertionError("close state");

        testPetAccessoryPublicationAtomicity();

        System.out.println(
            "LOCAL_PET_INVENTORY_DIALOG_HANDLER_PASS "+
            "mini=true color=true compat=true pendingStateOwned=true "+
            "accessoryNoPetFailureAtomic=true "+
            "accessoryReplacementFailureAtomic=true "+
            "accessoryDetachFailureAtomic=true "+
            "accessoryActivePetFailureAtomic=true "+
            "accessoryRetryExact=true");
    }

    private static void testPetAccessoryPublicationAtomicity()
        throws Exception
    {
        WorldPlayer player=new WorldPlayer();
        BankState bank=player.bank();
        PetState petState=player.petState();
        MiniPetService miniPets=player.miniPets();
        MovementState movement=player.movement();
        DevAuthorityWorkbench dev=
            new DevAuthorityWorkbench();
        NpcRegistry npcs=
            new NpcRegistry(dev);
        PetAccessoryState accessory=
            new PetAccessoryState();

        LocalPetInventoryDialogHandler handler=
            new LocalPetInventoryDialogHandler(
                bank,
                miniPets,
                petState,
                npcs,
                movement,
                accessory
            );

        ServerPacketWriter good=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(
                    new int[]{31,32,33,34}
                )
            );

        // No active pet: even though only the close packet is visible,
        // failed batch admission must preserve semantic accessory + selector
        // authority and the pending dialog for retry.
        bank.spawnItem(
            20543,
            1,
            good
        );
        int redSlot=
            findSlot(bank,20543);
        openAccessory(
            handler,
            redSlot,
            20543,
            good
        );

        boolean noPetFailed=false;
        try{
            handler.handleWidget(
                2482,
                failingWriter(
                    new int[]{35,36,37,38}
                )
            );
        }catch(java.io.IOException expected){
            noPetFailed=true;
        }

        if(!noPetFailed||
           accessory.activeItem()!=0||
           npcs.petParticleSelector()!=null||
           !handler.hasAnyOpen())
            throw new AssertionError(
                "failed no-pet accessory activation mutated authority"
            );

        LocalPetInventoryDialogHandler.Result
            redRetry=
                handler.handleWidget(
                    2482,
                    good
                );

        if(redRetry==null||
           !"PET_ACCESSORY_ACTIVATE".equals(
                redRetry.saveReason
           )||
           accessory.activeItem()!=20543||
           !Integer.valueOf(2).equals(
                npcs.petParticleSelector()
           )||
           handler.hasAnyOpen())
            throw new AssertionError(
                "no-pet accessory retry failed"
            );

        // Replacing one active accessory must preserve the previous exact
        // accessory + selector when publication fails.
        bank.spawnItem(
            20544,
            1,
            good
        );
        int greenSlot=
            findSlot(bank,20544);
        openAccessory(
            handler,
            greenSlot,
            20544,
            good
        );

        boolean replacementFailed=false;
        try{
            handler.handleWidget(
                2482,
                failingWriter(
                    new int[]{39,40,41,42}
                )
            );
        }catch(java.io.IOException expected){
            replacementFailed=true;
        }

        if(!replacementFailed||
           accessory.activeItem()!=20543||
           !Integer.valueOf(2).equals(
                npcs.petParticleSelector()
           )||
           !handler.hasAnyOpen())
            throw new AssertionError(
                "failed accessory replacement changed preimage"
            );

        handler.handleWidget(
            2482,
            good
        );

        if(accessory.activeItem()!=20544||
           !Integer.valueOf(3).equals(
                npcs.petParticleSelector()
           )||
           handler.hasAnyOpen())
            throw new AssertionError(
                "accessory replacement retry failed"
            );

        // Detach failure likewise preserves the current selection and selector.
        openAccessory(
            handler,
            greenSlot,
            20544,
            good
        );

        boolean detachFailed=false;
        try{
            handler.handleWidget(
                2483,
                failingWriter(
                    new int[]{43,44,45,46}
                )
            );
        }catch(java.io.IOException expected){
            detachFailed=true;
        }

        if(!detachFailed||
           accessory.activeItem()!=20544||
           !Integer.valueOf(3).equals(
                npcs.petParticleSelector()
           )||
           !handler.hasAnyOpen())
            throw new AssertionError(
                "failed accessory detach changed preimage"
            );

        handler.handleWidget(
            2483,
            good
        );

        if(accessory.activeItem()!=0||
           npcs.petParticleSelector()!=null||
           handler.hasAnyOpen())
            throw new AssertionError(
                "accessory detach retry failed"
            );

        // Active-pet path: staging the remove/re-add selector presentation
        // must not mutate pet identity or selector before the full batch,
        // including S2C219 close, is admitted.
        PetDefinitionRepository.Def def=
            PetDefinitionRepository.get(24019);
        if(def==null)
            throw new AssertionError(
                "expected certified pet definition 24019"
            );

        String spawned=
            npcs.spawnPet(
                def,
                movement,
                good
            );
        if(!spawned.startsWith("PET_SPAWN_OK"))
            throw new AssertionError(
                "active-pet fixture spawn="+spawned
            );
        petState.activate(def);

        NpcEntity petBefore=
            npcs.pet();

        openAccessory(
            handler,
            redSlot,
            20543,
            good
        );

        boolean activePetFailed=false;
        try{
            handler.handleWidget(
                2482,
                failingWriter(
                    new int[]{47,48,49,50}
                )
            );
        }catch(java.io.IOException expected){
            activePetFailed=true;
        }

        if(!activePetFailed||
           accessory.activeItem()!=0||
           npcs.petParticleSelector()!=null||
           npcs.pet()!=petBefore||
           !handler.hasAnyOpen())
            throw new AssertionError(
                "failed active-pet accessory activation mutated runtime preimage"
            );

        LocalPetInventoryDialogHandler.Result
            activeRetry=
                handler.handleWidget(
                    2482,
                    good
                );

        if(activeRetry==null||
           !"PET_ACCESSORY_ACTIVATE".equals(
                activeRetry.saveReason
           )||
           accessory.activeItem()!=20543||
           !Integer.valueOf(2).equals(
                npcs.petParticleSelector()
           )||
           npcs.pet()!=petBefore||
           handler.hasAnyOpen())
            throw new AssertionError(
                "active-pet accessory retry failed"
            );

        openAccessory(
            handler,
            redSlot,
            20543,
            good
        );

        boolean activeDetachFailed=false;
        try{
            handler.handleWidget(
                2483,
                failingWriter(
                    new int[]{51,52,53,54}
                )
            );
        }catch(java.io.IOException expected){
            activeDetachFailed=true;
        }

        if(!activeDetachFailed||
           accessory.activeItem()!=20543||
           !Integer.valueOf(2).equals(
                npcs.petParticleSelector()
           )||
           npcs.pet()!=petBefore||
           !handler.hasAnyOpen())
            throw new AssertionError(
                "failed active-pet detach mutated runtime preimage"
            );

        handler.handleWidget(
            2483,
            good
        );

        if(accessory.activeItem()!=0||
           npcs.petParticleSelector()!=null||
           npcs.pet()!=petBefore||
           handler.hasAnyOpen())
            throw new AssertionError(
                "active-pet detach retry failed"
            );
    }

    private static void openAccessory(
        LocalPetInventoryDialogHandler handler,
        int slot,
        int itemId,
        ServerPacketWriter writer
    )throws Exception{
        LocalPetInventoryDialogHandler.Result opened=
            handler.handleItemAction(
                new ItemContainerAction(
                    122,
                    BankState.NORMAL_INVENTORY_CONTAINER,
                    slot,
                    itemId,
                    0,
                    "INVENTORY_OPTION_1"
                ),
                writer
            );

        if(opened==null||
           opened.keyAction!=
                LocalPetInventoryDialogHandler.KeyAction.PUBLISH_2482_2485||
           !handler.hasAnyOpen())
            throw new AssertionError(
                "pet accessory dialog did not open item="+
                itemId
            );
    }

    private static ServerPacketWriter failingWriter(
        int[] seed
    )throws Exception{
        OutboundPacketQueue queue=
            new OutboundPacketQueue(1024);
        queue.offer(
            new byte[1024]
        );

        return new ServerPacketWriter(
            queue,
            new IsaacCipher(seed)
        );
    }

    private static int findSlot(BankState bank,int itemId){
        for(int i=0;i<bank.inventoryCapacity();i++){
            BankState.Stack stack=bank.inventoryAt(i);
            if(stack!=null&&stack.itemId==itemId&&stack.qty>0)return i;
        }
        return -1;
    }
}
