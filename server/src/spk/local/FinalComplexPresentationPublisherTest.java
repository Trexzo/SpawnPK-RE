package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public final class FinalComplexPresentationPublisherTest {
    private static final int[] SEED={1,2,3,4};

    public static void main(String[] args)throws Exception{
        testEmptySceneBatch();
        testMultiChildSceneBatchWithCanonicalPlayerIdentity();
        testConstructedRegions();
        testCombatPopups();

        if(Modifier.isPublic(SceneBatchPublisher.class.getModifiers())||
           Modifier.isPublic(RegionPresentationPublisher.class.getModifiers())||
           Modifier.isPublic(CombatPopupPublisher.class.getModifiers()))
            throw new AssertionError("complex presentation protocol leaked into public API");

        System.out.println(
            "FINAL_COMPLEX_PRESENTATION_PUBLISHER_PASS "+
            "s2c60Empty=true s2c60MultiChild=true child147=true child215=true "+
            "canonicalPlayerIndex=true s2c241Empty=true s2c241Mixed=true "+
            "s2c255BlockHitProtection=true publicApiLeak=false"
        );
    }

    private static void testEmptySceneBatch()throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        ServerPacketWriter writer=new ServerPacketWriter(
            out,new IsaacCipher(SEED.clone())
        );
        SceneCoordinateContext ctx=new SceneCoordinateContext(
            MovementState.REGION_BASE_X,
            MovementState.REGION_BASE_Y,
            0
        );
        SceneBatchPublisher batches=new SceneBatchPublisher(writer,ctx);
        Tile anchor=new Tile(
            MovementState.REGION_BASE_X+8,
            MovementState.REGION_BASE_Y+16,
            0
        );
        batches.begin(anchor).send();
        assertVarShort(
            "s2c60-empty",
            60,
            bytes(0x10,0xf8),
            out.toByteArray()
        );
        if(ctx.currentChunkX()!=8||ctx.currentChunkY()!=16)
            throw new AssertionError("batch did not publish scene base");
    }

    private static void testMultiChildSceneBatchWithCanonicalPlayerIdentity()
        throws Exception
    {
        World world=World.isolatedForTest(50L);
        WorldPlayer viewer=new WorldPlayer();
        world.registerPlayer(viewer,"viewer");

        ByteArrayOutputStream out=new ByteArrayOutputStream();
        ServerPacketWriter writer=new ServerPacketWriter(
            out,new IsaacCipher(SEED.clone())
        );
        Player81WorldSync.register(
            writer,world,viewer,new DevAuthorityWorkbench()
        );

        try{
            SceneCoordinateContext ctx=new SceneCoordinateContext(
                MovementState.REGION_BASE_X,
                MovementState.REGION_BASE_Y,
                0
            );
            SceneBatchPublisher batches=
                new SceneBatchPublisher(writer,ctx);

            Tile anchor=new Tile(
                MovementState.REGION_BASE_X+8,
                MovementState.REGION_BASE_Y+16,
                0
            );
            GroundItem normal=new GroundItem(
                1L,0x1234,42,
                new Tile(
                    MovementState.REGION_BASE_X+9,
                    MovementState.REGION_BASE_Y+18,
                    0
                ),
                "viewer",0L,false
            );
            GroundItem excluded=new GroundItem(
                2L,0x2222,3,
                new Tile(
                    MovementState.REGION_BASE_X+10,
                    MovementState.REGION_BASE_Y+17,
                    0
                ),
                "viewer",0L,false
            );
            Tile objectTile=new Tile(
                MovementState.REGION_BASE_X+11,
                MovementState.REGION_BASE_Y+20,
                0
            );

            batches.begin(anchor)
                .groundSpawn(normal)
                .objectRemove(objectTile,10,2)
                .groundSpawnExcept(excluded,viewer)
                .attachTemporaryObjectToPlayer(
                    viewer,objectTile,
                    0x1234,10,3,
                    5,9,
                    -2,4,-3,5
                )
                .send();

            byte[] body=bytes(
                0x10,0xf8,
                0x2c,0xb4,0x12,0x00,0x2a,0x12,
                0x65,0xd6,0x34,
                0xd7,0x22,0xa2,0x5f,0x00,0x81,0x00,0x03,
                0x93,0x4c,0x00,0x01,0x82,0x05,0x00,0x03,
                0x00,0x09,0x55,0x04,0x12,0x34,0xfb
            );
            assertVarShort(
                "s2c60-multi",
                60,
                body,
                out.toByteArray()
            );
        }finally{
            Player81WorldSync.unregister(writer);
            world.unregisterPlayer(viewer);
            world.close();
        }
    }

    private static void testConstructedRegions()throws Exception{
        int[][][] empty=absentGrid();

        ByteArrayOutputStream emptyOut=new ByteArrayOutputStream();
        RegionPresentationPublisher emptyPublisher=
            new RegionPresentationPublisher(
                new ServerPacketWriter(
                    emptyOut,new IsaacCipher(SEED.clone())
                )
            );
        emptyPublisher.constructedRegion(0x4567,0x1234,empty);

        byte[] emptyBits=new byte[85];
        byte[] emptyBody=concat(
            bytes(0x12,0xb4),
            emptyBits,
            bytes(0x45,0x67)
        );
        assertVarShort(
            "s2c241-empty",
            241,
            emptyBody,
            emptyOut.toByteArray()
        );

        int[][][] mixed=absentGrid();
        mixed[0][0][0]=0x123456;
        mixed[0][0][1]=0x2abcdef;
        mixed[3][12][12]=0;

        ByteArrayOutputStream mixedOut=new ByteArrayOutputStream();
        RegionPresentationPublisher mixedPublisher=
            new RegionPresentationPublisher(
                new ServerPacketWriter(
                    mixedOut,new IsaacCipher(SEED.clone())
                )
            );
        mixedPublisher.constructedRegion(0x1357,0x2468,mixed);

        assertVarShort(
            "s2c241-mixed",
            241,
            referenceRegionBody(0x1357,0x2468,mixed),
            mixedOut.toByteArray()
        );
    }

    private static void testCombatPopups()throws Exception{
        long eventId=0x0102030405060708L;

        assertPopup(
            "block",
            0,eventId,0,
            bytes(
                0x00,0x00,
                0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,
                0x00,0x00
            )
        );
        assertPopup(
            "melee",
            25,eventId,1,
            bytes(
                0x00,0x19,
                0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,
                0x00,0x01
            )
        );
        assertPopup(
            "magic",
            25,eventId,2,
            bytes(
                0x00,0x19,
                0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,
                0x00,0x02
            )
        );
        assertPopup(
            "range",
            25,eventId,3,
            bytes(
                0x00,0x19,
                0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,
                0x00,0x03
            )
        );
    }

    private static void assertPopup(
        String name,int value,long eventId,int protectionType,byte[] body
    )throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        CombatPopupPublisher publisher=
            new CombatPopupPublisher(
                new ServerPacketWriter(
                    out,new IsaacCipher(SEED.clone())
                )
            );
        publisher.combatPopup(value,eventId,protectionType);
        assertFixed(
            "s2c255-"+name,
            255,
            body,
            out.toByteArray()
        );
    }

    private static int[][][] absentGrid(){
        int[][][] grid=new int[4][13][13];
        for(int p=0;p<4;p++)
            for(int x=0;x<13;x++)
                Arrays.fill(grid[p][x],-1);
        return grid;
    }

    private static byte[] referenceRegionBody(
        int regionX,int regionY,int[][][] grid
    ){
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write((regionY>>>8)&255);
        out.write((regionY+128)&255);

        RefBits bits=new RefBits();
        for(int p=0;p<4;p++){
            for(int x=0;x<13;x++){
                for(int y=0;y<13;y++){
                    int descriptor=grid[p][x][y];
                    if(descriptor<0){
                        bits.write(0,1);
                    }else{
                        bits.write(1,1);
                        bits.write(descriptor,26);
                    }
                }
            }
        }
        byte[] packed=bits.finish();
        out.write(packed,0,packed.length);
        out.write((regionX>>>8)&255);
        out.write(regionX&255);
        return out.toByteArray();
    }

    private static final class RefBits {
        private final ByteArrayOutputStream out=new ByteArrayOutputStream();
        private int current;
        private int used;

        void write(int value,int count){
            for(int i=count-1;i>=0;i--){
                current=(current<<1)|((value>>>i)&1);
                if(++used==8){
                    out.write(current);
                    current=0;
                    used=0;
                }
            }
        }

        byte[] finish(){
            if(used!=0){
                current<<=(8-used);
                out.write(current);
                current=0;
                used=0;
            }
            return out.toByteArray();
        }
    }

    private static void assertFixed(
        String name,int opcode,byte[] body,byte[] actual
    ){
        byte[] expected=new byte[1+body.length];
        expected[0]=(byte)encryptedOpcode(opcode);
        System.arraycopy(body,0,expected,1,body.length);
        assertBytes(name,expected,actual);
    }

    private static void assertVarShort(
        String name,int opcode,byte[] body,byte[] actual
    ){
        byte[] expected=new byte[3+body.length];
        expected[0]=(byte)encryptedOpcode(opcode);
        expected[1]=(byte)(body.length>>>8);
        expected[2]=(byte)body.length;
        System.arraycopy(body,0,expected,3,body.length);
        assertBytes(name,expected,actual);
    }

    private static int encryptedOpcode(int opcode){
        IsaacCipher cipher=new IsaacCipher(SEED.clone());
        return (opcode+cipher.nextInt())&255;
    }

    private static byte[] bytes(int... values){
        byte[] out=new byte[values.length];
        for(int i=0;i<values.length;i++)out[i]=(byte)values[i];
        return out;
    }

    private static byte[] concat(byte[]... parts){
        int len=0;
        for(byte[] part:parts)len+=part.length;
        byte[] out=new byte[len];
        int offset=0;
        for(byte[] part:parts){
            System.arraycopy(part,0,out,offset,part.length);
            offset+=part.length;
        }
        return out;
    }

    private static void assertBytes(
        String name,byte[] expected,byte[] actual
    ){
        if(!Arrays.equals(expected,actual))
            throw new AssertionError(
                name+
                " expected="+Arrays.toString(expected)+
                " actual="+Arrays.toString(actual)
            );
    }
}
