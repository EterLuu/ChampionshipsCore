-- =====================================================================
-- 数据库结构变更规范（务必阅读）：
-- 本文件只服务全新安装（由 DatabaseMigrationController 的 v1 baseline 执行，
-- 全部语句必须保持 IF NOT EXISTS 幂等）。
-- 任何结构变更（新表 / 新列 / 新索引 / 修改定义）时，必须：
--   1. 同步更新本文件，保持"当前完整结构"；且
--   2. 在 ink.ziip.championshipscore.database.DatabaseMigrationController 的
--      MIGRATIONS 注册表中【追加】下一个版本号的迁移（只追加，绝不修改或
--      删除已有迁移）。
-- 只改本文件不改迁移控制器的话，已部署的旧库不会升级。
-- =====================================================================

-- Set the storage engine
SET DEFAULT_STORAGE_ENGINE = InnoDB;

-- Enable foreign key constraints
SET FOREIGN_KEY_CHECKS = 1;

-- Create the teams table if it does not exist
CREATE TABLE IF NOT EXISTS `teams`
(
    `id`        INTEGER      NOT NULL AUTO_INCREMENT,
    `name`      VARCHAR(255) NOT NULL,
    `colorName` VARCHAR(16)  NOT NULL,
    `colorCode` VARCHAR(255) NOT NULL,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_teams_name` (`name`),
    UNIQUE KEY `uq_teams_color_name` (`colorName`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Create the team members table if it does not exist
CREATE TABLE IF NOT EXISTS `team_members`
(
    `id`       INTEGER      NOT NULL AUTO_INCREMENT,
    `uuid`     VARCHAR(255) NOT NULL,
    `username` VARCHAR(255) NOT NULL,
    `teamId`   INTEGER      NOT NULL,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_team_members_uuid` (`uuid`),
    UNIQUE KEY `uq_team_members_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Create the player status table if it does not exist
CREATE TABLE IF NOT EXISTS `player_points`
(
    `id`       INTEGER            NOT NULL AUTO_INCREMENT,
    `transactionId` VARCHAR(36)   NULL,
    `uuid`     VARCHAR(255)       NOT NULL,
    `username` VARCHAR(255)       NOT NULL,
    `teamId`   INTEGER            NOT NULL,
    `team`     VARCHAR(255)       NOT NULL,
    `rivalId`  INTEGER            NOT NULL,
    `rival`    VARCHAR(255)       NOT NULL,
    `game`     VARCHAR(255)       NOT NULL,
    `area`     VARCHAR(255)       NOT NULL,
    `round`    VARCHAR(255)       NOT NULL,
    `points`   DOUBLE             NOT NULL,
    `time`     VARCHAR(255)       NOT NULL,
    `valid`    INTEGER            NOT NULL DEFAULT 1,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_player_points_transaction_id` (`transactionId`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Create the game status if it does not exist
CREATE TABLE IF NOT EXISTS `game_status`
(
    `id`    INTEGER      NOT NULL AUTO_INCREMENT,
    `time`  VARCHAR(255) NOT NULL,
    `game`  VARCHAR(255) NOT NULL,
    `order` INTEGER      NOT NULL,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_game_status_game` (`game`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Create the players if it does not exist
CREATE TABLE IF NOT EXISTS `players`
(
    `id`       INTEGER      NOT NULL AUTO_INCREMENT,
    `uuid`     VARCHAR(255) NOT NULL,
    `username` VARCHAR(255) NOT NULL,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_players_uuid` (`uuid`),
    UNIQUE KEY `uq_players_username` (`username`)
    ) ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `remote_bingo_matches`
(
    `matchId`      VARCHAR(36)  NOT NULL,
    `epoch`        BIGINT       NOT NULL,
    `workerId`     VARCHAR(128) NOT NULL,
    `ownerInstance` VARCHAR(128) NOT NULL,
    `state`        VARCHAR(32)  NOT NULL,
    `manifest`     LONGBLOB     NOT NULL,
    `updatedAt`    BIGINT       NOT NULL,

    PRIMARY KEY (`matchId`, `epoch`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `remote_bingo_inbox`
(
    `messageId`     VARCHAR(36) NOT NULL,
    `matchId`       VARCHAR(36) NOT NULL,
    `epoch`         BIGINT      NOT NULL,
    `eventSeq`      BIGINT      NOT NULL,
    `eventType`     VARCHAR(32) NOT NULL,
    `processedAt`   BIGINT      NOT NULL,

    PRIMARY KEY (`messageId`),
    UNIQUE KEY `uq_remote_bingo_match_event` (`matchId`, `epoch`, `eventSeq`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- DAILY is deliberately isolated from player_points/game_status. These tables may be rebuilt or
-- ranked independently without changing any formal-event history.
CREATE TABLE IF NOT EXISTS `daily_player_stats`
(
    `uuid`          VARCHAR(36)  NOT NULL,
    `username`      VARCHAR(16)  NOT NULL,
    `game`          VARCHAR(64)  NOT NULL,
    `gamesPlayed`   BIGINT       NOT NULL DEFAULT 0,
    `wins`          BIGINT       NOT NULL DEFAULT 0,
    `lineCount`     BIGINT       NOT NULL DEFAULT 0,
    `completedTasks` BIGINT      NOT NULL DEFAULT 0,
    `maxCompletedTasks` BIGINT  NOT NULL DEFAULT 0,
    `updatedAt`     BIGINT       NOT NULL,

    PRIMARY KEY (`uuid`, `game`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `daily_match_results`
(
    `matchId`       VARCHAR(36)  NOT NULL,
    `uuid`          VARCHAR(36)  NOT NULL,
    `username`      VARCHAR(16)  NOT NULL,
    `game`          VARCHAR(64)  NOT NULL,
    `map`           VARCHAR(128) NOT NULL,
    `teamKey`       VARCHAR(64)  NOT NULL,
    `points`        DOUBLE       NOT NULL,
    `won`           BOOLEAN      NOT NULL,
    `lineCount`     BIGINT       NOT NULL DEFAULT 0,
    `completedTasks` BIGINT      NOT NULL DEFAULT 0,
    `finishedAt`    BIGINT       NOT NULL,

    PRIMARY KEY (`matchId`, `uuid`),
    INDEX `idx_daily_results_player` (`uuid`, `game`, `finishedAt`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `daily_player_records`
(
    `uuid`          VARCHAR(36)  NOT NULL,
    `username`      VARCHAR(16)  NOT NULL,
    `game`          VARCHAR(64)  NOT NULL,
    `map`           VARCHAR(128) NOT NULL,
    `mapRevision`   VARCHAR(64)  NOT NULL,
    `rulesHash`     VARCHAR(128) NOT NULL,
    `recordType`    VARCHAR(64)  NOT NULL,
    `durationMs`    BIGINT       NOT NULL,
    `matchId`       VARCHAR(36)  NOT NULL,
    `achievedBy`    VARCHAR(36)  NULL,
    `achievedAt`    BIGINT       NOT NULL,
    `recordRank`    TINYINT      NOT NULL DEFAULT 1,

    PRIMARY KEY (`uuid`, `game`, `map`, `mapRevision`, `rulesHash`, `recordType`, `recordRank`),
    INDEX `idx_daily_records_board` (`game`, `map`, `recordType`, `durationMs`, `recordRank`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `daily_map_player_stats`
(
    `uuid`            VARCHAR(36)  NOT NULL,
    `username`        VARCHAR(16)  NOT NULL,
    `game`            VARCHAR(64)  NOT NULL,
    `map`             VARCHAR(128) NOT NULL,
    `gamesPlayed`     BIGINT       NOT NULL DEFAULT 0,
    `wins`            BIGINT       NOT NULL DEFAULT 0,
    `maxTasks`        BIGINT       NOT NULL DEFAULT 0,
    `maxLines`        BIGINT       NOT NULL DEFAULT 0,
    `maxFirstTasks`   BIGINT       NOT NULL DEFAULT 0,
    `maxDragonDamage` DOUBLE       NOT NULL DEFAULT 0,
    `firstLiberate`   BIGINT       NOT NULL DEFAULT 0,
    `firstNextGen`    BIGINT       NOT NULL DEFAULT 0,
    `firstGateway`    BIGINT       NOT NULL DEFAULT 0,
    `maxStars`        BIGINT       NOT NULL DEFAULT 0,
    `finishes`        BIGINT       NOT NULL DEFAULT 0,
    `updatedAt`       BIGINT       NOT NULL,

    PRIMARY KEY (`uuid`, `game`, `map`),
    INDEX `idx_daily_map_stats_board` (`game`, `map`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `daily_pkw_records`
(
    `uuid`         VARCHAR(36)  NOT NULL,
    `username`     VARCHAR(16)  NOT NULL,
    `map`          VARCHAR(128) NOT NULL,
    `recordType`   VARCHAR(32)  NOT NULL,
    `primaryValue` DOUBLE       NOT NULL,
    `durationMs`   BIGINT       NOT NULL,
    `matchId`      VARCHAR(36)  NOT NULL,
    `achievedAt`   BIGINT       NOT NULL,

    PRIMARY KEY (`uuid`, `map`, `recordType`),
    INDEX `idx_daily_pkw_records_board` (`map`, `recordType`, `primaryValue`, `durationMs`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
