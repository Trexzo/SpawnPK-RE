import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;

public final class PatchClientPacketProbeR85 {
  public static void main(String[] args) throws Exception {
    if(args.length!=2) throw new IllegalArgumentException("usage: in.class out.class");
    byte[] in=Files.readAllBytes(Paths.get(args[0]));
    ClassReader cr=new ClassReader(in);
    ClassWriter cw=new ClassWriter(cr,ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS){ @Override protected String getCommonSuperClass(String a,String b){return "java/lang/Object";} };
    final boolean[] patched={false};
    ClassVisitor cv=new ClassVisitor(Opcodes.ASM7,cw){
      @Override public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] ex){
        MethodVisitor mv=super.visitMethod(access,name,desc,sig,ex);
        if(name.equals("consumeFramingOnly") && desc.equals("(I)Z")){
          patched[0]=true;
          return new MethodVisitor(Opcodes.ASM7,mv){
            @Override public void visitCode(){
              super.visitCode();
              Label cont=new Label();
              visitVarInsn(Opcodes.ALOAD,0);
              visitVarInsn(Opcodes.ILOAD,1);
              visitMethodInsn(Opcodes.INVOKESTATIC,"spk/local/R85GenericC2SBridge","tryConsume","(Ljava/lang/Object;I)Z",false);
              visitJumpInsn(Opcodes.IFEQ,cont);
              visitInsn(Opcodes.ICONST_1);
              visitInsn(Opcodes.IRETURN);
              visitLabel(cont);
            }
          };
        }
        return mv;
      }
    };
    cr.accept(cv,ClassReader.SKIP_FRAMES);
    if(!patched[0])throw new IllegalStateException("consumeFramingOnly(I)Z not found");
    Files.createDirectories(Paths.get(args[1]).getParent());
    Files.write(Paths.get(args[1]),cw.toByteArray());
    System.out.println("PATCH_CLIENT_PACKET_PROBE_R85_PASS bytes="+cw.toByteArray().length);
  }
}
