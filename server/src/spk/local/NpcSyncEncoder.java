package spk.local;

import java.io.*;
import java.util.*;

/** Exact packet-65 bit encoder for spawn/retain/walk/run/remove plus current-client update masks. */
final class NpcSyncEncoder {
    static final int MASK_FORCE_TEXT = 0x01;
    static final int MASK_MULTI_HIT = 0x08;
    static final int MASK_ANIMATION = 0x10;
    static final int MASK_INTERACTION_TARGET = 0x20;
    static final int MASK_SINGLE_HIT = 0x40;
    static final int MASK_GFX = 0x80;

    /** Mask payload. Only exact fields recovered from the pinned current-client decoder are exposed. */
    static final class Mask {
        final Integer animationId;
        final int animationDelay;
        final Integer interactionTarget;
        final Integer hitDamage;
        final Integer gfxId;
        final int gfxHeight;
        final int gfxDelay;
        final String forceText;
        final int hitType;
        final int hitCycle;
        final int currentHp,maxHp;

        private Mask(Integer animationId,int animationDelay,Integer interactionTarget,
                     Integer hitDamage,Integer gfxId,int gfxHeight,int gfxDelay,String forceText,
                     int hitType,int hitCycle,int currentHp,int maxHp){
            this.animationId=animationId; this.animationDelay=animationDelay; this.interactionTarget=interactionTarget;
            this.hitDamage=hitDamage; this.gfxId=gfxId; this.gfxHeight=gfxHeight; this.gfxDelay=gfxDelay;
            this.forceText=forceText; this.hitType=hitType; this.hitCycle=hitCycle; this.currentHp=currentHp; this.maxHp=maxHp;
        }
        static Mask interactionTarget(int target){ return new Mask(null,0,target,null,null,0,0,null,0,0,0,0); }
        static Mask animation(int id,int delay){ return new Mask(id,delay,null,null,null,0,0,null,0,0,0,0); }
        static Mask forceText(String text){
            if(text==null || text.indexOf('\n')>=0 || text.indexOf('\r')>=0) throw new IllegalArgumentException("forceText");
            return new Mask(null,0,null,null,null,0,0,text,0,0,0,0);
        }
        static Mask gfx(int id,int height,int delay){
            if(id < -1 || id > 0xffff || height < 0 || height > 0xffff || delay < 0 || delay > 0xffff)
                throw new IllegalArgumentException("gfx fields out of range");
            return new Mask(null,0,null,null,id,height,delay,null,0,0,0,0);
        }
        static Mask animationAndGfx(int animationId,int animationDelay,int gfxId,int gfxHeight,int gfxDelay){
            if(gfxId < -1 || gfxId > 0xffff || gfxHeight < 0 || gfxHeight > 0xffff || gfxDelay < 0 || gfxDelay > 0xffff)
                throw new IllegalArgumentException("gfx fields out of range");
            return new Mask(animationId,animationDelay,null,null,gfxId,gfxHeight,gfxDelay,null,0,0,0,0);
        }
        static Mask singleHit(int damage,int type,int currentHp,int maxHp){
            if(damage<0||damage>255||type<0||type>255||currentHp<0||currentHp>255||maxHp<0||maxHp>255)
                throw new IllegalArgumentException("single-hit field outside byte range");
            return new Mask(null,0,null,damage,null,0,0,null,type,0,currentHp,maxHp);
        }
        Mask withAnimation(int id,int delay){ return new Mask(id,delay,interactionTarget,hitDamage,gfxId,gfxHeight,gfxDelay,forceText,hitType,hitCycle,currentHp,maxHp); }
        Mask withGfx(int id,int height,int delay){ return new Mask(animationId,animationDelay,interactionTarget,hitDamage,id,height,delay,forceText,hitType,hitCycle,currentHp,maxHp); }
        Mask withForceText(String text){ return new Mask(animationId,animationDelay,interactionTarget,hitDamage,gfxId,gfxHeight,gfxDelay,text,hitType,hitCycle,currentHp,maxHp); }
        int bits(){
            int b=0;
            if(animationId!=null)b|=MASK_ANIMATION;
            if(interactionTarget!=null)b|=MASK_INTERACTION_TARGET;
            if(hitDamage!=null)b|=MASK_SINGLE_HIT;
            if(gfxId!=null)b|=MASK_GFX;
            if(forceText!=null)b|=MASK_FORCE_TEXT;
            return b;
        }
        boolean empty(){return bits()==0;}
    }

