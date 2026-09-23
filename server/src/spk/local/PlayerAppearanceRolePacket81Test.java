package spk.local;

import java.util.Arrays;

public final class PlayerAppearanceRolePacket81Test {
    private static final AtomicTransactionService.SourceAuthority POLICY =
        AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB;

    public static void main(String[] args) throws Exception {
        World world=World.isolatedForTest(GameClock.TICK_MILLIS);
        try {
            world.playerPrivileges().registerDefinition(
                new PlayerPrivilegeService.Definition(
                    "role:test",
                    "Test Role",
                    POLICY
                )
            );
            world.appearanceRoles().register(
                new PlayerAppearanceRoleProjection.Mapping(
                    "role:test",
                    45,
                    POLICY
                )
            );
            world.playerPrivileges().assign(
                "player:alice",
                "role:test"
            );

            require(
                world.appearanceRoleFor("PLAYER:ALICE")==45,
                "world semantic role projection"
            );
            require(
                world.appearanceRoleFor("player:bob")==0,
                "unassigned role default"
            );

            int[] worn=new int[12];
            Arrays.fill(worn,-1);
            worn[EquipmentSlot.HEAD.appearanceIndex]=22131;

            PlayerState normal=new PlayerState();
            byte[] wornBlock=
                BootstrapPackets.appearanceBlock(
                    "player:alice",
                    worn,
                    normal,
                    null,
                    world.appearanceRoleFor("player:alice")
                );

            require(
                signedBe16(wornBlock,5)==45,
                "aC wire role"
            );
            require(
                unsignedBe16(wornBlock,7)==512+22131,
                "worn HEAD appearance"
            );

            int[] noWorn=new int[12];
            Arrays.fill(noWorn,-1);
            PlayerState override=new PlayerState();
            override.cosmetic().set(22131);
            override.syncEquipmentPresentation(
                new EquipmentState()
            );
            byte[] overrideBlock=
                BootstrapPackets.appearanceBlock(
                    "player:alice",
                    noWorn,
                    override,
                    null,
                    world.appearanceRoleFor("player:alice")
                );

            require(
                signedBe16(overrideBlock,5)==45,
                "override aC role"
            );
            require(
                unsignedBe16(overrideBlock,7)!=512+22131,
                "Override must not occupy HEAD"
            );
            require(
                containsBe16AfterAppearanceSlots(
                    overrideBlock,
                    22131
                ),
                "Override cosmetic bs item missing"
            );

            byte[] legacy=
                BootstrapPackets.appearanceBlock(
                    "player:alice",
                    worn,
                    normal
                );
            require(
                signedBe16(legacy,5)==0,
                "legacy overload must remain role zero"
            );

            expect(
                IllegalArgumentException.class,
                ()->{
                    try {
                        BootstrapPackets.appearanceBlock(
                            "player:alice",
                            worn,
                            normal,
                            null,
                            32768
                        );
                    } catch (java.io.IOException error) {
                        throw new RuntimeException(error);
                    }
                },
                "positive role outside signed short"
            );

            System.out.println(
                "PLAYER_APPEARANCE_ROLE_PACKET81_PASS "+
                "semanticWorldProjection=true "+
                "signedBe16Ac=true "+
                "wornHeadGatePreserved=true "+
                "overrideBsSeparate=true "+
                "legacyZeroDefault=true "+
                "ctDerived=false "+
                "namedSpawnPkMappingInvented=false"
            );
        } finally {
            world.close();
        }
    }

    private static int signedBe16(byte[] data,int offset){
        return (short)unsignedBe16(data,offset);
    }

    private static int unsignedBe16(byte[] data,int offset){
        return ((data[offset]&255)<<8)|
            (data[offset+1]&255);
    }

    private static boolean containsBe16AfterAppearanceSlots(
        byte[] data,
        int value
    ){
        byte hi=(byte)((value>>>8)&255);
        byte lo=(byte)(value&255);
        for(int i=9;i+1<data.length;i++)
            if(data[i]==hi&&data[i+1]==lo)
                return true;
        return false;
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
        String label
    ){
        try {
            action.run();
        } catch (Throwable failure) {
            if(type.isInstance(failure))
                return;
            throw new AssertionError(
                label+" wrong failure "+failure,
                failure
            );
        }
        throw new AssertionError(label+" did not fail");
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private PlayerAppearanceRolePacket81Test(){}
}
