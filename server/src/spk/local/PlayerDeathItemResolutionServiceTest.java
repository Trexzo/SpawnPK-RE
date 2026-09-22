package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class PlayerDeathItemResolutionServiceTest {
    private static final String AUTHORITY=
        "CUSTOM_LOCALLAB_DEATH_ITEMS_TEST";

    public static void main(String[] args){
        exactDispositionAndReplay();
        deadRequired();
        fullCoverageRequired();
        invalidKeepRejected();
        policyFailureAtomic();
        stateDriftRejected();
        emptyCarriedAllowed();
        authorityGuards();
        boundaryGuard();

        System.out.println(
            "PLAYER_DEATH_ITEM_RESOLUTION_PASS "+
            "deadRequired=true "+
            "exactDeathTick=true "+\n            "deathSequenceIdentity=true "+
            "inventoryAndEquipment=true "+
            "exactSourceLines=true "+
            "callerPolicy=true "+
            "fullCoverageRequired=true "+
            "partialStackKeep=true "+
            "lostDerived=true "+
            "replayIdempotent=true "+
            "policyFailureAtomic=true "+
            "stateDriftRejected=true "+
            "itemValueOwned=false "+
            "protectItemOwned=false "+
            "lootRecipientOwned=false "+
            "itemMutation=false "+
            "groundMutation=false "+
            "protocolIndependent=true"
        );
    }

    private static void exactDispositionAndReplay(){
        WorldPlayer player=
            configuredPlayer();

        kill(
            player,
            77L,
            "pvp-test"
        );

        final int[] calls={0};

        PlayerDeathItemResolutionService service=
            new PlayerDeathItemResolutionService(
                player,
                new PlayerDeathItemResolutionService
                    .DispositionPolicy(){
                    @Override public List<
                        PlayerDeathItemResolutionService.Decision
                    > resolve(
                        PlayerDeathItemResolutionService
                            .DeathContext context
                    ){
                        calls[0]++;

                        require(
                            context.playerId.equals(
                                player.id()
                            )&&
                            context.deathTick==77L&&\n                            context.deathSequence==1L&&
                            "pvp-test".equals(
                                context.deathCause
                            ),
                            "death context identity"
                        );

                        require(
                            context.carried.size()==4,
                            "carried line count "+
                            context.carried.size()
                        );

                        PlayerDeathItemResolutionService
                            .CarriedLine coins=
                                findItem(
                                    context.carried,
                                    995
                                );
                        PlayerDeathItemResolutionService
                            .CarriedLine food=
                                findItem(
                                    context.carried,
                                    15272
                                );
                        PlayerDeathItemResolutionService
                            .CarriedLine weapon=
                                findItem(
                                    context.carried,
                                    4151
                                );
                        PlayerDeathItemResolutionService
                            .CarriedLine ammo=
                                findItem(
                                    context.carried,
                                    892
                                );

                        require(
                            coins.source==
                                PlayerDeathItemResolutionService
                                    .Source.INVENTORY&&
                            coins.sourceIndex==0&&
                            coins.quantity==100,
                            "coin source"
                        );

                        require(
                            food.source==
                                PlayerDeathItemResolutionService
                                    .Source.INVENTORY&&
                            food.sourceIndex==1&&
                            food.quantity==2,
                            "food source"
                        );

                        require(
                            weapon.source==
                                PlayerDeathItemResolutionService
                                    .Source.EQUIPMENT&&
                            weapon.equipmentSlot==
                                EquipmentSlot.WEAPON&&
                            weapon.quantity==1,
                            "weapon source"
                        );

                        require(
                            ammo.source==
                                PlayerDeathItemResolutionService
                                    .Source.EQUIPMENT&&
                            ammo.equipmentSlot==
                                EquipmentSlot.AMMO&&
                            ammo.quantity==50,
                            "ammo source"
                        );

                        return Arrays.asList(
                            new PlayerDeathItemResolutionService
                                .Decision(
                                    coins.lineId,
                                    30
                                ),
                            new PlayerDeathItemResolutionService
                                .Decision(
                                    food.lineId,
                                    0
                                ),
                            new PlayerDeathItemResolutionService
                                .Decision(
                                    weapon.lineId,
                                    1
                                ),
                            new PlayerDeathItemResolutionService
                                .Decision(
                                    ammo.lineId,
                                    10
                                )
                        );
                    }

                    @Override public String authority(){
                        return AUTHORITY;
                    }
                }
            );

        PlayerDeathItemResolutionService.Resolution first=
            service.resolveCurrentDeath();

        require(
            calls[0]==1&&
            first.playerId.equals(
                player.id()
            )&&
            first.deathTick==77L&&\n            first.deathSequence==1L&&
            first.dispositions.size()==4&&
            AUTHORITY.equals(
                first.policyAuthority
            )&&
            AUTHORITY.equals(
                service.policyAuthority()
            ),
            "first death item resolution"
        );

        PlayerDeathItemResolutionService.Disposition
            coins=
                dispositionFor(
                    first,
                    995
                );

        require(
            coins.keptAmount==30&&
            coins.lostAmount==70,
            "partial stack disposition"
        );

        require(
            dispositionFor(
                first,
                15272
            ).lostAmount==2&&
            dispositionFor(
                first,
                4151
            ).lostAmount==0&&
            dispositionFor(
                first,
                892
            ).lostAmount==40,
            "derived lost quantities"
        );

        require(
            first.keptTotalQuantity()==41&&
            first.lostTotalQuantity()==112,
            "disposition totals"
        );

        PlayerDeathItemResolutionService.Resolution replay=
            service.resolveCurrentDeath();

        require(
            replay==first&&
            calls[0]==1&&
            service.size()==1&&
            service.get(1L)==first,
            "death replay idempotency"
        );

        require(
            player.bank()
                .inventoryAt(0)
                .qty==100&&
            player.bank()
                .inventoryAt(1)
                .qty==2&&
            player.equipment()
                .itemAt(
                    EquipmentSlot.WEAPON
                )==4151&&
            player.equipment()
                .quantityAt(
                    EquipmentSlot.AMMO
                )==50,
            "resolution mutated carried items"
        );

        expectUnsupported(
            ()->first.dispositions.clear(),
            "resolution dispositions mutable"
        );

        expectUnsupported(
            ()->service.snapshot().clear(),
            "resolution snapshot mutable"
        );
    }

    private static void deadRequired(){
        WorldPlayer player=
            configuredPlayer();

        final int[] calls={0};

        PlayerDeathItemResolutionService service=
            new PlayerDeathItemResolutionService(
                player,
                policyKeepAll(
                    calls
                )
            );

        expect(
            IllegalStateException.class,
            service::resolveCurrentDeath,
            "alive player death resolution"
        );

        require(
            calls[0]==0&&
            service.size()==0,
            "alive rejection invoked policy"
        );
    }

    private static void fullCoverageRequired(){
        WorldPlayer player=
            configuredPlayer();

        kill(
            player,
            80L,
            "coverage"
        );

        PlayerDeathItemResolutionService service=
            new PlayerDeathItemResolutionService(
                player,
                new PlayerDeathItemResolutionService
                    .DispositionPolicy(){
                    @Override public List<
                        PlayerDeathItemResolutionService.Decision
                    > resolve(
                        PlayerDeathItemResolutionService
                            .DeathContext context
                    ){
                        return Collections.singletonList(
                            new PlayerDeathItemResolutionService
                                .Decision(
                                    context.carried
                                        .get(0)
                                        .lineId,
                                    0
                                )
                        );
                    }

                    @Override public String authority(){
                        return AUTHORITY;
                    }
                }
            );

        expect(
            IllegalArgumentException.class,
            service::resolveCurrentDeath,
            "partial coverage"
        );

        require(
            service.size()==0,
            "partial coverage cached"
        );

        PlayerDeathItemResolutionService duplicate=
            new PlayerDeathItemResolutionService(
                player,
                new PlayerDeathItemResolutionService
                    .DispositionPolicy(){
                    @Override public List<
                        PlayerDeathItemResolutionService.Decision
                    > resolve(
                        PlayerDeathItemResolutionService
                            .DeathContext context
                    ){
                        ArrayList<
                            PlayerDeathItemResolutionService.Decision
                        > out=
                            new ArrayList<>();

                        for(PlayerDeathItemResolutionService
                                .CarriedLine line:
                                context.carried)
                            out.add(
                                new PlayerDeathItemResolutionService
                                    .Decision(
                                        line.lineId,
                                        0
                                    )
                            );

                        out.set(
                            out.size()-1,
                            new PlayerDeathItemResolutionService
                                .Decision(
                                    context.carried
                                        .get(0)
                                        .lineId,
                                    0
                                )
                        );

                        return out;
                    }

                    @Override public String authority(){
                        return AUTHORITY;
                    }
                }
            );

        expect(
            IllegalArgumentException.class,
            duplicate::resolveCurrentDeath,
            "duplicate line coverage"
        );
    }

    private static void invalidKeepRejected(){
        WorldPlayer player=
            configuredPlayer();

        kill(
            player,
            81L,
            "invalid-keep"
        );

        PlayerDeathItemResolutionService service=
            new PlayerDeathItemResolutionService(
                player,
                new PlayerDeathItemResolutionService
                    .DispositionPolicy(){
                    @Override public List<
                        PlayerDeathItemResolutionService.Decision
                    > resolve(
                        PlayerDeathItemResolutionService
                            .DeathContext context
                    ){
                        ArrayList<
                            PlayerDeathItemResolutionService.Decision
                        > out=
                            new ArrayList<>();

                        for(PlayerDeathItemResolutionService
                                .CarriedLine line:
                                context.carried)
                            out.add(
                                new PlayerDeathItemResolutionService
                                    .Decision(
                                        line.lineId,
                                        line.lineId==
                                            context.carried
                                                .get(0)
                                                .lineId
                                            ?line.quantity+1
                                            :0
                                    )
                            );

                        return out;
                    }

                    @Override public String authority(){
                        return AUTHORITY;
                    }
                }
            );

        expect(
            IllegalArgumentException.class,
            service::resolveCurrentDeath,
            "kept quantity overflow"
        );

        require(
            service.size()==0,
            "invalid keep cached"
        );
    }

    private static void policyFailureAtomic(){
        WorldPlayer player=
            configuredPlayer();

        kill(
            player,
            82L,
            "policy-fail"
        );

        PlayerDeathItemResolutionService service=
            new PlayerDeathItemResolutionService(
                player,
                new PlayerDeathItemResolutionService
                    .DispositionPolicy(){
                    @Override public List<
                        PlayerDeathItemResolutionService.Decision
                    > resolve(
                        PlayerDeathItemResolutionService
                            .DeathContext context
                    ){
                        throw new IllegalStateException(
                            "policy boom"
                        );
                    }

                    @Override public String authority(){
                        return AUTHORITY;
                    }
                }
            );

        expect(
            IllegalStateException.class,
            service::resolveCurrentDeath,
            "policy failure"
        );

        require(
            service.size()==0&&
            player.bank()
                .inventoryAt(0)
                .qty==100&&
            player.equipment()
                .itemAt(
                    EquipmentSlot.WEAPON
                )==4151,
            "policy failure mutated/cached"
        );
    }

    private static void stateDriftRejected(){
        WorldPlayer player=
            configuredPlayer();

        kill(
            player,
            83L,
            "drift"
        );

        PlayerDeathItemResolutionService service=
            new PlayerDeathItemResolutionService(
                player,
                new PlayerDeathItemResolutionService
                    .DispositionPolicy(){
                    @Override public List<
                        PlayerDeathItemResolutionService.Decision
                    > resolve(
                        PlayerDeathItemResolutionService
                            .DeathContext context
                    ){
                        player.equipment()
                            .setStack(
                                EquipmentSlot.WEAPON,
                                4151,
                                2
                            );

                        ArrayList<
                            PlayerDeathItemResolutionService.Decision
                        > out=
                            new ArrayList<>();

                        for(PlayerDeathItemResolutionService
                                .CarriedLine line:
                                context.carried)
                            out.add(
                                new PlayerDeathItemResolutionService
                                    .Decision(
                                        line.lineId,
                                        0
                                    )
                            );

                        return out;
                    }

                    @Override public String authority(){
                        return AUTHORITY;
                    }
                }
            );

        expect(
            IllegalStateException.class,
            service::resolveCurrentDeath,
            "carried state drift"
        );

        require(
            service.size()==0,
            "drift cached resolution"
        );
    }

    private static void emptyCarriedAllowed(){
        WorldPlayer player=
            new WorldPlayer();

        clearCarried(
            player
        );

        kill(
            player,
            84L,
            "empty"
        );

        final int[] calls={0};

        PlayerDeathItemResolutionService service=
            new PlayerDeathItemResolutionService(
                player,
                new PlayerDeathItemResolutionService
                    .DispositionPolicy(){
                    @Override public List<
                        PlayerDeathItemResolutionService.Decision
                    > resolve(
                        PlayerDeathItemResolutionService
                            .DeathContext context
                    ){
                        calls[0]++;

                        require(
                            context.carried.isEmpty(),
                            "empty carried context"
                        );

                        return Collections.emptyList();
                    }

                    @Override public String authority(){
                        return AUTHORITY;
                    }
                }
            );

        PlayerDeathItemResolutionService.Resolution result=
            service.resolveCurrentDeath();

        require(
            calls[0]==1&&
            result.dispositions.isEmpty()&&
            result.keptTotalQuantity()==0&&
            result.lostTotalQuantity()==0,
            "empty carried resolution"
        );
    }

    private static void authorityGuards(){
        WorldPlayer player=
            new WorldPlayer();

        expect(
            IllegalArgumentException.class,
            ()->new PlayerDeathItemResolutionService(
                player,
                policyKeepAll(
                    new int[]{0},
                    "EXACT_CURRENT_CLIENT"
                )
            ),
            "client death policy authority"
        );

        expect(
            IllegalArgumentException.class,
            ()->new PlayerDeathItemResolutionService(
                player,
                policyKeepAll(
                    new int[]{0},
                    "UNKNOWN_SERVER_AUTHORITY"
                )
            ),
            "unknown death policy authority"
        );
    }

    private static WorldPlayer configuredPlayer(){
        WorldPlayer player=
            new WorldPlayer();

        BankState.Stack[] bank=
            new BankState.Stack[
                BankState.BANK_CAPACITY
            ];
        BankState.Stack[] inventory=
            new BankState.Stack[
                BankState.INVENTORY_CAPACITY
            ];

        inventory[0]=
            new BankState.Stack(
                995,
                100
            );
        inventory[1]=
            new BankState.Stack(
                15272,
                2
            );

        player.bank().restoreAccountState(
            bank,
            inventory,
            false
        );

        int[] items=
            new int[
                EquipmentState.EQUIPMENT_SLOTS
            ];
        int[] quantities=
            new int[
                EquipmentState.EQUIPMENT_SLOTS
            ];

        Arrays.fill(
            items,
            -1
        );

        items[
            EquipmentSlot.WEAPON.equipmentIndex
        ]=4151;
        quantities[
            EquipmentSlot.WEAPON.equipmentIndex
        ]=1;

        items[
            EquipmentSlot.AMMO.equipmentIndex
        ]=892;
        quantities[
            EquipmentSlot.AMMO.equipmentIndex
        ]=50;

        player.equipment()
            .restoreAccountState(
                items,
                quantities
            );

        return player;
    }

    private static void clearCarried(
        WorldPlayer player
    ){
        player.bank().restoreAccountState(
            new BankState.Stack[
                BankState.BANK_CAPACITY
            ],
            new BankState.Stack[
                BankState.INVENTORY_CAPACITY
            ],
            false
        );

        int[] items=
            new int[
                EquipmentState.EQUIPMENT_SLOTS
            ];
        int[] quantities=
            new int[
                EquipmentState.EQUIPMENT_SLOTS
            ];

        Arrays.fill(
            items,
            -1
        );

        player.equipment()
            .restoreAccountState(
                items,
                quantities
            );
    }

    private static void kill(
        WorldPlayer player,
        long tick,
        String cause
    ){
        PlayerLifecycleService.DamageResult result=
            new PlayerLifecycleService(
                player
            ).applyDamage(
                500,
                tick,
                cause
            );

        require(
            result.died&&
            player.lifecycle().dead(),
            "death fixture"
        );
    }

    private static PlayerDeathItemResolutionService
        .CarriedLine findItem(
            List<PlayerDeathItemResolutionService.CarriedLine>
                lines,
            int itemId
        ){
        PlayerDeathItemResolutionService.CarriedLine found=null;

        for(PlayerDeathItemResolutionService
                .CarriedLine line:lines)
            if(line.itemId==itemId){
                if(found!=null)
                    throw new AssertionError(
                        "duplicate fixture item "+
                        itemId
                    );
                found=line;
            }

        if(found==null)
            throw new AssertionError(
                "missing fixture item "+
                itemId
            );

        return found;
    }

    private static PlayerDeathItemResolutionService
        .Disposition dispositionFor(
            PlayerDeathItemResolutionService.Resolution
                resolution,
            int itemId
        ){
        for(PlayerDeathItemResolutionService
                .Disposition disposition:
                resolution.dispositions)
            if(disposition.line.itemId==
                    itemId)
                return disposition;

        throw new AssertionError(
            "missing disposition item "+
            itemId
        );
    }

    private static PlayerDeathItemResolutionService
        .DispositionPolicy policyKeepAll(
            int[] calls
        ){
        return policyKeepAll(
            calls,
            AUTHORITY
        );
    }

    private static PlayerDeathItemResolutionService
        .DispositionPolicy policyKeepAll(
            int[] calls,
            String authority
        ){
        return new PlayerDeathItemResolutionService
            .DispositionPolicy(){
            @Override public List<
                PlayerDeathItemResolutionService.Decision
            > resolve(
                PlayerDeathItemResolutionService
                    .DeathContext context
            ){
                calls[0]++;

                ArrayList<
                    PlayerDeathItemResolutionService.Decision
                > out=
                    new ArrayList<>();

                for(PlayerDeathItemResolutionService
                        .CarriedLine line:
                        context.carried)
                    out.add(
                        new PlayerDeathItemResolutionService
                            .Decision(
                                line.lineId,
                                line.quantity
                            )
                    );

                return out;
            }

            @Override public String authority(){
                return authority;
            }
        };
    }

    private static void boundaryGuard(){
        for(Class<?> type:new Class<?>[]{
                PlayerDeathItemResolutionService.class,
                PlayerDeathItemResolutionService.CarriedLine.class,
                PlayerDeathItemResolutionService.DeathContext.class,
                PlayerDeathItemResolutionService.Resolution.class,
                PlayerDeathItemResolutionService.Disposition.class
        }){
            for(Field field:
                    type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                for(String forbidden:
                        new String[]{
                            "widget",
                            "opcode",
                            "packet",
                            "sceneindex",
                            "itemvalue",
                            "protectitem",
                            "killer",
                            "recipient",
                            "grounditem"
                        })
                    require(
                        !name.contains(
                            forbidden
                        ),
                        "unowned identity leaked through "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }

        for(Method method:
                PlayerDeathItemResolutionService.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            for(String forbidden:
                    new String[]{
                        "packet",
                        "publish",
                        "removeitem",
                        "spawn",
                        "ground",
                        "killer",
                        "recipient",
                        "persist"
                    })
                require(
                    !name.contains(
                        forbidden
                    ),
                    "unowned behavior leaked through "+
                    method.getName()
                );
        }
    }

    private static void expectUnsupported(
        Runnable action,
        String label
    ){
        try{
            action.run();
            throw new AssertionError(
                "Expected immutable view: "+
                label
            );
        }catch(
            UnsupportedOperationException expected
        ){
            // expected
        }
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
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

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private PlayerDeathItemResolutionServiceTest(){}
}