    static final class Update {
        final NpcEntity npc;
        final boolean remove;
        final int dir1,dir2;
        final Mask mask;
        private Update(NpcEntity npc,boolean remove,int dir1,int dir2,Mask mask){
            this.npc=npc;this.remove=remove;this.dir1=dir1;this.dir2=dir2;this.mask=mask;
        }
        static Update retain(NpcEntity n){return new Update(n,false,-1,-1,null);}
        static Update mask(NpcEntity n,Mask m){return new Update(n,false,-1,-1,m);}
        static Update walk(NpcEntity n,int d){return new Update(n,false,d,-1,null);}
        static Update walk(NpcEntity n,int d,Mask m){return new Update(n,false,d,-1,m);}
        static Update run(NpcEntity n,int d1,int d2){return new Update(n,false,d1,d2,null);}
        static Update run(NpcEntity n,int d1,int d2,Mask m){return new Update(n,false,d1,d2,m);}
        static Update remove(NpcEntity n){return new Update(n,true,-1,-1,null);}
        boolean masked(){return mask!=null && !mask.empty();}
    }
    private NpcSyncEncoder(){}

    static byte[] initial(List<NpcEntity> npcs,int playerX,int playerY){
        return encode(Collections.emptyList(),npcs,playerX,playerY);
    }

    /**
     * Exact pinned-client packet-65 topology:
     * 8-bit existing count -> existing movement records -> new NPC records ->
     * 16383 sentinel when mask bytes follow -> byte align -> queued mask blocks.
     */
    static byte[] encode(List<Update> existing,List<NpcEntity> added,int playerX,int playerY){
        return encode(existing,added,playerX,playerY,Collections.emptyMap());
    }

    /**
     * Same exact packet-65 stream with optional per-new-NPC presentation fields.
     * An empty presentation map is byte-for-byte identical to the legacy encoder.
     */
    static byte[] encode(List<Update> existing,List<NpcEntity> added,int playerX,int playerY,
                         Map<Integer,NpcSpawnPresentation> spawnPresentationByScene){
        if(existing.size()>255) throw new IllegalArgumentException("existing NPC count >255");
        if(spawnPresentationByScene==null) spawnPresentationByScene=Collections.emptyMap();
        BitWriter b=new BitWriter();
        b.write(existing.size(),8);
        ArrayList<Mask> queuedMasks=new ArrayList<>();

        for(Update u:existing){
            if(u.remove){
                b.write(1,1); b.write(3,2);
            } else if(u.dir1<0){
                if(u.masked()){
                    // current client existing-NPC type 0 = retain + queue update mask
                    b.write(1,1); b.write(0,2); queuedMasks.add(u.mask);
                } else b.write(0,1);
            } else if(u.dir2<0){
                b.write(1,1); b.write(1,2); b.write(u.dir1,3); b.write(u.masked()?1:0,1);
                if(u.masked())queuedMasks.add(u.mask);
            } else {
                b.write(1,1); b.write(2,2); b.write(u.dir1,3); b.write(u.dir2,3); b.write(u.masked()?1:0,1);
                if(u.masked())queuedMasks.add(u.mask);
            }
        }

        for(NpcEntity n:added){
            Mask m=(n.pet && n.ownerPlayerIndex>=0)?Mask.interactionTarget(32768+n.ownerPlayerIndex):null;
            boolean masked=m!=null;
            writeSpawn(b,n,playerX,playerY,spawnPresentationByScene.get(n.sceneIndex),masked);
            if(masked) queuedMasks.add(m);
        }
        if(!queuedMasks.isEmpty()) b.write(16383,14);
        byte[] bitPart=b.finish();
        if(queuedMasks.isEmpty()) return bitPart;

        try {
            ByteArrayOutputStream out=new ByteArrayOutputStream(bitPart.length+queuedMasks.size()*10);
            out.write(bitPart);
            for(Mask m:queuedMasks) writeMask(out,m);
            return out.toByteArray();
        } catch(IOException impossible){ throw new AssertionError(impossible); }
    }

