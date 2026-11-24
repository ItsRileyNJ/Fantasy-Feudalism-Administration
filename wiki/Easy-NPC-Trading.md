# Easy NPC trading with Denar

[Wiki home](Home.md) · [Economy](Economy-and-Shops.md) · [Compatibility](Mod-Compatibility.md)

FFA can price selected offers in the existing merchant menus in **Easy NPC 7.18.0 and 7.20.0** using the player's Denar wallet. Prices are explicit whole numbers, per NPC UUID and original offer number. No Mercenaries or Easy NPC patch is required. Easy NPC remains a separate optional mod, with its own client requirements; players do not need FFA installed.

## Worked example: a village baker

This example creates one NPC where players **buy 16 bread for 25 Denar** and **sell 16 wheat for 10 Denar**. Buy and sell are from the **player's** point of view. Denar is a saved wallet balance, not a Minecraft item: do not search Easy NPC's item picker for a Denar coin.

### 1. Create two ordinary offers in Easy NPC

As an administrator, create the NPC using Easy NPC and open its configuration/trading editor. Enable its trading interaction and save these two valid item offers, in this order:

| Original offer number | Easy NPC input A | Input B | Easy NPC result | Purpose after FFA pricing |
| --- | --- | --- | --- | --- |
| `1` | 1 emerald | Empty | 16 bread | Player buys the 16 bread with Denar; the emerald cost is replaced |
| `2` | 16 wheat | Empty | 1 emerald | Player sells the 16 wheat for Denar; the emerald result is replaced |

The emeralds above are ordinary placeholder barter costs/results needed to define valid Easy NPC recipes. With the FFA prices below, players **neither need nor receive those emeralds**. Set Easy NPC's maximum uses/restocking as desired; FFA respects its stock. Close the editor before running FFA commands. If your offers already exist in a different order, use their actual numbers instead of assuming `1` and `2`.

### 2. Bind the offers to Denar

Stand within **eight blocks**, look directly at that NPC with no solid block in the way, and run:

```text
/ffa npctrade list
/ffa npctrade price 1 buy 25
/ffa npctrade price 2 sell 10
/ffa npctrade list
```

These commands target the NPC you are looking at; there is no NPC-name argument. Keep looking at the same NPC for each command. The final list should show offer 1 as **buy for 25 Denar** and offer 2 as **sell inputs for 10 Denar**. For a sell offer, `list` still identifies the original barter result (the emerald); the wheat input defined in Easy NPC is what the player sells.

There is no need to create `/ffa shop` entries, sign shops, a physical currency item, a dialogue command, or a separate economy plugin for this native menu integration. Only explicitly priced offers appear in this NPC's Denar menu. Price all the offers you want players to see.

### 3. Test it as a player

Replace `Riley` below with an online test player's name. An administrator sets that player's **total** wallet balance to 100:

```text
/ffa balance set Riley 100
```

Give that test player at least 16 wheat and free main-inventory space. Close the NPC editor, then open the NPC's normal trading menu:

1. Choose the bread offer. Its gold-nugget preview says **Pay 25 Denar**. Click the result once: the player receives **16 bread**, and their wallet changes **100 → 75**. No emerald is consumed. The menu closes after the transaction.
2. Reopen the NPC's trading menu and choose the wheat sale. Its preview says **Receive 10 Denar**. Click the result once: **16 wheat** are removed from the player's main inventory, and their wallet changes **75 → 85**. No emerald is given.
3. The player runs `/ffa balance` to confirm **85 Denar**. Shift-clicking a result also performs only one transaction.

Do not put money or goods into the preview slots. Purchases use the wallet; sales take matching items directly from the player's main inventory. If the NPC is in protected spawn, leave that region's `npc_trading` flag enabled. Check Mercenaries' own NPC restrictions separately if it controls that NPC.

### 4. Change or remove a price

Look at the NPC again. To make the bread cost 30 Denar, run `/ffa npctrade price 1 buy 30`. To restore all of that NPC's original emerald barter recipes, run `/ffa npctrade clear all`. If you change the recipes or reorder them in Easy NPC, list them and set their corresponding prices again before players trade.

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

After a crash, financial recovery does **not replay NPC scripts or XP callbacks**, since external effects cannot safely be duplicated. A callback failure after financial commit is logged; the completed item/money exchange is not retried. The adapter supports the inspected Easy NPC **7.18.0 and 7.20.0** APIs; other versions require compatibility verification. The older dialogue/player-command shop bridge remains available as described in [compatibility](Mod-Compatibility.md).


## Updating to Easy NPC 7.20.0

Use FFA **0.8.2 or later**, with `easy_npc-neoforge-1.21.1-7.20.0.jar` and `easy_npc_config_ui-neoforge-1.21.1-7.20.0.jar`. Replace the previous versions rather than keeping two core or UI versions in `mods`. Stop and restart the server after updating.

The supplied `easy_npc_bundle-neoforge-1.21.1-7.20.0.jar` contains dependency metadata but **no embedded core or UI JARs**. It can accompany the matching core/UI files, but that uploaded bundle alone does not install them. Config UI is needed for Easy NPC's editing interface; FFA's Denar adapter accesses the core trading API.

The 7.20.0 trading API used by FFA matches 7.18.0. FFA's previous strict version check rejected 7.20.0; version 0.8.2 explicitly supports both inspected versions. NPC UUIDs, configured offers, Denar prices and used-stock records keep their existing format. No FFA shop recreation or state conversion is required. Keep matching Easy NPC versions on the server and clients as required by Easy NPC; FFA itself remains server-only.

## Troubleshooting the example

| What happens | What to check |
| --- | --- |
| `/ffa npctrade` is missing | Use an administrator account and FFA 0.8.0 or later; use 0.8.2 or later for Easy NPC 7.20.0. |
| The command cannot find the NPC | Stand within eight blocks, look directly at its body, remove solid obstructions, and check that it is an Easy NPC with offers. |
| `list` says there are no offers | Create and save the recipes in Easy NPC first; FFA pricing does not create recipes. |
| The screen still asks for emeralds | Verify prices on this exact NPC with `list`, close the old screen and reopen. A copied/recreated NPC has a different UUID and needs its own prices. |
| Only one offer appears | Only priced offers are shown once this NPC uses Denar. Bind the other original offer numbers too. |
| “Offers changed” or a stale-price refusal | The underlying recipe changed or moved. Close the screen and set its price again using the current original offer number. |
| Purchase or sale is refused | Check wallet funds/limits, free main-inventory space, exact matching input items, native Easy NPC stock, and `npc_trading` protection. Equipment/offhand items do not count as sale stock. |
| There is no Denar item in the item picker | Expected: Denar lives in FFA's wallet. Keep valid ordinary items in the original recipe, then assign a Denar price with FFA. |
