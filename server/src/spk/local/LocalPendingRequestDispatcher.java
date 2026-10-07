package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Drains decoded client intent in the exact established LocalSession order.
 *
 * The caller is responsible for entering the authoritative World execution
 * context before invoking this dispatcher. This class owns request consumption
 * and domain routing, not socket reads or WorldCommandInbox handoff.
 */
final class LocalPendingRequestDispatcher {
    interface SessionBridge {
        String username();
        String loginAlias();
        boolean persistentAccount();
        long sessionWorldTick();
        SceneUpdatePublisher scenePublisher();
        Player81WorldSync.Context player81Sync();
        void refreshPlayerAppearance(
            ServerPacketWriter writer
        )throws IOException;
        void saveAccount(String tag,String reason);
        void applyPetDialogResult(
            LocalPetInventoryDialogHandler.Result result,
            String tag
        );
        void clearOpponentOverlay(
            ServerPacketWriter writer,
            String tag,
            String reason
        )throws IOException;
        void handleDevPanelAmount(
            int value,
            ServerPacketWriter writer,
            String tag
        )throws IOException;

        default LocalDailyChallengeCommandHandler.Result
            handleDailyChallengeRequest(
                DailyChallengeClientRequest request
            )throws IOException{
            return null;
        }

        default void handleRegionLoadAck(
            String tag
        ){}

        default LocalCanonicalNpcAttackHandler.Result
            handleCanonicalNpcAttack(
                NpcAction action,
                NpcEntity clicked,
                ServerPacketWriter writer
            )throws IOException{
            return null;
        }
    }

    private final WorldPlayer worldPlayer;
    private final BankState bank;
    private final EquipmentState equipment;
    private final CombatStyleState combatStyles;
    private final MovementState movement;
    private final NpcRegistry npcs;
    private final CombatEngine combat;
    private final DevControlCenter devPanel;
    private final LocalSessionUiActionHandler uiActions;
    private final LocalCommandDispatcher commandDispatcher;
    private final LocalBankObjectInteractionHandler bankObjectHandler;
    private final LocalGenericInteractionHandler genericInteractionHandler;
    private final LocalEquipmentItemActionHandler equipmentItemActions;
    private final G1RocktailConsumableHandler rocktailConsumables;
    private final LocalPetInventoryDialogHandler petDialogs;
    private final LocalMakeoverMageHandler makeoverMage;
    private final LocalSuppliesMerchantHandler suppliesMerchant;
    private final LocalCompCapeCustomizeHandler compCapeCustomize;
    private final LocalItemOnItemHandler itemOnItemHandler;
    private final LocalItemOnNpcHandler itemOnNpcHandler;
    private final LocalSpellTargetHandler spellTargetHandler;
    private final LocalPetDropPickupHandler petDropPickup;
    private final LocalGroundItemInteractionHandler groundItemHandler;
    private final LocalPlayerInteractionHandler playerInteractions;
    private final LocalRoutedNpcInteractionHandler routedNpcHandler;
    private final LocalBankRequestHandler bankRequests;
    private final LocalMovementRequestHandler movementRequests;
    private final LocalPetRealtimeScheduler petRealtime;
    private final SessionBridge bridge;
    private volatile LocalLootingBagBankHandler lootingBagBank;

