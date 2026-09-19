package spk.local;

import java.io.*;

/** Exact packet-81 combat/presentation synchronization helpers from the pinned client. */
final class CombatSync {
    private CombatSync() {}

    /** Local player mask 0x08 animation-only transport retained from Combat M1. */
    static byte[] player81AnimationOnly(int animationId) throws IOException {
        if(animationId < -1 || animationId > 0xffff) throw new IllegalArgumentException("animationId");
        BitWriter bits=new BitWriter();
        bits.write(1,1);      // local update follows
        bits.write(0,2);      // no movement, mask follows
        bits.write(0,8);      // no other existing players
        bits.write(2047,11);  // new-player sentinel
        byte[] bitPayload=bits.finish();
        ByteArrayOutputStream out=new ByteArrayOutputStream(bitPayload.length+4);
        out.write(bitPayload);
        out.write(0x08);
        int wire=animationId<0?0xffff:animationId;
        putLE16(out,wire);    // S()
        out.write(0);         // O(0)
        out.write(0);         // O(0)
        return out.toByteArray();
    }

    /** Exact local-player animation + NPC interaction-target mask (0x08|0x01).
     * Interaction target is decoded through S(): unsigned LE16. This is used by
     * pet Pick-up so the actor really faces the pet and keeps that orientation. */
    static byte[] player81AnimationAndInteraction(int animationId,int interactionTarget) throws IOException {
        if(animationId < -1 || animationId > 0xffff) throw new IllegalArgumentException("animationId");
        if(interactionTarget < -1 || interactionTarget > 0xffff) throw new IllegalArgumentException("interactionTarget");
        BitWriter bits=new BitWriter();
        bits.write(1,1); bits.write(0,2); bits.write(0,8); bits.write(2047,11);
        byte[] bitPayload=bits.finish();
        ByteArrayOutputStream out=new ByteArrayOutputStream(bitPayload.length+6);
        out.write(bitPayload);
        out.write(0x09); // animation 0x08 + interaction target 0x01
        int animWire=animationId<0?0xffff:animationId;
        putLE16(out,animWire); out.write(0); out.write(0);
        int targetWire=interactionTarget<0?0xffff:interactionTarget;
        putLE16(out,targetWire);
        return out.toByteArray();
    }

    /** Exact player GFX-only mask 0x100 transport for Workbench isolation tests. */
    static byte[] player81GfxOnly(int gfxId,int height,int startDelay) throws IOException {
        if(gfxId < -1 || gfxId > 0xffff) throw new IllegalArgumentException("gfxId");
        if(height < 0 || height > 0xffff || startDelay < 0 || startDelay > 0xffff) throw new IllegalArgumentException("gfx timing");
        BitWriter bits=new BitWriter();
        bits.write(1,1);
        bits.write(0,2);
        bits.write(0,8);
        bits.write(2047,11);
        byte[] bitPayload=bits.finish();
        ByteArrayOutputStream out=new ByteArrayOutputStream(bitPayload.length+8);
        out.write(bitPayload);
        out.write(0x40); // extension marker only in low mask byte
        out.write(0x01); // high mask byte => 0x100 GFX
        int gfxWire=gfxId<0?0xffff:gfxId;
        putLE16(out,gfxWire);
        int packed=((height&0xffff)<<16)|(startDelay&0xffff);
        putI32(out,packed);
        return out.toByteArray();
    }

