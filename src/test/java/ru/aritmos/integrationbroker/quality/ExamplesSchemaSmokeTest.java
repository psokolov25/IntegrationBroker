package ru.aritmos.integrationbroker.quality;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExamplesSchemaSmokeTest {

    @Test
    void appointmentExamples_shouldBeValidJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path dir = Path.of("src/main/resources/examples/appointment");
        List<Path> files;
        try (var stream = Files.list(dir)) {
            files = stream.filter(p -> p.getFileName().toString().endsWith(".json")).toList();
        }
        assertFalse(files.isEmpty());

        for (Path f : files) {
            var node = mapper.readTree(Files.readString(f));
            assertNotNull(node, "json must parse: " + f);
        }
    }
}
