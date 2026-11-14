# Easy NPC trading with Denar

[Wiki home](Home.md) · [Economy](Economy-and-Shops.md) · [Compatibility](Mod-Compatibility.md)

FFA can price selected offers in **Easy NPC 7.18.0's existing merchant menus** using the player's Denar wallet. Prices are explicit whole numbers, per NPC UUID and original offer number. No Mercenaries or Easy NPC patch is required. Easy NPC remains a separate optional mod, with its own client requirements; players do not need FFA installed.

## Price an offer

Create the NPC and its item offers in Easy NPC first. Then stand within eight blocks and **look directly at the NPC**, with no solid block between you and it:

```text
/ffa npctrade list
/ffa npctrade price 1 buy 25
/ffa npctrade price 2 sell 10
```

These commands are administrator-only and hidden from normal players. Offer numbers begin at **1** and follow Easy NPC's original offer order. `list` shows that order and current bindings.

| Direction | Items | Wallet |
| --- | --- | --- |
| `buy` | Give the original offer's complete result stack, preserving its components | Subtract the configured Denar price |
| `sell` | Take the original offer's input A and optional input B, matching component predicates | Add the configured Denar price; the original barter result is replaced |

Prices cover **one whole offer**, not one individual item. For an offer producing 32 stone, `buy 25` charges 25 Denar for all 32 stone. Selling uses the original base input quantities and component requirements, not villager demand discounts. Zero is allowed for a deliberately free offering; the maximum price is the configured maximum wallet balance.

Only NPCs with at least one configured Denar price are adapted. Their currency menu lists **only the explicitly priced offers**, in original offer order. Other offers on that NPC are hidden from the currency screen until priced. Unconfigured NPCs retain normal item barter. Copying an NPC to a new UUID does not copy FFA prices.

## Use the merchant menu

Open the NPC's normal trading screen and select an offer. The gold-nugget icon is labeled **Pay N Denar** or **Receive N Denar**. It is a wallet preview, not an obtainable coin. Hover to read the full price, including amounts larger than a stack.

Click the result slot to execute one transaction. Shift-click also executes **one** transaction. The menu closes after a successful trade and gives a short confirmation; reopen it to trade again. Goods go directly into the player's main inventory, and sales use main-inventory items. Armor, offhand and items held by another container are not used.

The merchant slots are read-only previews. Inventory rearranging, dragging and hotkeys are ignored while this currency screen is open. Close it to rearrange inventory. Full inventories, insufficient funds, missing matching items, wallet limits, jail, blocked NPC-trading regions, stale quotes and exhausted stock refuse the trade without charging.

## Stock and offer changes

Easy NPC's native maximum uses and restocking schedule remain authoritative. FFA saves used stock alongside the money commit so interrupted saves cannot create extra stock. Native XP/sound/trade-action callbacks run after a successful financial commit.

Changing a price closes existing trading screens for that NPC. Repricing an unchanged recipe preserves its used stock. If you change, replace or reorder the underlying Easy NPC offers, set the corresponding Denar prices again before trading; changed recipes are refused rather than silently charging a stale quote. Removing a bound offer likewise requires clearing or reconfiguring that binding.

```text
/ffa npctrade clear 2
/ffa npctrade clear all
```

Removing the last price restores the NPC's original item-trading menu. Clearing prices does not reset native stock. FFA does not alter unrelated NPC offers, dialogue, models, settlement membership or Mercenaries' payment system.

## Recovery and compatibility

Bindings, prices and used-stock records are in `<world>/ffa/state.json`. Transactions use FFA's durable prepare → player inventory save → wallet/stock commit journal. Reconnecting settles a prepared transaction once. An NPC with an unresolved transaction cannot trade or have its prices edited until recovery finishes. Keep world/player data and FFA state together in backups.

After a crash, financial recovery does **not replay NPC scripts or XP callbacks**, since external effects cannot safely be duplicated. A callback failure after financial commit is logged; the completed item/money exchange is not retried. The adapter targets the exact supplied Easy NPC **7.18.0** API. The older dialogue/player-command shop bridge remains available as described in [compatibility](Mod-Compatibility.md).
