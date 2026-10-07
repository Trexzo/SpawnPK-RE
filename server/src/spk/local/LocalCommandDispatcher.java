package spk.local;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import spk.content.api.ContentResult;

/**
 * Owns raw command normalization and the ordered command-handler routing chain.
 *
 * Session-owned effects that must remain at the network/session boundary are
 * exposed through a narrow bridge rather than implemented here.
 */
final class LocalCommandDispatcher {
    @FunctionalInterface
    interface RootReplacingCommandAction {
        boolean handle() throws IOException;
    }

    static final class RootReplacingCommandDispatch {
        final boolean admitted;
        final boolean handled;

        private RootReplacingCommandDispatch(
            boolean admitted,
            boolean handled
        ){
            this.admitted=admitted;
            this.handled=handled;
        }

        static RootReplacingCommandDispatch admitted(
            boolean handled
        ){
            return new RootReplacingCommandDispatch(
                true,
                handled
            );
        }

        static RootReplacingCommandDispatch rejected(){
            return new RootReplacingCommandDispatch(
                false,
                false
            );
        }
    }

    interface SessionBridge {
        SceneUpdatePublisher scenePublisher();
        void replaceScenePublisher(SceneUpdatePublisher scenePublisher);
        void saveAccount(String tag,String reason);
        void openDevPanel(ServerPacketWriter serverPackets)throws IOException;
        default boolean openMonsterSpawner(
            ServerPacketWriter serverPackets
        )throws IOException{
            return false;
        }
        default boolean openBloodSlayer(
            ServerPacketWriter serverPackets
        )throws IOException{
            return false;
        }
        default boolean openLootingBag(
            ServerPacketWriter serverPackets
        )throws IOException{
            return false;
        }
        default boolean openTournament(
            ServerPacketWriter serverPackets
        )throws IOException{
            return false;
        }
        default boolean openPkRatings(
            ServerPacketWriter serverPackets
        )throws IOException{
            return false;
        }
        default boolean openDuel(
            String targetRef,
            ServerPacketWriter serverPackets
        )throws IOException{
            return false;
        }
        default RootReplacingCommandDispatch
            handleRootReplacingCommand(
                RootReplacingCommandAction action
            )throws IOException{
            return RootReplacingCommandDispatch.admitted(
                Objects.requireNonNull(
                    action,
                    "action"
                ).handle()
            );
        }
        default LocalShopCommandHandler.Result
            handleShopCommand(
                String[] tokens
            )throws IOException{
            return null;
        }
        default LocalSlayerCommandHandler.Result
            handleSlayerCommand(
                String[] tokens,
                long worldTick
            )throws IOException{
            return null;
        }
        default LocalDuelCommandHandler.Result
            handleDuelCommand(
                String[] tokens
            )throws IOException{
            return null;
        }
        void applyPetDialog(LocalPetInventoryDialogHandler.Result result,String tag);
    }

    private final LocalBankRequestHandler bankRequests;
    private final LocalDiagnosticCommandHandler diagnosticCommands;
    private final LocalRegionDevCommandHandler regionDevCommands;
    private final LocalPrayerMagicCommandHandler prayerMagicCommands;
    private final LocalMiniPetCommandHandler miniPetCommands;
    private final LocalCosmeticCommandHandler cosmeticCommands;
    private final LocalContentCommandActionExecutor contentCommandActions;
    private final LocalDevWorldCommandHandler devWorldCommands;
    private final DevAuthorityWorkbench dev;
    private final LocalDevSessionCommandHandler devSessionCommands;
    private final LocalDevPetCommandHandler devPetCommands;
    private final LocalDevPlayerCommandHandler devPlayerCommands;
    private final LocalDevNpcCommandHandler devNpcCommands;
    private final LocalDevToolCommandHandler devToolCommands;
    private final LocalVoidglassCommandHandler voidglassCommands;
    private final LocalPetRuntimeCommandHandler petRuntimeCommands;
    private final LocalCompColorsCommandHandler compColorsCommands;
    private final LocalCombatCommandHandler combatCommands;
    private final LocalPetCompatibilityCommandHandler petCompatibilityCommands;
    private final ContentRegistry contentRegistry;
    private final WorldPlayer worldPlayer;
    private final SessionBridge bridge;

