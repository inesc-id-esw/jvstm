/*
 * JVSTM: a Java library for Software Transactional Memory
 * Copyright (C) 2005 INESC-ID Software Engineering Group
 * http://www.esw.inesc-id.pt
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * Author's contact:
 * INESC-ID Software Engineering Group
 * Rua Alves Redol 9
 * 1000 - 029 Lisboa
 * Portugal
 */
package jvstm.atomic;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import static java.io.File.separatorChar;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.objectweb.asm.Opcodes.*;

public final class GenerateAtomicInstance {

    private static final String ATOMIC_INSTANCE = "jvstm/atomic/AtomicInstance";

    private GenerateAtomicInstance() { }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Syntax: GenerateAtomicInstance <save-path>");
            System.exit(-1);
        }
        ClassReader cr = new ClassReader("jvstm.Atomic");
        ClassNode cNode = new ClassNode();
        cr.accept(cNode, 0);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_6, ACC_PUBLIC | ACC_FINAL, ATOMIC_INSTANCE, null, "java/lang/Object", new String[] { "jvstm/Atomic" });
        cw.visitSource("JVSTM Atomic Instance Class", null);

        // Generate fields
        for (MethodNode annotationElems : cNode.methods) {
            cw.visitField(ACC_PRIVATE | ACC_FINAL, annotationElems.name, getReturnTypeDescriptor(annotationElems), null, null);
        }

        // Generate constructor
        {
            StringBuffer ctorDescriptor = new StringBuffer("(");
            for (MethodNode annotationElems : cNode.methods) ctorDescriptor.append(getReturnTypeDescriptor(annotationElems));
            ctorDescriptor.append(")V");

            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", ctorDescriptor.toString(), null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
            int localsPos = 0;
            for (MethodNode annotationElems : cNode.methods) {
                Type t = Type.getReturnType(annotationElems.desc);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(t.getOpcode(ILOAD), localsPos+1);
                mv.visitFieldInsn(PUTFIELD, ATOMIC_INSTANCE, annotationElems.name, t.getDescriptor());
                localsPos += t.getSize();
            }
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // Generate getters
        for (MethodNode annotationElems : cNode.methods) {
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, annotationElems.name, annotationElems.desc, null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, ATOMIC_INSTANCE, annotationElems.name, getReturnTypeDescriptor(annotationElems));
            mv.visitInsn(Type.getReturnType(annotationElems.desc).getOpcode(IRETURN));
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // Generate annotationType() method
        {
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "annotationType", "()Ljava/lang/Class;",
                    "()Ljava/lang/Class<+Ljava/lang/annotation/Annotation;>;", null);
            mv.visitCode();
            mv.visitLdcInsn(Type.getType(jvstm.Atomic.class));
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }

        // Write Class
        FileOutputStream fos = null;
        try {
            File f = new File(args[0] + separatorChar + "jvstm" + separatorChar + "atomic" + separatorChar + "AtomicInstance" + ".class");
            fos = new FileOutputStream(f);
            fos.write(cw.toByteArray());
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) { }
            }
        }
    }

    private static String getReturnTypeDescriptor(MethodNode mNode) {
        return Type.getReturnType(mNode.desc).getDescriptor();
    }

}
