package com.ai.llm;

import org.springframework.stereotype.Service;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RuntimeBytecodeService {

    private static final Map<String, Object> dynamicInstances = new ConcurrentHashMap<>();
    private static final Map<String, Class<?>> dynamicClasses = new ConcurrentHashMap<>();
    private static Instrumentation instrumentation;

    static {
        try {
            instrumentation = ByteBuddyAgent.install();
        } catch (Exception e) {
            System.err.println("Failed to install ByteBuddy agent: " + e.getMessage());
        }
    }

    /**
     * Compile and load Java source code at runtime
     */
    public CompilationResult compileAndLoadJavaCode(String className, String javaSource) {
        try {
            // Compile Java source to bytecode
            byte[] bytecode = compileJavaSource(className, javaSource);

            // Load the class dynamically
            Class<?> dynamicClass = loadClass(className, bytecode);

            // Create instance
            Object instance = dynamicClass.getDeclaredConstructor().newInstance();

            // Store for later use
            dynamicClasses.put(className, dynamicClass);
            dynamicInstances.put(className, instance);

            return new CompilationResult(true, "Class loaded successfully", dynamicClass, instance);

        } catch (Exception e) {
            return new CompilationResult(false, "Compilation failed: " + e.getMessage(), null, null);
        }
    }

    /**
     * Modify existing method using ByteBuddy
     */
    public ModificationResult modifyMethod(String className, String methodName, String newImplementation) {
        try {
            Class<?> targetClass = Class.forName(className);

            // Create interceptor class for the new implementation
            String interceptorSource = createInterceptorClass(methodName, newImplementation);
            CompilationResult interceptorResult = compileAndLoadJavaCode("Interceptor_" + System.currentTimeMillis(), interceptorSource);

            if (!interceptorResult.success()) {
                return new ModificationResult(false, "Failed to compile interceptor: " + interceptorResult.message());
            }

            // Apply transformation using ByteBuddy
            new ByteBuddy()
                    .redefine(targetClass)
                    .method(ElementMatchers.named(methodName))
                    .intercept(MethodDelegation.to(interceptorResult.instance()))
                    .make()
                    .load(targetClass.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());

            return new ModificationResult(true, "Method modified successfully");

        } catch (Exception e) {
            return new ModificationResult(false, "Modification failed: " + e.getMessage());
        }
    }

    /**
     * Execute method on dynamic class
     */
    public ExecutionResult executeMethod(String className, String methodName, Object... args) {
        try {
            Object instance = dynamicInstances.get(className);
            if (instance == null) {
                return new ExecutionResult(false, "Class not found: " + className, null);
            }

            Class<?> clazz = instance.getClass();
            Method method = findMethod(clazz, methodName, args);

            if (method == null) {
                return new ExecutionResult(false, "Method not found: " + methodName, null);
            }

            Object result = method.invoke(instance, args);
            return new ExecutionResult(true, "Execution successful", result);

        } catch (Exception e) {
            return new ExecutionResult(false, "Execution failed: " + e.getMessage(), null);
        }
    }

    /**
     * Generate ASM code for bytecode transformation
     */
    public String generateAsmCode(String javaCode, String optimizationGoal) {
        // This would use ASM's ClassReader/ClassWriter to generate transformation code
        // For now, return a template
        return String.format("""
            // ASM Transformation for: %s
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "optimizedMethod", "()V", null, null);
            mv.visitCode();
            
            // Optimization: %s
            // Original code: %s
            
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            """, optimizationGoal, optimizationGoal, javaCode);
    }

    // Helper methods
    private byte[] compileJavaSource(String className, String source) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new RuntimeException("No Java compiler available. Make sure you're running with JDK, not JRE.");
        }

        JavaFileManager fileManager = new InMemoryJavaFileManager(
                compiler.getStandardFileManager(null, null, null));

        JavaFileObject sourceFile = new InMemoryJavaFileObject(className, source);

        Iterable<? extends JavaFileObject> compilationUnits = Arrays.asList(sourceFile);
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, null, null, null, compilationUnits);

        if (!task.call()) {
            throw new RuntimeException("Compilation failed");
        }

        return ((InMemoryJavaFileManager) fileManager).getCompiledBytes(className);
    }

    private Class<?> loadClass(String className, byte[] bytecode) throws Exception {
        ClassLoader classLoader = new ByteArrayClassLoader(bytecode, className);
        return classLoader.loadClass(className);
    }

    private Method findMethod(Class<?> clazz, String methodName, Object[] args) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == args.length) {
                return method;
            }
        }
        return null;
    }

    private String createInterceptorClass(String methodName, String implementation) {
        return String.format("""
            public class Interceptor_%s {
                public static Object intercept() {
                    %s
                }
            }
            """, System.currentTimeMillis(), implementation);
    }

    // Helper classes for in-memory compilation
    private static class ByteArrayClassLoader extends ClassLoader {
        private final byte[] bytecode;
        private final String className;

        public ByteArrayClassLoader(byte[] bytecode, String className) {
            this.bytecode = bytecode;
            this.className = className;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.equals(className)) {
                return defineClass(name, bytecode, 0, bytecode.length);
            }
            return super.findClass(name);
        }
    }

    private static class InMemoryJavaFileObject extends SimpleJavaFileObject {
        private final String source;

        public InMemoryJavaFileObject(String className, String source) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    private static class InMemoryJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {
        private final Map<String, ByteArrayOutputStream> compiledClasses = new HashMap<>();

        protected InMemoryJavaFileManager(JavaFileManager fileManager) {
            super(fileManager);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
            return new InMemoryClassFileObject(className, compiledClasses);
        }

        public byte[] getCompiledBytes(String className) {
            ByteArrayOutputStream baos = compiledClasses.get(className);
            return baos != null ? baos.toByteArray() : null;
        }
    }

    private static class InMemoryClassFileObject extends SimpleJavaFileObject {
        private final String className;
        private final Map<String, ByteArrayOutputStream> compiledClasses;

        public InMemoryClassFileObject(String className, Map<String, ByteArrayOutputStream> compiledClasses) {
            super(URI.create("bytes:///" + className), Kind.CLASS);
            this.className = className;
            this.compiledClasses = compiledClasses;
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            compiledClasses.put(className, baos);
            return baos;
        }
    }

    // Result classes
    public record CompilationResult(boolean success, String message, Class<?> clazz, Object instance) {}
    public record ModificationResult(boolean success, String message) {}
    public record ExecutionResult(boolean success, String message, Object result) {}
}