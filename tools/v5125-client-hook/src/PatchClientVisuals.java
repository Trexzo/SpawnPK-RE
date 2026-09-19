import java.nio.file.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

public final class PatchClientVisuals {
  public static void main(String[] args) throws Exception {
    if(args.length!=2) throw new IllegalArgumentException("input rs/a/a.class output class");
    byte[] in=Files.readAllBytes(Paths.get(args[0]));
    ClassNode cn=new ClassNode(); new ClassReader(in).accept(cn,0);
    int hooks=0,fxGates=0;
    for(MethodNode mn:cn.methods){
      if(!mn.name.equals("a") || !mn.desc.equals("(IIIIIIIIII)V"))continue;
      List<AbstractInsnNode> calls=new ArrayList<>();
      for(AbstractInsnNode n=mn.instructions.getFirst();n!=null;n=n.getNext()){
        if(n instanceof MethodInsnNode){MethodInsnNode m=(MethodInsnNode)n;
          if(m.getOpcode()==Opcodes.INVOKEVIRTUAL&&m.owner.equals("rs/a/h")&&m.name.equals("a")&&m.desc.equals("(IIIIIIIIII)V"))calls.add(n);
        }
      }
      for(AbstractInsnNode call:calls){
        AbstractInsnNode q=call.getPrevious(); VarInsnNode aload=null;
        while(q!=null){
          if(q instanceof VarInsnNode && q.getOpcode()==Opcodes.ALOAD && ((VarInsnNode)q).var==11){aload=(VarInsnNode)q;break;}
          if(q instanceof LabelNode || q instanceof JumpInsnNode)break;
          q=q.getPrevious();
        }
        if(aload==null)throw new IllegalStateException("no ALOAD 11 before draw call");
        InsnList pre=new InsnList();
        // after original ALOAD 11 stack=[model]; duplicate it, load actor, swap -> [model,actor,model]
        pre.add(new InsnNode(Opcodes.DUP));
        pre.add(new VarInsnNode(Opcodes.ALOAD,0));
        pre.add(new InsnNode(Opcodes.SWAP));
        pre.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"spk/dev/PetVisualOverrides","beforeDraw","(Ljava/lang/Object;Ljava/lang/Object;)V",false));
        mn.instructions.insert(aload,pre);
        InsnList post=new InsnList();
        post.add(new VarInsnNode(Opcodes.ALOAD,11));
        post.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"spk/dev/PetVisualOverrides","afterDraw","(Ljava/lang/Object;)V",false));
        mn.instructions.insert(call,post);
        hooks++;
      }

      // Exact-current renderer injects selector 3 for NPC 1334/8210 before model
      // drawing. Gate only that intrinsic injection; explicit server selectors in
      // aw/ax/ay remain untouched.
      AbstractInsnNode gateAt=null; LabelNode skipLabel=null;
      for(AbstractInsnNode n=mn.instructions.getFirst();n!=null;n=n.getNext()){
        if(!(n instanceof LdcInsnNode) || !Long.valueOf(1334L).equals(((LdcInsnNode)n).cst))continue;
        AbstractInsnNode q=n.getNext(); boolean saw8210=false; JumpInsnNode jump8210=null; AbstractInsnNode iconst3=null;
        int steps=0;
        while(q!=null && steps++<40){
          if(q instanceof LdcInsnNode && Long.valueOf(8210L).equals(((LdcInsnNode)q).cst))saw8210=true;
          if(saw8210 && q instanceof JumpInsnNode && q.getOpcode()==Opcodes.IFNE){jump8210=(JumpInsnNode)q;}
          if(saw8210 && q.getOpcode()==Opcodes.ICONST_3){iconst3=q;break;}
          q=q.getNext();
        }
        if(saw8210 && jump8210!=null && iconst3!=null){gateAt=iconst3;skipLabel=jump8210.label;break;}
      }
      if(gateAt!=null && skipLabel!=null){
        InsnList gate=new InsnList();
        gate.add(new VarInsnNode(Opcodes.ALOAD,0));
        gate.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"spk/dev/PetVisualOverrides","allowIntrinsicSpecialFx","(Ljava/lang/Object;)Z",false));
        gate.add(new JumpInsnNode(Opcodes.IFEQ,skipLabel));
        mn.instructions.insertBefore(gateAt,gate);
        fxGates++;
      }
    }
    if(hooks!=3)throw new IllegalStateException("expected 3 render hooks, got "+hooks);
    if(fxGates!=1)throw new IllegalStateException("expected 1 intrinsic-fx gate, got "+fxGates);
    ClassWriter cw=new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS); cn.accept(cw);
    Files.createDirectories(Paths.get(args[1]).getParent()); Files.write(Paths.get(args[1]),cw.toByteArray());
    System.out.println("PATCH_CLIENT_VISUALS_OK hooks="+hooks+" intrinsicFxGates="+fxGates+" bytes="+cw.toByteArray().length);
  }
}