    LocalCommandDispatcher(
        LocalBankRequestHandler bankRequests,
        LocalDiagnosticCommandHandler diagnosticCommands,
        LocalRegionDevCommandHandler regionDevCommands,
        LocalPrayerMagicCommandHandler prayerMagicCommands,
        LocalMiniPetCommandHandler miniPetCommands,
        LocalCosmeticCommandHandler cosmeticCommands,
        LocalDevWorldCommandHandler devWorldCommands,
        DevAuthorityWorkbench dev,
        LocalDevSessionCommandHandler devSessionCommands,
        LocalDevPetCommandHandler devPetCommands,
        LocalDevPlayerCommandHandler devPlayerCommands,
        LocalDevNpcCommandHandler devNpcCommands,
        LocalDevToolCommandHandler devToolCommands,
        LocalVoidglassCommandHandler voidglassCommands,
        LocalPetRuntimeCommandHandler petRuntimeCommands,
        LocalCompColorsCommandHandler compColorsCommands,
        LocalCombatCommandHandler combatCommands,
        LocalPetCompatibilityCommandHandler petCompatibilityCommands,
        ContentRegistry contentRegistry,
        WorldPlayer worldPlayer,
        SessionBridge bridge
    ){
        this.bankRequests=Objects.requireNonNull(bankRequests,"bankRequests");
        this.diagnosticCommands=Objects.requireNonNull(diagnosticCommands,"diagnosticCommands");
        this.regionDevCommands=Objects.requireNonNull(regionDevCommands,"regionDevCommands");
        this.prayerMagicCommands=Objects.requireNonNull(prayerMagicCommands,"prayerMagicCommands");
        this.miniPetCommands=Objects.requireNonNull(miniPetCommands,"miniPetCommands");
        this.cosmeticCommands=Objects.requireNonNull(cosmeticCommands,"cosmeticCommands");
        this.compColorsCommands=Objects.requireNonNull(compColorsCommands,"compColorsCommands");
        this.petCompatibilityCommands=Objects.requireNonNull(petCompatibilityCommands,"petCompatibilityCommands");
        this.combatCommands=Objects.requireNonNull(combatCommands,"combatCommands");
        this.devSessionCommands=Objects.requireNonNull(devSessionCommands,"devSessionCommands");
        this.petRuntimeCommands=Objects.requireNonNull(petRuntimeCommands,"petRuntimeCommands");
        this.contentCommandActions=
            new LocalContentCommandActionExecutor(
                this.cosmeticCommands,
                this.compColorsCommands,
                this.miniPetCommands,
                this.petCompatibilityCommands,
                this.combatCommands,
                this.diagnosticCommands,
                this.devSessionCommands,
                this.prayerMagicCommands,
                this.petRuntimeCommands
            );
        this.devWorldCommands=Objects.requireNonNull(devWorldCommands,"devWorldCommands");
        this.dev=Objects.requireNonNull(dev,"dev");
        this.devPetCommands=Objects.requireNonNull(devPetCommands,"devPetCommands");
        this.devPlayerCommands=Objects.requireNonNull(devPlayerCommands,"devPlayerCommands");
        this.devNpcCommands=Objects.requireNonNull(devNpcCommands,"devNpcCommands");
        this.devToolCommands=Objects.requireNonNull(devToolCommands,"devToolCommands");
        this.voidglassCommands=Objects.requireNonNull(voidglassCommands,"voidglassCommands");
        this.contentRegistry=Objects.requireNonNull(contentRegistry,"contentRegistry");
        this.worldPlayer=Objects.requireNonNull(worldPlayer,"worldPlayer");
        this.bridge=Objects.requireNonNull(bridge,"bridge");
    }

