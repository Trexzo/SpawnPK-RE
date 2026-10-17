package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public final class G1RocktailConsumableHandlerTest {
    private static final int[] SEED={31,32,33,34};

    public static void main(String[] args)throws Exception{
        WorldPlayer player=new WorldPlayer();
        BankState bank=player.bank();

        require(
            "Eat".equalsIgnoreCase(
                ItemActionResolver.inventoryAction(
                    G1RocktailConsumableHandler.ITEM_ID,
                    0
                )
            ),
            "Rocktail option-1 no longer resolves Eat"
        );
        require(
            !ItemDefinitionRepository.isStackable(
                G1RocktailConsumableHandler.ITEM_ID
            ),
            "Rocktail unexpectedly stackable"
        );

        BankState.PreparedInventoryMutation starter=
            bank.prepareAddInventoryAmount(
                G1RocktailConsumableHandler.ITEM_ID,
                4
            );
        require(
            starter.accepted(),
            "Rocktail fixture rejected"
        );
        bank.commitPreparedInventoryMutation(
            starter
        );

        player.playerState().setCurrentLevel(
            PlayerState.HITPOINTS,
            50
        );

        G1RocktailConsumableHandler handler=
            new G1RocktailConsumableHandler(
                player,
                bank
            );

        PacketCapture first=
            new PacketCapture();

        G1RocktailConsumableHandler.Result r1=
            handler.handle(
                action(0),
                10L,
                first.writer
            );

        require(
            r1!=null&&
            r1.consumed()&&
            r1.beforeHitpoints==50&&
            r1.afterHitpoints==60&&
            r1.maximumHitpoints==
                new CombatSkillProgressionService(
                    player,
                    new LocalLabCombatXpCurve()
                ).snapshot(
                    CombatSkillProgressionService
                        .Skill.HITPOINTS
                ).baseLevel&&
            r1.maximumHitpoints>r1.afterHitpoints,
            "first eat result incorrect "+
            r1
        );
        require(
            !bank.inventorySlotSnapshot(0).occupied,
            "first Rocktail not consumed"
        );
        require(
            player.playerState().currentLevel(
                PlayerState.HITPOINTS
            )==60,
            "first heal not canonical"
        );
        first.requireOpcodes(
            53,
            134
        );

        PacketCapture cadence=
            new PacketCapture();

        G1RocktailConsumableHandler.Result blocked=
            handler.handle(
                action(1),
                11L,
                cadence.writer
            );

        require(
            blocked.status==
                G1RocktailConsumableHandler
                    .Status.CADENCE_BLOCKED&&
            bank.inventorySlotSnapshot(1).occupied&&
            player.playerState().currentLevel(
                PlayerState.HITPOINTS
            )==60&&
            cadence.bytes().length==0,
            "cadence consumed or published "+
            blocked
        );

        PacketCapture second=
            new PacketCapture();

        G1RocktailConsumableHandler.Result r2=
            handler.handle(
                action(1),
                13L,
                second.writer
            );

        require(
            r2.consumed()&&
            r2.afterHitpoints==70&&
            !bank.inventorySlotSnapshot(1).occupied,
            "later eligible eat failed "+
            r2
        );
        second.requireOpcodes(
            53,
            134
        );

        player.playerState().setCurrentLevel(
            PlayerState.HITPOINTS,
            r2.maximumHitpoints
        );

        PacketCapture full=
            new PacketCapture();

        G1RocktailConsumableHandler.Result fullResult=
            handler.handle(
                action(2),
                16L,
                full.writer
            );

        require(
            fullResult.status==
                G1RocktailConsumableHandler
                    .Status.FULL_HITPOINTS&&
            bank.inventorySlotSnapshot(2).occupied&&
            full.bytes().length==0,
            "full HP consumed Rocktail "+
            fullResult
        );

        player.playerState().setCurrentLevel(
            PlayerState.HITPOINTS,
            40
        );

        PacketCapture stale=
            new PacketCapture();

        G1RocktailConsumableHandler.Result staleResult=
            handler.handle(
                action(27),
                16L,
                stale.writer
            );

        require(
            staleResult.status==
                G1RocktailConsumableHandler
                    .Status.STALE_SLOT&&
            stale.bytes().length==0,
            "stale slot not rejected "+
            staleResult
        );

        require(
            handler.handle(
                new ItemContainerAction(
                    122,
                    BankState.NORMAL_INVENTORY_CONTAINER,
                    2,
                    4151,
                    0,
                    ""
                ),
                16L,
                new PacketCapture().writer
            )==null,
            "unrelated item action did not fall through"
        );

        int hpBeforeFailure=
            player.playerState().currentLevel(
                PlayerState.HITPOINTS
            );

        boolean transportFailed=false;
        try{
            handler.handle(
                action(2),
                16L,
                new ServerPacketWriter(
                    new OutputStream(){
                        @Override public void write(
                            int value
                        )throws IOException{
                            throw new IOException(
                                "fixture transport failure"
                            );
                        }

                        @Override public void write(
                            byte[] value,
                            int offset,
                            int length
                        )throws IOException{
                            throw new IOException(
                                "fixture transport failure"
                            );
                        }
                    },
                    new IsaacCipher(
                        SEED.clone()
                    )
                )
            );
        }catch(IOException expected){
            transportFailed=true;
        }

        require(
            transportFailed&&
            bank.inventorySlotSnapshot(2).occupied&&
            player.playerState().currentLevel(
                PlayerState.HITPOINTS
            )==hpBeforeFailure&&
            handler.nextEligibleTick()==16L,
            "transport failure mutated gameplay state"
        );

        PacketCapture retry=
            new PacketCapture();

        G1RocktailConsumableHandler.Result retryResult=
            handler.handle(
                action(2),
                16L,
                retry.writer
            );

        require(
            retryResult.consumed()&&
            !bank.inventorySlotSnapshot(2).occupied&&
            player.playerState().currentLevel(
                PlayerState.HITPOINTS
            )==50,
            "healthy retry did not consume exactly once "+
            retryResult
        );
        retry.requireOpcodes(
            53,
            134
        );

        player.lifecycle().markDead(
            20L,
            5L,
            "TEST"
        );

        PacketCapture dead=
            new PacketCapture();

        G1RocktailConsumableHandler.Result deadResult=
            handler.handle(
                action(3),
                20L,
                dead.writer
            );

        require(
            deadResult.status==
                G1RocktailConsumableHandler
                    .Status.DEAD&&
            bank.inventorySlotSnapshot(3).occupied&&
            dead.bytes().length==0,
            "dead player consumed/healed "+
            deadResult
        );

        require(
            new LocalLabCombatXpCurve()
                .minimumXpForLevel(
                    new LocalLabCombatXpCurve()
                        .maxLevel()
                )==
                PlayerState.XP_99,
            "LocalLab XP curve not fenced to XP_99"
        );

        System.out.println(
            "G1_ROCKTAIL_CONSUMABLE_PASS "+
            "eatRouting=true "+
            "exactSlotConsumeOne=true "+
            "xpDerivedBaseHp=true "+
            "healClamp=true "+
            "fullHpNoConsume=true "+
            "deadNoConsume=true "+
            "staleSlotNoConsume=true "+
            "cadenceBlocksRapid=true "+
            "laterEatSucceeds=true "+
            "packet53=true "+
            "packet134=true "+
            "transportFailureAtomic=true "+
            "retryExactlyOnce=true "+
            "unrelatedFallthrough=true "+
            "healAmount="+
            G1RocktailConsumableHandler.HEAL_AMOUNT+" "+
            "cooldownTicks="+
            G1RocktailConsumableHandler.EAT_COOLDOWN_TICKS+" "+
            "authority="+
            G1RocktailConsumableHandler.AUTHORITY
        );
    }

    private static ItemContainerAction action(
        int slot
    ){
        return new ItemContainerAction(
            122,
            BankState.NORMAL_INVENTORY_CONTAINER,
            slot,
            G1RocktailConsumableHandler.ITEM_ID,
            0,
            ""
        );
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(
                message
            );
    }

    private static final class PacketCapture {
        final OutboundPacketQueue queue=
            new OutboundPacketQueue(
                1<<20
            );
        final ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                new IsaacCipher(
                    SEED.clone()
                )
            );

        byte[] bytes()throws Exception{
            ByteArrayOutputStream out=
                new ByteArrayOutputStream();
            queue.drainTo(
                out,
                1<<20
            );
            return out.toByteArray();
        }

        void requireOpcodes(
            int first,
            int second
        )throws Exception{
            byte[] data=bytes();
            IsaacCipher decode=
                new IsaacCipher(
                    SEED.clone()
                );
            int offset=0;

            int opcode1=
                ((data[offset++]&255)-
                    decode.nextInt())&255;
            require(
                opcode1==first,
                "first opcode="+opcode1+
                " expected="+first
            );

            int len=
                ((data[offset++]&255)<<8)|
                (data[offset++]&255);
            offset+=len;

            int opcode2=
                ((data[offset++]&255)-
                    decode.nextInt())&255;
            require(
                opcode2==second,
                "second opcode="+opcode2+
                " expected="+second
            );

            offset+=6;

            require(
                offset==data.length,
                "unexpected trailing packet bytes offset="+
                offset+
                " length="+
                data.length
            );
        }
    }

    private G1RocktailConsumableHandlerTest(){}
}
