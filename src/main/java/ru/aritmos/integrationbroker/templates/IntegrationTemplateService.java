package ru.aritmos.integrationbroker.templates;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.groovy.GroovyToolingService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Singleton
public class IntegrationTemplateService {

    private static final String FORMAT_VERSION = "1.0.0";
    private static final String COMPATIBILITY_RANGE = "[1.0.0,2.0.0)";
    private static final int MAX_ARCHIVE_ENTRY_BYTES = 2 * 1024 * 1024;

    private final RuntimeConfigStore runtimeConfigStore;
    private final GroovyToolingService groovyToolingService;

    public IntegrationTemplateService(RuntimeConfigStore runtimeConfigStore, GroovyToolingService groovyToolingService) {
        this.runtimeConfigStore = runtimeConfigStore;
        this.groovyToolingService = groovyToolingService;
    }

    public ExportResult exportTemplate(String branchId,
                                       String solution,
                                       String customerGroup,
                                       String templateVersion,
                                       boolean templateSet,
                                       Map<String, Object> customerOverrides) {
        String safeBranchId = safeBranch(branchId);
        String root = templateSet ? "templates/" + safeBranchId + "/" : "";

        RuntimeConfigStore.RuntimeConfig cfg = runtimeConfigStore.getEffective();
        RuntimeConfigStore.RuntimeConfig normalized = cfg == null ? null : cfg.normalize();
        Map<String, byte[]> files = buildFiles(root, normalized, customerOverrides);

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("format", templateSet ? "ibts" : "ibt");
        manifest.put("formatVersion", FORMAT_VERSION);
        manifest.put("templateVersion", safe(templateVersion));
        manifest.put("compatibilityRange", COMPATIBILITY_RANGE);
        manifest.put("createdAt", Instant.now().toString());
        manifest.put("branchId", safeBranchId);
        manifest.put("solution", safe(solution));
        manifest.put("customerGroup", safe(customerGroup));
        manifest.put("checksums", checksums(files));
        files.put(root + "manifest.yml", yamlBytes(manifest));

        byte[] archive = zip(files);
        String extension = templateSet ? "ibts" : "ibt";
        return new ExportResult(safeBranchId + "-template." + extension, archive, files.size());
    }

    public ImportDryRunResult importDryRun(byte[] archiveBytes,
                                            String mergeStrategy,
                                            String branchIdHint) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> scriptsChecked = new ArrayList<>();
        List<String> changedArtifacts = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();

        if (archiveBytes == null || archiveBytes.length == 0) {
            return new ImportDryRunResult(false, "UNKNOWN", mergeStrategyOrDefault(mergeStrategy), warnings,
                    List.of("Archive is empty"), scriptsChecked, changedArtifacts, conflicts, Map.of());
        }

        Map<String, byte[]> files = unzip(archiveBytes, errors);
        if (files.isEmpty()) {
            errors.add("Archive has no files");
            return new ImportDryRunResult(false, "UNKNOWN", mergeStrategyOrDefault(mergeStrategy), warnings,
                    errors, scriptsChecked, changedArtifacts, conflicts, Map.of());
        }

        String manifestPath = files.keySet().stream().filter(p -> p.endsWith("manifest.yml")).findFirst().orElse(null);
        if (manifestPath == null) {
            errors.add("manifest.yml is missing");
            return new ImportDryRunResult(false, "UNKNOWN", mergeStrategyOrDefault(mergeStrategy), warnings,
                    errors, scriptsChecked, changedArtifacts, conflicts, Map.of());
        }

        Map<String, Object> manifest = parseYamlMap(files.get(manifestPath), errors, "manifest.yml");
        String format = asString(manifest.get("format"), "UNKNOWN");
        if (!List.of("ibt", "ibts").contains(format)) {
            errors.add("Unsupported format: " + format);
        }
        String formatVersion = asString(manifest.get("formatVersion"), "");
        if (!FORMAT_VERSION.equals(formatVersion)) {
            errors.add("Unsupported formatVersion: " + formatVersion);
        }
        String templateVersion = asString(manifest.get("templateVersion"), "");
        if (!templateVersion.isBlank() && !isSemver(templateVersion)) {
            errors.add("Invalid templateVersion (expected semver): " + templateVersion);
        }
        String compatibilityRange = asString(manifest.get("compatibilityRange"), "");
        if (!compatibilityRange.isBlank() && !isCompatibilityRange(compatibilityRange)) {
            errors.add("Invalid compatibilityRange: " + compatibilityRange);
        }