    boolean handle(
        String command,
        boolean decoderAligned,
        String username,
        String loginAlias,
        boolean persistentAccount,
        long sessionWorldTick,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        if(command==null)return false;

        LocalBankRequestHandler.Result bankCommand=
            bankRequests.handleCommand(command,serverPackets);
        if(bankCommand!=null){
            if(bankCommand.saveReason!=null)
                bridge.saveAccount(tag,bankCommand.saveReason);
            System.out.println(tag+bankCommand.logText+
                " decoderAligned="+decoderAligned);
            return true;
        }

        String clean=clean(command);
        String[] p=tokens(clean);

        LocalShopCommandHandler.Result shopCommand=
            bridge.handleShopCommand(
                p
            );
        if(shopCommand!=null){
            /*
             * Canonical mutation has already committed inside the semantic
             * handler. Request persistence before advisory S2C253 publication
             * so a transport failure cannot suppress the successful save.
             */
            if(shopCommand.saveReason!=null)
                bridge.saveAccount(
                    tag,
                    shopCommand.saveReason
                );

            new SocialChatPresentationPublisher(
                serverPackets
            ).serverMessage(
                shopCommand.clientMessage
            );

            System.out.println(
                tag+
                shopCommand.logText+
                " clientFeedback=true"+
                " persistenceBeforeFeedback=true"+
                " route=EXACT_CURRENT_C2S103"
            );
            return true;
        }

        LocalSlayerCommandHandler.Result slayerCommand=
            bridge.handleSlayerCommand(
                p,
                sessionWorldTick
            );
        if(slayerCommand!=null){
            if(slayerCommand.saveReason!=null)
                bridge.saveAccount(
                    tag,
                    slayerCommand.saveReason
                );

            boolean rootOpened=false;

            if(slayerCommand.openRoot)
                rootOpened=
                    bridge.openBloodSlayer(
                        serverPackets
                    );

            new SocialChatPresentationPublisher(
                serverPackets
            ).serverMessage(
                slayerCommand.openRoot&&
                    !rootOpened
                    ?"Blood Slayer interface unavailable."
                    :slayerCommand.clientMessage
            );

            System.out.println(
                tag+
                slayerCommand.logText+
                " clientFeedback=true"+
                " route=EXACT_CURRENT_C2S103"+
                " rootRequested="+
                slayerCommand.openRoot+
                " rootOpened="+
                rootOpened+
                " persistenceBeforeFeedback="+
                (slayerCommand.saveReason!=null)
            );
            return true;
        }

        if(dispatchDuelLifecycleCommand(
                p,
                bridge,
                serverPackets,
                tag
            ))
            return true;

        try{
            ContentResult content=
                contentRegistry.dispatchCommand(
                    worldPlayer,
                    command,
                    serverPackets
                );

            if(content!=null){
                if(content.hasAction()){
                    String contentActionKey=
                        content.actionKey();
                    LocalContentCommandActionExecutor.Outcome
                        action;

                    if(isItemLibraryRootAction(
                            contentActionKey
                        )){
                        final LocalContentCommandActionExecutor.Outcome[]
                            rootAction={null};

                        RootReplacingCommandDispatch rootDispatch=
                            bridge.handleRootReplacingCommand(
                                ()->{
                                    rootAction[0]=
                                        contentCommandActions
                                            .executeOutcome(
                                                contentActionKey,
                                                command,
                                                username,
                                                loginAlias,
                                                persistentAccount,
                                                bridge.scenePublisher(),
                                                serverPackets
                                            );
                                    return true;
                                }
                            );

                        if(!rootDispatch.admitted){
                            System.out.println(
                                tag+
                                "V5150_ITEM_LIBRARY_DEV_OPEN "+
                                "result=LIFECYCLE_REJECTED"
                            );
                            return true;
                        }

                        action=rootAction[0];

                        if(action==null)
                            throw new IllegalStateException(
                                "Item Library root action produced no runtime outcome"
                            );
                    }else{
                        action=
                            contentCommandActions.executeOutcome(
                                contentActionKey,
                                command,
                                username,
                                loginAlias,
                                persistentAccount,
                                bridge.scenePublisher(),
                                serverPackets
                            );
                    }

                    if(action.dialogResult!=null){
                        bridge.applyPetDialog(
                            action.dialogResult,
                            tag
                        );
                        return true;
                    }

                    if(action.logLines!=null){
                        for(String line:
                                action.logLines)
                            System.out.println(
                                tag+line
                            );
                        return true;
                    }

                    content=
                        action.contentResult;
                }

                if(content.saveReason()!=null)
                    bridge.saveAccount(
                        tag,
                        content.saveReason()
                    );

                System.out.println(
                    tag+content.logText()
                );
                return true;
            }
        }catch(IOException e){
            throw e;
        }catch(RuntimeException e){
            throw e;
        }catch(Exception e){
            throw new IOException(
                "content command failed command="+
                clean,
                e
            );
        }

        if(isLootingBagRoute(p)){
            boolean opened=
                bridge.openLootingBag(
                    serverPackets
                );

            System.out.println(
                tag+
                "G8_LOOTING_BAG_UI_COMMAND"+
                " opened="+opened+
                " root="+
                LootingBagPresentation.ROOT+
                " container="+
                LootingBagPresentation.CONTAINER_WIDGET+
                " presentationAuthority="+
                LootingBagPresentation.PRESENTATION_AUTHORITY+
                " routeAuthority=CUSTOM_LOCALLAB"
            );
            return true;
        }

        if(isTournamentRoute(p)){
            boolean opened=
                bridge.openTournament(
                    serverPackets
                );

            System.out.println(
                tag+
                "G91_TOURNAMENT_UI_COMMAND"+
                " opened="+opened+
                " root="+
                TournamentPresentation.TOURNAMENT_ROOT+
                " presentationAuthority="+
                TournamentPresentation.PRESENTATION_AUTHORITY+
                " gameplayAuthority="+
                LocalLabTournamentRuntime.AUTHORITY+
                " routeAuthority=CUSTOM_LOCALLAB"
            );
            return true;
        }

        if(dispatchPkRatingsCommand(
                p,
                bridge,
                serverPackets,
                tag
            ))
            return true;

        if(isDuelRoute(p)){
            String target=
                duelTarget(p);
            boolean opened=
                bridge.openDuel(
                    target,
                    serverPackets
                );

            System.out.println(
                tag+
                "G103_NORMAL_DUEL_UI_COMMAND"+
                " opened="+opened+
                " target="+target+
                " root="+
                NormalDuelPresentation.SELECTOR_ROOT+
                " presentationAuthority="+
                NormalDuelPresentation.PRESENTATION_AUTHORITY+
                " gameplayAuthority="+
                LocalLabDuelRuntime.AUTHORITY+
                " routeAuthority=CUSTOM_LOCALLAB"+
                " originalTargetTransportClaim=false"
            );
            return true;
        }

        if(isDevPanelRoute(p)){
            bridge.openDevPanel(serverPackets);
            System.out.println(
                tag+"V5172_DEV_PANEL_OPEN route="+p[0]+
                " authority="+ContentAuthorityRepository.summary()+
                " runtimeWeaponProfiles="+V913WeaponRuntimeAuthority.count());
            return true;
        }

        if(isMonsterSpawnerRoute(p)){
            boolean opened=
                bridge.openMonsterSpawner(
                    serverPackets
                );

            System.out.println(
                tag+
                "MONSTER_SPAWNER_UI_COMMAND "+
                "route="+p[0]+
                " opened="+opened+
                " presentationAuthority="+
                MonsterSpawnerPresentation.PRESENTATION_AUTHORITY+
                " routeAuthority=CUSTOM_LOCALLAB"
            );
            return true;
        }

        if(diagnosticCommands.itemLibrarySearchWillOpen(
                p
            )){
            RootReplacingCommandDispatch rootDispatch=
                bridge.handleRootReplacingCommand(
                    ()->
                        diagnosticCommands.handle(
                            p,
                            serverPackets,
                            tag,
                            username,
                            loginAlias,
                            persistentAccount,
                            bridge.scenePublisher()
                        )
                );

            if(!rootDispatch.admitted){
                System.out.println(
                    tag+
                    "V5150_ITEM_LIBRARY_IGSEARCH "+
                    "result=LIFECYCLE_REJECTED"
                );
                return true;
            }

            if(!rootDispatch.handled)
                throw new IllegalStateException(
                    "known Item Library root command was not handled"
                );

            return true;
        }

        if(diagnosticCommands.handle(
            p,
            serverPackets,
            tag,
            username,
            loginAlias,
            persistentAccount,
            bridge.scenePublisher()
        ))return true;

        LocalRegionDevCommandHandler.Result regionDevCommand=
            regionDevCommands.handle(
                p,
                username,
                bridge.scenePublisher(),
                serverPackets
            );
        if(regionDevCommand!=null){
            if(regionDevCommand.scenePublisher!=null)
                bridge.replaceScenePublisher(regionDevCommand.scenePublisher);
            if(regionDevCommand.saveReason!=null)
                bridge.saveAccount(tag,regionDevCommand.saveReason);
            System.out.println(tag+regionDevCommand.logText);
            return true;
        }

        if(devWorldCommands.handle(
            p,
            bridge.scenePublisher(),
            username,
            sessionWorldTick,
            tag
        ))return true;

        if(dev.trace().enabled()&&
           p.length>0&&
           p[0].toLowerCase(Locale.ROOT).startsWith("dev")){
            dev.trace().record(
                "DEV_COMMAND_REQUEST",
                "C2S103 command=\""+clean+"\" -> router="+p[0],
                "EXACT_C2S103_TRANSPORT/LOCAL_DEV_ROUTE"
            );
        }

        List<String> devPetCommand=
            devPetCommands.handle(p,serverPackets);
        if(devPetCommand!=null){
            for(String line:devPetCommand)
                System.out.println(tag+line);
            return true;
        }

        List<String> devPlayerCommand=
            devPlayerCommands.handle(p,username,serverPackets);
        if(devPlayerCommand!=null){
            for(String line:devPlayerCommand)
                System.out.println(tag+line);
            return true;
        }

        List<String> devNpcCommand=
            devNpcCommands.handle(p,serverPackets);
        if(devNpcCommand!=null){
            for(String line:devNpcCommand)
                System.out.println(tag+line);
            return true;
        }

        List<String> devToolCommand=
            devToolCommands.handle(p,serverPackets);
        if(devToolCommand!=null){
            for(String line:devToolCommand)
                System.out.println(tag+line);
            return true;
        }

        LocalVoidglassCommandHandler.Outcome voidglassCommand=
            voidglassCommands.handle(p,serverPackets);
        if(voidglassCommand!=null){
            if(voidglassCommand.saveReason!=null)
                bridge.saveAccount(tag,voidglassCommand.saveReason);
            System.out.println(tag+voidglassCommand.text);
            return true;
        }

        List<String> petRuntimeCommand=
            petRuntimeCommands.handle(p,serverPackets);
        if(petRuntimeCommand!=null){
            for(String line:petRuntimeCommand)
                System.out.println(tag+line);
            return true;
        }

        return false;
    }

