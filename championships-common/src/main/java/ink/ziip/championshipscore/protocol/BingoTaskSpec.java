package ink.ziip.championshipscore.protocol;

import java.util.Map;

/** Resolved card cell. Namespaced strings keep the wire model independent of Bukkit registries. */
public record BingoTaskSpec(
        int cellIndex,
        String taskId,
        String taskType,
        Map<String, String> attributes
) {
    public BingoTaskSpec {
        if (cellIndex < 0) throw new IllegalArgumentException("cellIndex must be non-negative");
        taskId = ProtocolSupport.nonBlank(taskId, "taskId");
        taskType = ProtocolSupport.nonBlank(taskType, "taskType");
        attributes = ProtocolSupport.immutableAttributes(attributes);
    }
}