    LocalPendingRequestDispatcher(
        WorldPlayer worldPlayer,
        BankState bank,
        EquipmentState equipment,
        CombatStyleState combatStyles,
        MovementState movement,
        NpcRegistry npcs,
        CombatEngine combat,
        DevControlCenter devPanel,
        LocalSessionUiActionHandler uiActions,
        LocalCommandDispatcher commandDispatcher,
        LocalBankObjectInteractionHandler bankObjectHandler,
        LocalGenericInteractionHandler genericInteractionHandler,
        LocalEquipmentItemActionHandler equipmentItemActions,
        LocalPetInventoryDialogHandler petDialogs,
        LocalCompCapeCustomizeHandler compCapeCustomize,
        LocalItemOnItemHandler itemOnItemHandler,
        LocalItemOnNpcHandler itemOnNpcHandler,
        LocalSpellTargetHandler spellTargetHandler,
        LocalPetDropPickupHandler petDropPickup,
        LocalGroundItemInteractionHandler groundItemHandler,
        LocalPlayerInteractionHandler playerInteractions,
        LocalRoutedNpcInteractionHandler routedNpcHandler,
        LocalBankRequestHandler bankRequests,
        LocalMovementRequestHandler movementRequests,
        LocalPetRealtimeScheduler petRealtime,
        SessionBridge bridge
    ){
        this.worldPlayer=Objects.requireNonNull(worldPlayer,"worldPlayer");
        this.bank=Objects.requireNonNull(bank,"bank");
        this.equipment=Objects.requireNonNull(equipment,"equipment");
        this.combatStyles=Objects.requireNonNull(combatStyles,"combatStyles");
        this.movement=Objects.requireNonNull(movement,"movement");
        this.npcs=Objects.requireNonNull(npcs,"npcs");
        this.combat=Objects.requireNonNull(combat,"combat");
        this.devPanel=Objects.requireNonNull(devPanel,"devPanel");
        this.uiActions=Objects.requireNonNull(uiActions,"uiActions");
        this.commandDispatcher=Objects.requireNonNull(
            commandDispatcher,"commandDispatcher");
        this.bankObjectHandler=Objects.requireNonNull(
            bankObjectHandler,"bankObjectHandler");
        this.genericInteractionHandler=Objects.requireNonNull(
            genericInteractionHandler,"genericInteractionHandler");
        this.equipmentItemActions=Objects.requireNonNull(
            equipmentItemActions,"equipmentItemActions");
        this.rocktailConsumables=
            new G1RocktailConsumableHandler(
                worldPlayer,
                bank
            );
        this.petDialogs=Objects.requireNonNull(petDialogs,"petDialogs");

        LocalMakeoverMageHandler sharedMakeover=
            routedNpcHandler==null
                ?null
                :routedNpcHandler.makeoverMage();

        this.makeoverMage=
            sharedMakeover!=null
                ?sharedMakeover
                :new LocalMakeoverMageHandler(
                    worldPlayer,
                    equipment,
                    movement,
                    npcs
                );
        this.suppliesMerchant=
            routedNpcHandler==null
                ?null
                :routedNpcHandler.suppliesMerchant();
        this.makeoverMage.installDesignerRootOwner(
            new LocalMakeoverMageHandler.DesignerRootOwner(){
                @Override public void publish(
                    LocalMakeoverMageHandler.DesignerRootAction action
                )throws IOException{
                    uiActions.replaceMonsterSpawnerWithMakeoverRoot(
                        ()->{
                            action.open();
                            return "MAKEOVER_DESIGN_ROOT_OPENED";
                        }
                    );
                }

                @Override public void publish(
                    LocalMakeoverMageHandler.DesignerRootAction action,
                    LocalMakeoverMageHandler.DesignerRootCommit commit
                )throws IOException{
                    uiActions.replaceMonsterSpawnerWithMakeoverRoot(
                        ()->{
                            action.open();
                            return "MAKEOVER_DESIGN_ROOT_OPENED";
                        },
                        commit::commit
                    );
                }
            }
        );

        this.compCapeCustomize=Objects.requireNonNull(
            compCapeCustomize,"compCapeCustomize");
        this.itemOnItemHandler=Objects.requireNonNull(
            itemOnItemHandler,"itemOnItemHandler");
        this.itemOnNpcHandler=Objects.requireNonNull(
            itemOnNpcHandler,"itemOnNpcHandler");
        this.spellTargetHandler=Objects.requireNonNull(
            spellTargetHandler,"spellTargetHandler");
        this.petDropPickup=Objects.requireNonNull(
            petDropPickup,"petDropPickup");
        this.groundItemHandler=Objects.requireNonNull(
            groundItemHandler,"groundItemHandler");
        this.playerInteractions=Objects.requireNonNull(
            playerInteractions,"playerInteractions");
        this.routedNpcHandler=Objects.requireNonNull(
            routedNpcHandler,"routedNpcHandler");
        this.bankRequests=Objects.requireNonNull(
            bankRequests,"bankRequests");
        this.movementRequests=Objects.requireNonNull(
            movementRequests,"movementRequests");
        this.petRealtime=Objects.requireNonNull(
            petRealtime,"petRealtime");
        this.bridge=Objects.requireNonNull(bridge,"bridge");
    }

    synchronized void installLootingBagBankHandler(
        LocalLootingBagBankHandler handler
    ){
        LocalLootingBagBankHandler checked=
            Objects.requireNonNull(
                handler,
                "handler"
            );

        if(lootingBagBank==null){
            lootingBagBank=checked;
            return;
        }

        if(lootingBagBank!=checked)
            throw new IllegalStateException(
                "Looting Bag bank handler already installed"
            );
    }

    void drain(
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        Objects.requireNonNull(clientPackets,"clientPackets");
        Objects.requireNonNull(serverPackets,"serverPackets");

        acceptTypedRequests(
            clientPackets,
            serverPackets,
            tag
        );
        long now=System.currentTimeMillis();
        petRealtime.ensureFollowScheduled(now);
        petRealtime.ensureTestSequenceScheduled(now);
    }

