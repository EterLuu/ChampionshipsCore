ChampionshipsCore
=========

The core plugin of the Summer/Winter Collab Championship.

## Placeholders

### Player

| **Placeholder**                 | **Description**                                                 |
|---------------------------------|-----------------------------------------------------------------|
| `%cc_player_points%`            | Show the current points of the player.                          |
| `%cc_player_rank%`              | Show the current rank of the player.                            |
| `%cc_player_team_name_no_color%`| Show the current team name (without color) of the player.       |
| `%cc_player_team_color_code%`   | Show the current team color code.                               |
| `%cc_player_team_name%`         | Show the current team name of the player.                       |
| `%cc_player_team_color%`        | Show the current team color name of the player.                 |
| `%cc_player_team_points%`       | Show the current team points of the player.                     |
| `%cc_player_team_rank%`         | Show the current team rank of the player.                       |

### Leaderboard

| **Placeholder**              | **Description**                                       |
|------------------------------|-------------------------------------------------------|
| `%leaderboard_player_[num]%` | Show current points and rank of the num-place player. |
| `%leaderboard_team_[num]%`   | Show current points and rank of the num-place team.   |

### Schedule

| **Placeholder**           | **Description**                                    |
|---------------------------|----------------------------------------------------|
| `%schedule_round_total%`  | Show the current round of the whole game.          |
| `%schedule_round_points%` | Show multiple points that the next round can gain. |
| `%schedule_round_[game]%` | Show current sub round of the game.                |

### Vote

| **Placeholder**           | **Description**                                                                         |
|---------------------------|-----------------------------------------------------------------------------------------|
| `%vote_can_vote_[game]%`  | Whether the game can be voted or not, return true or false.                             |
| `%vote_vote_nums_[game]%` | Show current number of the game players voted for.                                      |
| `%vote_player_vote%`      | Show the current game that the player voted on, and return none in the message if none. |

### Battle Box

| **Placeholder**                      | **Description**                                       |
|--------------------------------------|-------------------------------------------------------|
| `%battlebox_area_status_[areaName]%` | Show current status of the area.                      |
| `%battlebox_area_team_[areaName]%`   | Show current team of the area.                        |
| `%battlebox_area_rival_[areaName]%`  | Show current rival team of the area.                  |
| `%battlebox_area_timer_[areaName]%`  | Show current timer of the area.                       |
| `%battlebox_player_kits_[areaName]%` | Show the current kits the player chooses in the area. |

### Parkour Tag

| **Placeholder**                                 | **Description**                                                           |
|-------------------------------------------------|---------------------------------------------------------------------------|
| `%parkourtag_area_status_[areaName]%`           | Show current status of the area.                                          |
| `%parkourtag_area_team_[areaName]%`             | Show current team of the area.                                            |
| `%parkourtag_area_rival_[areaName]%`            | Show current rival team of the area.                                      |
| `%parkourtag_area_timer_[areaName]%`            | Show current timer of the area.                                           |
| `%parkourtag_area_chaser_[areaName]%`           | Show the current chaser in the area player stands.                        |
| `%parkourtag_area_escapees_[areaName]%`         | Show the current number of escapees in the area where that player stands. |
| `%parkourtag_area_survived_players_[areaName]%` | Show the current number of survived players in the area player stands.    |
| `%parkourtag_player_role_[areaName]%`           | Show current role of the player.                                          |

### TNT Run

| **Placeholder**                               | **Description**                                          |
|-----------------------------------------------|----------------------------------------------------------|
| `%tntrun_area_status_[areaName]%`             | Show current status of the area.                         |
| `%tntrun_area_timer_[areaName]%`              | Show current timer of the area.                          |
| `%tntrun_area_tnt_rain_countdown_[areaName]%` | Show the current timer of the next tnt rain in the area. |
| `%tntrun_area_survived_players_[areaName]%`   | Show current number of survived players.                 |

### Sky Wars

| **Placeholder**                               | **Description**                                                  |
|-----------------------------------------------|------------------------------------------------------------------|
| `%skywars_area_status_[areaName]%`            | Show current status of the area.                                 |
| `%skywars_area_timer_[areaName]%`             | Show current timer of the area.                                  |
| `%skywars_area_survived_players_[areaName]%`  | Show current number of survived players.                         |
| `%skywars_area_survived_teams_[areaName]%`    | Show current number of survived teams.                           |
| `%skywars_player_border_distance_[areaName]%` | Show the distance between the player and the border in the area. |

### TGTTOS

| **Placeholder**                               | **Description**                                                                 |
|-----------------------------------------------|---------------------------------------------------------------------------------|
| `%tgttos_area_name_[areaName]%`               | Show current name of the area.                                                  |
| `%tgttos_area_status_[areaName]%`             | Show current status of the area.                                                |
| `%tgttos_area_timer_[areaName]%`              | Show current timer of the area.                                                 |
| `%tgttos_area_player_arrived_[areaName]%`     | Show the current number of arrived players in the area.                         |
| `%tgttos_player_team_not_arrived_[areaName]%` | Show the current number of un-arrived players in the player's team in the area. |

### Snowball Showdown

| **Placeholder**                                 | **Description**                                          |
|-------------------------------------------------|----------------------------------------------------------|
| `%snowball_area_status_[areaName]%`             | Show current status of the area.                         |
| `%snowball_area_timer_[areaName]%`              | Show current timer of the area.                          |
| `%snowball_area_rank_[num]_[areaName]%`         | Show current rank of the area.                           |
| `%snowball_player_individual_kills_[areaName]%` | Show current kills that the player archived in the area. |

### Dragon Egg Carnival

| **Placeholder**                               | **Description**                                                    |
|-----------------------------------------------|--------------------------------------------------------------------|
| `%decarnival_area_status_[areaName]%`         | Show current status of the area.                                   |
| `%decarnival_area_timer_[areaName]%`          | Show current timer of the area.                                    |
| `%decarnival_area_team_[areaName]%`           | Show current team of the area.                                     |
| `%decarnival_area_rival_[areaName]%`          | Show current rival team of the area.                               |
| `%decarnival_area_team_wins_[areaName]%`      | Show the current number of wins of the team in the area.           |
| `%decarnival_area_rival_wins_[areaName]%`     | Show current times of wins of the rival team in the area.          |
| `%decarnival_playtool_countdown_[areaName]%`  | Show the current timer of the next play tool provided in the area. |
| `%decarnival_egg_spawn_countdown_[areaName]%` | Show the current timer of spawning egg in the area.                |

### Bingo

| **Placeholder**                  | **Description**                                     |
|----------------------------------|-----------------------------------------------------|
| `%bingo_team_points_[material]%` | Show current points that the item material can get. |
