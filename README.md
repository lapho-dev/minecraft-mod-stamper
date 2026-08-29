# Stamper
This mod adds exactly one block to a vanilla Minecraft experience - stamper. Stamper block is used to copy and paste a custom name from one item to another. This provides automation to "naming", which an anvil cannot.


## Description
The stamper block ejects similarly to a crafter, and the IO is similar to that of a furnace. 
Slot 0: Extract the name of the item in that slot (both original/custom name)
Slot 1: The item(s) to be named. It will be named as what's provided in slot 0. If there is no item in slot 0, the item will revert to its original name (erase any custom name).
Slot 2: A ghost preview of the named item.

The template item in slot 0 is never consumed.

### IO
The top and bottom are dedicated to Slot 0, the stamp template (extracting name). 
The 3 sides are the input for slot 1.

The front is where the product ejects. Upon a redstone pulse (just like a crafter), it will drop an item out into space or inventory.

### Redstone
The stamper block accepts redstone pulse in the cycle of 8gt.

A comparator signal can be read out of the stamper block - it reads the inventory of only slot 0 (whether there are templates).

### Possible Usage
- Say you want to obtain many stacks of "filter item" in a storage system. You can name just one item "filter", and then use the stamper block to name an infinite amount of items with the name "filter"
- Erase the name of item(s)

## Author Note
The Stamper is functionally complete — it does everything it set out to do. Improvements and new features are still very much on the table. The art is functional rather than fancy. If you have thoughts, or would like to help, please let me know.

The idea of a "stamper" block originates from vanilla redstone mechanics, where "naming" hasn't been a way for automation. I look forward to this (or being added in vanilla) as it unlocks a whole new level of possibilities, e.g., redstone data storage. An advanced version of this (with prefix/ suffix) might be made in the future.

The work is assisted by Claude.

## Requirements

- **Fabric** — requires **Fabric API**
- **NeoForge** — no dependencies


Available in 16 languages. Licensed **MIT**.