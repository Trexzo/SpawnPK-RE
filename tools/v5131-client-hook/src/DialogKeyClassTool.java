import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Variant-safe LocalLab transformer for rs/Client.class.
 * It patches the actual installed class rather than replacing it, preserving
 * localhost/airgap endpoint rewrites and any other variant-specific bytes.
 */
public final class DialogKeyClassTool {
    private static final String HOOK_OWNER="spk/dev/DialogNumberKeys";
    private static final String HOOK_NAME="observe";
    private static final String HOOK_DESC="(I)V";

    private static final class Scan {
        int methodCount, eligibleSites, hookCalls;
        MethodNode method;
        VarInsnNode store;
    }

    private static Scan scan(ClassNode cn){
        Scan s=new Scan();
        for(MethodNode mn:cn.methods){
            if(!mn.name.equals("C")||!mn.desc.equals("()V")) continue;
            s.methodCount++;
            s.method=mn;
            AbstractInsnNode[] ins=mn.instructions.toArray();
            for(int i=0;i<ins.length;i++){
                AbstractInsnNode n=ins[i];
                if(n instanceof MethodInsnNode){
                    MethodInsnNode mi=(MethodInsnNode)n;
                    if(mi.getOpcode()==Opcodes.INVOKESTATIC && mi.owner.equals(HOOK_OWNER) && mi.name.equals(HOOK_NAME) && mi.desc.equals(HOOK_DESC)){
                        s.hookCalls++;
                    }
                    if(mi.name.equals("w") && mi.desc.equals("(I)I")){
                        AbstractInsnNode q=n.getNext();
                        while(q!=null&&q.getOpcode()<0) q=q.getNext();
                        if(q instanceof VarInsnNode && q.getOpcode()==Opcodes.ISTORE && ((VarInsnNode)q).var==1){
                            s.eligibleSites++;
                            s.store=(VarInsnNode)q;
                        }
                    }
                }
            }
        }
        return s;
    }

    private static ClassNode read(Path p)throws Exception{
        byte[] in=Files.readAllBytes(p);
        ClassNode cn=new ClassNode();
        new ClassReader(in).accept(cn,0);
        if(!"rs/Client".equals(cn.name)) throw new IllegalStateException("expected rs/Client, got "+cn.name);
        return cn;
    }

    private static void requireRecognized(Scan s){
        if(s.methodCount!=1) throw new IllegalStateException("expected one Client.C()V, found="+s.methodCount);
        if(s.eligibleSites!=1) throw new IllegalStateException("expected one Client.C key observer site, found="+s.eligibleSites);
        if(s.hookCalls<0||s.hookCalls>1) throw new IllegalStateException("unexpected dialog hook count="+s.hookCalls);
    }

    public static void main(String[] args)throws Exception{
        if(args.length<2) throw new IllegalArgumentException("usage: probe <in> | verify <in> | patch <in> <out>");
        String mode=args[0]; Path in=Paths.get(args[1]);
        ClassNode cn=read(in); Scan s=scan(cn); requireRecognized(s);
        if("probe".equals(mode)){
            System.out.println("DIALOG_KEY_CLASS_PROBE_PASS eligible="+s.eligibleSites+" hooks="+s.hookCalls+" variantSafe=true");
            return;
        }
        if("verify".equals(mode)){
            if(s.hookCalls!=1) throw new IllegalStateException("expected exactly one dialog hook, found="+s.hookCalls);
            System.out.println("DIALOG_KEY_CLASS_VERIFY_PASS eligible="+s.eligibleSites+" hooks=1 variantSafe=true");
            return;
        }
        if("patch".equals(mode)){
            if(args.length!=3) throw new IllegalArgumentException("patch requires in out");
            if(s.hookCalls!=0) throw new IllegalStateException("refusing to double-patch; existing hooks="+s.hookCalls);
            InsnList add=new InsnList();
            add.add(new VarInsnNode(Opcodes.ILOAD,1));
            add.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK_OWNER,HOOK_NAME,HOOK_DESC,false));
            s.method.instructions.insert(s.store,add);
            Scan after=scan(cn);
            if(after.eligibleSites!=1||after.hookCalls!=1) throw new IllegalStateException("post-transform structure mismatch eligible="+after.eligibleSites+" hooks="+after.hookCalls);
            ClassWriter cw=new ClassWriter(0); cn.accept(cw);
            Files.write(Paths.get(args[2]),cw.toByteArray());
            System.out.println("DIALOG_KEY_CLASS_PATCH_PASS eligible=1 hooks=1 variantSafe=true");
            return;
        }
        throw new IllegalArgumentException("unknown mode: "+mode);
    }
}
