package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class LocalMonsterSpawnerUiHandlerTest {
    private static final String OWNER=
        "monster-ui-owner";
    private static final String POLICY_AUTHORITY=
        "CUSTOM_LOCALLAB_MONSTER_UI_POLICY";
    private static final String CATALOG_AUTHORITY=
        "CUSTOM_LOCALLAB_MONSTER_UI_CATALOG";

    public static void main(String[] args)
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );

        try{
            MonsterSpawnerService service=
                configuredService(
                    world
                );

            int[] activationCalls={0};

            LocalMonsterSpawnerUiHandler handler=
                new LocalMonsterSpawnerUiHandler(
                    service,
                    OWNER,
                    new LocalMonsterSpawnerUiHandler
                        .ActivationBudgetResolver(){
                        @Override public int spawnBudget(
                            LocalMonsterSpawnerUiHandler
                                .Context context
                        ){
                            activationCalls[0]++;

                            require(
                                context.session!=null&&
                                OWNER.equals(
                                    context.ownerRef
                                ),
                                "activation context"
                            );

                            return 3;
                        }

                        @Override public String authority(){
                            return POLICY_AUTHORITY;
                        }
                    },
                    new LocalMonsterSpawnerUiHandler
                        .SelectedNpcLabelResolver(){
                        @Override public String label(
                            MonsterSpawnerService
                                .CatalogEntry entry
                        ){
                            return "NPC-"+entry.definitionId;
                        }

                        @Override public String authority(){
                            return CATALOG_AUTHORITY;
                        }
                    }
                );

            exactRowSelection(
                world,
                service,
                handler
            );
            exactToggle(
                world,
                service,
                handler,
                activationCalls
            );
            activationSnapshotRaceFailsClosed(
                world
            );
            deactivationSnapshotRaceFailsClosed(
                world
            );
            rowLabelFailureAtomic(
                world
            );
            rowInvalidTextFailureAtomic(
                world
            );
            rowAuthorityFailureAtomic(
                world
            );
            rowPacketFailureAtomic(
                world
            );
            rowCatalogRaceFailsClosed(
                world
            );
            rowSessionRaceFailsClosed(
                world
            );
            unattachedControlsIgnored(
                service,
                handler
            );
            failClosedAuthority(
                service
            );
            noSpawnPolicyInAdapter();

            System.out.println(
                "MONSTER_SPAWNER_UI_SERVICE_ADAPTER_PASS "+
                "exactRows=true "+
                "exactToggle=true "+
                "callerBudget=true "+
                "selectedText41019=true "+
                "rowFailureAtomic=true "+
                "rowPacketFailureAtomic=true "+
                "catalogCompareSelect=true "+
                "noSpawn=true "+
                "unattachedIgnored=true "+
                "policyOwned=false"
            );
        }finally{
            world.close();
        }
    }

    private static void exactRowSelection(
        World world,
        MonsterSpawnerService service,
        LocalMonsterSpawnerUiHandler handler
    )throws Exception{
        ByteArrayOutputStream firstBytes=
            new ByteArrayOutputStream();

        LocalMonsterSpawnerUiHandler.Result first=
            handler.handle(
                MonsterSpawnerPresentation
                    .rowWidget(0),
                writer(firstBytes)
            );

        require(
            first!=null&&
            first.status==
                LocalMonsterSpawnerUiHandler
                    .Status.ROW_SELECTED&&
            first.rowIndex==0&&
            first.session.selectedRowIndex!=null&&
            first.session.selectedRowIndex.intValue()==0&&
            service.getSession(OWNER)
                .selectedRowIndex.intValue()==0&&
            world.npcs().size()==0,
            "first row selection"
        );

        requireSelectedTextPacket(
            firstBytes.toByteArray(),
            "NPC-1500"
        );

        ByteArrayOutputStream lastBytes=
            new ByteArrayOutputStream();

        LocalMonsterSpawnerUiHandler.Result last=
            handler.handle(
                MonsterSpawnerPresentation
                    .rowWidget(21),
                writer(lastBytes)
            );

        require(
            last!=null&&
            last.status==
                LocalMonsterSpawnerUiHandler
                    .Status.ROW_SELECTED&&
            last.rowIndex==21&&
            last.session.selectedRowIndex!=null&&
            last.session.selectedRowIndex.intValue()==21&&
            service.getSession(OWNER)
                .selectedDefinitionId.intValue()==1521&&
            world.npcs().size()==0,
            "last row selection"
        );

        requireSelectedTextPacket(
            lastBytes.toByteArray(),
            "NPC-1521"
        );
    }

    private static void exactToggle(
        World world,
        MonsterSpawnerService service,
        LocalMonsterSpawnerUiHandler handler,
        int[] activationCalls
    )throws Exception{
        LocalMonsterSpawnerUiHandler.Result activated=
            handler.handle(
                MonsterSpawnerPresentation
                    .TOGGLE_WIDGET,
                writer(
                    new ByteArrayOutputStream()
                )
            );

        MonsterSpawnerService.SessionSnapshot active=
            service.getSession(
                OWNER
            );

        require(
            activated!=null&&
            activated.status==
                LocalMonsterSpawnerUiHandler
                    .Status.ACTIVATED&&
            activated.activationBudget==3&&
            active.active&&
            active.remainingSpawnBudget==3&&
            activationCalls[0]==1&&
            world.npcs().size()==0,
            "caller activation budget"
        );

        expect(
            IllegalStateException.class,
            ()->handler.handle(
                MonsterSpawnerPresentation
                    .rowWidget(1),
                writer(
                    new ByteArrayOutputStream()
                )
            ),
            "row change while active"
        );

        MonsterSpawnerService.SessionSnapshot
            afterRejectedRow=
                service.getSession(
                    OWNER
                );

        require(
            afterRejectedRow.active&&
            afterRejectedRow.remainingSpawnBudget==3&&
            afterRejectedRow.selectedRowIndex!=null&&
            afterRejectedRow.selectedRowIndex.intValue()==21&&
            world.npcs().size()==0,
            "active row rejection mutated service"
        );

        LocalMonsterSpawnerUiHandler.Result deactivated=
            handler.handle(
                MonsterSpawnerPresentation
                    .TOGGLE_WIDGET,
                writer(
                    new ByteArrayOutputStream()
                )
            );

        MonsterSpawnerService.SessionSnapshot inactive=
            service.getSession(
                OWNER
            );

        require(
            deactivated.status==
                LocalMonsterSpawnerUiHandler
                    .Status.DEACTIVATED&&
            !inactive.active&&
            inactive.remainingSpawnBudget==0&&
            activationCalls[0]==1&&
            world.npcs().size()==0,
            "deactivation"
        );
    }

    private static void activationSnapshotRaceFailsClosed(
        World world
    )throws Exception{
        final String owner="monster-ui-race-owner";
        MonsterSpawnerService service=
            new MonsterSpawnerService(
                world.npcs()
            );

        List<MonsterSpawnerService.CatalogEntry>
            catalog=
                new ArrayList<>();

        catalog.add(
            new MonsterSpawnerService.CatalogEntry(
                0,
                "race-row-0",
                1600
            )
        );
        catalog.add(
            new MonsterSpawnerService.CatalogEntry(
                1,
                "race-row-1",
                1601
            )
        );

        service.replaceCatalog(
            catalog,
            CATALOG_AUTHORITY
        );
        service.openSession(
            owner,
            POLICY_AUTHORITY
        );
        service.selectRow(
            owner,
            0
        );

        LocalMonsterSpawnerUiHandler handler=
            new LocalMonsterSpawnerUiHandler(
                service,
                owner,
                new LocalMonsterSpawnerUiHandler
                    .ActivationBudgetResolver(){
                    @Override public int spawnBudget(
                        LocalMonsterSpawnerUiHandler
                            .Context context
                    ){
                        service.selectRow(
                            owner,
                            1
                        );
                        return 7;
                    }

                    @Override public String authority(){
                        return POLICY_AUTHORITY;
                    }
                },
                labels()
            );

        expect(
            IllegalStateException.class,
            ()->handler.handle(
                MonsterSpawnerPresentation
                    .TOGGLE_WIDGET,
                writer(
                    new ByteArrayOutputStream()
                )
            ),
            "activation snapshot changed"
        );

        MonsterSpawnerService.SessionSnapshot after=
            service.getSession(
                owner
            );

        require(
            !after.active&&
            after.remainingSpawnBudget==0&&
            after.selectedRowIndex!=null&&
            after.selectedRowIndex.intValue()==1&&
            world.npcs().size()==0,
            "stale activation policy crossed row change"
        );
    }

    private static void deactivationSnapshotRaceFailsClosed(
        World world
    ){
        final String owner="monster-ui-deactivate-race-owner";
        MonsterSpawnerService service=
            new MonsterSpawnerService(
                world.npcs()
            );

        List<MonsterSpawnerService.CatalogEntry>
            catalog=
                new ArrayList<>();

        catalog.add(
            new MonsterSpawnerService.CatalogEntry(
                0,
                "deactivate-race-row",
                1610
            )
        );

        service.replaceCatalog(
            catalog,
            CATALOG_AUTHORITY
        );
        service.openSession(
            owner,
            POLICY_AUTHORITY
        );
        service.selectRow(
            owner,
            0
        );
        service.activate(
            owner,
            2
        );

        MonsterSpawnerService.SessionSnapshot stale=
            service.getSession(
                owner
            );

        service.deactivate(
            owner
        );
        service.activate(
            owner,
            5
        );

        expect(
            IllegalStateException.class,
            ()->service.deactivateIfCurrent(
                owner,
                stale
            ),
            "deactivation snapshot changed"
        );

        MonsterSpawnerService.SessionSnapshot current=
            service.getSession(
                owner
            );

        require(
            current.active&&
            current.remainingSpawnBudget==5&&
            world.npcs().size()==0,
            "stale deactivation crossed newer activation"
        );
    }

    private static void rowLabelFailureAtomic(
        World world
    ){
        final String owner="monster-ui-row-label-failure";
        MonsterSpawnerService service=
            rowAtomicService(
                world,
                owner
            );

        LocalMonsterSpawnerUiHandler handler=
            new LocalMonsterSpawnerUiHandler(
                service,
                owner,
                fixedBudget(),
                new LocalMonsterSpawnerUiHandler
                    .SelectedNpcLabelResolver(){
                    @Override public String label(
                        MonsterSpawnerService.CatalogEntry entry
                    ){
                        throw new IllegalStateException(
                            "EXPECTED_ROW_LABEL_FAILURE"
                        );
                    }

                    @Override public String authority(){
                        return CATALOG_AUTHORITY;
                    }
                }
            );

        expect(
            IllegalStateException.class,
            ()->handler.handle(
                MonsterSpawnerPresentation.rowWidget(0),
                writer(new ByteArrayOutputStream())
            ),
            "row label failure"
        );

        require(
            service.getSession(owner)
                .selectedRowIndex==null,
            "row label failure mutated selection"
        );
    }

    private static void rowInvalidTextFailureAtomic(
        World world
    ){
        final String owner="monster-ui-row-invalid-text";
        MonsterSpawnerService service=
            rowAtomicService(
                world,
                owner
            );

        LocalMonsterSpawnerUiHandler handler=
            new LocalMonsterSpawnerUiHandler(
                service,
                owner,
                fixedBudget(),
                new LocalMonsterSpawnerUiHandler
                    .SelectedNpcLabelResolver(){
                    @Override public String label(
                        MonsterSpawnerService.CatalogEntry entry
                    ){
                        return "   ";
                    }

                    @Override public String authority(){
                        return CATALOG_AUTHORITY;
                    }
                }
            );

        expect(
            IllegalArgumentException.class,
            ()->handler.handle(
                MonsterSpawnerPresentation.rowWidget(0),
                writer(new ByteArrayOutputStream())
            ),
            "row invalid presentation text"
        );

        require(
            service.getSession(owner)
                .selectedRowIndex==null,
            "invalid row presentation text mutated selection"
        );
    }

    private static void rowAuthorityFailureAtomic(
        World world
    ){
        final String owner="monster-ui-row-authority-failure";
        MonsterSpawnerService service=
            rowAtomicService(
                world,
                owner
            );
        final int[] authorityCalls={0};

        LocalMonsterSpawnerUiHandler handler=
            new LocalMonsterSpawnerUiHandler(
                service,
                owner,
                fixedBudget(),
                new LocalMonsterSpawnerUiHandler
                    .SelectedNpcLabelResolver(){
                    @Override public String label(
                        MonsterSpawnerService.CatalogEntry entry
                    ){
                        return "NPC-"+entry.definitionId;
                    }

                    @Override public String authority(){
                        return authorityCalls[0]++==0
                            ?CATALOG_AUTHORITY
                            :"EXACT_CURRENT_CLIENT";
                    }
                }
            );

        expect(
            IllegalArgumentException.class,
            ()->handler.handle(
                MonsterSpawnerPresentation.rowWidget(0),
                writer(new ByteArrayOutputStream())
            ),
            "row label authority changed"
        );

        require(
            service.getSession(owner)
                .selectedRowIndex==null,
            "row authority failure mutated selection"
        );
    }

    private static void rowPacketFailureAtomic(
        World world
    ){
        final String owner="monster-ui-row-packet-failure";
        MonsterSpawnerService service=
            rowAtomicService(
                world,
                owner
            );

        LocalMonsterSpawnerUiHandler handler=
            new LocalMonsterSpawnerUiHandler(
                service,
                owner,
                fixedBudget(),
                labels()
            );

        expect(
            IOException.class,
            ()->handler.handle(
                MonsterSpawnerPresentation.rowWidget(0),
                failingWriter()
            ),
            "row packet failure"
        );

        require(
            service.getSession(owner)
                .selectedRowIndex==null,
            "row packet failure mutated selection"
        );
    }

    private static void rowCatalogRaceFailsClosed(
        World world
    ){
        final String owner="monster-ui-row-catalog-race";
        MonsterSpawnerService service=
            rowAtomicService(
                world,
                owner
            );

        LocalMonsterSpawnerUiHandler handler=
            new LocalMonsterSpawnerUiHandler(
                service,
                owner,
                fixedBudget(),
                new LocalMonsterSpawnerUiHandler
                    .SelectedNpcLabelResolver(){
                    @Override public String label(
                        MonsterSpawnerService.CatalogEntry entry
                    ){
                        List<MonsterSpawnerService.CatalogEntry>
                            replacement=
                                new ArrayList<>();

                        replacement.add(
                            new MonsterSpawnerService.CatalogEntry(
                                0,
                                "row-race-replaced",
                                1620
                            )
                        );
                        replacement.add(
                            new MonsterSpawnerService.CatalogEntry(
                                1,
                                "row-race-1",
                                1611
                            )
                        );

                        service.replaceCatalog(
                            replacement,
                            CATALOG_AUTHORITY
                        );

                        return "NPC-"+entry.definitionId;
                    }

                    @Override public String authority(){
                        return CATALOG_AUTHORITY;
                    }
                }
            );

        expect(
            IllegalStateException.class,
            ()->handler.handle(
                MonsterSpawnerPresentation.rowWidget(0),
                writer(new ByteArrayOutputStream())
            ),
            "row catalog changed"
        );

        MonsterSpawnerService.SessionSnapshot after=
            service.getSession(
                owner
            );

        require(
            after.selectedRowIndex==null&&
            service.catalog().row(0).definitionId==1620,
            "stale row candidate crossed catalog replacement"
        );
    }

    private static void rowSessionRaceFailsClosed(
        World world
    ){
        final String owner="monster-ui-row-session-race";
        MonsterSpawnerService service=
            rowAtomicService(
                world,
                owner
            );

        LocalMonsterSpawnerUiHandler handler=
            new LocalMonsterSpawnerUiHandler(
                service,
                owner,
                fixedBudget(),
                new LocalMonsterSpawnerUiHandler
                    .SelectedNpcLabelResolver(){
                    @Override public String label(
                        MonsterSpawnerService.CatalogEntry entry
                    ){
                        service.selectRow(
                            owner,
                            1
                        );

                        return "NPC-"+entry.definitionId;
                    }

                    @Override public String authority(){
                        return CATALOG_AUTHORITY;
                    }
                }
            );

        expect(
            IllegalStateException.class,
            ()->handler.handle(
                MonsterSpawnerPresentation.rowWidget(0),
                writer(new ByteArrayOutputStream())
            ),
            "row session changed"
        );

        MonsterSpawnerService.SessionSnapshot after=
            service.getSession(
                owner
            );

        require(
            after.selectedRowIndex!=null&&
            after.selectedRowIndex.intValue()==1&&
            after.selectedDefinitionId!=null&&
            after.selectedDefinitionId.intValue()==1611,
            "stale row candidate overwrote newer selection"
        );
    }

    private static MonsterSpawnerService rowAtomicService(
        World world,
        String owner
    ){
        MonsterSpawnerService service=
            new MonsterSpawnerService(
                world.npcs()
            );
        List<MonsterSpawnerService.CatalogEntry> rows=
            new ArrayList<>();

        rows.add(
            new MonsterSpawnerService.CatalogEntry(
                0,
                "row-race-0",
                1610
            )
        );
        rows.add(
            new MonsterSpawnerService.CatalogEntry(
                1,
                "row-race-1",
                1611
            )
        );

        service.replaceCatalog(
            rows,
            CATALOG_AUTHORITY
        );
        service.openSession(
            owner,
            POLICY_AUTHORITY
        );

        return service;
    }

    private static LocalMonsterSpawnerUiHandler
        .ActivationBudgetResolver fixedBudget(){
        return new LocalMonsterSpawnerUiHandler
            .ActivationBudgetResolver(){
            @Override public int spawnBudget(
                LocalMonsterSpawnerUiHandler.Context context
            ){
                return 1;
            }

            @Override public String authority(){
                return POLICY_AUTHORITY;
            }
        };
    }

    private static void unattachedControlsIgnored(
        MonsterSpawnerService service,
        LocalMonsterSpawnerUiHandler handler
    )throws Exception{
        MonsterSpawnerService.SessionSnapshot before=
            service.getSession(
                OWNER
            );

        int[] ignored={
            MonsterSpawnerPresentation
                .UNATTACHED_DISTANCED_WIDGET,
            MonsterSpawnerPresentation
                .UNATTACHED_X3_WIDGET,
            MonsterSpawnerPresentation
                .SCROLL_ROOT,
            MonsterSpawnerPresentation
                .LAST_ROW_WIDGET+1
        };

        for(int widget:ignored)
            require(
                handler.handle(
                    widget,
                    writer(
                        new ByteArrayOutputStream()
                    )
                )==null,
                "ignored widget "+widget
            );

        MonsterSpawnerService.SessionSnapshot after=
            service.getSession(
                OWNER
            );

        require(
            before.selectedRowIndex.equals(
                after.selectedRowIndex
            )&&
            before.active==after.active&&
            before.remainingSpawnBudget==
                after.remainingSpawnBudget,
            "ignored widgets mutated service"
        );
    }

    private static void failClosedAuthority(
        MonsterSpawnerService service
    ){
        expect(
            IllegalArgumentException.class,
            ()->new LocalMonsterSpawnerUiHandler(
                service,
                OWNER,
                new LocalMonsterSpawnerUiHandler
                    .ActivationBudgetResolver(){
                    @Override public int spawnBudget(
                        LocalMonsterSpawnerUiHandler
                            .Context context
                    ){ return 1; }

                    @Override public String authority(){
                        return "EXACT_CURRENT_CLIENT";
                    }
                },
                labels()
            ),
            "client authority cannot own budget"
        );

        LocalMonsterSpawnerUiHandler mismatch=
            new LocalMonsterSpawnerUiHandler(
                service,
                OWNER,
                new LocalMonsterSpawnerUiHandler
                    .ActivationBudgetResolver(){
                    @Override public int spawnBudget(
                        LocalMonsterSpawnerUiHandler
                            .Context context
                    ){ return 1; }

                    @Override public String authority(){
                        return "CUSTOM_LOCALLAB_OTHER_POLICY";
                    }
                },
                labels()
            );

        expect(
            IllegalStateException.class,
            ()->mismatch.handle(
                MonsterSpawnerPresentation
                    .TOGGLE_WIDGET,
                writer(
                    new ByteArrayOutputStream()
                )
            ),
            "session policy authority mismatch"
        );
    }

    private static void noSpawnPolicyInAdapter(){
        for(Method method:
                LocalMonsterSpawnerUiHandler.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase();

            if(name.equals("spawn")||
               name.equals("spawnselected")||
               name.equals("spawnandbind"))
                throw new AssertionError(
                    "UI adapter owns spawn method "+
                    method.getName()
                );
        }
    }

    private static LocalMonsterSpawnerUiHandler
        .SelectedNpcLabelResolver labels(){
        return new LocalMonsterSpawnerUiHandler
            .SelectedNpcLabelResolver(){
            @Override public String label(
                MonsterSpawnerService
                    .CatalogEntry entry
            ){
                return "NPC-"+entry.definitionId;
            }

            @Override public String authority(){
                return CATALOG_AUTHORITY;
            }
        };
    }

    private static MonsterSpawnerService
        configuredService(
            World world
        ){
        MonsterSpawnerService service=
            new MonsterSpawnerService(
                world.npcs()
            );

        List<MonsterSpawnerService.CatalogEntry>
            catalog=
                new ArrayList<>();

        for(int row=0;
            row<MonsterSpawnerService
                .CLIENT_ROW_COUNT;
            row++)
            catalog.add(
                new MonsterSpawnerService
                    .CatalogEntry(
                        row,
                        "monster-ui-row-"+row,
                        1500+row
                    )
            );

        service.replaceCatalog(
            catalog,
            CATALOG_AUTHORITY
        );
        service.openSession(
            OWNER,
            POLICY_AUTHORITY
        );

        return service;
    }

    private static void requireSelectedTextPacket(
        byte[] packet,
        String label
    ){
        require(
            packet.length>=4,
            "selected text packet length"
        );

        int n=packet.length;
        int target=
            ((packet[n-2]&255)<<8)|
            (((packet[n-1]&255)-128)&255);

        require(
            target==
                MonsterSpawnerPresentation
                    .SELECTED_NPC_TEXT_WIDGET,
            "selected text target"
        );

        String raw=
            new String(
                packet,
                StandardCharsets.ISO_8859_1
            );

        require(
            raw.contains(
                "You have selected: @yel@"+
                label
            ),
            "selected text label"
        );
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream out
    ){
        return new ServerPacketWriter(
            out,
            new IsaacCipher(
                new int[4]
            )
        );
    }

    private static ServerPacketWriter failingWriter(){
        return new ServerPacketWriter(
            new OutputStream(){
                @Override public void write(int value)
                    throws IOException{
                    throw new IOException(
                        "EXPECTED_ROW_PACKET_FAILURE"
                    );
                }
            },
            new IsaacCipher(
                new int[4]
            )
        );
    }

    private static void expect(
        Class<? extends Throwable> type,
        ThrowingRunnable action,
        String label
    ){
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(
                    failure))
                return;

            throw new AssertionError(
                label+
                " wrong failure "+
                failure,
                failure
            );
        }

        throw new AssertionError(
            label+
            " did not fail"
        );
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(
                label
            );
    }

    private LocalMonsterSpawnerUiHandlerTest(){}
}