    private static void writeMask(OutputStream out,Mask m)throws IOException{
        int bits=m.bits();
        putU16(out,bits); // NPC decoder reads A(): ordinary BE unsigned short.
        // Exact current-client decode order for implemented fields:
        // animation 0x10 -> multi-hit 0x08 -> single-hit 0x40 -> GFX 0x80
        // -> interaction 0x20 -> force-text 0x01. Multi-hit is intentionally not emitted.
        if((bits&MASK_ANIMATION)!=0){
            int id=m.animationId<0?0xffff:m.animationId;
            putLE16(out,id);
            out.write(m.animationDelay&255);
        }
        if((bits&MASK_SINGLE_HIT)!=0){
            out.write(encO(m.hitDamage));
            out.write(encP(m.hitType));
            out.write(m.hitCycle&255);
            out.write(encP(m.currentHp));
            out.write(encO(m.maxHp));
        }
        if((bits&MASK_GFX)!=0){
            int id=m.gfxId<0?0xffff:m.gfxId;
            putU16(out,id); // A(): big-endian unsigned short
            int packed=((m.gfxHeight&0xffff)<<16)|(m.gfxDelay&0xffff);
            putU32(out,packed); // D(): ordinary big-endian int
        }
        if((bits&MASK_INTERACTION_TARGET)!=0) putU16(out,m.interactionTarget);
        if((bits&MASK_FORCE_TEXT)!=0){
            byte[] raw=m.forceText.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            out.write(raw);
            out.write(10); // F(): newline-terminated string
        }
    }

    private static void writeSpawn(BitWriter b,NpcEntity n,int playerX,int playerY,NpcSpawnPresentation presentation,boolean mask){
        int dx=n.x-playerX,dy=n.y-playerY;
        if(dx<-16||dx>15||dy<-16||dy>15) throw new IllegalArgumentException("NPC spawn outside signed5 relative range: "+n+" player="+playerX+","+playerY);
        b.write(n.sceneIndex,14);
        b.write(dy&31,5);
        b.write(dx&31,5);

        // Exact current-client new-NPC field order. The first optional bit is the
        // 8-bit pet/accessory particle selector. Keeping it absent emits the same
        // four zero bits as the pre-Workbench encoder.
        Integer selector=presentation==null?null:presentation.particleSelector;
        if(selector==null) b.write(0,1);
        else { b.write(1,1); b.write(selector,8); }
        b.write(0,1); // optional 4-byte presentation field not exposed yet
        b.write(0,1); // optional orientation/state field not exposed yet
        b.write(0,1); // aH flag remains false in LocalLab
        b.write(n.definitionId,14);
        b.write(mask?1:0,1);
    }

    private static int encO(int v){return (-v)&255;}
    private static int encP(int v){return (128-v)&255;}
    private static void putU16(OutputStream out,int v)throws IOException{out.write((v>>>8)&255);out.write(v&255);}
    private static void putU32(OutputStream out,int v)throws IOException{out.write((v>>>24)&255);out.write((v>>>16)&255);out.write((v>>>8)&255);out.write(v&255);}
    private static void putLE16(OutputStream out,int v)throws IOException{out.write(v&255);out.write((v>>>8)&255);}
}
