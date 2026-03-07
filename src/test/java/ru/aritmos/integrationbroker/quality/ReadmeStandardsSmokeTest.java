package ru.aritmos.integrationbroker.quality;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadmeStandardsSmokeTest {

    private static final List<String> REQUIRED_SECTIONS = List.of(
            "## Содержание",
            "## 1. Назначение",
            "## 2. Архитектурные принципы",
            "## 4. Быстрый старт",
            "## 6. Документация",
            "## 8. Безопасность и эксплуатация"
    );

    private static final List<String> BINARY_EXTENSIONS = List.of(
            ".pdf", ".doc", ".docx", ".png", ".jpg", ".jpeg", ".gif", ".zip", ".jar"
    );

    @Test
    void readme_shouldContainRequiredSectionsAndMavenQuickStart() throws Exception {
        String readme = Files.readString(Path.of("README.md"));

        for (String section : REQUIRED_SECTIONS) {
            assertTrue(readme.contains(section), "README missing section: " + section);
        }
        assertTrue(readme.contains("./mvnw -q -Dmaven.site.skip=true test"),
                "README quick-start must use Maven test command from AGENTS.md");
    }

    @Test
    void readmeTocAnchors_shouldResolveToHeadings() throws Exception {
        String readme = Files.readString(Path.of("README.md"));

        Set<String> headingAnchors = new HashSet<>();
        Pattern headingPattern = Pattern.compile("^##\\s+(.+)$", Pattern.MULTILINE);
        Matcher headingMatcher = headingPattern.matcher(readme);
        while (headingMatcher.find()) {
            headingAnchors.add(slugify(headingMatcher.group(1)));
        }

        Pattern tocPattern = Pattern.compile("- \\[[^]]+\\]\\(#([^)]+)\\)");
        Matcher tocMatcher = tocPattern.matcher(readme);
        List<String> missingAnchors = new ArrayList<>();
        int anchors = 0;
        while (tocMatcher.find()) {
            String anchor = tocMatcher.group(1);
            anchors++;
            if (!headingAnchors.contains(anchor)) {
                missingAnchors.add(anchor);
            }
        }

        assertTrue(anchors > 0, "README TOC should contain at least one anchor");
        assertTrue(missingAnchors.isEmpty(), "README TOC has unresolved anchors: " + missingAnchors);
    }

    @Test
    void readmeLocalDocLinks_shouldPointToExistingFiles() throws Exception {
        String readme = Files.readString(Path.of("README.md"));
        Pattern pathPattern = Pattern.compile("`((?:docs|scripts|apps|src)/[^`]+)`");
        Matcher matcher = pathPattern.matcher(readme);

        List<String> missing = new ArrayList<>();
        int verified = 0;
        while (matcher.find()) {
            String rawPath = matcher.group(1);
            if (rawPath.contains("...") || rawPath.contains("*")) {
                continue;
            }
            Path path = Path.of(rawPath);
            verified++;
            if (!Files.exists(path)) {
                missing.add(rawPath);
            }
        }

        assertTrue(verified > 0, "README should contain verifiable local links to repository paths");
        assertTrue(missing.isEmpty(), "README has broken local path links: " + missing);
    }

    @Test
    void readmeSecuritySection_shouldMentionCoreOperationalRestrictions() throws Exception {
        String readme = Files.readString(Path.of("README.md")).toLowerCase();
        assertTrue(readme.contains("не логировать секреты"), "README security section should mention secrets policy");
        assertTrue(readme.contains("корреляцион"), "README security section should mention correlation IDs");
        assertTrue(readme.contains("role-based") || readme.contains("rbac"), "README security section should mention access control");
    }

    @Test
    void docsDirectory_shouldNotContainBinaryFiles() throws Exception {
        try (var stream = Files.walk(Path.of("docs"))) {
            List<Path> binaries = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> hasBinaryExtension(p.getFileName().toString().toLowerCase()))
                    .toList();
            assertTrue(binaries.isEmpty(), "Binary files are not allowed in docs: " + binaries);
        }
    }

    private static String slugify(String heading) {
        String h = heading.trim().toLowerCase();
        StringBuilder out = new StringBuilder();
        for (char c : h.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_') {
                out.append(c);
            }
        }
        return out.toString().trim().replace(' ', '-');
    }

    private static boolean hasBinaryExtension(String name) {
        for (String ext : BINARY_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
}
