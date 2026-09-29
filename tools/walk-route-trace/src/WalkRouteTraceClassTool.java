import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/**
 * Variant-safe diagnostic transformer for the exact-v308 ordinary scene
 * Walk-here route call. The observer is inserted only after the original route
 * result is stored.
 */
public final class WalkRouteTraceClassTool {
    private static final String CLIENT="rs/Client";
    private static final String ROUTE_DESC="(IIIIIIIIIZI)Z";
    private static final String HOOK_OWNER="spk/dev/WalkRouteTrace";
    private static final String HOOK_DESC="(Ljava/lang/Object;IIZ)V";

    private static final class Scan {
        int ownerMethods;
        int routeSites;
        int hooks;
        MethodNode method;
        VarInsnNode resultStore;
    }

    private static AbstractInsnNode codeBefore(AbstractInsnNode n){
        AbstractInsnNode q=n==null?null:n.getPrevious();
        while(q!=null&&q.getOpcode()<0) q=q.getPrevious();
        return q;
    }

    private static AbstractInsnNode codeAfter(AbstractInsnNode n){
        AbstractInsnNode q=n==null?null:n.getNext();
        while(q!=null&&q.getOpcode()<0) q=q.getNext();
        return q;
    }

    private static Scan scan(ClassNode cn){
        Scan s=new Scan();

        for(MethodNode mn:cn.methods){
            if(!mn.name.equals("bz")||!mn.desc.equals("()V"))
                continue;

            s.ownerMethods++;
            s.method=mn;

            for(AbstractInsnNode n:mn.instructions.toArray()){
                if(!(n instanceof MethodInsnNode))
                    continue;

                MethodInsnNode call=(MethodInsnNode)n;

                if(call.getOpcode()==Opcodes.INVOKESTATIC&&
                   call.owner.equals(HOOK_OWNER)&&
                   call.name.equals("observe")&&
                   call.desc.equals(HOOK_DESC)){
                    s.hooks++;
                    continue;
                }

                if(call.getOpcode()!=Opcodes.INVOKESPECIAL||
                   !call.owner.equals(CLIENT)||
                   !call.name.equals("a")||
                   !call.desc.equals(ROUTE_DESC))
                    continue;

                AbstractInsnNode pickedX=codeBefore(call);
                AbstractInsnNode fallback=codeBefore(pickedX);
                AbstractInsnNode result=codeAfter(call);

                if(!(pickedX instanceof VarInsnNode)||
                   pickedX.getOpcode()!=Opcodes.ILOAD||
                   ((VarInsnNode)pickedX).var!=1||
                   fallback==null||
                   fallback.getOpcode()!=Opcodes.ICONST_1||
                   !(result instanceof VarInsnNode)||
                   result.getOpcode()!=Opcodes.ISTORE||
                   ((VarInsnNode)result).var!=3)
                    continue;

                s.routeSites++;
                s.resultStore=(VarInsnNode)result;
            }
        }

        return s;
    }

    private static ClassNode read(Path path)throws Exception{
        ClassNode cn=new ClassNode();
        new ClassReader(Files.readAllBytes(path)).accept(cn,0);

        if(!CLIENT.equals(cn.name))
            throw new IllegalStateException(
                "expected "+CLIENT+", got "+cn.name
            );

        return cn;
    }

    private static void requireRecognized(Scan s){
        if(s.ownerMethods!=1)
            throw new IllegalStateException(
                "expected one Client.bz()V, found="+s.ownerMethods
            );

        if(s.routeSites!=1)
            throw new IllegalStateException(
                "expected one ordinary scene route site, found="+s.routeSites
            );

        if(s.hooks<0||s.hooks>1)
            throw new IllegalStateException(
                "unexpected route trace hook count="+s.hooks
            );

        if(s.resultStore==null)
            throw new IllegalStateException(
                "ordinary scene route result store not found"
            );
    }

    public static void main(String[] args)throws Exception{
        if(args.length<2)
            throw new IllegalArgumentException(
                "usage: probe <in> | verify <in> | patch <in> <out>"
            );

        String mode=args[0];
        ClassNode cn=read(Paths.get(args[1]));
        Scan before=scan(cn);
        requireRecognized(before);

        if("probe".equals(mode)){
            System.out.println(
                "WALK_ROUTE_TRACE_CLASS_PROBE_PASS "+
                "routeSites=1 hooks="+before.hooks+" "+
                "fallbackTrue=true postCallOnly=true variantSafe=true"
            );
            return;
        }

        if("verify".equals(mode)){
            if(before.hooks!=1)
                throw new IllegalStateException(
                    "expected one route trace hook, found="+before.hooks
                );

            System.out.println(
                "WALK_ROUTE_TRACE_CLASS_VERIFY_PASS "+
                "routeSites=1 hooks=1 fallbackTrue=true "+
                "postCallOnly=true variantSafe=true"
            );
            return;
        }

        if(!"patch".equals(mode))
            throw new IllegalArgumentException("unknown mode: "+mode);

        if(args.length!=3)
            throw new IllegalArgumentException("patch requires in out");

        if(before.hooks!=0)
            throw new IllegalStateException(
                "refusing to double-patch; hooks="+before.hooks
            );

        InsnList add=new InsnList();
        add.add(new VarInsnNode(Opcodes.ALOAD,0));
        add.add(new VarInsnNode(Opcodes.ILOAD,1));
        add.add(new VarInsnNode(Opcodes.ILOAD,2));
        add.add(new VarInsnNode(Opcodes.ILOAD,3));
        add.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            HOOK_OWNER,
            "observe",
            HOOK_DESC,
            false
        ));

        before.method.instructions.insert(before.resultStore,add);

        Scan after=scan(cn);
        requireRecognized(after);

        if(after.hooks!=1)
            throw new IllegalStateException(
                "post-transform hook mismatch="+after.hooks
            );

        // Match the already-proven DialogKeyClassTool pattern. The injected
        // observer is straight-line code after an existing ISTORE and adds no
        // labels or control-flow edges, so preserving existing frames is safer
        // than asking ASM to resolve the entire obfuscated client hierarchy.
        ClassWriter writer=
            new ClassWriter(0);

        cn.accept(writer);
        byte[] output=writer.toByteArray();
        Path target=Paths.get(args[2]);
        Path parent=target.getParent();

        if(parent!=null) Files.createDirectories(parent);
        Files.write(target,output);

        System.out.println(
            "WALK_ROUTE_TRACE_CLASS_PATCH_PASS "+
            "routeSites=1 hooks=1 fallbackTrue=true "+
            "postCallOnly=true bytes="+output.length
        );
    }
}
