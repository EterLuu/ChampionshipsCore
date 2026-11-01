package ink.ziip.championshipscore.auth;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Shared fixed-source policy for long-lived test Bot accounts. */
public final class BotLoginPolicy {
    private final String usernamePrefix;
    private final List<Network> networks;

    private BotLoginPolicy(String usernamePrefix, List<Network> networks) {
        this.usernamePrefix = usernamePrefix.toLowerCase(Locale.ROOT);
        this.networks = List.copyOf(networks);
    }

    public static BotLoginPolicy of(String usernamePrefix, List<String> cidrs) {
        Objects.requireNonNull(usernamePrefix, "usernamePrefix");
        if (usernamePrefix.isBlank())
            throw new IllegalArgumentException("Bot username prefix is blank");
        if (!usernamePrefix.matches("[A-Za-z0-9_]{1,12}"))
            throw new IllegalArgumentException("Invalid Bot username prefix");
        Objects.requireNonNull(cidrs, "cidrs");
        List<Network> networks = new ArrayList<>();
        for (String cidr : cidrs) networks.add(Network.parse(cidr));
        return new BotLoginPolicy(usernamePrefix, networks);
    }

    public boolean isBot(String username) {
        return username != null && username.toLowerCase(Locale.ROOT).startsWith(usernamePrefix);
    }

    public boolean allows(String username, InetAddress address) {
        if (!isBot(username)) return true;
        return allowsBotAddress(address, networks);
    }

    /**
     * Applies authoritative per-account metadata when the identity service supplied it. A missing
     * metadata value falls back to the local prefix policy for compatibility with older identity
     * responses; an explicit Bot value with an empty network list remains fail-closed.
     */
    public boolean allows(
            String username,
            InetAddress address,
            Boolean authoritativeBot,
            List<String> authoritativeCidrs) {
        if (authoritativeBot == null) return allows(username, address);
        if (!authoritativeBot) return true;
        if (authoritativeCidrs == null) return false;
        List<Network> authoritative = new ArrayList<>();
        for (String cidr : authoritativeCidrs) authoritative.add(Network.parse(cidr));
        return allowsBotAddress(address, authoritative);
    }

    private static boolean allowsBotAddress(InetAddress address, List<Network> networks) {
        if (address == null || networks.isEmpty()) return false;
        return networks.stream().anyMatch(network -> network.contains(address));
    }

    public String usernamePrefix() {
        return usernamePrefix;
    }

    public List<String> cidrs() {
        return networks.stream().map(Network::text).toList();
    }

    private record Network(byte[] address, int prefix, String text) {
        static Network parse(String value) {
            if (value == null) throw new IllegalArgumentException("Bot CIDR is null");
            int slash = value.lastIndexOf('/');
            if (slash <= 0 || slash == value.length() - 1)
                throw new IllegalArgumentException("Invalid Bot CIDR: " + value);
            String addressText = value.substring(0, slash);
            int prefix;
            try {
                prefix = Integer.parseInt(value.substring(slash + 1));
            } catch (NumberFormatException failure) {
                throw new IllegalArgumentException("Invalid Bot CIDR: " + value, failure);
            }
            try {
                byte[] address = InetAddress.getByName(addressText).getAddress();
                if (prefix < 0 || prefix > address.length * 8)
                    throw new IllegalArgumentException("Invalid Bot CIDR prefix: " + value);
                return new Network(address, prefix, value);
            } catch (UnknownHostException failure) {
                throw new IllegalArgumentException("Invalid Bot CIDR address: " + value, failure);
            }
        }

        boolean contains(InetAddress candidate) {
            byte[] value = candidate.getAddress();
            if (value.length != address.length) return false;
            int fullBytes = prefix / 8;
            int remainingBits = prefix % 8;
            for (int index = 0; index < fullBytes; index++)
                if (value[index] != address[index]) return false;
            if (remainingBits == 0) return true;
            int mask = 0xff << (8 - remainingBits);
            return (value[fullBytes] & mask) == (address[fullBytes] & mask);
        }
    }
}