    private void acceptTypedRequests(
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        for(
            ClientRequest request;
            (request=clientPackets
                .takeTypedRequest())!=null;
        ){
            if(request instanceof
                    RegionLoadAckClientRequest){
                bridge.handleRegionLoadAck(tag);
                continue;
            }

            if(request instanceof
                    InterfaceCloseClientRequest){
                boolean makeoverCancelled=
                    makeoverMage.cancel();
                if(makeoverCancelled)
                    System.out.println(
                        tag+
                        "MAKEOVER_MAGE_DIALOG_CANCEL reason=CLIENT_INTERFACE_CLOSE"
                    );

                if(suppliesMerchant!=null)
                    suppliesMerchant
                        .cancelForInterfaceClose(
                            tag
                        );

                uiActions.handleInterfaceClose(
                    clientPackets.isAligned(),
                    serverPackets,
                    tag
                );
                continue;
            }

            if(request instanceof
                    DialogueContinueClientRequest){
                DialogueContinueClientRequest dialogue=
                    (DialogueContinueClientRequest)request;

                if(!makeoverMage.handleContinue(
                        dialogue.widgetId(),
                        serverPackets,
                        tag))
                    System.out.println(
                        tag+
                        "DIALOGUE_CONTINUE_UNHANDLED widget="+
                        dialogue.widgetId()+
                        " framingPreserved=true"
                    );
                continue;
            }

            if(request instanceof
                    CharacterDesignClientRequest){
                CharacterDesignClientRequest design=
                    (CharacterDesignClientRequest)request;

                LocalMakeoverMageHandler.Result result=
                    makeoverMage.handleDesign(
                        design.design(),
                        serverPackets,
                        tag
                    );

                if(result.handled){
                    if(result.saveReason!=null){
                        bridge.refreshPlayerAppearance(
                            serverPackets
                        );
                        bridge.saveAccount(
                            tag,
                            result.saveReason
                        );
                    }
                    if(result.logText!=null)
                        System.out.println(
                            tag+result.logText
                        );
                }else{
                    System.out.println(
                        tag+
                        "CHARACTER_DESIGN_UNHANDLED request="+
                        design.design()
                    );
                }
                continue;
            }

            if(request instanceof
                    WidgetActionClientRequest){
                WidgetActionClientRequest widget=
                    (WidgetActionClientRequest)request;

                if(makeoverMage.handleWidget(
                        widget.widgetId(),
                        serverPackets,
                        tag))
                    continue;

                if(suppliesMerchant!=null){
                    LocalSuppliesMerchantHandler.Result
                        merchantResult=
                            suppliesMerchant.handleWidget(
                                widget.widgetId(),
                                serverPackets
                            );

                    if(merchantResult.handled){
                        applySuppliesMerchantResult(
                            merchantResult,
                            serverPackets,
                            tag
                        );
                        continue;
                    }
                }

                uiActions.handleWidget(
                    widget.widgetId(),
                    serverPackets,
                    tag
                );
                continue;
            }

            if(request instanceof
                    AmountEntryClientRequest){
                AmountEntryClientRequest amount=
                    (AmountEntryClientRequest)request;

                routeAmount(
                    amount.amount(),
                    clientPackets,
                    serverPackets,
                    tag
                );
                continue;
            }

            if(request instanceof
                    ContainerDragClientRequest){
                ContainerDragClientRequest drag=
                    (ContainerDragClientRequest)request;

                routeContainerDrag(
                    drag.drag(),
                    clientPackets,
                    serverPackets,
                    tag
                );
                continue;
            }

            if(request instanceof
                    DropItemClientRequest){
                DropItemClientRequest drop=
                    (DropItemClientRequest)request;

                routeDropItem(
                    drop.action(),
                    serverPackets,
                    tag
                );
                continue;
            }

            if(request instanceof
                    ItemOnItemClientRequest){
                ItemOnItemClientRequest itemOnItem=
                    (ItemOnItemClientRequest)request;

                routeItemOnItem(
                    itemOnItem.action(),
                    serverPackets,
                    tag
                );
                continue;
            }

            if(request instanceof
                    ItemOnNpcClientRequest){
                ItemOnNpcClientRequest itemOnNpc=
                    (ItemOnNpcClientRequest)request;

                routeItemOnNpc(
                    itemOnNpc.action(),
                    serverPackets,
                    tag
                );
                continue;
            }

            if(request instanceof
                    ObjectInteractionClientRequest){
                ObjectInteractionClientRequest object=
                    (ObjectInteractionClientRequest)request;

                routeObjectInteraction(
                    object.interaction(),
                    serverPackets,
                    tag
                );
                continue;
            }

            if(request instanceof
                    PlayerActionClientRequest){
                PlayerActionClientRequest player=
                    (PlayerActionClientRequest)request;

                routePlayerAction(
                    player.action(),
                    serverPackets,
                    tag
                );
                continue;
            }

            if(request instanceof
                    NpcActionClientRequest){
                NpcActionClientRequest npc=
                    (NpcActionClientRequest)request;

                routeNpcAction(
                    npc.action(),
                    serverPackets,
                    tag
                );
                continue;
            }

            if(request instanceof
                    SpellTargetClientRequest){
                SpellTargetClientRequest spell=
                    (SpellTargetClientRequest)request;

                routeSpellTarget(
                    spell.request(),
                    serverPackets,
                    tag
                );
                continue;
            }

            if(request instanceof
                    GenericInteractionClientRequest){
                GenericInteractionClientRequest generic=
                    (GenericInteractionClientRequest)request;

                routeGenericInteraction(
                    generic.event(),
                    tag
                );
                continue;
            }

            if(request instanceof
                    MovementClientRequest){
                MovementClientRequest movement=
                    (MovementClientRequest)request;

                routeMovement(
                    movement.movement(),
                    serverPackets,
                    tag
                );
                continue;
            }

            if(request instanceof
                    ItemContainerActionClientRequest){
                ItemContainerActionClientRequest item=
                    (ItemContainerActionClientRequest)request;

                routeItemAction(
                    item.action(),
                    serverPackets,
                    tag
                );
                continue;
            }

            if(request instanceof
                    GroundItemClientRequest){
                GroundItemClientRequest ground=
                    (GroundItemClientRequest)request;

                routeGroundItemInteraction(
                    ground.interaction(),
                    serverPackets,
                    tag
                );
                continue;
            }

            if(request instanceof
                    PublicChatClientRequest){
                PublicChatClientRequest publicChat=
                    (PublicChatClientRequest)request;

                System.out.println(
                    tag+
                    publicChatFailClosedDiagnostic(
                        publicChat
                    )
                );
                continue;
            }

            if(request instanceof
                    PrivateMessageClientRequest){
                PrivateMessageClientRequest privateMessage=
                    (PrivateMessageClientRequest)request;

                System.out.println(
                    tag+
                    privateMessageFailClosedDiagnostic(
                        privateMessage
                    )
                );
                continue;
            }

            if(request instanceof
                    NameEntryClientRequest){
                NameEntryClientRequest nameEntry=
                    (NameEntryClientRequest)request;

                System.out.println(
                    tag+
                    nameEntryFailClosedDiagnostic(
                        nameEntry
                    )
                );
                continue;
            }

            if(request instanceof
                    ChatModeClientRequest){
                ChatModeClientRequest chatMode=
                    (ChatModeClientRequest)request;

                System.out.println(
                    tag+
                    chatModeFailClosedDiagnostic(
                        chatMode
                    )
                );
                continue;
            }

            if(request instanceof
                    ReportAbuseClientRequest){
                ReportAbuseClientRequest report=
                    (ReportAbuseClientRequest)request;

                System.out.println(
                    tag+
                    reportAbuseFailClosedDiagnostic(
                        report
                    )
                );
                continue;
            }

            if(request instanceof
                    SocialListClientRequest){
                SocialListClientRequest social=
                    (SocialListClientRequest)request;

                System.out.println(
                    tag+
                    socialListFailClosedDiagnostic(
                        social
                    )
                );
                continue;
            }

            if(request instanceof
                    LoadoutEditorSaveClientRequest){
                LoadoutEditorSaveClientRequest loadout=
                    (LoadoutEditorSaveClientRequest)request;

                System.out.println(
                    tag+
                    loadoutEditorSaveFailClosedDiagnostic(
                        loadout
                    )
                );
                continue;
            }

            if(request instanceof
                    DailyChallengeClientRequest){
                DailyChallengeClientRequest dailyChallenge=
                    (DailyChallengeClientRequest)request;

                LocalDailyChallengeCommandHandler.Result
                    dailyResult=
                        bridge.handleDailyChallengeRequest(
                            dailyChallenge
                        );

                if(dailyResult==null){
                    System.out.println(
                        tag+
                        dailyChallengeFailClosedDiagnostic(
                            dailyChallenge
                        )
                    );
                    continue;
                }

                publishDailyChallengeResult(
                    dailyResult,
                    serverPackets
                );

                System.out.println(
                    tag+
                    dailyResult.logText+
                    " clientFeedback=true"+
                    " route=EXACT_CURRENT_C2S103_TYPED"+
                    " nativeDefinitionPublished="+
                    (dailyResult.nativeDefinition!=null)+
                    " nativePresentationTarget55="+
                    (dailyResult.nativeDefinition!=null)
                );
                continue;
            }

            if(request instanceof
                    CommandClientRequest){
                CommandClientRequest command=
                    (CommandClientRequest)request;

                int dialogueOption=
                    ClientCommandSemanticRouter
                        .dialogueOptionIndex(
                            command.command()
                        );

                if(dialogueOption>0){
                    boolean handled=
                        makeoverMage.handleOption(
                            dialogueOption,
                            serverPackets,
                            tag
                        );

                    if(!handled&&
                       suppliesMerchant!=null){
                        LocalSuppliesMerchantHandler.Result
                            merchantResult=
                                suppliesMerchant.handleOption(
                                    dialogueOption,
                                    serverPackets
                                );

                        if(merchantResult.handled){
                            applySuppliesMerchantResult(
                                merchantResult,
                                serverPackets,
                                tag
                            );
                            handled=true;
                        }
                    }

                    if(!handled)
                        System.out.println(
                            tag+
                            "DIALOGUE_OPTION_UNHANDLED index="+
                            dialogueOption+
                            " framingPreserved=true"
                        );

                    continue;
                }

                commandDispatcher.handle(
                    command.command(),
                    clientPackets.isAligned(),
                    bridge.username(),
                    bridge.loginAlias(),
                    bridge.persistentAccount(),
                    bridge.sessionWorldTick(),
                    serverPackets,
                    tag
                );
                continue;
            }

            throw new IOException(
                "unrouted typed client request "+
                request
            );
        }
    }

