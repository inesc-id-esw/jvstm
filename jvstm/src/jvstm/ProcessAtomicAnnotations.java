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

import jvstm.util.Cons;
import jvstm.util.Pair;


public class ProcessAtomicAnnotations {
    private static final String ATOMIC_DESC = Type.getDescriptor(Atomic.class);

    private String[] files;
    private String txClassInternalName = Type.getInternalName(Transaction.class);

    public ProcessAtomicAnnotations(String[] files) {
	this.files = files;
    }

    public ProcessAtomicAnnotations(Class txClassToUse, String[] files) {
        this(files);
        this.txClassInternalName = Type.getInternalName(txClassToUse);
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
        //System.out.println("Processing file " + classFile + " for atomic annotations");
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
            cr.accept(cv, 0);
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
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            AtomicMethodTransformer cv = new AtomicMethodTransformer(cw, atomicMethods, txClassInternalName);
            cr.accept(cv, 0);
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
                    return new AtomicAnnotationVisitor();
		} else {
                    return super.visitAnnotation(desc, visible);
                }
	    }

            class AtomicAnnotationVisitor implements AnnotationVisitor {
                private AtomicParams annotationParams = new AtomicParams();

                public void visit(String name, Object value) {
                    annotationParams.addParam(name, value);
                }

                public AnnotationVisitor visitAnnotation(String name, String desc) {
                    return null;
                }

                public AnnotationVisitor visitArray(String name) {
                    return null;
                }

                public void visitEnd() {
		    atomicMethods.addAtomicMethod(methodName, methodDesc, annotationParams);
                }

                public void visitEnum(String name, String desc, String value) {
                    // empty
                }
            }
	}
    }


    protected static String getInternalMethodName(String name) {
	return "atomic$" + name;
    }

    static class AtomicMethodTransformer extends ClassAdapter implements Opcodes {
	private AtomicMethodsInfo atomicMethods;
        private String txClassInternalName;
        private String className = null;
	private boolean renameMethods = true;
	private ArrayList<MethodWrapper> methods = new ArrayList<MethodWrapper>();

        public AtomicMethodTransformer(ClassWriter cw, AtomicMethodsInfo atomicMethods, String txClassInternalName) {
            super(cw);
	    this.atomicMethods = atomicMethods;
            this.txClassInternalName = txClassInternalName;
        }

        public void visitEnd() {
	    renameMethods = false;
	    for (MethodWrapper mw : methods) {
                boolean canFail = mw.annParams.getParamAsBoolean("canFail");
                boolean readOnly = mw.annParams.getParamAsBoolean("readOnly");
                boolean flattenNested = (readOnly || (! canFail));

		Type returnType = Type.getReturnType(mw.desc);
		Type[] argsType = Type.getArgumentTypes(mw.desc);

		int argsSize = 0;
		for (Type arg : argsType) {
		    argsSize += arg.getSize();
		}

                if ((mw.access & Opcodes.ACC_STATIC) == 0) {
                    argsSize++;
                }

		int boolVarPos = argsSize;
		int retSize = (returnType == Type.VOID_TYPE) ? 0 : returnType.getSize();

		MethodVisitor mv = visitMethod(mw.access, mw.name, mw.desc, mw.signature, mw.exceptions);

                // the following code comes originally from the ASMfier
		mv.visitCode();
                Label l0 = new Label();
                Label l1 = new Label();
                Label l2 = new Label();
                mv.visitTryCatchBlock(l0, l1, l2, "jvstm/CommitException");
                Label l3 = new Label();
                mv.visitTryCatchBlock(l0, l1, l3, null);
                Label l4 = new Label();
                mv.visitTryCatchBlock(l2, l4, l3, null);
                Label l5 = new Label();
                mv.visitTryCatchBlock(l3, l5, l3, null);

                if (flattenNested) {
                    mv.visitMethodInsn(INVOKESTATIC, txClassInternalName, "isInTransaction", "()Z");
                }

                Label l6 = new Label();

                Label lExit = null;
                if (flattenNested) {
                    mv.visitJumpInsn(IFEQ, l6);
                    generateInternalMethodCall(mv, mw, argsType);
                    if (returnType == Type.VOID_TYPE) {
                        lExit = new Label();
                        mv.visitJumpInsn(GOTO, lExit);
                    } else {
                        mv.visitInsn(returnType.getOpcode(IRETURN));
                    }
                }

                mv.visitLabel(l6);
                mv.visitMethodInsn(INVOKESTATIC, txClassInternalName, "begin", "()Ljvstm/Transaction;");
                mv.visitInsn(POP);
                mv.visitInsn(ICONST_0);
                mv.visitVarInsn(ISTORE, boolVarPos);
                mv.visitLabel(l0);
                generateInternalMethodCall(mv, mw, argsType);

                if (returnType != Type.VOID_TYPE) {
                    mv.visitVarInsn(returnType.getOpcode(ISTORE), boolVarPos + 1);
                }

                mv.visitMethodInsn(INVOKESTATIC, txClassInternalName, "commit", "()V");
                mv.visitInsn(ICONST_1);
                mv.visitVarInsn(ISTORE, boolVarPos);

                if (returnType != Type.VOID_TYPE) {
                    mv.visitVarInsn(returnType.getOpcode(ILOAD), boolVarPos + 1);
                    mv.visitVarInsn(returnType.getOpcode(ISTORE), boolVarPos + 1 + retSize);
                }

                mv.visitLabel(l1);
                mv.visitVarInsn(ILOAD, boolVarPos);
                Label l7 = new Label();
                mv.visitJumpInsn(IFNE, l7);
                mv.visitMethodInsn(INVOKESTATIC, txClassInternalName, "abort", "()V");
                mv.visitLabel(l7);

                if (returnType == Type.VOID_TYPE) {
                    mv.visitInsn(RETURN);
                } else {
                    mv.visitVarInsn(returnType.getOpcode(ILOAD), boolVarPos + 1 + retSize);
                    mv.visitInsn(returnType.getOpcode(IRETURN));
                }

                mv.visitLabel(l2);
                mv.visitVarInsn(ASTORE, boolVarPos + 1);
                mv.visitMethodInsn(INVOKESTATIC, txClassInternalName, "abort", "()V");
                mv.visitInsn(ICONST_1);
                mv.visitVarInsn(ISTORE, boolVarPos);
                mv.visitLabel(l4);
                mv.visitVarInsn(ILOAD, boolVarPos);
                Label l8 = new Label();
                mv.visitJumpInsn(IFNE, l8);
                mv.visitMethodInsn(INVOKESTATIC, txClassInternalName, "abort", "()V");
                mv.visitJumpInsn(GOTO, l8);
                mv.visitLabel(l3);
                mv.visitVarInsn(ASTORE, boolVarPos + 1 + (2 * retSize));
                mv.visitLabel(l5);
                mv.visitVarInsn(ILOAD, boolVarPos);
                Label l9 = new Label();
                mv.visitJumpInsn(IFNE, l9);
                mv.visitMethodInsn(INVOKESTATIC, txClassInternalName, "abort", "()V");
                mv.visitLabel(l9);
                mv.visitVarInsn(ALOAD, boolVarPos + 1 + (2 * retSize));
                mv.visitInsn(ATHROW);
                mv.visitLabel(l8);
                mv.visitJumpInsn(GOTO, l6);

                if (flattenNested && (returnType == Type.VOID_TYPE)) {
                    mv.visitLabel(lExit);
                    mv.visitInsn(RETURN);
                }

                // the params of the call to visitMaxs is irrelevant
                // because the ClassWriter is created with the
                // ClassWriter.COMPUTE_MAXS option
                mv.visitMaxs(0, 0);
                mv.visitEnd();
	    }
        }

        private void generateInternalMethodCall(MethodVisitor mv, MethodWrapper mw, Type[] argsType) {
            int stackPos = 0;

            boolean isStatic = ((mw.access & Opcodes.ACC_STATIC) != 0);

            if (! isStatic) {
                mv.visitVarInsn(ALOAD, 0);
                stackPos++;
            }

            for (Type arg : argsType) {
                mv.visitVarInsn(arg.getOpcode(ILOAD), stackPos);
                stackPos += arg.getSize();
            }

            mv.visitMethodInsn((isStatic ? INVOKESTATIC : INVOKESPECIAL), 
                               className, 
                               getInternalMethodName(mw.name), 
                               mw.desc);
        }

        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
	    if (renameMethods) {
                AtomicParams params = atomicMethods.getAtomicParams(name, desc);
                if (params != null) {
                    methods.add(new MethodWrapper(access, name, desc, signature, exceptions, params));
                    access &= (~ Opcodes.ACC_PUBLIC);
                    access &= (~ Opcodes.ACC_PROTECTED);
                    access |= Opcodes.ACC_PRIVATE;
                    return new RemoveAtomicAnnotation(super.visitMethod(access, getInternalMethodName(name), desc, signature, exceptions));
                }
	    }

            return super.visitMethod(access, name, desc, signature, exceptions);
        }

	static class RemoveAtomicAnnotation extends MethodAdapter {
	    RemoveAtomicAnnotation(MethodVisitor mv) {
		super(mv);
	    }

            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
 		if (ATOMIC_DESC.equals(desc)) {
 		    return null;
 		}
 		return super.visitAnnotation(desc, visible);
            }
	}

	static class MethodWrapper {
	    final int access;
	    final String name;
	    final String desc;
	    final String signature;
	    final String[] exceptions;
            final AtomicParams annParams;

	    MethodWrapper(int access, String name, String desc, String signature, String[] exceptions, AtomicParams annParams) {
		this.access = access;
		this.name = name;
		this.desc = desc;
		this.signature = signature;
		this.exceptions = exceptions;
                this.annParams = annParams;
	    }
	}
    }

    static class AtomicMethodsInfo {
	private Map<Pair<String,String>,AtomicParams> atomicMethods = new HashMap<Pair<String,String>,AtomicParams>();

	public boolean isEmpty() {
	    return atomicMethods.isEmpty();
	}

	public void addAtomicMethod(String name, String desc, AtomicParams params) {
            atomicMethods.put(new Pair<String,String>(name, desc), params);
	}

	public AtomicParams getAtomicParams(String name, String desc) {
	    return atomicMethods.get(new Pair<String,String>(name, desc));
	}
    }

    static class AtomicParams {
        private static final Map<String,Object> defaults = new HashMap<String,Object>();

        static {
            for (java.lang.reflect.Method m : Atomic.class.getDeclaredMethods()) {
                defaults.put(m.getName(), m.getDefaultValue());
            }
        }

        private Cons<Pair<String,Object>> params = Cons.empty();

        public void addParam(String name, Object value) {
            params = params.cons(new Pair<String,Object>(name, value));
        }
        
        public boolean getParamAsBoolean(String name) {
            for (Pair<String,Object> p : params) {
                if (p.first.equals(name)) {
                    return ((Boolean)p.second).booleanValue();
                }
            }

            // check default value
            return ((Boolean)defaults.get(name)).booleanValue();
        }
    }
}
