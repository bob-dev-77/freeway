package com.jujin.freeway.ioc.internal;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates proxy instances using JDK 25 {@code ClassFile} API to generate hidden
 * classes that implement the service interface directly.
 *
 * <p>
 * Each method body calls its pre-composed {@link MethodHandle} inline,
 * eliminating the {@code InvocationHandler} indirection of
 * {@code java.lang.reflect.Proxy}. The generated class name is
 * {@code <InterfaceName>$Proxy}, making stack traces readable.
 * </p>
 *
 * <p>
 * Generated classes are cached by interface for reuse.
 * </p>
 */
final class ClassFileProxyGenerator {

    static final String PROXY_CLASS_SUFFIX = "$Proxy";

    private static final Map<Class<?>, WeakReference<Constructor<?>>> constructorCache = new ConcurrentHashMap<>();
    private static final Object constructorLock = new Object();

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private static final ClassDesc CD_OBJECT = ClassDesc.of("java.lang.Object");
    private static final ClassDesc CD_STRING = ClassDesc.of("java.lang.String");
    private static final ClassDesc CD_METHOD_HANDLE = ClassDesc.of(
        "java.lang.invoke.MethodHandle");
    private static final ClassDesc CD_MH_ARRAY = CD_METHOD_HANDLE.arrayType();
    private static final MethodTypeDesc MTD_VOID = MethodTypeDesc.ofDescriptor(
        "()V");
    private static final MethodTypeDesc MTD_TO_STRING = MethodTypeDesc.ofDescriptor("()Ljava/lang/String;");
    private static final MethodTypeDesc MTD_HASHCODE = MethodTypeDesc.ofDescriptor("()I");
    private static final MethodTypeDesc MTD_EQUALS = MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)Z");
    private static final MethodTypeDesc MTD_HANDLE_INVOKE = MethodTypeDesc
        .ofDescriptor("([Ljava/lang/Object;)Ljava/lang/Object;");

    private static final MethodTypeDesc MTD_CTOR = MethodTypeDesc.ofDescriptor(
        "([Ljava/lang/invoke/MethodHandle;Ljava/lang/Object;Ljava/lang/String;)V");

    private ClassFileProxyGenerator() {}

    @SuppressWarnings("unchecked")
    static <T> T createProxy(
        Class<T> interfaceType,
        Object delegate,
        MethodHandle[] handles,
        String description) {
        Method[] methods = interfaceType.getMethods();
        if (handles.length != methods.length) {
            throw new IllegalArgumentException(
                "Handle count " +
                    handles.length +
                    " != method count " +
                    methods.length);
        }

        Constructor<?> ctor = getOrCreateConstructor(interfaceType, methods);
        try {
            return (T) ctor.newInstance(handles, delegate, description);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(
                "Failed to instantiate proxy for " + interfaceType.getName(),
                e);
        }
    }

    private static Constructor<?> getOrCreateConstructor(
        Class<?> interfaceType,
        Method[] methods) {
        var ref = constructorCache.get(interfaceType);
        Constructor<?> ctor = ref != null ? ref.get() : null;
        if (ctor != null)
            return ctor;

        synchronized (constructorLock) {
            ref = constructorCache.get(interfaceType);
            ctor = ref != null ? ref.get() : null;
            if (ctor != null)
                return ctor;

            String className = interfaceType.getSimpleName() + PROXY_CLASS_SUFFIX;
            ClassDesc thisDesc = ClassDesc.of(className);
            byte[] classBytes = generateClassBytes(
                thisDesc,
                interfaceType,
                methods);

            try {
                MethodHandles.Lookup classLookup = LOOKUP.defineHiddenClass(
                    classBytes,
                    true);
                Class<?> hiddenClass = classLookup.lookupClass();
                ctor = hiddenClass.getConstructor(
                    MethodHandle[].class,
                    Object.class,
                    String.class);
                constructorCache.put(interfaceType, new WeakReference<>(ctor));
                return ctor;
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(
                    "Failed to resolve constructor for " +
                        interfaceType.getName(),
                    e);
            }
        }
    }

    /**
     * Generates bytecode for a hidden class implementing the given interface.
     */
    private static byte[] generateClassBytes(
        ClassDesc thisDesc,
        Class<?> interfaceType,
        Method[] methods) {
        ClassDesc ifaceDesc = ClassDesc.ofDescriptor(
            interfaceType.descriptorString());

        return ClassFile.of().build(thisDesc, classBuilder -> {
            classBuilder.withFlags(
                ClassFile.ACC_FINAL |
                    ClassFile.ACC_SYNTHETIC |
                    ClassFile.ACC_SUPER);
            classBuilder.withInterfaceSymbols(ifaceDesc);

            // Fields: handles, delegate, description
            classBuilder.withField(
                "handles",
                CD_MH_ARRAY,
                ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);
            classBuilder.withField(
                "delegate",
                CD_OBJECT,
                ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);
            classBuilder.withField(
                "description",
                CD_STRING,
                ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);

            // Constructor
            generateConstructor(classBuilder, thisDesc);

            // Object methods
            generateToString(classBuilder, thisDesc);
            generateHashCode(classBuilder);
            generateEquals(classBuilder);

            // Interface methods (skip Object methods declared on the interface)
            for (int i = 0; i < methods.length; i++) {
                Method m = methods[i];
                if (m.getDeclaringClass() == Object.class)
                    continue;
                generateMethod(classBuilder, thisDesc, m, i);
            }
        });
    }

    // ── Constructor ──────────────────────────────────────────────

    private static void generateConstructor(
        ClassBuilder classBuilder,
        ClassDesc thisDesc) {
        classBuilder.withMethod(
            "<init>",
            MTD_CTOR,
            ClassFile.ACC_PUBLIC,
            methodBuilder -> methodBuilder.withCode(codeBuilder -> {
                codeBuilder.aload(0);
                codeBuilder.invokespecial(CD_OBJECT, "<init>", MTD_VOID);

                codeBuilder.aload(0);
                codeBuilder.aload(1);
                codeBuilder.putfield(thisDesc, "handles", CD_MH_ARRAY);

                codeBuilder.aload(0);
                codeBuilder.aload(2);
                codeBuilder.putfield(thisDesc, "delegate", CD_OBJECT);

                codeBuilder.aload(0);
                codeBuilder.aload(3);
                codeBuilder.putfield(thisDesc, "description", CD_STRING);

                codeBuilder.return_();
            }));
    }

    // ── Object methods ───────────────────────────────────────────

    private static void generateToString(
        ClassBuilder classBuilder,
        ClassDesc thisDesc) {
        classBuilder.withMethod(
            "toString",
            MTD_TO_STRING,
            ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
            methodBuilder -> methodBuilder.withCode(codeBuilder -> {
                codeBuilder.aload(0);
                codeBuilder.getfield(thisDesc, "description", CD_STRING);
                codeBuilder.areturn();
            }));
    }

    private static void generateHashCode(ClassBuilder classBuilder) {
        classBuilder.withMethod(
            "hashCode",
            MTD_HASHCODE,
            ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
            methodBuilder -> methodBuilder.withCode(codeBuilder -> {
                codeBuilder.aload(0);
                codeBuilder.invokestatic(
                    ClassDesc.of("java.lang.System"),
                    "identityHashCode",
                    MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)I"));
                codeBuilder.ireturn();
            }));
    }

    private static void generateEquals(ClassBuilder classBuilder) {
        classBuilder.withMethod(
            "equals",
            MTD_EQUALS,
            ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
            methodBuilder -> methodBuilder.withCode(codeBuilder -> {
                codeBuilder.aload(0);
                codeBuilder.aload(1);
                codeBuilder.if_acmpeq(codeBuilder.newLabel());
                codeBuilder.iconst_0();
                codeBuilder.ireturn();
                codeBuilder.labelBinding(codeBuilder.newLabel());
                codeBuilder.iconst_1();
                codeBuilder.ireturn();
            }));
    }

    // ── Interface methods ────────────────────────────────────────

    /**
     * Generates a method body:
     * 
     * <pre>
     * RetType method(P1 p1, P2 p2, ...) {
     *     Object[] a = new Object[slotCount];
     *     a[0] = delegate;
     *     a[1] = p1; a[2] = p2; ...
     *     Object result = handles[i].invoke(a);
     *     return (RetType) result;  // or void
     * }
     * </pre>
     */
    private static void generateMethod(
        ClassBuilder classBuilder,
        ClassDesc thisDesc,
        Method method,
        int methodIndex) {
        Class<?>[] paramTypes = method.getParameterTypes();
        Class<?> returnType = method.getReturnType();
        int slotCount = paramTypes.length + 1; // +1 for delegate
        boolean returnsVoid = returnType == void.class;

        // Build method descriptor
        ClassDesc[] paramDescs = new ClassDesc[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            paramDescs[i] = ClassDesc.ofDescriptor(
                paramTypes[i].descriptorString());
        }
        ClassDesc retDesc = returnsVoid
            ? ClassDesc.ofDescriptor("V")
            : ClassDesc.ofDescriptor(returnType.descriptorString());
        MethodTypeDesc mtDesc = MethodTypeDesc.of(retDesc, paramDescs);

        classBuilder.withMethod(
            method.getName(),
            mtDesc,
            ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
            methodBuilder -> methodBuilder.withCode(codeBuilder -> {
                // new Object[slotCount]
                pushInt(codeBuilder, slotCount);
                codeBuilder.anewarray(CD_OBJECT);
                // Stack: Object[]

                // args[0] = this.delegate
                codeBuilder.dup();
                codeBuilder.iconst_0();
                codeBuilder.aload(0);
                codeBuilder.getfield(thisDesc, "delegate", CD_OBJECT);
                codeBuilder.aastore();

                // args[i+1] = pi (box primitives)
                for (int i = 0; i < paramTypes.length; i++) {
                    codeBuilder.dup();
                    pushInt(codeBuilder, i + 1);
                    codeBuilder.aload(i + 1);
                    if (paramTypes[i].isPrimitive()) {
                        boxPrimitive(codeBuilder, paramTypes[i]);
                    }
                    codeBuilder.aastore();
                }

                // args is now on the stack; duplicate for the handle call
                // Load handle and swap so handle is under args
                codeBuilder.aload(0);
                codeBuilder.getfield(thisDesc, "handles", CD_MH_ARRAY);
                pushInt(codeBuilder, methodIndex);
                codeBuilder.aaload(); // Stack: Object[], MethodHandle
                codeBuilder.swap(); // Stack: MethodHandle, Object[]

                // handle.invoke(Object[])
                codeBuilder.invokevirtual(
                    CD_METHOD_HANDLE,
                    "invoke",
                    MTD_HANDLE_INVOKE);

                // Return
                if (returnsVoid) {
                    codeBuilder.pop();
                    codeBuilder.return_();
                } else if (returnType.isPrimitive()) {
                    unboxAndReturn(codeBuilder, returnType);
                } else {
                    codeBuilder.checkcast(
                        ClassDesc.ofDescriptor(
                            returnType.descriptorString()));
                    codeBuilder.areturn();
                }
            }));
    }

    // ── Int constants ────────────────────────────────────────────

    private static void pushInt(CodeBuilder codeBuilder, int value) {
        switch (value) {
            case 0 -> codeBuilder.iconst_0();
            case 1 -> codeBuilder.iconst_1();
            case 2 -> codeBuilder.iconst_2();
            case 3 -> codeBuilder.iconst_3();
            case 4 -> codeBuilder.iconst_4();
            case 5 -> codeBuilder.iconst_5();
            default -> {
                if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
                    codeBuilder.bipush(value);
                } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
                    codeBuilder.sipush(value);
                } else {
                    codeBuilder.ldc(value);
                }
            }
        }
    }

    // ── Boxing / Unboxing ────────────────────────────────────────

    private static void boxPrimitive(CodeBuilder codeBuilder, Class<?> type) {
        ClassDesc wrapper = wrapperDesc(type);
        String sig = primitiveSig(type);
        codeBuilder.invokestatic(
            wrapper,
            "valueOf",
            MethodTypeDesc.of(wrapper, ClassDesc.ofDescriptor(sig)));
    }

    private static void unboxAndReturn(CodeBuilder codeBuilder, Class<?> type) {
        ClassDesc wrapper = wrapperDesc(type);
        String methodName = unboxMethod(type);
        String sig = primitiveSig(type);

        codeBuilder.checkcast(wrapper);
        codeBuilder.invokevirtual(
            wrapper,
            methodName,
            MethodTypeDesc.ofDescriptor("()" + sig));

        switch (sig) {
            case "I" -> codeBuilder.ireturn();
            case "J" -> codeBuilder.lreturn();
            case "F" -> codeBuilder.freturn();
            case "D" -> codeBuilder.dreturn();
            case "Z", "B", "S", "C" -> codeBuilder.ireturn();
            default -> codeBuilder.areturn();
        }
    }

    private static ClassDesc wrapperDesc(Class<?> type) {
        if (type == int.class)
            return ClassDesc.of("java.lang.Integer");
        if (type == long.class)
            return ClassDesc.of("java.lang.Long");
        if (type == boolean.class)
            return ClassDesc.of("java.lang.Boolean");
        if (type == double.class)
            return ClassDesc.of("java.lang.Double");
        if (type == float.class)
            return ClassDesc.of("java.lang.Float");
        if (type == short.class)
            return ClassDesc.of("java.lang.Short");
        if (type == byte.class)
            return ClassDesc.of("java.lang.Byte");
        if (type == char.class)
            return ClassDesc.of("java.lang.Character");
        throw new IllegalArgumentException("Not a primitive: " + type);
    }

    private static String primitiveSig(Class<?> type) {
        if (type == int.class)
            return "I";
        if (type == long.class)
            return "J";
        if (type == boolean.class)
            return "Z";
        if (type == double.class)
            return "D";
        if (type == float.class)
            return "F";
        if (type == short.class)
            return "S";
        if (type == byte.class)
            return "B";
        if (type == char.class)
            return "C";
        throw new IllegalArgumentException("Not a primitive: " + type);
    }

    private static String unboxMethod(Class<?> type) {
        if (type == int.class)
            return "intValue";
        if (type == long.class)
            return "longValue";
        if (type == boolean.class)
            return "booleanValue";
        if (type == double.class)
            return "doubleValue";
        if (type == float.class)
            return "floatValue";
        if (type == short.class)
            return "shortValue";
        if (type == byte.class)
            return "byteValue";
        if (type == char.class)
            return "charValue";
        throw new IllegalArgumentException("Not a primitive: " + type);
    }
}