    static void publishDailyChallengeResult(
        LocalDailyChallengeCommandHandler.Result result,
        ServerPacketWriter serverPackets
    )throws IOException{
        LocalDailyChallengeCommandHandler.Result checked=
            Objects.requireNonNull(
                result,
                "result"
            );
        ServerPacketWriter writer=
            Objects.requireNonNull(
                serverPackets,
                "serverPackets"
            );

        if(checked.nativeDefinition!=null)
            new LocalLabDailyChallengePresentation(
                writer
            ).publishDefinition(
                checked.nativeDefinition
            );

        new SocialChatPresentationPublisher(
            writer
        ).serverMessage(
            checked.clientMessage
        );
    }

    static String loadoutEditorSaveFailClosedDiagnostic(
        LoadoutEditorSaveClientRequest request
    ){
        Objects.requireNonNull(
            request,
            "request"
        );

        return "LOADOUT_EDITOR_SAVE_FAIL_CLOSED inventoryEntries="+
            request.inventory().size()+
            " equipmentEntries="+
            request.equipment().size()+
            " packetSeq="+
            request.inventoryPacketSequence()+
            "->"+
            request.equipmentPacketSequence()+
            " reason=LOADOUT_EDITOR_TARGET_ADAPTER_UNPROVEN"+
            " stateMutation=false"+
            " authority=EXACT_CURRENT_CLIENT";
    }

