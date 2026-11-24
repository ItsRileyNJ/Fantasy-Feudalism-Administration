# FTB Quests and Denar

[Wiki home](Home.md) · [Denar economy](Economy-and-Shops.md) · [Compatibility](Mod-Compatibility.md)

FFA **0.10.0 or later** connects FTB Quests to the server’s Denar wallets. Install FFA only on the server.

Use FTB Quests' existing **Custom Reward** and **Custom Task** entries. An administrator puts the Denar amount in their built-in **Tags** field. There is no coin item, additional FFA client mod, script, permission-elevated command reward, or shared Denar account. FTB Quests and its own dependencies still need to be installed wherever those mods require them.

## Versions and prerequisites

The adapter targets the uploaded **`ftb-quests-neoforge-2101.1.39.jar`** for Minecraft 1.21.1. That JAR requires **FTB Library 2101.1.40 or later**, **FTB Teams 2101.1.9 or later**, and **Architectury 13.0.11 or later**. Use their matching Minecraft 1.21.1 NeoForge versions. The runtime suite used the uploaded **FTB Library 2101.1.40**, **FTB Teams 2101.1.12**, and **Architectury 13.0.12** alongside Quests 2101.1.39. No third-party JARs are bundled with FFA.

**FTB Teams is required even for solo quests.** It provides personal progress groups automatically; players do not need to create or join a party. Party quests are optional.

Leave `modules.economy = true` in `config/ffa/server.toml`. Players use the same Denar wallet as `/ffa balance`, Easy NPC Denar offers, and FFA shops. All amounts are positive whole numbers and must fit the configured maximum balance.

## Example 1: award 250 Denar

1. Open the FTB Quests book as an administrator and enable its built-in editing mode.
2. Create a quest and its normal completion task, such as an item or kill task.
3. Add a reward and choose **Custom Reward** (the built-in `ftbquests:custom` reward). Do **not** choose Item Reward or the separate Currency Reward used by other currency providers.
4. Edit the reward. Give it a readable title such as **250 Denar**, optionally choose a gold-nugget icon, and add this single entry to **Tags**:

   ```text
   ffa_denar_reward_250
   ```

5. Set **Team Reward = False** for a separate claim for each eligible player, or **True** for one claim for the whole team. Set **Auto-claim = Disabled** for this example so players choose when to receive the money.
6. Save the edit and leave editing mode. Complete the quest as a normal test player and claim the reward. Their wallet increases by **250 Denar**; their inventory receives no currency item. `/ffa balance` shows the new total.

If the wallet is too full, the reward is refused and remains unclaimed. The player can spend or transfer some Denar and claim it again. Disabled economy, unavailable adapter, or pending financial recovery also refuse payment instead of consuming the reward.

## Example 2: pay 1,000 Denar to complete a task

1. In the same quest editor, add a **Custom Task** (`ftbquests:custom`).
2. Set its title to **Pay 1,000 Denar**. Explain in the task/quest description that pressing its button pays from the player's FFA wallet.
3. Add this entry to the task's **Tags** field:

   ```text
   ffa_denar_pay_1000
   ```

4. Save the edit. FFA enables FTB's existing **Submit** button and gives the task one completion step. No item belongs in a trade or submission slot.
5. Test as a normal player with 1,500 Denar. Clicking Submit subtracts **1,000**, leaving **500**, and completes the task. With less than 1,000, the wallet and task remain unchanged.

Payment is deliberate: FFA does not charge when a player opens the quest book, walks around, logs in, or receives a periodic task check. Dependencies, task ordering and locked progress must allow submission first. Players must accept FFA's server rules; jailed players cannot submit payments.

If this payment task is the only task and the quest awards the **250-Denar** reward above, the full example is **1,500 → 500 → 750**. Payment and reward are separate operations; finishing a payment task does not claim its reward unless the quest maker enabled auto-claim.

## Team quests and balance

Wallets remain **individual**. FTB Teams membership does not create a pooled bank account.

