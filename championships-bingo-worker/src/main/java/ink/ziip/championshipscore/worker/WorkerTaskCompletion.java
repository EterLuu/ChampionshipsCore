package ink.ziip.championshipscore.worker;

import net.kyori.adventure.text.Component;

import java.util.UUID;

/** Accepted completion facts, including the original player's identity for cooperative credits. */
record WorkerTaskCompletion(int teamId, UUID playerId, Component playerName, long seconds) {}