    static String dailyChallengeFailClosedDiagnostic(
        DailyChallengeClientRequest request
    ){
        Objects.requireNonNull(
            request,
            "request"
        );

        return "DAILY_CHALLENGE_REQUEST_FAIL_CLOSED action="+
            request.action()+
            " challengeKey="+
            request.challengeKey()+
            " reason=DAILY_CHALLENGE_KEY_ADAPTER_UNPROVEN"+
            " stateMutation=false"+
            " authority=EXACT_CURRENT_CLIENT";
    }

    static String publicChatFailClosedDiagnostic(
        PublicChatClientRequest request
    ){
        Objects.requireNonNull(
            request,
            "request"
        );

        return "PUBLIC_CHAT_REQUEST_FAIL_CLOSED effect="+
            request.effect()+
            " colour="+
            request.colour()+
            " messageLength="+
            request.message().length()+
            " reason=SERVER_PUBLIC_CHAT_POLICY_UNPROVEN"+
            " stateMutation=false"+
            " authority=EXACT_CURRENT_CLIENT";
    }

    static String privateMessageFailClosedDiagnostic(
        PrivateMessageClientRequest request
    ){
        Objects.requireNonNull(
            request,
            "request"
        );

        return "PRIVATE_MESSAGE_REQUEST_FAIL_CLOSED recipientNameKey="+
            Long.toUnsignedString(
                request.recipientNameKey()
            )+
            " messageLength="+
            request.message().length()+
            " reason=RECIPIENT_MAPPING_AND_PM_POLICY_UNPROVEN"+
            " stateMutation=false"+
            " authority=EXACT_CURRENT_CLIENT";
    }