        long manifestCount = files.keySet().stream().filter(path -> path.endsWith("manifest.yml")).count();
        if (manifestCount > 1) {
            errors.add("Archive must contain a single manifest.yml file");
        }

        String rootPrefix = manifestPath.substring(0, manifestPath.length() - "manifest.yml".length());
        String manifestBranchId = safeBranch(asString(manifest.get("branchId"), ""));
        validateArchiveLayout(files, format, rootPrefix, manifestBranchId, errors, warnings);

        Map<String, String> declaredChecksums = toStringMap(manifest.get("checksums"));
        Map<String, String> actualChecksums = checksums(withoutManifest(files));
        for (Map.Entry<String, String> entry : declaredChecksums.entrySet()) {
            String actual = actualChecksums.get(entry.getKey());
            if (!Objects.equals(entry.getValue(), actual)) {
                errors.add("Checksum mismatch for: " + entry.getKey());
            }
        }
        for (String actualPath : actualChecksums.keySet()) {
            if (!declaredChecksums.containsKey(actualPath)) {
                errors.add("Checksum is missing in manifest for: " + actualPath);
            }
        }

        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            String path = entry.getKey();
            if (path.endsWith(".yml")) {
                parseYamlMap(entry.getValue(), errors, path);
                changedArtifacts.add(path);
            }
            if (path.endsWith(".groovy")) {
                String script = new String(entry.getValue(), StandardCharsets.UTF_8);
                GroovyToolingService.ValidationResult validation = groovyToolingService.validate(script);
                scriptsChecked.add(path);
                if (!validation.valid()) {
                    errors.add("Groovy compile error in " + path + ": " + String.join(";", validation.errors()));
                }
                changedArtifacts.add(path);
            }
        }

        String strategy = mergeStrategyOrDefault(mergeStrategy);
        if (!List.of("replace", "merge", "keep-local").contains(strategy)) {
            warnings.add("Unknown merge strategy, fallback to merge");
            strategy = "merge";
        }

        Set<String> currentPaths = currentArtifactPaths(branchIdHint);
        for (String path : files.keySet()) {
            if (currentPaths.contains(path) && !path.endsWith("manifest.yml")) {
                conflicts.add(path);
            }
        }

        if (!conflicts.isEmpty() && "keep-local".equals(strategy)) {
            warnings.add("keep-local: existing files will remain untouched, imported conflicting files are skipped");
        }

        return new ImportDryRunResult(errors.isEmpty(), format, strategy, warnings, errors,
                scriptsChecked, changedArtifacts, conflicts, actualChecksums);
    }

    private static void validateArchiveLayout(Map<String, byte[]> files,
                                              String format,
                                              String rootPrefix,
                                              String manifestBranchId,
                                              List<String> errors,
                                              List<String> warnings) {
        String effectiveRoot = rootPrefix == null ? "" : rootPrefix;

        if ("ibts".equals(format)) {
            String expectedRoot = "templates/" + (manifestBranchId.isBlank() ? "default-branch" : manifestBranchId) + "/";
            if (!effectiveRoot.equals(expectedRoot)) {
                errors.add("IBTS root mismatch: expected " + expectedRoot + " but was " + effectiveRoot);
            }
        } else if ("ibt".equals(format) && !effectiveRoot.isEmpty()) {
            errors.add("IBT root mismatch: expected archive root but was " + effectiveRoot);
        }

        String runtimePath = effectiveRoot + "runtime/runtime-config.yml";
        if (!files.containsKey(runtimePath)) {
            errors.add("Required file is missing: " + runtimePath);
        }

        boolean hasFlow = files.keySet().stream().anyMatch(path -> path.startsWith(effectiveRoot + "flows/") && path.endsWith("/flow.yml"));
        if (!hasFlow) {
            warnings.add("No flow.yml files found in archive");
        }

        Set<String> flowRootsWithFlowYml = files.keySet().stream()
                .filter(path -> path.startsWith(effectiveRoot + "flows/") && path.endsWith("/flow.yml"))
                .map(path -> path.substring(0, path.length() - "flow.yml".length()))
                .collect(Collectors.toSet());
        files.keySet().stream()
                .filter(path -> path.startsWith(effectiveRoot + "flows/") && !path.endsWith("/flow.yml"))
                .map(path -> path.substring(0, path.lastIndexOf('/') + 1))
                .filter(flowRoot -> !flowRootsWithFlowYml.contains(flowRoot))
                .forEach(flowRoot -> warnings.add("Flow directory has no flow.yml: " + flowRoot));

        for (String path : files.keySet()) {
            if (path.endsWith("manifest.yml") || path.endsWith(".yml") || path.endsWith(".groovy")) {
                continue;
            }
            warnings.add("Unsupported file type in archive: " + path);
        }
    }

    private Set<String> currentArtifactPaths(String branchIdHint) {
        RuntimeConfigStore.RuntimeConfig cfg = runtimeConfigStore.getEffective();
        RuntimeConfigStore.RuntimeConfig normalized = cfg == null ? null : cfg.normalize();
        String root = "";
        if (branchIdHint != null && !branchIdHint.isBlank()) {
            root = "templates/" + safeBranch(branchIdHint) + "/";
        }
        return new LinkedHashSet<>(buildFiles(root, normalized, Map.of()).keySet());
    }

    private static Map<String, byte[]> buildFiles(String root,
                                                  RuntimeConfigStore.RuntimeConfig normalized,
                                                  Map<String, Object> customerOverrides) {
        Map<String, byte[]> files = new TreeMap<>();
        files.put(root + "runtime/runtime-config.yml", yamlBytes(runtimeConfigAsMap(normalized)));

        List<RuntimeConfigStore.FlowConfig> flows = normalized == null || normalized.flows() == null ? List.of() : normalized.flows();
        List<RuntimeConfigStore.FlowConfig> sortedFlows = new ArrayList<>(flows);
        sortedFlows.sort(Comparator.comparing(RuntimeConfigStore.FlowConfig::id, Comparator.nullsLast(String::compareTo)));
        for (RuntimeConfigStore.FlowConfig flow : sortedFlows) {
            if (flow == null || flow.id() == null || flow.id().isBlank()) {
                continue;
            }
            String flowRoot = root + "flows/" + flow.id() + "/";
            files.put(flowRoot + "flow.yml", yamlBytes(flowAsMap(flow)));
            if (flow.groovy() != null && !flow.groovy().isBlank()) {
                files.put(flowRoot + "script.groovy", normalizeLineEnding(flow.groovy()).getBytes(StandardCharsets.UTF_8));
            }
            if (flow.metadata() != null && !flow.metadata().isEmpty()) {
                files.put(flowRoot + "metadata.yml", yamlBytes(new TreeMap<>(flow.metadata())));
            }
        }

        Map<String, Object> overrides = customerOverrides == null ? Map.of() : new TreeMap<>(customerOverrides);
        if (!overrides.isEmpty()) {
            files.put(root + "overrides/customer-overrides.yml", yamlBytes(overrides));
        }
        return files;
    }

    private static String mergeStrategyOrDefault(String strategy) {
        if (strategy == null || strategy.isBlank()) {
            return "merge";
        }
        return strategy.trim().toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safeBranch(String branchId) {
        String raw = safe(branchId);
        if (raw.isBlank()) {
            return "default-branch";
        }
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static Map<String, Object> runtimeConfigAsMap(RuntimeConfigStore.RuntimeConfig cfg) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (cfg == null) {
            return map;
        }
        map.put("revision", cfg.revision());
        map.put("flowsCount", cfg.flows() == null ? 0 : cfg.flows().size());
        map.put("restConnectors", cfg.restConnectors() == null ? List.of() : cfg.restConnectors().keySet().stream().sorted().collect(Collectors.toList()));
        map.put("visitManagerEnabled", cfg.visitManager() != null && cfg.visitManager().enabled());
        map.put("dataBusEnabled", cfg.dataBus() != null && cfg.dataBus().enabled());
        return map;
    }

    private static Map<String, Object> flowAsMap(RuntimeConfigStore.FlowConfig flow) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", flow.id());
        map.put("enabled", flow.enabled());
        if (flow.selector() != null) {
            Map<String, Object> selector = new LinkedHashMap<>();
            selector.put("kind", flow.selector().kind());
            selector.put("type", flow.selector().type());
            map.put("selector", selector);
        }
        map.put("scriptFile", "script.groovy");
        return map;
    }

    private static byte[] yamlBytes(Map<String, Object> map) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        Yaml yaml = new Yaml(options);
        String text = yaml.dump(map == null ? Map.of() : map);
        return normalizeLineEnding(text).getBytes(StandardCharsets.UTF_8);
    }

    private static String normalizeLineEnding(String text) {
        return text == null ? "" : text.replace("\r\n", "\n");
    }

    private static byte[] zip(Map<String, byte[]> files) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                    ZipEntry ze = new ZipEntry(entry.getKey());
                    ze.setTime(0);
                    zos.putNextEntry(ze);
                    zos.write(entry.getValue());
                    zos.closeEntry();
                }
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create archive", e);
        }
    }

    private static Map<String, byte[]> unzip(byte[] archiveBytes, List<String> errors) {
        Map<String, byte[]> files = new TreeMap<>();
        try (InputStream in = new ByteArrayInputStream(archiveBytes); ZipInputStream zis = new ZipInputStream(in, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = entry.getName();
                if (entryName.contains("../") || entryName.startsWith("/") || entryName.startsWith("\\")) {
                    errors.add("Unsafe zip entry path: " + entryName);
                    continue;
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                zis.transferTo(bos);
                if (bos.size() > MAX_ARCHIVE_ENTRY_BYTES) {
                    errors.add("Zip entry is too large: " + entryName);
                    continue;
                }
                files.put(entryName, bos.toByteArray());
            }
        } catch (IOException e) {
            errors.add("Unable to read archive: " + e.getMessage());
        }
        return files;
    }

    private static boolean isSemver(String value) {
        return value.matches("^\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?$");
    }

    private static boolean isCompatibilityRange(String value) {
        if (value == null || value.length() < 5) {
            return false;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        boolean bounded = (first == '[' || first == '(') && (last == ']' || last == ')');
        return bounded && value.contains(",");
    }

    private static Map<String, byte[]> withoutManifest(Map<String, byte[]> files) {
        Map<String, byte[]> copy = new TreeMap<>(files);
        copy.entrySet().removeIf(e -> e.getKey().endsWith("manifest.yml"));
        return copy;
    }

    private static Map<String, Object> parseYamlMap(byte[] bytes, List<String> errors, String path) {
        try {
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(new String(bytes, StandardCharsets.UTF_8));
            if (loaded instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                map.forEach((k, v) -> result.put(String.valueOf(k), v));
                return result;
            }
            return Map.of();
        } catch (Exception e) {
            errors.add("YAML parse error in " + path + ": " + e.getMessage());
            return Map.of();
        }
    }

    private static Map<String, String> checksums(Map<String, byte[]> files) {
        Map<String, String> result = new TreeMap<>();
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            result.put(entry.getKey(), sha256(entry.getValue()));
        }
        return result;
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data == null ? new byte[0] : data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, String> toStringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        map.forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
        return result;
    }

    private static String asString(Object value, String def) {
        if (value == null) {
            return def;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? def : text;
    }

    @Serdeable
    public record ExportResult(String filename, byte[] bytes, int filesCount) {
    }

    @Serdeable
    public record ImportDryRunResult(boolean ok,
                                     String format,
                                     String mergeStrategy,
                                     List<String> warnings,
                                     List<String> errors,
                                     List<String> scriptsChecked,
                                     List<String> changedArtifacts,
                                     List<String> conflicts,
                                     Map<String, String> checksums) {
    }
}