    /**
     * V9.08 nurse/pet-boost presentation: player GFX mask 0x100 plus player
     * animation mask 0x08. The first mask byte uses 0x40 as the exact client's
     * extension sentinel, followed by the high mask byte.
     *
     * GFX decoder: S() id, Y()/D()-style packed int where high16 is height and
     * low16 is start-delay relative to client cycle. Animation then decodes via
     * S(), O(), O(). LocalLab uses zero replay/delay hints for the recovered
     * 10184 + 1310 presentation.
     */
    static byte[] player81AnimationAndGfx(int animationId,int gfxId,int height,int startDelay) throws IOException {
        if(animationId < -1 || animationId > 0xffff) throw new IllegalArgumentException("animationId");
        if(gfxId < -1 || gfxId > 0xffff) throw new IllegalArgumentException("gfxId");
        if(height < 0 || height > 0xffff || startDelay < 0 || startDelay > 0xffff) throw new IllegalArgumentException("gfx timing");
        BitWriter bits=new BitWriter();
        bits.write(1,1);
        bits.write(0,2);
        bits.write(0,8);
        bits.write(2047,11);
        byte[] bitPayload=bits.finish();
        ByteArrayOutputStream out=new ByteArrayOutputStream(bitPayload.length+16);
        out.write(bitPayload);
        out.write(0x48); // 0x40 extension marker + 0x08 animation
        out.write(0x01); // high byte => 0x100 GFX

        int gfxWire=gfxId<0?0xffff:gfxId;
        putLE16(out,gfxWire); // S()
        int packed=((height&0xffff)<<16)|(startDelay&0xffff);
        putI32(out,packed);   // D()/BE32 in this exact branch

        int animWire=animationId<0?0xffff:animationId;
        putLE16(out,animWire); // S()
        out.write(0);          // O(0)
        out.write(0);          // O(0)
        return out.toByteArray();
    }


    /** Combined attack presentation: actor GFX + action animation + NPC interaction target.
     * Mask = 0x100 | 0x008 | 0x001; decode order follows exact current packet-81
     * branch order already proven independently by the existing GFX/animation and
     * animation/interaction helpers. */
    static byte[] player81AnimationGfxAndInteraction(int animationId,int gfxId,int height,int startDelay,int interactionTarget) throws IOException {
        if(animationId < -1 || animationId > 0xffff) throw new IllegalArgumentException("animationId");
        if(gfxId < -1 || gfxId > 0xffff) throw new IllegalArgumentException("gfxId");
        if(height < 0 || height > 0xffff || startDelay < 0 || startDelay > 0xffff) throw new IllegalArgumentException("gfx timing");
        if(interactionTarget < -1 || interactionTarget > 0xffff) throw new IllegalArgumentException("interactionTarget");
        BitWriter bits=new BitWriter();
        bits.write(1,1); bits.write(0,2); bits.write(0,8); bits.write(2047,11);
        byte[] bitPayload=bits.finish();
        ByteArrayOutputStream out=new ByteArrayOutputStream(bitPayload.length+18);
        out.write(bitPayload);
        out.write(0x49); // extension marker 0x40 + animation 0x08 + interaction 0x01
        out.write(0x01); // high mask byte => GFX 0x100

        int gfxWire=gfxId<0?0xffff:gfxId;
        putLE16(out,gfxWire);
        putI32(out,((height&0xffff)<<16)|(startDelay&0xffff));

        int animWire=animationId<0?0xffff:animationId;
        putLE16(out,animWire); out.write(0); out.write(0);

        int targetWire=interactionTarget<0?0xffff:interactionTarget;
        putLE16(out,targetWire);
        return out.toByteArray();
    }

    /** NPC interaction target without animation/GFX. */
    static byte[] player81InteractionOnly(int interactionTarget) throws IOException {
        if(interactionTarget < -1 || interactionTarget > 0xffff) throw new IllegalArgumentException("interactionTarget");
        BitWriter bits=new BitWriter();
        bits.write(1,1); bits.write(0,2); bits.write(0,8); bits.write(2047,11);
        byte[] bitPayload=bits.finish();
        ByteArrayOutputStream out=new ByteArrayOutputStream(bitPayload.length+3);
        out.write(bitPayload);
        out.write(0x01);
        int targetWire=interactionTarget<0?0xffff:interactionTarget;
        putLE16(out,targetWire);
        return out.toByteArray();
    }

    private static void putLE16(OutputStream out,int v)throws IOException{out.write(v&255);out.write((v>>>8)&255);}
    private static void putI32(OutputStream out,int v)throws IOException{
        out.write((v>>>24)&255);out.write((v>>>16)&255);out.write((v>>>8)&255);out.write(v&255);
    }
}