    static String nameEntryFailClosedDiagnostic(
        NameEntryClientRequest request
    ){
        Objects.requireNonNull(
            request,
            "request"
        );

        return "NAME_ENTRY_REQUEST_FAIL_CLOSED nameKey="+
            Long.toUnsignedString(
                request.nameKey()
            )+
            " reason=ACTIVE_NAME_PROMPT_AND_ACCOUNT_MAPPING_UNPROVEN"+
            " stateMutation=false"+
            " authority=EXACT_CURRENT_CLIENT";
    }

    static String chatModeFailClosedDiagnostic(
        ChatModeClientRequest request
    ){
        Objects.requireNonNull(
            request,
            "request"
        );

        return "CHAT_MODE_REQUEST_FAIL_CLOSED modes="+
            request.mode0()+","+
            request.mode1()+","+
            request.mode2()+
            " reason=SERVER_CHAT_MODE_POLICY_UNPROVEN"+
            " stateMutation=false"+
            " authority=EXACT_CURRENT_CLIENT";
    }

    static String reportAbuseFailClosedDiagnostic(
        ReportAbuseClientRequest request
    ){
        Objects.requireNonNull(
            request,
            "request"
        );

        return "REPORT_ABUSE_REQUEST_FAIL_CLOSED nameKey="+
            Long.toUnsignedString(
                request.nameKey()
            )+
            " ruleIndex="+
            request.ruleIndex()+
            " muteToggle="+
            request.muteToggle()+
            " reason=ACCOUNT_MAPPING_AND_MODERATION_POLICY_UNPROVEN"+
            " stateMutation=false"+
            " authority=EXACT_CURRENT_CLIENT";
    }

    static String socialListFailClosedDiagnostic(
        SocialListClientRequest request
    ){
        Objects.requireNonNull(
            request,
            "request"
        );

        return "SOCIAL_LIST_REQUEST_FAIL_CLOSED action="+
            request.action()+
            " nameKey="+
            Long.toUnsignedString(
                request.nameKey()
            )+
            " reason=NAME_KEY_ACCOUNT_MAPPING_UNPROVEN"+
            " stateMutation=false"+
            " authority=EXACT_CURRENT_CLIENT";
    }

    private void applySuppliesMerchantResult(
        LocalSuppliesMerchantHandler.Result result,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        if(result==null||!result.handled)
            return;

        if(result.saveReason!=null)
            bridge.saveAccount(
                tag,
                result.saveReason
            );

        if(result.feedback!=null)
            new SocialChatPresentationPublisher(
                serverPackets
            ).serverMessage(
                result.feedback
            );

        if(result.logText!=null)
            System.out.println(
                tag+
                result.logText+
                " persistenceBeforeFeedback="+
                (result.saveReason!=null&&
                 result.feedback!=null)
            );
    }

    private void routeObjectInteraction(
        ObjectInteraction request,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        String result=
            bankObjectHandler.handle(
                request,
                serverPackets
            );

        if(result!=null)
            System.out.println(tag+result);
    }

    private void routeGenericInteraction(
        GenericInteractionEvent event,
        String tag
    ){
        String result=
            genericInteractionHandler.handle(event);

        if(result!=null)
            System.out.println(tag+result);
    }

