# MineGTA — Grand Theft Auto in Minecraft

Two halves that work together, both built by GitHub Actions on every push:

| jar / dll | what it is | built by |
|---|---|---|
| `minegta-<version>.jar` | the **GTA in Minecraft** Fabric mod: a GTA HUD (radar, wanted stars, health and armor arcs, cash, ammo, WASTED), GTA weapons (pistol, micro SMG, pump shotgun, carbine rifle, RPG), and an LSPD that grows a wanted level on you. | `mods.yml` |
| `passthrough-<version>.jar` | the **Minecraft side**: it runs as a normal Minecraft mod, publishes its frames (world colour + the hand/HUD overlay) to shared memory and takes the host game's camera, ground and keys. | `mods.yml` |
| `MCOnly.asi` | the **GTA V side** (ScriptHookV + a D3D11 hook). It draws *only* Minecraft's picture — no ReShade, no depth compositing, GTA's own world is drawn and then replaced. | `gta-plugin` |

Minecraft: **26.3 + Fabric Loader 0.19.5 + Fabric API**. Java 25. GTA V: **Legacy** (`GTA5.exe`), story mode only.

## The picture you get

Start the Minecraft profile, then start GTA V in story mode. GTA's camera drives Minecraft's, GTA's player is
what Minecraft's Steve stands as, and every frame of GTA's window is Minecraft's frame instead of GTA's.
GTA's own world is still rendered (and still runs its collision, so you can lean on it), you just never see
it — until you walk into a menu, where the passthrough turns off and GTA's picture comes back.

If the passthrough mod is not running when you start GTA, the screen stays a dark slate: that is
"waiting for Minecraft", not a crash. `MCOnly.log` in the GTA folder says what it is doing.

Keys: **F7** turns the passthrough off and on, **F8** re-levels Minecraft's ground to where you stand, left
and right mouse are Minecraft's attack and use, the wheel and `1`-`9` are the hotbar, `Tab` is gun mode.

## Downloads

Every push builds three artifacts in the Actions run (`github.com/ladnojak-ship-it/minegta/actions`):

| artifact | goes into |
|---|---|
| `minegta-mod` → `minegta-*.jar` | `minecraft/mods/` |
| `passthrough-mod` → `passthrough-*.jar` | `minecraft/mods/` |
| `MCOnly-asi` → `MCOnly.asi` | the GTA V folder |

## Building it yourself

```powershell
# the GTA side (needs MSVC plus the ScriptHookV SDK, which is fetched, not vendored)
gta\fetch_sdk.ps1 -WithRuntime     # ScriptHookV SDK headers + ScriptHookV.lib, and the runtime DLLs
gta\build.bat                      # -> gta\build\MCOnly.asi
gta\install.ps1                    # copies it (and ScriptHookV, if absent) into GTA V
gta\install.ps1 -Remove            # takes MCOnly.asi back out
```

```bash
# the Minecraft side (needs a JDK 25)
./gradlew build                  # minegta-*.jar
./gradlew -p mc build            # passthrough-*.jar
```

## Safety

- Story mode only, never GTA Online: BattlEye protects it and ScriptHookV refuses to run online anyway.
  Keep `-nobattleye -noBE` in `args.txt` (or the Rockstar launcher's toggle).
- Don't automate clicks on GTA's landing page while someone is at the keyboard — pick Story Mode by hand.
- `install.ps1` only adds files, never replaces another mod's `dinput8.dll` (ASI loader) without `-Force`,
  and `-Remove` deletes exactly what it added.
- Nothing from GTA V, Minecraft, ScriptHookV or ReShade is in this repo; the SDK and the runtime are
  downloaded when you build.

## Credits

- [ScriptHookV](https://www.dev-c.com/gtav/scripthookv/) and its ASI loader, by Alexander Blade.
- Fabric (Loom, Fabric Loader, Fabric API) and [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket).
- The GTA native hashes come from alloc8or's [native DB](https://github.com/alloc8or/gta5-nativedb-data).
- Minecraft belongs to Mojang Studios and Microsoft, GTA V to Rockstar Games and Take-Two: fan project.
- Built with AI.