    static boolean dispatchPkRatingsCommand(
        String[] tokens,
        SessionBridge bridge,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        if(!isPkRatingsRoute(tokens))
            return false;

        boolean opened=
            Objects.requireNonNull(
                bridge,
                "bridge"
            ).openPkRatings(
                Objects.requireNonNull(
                    serverPackets,
                    "serverPackets"
                )
            );

        System.out.println(
            tag+
            "G111_PK_RATINGS_COMMAND"+
            " opened="+opened+
            " root="+
            PkRatingsPresentation.RATINGS_ROOT+
            " subtype="+
            PkRatingsPresentation.APPLICATION_SUBTYPE+
            " presentationAuthority="+
            PkRatingsPresentation.PRESENTATION_AUTHORITY+
            " gameplayAuthority="+
            LocalPkRatingsUiHandler.AUTHORITY+
            " routeAuthority=CUSTOM_LOCALLAB"+
            " ratingFormulaClaim=false"+
            " originalCommandClaim=false"
        );

        return true;
    }

    static boolean dispatchDuelLifecycleCommand(
        String[] tokens,
        SessionBridge bridge,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        LocalDuelCommandHandler.Result duelCommand=
            Objects.requireNonNull(
                bridge,
                "bridge"
            ).handleDuelCommand(
                tokens
            );

        if(duelCommand==null)
            return false;

        new SocialChatPresentationPublisher(
            Objects.requireNonNull(
                serverPackets,
                "serverPackets"
            )
        ).serverMessage(
            duelCommand.clientMessage
        );

        System.out.println(
            tag+
            duelCommand.logText+
            " clientFeedback=true"+
            " route=EXACT_CURRENT_C2S103"+
            " routeAuthority="+
            LocalDuelCommandHandler.ROUTE_AUTHORITY+
            " persistenceClaim=false"
        );

        return true;
    }

