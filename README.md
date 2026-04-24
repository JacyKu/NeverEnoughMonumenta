# NeverEnoughMonumenta

NeverEnoughMonumenta is a client-side Fabric mod for Minecraft 1.20.4 that shows Monumenta items into JEI, EMI, and REI as synthetic item stacks.

## What it does

- Registers Monumenta items in JEI, EMI, and REI.
- Supports synthetic stacks and shift-scroll cycling for masterwork item ranks.
- Hide vanilla/Monumenta items with keybinds.

## Requirements

- Minecraft 1.20.4
- Fabric Loader 0.18.6+
- Java 17+
- Fabric API
- Entity Model Features 3.0.10
- Entity Texture Features 7.0.8
- CIT Resewn 1.1.4-1.1.5

JEI, EMI, and REI are optional viewer integrations. The mod can integrate with whichever of those are installed on the client.

## Data source and cache

- Item data is fetched from `https://api.playmonumenta.com/itemswithnbt`.
- Cached data is stored under the game config directory in `neverenoughmonumenta/monumenta-items-cache.json`.