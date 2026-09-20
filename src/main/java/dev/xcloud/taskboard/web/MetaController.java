package dev.xcloud.taskboard.web;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/meta")
public class MetaController {

    private final String release;

    public MetaController(ResourceLoader resourceLoader) {
        this.release = readRelease(resourceLoader);
    }

    @GetMapping
    public Map<String, String> meta() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("service", "deploy-test-spring-boot");
        out.put("release", release);
        out.put("javaVersion", System.getProperty("java.version"));
        out.put("runtime", "Spring Boot");
        return out;
    }

    private static String readRelease(ResourceLoader loader) {
        Resource resource = loader.getResource("classpath:VERSION");
        if (!resource.exists()) {
            return "unknown";
        }
        try (InputStream in = resource.getInputStream()) {
            String value = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            return value.isEmpty() ? "unknown" : value;
        } catch (IOException e) {
            return "unknown";
        }
    }
}