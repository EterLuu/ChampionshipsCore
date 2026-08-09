package ink.ziip.championshipscore.protocol;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ProtocolSupport {
    private ProtocolSupport() {
    }

    static <T> T required(T value, String name) {
        return Objects.requireNonNull(value, name);
    }

    static String nonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    static <T> List<T> immutableList(List<T> values, String name) {
        Objects.requireNonNull(values, name);
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(name + " must not contain null values");
        }
        return List.copyOf(values);
    }

    static <T> List<List<T>> immutableNestedList(List<List<T>> values, String name) {
        Objects.requireNonNull(values, name);
        return values.stream().map(section -> immutableList(section, name + " section")).toList();
    }

    static Map<String, String> immutableAttributes(Map<String, String> attributes) {
        Objects.requireNonNull(attributes, "attributes");
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        attributes.forEach((key, value) -> copy.put(
                nonBlank(key, "attribute key"), required(value, "attribute value")));
        return Map.copyOf(copy);
    }
}
