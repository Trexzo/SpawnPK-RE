package spk.local;

import java.util.*;

public final class EngineR3PlayerSyncTest {
    public static void main(String[] args)throws Exception{
        World w=World.isolatedForTest(600L);
        WorldPlayer a=new WorldPlayer(),b=new WorldPlayer();
        w.registerPlayer(a,"opensrc");w.registerPlayer(b,"src");
        ServerPacketWriter wa=new ServerPacketWriter(new OutboundPacketQueue(),new IsaacCipher(new int[]{1,2,3,4}));
        ServerPacketWriter wb=new ServerPacketWriter(new OutboundPacketQueue(),new IsaacCipher(new int[]{5,6,7,8}));
        Player81WorldSync.Context ca=Player81WorldSync.register(wa,w,a,new DevAuthorityWorkbench());
        Player81WorldSync.Context cb=Player81WorldSync.register(wb,w,b,new DevAuthorityWorkbench());
        try{
            byte[] first=Player81WorldSync.transformForTest(ca,BootstrapPackets.player81Idle());
            Bits r=new Bits(first);eq(0,r.read(1),"local idle");eq(0,r.read(8),"old remote count");
            int bIndex=r.read(11);ok(bIndex>1&&bIndex<2047,"remote index");eq(1,r.read(1),"new mask");eq(1,r.read(1),"new clear");
            eq(0,signed5(r.read(5)),"relY");eq(0,signed5(r.read(5)),"relX");eq(2047,r.read(11),"sentinel");r.align();
            eq(0x10,first[r.bytePos()]&255,"initial appearance mask");
            eq(bIndex,ca.clientIndexFor(b),"context index");

            // Let B know A before target-translation checks.
            Player81WorldSync.transformForTest(cb,BootstrapPackets.player81Idle());

            int fromX=b.movement().x(),fromY=b.movement().y();
            String accepted=b.movement().accept(new MovementRequest(164,false,new int[]{fromX+1},new int[]{fromY},new byte[0]));
            ok(accepted.startsWith("ACCEPTED"),"movement accepted: "+accepted);
            MovementState.Tick tick=b.movement().advance();ok(tick!=null,"movement tick");
            Player81WorldSync.transformForTest(cb,BootstrapPackets.player81WalkStep(tick.dir1));
            byte[] moved=Player81WorldSync.transformForTest(ca,BootstrapPackets.player81Idle());
            r=new Bits(moved);eq(0,r.read(1),"viewer idle");eq(1,r.read(8),"old count 1");eq(1,r.read(1),"remote changed");eq(1,r.read(2),"remote walk");eq(tick.dir1,r.read(3),"exact relayed direction");eq(0,r.read(1),"no mask");eq(2047,r.read(11),"move sentinel");

            byte[] appearanceLocal=BootstrapPackets.player81AppearanceOnly("src",b.equipment().appearanceItems(),b.playerState());
            Player81WorldSync.transformForTest(cb,appearanceLocal);
            byte[] app=Player81WorldSync.transformForTest(ca,BootstrapPackets.player81Idle());
            r=new Bits(app);eq(0,r.read(1),"app viewer idle");eq(1,r.read(8),"app old count");eq(1,r.read(1),"app changed");eq(0,r.read(2),"mask-only type");eq(2047,r.read(11),"app sentinel");r.align();eq(0x10,app[r.bytePos()]&255,"appearance relay mask");

            Player81WorldSync.transformForTest(cb,CombatSync.player81AnimationAndGfx(827,1310,0,0));
            byte[] fx=Player81WorldSync.transformForTest(ca,BootstrapPackets.player81Idle());
            r=new Bits(fx);r.read(1);eq(1,r.read(8),"fx old count");eq(1,r.read(1),"fx changed");eq(0,r.read(2),"fx mask-only");eq(2047,r.read(11),"fx sentinel");r.align();
            int mask=maskAt(fx,r.bytePos());ok((mask&0x8)!=0&&(mask&0x100)!=0,"animation+gfx mask 0x"+Integer.toHexString(mask));

            // A targets B using A's remote index. When relayed to B, target must translate to B's local index (1).
            Player81WorldSync.transformForTest(ca,CombatSync.player81InteractionOnly(32768+bIndex));
            byte[] targeted=Player81WorldSync.transformForTest(cb,BootstrapPackets.player81Idle());
            r=new Bits(targeted);r.read(1);eq(1,r.read(8),"target old count");eq(1,r.read(1),"target changed");eq(0,r.read(2),"target mask-only");eq(2047,r.read(11),"target sentinel");r.align();
            int p=r.bytePos();eq(0x1,targeted[p]&255,"interaction mask");int translated=(targeted[p+1]&255)|((targeted[p+2]&255)<<8);eq(32768+NpcRegistry.LOCAL_PLAYER_INDEX,translated,"viewer-local target translation");

            w.unregisterPlayer(b);
            byte[] removed=Player81WorldSync.transformForTest(ca,BootstrapPackets.player81Idle());
            r=new Bits(removed);r.read(1);eq(1,r.read(8),"remove old count");eq(1,r.read(1),"remove changed");eq(3,r.read(2),"remove type");eq(2047,r.read(11),"remove sentinel");
            System.out.println("V5130_ENGINE_R3_PLAYER_SYNC_PASS initialAdd=true movementExactDir=true appearance=true animationGfx=true targetTranslation=true logoutRemoval=true");
        }finally{
            Player81WorldSync.unregister(wa);Player81WorldSync.unregister(wb);try{w.unregisterPlayer(a);}catch(Throwable ignored){}try{w.unregisterPlayer(b);}catch(Throwable ignored){}w.close();
        }
    }
    static int maskAt(byte[] b,int p){int low=b[p]&255,m=low;if((low&0x40)!=0)m|=(b[p+1]&255)<<8;return m;}
    static int signed5(int v){return v>15?v-32:v;}
    static void ok(boolean v,String m){if(!v)throw new AssertionError(m);}
    static void eq(int e,int a,String m){if(e!=a)throw new AssertionError(m+" expected="+e+" actual="+a);}
    static final class Bits{final byte[] b;int bit;Bits(byte[]b){this.b=b;}int read(int n){int v=0;for(int i=0;i<n;i++){v=(v<<1)|((b[bit>>>3]>>(7-(bit&7)))&1);bit++;}return v;}void align(){bit=(bit+7)&~7;}int bytePos(){return bit>>>3;}}
}
