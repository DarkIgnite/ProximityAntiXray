# ProximityAntiXray

**Author:** DarkIgnite  
**Target:** Minecraft Paper 1.21.x (with ProtocolLib)

Ultra-lightweight proximity-based spawner, chest, and structure hider designed specifically to defeat **Freecam** and **TileEntity / Spawner ESP** on Paper Minecraft servers.

---

## Features

- **Defeats Freecam & Spectator:** Freecam only detaches the client camera while the player's server coordinates remain outside. Spawners, chests, and trial structures physically do not exist in the chunk packet until the player's body gets within close range.
- **Ultra-Lightweight (~0.001% CPU):** Replaces heavy 3D voxel raycasting with simple distance mathematics ($\Delta x^2 + \Delta y^2 + \Delta z^2 \le r^2$).
- **Trial Chamber & Vault Protection:** Fully hides `trial_spawner` and `vault` blocks in Minecraft 1.21.
- **Pairs with Paper Engine Mode 2:** Let Paper's native Engine Mode 2 blindfold cave-outline X-Rayers with fake ores, while ProximityAntiXray eliminates Freecam & ESP!
- **Zero Chunk Bloat:** Strips tile entities directly from chunk packets before network dispatch.

---

## Installation

1. Install **ProtocolLib**.
2. Drop `ProximityAntiXray.jar` into your server's `plugins/` directory.
3. Configure `plugins/ProximityAntiXray/config.yml` as desired.
4. Restart your server.

---

## Commands

- `/proximityantixray` (Aliases: `/panti`, `/panti-xray`): Shows plugin status and tracked players.
- `/panti timings`: Toggles tick execution time diagnostics.

---

## License

Created by DarkIgnite.
