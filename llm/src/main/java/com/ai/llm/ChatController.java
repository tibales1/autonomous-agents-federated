package com.ai.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;
    private final RuntimeBytecodeService bytecodeService;

    public ChatController(ChatClient.Builder chatClientBuilder, RuntimeBytecodeService bytecodeService) {
        this.chatClient = chatClientBuilder.build();
        this.bytecodeService = bytecodeService;
    }

    @PostMapping
    public String chat(@RequestBody ChatRequest request) {
        return chatClient.prompt()
                .user(request.message())
                .call()
                .content();
    }

    @GetMapping("/test")
    public Map<String, Object> test() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Spring AI is running with HOT SWAP!");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("version", "3.0 - HOT SWAPPED!");
        return response;
    }

    @GetMapping("/hotswap-test")
    public Map<String, Object> hotSwapTest() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "✨ AUTOMATIC HOT SWAP SUCCESS! ✨");
        response.put("timestamp", LocalDateTime.now());
        response.put("feature", "Live code changes without restart");
        response.put("testNumber", "42");
        return response;
    }

    @PostMapping("/bytecode")
    public String generateBytecode(@RequestBody BytecodeRequest request) {
        String prompt = String.format("""
                You are a Java bytecode optimization expert using ASM library.
               \s
                Given this Java method:
```java
                %s
```
                
                Optimization goal: %s
           \s
                Generate the ASM code to transform this method. Follow these rules:
                1. Use ASM 9.x API syntax
                2. Show the MethodVisitor code
                3. Explain what bytecode instructions you're changing
                4. Focus on the specific optimization requested
                5. Be precise and concise
           \s
                Provide the ASM transformation code and a brief explanation.
           \s""", request.javaCode(), request.optimizationGoal());

        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    // NEW: Runtime compilation and execution
    @PostMapping("/runtime/compile")
    public Map<String, Object> compileAndRun(@RequestBody RuntimeCompilationRequest request) {
        RuntimeBytecodeService.CompilationResult result =
                bytecodeService.compileAndLoadJavaCode(request.className(), request.javaCode());

        Map<String, Object> response = new HashMap<>();
        response.put("success", result.success());
        response.put("message", result.message());
        response.put("timestamp", LocalDateTime.now());

        if (result.success()) {
            response.put("className", result.clazz().getName());
            response.put("methods", result.clazz().getDeclaredMethods().length);
        }

        return response;
    }

    // NEW: Execute method on runtime-compiled class
    @PostMapping("/runtime/execute")
    public Map<String, Object> executeMethod(@RequestBody RuntimeExecutionRequest request) {
        RuntimeBytecodeService.ExecutionResult result =
                bytecodeService.executeMethod(request.className(), request.methodName(), request.args());

        Map<String, Object> response = new HashMap<>();
        response.put("success", result.success());
        response.put("message", result.message());
        response.put("result", result.result());
        response.put("timestamp", LocalDateTime.now());

        return response;
    }

    // NEW: Modify existing method at runtime
    @PostMapping("/runtime/modify")
    public Map<String, Object> modifyMethod(@RequestBody RuntimeModificationRequest request) {
        RuntimeBytecodeService.ModificationResult result =
                bytecodeService.modifyMethod(request.className(), request.methodName(), request.newImplementation());

        Map<String, Object> response = new HashMap<>();
        response.put("success", result.success());
        response.put("message", result.message());
        response.put("timestamp", LocalDateTime.now());

        return response;
    }

    // NEW: Generate ASM transformation code
    @PostMapping("/runtime/asm-generate")
    public Map<String, Object> generateAsmTransformation(@RequestBody BytecodeRequest request) {
        String asmCode = bytecodeService.generateAsmCode(request.javaCode(), request.optimizationGoal());

        Map<String, Object> response = new HashMap<>();
        response.put("originalCode", request.javaCode());
        response.put("optimizationGoal", request.optimizationGoal());
        response.put("asmTransformation", asmCode);
        response.put("timestamp", LocalDateTime.now());

        return response;
    }
}

// Request records
record ChatRequest(String message) {}
record BytecodeRequest(String javaCode, String optimizationGoal) {}
record RuntimeCompilationRequest(String className, String javaCode) {}
record RuntimeExecutionRequest(String className, String methodName, Object[] args) {}
record RuntimeModificationRequest(String className, String methodName, String newImplementation) {}