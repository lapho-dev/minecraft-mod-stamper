# Stamper

A Minecraft mod that adds one block.

The **Stamper** copies a custom name from a reusable template item onto a stream of other items,
and dispenses the result on a redstone pulse. It never invents a name — the name has to already
exist on some item, which means someone already paid for it at an anvil.

Think of it as **a dropper that stamps items on the way out.**

## What it does

Three slots, laid out like an anvil with the text field removed:

```
[Template]   [Input]   -->   (Result)
```

- **Template** — the item whose name gets copied. Never consumed. Put a chest named "Bye" here.
- **Input** — the items to be named.
- **Result** — a preview only. You cannot take it out; it leaves through the front on a redstone
  pulse.

| Template | Input | Out |
|---|---|---|
| *(empty)* | carpet named "hello" | carpet, name **stripped** |
| plain chest | carpet named "hello" | carpet named *"Chest"* |
| chest named "bye" | carpet named "hello" | carpet named "bye" |

## Automation

Faces are specialised, furnace-style. The vertical axis carries the template; the horizontal
plane carries the items.

| Face | Slot | In | Out |
|---|---|---|---|
| Top | Template | yes | no |
| Bottom | Template | yes | yes |
| 3 horizontal sides | Input | yes | no |
| Front | — | no | no |

A redstone pulse stamps one item and sends it out the front — into a container if one is there,
onto the ground if not. A comparator reads the **template** slot only.

## Recipe

```
iron   paper    iron
iron   anvil    iron
redst. dropper  redst.
```

## Status

**v1.0.0 — released for Minecraft 1.21.11**, on both **Fabric** and **NeoForge**.

Behaviour matches [docs/SPEC.md](docs/SPEC.md) and the full test plan in
[docs/TESTING.md](docs/TESTING.md) passes on both loaders.

Ports to **1.21.1** and **26.2** are planned; see [docs/TODO.md](docs/TODO.md). Forge is out of
scope.

Licensed **MIT**. No runtime dependencies on NeoForge; the Fabric build requires **Fabric API**.

## For contributors

Start with [CLAUDE.md](CLAUDE.md), then:

| Document | What it covers |
|---|---|
| [docs/SPEC.md](docs/SPEC.md) | Exact behaviour. The contract. |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Layer map, why no Architectury, reuse plan |
| [docs/VERSIONING.md](docs/VERSIONING.md) | Support matrix and what breaks between versions |
| [docs/TODO.md](docs/TODO.md) | The only list of outstanding work |
| [docs/TESTING.md](docs/TESTING.md) | Three-tier test plan |
| [docs/ART.md](docs/ART.md) | Asset brief |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Why things are the way they are |

The short version: **the spec is authoritative**, `core/` stays pure, and nobody ports from
memory.
