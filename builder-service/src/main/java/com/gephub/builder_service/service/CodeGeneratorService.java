package com.gephub.builder_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gephub.builder_service.domain.Page;
import com.gephub.builder_service.domain.Project;
import com.gephub.builder_service.repository.PageRepository;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CodeGeneratorService {
    private final PageRepository pages;
    private final VelocityEngine velocity;
    private final ObjectMapper json = new ObjectMapper();
    private final String storageRoot;

    public CodeGeneratorService(PageRepository pages, @Value("${gephub.builder.storage.root}") String storageRoot) {
        this.pages = pages;
        this.storageRoot = storageRoot;
        this.velocity = new VelocityEngine();
        velocity.setProperty(RuntimeConstants.RESOURCE_LOADERS, "classpath");
        velocity.setProperty("resource.loader.classpath.class", ClasspathResourceLoader.class.getName());
        velocity.init();
    }

    public Resource generateFrontendCode(Project project, String format) throws Exception {
        List<Page> projectPages = pages.findByProjectId(project.getId());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(baos)) {
            if ("nextjs".equals(format)) {
                generateNextJSProject(zos, project, projectPages);
            } else if ("react".equals(format)) {
                generateReactProject(zos, project, projectPages);
            } else {
                generateHTMLProject(zos, project, projectPages);
            }
        }
        return new ByteArrayResource(baos.toByteArray());
    }

    private void generateNextJSProject(ZipArchiveOutputStream zos, Project project, List<Page> pages) throws Exception {
        addEntry(zos, "package.json", generatePackageJson(project, "nextjs"));
        addEntry(zos, "next.config.js", "module.exports = { reactStrictMode: true };\n");
        addEntry(zos, "tailwind.config.js", generateTailwindConfig());
        addEntry(zos, "tsconfig.json", generateTSConfig());
        for (Page page : pages) {
            String path = page.getPath().equals("/") ? "index" : page.getPath().substring(1).replace("/", "-");
            String componentCode = generateReactComponent(page.getName(), page.getComponentTree());
            addEntry(zos, "pages/" + path + ".tsx", componentCode);
        }
        addEntry(zos, "styles/globals.css", "@tailwind base; @tailwind components; @tailwind utilities;");
    }

    private void generateReactProject(ZipArchiveOutputStream zos, Project project, List<Page> pages) throws Exception {
        addEntry(zos, "package.json", generatePackageJson(project, "react"));
        addEntry(zos, "tailwind.config.js", generateTailwindConfig());
        addEntry(zos, "src/App.jsx", generateReactApp(pages));
        for (Page page : pages) {
            String componentCode = generateReactComponent(page.getName(), page.getComponentTree());
            addEntry(zos, "src/components/" + page.getName() + ".jsx", componentCode);
        }
    }

    private void generateHTMLProject(ZipArchiveOutputStream zos, Project project, List<Page> pages) throws Exception {
        for (Page page : pages) {
            String html = generateHTMLPage(page);
            String filename = page.getPath().equals("/") ? "index.html" : page.getPath().substring(1) + ".html";
            addEntry(zos, filename, html);
        }
        addEntry(zos, "styles.css", "/* Tailwind CDN styles */");
    }

    private String generateReactComponent(String name, String componentTreeJson) {
        try {
            Map<String, Object> tree = json.readValue(componentTreeJson, Map.class);
            return renderComponent(tree, 0);
        } catch (Exception e) {
            return "export default function " + name + "() { return <div>" + name + "</div>; }";
        }
    }

    private String renderComponent(Map<String, Object> node, int indent) {
        String type = (String) node.get("type");
        Map<String, Object> props = (Map<String, Object>) node.getOrDefault("props", new HashMap<>());
        List<Map<String, Object>> children = (List<Map<String, Object>>) node.getOrDefault("children", List.of());
        String indentStr = "  ".repeat(indent);
        StringBuilder sb = new StringBuilder();
        if (indent == 0) {
            sb.append("export default function ").append(type).append("() {\n");
            sb.append(indentStr).append("  return (\n");
        }
        sb.append(indentStr).append("    <").append(type.toLowerCase());
        props.forEach((k, v) -> {
            if ("className".equals(k)) {
                sb.append(" className=\"").append(v).append("\"");
            } else {
                sb.append(" ").append(k).append("=\"").append(v).append("\"");
            }
        });
        sb.append(">\n");
        for (Map<String, Object> child : children) {
            sb.append(renderComponent(child, indent + 1));
        }
        sb.append(indentStr).append("    </").append(type.toLowerCase()).append(">\n");
        if (indent == 0) {
            sb.append(indentStr).append("  );\n");
            sb.append(indentStr).append("}\n");
        }
        return sb.toString();
    }

    private String generateHTMLPage(Page page) {
        return "<!DOCTYPE html><html><head><title>" + page.getName() + "</title></head><body><h1>" + page.getName() + "</h1></body></html>";
    }

    private String generatePackageJson(Project project, String type) {
        return "{\n  \"name\": \"" + project.getName().toLowerCase().replace(" ", "-") + "\",\n  \"version\": \"1.0.0\"\n}";
    }

    private String generateTailwindConfig() {
        return "module.exports = { content: ['./pages/**/*.{js,ts,jsx,tsx}'], theme: { extend: {} }, plugins: [] };\n";
    }

    private String generateTSConfig() {
        return "{ \"compilerOptions\": { \"target\": \"es5\", \"lib\": [\"dom\", \"dom.iterable\", \"esnext\"], \"jsx\": \"preserve\" } }\n";
    }

    private String generateReactApp(List<Page> pages) {
        return "import { BrowserRouter, Routes, Route } from 'react-router-dom';\n" +
                pages.stream().map(p -> "import " + p.getName() + " from './components/" + p.getName() + "';\n").reduce("", String::concat) +
                "export default function App() { return <BrowserRouter><Routes>" +
                pages.stream().map(p -> "<Route path=\"" + p.getPath() + "\" element={<" + p.getName() + " />} />").reduce("", String::concat) +
                "</Routes></BrowserRouter>; }";
    }

    private void addEntry(ZipArchiveOutputStream zos, String name, String content) throws Exception {
        ZipArchiveEntry entry = new ZipArchiveEntry(name);
        zos.putArchiveEntry(entry);
        zos.write(content.getBytes());
        zos.closeArchiveEntry();
    }
}

