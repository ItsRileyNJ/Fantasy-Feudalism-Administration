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
/ffa balance set Alice 100
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

## Turn an admin sign into a shop

Create a shop offering, place an ordinary sign, look directly at it within six blocks, then bind it:

```text
/ffa shop set apples minecraft:apple 5 2
/ffa shop sign bind apples 1
```

The optional number is the quantity per click (default one). **Right click buys** and **left click sells**. Labels show the offering, quantity and total prices; price changes refresh loaded linked signs. Disabled buy/sell directions remain disabled. Buyers still need the public shop permission and sufficient funds/space; sellers need the items. The existing durable item/currency journal handles each trade.

Only full administrators can bind/unlink signs. Linked signs work inside protected spawn without opening general block interaction. A short click cooldown prevents accidental repeats; holding left click does not repeatedly sell. Signs are waxed and protected against breaking while linked.

To remove one, look at it and run `/ffa shop sign remove`, then edit or break it normally. Unlink signs before deleting their shop offering. Replacing a sign cannot inherit the old link. Both faces show the shop labels; unlinking restores its prior wax state.

## Send or request Denar

```text
/ffa balance send Alice 100
/ffa balance request Alice 100
/ffa balance accept
/ffa balance deny
```

Amounts are positive whole Denar, up to `economy.maximumTransfer`. Sending transfers immediately. Requesting asks the named player to pay **you**; their clickable Accept/Deny prompt states the amount. A request never takes money until its payer accepts. Acceptance rechecks funds, permissions, online visibility and balance limits. Expired, denied, stale or replayed buttons cannot debit anyone. Failed acceptance consumes the request; send a new request after correcting the issue.

Only one incoming request per payer and one active outgoing request per requester are allowed. Requests use the configured request cooldown/expiry and are cancelled when either participant disconnects. UUID tokens in buttons are generated automatically; players can use the bare accept/deny command for their current request.

These balance subcommands are available with the economy module. The legacy `modules.pay` setting controls only the older `/ffa pay` alias. Both `ffa.command.balance` and `ffa.command.pay` must allow ordinary players to use the new transfer/request actions; explicit denies still apply. Vanished senders must leave vanish before sending identity-bearing payments or requests.

## Uppercase shop names

Shop names now accept uppercase/lowercase letters, digits, underscores and hyphens. Lookup ignores case, so `DiamondShop`, `diamondshop` and `DIAMONDSHOP` address the same shop. A newly created name preserves its casing; changing prices through another casing updates the existing shop rather than creating a duplicate. Existing signs keep their canonical shop link. Item registry IDs remain lowercase.

## Configurable buy and sell limits

Limits count **item units**, from the player's perspective: buy means players buying from the shop; sell means players selling to it. Each direction can have a different limit. `-1` means unlimited and `0` means no remaining capacity.

```text
/ffa shop limits DiamondShop shared manual 100 50
/ffa shop limits DiamondShop player daily 10 20
/ffa shop limits DiamondShop player manual 25 25
/ffa shop limits DiamondShop
/ffa shop reset DiamondShop
/ffa shop reset DiamondShop Alice
```

Choose the configuration that suits each shop:

| Scope/reset | Meaning |
| --- | --- |
| `shared manual` | Everyone shares the totals until a manager resets them. |
| `player daily` | Every UUID has its own allowance, resetting at 00:00 UTC. Offline time counts. |
| `player manual` | Every UUID has its own allowance until reset. |
| `shared daily` | Shared totals with the same daily reset, if useful. |

A reset without a player clears all counters for that shop. A player argument resets only that UUID's counters on a per-player shop; use a full UUID for an offline player. Changing numeric limits or prices retains usage. Changing scope/reset policy starts a fresh set of counters. Legacy shops start unlimited.

Commands and sign clicks share these limits, including administrator trades. An oversized trade is refused in full; it does not partially charge or consume items. Prepared transactions reserve capacity. Successful recovery commits the balance and usage counters once; unapplied recovery releases the reservation. A shop with unresolved journals cannot be reset, reconfigured or deleted until recovery, preventing limit bypass. Recover legacy prepared transactions before editing shop definitions.