    static String clean(String command){
        String clean=command==null?"":command.trim();
        if(clean.startsWith("::"))
            clean=clean.substring(2);
        return clean;
    }

    static String[] tokens(String clean){
        return clean.split("\\s+");
    }

    static boolean isItemLibraryRootAction(
        String actionKey
    ){
        return actionKey!=null&&
            actionKey.startsWith(
                LocalDiagnosticContentModule
                    .ITEMLIB_OPEN_ACTION_PREFIX+
                ":"
            );
    }

    static boolean isLootingBagRoute(String[] p){
        return p!=null&&
            p.length==1&&
            (
                p[0].equalsIgnoreCase(
                    "lootingbag"
                )||
                p[0].equalsIgnoreCase(
                    "lootbag"
                )
            );
    }

    static boolean isTournamentRoute(String[] p){
        return p!=null&&
            p.length==1&&
            (
                p[0].equalsIgnoreCase(
                    "tournament"
                )||
                p[0].equalsIgnoreCase(
                    "tourny"
                )
            );
    }

    static boolean isPkRatingsRoute(String[] p){
        return p!=null&&
            p.length==1&&
            (
                p[0].equalsIgnoreCase(
                    "pkratings"
                )||
                p[0].equalsIgnoreCase(
                    "pkrating"
                )
            );
    }

