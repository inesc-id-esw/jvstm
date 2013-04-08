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

import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_PROTECTED;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ASM4;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_6;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public class ProcessParNestAnnotations {

        private static final Type UNSAFE_SPAWN = Type.getType(jvstm.atomic.UnsafeSpawn.class);
        private static final Type PARALLEL_SPAWN = Type.getType(jvstm.atomic.ParallelSpawn.class);
        private static final Type COMBINER = Type.getType(jvstm.atomic.Combiner.class);
        private static final Type PAR_NEST = Type.getType(jvstm.atomic.ParNest.class);
        private static final Type ARRAY_LIST = Type.getType(java.util.ArrayList.class);
        private static final Type TRANSACTION = Type.getType(jvstm.Transaction.class);

        private final String[] files;

        public ProcessParNestAnnotations(String[] files) {
                this.files = files;
        }

        public static void main(final String args[]) throws Exception {
                new ProcessParNestAnnotations(args).process();
        }

        public void process() {
                for (String file : files) {
                        processFile(new File(file));
                }
        }

        public static void processFile(File file) {
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

        private static List<String> alreadyProcessed;
        // maps the method to the callable name
        private static Map<String, String> callablesCreated;

        private static String createUniqueMethodName(String methodName) {
                alreadyProcessed.add(methodName);
                int count = 0;
                for (String name : alreadyProcessed) {
                        if (methodName.equals(name)) {
                                count++;
                        }
                }
                return methodName + (count > 0 ? "$" + count : "");
        }

        protected static void processClassFile(File classFile) {
                alreadyProcessed = new ArrayList<String>();
                callablesCreated = new HashMap<String, String>();
                InputStream is = null;

                try {
                        // get an input stream to read the bytecode of the class
                        is = new FileInputStream(classFile);
                        ClassNode cn = new ClassNode(ASM4);
                        ClassReader cr = new ClassReader(is);
                        cr.accept(cn, 0);

                        List<MethodNode> parNestedMethods = new ArrayList<MethodNode>();
                        MethodNode combinerMethod = null;
                        MethodNode execMethod = null;

                        List<MethodNode> staticMethodsToAdd = new ArrayList<MethodNode>();

                        boolean parallelSpawn = extendsParallelSpawn(cn);
                        boolean unsafeSpawn = extendsUnsafeSpawn(cn);
                        if (parallelSpawn || unsafeSpawn) {
                                Iterator<MethodNode> methodIter = cn.methods.iterator();
                                while (methodIter.hasNext()) {
                                        MethodNode mn = methodIter.next();
                                        if (mn.name.equals("exec") && execMethod == null) {
                                                execMethod = mn;
                                                continue;
                                        }
                                        if (mn.invisibleAnnotations == null) {
                                                continue;
                                        }
                                        for (AnnotationNode an : mn.invisibleAnnotations) {
                                                if (an.desc.equals(PAR_NEST.getDescriptor())) {
                                                        // Ensure the method can be called from outside
                                                        mn.access = (mn.access & ~ACC_PRIVATE) | ACC_PUBLIC;
                                                        parNestedMethods.add(mn);
                                                        String uniqueMethodName = createUniqueMethodName(mn.name);
                                                        String callableClass;
                                                        if (parallelSpawn) {
                                                                callableClass = cn.name + "$nested$work$unit$" + uniqueMethodName;
                                                        } else {
                                                                callableClass = cn.name + "$unsafe$work$unit$" + uniqueMethodName;
                                                        }
                                                        callablesCreated.put(mn.name, callableClass);
                                                        boolean readOnlyCallable = ( an.values == null ) ? false : (Boolean) an.values.get(1);
                                                        generateCallable(classFile, cn.name, callableClass, mn, readOnlyCallable, unsafeSpawn);
                                                        staticMethodsToAdd.add(generateStaticCallableCreation(cn, cn.name, callableClass, mn));
                                                        break;
                                                } else if (an.desc.equals(COMBINER.getDescriptor())) {
                                                        if (combinerMethod != null) {
                                                                throw new RuntimeException("Class: " + cn.name + " contains two @Combiner methods: "
                                                                                + combinerMethod.name + " and " + mn.name);
                                                        }
                                                        combinerMethod = mn;
                                                }
                                        }
                                }

                                // TODO Verify the @Combiner method
                                // The return should be of the same type of the parameterization
                                // of the ParallelSpawn

                                for (MethodNode methodToAdd : staticMethodsToAdd) {
                                        cn.methods.add(methodToAdd);
                                }

                                if (alreadyProcessed.size() == 0) {
                                        throw new RuntimeException("Class: " + cn.name + " must have at least one method annotated with @ParNested");
                                }
                                if (combinerMethod == null) {
                                        throw new RuntimeException("Class: " + cn.name + " must have one method annotated with @Combiner");
                                }

                                List<Integer> localVariablesIdx = new ArrayList<Integer>();
                                int numberLocalVariables = 0;
                                int listIndex = execMethod.maxLocals;
                                execMethod.maxLocals++;

                                InsnList preamble = new InsnList();
                                preamble.add(new TypeInsnNode(NEW, ARRAY_LIST.getInternalName()));
                                preamble.add(new InsnNode(DUP));
                                preamble.add(new MethodInsnNode(INVOKESPECIAL, ARRAY_LIST.getInternalName(), "<init>", "()V"));
                                preamble.add(new VarInsnNode(ASTORE, listIndex));

                                Iterator<AbstractInsnNode> execInstIter = execMethod.instructions.iterator();
                                while (execInstIter.hasNext()) {
                                        AbstractInsnNode instr = execInstIter.next();
                                        // Look out for calls to methods
                                        if (instr.getOpcode() == INVOKEVIRTUAL || instr.getOpcode() == INVOKESPECIAL) {
                                                MethodInsnNode methodInstr = (MethodInsnNode) instr;
                                                // Is method being called annotated with @ParNested
                                                for (MethodNode parNestedMethod : parNestedMethods) {
                                                        if (parNestedMethod.name.equals(methodInstr.name)) {
                                                                numberLocalVariables++;
                                                        }
                                                }
                                        }
                                }

                                for (int i = 0; i < numberLocalVariables; i++) {
                                        localVariablesIdx.add(i, execMethod.maxLocals);
                                        execMethod.maxLocals++;
                                }

                                int callablesManipulated = 0;
                                execInstIter = execMethod.instructions.iterator();
                                while (execInstIter.hasNext()) {
                                        AbstractInsnNode instr = execInstIter.next();
                                        // Look out for calls to methods
                                        if (instr.getOpcode() != INVOKEVIRTUAL && instr.getOpcode() != INVOKESPECIAL) {
                                                continue;
                                        }

                                        MethodInsnNode methodInstr = (MethodInsnNode) instr;
                                        // Is method being called annotated with @ParNested
                                        boolean isParNestedMethod = false;
                                        for (MethodNode parNestedMethod : parNestedMethods) {
                                                if (parNestedMethod.name.equals(methodInstr.name)) {
                                                        isParNestedMethod = true;
                                                        break;
                                                }
                                        }
                                        if (!isParNestedMethod) {
                                                continue;
                                        }

                                        // Let's change this call
                                        // If it was a call to: @ParNested public int add(int i1,
                                        // int i2)
                                        // add(foo, bar) -> add$static$callable$creator(this, foo,
                                        // bar)
                                        // the 'this' will be already in the right place in the
                                        // stack
                                        // because the method being called now is static whereas
                                        // previously
                                        // it was not
                                        methodInstr.setOpcode(INVOKESTATIC);
                                        methodInstr.name = methodInstr.name + "$static$callable$creator";
                                        for (MethodNode staticCreated : staticMethodsToAdd) {
                                                if (staticCreated.name.equals(methodInstr.name)) {
                                                        methodInstr.desc = staticCreated.desc;
                                                        break;
                                                }
                                        }

                                        InsnList midterm = new InsnList();

                                        // Store the callable instantiated in local variable
                                        midterm.add(new VarInsnNode(ASTORE, localVariablesIdx.get(callablesManipulated)));
                                        // Load the list
                                        midterm.add(new VarInsnNode(ALOAD, listIndex));
                                        // Load the callable
                                        midterm.add(new VarInsnNode(ALOAD, localVariablesIdx.get(callablesManipulated)));
                                        // Add it to the list
                                        midterm.add(new MethodInsnNode(INVOKEVIRTUAL, ARRAY_LIST.getInternalName(), "add", "(Ljava/lang/Object;)Z"));
                                        // Pop the boolean that results from the add(Object)
                                        // May reuse a POP if the previous call had a return
                                        if (methodInstr.getNext().getOpcode() != POP) {
                                                midterm.add(new InsnNode(POP));
                                        }

                                        // Add this set of instructions after the call to the
                                        // constrution of the callable
                                        execMethod.instructions.insert(methodInstr, midterm);
                                        callablesManipulated++;

                                }

                                // Insert the preamble in the start
                                execMethod.instructions.insert(preamble);

                                InsnList finish = new InsnList();
                                // Push 'this' for the call to the combiner method
                                finish.add(new VarInsnNode(ALOAD, 0));
                                // Call the static method current() of jvstm.Transaction
                                finish.add(new MethodInsnNode(INVOKESTATIC, TRANSACTION.getInternalName(), "current", "()Ljvstm/Transaction;"));
                                // Load the callables list
                                finish.add(new VarInsnNode(ALOAD, listIndex));
                                // Call the manage parnested method
                                finish.add(new MethodInsnNode(INVOKEVIRTUAL, TRANSACTION.getInternalName(), "manageNestedParallelTxs",
                                                "(Ljava/util/List;)Ljava/util/List;"));
                                // Call the combiner method
                                finish.add(new MethodInsnNode(INVOKEVIRTUAL, cn.name, combinerMethod.name, combinerMethod.desc));
                                // Return what the combiner returns
                                finish.add(new InsnNode(ARETURN));

                                // Remove the "return null" that's supposed to be at the end of
                                // the exec method
                                execInstIter = execMethod.instructions.iterator();
                                while (execInstIter.hasNext()) {
                                        AbstractInsnNode curNode = execInstIter.next();
                                        if (!execInstIter.hasNext()) {
                                                // Insert the finish in the end
                                                execMethod.instructions.insert(curNode.getPrevious().getPrevious(), finish);
                                                execMethod.instructions.remove(curNode.getPrevious());
                                                execMethod.instructions.remove(curNode);
                                                break;
                                        }
                                }

                        }

                        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                        cn.accept(cw);
                        writeClassFile(classFile, cw.toByteArray());
                } catch (IOException e) {
                        throw new Error("Error processing class file", e);
                } finally {
                        if (is != null) {
                                try {
                                        is.close();
                                } catch (IOException e) {
                                }
                        }
                }
        }

        private static boolean isStatic(MethodNode mn) {
                return (mn.access & ACC_STATIC) > 0;
        }

        private static boolean isPrimitive(Type type) {
                int sort = type.getSort();
                return sort != Type.VOID && sort != Type.ARRAY && sort != Type.OBJECT && sort != Type.METHOD;
        }

        private static final Object[][] primitiveWrappers = new Object[][] { { "java/lang/Boolean", Type.BOOLEAN_TYPE },
                { "java/lang/Byte", Type.BYTE_TYPE }, { "java/lang/Character", Type.CHAR_TYPE },
                { "java/lang/Short", Type.SHORT_TYPE }, { "java/lang/Integer", Type.INT_TYPE }, { "java/lang/Long", Type.LONG_TYPE },
                { "java/lang/Float", Type.FLOAT_TYPE }, { "java/lang/Double", Type.DOUBLE_TYPE } };

        private static Type toObject(Type primitiveType) {
                for (Object[] map : primitiveWrappers) {
                        if (primitiveType.equals(map[1]))
                                return Type.getObjectType((String) map[0]);
                }
                throw new AssertionError();
        }

        private static String getCallableCtorDesc(String className, MethodNode mn) {
                List<Type> callableCtorDescList = new ArrayList<Type>();
                if (!isStatic(mn))
                        callableCtorDescList.add(Type.getObjectType(className));
                callableCtorDescList.addAll(Arrays.asList(Type.getArgumentTypes(mn.desc)));
                String callableCtorDesc = Type.getMethodDescriptor(Type.VOID_TYPE, callableCtorDescList.toArray(new Type[0]));
                return callableCtorDesc;
        }

        private static MethodNode generateStaticCallableCreation(ClassNode classNode, String className, String callableClass,
                        MethodNode mn) {
                MethodNode staticMethod = new MethodNode(V1_6, mn.access | ACC_STATIC, mn.name + "$static$callable$creator", "(L"
                                + className + ";" + mn.desc.substring(1, mn.desc.indexOf(')') + 1) + "L" + callableClass + ";", mn.signature,
                                new String[0]);
                InsnList content = new InsnList();
                content.add(new TypeInsnNode(NEW, callableClass));
                content.add(new InsnNode(DUP));

                int pos = 0;
                // Push the instance of the class being modified (first argument of this
                // synthetized method)
                content.add(new VarInsnNode(ALOAD, pos++));
                // Push arguments of original method on the stack for callable creation
                for (Type t : Type.getArgumentTypes(mn.desc)) {
                        content.add(new VarInsnNode(t.getOpcode(ILOAD), pos));
                        pos += t.getSize();
                }

                // Instantiate the callable
                content.add(new MethodInsnNode(INVOKESPECIAL, callableClass, "<init>", getCallableCtorDesc(className, mn)));

                // Return it from the static method
                content.add(new InsnNode(ARETURN));

                staticMethod.instructions.add(content);
                return staticMethod;
        }

        private static void generateCallable(File classFile, String className, String callableClass, MethodNode mn, boolean readOnly, boolean unsafe) {
                Type returnType = Type.getReturnType(mn.desc);

                List<Type> arguments = new ArrayList<Type>(Arrays.asList(Type.getArgumentTypes(mn.desc)));
                if (!isStatic(mn))
                        arguments.add(0, Type.getObjectType(className));

                ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                cw.visit(
                                V1_6,
                                ACC_FINAL,
                                callableClass,
                                unsafe ? "Ljvstm/UnsafeParallelTask<" : "Ljvstm/ParallelTask<"
                                                + (isPrimitive(returnType) ? toObject(returnType) : (returnType.equals(Type.VOID_TYPE) ? Type
                                                                .getObjectType("java/lang/Void") : returnType)).getDescriptor() + ">;",
                                                                unsafe ? "jvstm/UnsafeParallelTask" : "jvstm/ParallelTask", new String[] {});
                cw.visitSource("JVSTM Generated Wrapper Class", null);

                // Create fields to hold arguments
                {
                        int fieldPos = 0;
                        for (Type t : arguments) {
                                cw.visitField(ACC_PRIVATE | ACC_FINAL, "arg" + (fieldPos++), t.getDescriptor(), null, null);
                        }
                }

                // Create constructor
                {
                        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", getCallableCtorDesc(className, mn), null, null);
                        mv.visitCode();
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitMethodInsn(INVOKESPECIAL, unsafe ? "jvstm/UnsafeParallelTask" : "jvstm/ParallelTask", "<init>", "()V");
                        int localsPos = 0;
                        int fieldPos = 0;
                        for (Type t : arguments) {
                                mv.visitVarInsn(ALOAD, 0);
                                mv.visitVarInsn(t.getOpcode(ILOAD), localsPos + 1);
                                mv.visitFieldInsn(PUTFIELD, callableClass, "arg" + fieldPos++, t.getDescriptor());
                                localsPos += t.getSize();
                        }
                        mv.visitInsn(RETURN);
                        mv.visitMaxs(0, 0);
                        mv.visitEnd();
                }

                // Create execute method
                {
                        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "execute", "()Ljava/lang/Object;", null, null);
                        mv.visitCode();
                        int fieldPos = 0;
                        for (Type t : arguments) {
                                mv.visitVarInsn(ALOAD, 0);
                                mv.visitFieldInsn(GETFIELD, callableClass, "arg" + fieldPos++, t.getDescriptor());
                        }
                        mv.visitMethodInsn(isStatic(mn) ? INVOKESTATIC : INVOKEVIRTUAL, className, mn.name, mn.desc);
                        if (returnType.equals(Type.VOID_TYPE))
                                mv.visitInsn(ACONST_NULL);
                        else if (isPrimitive(returnType))
                                boxWrap(returnType, mv);
                        mv.visitInsn(ARETURN);
                        mv.visitMaxs(0, 0);
                        mv.visitEnd();
                }

                // Create the readOnly method
                {
                        if (readOnly) {
                                MethodVisitor mv = cw.visitMethod(ACC_PROTECTED, "isReadOnly", "()Z", null, null);
                                mv.visitCode();
                                mv.visitInsn(ICONST_0);
                                mv.visitInsn(IRETURN);
                                mv.visitMaxs(0, 0);
                                mv.visitEnd();
                        }
                }

                /*    protected boolean isReadOnly() {
        return false;
    }*/

                // Write the callable class file in the same directory as the original
                // class file
                String callableFileName = callableClass.substring(Math.max(callableClass.lastIndexOf('/'), 0)) + ".class";
                writeClassFile(new File((classFile.getParent() == null ? "" : classFile.getParent() + File.separatorChar)
                                + callableFileName), cw.toByteArray());
        }

        private static void boxWrap(Type primitiveType, MethodVisitor mv) {
                Type objectType = toObject(primitiveType);
                mv.visitMethodInsn(INVOKESTATIC, objectType.getInternalName(), "valueOf", "(" + primitiveType.getDescriptor() + ")"
                                + objectType.getDescriptor());
        }

        private static boolean extendsParallelSpawn(ClassNode cn) {
                // TODO Support extending ParallelSpawn at multiple levels, the
                // issue at the moment is that the exec() method could be
                // elsewhere...
                // Plus, if we attempt to check on a class that extends some
                // class whose .class is not in the project (Thread.class for instance)
                // we run into problems
                for (String implementedInterfaceName : cn.interfaces) {
                        if (implementedInterfaceName.equals(PARALLEL_SPAWN.getInternalName())) {
                                return true;
                        }
                }
                return false;
        }

        private static boolean extendsUnsafeSpawn(ClassNode cn) {
                for (String implementedInterfaceName : cn.interfaces) {
                        if (implementedInterfaceName.equals(UNSAFE_SPAWN.getInternalName())) {
                                return true;
                        }
                }
                return false;
        }

        protected static void writeClassFile(File classFile, byte[] bytecode) {
                FileOutputStream fos = null;
                try {
                        fos = new FileOutputStream(classFile);
                        fos.write(bytecode);
                } catch (IOException e) {
                        throw new Error("Couldn't write class file", e);
                } finally {
                        if (fos != null) {
                                try {
                                        fos.close();
                                } catch (IOException e) {
                                }
                        }
                }
        }

        static class ParNestMethodTransformer extends ClassVisitor {
                private final List<MethodNode> methods = new ArrayList<MethodNode>();
                private final List<String> parNestMethodNames = new ArrayList<String>();

                private final MethodNode atomicClInit;
                private final File classFile;

                private String className;

                public ParNestMethodTransformer(ClassVisitor cv, File originalClassFile) {
                        super(ASM4, cv);

                        classFile = originalClassFile;

                        atomicClInit = new MethodNode(ACC_STATIC, "<clinit>", "()V", null, null);
                        atomicClInit.visitCode();
                }

                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                        className = name;
                        System.err.println("Class: " + name);
                        cv.visit(version, access, name, signature, superName, interfaces);
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                        // Use a MethodNode to represent the method
                        MethodNode mn = new MethodNode(access, name, desc, signature, exceptions);
                        methods.add(mn);
                        return mn;
                }

                @Override
                public void visitInnerClass(String name, String outerName, String innerName, int access) {
                        System.err.println("Inner Class: " + name + " inner name: " + innerName + " outer: " + outerName);
                        InnerClassNode n = new InnerClassNode(name, outerName, innerName, access);
                        cv.visitInnerClass(name, outerName, innerName, access);
                }

                @Override
                public void visitEnd() {
                        MethodNode clInit = null;
                        boolean hasParNest = false;
                        for (MethodNode mn : methods) {
                                if (mn.name.equals("<clinit>")) {
                                        clInit = mn;
                                        continue;
                                }

                                if (mn.invisibleAnnotations != null) {
                                        for (AnnotationNode an : mn.invisibleAnnotations) {
                                                if (an.desc.equals(PAR_NEST.getDescriptor())) {
                                                        System.out.println("Method " + mn.name + " is tagged with @ParNest");
                                                        hasParNest = true;
                                                        // Create new transactified method
                                                        // transactify(mn, an);
                                                        break;
                                                }
                                        }
                                }
                                // Visit method, so it will be present on the output class
                                mn.accept(cv);
                        }

                        if (hasParNest) {
                                // Insert <clinit> into class
                                if (clInit != null) {
                                        // Merge existing clinit with our additions
                                        clInit.instructions.accept(atomicClInit);
                                } else {
                                        atomicClInit.visitInsn(RETURN);
                                }
                                atomicClInit.visitMaxs(0, 0);
                                atomicClInit.visitEnd();
                                atomicClInit.accept(cv);
                        } else {
                                // Preserve existing <clinit>
                                if (clInit != null)
                                        clInit.accept(cv);
                        }

                        cv.visitEnd();
                }

                private String getMethodName(String methodName) {
                        // Count number of atomic methods with same name
                        int count = 0;
                        for (String name : parNestMethodNames) {
                                if (name.equals(methodName))
                                        count++;
                        }
                        // Add another one
                        parNestMethodNames.add(methodName);

                        return methodName + (count > 0 ? "$" + count : "");
                }
        }

}

