# Overview

This mod is made to address one gripe I've alway had with create mod: Needing to have items on hand to set redstone link frequencies is just too inconvenient. The release of Aeronautics exacerbated this problem with how much redstone link usage it demands, motivating me to make this mod.

---

## Installation

Modrinth: https://modrinth.com/mod/create-redstone-link-gui

Curseforge: https://www.curseforge.com/minecraft/mc-mods/create-redstone-link-gui

## Feature list

### 🔗 Frequency GUI

- Shift right-click a Redstone Link or Void with an **empty hand** to open a GUI (shift-click behavior configurable in **client side** settings)
- Set items in frequency slots directly from your inventory
- **JEI/EMI** support — drag items from JEI/EMI onto frequency slots
- Switch between **Sender** and **Receiver** mode directly from the GUI

### 📋 Preset System

- **4 preset rows** for saving/loading frequency configurations
- **Copy button** — saves current link frequencies into a preset row
- **Paste button** — applies saved preset frequencies to the current link

### 🔄 Relocate

- Move a Redstone Link block to a new position using the **Move button** in the GUI
- Visual feedback: green highlight for valid targets, red for invalid
- Respects Factory gauge movement constraints (same surface, within range) if one is attached to the redstone link
- Configurable move range (`MOVE_RANGE` in common config, default 24 blocks, max 256)
- **Sable** integration for sublevel-aware distance calculations

### 👤 Create Utilities Mod Integrations: Void Link Ownership

- **Claim** / **forfeit** ownership of Void Links via a skull button
- Visual indicator: skeleton skull when unowned, player head when owned
- Optional dependency

### 🟨 Frequency Create Mod Integration

- If **Frequency Create** mod is installed, Middle-click on frequency symbols inside frequency slot to open symbol picker menu
- Register all of **Frequency Create**'s items to JEI menu for easier picking

### 🎨 Compatible Blocks

- Works with Create's **Redstone Link** and any block using the `LinkBehaviour` method from create(meaning anthing that uses Create's frequency system)
- Special compatibility with Create Utilities' **Void Link** and any other blocks using `VoidLinkBehaviour`
- Special compatibility with Tiny Create's Tiny Redstone Link
- Special compatibility with Create: Propulsion Simulated's Vector Thrusters

### ⚙️ Config

- **Client config**: Right Click modes: (Shiift Slot, Shift Block, Slot)
- **Common config**: Move range (1-256 blocks)

## Disclaimer:

AI coded with some human review. I do my best to keep the code bug-free and clean, but no guarentees.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
