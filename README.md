# Item Checklist (Fabric, Minecraft 26.3)

Client-side mod: you pick items and amounts, and the list appears in the top left of your screen
and is ticked off automatically once you have enough of them in your inventory.
Items inside **shulker boxes you carry in your inventory** count as well.

## Usage

| Key | Function |
|-----|----------|
| `K` | Open the editor |
| `J` | Show/hide the list |
| `Left` / `Right` arrow | Switch to the previous / next list (only while the list is visible) |

All keys can be rebound under Controls > Item Checklist.

The editor has four tabs:

- **Add items:** search for an item, type the amount into the "Amount" field, click the item. Items are added to the active list.
- **My list:** type the amount for each item directly or change it with `-64 / -1 / +1 / +64`; `X` removes the item.
- **Lists:** create as many lists as you like with "New list", rename a list by typing in its name field,
  make a list the active one with "Select", and delete it with `X` (a second click on "Sure?" confirms; empty lists are deleted right away).
  The active list is the one shown on screen and edited in the other tabs. With more than one list, the title on screen
  looks like `< Name >`.
- **Display:** type the size in percent (30 to 400) or change it with `-` / `+`, reset the position, show/hide the list.
  While the editor is open, you can drag the list itself anywhere on the screen with the mouse (yellow frame).

- **Import** (button at the bottom left of the editor): copy text with lines like `200 - Glass`, `2x Bucket of water` or
  `Glass: 200` to the clipboard and click the button. The lines are matched to items (English names
  also work in a German game). Unclear lines such as "Building block" are set to "skip";
  clicking the button on the right cycles through the suggestions. Optionally, the import replaces the existing list.

Everything is saved in `config/itemchecklist.json`. Configs from older versions (a single list) are converted automatically to one list named "List 1".


## Limitations

- Counted are the inventory, armor/offhand slots, the item on your cursor, and shulker boxes in your inventory.
- Shulker boxes that only stand in the world or lie in chests/ender chests cannot be read by a client.
- The mod runs purely client-side; nothing needs to be installed on the server.
