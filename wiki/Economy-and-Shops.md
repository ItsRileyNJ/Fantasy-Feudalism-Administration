# Denar economy and administrator shops

[Wiki home](Home.md) · [Easy NPC integration](Mod-Compatibility.md)

Denar is a **virtual account balance**, not a registered coin item. Amounts and prices are whole numbers. The default starting balance for a newly created FFA account is **0 Denar**.

## Create a working shop

As an administrator:

```text
/ffa shop set stone minecraft:stone 5 2
```

This means a player pays **5 Denar per stone** when buying and receives **2 Denar per stone** when selling. A normal player uses:

```text
/ffa shop list
/ffa balance
/ffa shop buy stone 10
/ffa shop sell stone 4
```

Buying 10 costs 50 Denar. Selling 4 earns 8. Quantities are item counts, not stacks, and must be 1..2304; actual available funds, matching items and inventory space are checked.

Administrator shops have no finite stock counter in this release. Buying creates the offered items; selling consumes the sold items rather than depositing them in an NPC/chest stock inventory. The command interface is the native shop interface; there is no separate FFA merchant GUI.

## Price rules

- A nonnegative buy price enables purchases. **0 means free.**
- A nonnegative sell price enables sales. **0 consumes the sold items for no payment.**
- **-1 disables that direction.** For a buy-only offering, use `5 -1`; for a shop that only buys from players, use `-1 2`.
- When buying is enabled, the sell price cannot exceed the buy price.

```text
/ffa shop set stone minecraft:stone 6 2
/ffa shop delete stone
```

Setting an existing shop ID updates it. Item IDs can be vanilla or registered mod items. Offers use the item's default components; custom-named, enchanted, damaged or otherwise modified items are not automatically equivalent for a sale. Main inventory slots are used; spare armor/offhand capacity is not counted as ordinary shop storage.

## Give a player their initial funds

The player must be online:

```text
/ffa economy set Alice 100
```

This **sets the total balance to 100**; it does not add 100 to the current amount. There is no separate `/ffa economy give` command. Use the audit trail when correcting an account.

`economy.startingBalance` changes the default for newly created accounts. It does not retroactively fund existing players. The default maximum balance is 9,000,000,000,000,000 Denar.

## Optional transfers and leaderboard

Edit the existing `[modules]` section, then restart:

```toml
pay = true
leaderboard = true
```

Players can then use:

```text
/ffa pay Bob 25
/ffa baltop
```

Transfers require an online recipient, a positive whole amount, sufficient funds and the configured limits. Default maximum transfer: **1,000,000 Denar**. There is no configured transaction fee.

The optional leaderboard displays stored player **UUIDs** and balances in the current release. Enable it only when that visibility fits your server; rank command permissions can restrict access. Disabling the economy does not delete account records.

## Transactions and NPCs

Purchases/sales use one shared journaled server transaction path. Inventory capacity/contents and balances are validated before applying the change. Interrupted transactions are recovered on reconnect when the saved inventory matches a known snapshot; uncertain recovery locks the account rather than issuing guessed rewards.

Easy NPC buttons can execute the same player shop commands at ordinary player permission. Its native merchant screen does not automatically spend Denar. Mercenaries' own payment items are also not replaced by this virtual ledger. See [the exact bridge setup](Mod-Compatibility.md) and [recovery](Backups-and-Recovery.md).
