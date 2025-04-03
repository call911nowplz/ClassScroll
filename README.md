# ClassSelector Plugin

ClassSelector provides a cinematic and immersive class selection system for RPG Minecraft servers.  
Players can scroll between classes using their hotbar, preview each class with camera effects and teleportation, and select their desired class to begin playing.

## Features

- Scroll-based class preview using hotbar slots
- Cinematic camera view with invisible ArmorStands
- Custom teleport locations per class
- Freezes screen and disables movement during preview
- Configurable screen effects and sounds
- Integrates with MythicHUD and TAB
- Automatically sends players to another server after selection
- Fully configurable through `config.yml`

## Configuration

```yaml
classes:
  - name: Barbarian
    location:
      world: spawn
      x: 51.5
      y: 2.0
      z: -100.5
      yaw: -152.1
      pitch: 2.1
  - name: Archer
    location:
      world: spawn
      x: -105.5
      y: 70
      z: 82.5
      yaw: 54.0
      pitch: 0.2
  # Add more classes here...

default-class: Barbarian
cooldown-ms: 1000
preview-slot: 5
preview-sound: "rpg:rpg.classsong"
target-server: "RPG"

screeneffect:
  color: BLACK
  fade-in: 10
  stay: 20
  fade-out: 10
  freeze: true

kick-delay-ticks: 120
kick-message: "Server is full!"
```

## Required Format for Integrations

### MythicHUD

Each class **must have a HUD layout** named exactly like this:

```
<lowercase_class_name>_hud-layout
```

**Example**:
```yaml
barbarian_hud-layout
archer_hud-layout
summoner_hud-layout
```

These layouts will be dynamically added and removed during class previews.

### TAB Scoreboard

Each class **must have a scoreboard** configured in TAB using the class name **as-is**, matching the `classes:` list.

**Example**:
```yaml
scoreboards:
  Barbarian:
    ...
  Archer:
    ...
```

During preview, the plugin will run:
```
/tab scoreboard show <ClassName> <PlayerName>
```


## Commands

- `/adminclass`  
  Resets the player's HUD and exits preview mode manually.

# How to set this up?
This plugin requires a server to only be responsible for class selection. It cannot (at the moment) run in the same server players play on.
This plugin is made to be ran alongside another "profile" server running MMOProfiles. On new-profile creation, It sends the players to the class selection server. If they select an already made profile, It skips class selection and instantly sends them to RPG
Let me know if i should implement a better method if anyone is interested in using this plugin INSIDE the playing server

- Spawn NPCs (Citizens has /npc mimic feature to get the player's skin) and equip it with something that fits the class with /npc equip
- Setup the config for Classes, Song name during the class selection, Scoreboards etc..
- Grab the coords for the camera location when looking at the NPC and place that in the config
- Join the server


For support or questions, Join my [Discord](https://discord.gg/JEqREs76yh) and open a ticket
