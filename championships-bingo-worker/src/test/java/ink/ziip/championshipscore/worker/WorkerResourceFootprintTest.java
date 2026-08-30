package ink.ziip.championshipscore.worker;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WorkerResourceFootprintTest {
    @Test
    void workerOnlyBundlesRequiredYamlResources() throws IOException {
        try (var resources = Files.list(Path.of("src/main/resources"))) {
            List<String> yamlFiles = resources
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".yml"))
                    .sorted()
                    .toList();
            assertEquals(List.of("config.yml", "plugin.yml"), yamlFiles);
        }
    }

    @Test
    void coreOwnsChinesePlayerFacingCopy() throws IOException {
        try (var sources = Files.walk(Path.of("src/main/java"))) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                boolean containsHan = Files.readString(source).codePoints()
                        .anyMatch(codePoint -> Character.UnicodeScript.of(codePoint)
                                == Character.UnicodeScript.HAN);
                assertFalse(containsHan, source + " must receive player-facing copy from Core");
            }
        }
    }
}