    private void routeItemAction(
        ItemContainerAction action,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        String tradeItem=
            TradeService.handleItemAction(
                worldPlayer,
                action
            );

        if(tradeItem!=null){
            System.out.println(
                tag+"V5140_TRADE_ITEM "+
                action+
                " result="+tradeItem
            );
            return;
        }

        LocalEquipmentItemActionHandler.Result equipmentAction=
            equipmentItemActions.handle(
                action,
                bridge.username(),
                serverPackets
            );

        if(equipmentAction!=null){
            for(String line:
                equipmentAction.beforeSaveLogs){
                System.out.println(tag+line);
            }

            if(equipmentAction.saveReason!=null){
                bridge.saveAccount(
                    tag,
                    equipmentAction.saveReason
                );
            }

            for(String line:
                equipmentAction.afterSaveLogs){
                System.out.println(tag+line);
            }
            return;
        }

        LocalPetInventoryDialogHandler.Result petDialogItem=
            petDialogs.handleItemAction(
                action,
                serverPackets
            );

        if(petDialogItem!=null){
            bridge.applyPetDialogResult(
                petDialogItem,
                tag
            );
            return;
        }

        String compCapeItem;

        if(compCapeCustomize.willOpenRoot(
                action
            ))
            compCapeItem=
                uiActions.replaceMonsterSpawnerWithCompCapeRoot(
                    ()->
                        compCapeCustomize.handleItemAction(
                            action,
                            serverPackets
                        )
                );
        else
            compCapeItem=
                compCapeCustomize.handleItemAction(
                    action,
                    serverPackets
                );

        if(compCapeItem!=null){
            System.out.println(tag+compCapeItem);
            return;
        }

        G1RocktailConsumableHandler.Result
            rocktail=
                rocktailConsumables.handle(
                    action,
                    bridge.sessionWorldTick(),
                    serverPackets
                );

        if(rocktail!=null){
            if(rocktail.saveReason!=null)
                bridge.saveAccount(
                    tag,
                    rocktail.saveReason
                );

            System.out.println(
                tag+
                "G1_ROCKTAIL_CONSUMABLE "+
                action+
                " result="+
                rocktail+
                " authority="+
                G1RocktailConsumableHandler.AUTHORITY
            );
            return;
        }

        LocalLootingBagBankHandler bagHandler=
            lootingBagBank;

        if(bagHandler!=null&&
           bagHandler.owns(action)){
            LocalLootingBagBankHandler.Result
                bag=
                    bagHandler.handle(
                        action,
                        serverPackets
                    );

            if(bag.saveReason!=null)
                bridge.saveAccount(
                    tag,
                    bag.saveReason
                );

            System.out.println(
                tag+
                "G8_LOOTING_BAG_BANK "+
                action+
                " status="+
                bag.status+
                " item="+
                bag.itemId+
                " amount="+
                bag.amount+
                " detail=["+
                bag.detail+
                "] authority="+
                LocalLootingBagBankHandler
                    .POLICY_AUTHORITY
            );
            return;
        }

        boolean bankOwned=
            action.widgetId==
                BankState.BANK_CONTAINER||
            action.widgetId==
                BankState.BANK_INVENTORY_CONTAINER;

        if(!bankOwned){
            System.out.println(
                tag+
                "V522_ITEM_CONTAINER_UNCLAIMED "+
                action+
                " result=FAIL_CLOSED_NON_BANK_WIDGET"+
                " bankOwnership=false"+
                " shopAuthority=UNPROVEN"+
                " decoderAligned=true"
            );
            return;
        }

        String result=
            bank.apply(
                action,
                serverPackets
            );

        bridge.saveAccount(
            tag,
            "BANK_ITEM_ACTION"
        );

        System.out.println(
            tag+"V522_BANK_ITEM_ACTION "+
            action+
            " result="+result+
            " decoderAligned=true"+
            " bankOwnership=true"
        );
    }

    private void routeItemOnItem(
        ItemOnItemAction action,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        LocalItemOnItemHandler.Result result=
            itemOnItemHandler.handle(
                action,
                serverPackets
            );

        if(result.saveReason!=null)
            bridge.saveAccount(
                tag,
                result.saveReason
            );

        System.out.println(
            tag+result.logText
        );
    }

    private void routeItemOnNpc(
        ItemOnNpcAction action,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        LocalItemOnNpcHandler.Result result=
            itemOnNpcHandler.handle(
                action,
                serverPackets
            );

        if(result.saveReason!=null)
            bridge.saveAccount(
                tag,
                result.saveReason
            );

        System.out.println(
            tag+result.logText
        );
    }

    private void routeSpellTarget(
        SpellTargetRequest request,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        System.out.println(
            tag+
            spellTargetHandler.handle(
                request,
                serverPackets
            )
        );
    }

    private void routeDropItem(
        DropItemAction action,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        petDropPickup.handleDrop(
            action,
            serverPackets,
            tag
        );
    }

    private void routeGroundItemInteraction(
        GroundItemInteraction action,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        applyGroundItemResult(
            groundItemHandler.handle(
                action,
                bridge.username(),
                bridge.scenePublisher(),
                serverPackets
            ),
            tag
        );
    }

    private void applyGroundItemResult(
        LocalGroundItemInteractionHandler.Result result,
        String tag
    ){
        if(result==null)return;

        if(result.saveReason!=null)
            bridge.saveAccount(
                tag,
                result.saveReason
            );

        System.out.println(
            tag+result.logText
        );
    }