    static boolean isDuelRoute(String[] p){
        return p!=null&&
            p.length>=2&&
            p[0].equalsIgnoreCase(
                "duel"
            );
    }

    static String duelTarget(String[] p){
        if(!isDuelRoute(p))
            throw new IllegalArgumentException(
                "duel route requires target"
            );

        StringBuilder out=
            new StringBuilder();

        for(int i=1;i<p.length;i++){
            if(i>1)
                out.append(' ');
            out.append(p[i]);
        }

        String target=
            out.toString().trim();

        if(target.isEmpty())
            throw new IllegalArgumentException(
                "duel target blank"
            );

        return target;
    }

    static boolean isDevPanelRoute(String[] p){
        return p!=null&&
            p.length>=1&&
            (
                p[0].equalsIgnoreCase("devpanel")||
                p[0].equalsIgnoreCase("devui")||
                p[0].equalsIgnoreCase("lab")||
                (
                    p[0].equalsIgnoreCase("dev")&&
                    p.length>=2&&
                    p[1].equalsIgnoreCase("panel")
                )
            );
    }

    static boolean isMonsterSpawnerRoute(String[] p){
        return p!=null&&
            p.length==1&&
            (
                p[0].equalsIgnoreCase(
                    "monsterspawner"
                )||
                p[0].equalsIgnoreCase(
                    "mspawn"
                )
            );
    }
}
