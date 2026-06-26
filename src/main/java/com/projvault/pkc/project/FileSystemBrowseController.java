package com.projvault.pkc.project;

import com.projvault.common.ApiResponse;
import com.projvault.security.RequirePerm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pkc/fs")
public class FileSystemBrowseController {

    @GetMapping("/roots")
    @RequirePerm("pkc:project:view")
    public ApiResponse<List<Map<String, Object>>> roots() {
        Map<String, Map<String, Object>> roots = new LinkedHashMap<>();
        for (File root : File.listRoots()) {
            addRoot(roots, root.toPath());
        }
        addRoot(roots, Paths.get(System.getProperty("user.home", ".")).toAbsolutePath().normalize());
        addRoot(roots, Paths.get("").toAbsolutePath().normalize());
        return ApiResponse.ok(new ArrayList<>(roots.values()));
    }

    @GetMapping("/list")
    @RequirePerm("pkc:project:view")
    public ApiResponse<Map<String, Object>> list(@RequestParam String path) {
        Path current;
        try {
            current = Paths.get(path).toAbsolutePath().normalize();
        } catch (Exception e) {
            return ApiResponse.error(400, "目录路径非法: " + path);
        }
        if (!Files.isDirectory(current)) {
            return ApiResponse.error(400, "目录不存在或不可访问: " + path);
        }

        List<Map<String, Object>> dirs = new ArrayList<>();
        try (var stream = Files.list(current)) {
            stream.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .limit(500)
                    .forEach(p -> dirs.add(dirEntry(p)));
        } catch (Exception e) {
            return ApiResponse.error(400, "目录不可读取: " + e.getMessage());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("path", current.toString());
        Path parent = current.getParent();
        out.put("parent", parent != null ? parent.toString() : null);
        out.put("dirs", dirs);
        return ApiResponse.ok(out);
    }

    private void addRoot(Map<String, Map<String, Object>> roots, Path path) {
        if (path == null) {
            return;
        }
        try {
            Path normalized = path.toAbsolutePath().normalize();
            if (Files.isDirectory(normalized)) {
                roots.putIfAbsent(normalized.toString(), dirEntry(normalized));
            }
        } catch (Exception ignored) {
            // 忽略不可访问根目录。
        }
    }

    private Map<String, Object> dirEntry(Path path) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", path.getFileName() != null ? path.getFileName().toString() : path.toString());
        entry.put("path", path.toString());
        return entry;
    }
}
