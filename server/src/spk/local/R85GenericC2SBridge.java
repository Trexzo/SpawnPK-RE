package spk.local;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Minimal bridge used to promote previously framing-only exact-current C2S packets
 * without replacing the historical ClientPacketProbe source wholesale.
 *
 * consumeFramingOnly() is bytecode-instrumented to call tryConsume() first.
 * Events are held per ClientPacketProbe instance and consumed synchronously by LocalSession.
 */
final class R85GenericC2SBridge {
    private static final Map<Object,ArrayDeque<GenericInteractionEvent>> Q=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Field IN=field("in"), TAG=field("tag"), COUNT=field("decodedCount");
    private static Field field(String n){try{Field f=ClientPacketProbe.class.getDeclaredField(n);f.setAccessible(true);return f;}catch(Exception e){throw new ExceptionInInitializerError(e);}}

    static boolean tryConsume(Object probe,int opcode) throws java.io.IOException {
        final int len=length(opcode);if(len<0)return false;
        try{
            InputStream in=(InputStream)IN.get(probe);byte[] p=Binary.readExactly(in,len);
            GenericInteractionEvent e=decode(opcode,p);enqueue(probe,e);
            String tag=(String)TAG.get(probe);long seq=((Long)COUNT.get(probe)).longValue();
            System.out.printf("%sCLIENT_PACKET seq=%d opcode=%d len=%d genericInteraction=%s schema=STATIC_EXACT%n",tag,seq,opcode,len,e);
            return true;
        }catch(java.io.IOException x){throw x;}catch(Exception x){throw new java.io.IOException("R85 generic C2S bridge failed opcode="+opcode,x);}
    }
    static GenericInteractionEvent take(Object probe){synchronized(Q){ArrayDeque<GenericInteractionEvent> q=Q.get(probe);if(q==null)return null;GenericInteractionEvent e=q.pollFirst();if(q.isEmpty())Q.remove(probe);return e;}}
    static void clear(Object probe){Q.remove(probe);}
    private static void enqueue(Object probe,GenericInteractionEvent e){synchronized(Q){Q.computeIfAbsent(probe,k->new ArrayDeque<>()).addLast(e);}}
    static int length(int opcode){switch(opcode){case 14:return 8;case 25:return 12;case 70:return 6;case 176:return 6;case 192:return 12;case 228:return 6;case 234:return 6;case 252:return 6;default:return -1;}}

    static GenericInteractionEvent decode(int opcode,byte[] p){
        if(p==null||p.length!=length(opcode))throw new IllegalArgumentException("opcode="+opcode+" len="+(p==null?-1:p.length));
        switch(opcode){
            // Client writer: selectedWidget=o(BE_A), player=d(BE), selectedItem=d(BE), selectedSlot=n(LE)
            case 14:return GenericInteractionEvent.itemOnPlayer(opcode,beA(p,0),le(p,6),be(p,4),be(p,2));
            // selectedWidget=n(LE), selectedItem=o(BE_A), groundItem=d(BE), worldX=o(BE_A), selectedSlot=p(LE_A), worldY=d(BE)
            case 25:return GenericInteractionEvent.itemOnGround(opcode,le(p,0),leA(p,8),beA(p,2),be(p,4),beA(p,6),be(p,10));
            // object option 3: worldY=n(LE), worldX=d(BE), objectId=p(LE_A)
            case 70:return GenericInteractionEvent.objectOption(opcode,3,leA(p,4),be(p,2),le(p,0));
            // widget item option 6: slot=n(LE), widget=o(BE_A), item=n(LE)
            case 176:return GenericInteractionEvent.widgetItemOption(opcode,6,beA(p,2),le(p,0),le(p,4));
            // selectedWidget=d(BE), objectId=n(LE), worldX=p(LE_A), selectedSlot=n(LE), worldY=p(LE_A), selectedItem=d(BE)
            case 192:return GenericInteractionEvent.itemOnObject(opcode,be(p,0),le(p,6),be(p,10),le(p,2),leA(p,4),leA(p,8));
            // object option 5: objectId=o(BE_A), worldX=o(BE_A), worldY=d(BE)
            case 228:return GenericInteractionEvent.objectOption(opcode,5,beA(p,0),beA(p,2),be(p,4));
            // object option 4: worldY=p(LE_A), objectId=o(BE_A), worldX=p(LE_A)
            case 234:return GenericInteractionEvent.objectOption(opcode,4,beA(p,2),leA(p,4),leA(p,0));
            // object option 2: objectId=p(LE_A), worldX=n(LE), worldY=o(BE_A)
            case 252:return GenericInteractionEvent.objectOption(opcode,2,leA(p,0),le(p,2),beA(p,4));
            default:throw new IllegalArgumentException("unsupported opcode "+opcode);
        }
    }
    private static int be(byte[]b,int o){return ((b[o]&255)<<8)|(b[o+1]&255);}
    private static int le(byte[]b,int o){return (b[o]&255)|((b[o+1]&255)<<8);}
    private static int beA(byte[]b,int o){return ((b[o]&255)<<8)|((b[o+1]-128)&255);}
    private static int leA(byte[]b,int o){return ((b[o]-128)&255)|((b[o+1]&255)<<8);}
    private R85GenericC2SBridge(){}
}