    private void routePlayerAction(
        PlayerAction action,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        Player81WorldSync.Context playerSync=
            bridge.player81Sync();

        if(playerSync==null){
            System.out.println(
                tag+
                "V5131_PLAYER_ACTION "+
                action+
                " result=REJECTED_SYNC_NOT_READY"
            );
            return;
        }

        WorldPlayer target=
            playerSync.resolveVisible(
                action.playerIndex
            );

        if(target==null||
           !target.registered()){
            System.out.println(
                tag+
                "V5131_PLAYER_ACTION "+
                action+
                " result=REJECTED_STALE_OR_NOT_VISIBLE"
            );
            return;
        }

        if(combat.active()){
            boolean cancelled=
                combat.cancelForManualMovement();

            if(cancelled){
                bridge.clearOpponentOverlay(
                    serverPackets,
                    tag,
                    "PLAYER_INTERACTION_REPLACES_NPC_COMBAT"
                );
            }
        }

        String result=
            playerInteractions.handleResolved(
                action,
                target,
                playerSync
            );

        if(result!=null)
            System.out.println(tag+result);
    }

    private void routeNpcAction(
        NpcAction action,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        NpcEntity clicked=
            npcs.scene(action.sceneIndex);

        NpcEntity pet=npcs.pet();

        makeoverMage.cancelForNewNpcAction(
            serverPackets,
            tag
        );

        if(suppliesMerchant!=null)
            suppliesMerchant
                .cancelForNewNpcAction(
                    serverPackets,
                    tag
                );

        if(petDropPickup.handlePickupNpcAction(
            action,
            serverPackets,
            tag
        )){
            return;
        }

        petDropPickup.cancelDeferredForNewNpcAction(
            action,
            tag
        );

        if(clicked!=null&&
           clicked==pet&&
           clicked.definitionId==1334&&
           action.opcode==17){
            String reset=
                LocalDevVisualOverrideStore.set(
                    "intrinsicfx",
                    null
                );

            System.out.println(
                tag+
                "V5128_YOSHIGANGER_SWITCH_EFFECT scene="+
                clicked.sceneIndex+
                " result=PENDING_FUNCTIONAL_MODE_RECONSTRUCTION visualAccessoryInvented=false bodyGreenPreserved=true overrideReset="+
                reset
            );
            return;
        }

        if(isCombatAttackAction(
            action,
            clicked
        )){
            int combatRoot=
                CombatInterfaceRepository.forWeapon(
                    equipment.weapon()
                );

            long now=System.currentTimeMillis();

            String result=
                combat.request(
                    clicked,
                    movement,
                    equipment.weapon(),
                    now,
                    combatStyles.current(combatRoot),
                    serverPackets
                );

            String approach="NONE";

            if(result.contains(
                "TARGET_DEFERRED_RANGE"
            )){
                approach=
                    combat.beginServerOwnedApproach(
                        clicked,
                        movement,
                        equipment.weapon(),
                        now
                    );
            }

            System.out.println(
                tag+
                "V5123_COMBAT_REQUEST "+
                action+
                " semantic=NPC_ATTACK clicked="+
                clicked+
                " result="+result+
                " approach="+approach
            );
            return;
        }

        LocalCanonicalNpcAttackHandler.Result
            canonicalAttack=
                bridge.handleCanonicalNpcAttack(
                    action,
                    clicked,
                    serverPackets
                );

        if(canonicalAttack!=null){
            System.out.println(
                tag+
                "CANONICAL_NPC_ATTACK_CLICK "+
                action+
                " clicked="+
                clicked+
                " result="+
                canonicalAttack
            );
            return;
        }

        String routed=
            routedNpcHandler.handle(
                action,
                clicked,
                serverPackets,
                tag
            );

        if(routed!=null)
            System.out.println(tag+routed);
    }

    private void routeAmount(
        int amount,
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        if(devPanel.hasPending()){
            bridge.handleDevPanelAmount(
                amount,
                serverPackets,
                tag
            );
            return;
        }

        LocalBankRequestHandler.Result result=
            bankRequests.handleAmount(
                amount,
                serverPackets
            );

        if(result.saveReason!=null)
            bridge.saveAccount(
                tag,
                result.saveReason
            );

        System.out.println(
            tag+
            result.logText+
            " decoderAligned="+
            clientPackets.isAligned()
        );
    }

    private void routeContainerDrag(
        ContainerDrag drag,
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        LocalBankRequestHandler.Result result=
            bankRequests.handleDrag(
                drag,
                serverPackets
            );

        if(result.saveReason!=null)
            bridge.saveAccount(
                tag,
                result.saveReason
            );

        System.out.println(
            tag+
            result.logText+
            " decoderAligned="+
            clientPackets.isAligned()
        );
    }

    private void routeMovement(
        MovementRequest request,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        movementRequests.handle(
            request,
            serverPackets,
            tag
        );
    }

    static boolean isCombatAttackAction(
        NpcAction action,
        NpcEntity clicked
    ){
        return action!=null&&
            action.opcode==72&&
            clicked!=null&&
            CombatTargetRepository.isCombatDummy(
                clicked.definitionId
            );
    }
}
