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

    PRIMARY KEY (`id`)
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

    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Create the player status table if it does not exist
CREATE TABLE IF NOT EXISTS `player_points`
(
    `id`       INTEGER      NOT NULL AUTO_INCREMENT,
    `uuid`     VARCHAR(255) NOT NULL,
    `username` VARCHAR(255) NOT NULL,
    `teamId`   INTEGER      NOT NULL,
    `team`     VARCHAR(255) NOT NULL,
    `rivalId`  INTEGER      NOT NULL,
    `rival`    VARCHAR(255) NOT NULL,
    `game`     VARCHAR(255) NOT NULL,
    `area`     VARCHAR(255) NOT NULL,
    `round`    VARCHAR(255) NOT NULL,
    `points`   INTEGER      NOT NULL,
    `time`     VARCHAR(255) NOT NULL,

    PRIMARY KEY (`id`)
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

    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Create the players if it does not exist
CREATE TABLE IF NOT EXISTS `players`
(
    `id`       INTEGER      NOT NULL AUTO_INCREMENT,
    `uuid`     VARCHAR(255) NOT NULL,
    `username` VARCHAR(255) NOT NULL,

    PRIMARY KEY (`id`)
    ) ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci;