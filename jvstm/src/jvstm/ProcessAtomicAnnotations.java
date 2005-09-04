package jvstm;

public class ProcessAtomicAnnotations {
}

/*
public class AtomicSampleDump implements Opcodes {

    public static byte[] dump () throws Exception {

        ClassWriter cw = new ClassWriter(false);
        FieldVisitor fv;
        MethodVisitor mv;
        AnnotationVisitor av0;

        cw.visit(V1_5, ACC_PUBLIC + ACC_SUPER, "AtomicSample", null, "java/lang/Object", null);

        cw.visitSource("AtomicSample.java", null);

        {
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PUBLIC, "atomicVoid", "(IZLjava/lang/Object;)V", null, null);
            mv.visitCode();
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitMethodInsn(INVOKESTATIC, "Transaction", "begin", "()LTransaction;");
            mv.visitInsn(POP);
            mv.visitInsn(ICONST_0);
            mv.visitVarInsn(ISTORE, 4);
            Label l1 = new Label();
            mv.visitLabel(l1);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ILOAD, 1);
            mv.visitVarInsn(ILOAD, 2);
            mv.visitVarInsn(ALOAD, 3);
            mv.visitMethodInsn(INVOKESPECIAL, "AtomicSample", "internalAtomicVoid", "(IZLjava/lang/Object;)V");
            mv.visitMethodInsn(INVOKESTATIC, "Transaction", "commit", "()I");
            mv.visitInsn(POP);
            mv.visitInsn(ICONST_1);
            mv.visitVarInsn(ISTORE, 4);
            Label l2 = new Label();
            mv.visitLabel(l2);
            mv.visitVarInsn(ILOAD, 4);
            Label l3 = new Label();
            mv.visitJumpInsn(IFNE, l3);
            mv.visitMethodInsn(INVOKESTATIC, "Transaction", "abort", "()V");
            mv.visitLabel(l3);
            mv.visitInsn(RETURN);
            Label l4 = new Label();
            mv.visitLabel(l4);
            mv.visitVarInsn(ASTORE, 5);
            mv.visitMethodInsn(INVOKESTATIC, "Transaction", "abort", "()V");
            mv.visitInsn(ICONST_1);
            mv.visitVarInsn(ISTORE, 4);
            Label l5 = new Label();
            mv.visitLabel(l5);
            mv.visitVarInsn(ILOAD, 4);
            Label l6 = new Label();
            mv.visitJumpInsn(IFNE, l6);
            mv.visitMethodInsn(INVOKESTATIC, "Transaction", "abort", "()V");
            mv.visitJumpInsn(GOTO, l6);
            Label l7 = new Label();
            mv.visitLabel(l7);
            mv.visitVarInsn(ASTORE, 6);
            Label l8 = new Label();
            mv.visitLabel(l8);
            mv.visitVarInsn(ILOAD, 4);
            Label l9 = new Label();
            mv.visitJumpInsn(IFNE, l9);
            mv.visitMethodInsn(INVOKESTATIC, "Transaction", "abort", "()V");
            mv.visitLabel(l9);
            mv.visitVarInsn(ALOAD, 6);
            mv.visitInsn(ATHROW);
            mv.visitLabel(l6);
            mv.visitJumpInsn(GOTO, l0);
            mv.visitTryCatchBlock(l1, l2, l4, "CommitException");
            mv.visitTryCatchBlock(l1, l2, l7, null);
            mv.visitTryCatchBlock(l4, l5, l7, null);
            mv.visitTryCatchBlock(l7, l8, l7, null);
            mv.visitMaxs(4, 7);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PRIVATE, "internalAtomicVoid", "(IZLjava/lang/Object;)V", null, null);
            mv.visitCode();
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 4);
            mv.visitEnd();
        }
        cw.visitEnd();

        return cw.toByteArray();
    }
}
*/
