# Craft Planner

Craft Planner is a client-side Fabric mod for Minecraft that helps you plan larger crafting projects and keep track of the materials you still need.

Instead of only showing the raw ingredients for an item, Craft Planner follows the full crafting chain and updates it as you collect and craft items.

For example, if you want to craft a Redstone Repeater, the planner can track the complete process from logs, cobblestone and redstone all the way to planks, sticks, Redstone Torches, stone and finally the repeater.

## Documentation

Read the full documentation in the [CraftPlanner Wiki](https://github.com/oepol/CraftPlanner/wiki).

## Features

- Full crafting chains with intermediate items
- Automatic material calculation
- Correct recipe output amounts
- Shared materials across multiple goals
- Live inventory tracking
- Support for carried Shulker Boxes
- Optional chest material sources
- Dynamic next crafting and smelting steps
- Multiple pinned crafting goals
- Completed goal tracking
- Separate plans for every world and server
- Customizable HUD
- English and German language support

Craft Planner only plans and tracks your progress. It does **not** automatically craft items or move items between inventories.

## How it works

Open the planner, search for an item and choose how many you want to craft.

Craft Planner calculates everything needed for the goal and compares it with the items you currently have.

As you gather materials or craft intermediate items, the plan updates automatically.

Having all raw materials does not immediately complete the goal. The planner continues showing the remaining crafting steps until the final items are actually made.

Existing intermediate items are also taken into account, so already having sticks, stone or other components skips unnecessary earlier steps.

## Material Sources

Craft Planner can use items from:

- Your inventory
- Your offhand
- Carried Shulker Boxes
- An opened chest that has been added as a Craft Planner source

Closed chests are still remembered and shown as previously observed storage locations, but their contents are not included in the live material total until they are opened again.

## Completed Goals

When you finish a crafting goal and deposit the target items into a normal chest, Craft Planner can track the deposited amount.

Partial deposits reduce the remaining goal amount.

Once the complete target amount has been deposited, the goal is archived and removed from the active HUD.

## Controls

| Key | Action |
| --- | --- |
| `G` | Open Craft Planner |
| `H` | Toggle the HUD |

Both keybinds can be changed in:

**Options → Controls → Key Binds → Craft Planner**

## HUD

The HUD can display:

- Pinned crafting goals
- Current progress
- Missing materials
- Next crafting steps

Its position, size, width, height and opacity can be customized in the settings.

By default, up to three goals can be pinned at the same time. The limit can be changed in the settings.

## Language

Craft Planner currently includes:

- English
- German

The language automatically follows your selected Minecraft language.

## Requirements

- Minecraft **26.3**
- Java **25**
- Fabric Loader **0.19.5+**
- Fabric API **0.163.0+26.3**

### Optional

- Mod Menu

Craft Planner is completely client-side. No Craft Planner installation is required on the server.

## Installation

1. Install Fabric Loader for Minecraft 26.3.
2. Install Fabric API.
3. Download Craft Planner.
4. Put the Craft Planner `.jar` into your Minecraft `mods` folder.
5. Start Minecraft with your Fabric installation.

If you are updating from an older version, remove the old Craft Planner `.jar` first.

## Building from source

Craft Planner uses Java 25.

```bash
./gradlew build
```

On Windows:

```bat
gradlew.bat build
```

The finished mod can be found in:

```text
build/libs/
```

## Multiplayer

Craft Planner works on multiplayer servers without requiring a server-side installation.

However, recipe planning is limited to recipe information that the server provides to the client.

## Supported Recipes

Craft Planner supports planning for common recipe types including:

- Crafting Table recipes
- Furnace recipes
- Blast Furnace recipes
- Smoker recipes
- Stonecutter recipes
- Fixed Smithing Table results

Some special or highly dynamic recipes may not be fully supported.

## License

See the repository license for details.

---

Made for people who start a crafting project, gather half the materials, and then forget what they were doing.
