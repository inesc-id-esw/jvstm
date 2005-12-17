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
package jvstm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

public class ProcessAtomicAnnotations {
    private static final String ATOMIC_DESC = Type.getDescriptor(Atomic.class);

    private String[] files;

    ProcessAtomicAnnotations(String[] files) {
	this.files = files;
    }
    
    public void start() {
        for (String file : files) {
            processFile(new File(file));
        }
    }

    public void processFile(File file) {
        if (file.isDirectory()) {
            for (File subFile : file.listFiles()) {
                processFile(subFile);
            }
        } else {
            String fileName = file.getName();
            if (fileName.endsWith(".class")) {
                processClassFile(file);
            }
        }
    }

    protected void processClassFile(File classFile) {
	AtomicMethodsInfo atomicMethods = collectAtomicMethods(classFile);
	if (! atomicMethods.isEmpty()) {
	    transformClassFile(classFile, atomicMethods);
	}
    }


    protected AtomicMethodsInfo collectAtomicMethods(File classFile) {
        InputStream is = null;
        
        try {
            // get an input stream to read the bytecode of the class
            is = new FileInputStream(classFile);
            ClassReader cr = new ClassReader(is);
            AtomicMethodCollector cv = new AtomicMethodCollector();
            cr.accept(cv, false);
	    return cv.getAtomicMethods();
        } catch (Exception e) {
            e.printStackTrace();
            throw new Error("Error processing class file: " + e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                    // intentionally empty
                }
            }
        }
    }


    protected void transformClassFile(File classFile, AtomicMethodsInfo atomicMethods) {
        InputStream is = null;
        
        try {
            // get an input stream to read the bytecode of the class
            is = new FileInputStream(classFile);
            ClassReader cr = new ClassReader(is);
            ClassWriter cw = new ClassWriter(false);
            AtomicMethodTransformer cv = new AtomicMethodTransformer(cw, atomicMethods);
            cr.accept(cv, false);
	    writeNewClassFile(classFile, cw.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            throw new Error("Error processing class file: " + e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                    // intentionally empty
                }
            }
        }
    }

    protected void writeNewClassFile(File classFile, byte[] bytecode) {
	FileOutputStream fos = null;
        try {
	    fos = new FileOutputStream(classFile);
            fos.write(bytecode);
        } catch (Exception e) {
            throw new Error("Couldn't rewrite class file: " + e);
        } finally {
	    if (fos != null) {
		try {
		    fos.close();
		} catch (Exception e) {
		    // intentionally empty
		}
	    }
	}
    }


    public static void main (final String args[]) throws Exception {
        ProcessAtomicAnnotations processor = new ProcessAtomicAnnotations(args);
        processor.start();
    }


    static class AtomicMethodCollector extends ClassAdapter {
	private AtomicMethodsInfo atomicMethods = new AtomicMethodsInfo();

        public AtomicMethodCollector() {
            super(new EmptyVisitor());
        }

	public AtomicMethodsInfo getAtomicMethods() {
	    return atomicMethods;
	}

        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
	    return new MethodCollector(mv, name, desc);
        }

	
	class MethodCollector extends MethodAdapter {
	    private String methodName;
	    private String methodDesc;

	    MethodCollector(MethodVisitor mv, String name, String desc) {
		super(mv);
		this.methodName = name;
		this.methodDesc = desc;
	    }

	    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
		if (ATOMIC_DESC.equals(desc)) {
		    atomicMethods.addAtomicMethod(methodName, methodDesc);
		}
		return super.visitAnnotation(desc, visible);
	    }
	}
    }


    protected static String getInternalMethodName(String name) {
	return "atomic$" + name;
    }

    static class AtomicMethodTransformer extends ClassAdapter implements Opcodes {
	private AtomicMethodsInfo atomicMethods;
        private String className = null;
	private boolean renameMethods = true;
	private ArrayList<MethodWrapper> methods = new ArrayList<MethodWrapper>();

        public AtomicMethodTransformer(ClassWriter cw, AtomicMethodsInfo atomicMethods) {
            super(cw);
	    this.atomicMethods = atomicMethods;
        }

        public void visitEnd() {
	    renameMethods = false;
	    for (MethodWrapper mw : methods) {
		Type returnType = Type.getReturnType(mw.desc);
		Type[] argsType = Type.getArgumentTypes(mw.desc);

		int argsSize = 0;
		for (Type arg : argsType) {
		    argsSize += arg.getSize();
		}

		int boolVarPos = argsSize + 1;
		int retSize = (returnType == Type.VOID_TYPE) ? 0 : returnType.getSize();

		MethodVisitor mv = visitMethod(mw.access, mw.name, mw.desc, mw.signature, mw.exceptions);
		mv.visitCode();
		Label l0 = new Label();
		mv.visitLabel(l0);
		mv.visitMethodInsn(INVOKESTATIC, "jvstm/Transaction", "begin", "()Ljvstm/Transaction;");
		mv.visitInsn(POP);
		mv.visitInsn(ICONST_0);
		mv.visitVarInsn(ISTORE, boolVarPos);
		Label l1 = new Label();
		mv.visitLabel(l1);
		mv.visitVarInsn(ALOAD, 0);

		int stackPos = 1;
		for (Type arg : argsType) {
		    mv.visitVarInsn(arg.getOpcode(ILOAD), stackPos);
		    stackPos += arg.getSize();
		}

		mv.visitMethodInsn(INVOKESPECIAL, className, getInternalMethodName(mw.name), mw.desc);
		if (returnType != Type.VOID_TYPE) {
		    mv.visitVarInsn(returnType.getOpcode(ISTORE), boolVarPos + 1);
		}

		mv.visitMethodInsn(INVOKESTATIC, "jvstm/Transaction", "commit", "()V");
		mv.visitInsn(ICONST_1);
		mv.visitVarInsn(ISTORE, boolVarPos);

		if (returnType != Type.VOID_TYPE) {
		    mv.visitVarInsn(returnType.getOpcode(ILOAD), boolVarPos + 1);
		    mv.visitVarInsn(returnType.getOpcode(ISTORE), boolVarPos + 1 + retSize);
		}

		Label l2 = new Label();
		mv.visitLabel(l2);
		mv.visitVarInsn(ILOAD, boolVarPos);
		Label l3 = new Label();
		mv.visitJumpInsn(IFNE, l3);
		mv.visitMethodInsn(INVOKESTATIC, "jvstm/Transaction", "abort", "()V");
		mv.visitLabel(l3);

		if (returnType != Type.VOID_TYPE) {
		    mv.visitVarInsn(returnType.getOpcode(ILOAD), boolVarPos + 1 + retSize);
		}
		mv.visitInsn(returnType.getOpcode(IRETURN));
		Label l4 = new Label();
		mv.visitLabel(l4);
		mv.visitVarInsn(ASTORE, boolVarPos + 1);
		mv.visitMethodInsn(INVOKESTATIC, "jvstm/Transaction", "abort", "()V");
		mv.visitInsn(ICONST_1);
		mv.visitVarInsn(ISTORE, boolVarPos);
		Label l5 = new Label();
		mv.visitLabel(l5);
		mv.visitVarInsn(ILOAD, boolVarPos);
		Label l6 = new Label();
		mv.visitJumpInsn(IFNE, l6);
		mv.visitMethodInsn(INVOKESTATIC, "jvstm/Transaction", "abort", "()V");
		mv.visitJumpInsn(GOTO, l6);
		Label l7 = new Label();
		mv.visitLabel(l7);
		mv.visitVarInsn(ASTORE, boolVarPos + 1 + (2 * retSize));
		Label l8 = new Label();
		mv.visitLabel(l8);
		mv.visitVarInsn(ILOAD, boolVarPos);
		Label l9 = new Label();
		mv.visitJumpInsn(IFNE, l9);
		mv.visitMethodInsn(INVOKESTATIC, "jvstm/Transaction", "abort", "()V");
		mv.visitLabel(l9);
		mv.visitVarInsn(ALOAD, boolVarPos + 1 + (2 * retSize));
		mv.visitInsn(ATHROW);
		mv.visitLabel(l6);
		mv.visitJumpInsn(GOTO, l0);
		mv.visitTryCatchBlock(l1, l2, l4, "jvstm/CommitException");
		mv.visitTryCatchBlock(l1, l2, l7, null);
		mv.visitTryCatchBlock(l4, l5, l7, null);
		mv.visitTryCatchBlock(l7, l8, l7, null);
		mv.visitMaxs(boolVarPos, boolVarPos + 2 + (2 * retSize));
		mv.visitEnd();
	    }
        }

        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
	    if (renameMethods && atomicMethods.isAtomicMethod(name, desc)) {
		methods.add(new MethodWrapper(access, name, desc, signature, exceptions));
		return super.visitMethod(ACC_PRIVATE, getInternalMethodName(name), desc, signature, exceptions);
	    } else {
		return super.visitMethod(access, name, desc, signature, exceptions);
	    }
        }

	static class MethodWrapper {
	    final int access;
	    final String name;
	    final String desc;
	    final String signature;
	    final String[] exceptions;

	    MethodWrapper(int access, String name, String desc, String signature, String[] exceptions) {
		this.access = access;
		this.name = name;
		this.desc = desc;
		this.signature = signature;
		this.exceptions = exceptions;
	    }
	}
    }

    static class AtomicMethodsInfo {
	private Map<String,Set<String>> atomicMethods = new HashMap<String,Set<String>>();

	public boolean isEmpty() {
	    return atomicMethods.isEmpty();
	}

	public void addAtomicMethod(String name, String desc) {
	    Set<String> methodDescs = atomicMethods.get(name);
	    if (methodDescs == null) {
		methodDescs = new HashSet<String>();
		atomicMethods.put(name, methodDescs);
	    }
	    methodDescs.add(desc);
	}

	public boolean isAtomicMethod(String name, String desc) {
	    Set<String> methodDescs = atomicMethods.get(name);
	    return (methodDescs != null) && methodDescs.contains(desc);
	}
    }
}