| Quest setting/action | Result |
| --- | --- |
| Payment task in a shared quest | One submitting player pays the entire cost. Native team progress completes once; teammates are not also charged. |
| Team Reward = True | One successful claim per team/completion. The full amount goes to the claiming player's wallet. It is not copied into every member's wallet or automatically divided. |
| Team Reward = False | Each eligible player has their own claim and receives the configured amount into their own wallet. The total payout can therefore increase with team size. |
| Auto-claim enabled | FTB's own auto-claim rules apply. Individual rewards can pay each eligible online member; use manual claims for the clearest control. |
| Repeatable quest | FTB's completion cycle permits one new payment/claim for each legitimate repeat. Repeated clicks within the same cycle do not pay or charge again. |

For a fixed-cost team quest, **Team Reward = True** keeps its total Denar payout fixed regardless of party size. If you choose individual rewards, budget for all eligible members. A repeatable quest charging 1,000 and giving each of eight members 250 would create 1,000 net Denar per repeat; decide whether that income is intended. FFA respects the amounts you configure rather than adjusting them silently.

Players can voluntarily reimburse a payer or distribute a team reward using `/ffa balance send PLAYER AMOUNT`. Nobody is automatically charged on someone else's behalf. Individual reward receipts are tied to player UUID and reward ID, so changing teams does not reissue the same individual reward cycle.

## Tags and editing

| Editor object | Tag example | Meaning |
| --- | --- | --- |
| Custom Reward | `ffa_denar_reward_1000` | Credit 1,000 Denar after a successful claim |
| Custom Task | `ffa_denar_pay_1000` | Debit 1,000 Denar when the player submits |

Use **exactly one** `ffa_denar_...` tag on the relevant task/reward, with a positive whole amount. Lowercase letters, underscores and digits match FTB's Tags editor. Do not add spaces, commas, decimals, minus signs, or leading zeroes. Unrelated tags can coexist. Put the tag on the **task or reward itself**, not on the chapter or parent quest.

Normal Custom Tasks and Custom Rewards without an FFA Denar tag keep their original behavior. Invalid or conflicting Denar tags refuse the financial action and give the player a short error. Other mods' currency providers and Easy NPC's existing trade configuration are unchanged.

Editing an amount before use is fine. Editing an already processed amount does not trigger an extra debit/credit in the same cycle. Test with fresh task/reward IDs when changing a previously processed example. Creating a new ID intentionally creates a new financial offer.

## Persistence, retries and troubleshooting

FFA commits the wallet change and a transaction receipt together in `<world>/ffa/state.json`. FTB then records its claim or task progress using its normal system. If the server stops between those steps, retrying the same cycle restores the FTB step without paying or charging twice. A teammate can restore an interrupted team payment without being charged again.

These are separate mods' save files. Back up the entire world, FTB quest definitions and FFA state together. FFA's own snapshots do not replace a complete FTB/world backup. Do not independently roll FTB progress back to an older cycle while keeping newer FFA receipts; stale cycles are refused. Manually resetting the same completed quest does not erase financial history or automatically authorize another payout. Use FTB's repeatable-quest feature for normal repeats.

| Problem | Check |
| --- | --- |
| No Submit button | Use Custom Task, put the tag on that task, save the edit, and reopen the quest. Check that the matching FFA build is installed on the server. |
| Reward gives nothing | Use Custom Reward with the reward tag, not Currency Reward or an item named Denar. Check the server's adapter status/error log. |
| “You need … Denar” | Only the submitting player's wallet funds the task; a teammate's balance is not used. |
| Wallet cannot hold the reward | Spend or transfer some money, then claim again. |
| Progress is older than Denar history | FTB progress was rewound separately. Restore a coherent backup or test with fresh task/reward IDs; repeated claims must not bypass the saved receipt. |
| Team members see different wallet totals | Expected: Denar is personal. Team Reward = True pays the claimant once, not every member. |
| Integration is unavailable | Check FTB Quests 2101.1.39, its dependencies, the matching FFA build and `modules.economy`. Other Quests versions need adapter verification. |
