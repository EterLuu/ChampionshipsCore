package ink.ziip.championshipscore.api.game.buildmart;

import ink.ziip.championshipscore.integration.worldedit.WorldEditManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Idempotent migration for persisted Build Mart blueprints and material-zone schematics. */
public final class BuildMartCopperAssetMigrator {
    private BuildMartCopperAssetMigrator() {
    }

    public static @NotNull Result migrate(@NotNull File buildMartDirectory) throws IOException {
        int blueprintFiles = 0;
        int blueprintBlocks = 0;
        File blueprintDirectory = new File(buildMartDirectory, "blueprints");
        File[] blueprints = blueprintDirectory.listFiles((directory, name) -> name.toLowerCase().endsWith(".yml"));
        if (blueprints != null) {
            for (File file : blueprints) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                List<String> original = yaml.getStringList("blocks");
                List<String> normalized = new ArrayList<>(original.size());
                int changed = 0;
                for (String block : original) {
                    int equals = block.indexOf('=');
                    if (equals < 0) {
                        normalized.add(block);
                        continue;
                    }
                    String replacement = block.substring(0, equals + 1)
                            + BuildMartCopperPolicy.normalizeBlueprint(block.substring(equals + 1));
                    normalized.add(replacement);
                    if (!replacement.equals(block)) changed++;
                }
                if (changed > 0) {
                    yaml.set("blocks", normalized);
                    yaml.save(file);
                    blueprintFiles++;
                    blueprintBlocks += changed;
                }
            }
        }

        int schematicFiles = 0;
        int schematicBlocks = 0;
        File schematicDirectory = new File(buildMartDirectory, "schematics");
        List<File> schematics = new ArrayList<>();
        collectMaterialZoneSchematics(schematicDirectory, schematics);
        for (File schematic : schematics) {
            int changed = WorldEditManager.rewriteSchematicBlockStates(
                    schematic, BuildMartCopperPolicy::withoutWax);
            if (changed > 0) {
                schematicFiles++;
                schematicBlocks += changed;
            }
        }
        return new Result(blueprintFiles, blueprintBlocks, schematicFiles, schematicBlocks);
    }

    private static void collectMaterialZoneSchematics(File directory, List<File> result) {
        File[] files = directory.listFiles();
        if (files == null) return;
        boolean materialZones = directory.getName().equals("material-zones");
        for (File file : files) {
            if (file.isDirectory()) collectMaterialZoneSchematics(file, result);
            else if (materialZones && file.getName().toLowerCase().endsWith(".schem")) result.add(file);
        }
    }

    public record Result(int blueprintFiles, int blueprintBlocks, int schematicFiles, int schematicBlocks) {
        public boolean changed() {
            return blueprintBlocks > 0 || schematicBlocks > 0;
        }
    }
}
