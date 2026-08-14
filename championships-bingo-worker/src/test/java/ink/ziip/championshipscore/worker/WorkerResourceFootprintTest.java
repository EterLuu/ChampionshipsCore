package ink.ziip.championshipscore.worker;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkerResourceFootprintTest {
    @Test
    void workerOnlyBundlesItsInfrastructureConfiguration() throws IOException {
        try (var resources = Files.list(Path.of("src/main/resources"))) {
            List<String> yamlFiles = resources
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".yml"))
                    .sorted()
                    .toList();
            assertEquals(List.of("config.yml", "plugin.yml"), yamlFiles);
        }
    }
}
