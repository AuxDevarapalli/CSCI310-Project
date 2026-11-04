# SplitSmart — CSCI 310 Team 04 (Project A)

SplitSmart is a native Android app (Java) for USC students and alumni to record shared
expenses, split them, see who owes whom, and record payments made outside the app. It
implements the Project 2.1 requirements (FR 1.1 – FR 5.7, NFR 1 – NFR 4) using the
Project 2.2 design: MVVM over Firebase Authentication + Cloud Firestore.

**Team 04:** Zachary Chung · Aux Devarapalli · Edward Zhao · Swayam Parekh · Brian Shi

| Document | Where |
|---|---|
| Project 2.3 implementation document | [`docs/implementation-document/Team04_Project2.3_Implementation_Document.pdf`](docs/implementation-document/Team04_Project2.3_Implementation_Document.pdf) (the PDF submitted on Brightspace; its source and build script are in the same folder) |
| Revised diagrams (PlantUML sources + images) | [`docs/implementation-document/diagrams/`](docs/implementation-document/diagrams/) |
| Firestore security rules | [`firestore.rules`](firestore.rules) |

---

## 1. Run it (every teammate)

1. Install the latest **Android Studio**. It bundles the JDK; accept its prompts to install
   **Android SDK Platform 36** if asked.
2. Clone this repo and open the folder in Android Studio (*File → Open*). Let Gradle sync.
3. Make sure `app/google-services.json` exists (see section 2 — only one person creates it).
4. Create an emulator (*Device Manager → Create device*, e.g. Pixel 8, API 35 or 36) or plug
   in a phone, then press **Run ▶**.

Without `app/google-services.json` the project still builds and the unit tests still run,
but the app opens on a "Firebase setup needed" screen.

## 2. Firebase setup (one person, about 10 minutes)

The whole team should share **one** Firebase project so everyone sees the same data.

1. Go to <https://console.firebase.google.com> → **Create a project** (e.g. `splitsmart-team04`).
   Google Analytics is not needed.
2. On the project overview, click **Add app → Android**.
   * Package name: **`com.team04.splitsmart`** (must match exactly)
   * Nickname: SplitSmart. The SHA-1 is not needed (we only use email/password sign-in).
   * Download **`google-services.json`** and put it in the **`app/`** folder of this repo.
   * Skip the "add Firebase SDK" steps — the Gradle files already do that.
3. **Build → Authentication → Get started → Sign-in method → Email/Password → Enable → Save.**
4. **Build → Firestore Database → Create database.** Pick the *Standard* edition, any
   location (e.g. `us-west2 (Los Angeles)`), and **production mode**.
5. In **Firestore → Rules**, replace everything with the contents of
   [`firestore.rules`](firestore.rules) and click **Publish**. (Without this step every read
   fails with *"You don't have permission to do that."*)
6. Commit `app/google-services.json` so teammates get it with `git pull`.
   ⚠️ Keep this GitHub repository **private** — other teams build the same app, and the file
   identifies our Firebase project.
7. Optional: *Project settings → Users and permissions* → add teammates so they can browse
   the data in the console.

No composite indexes are needed: every query is either a single document read or a
single-field filter, and sorting happens in the app.

## 3. Tests

```bash
./gradlew test
```

Runs 74 JUnit tests for the business logic (money parsing/formatting, every split method
and the FR 3.5 rounding rule, pairwise balances, validation, history filtering and
case-sensitive search, monthly summaries, member-management helpers) using the exact examples
from the customer description. They need no Firebase.

Security-rule tests (optional, needs Node.js 20+ and Java 21; starts the Firestore emulator):

```bash
cd firestore-tests && npm install && npm test
```

29 tests check NFR 4 on the server side: members-only access, owner-only group changes,
creator-only edits, locked USC email/ID, unique USC IDs, valid document contents, settlements
recorded by either the payer or the receiver (but never by a third member), and the
leave / remove member / transfer ownership rules.

### Running the whole app against local Firebase emulators (optional)

Useful for testing without creating accounts in the shared Firebase project:

```bash
cd firestore-tests && npm run emulators                     # terminal 1 (after npm install)
cd firestore-tests && npm run seed:emulator                 # terminal 2: optional demo data
./gradlew installDebug -Psplitsmart.emulatorHost=10.0.2.2   # Android emulator running
```

`10.0.2.2` is how the Android emulator reaches your computer. Builds without the flag always use
the real Firebase project.

## 4. Demo (what the TA checks)

The TA's *Project 2.3 Demo Checklist* asks for four test accounts, at least two groups with
different owners, a group to delete, and records in more than one month. The app always stamps
today's date, so past-month records have to be prepared in the database. One command does all
of it:

```bash
cd firestore-tests && npm install && npm run seed   # real splitsmart-team04 project
```

Use `npm run seed:emulator` instead to fill the local emulators (section 3). The script signs in
as each demo user and writes through the normal security rules. Running it again first deletes
every group the demo accounts own, so the demo always starts from the state below. It never
touches anyone else's groups.

**Demo accounts** (password for all four: `splitsmart-demo`):
`alice.demo@usc.edu`, `bob.demo@usc.edu`, `carol.demo@usc.edu`, `david.demo@usc.edu`.
Switch users with *Profile → Log out*, or run two emulators.

| Group (owner) | What it shows | Expected numbers |
|---|---|---|
| **Apartment 4B** (Alice) | Separate totals (Feature 4) | Groceries $20.00 (Alice paid; Alice, Bob), Cleaning supplies $10.00 (Alice paid; Alice, Carol), Gas for the car $8.00 (David paid; David, Alice). Alice: **$15.00 to receive, $4.00 to pay**. |
| **Road Trip** (Bob) | Debts add up and offset | Gas $20.00 + Snacks $10.00 (Bob paid; Bob, Alice), Parking $8.00 (Alice paid; Alice, Bob). Alice owes Bob **$11.00**; a $5.00 settlement leaves **$6.00**. Carol is settled up. |
| **Study Group** (Carol) | No simplifying across pairs; recording an expense you're not part of | Textbook $20.00 (Bob paid; Bob, Alice), Printing $20.00 (Carol paid; Carol, Bob, *recorded by Alice*). Alice owes Bob $10.00, Bob owes Carol $10.00, **no** Alice–Carol balance. |
| **Dinner Club** (David) | Splits, history, search, settlements from both sides, two months of summaries | This month: Pizza night $30.00 Food (Alice paid, equal $10.00 each for Alice, Bob, Carol), Uber to the concert $20.00 Transportation (Bob paid; Bob, Carol), Carol → Alice $5.00 "Venmo for pizza" (recorded by Carol, who paid), Carol → Bob $4.00 "Cash" (recorded by Bob, who was paid). Last month: Concert tickets $30.00 Entertainment (percentage: David 50% $15.00, Bob 30% $9.00, Alice 20% $6.00, Carol 0%), Farmers market $30.00 Food (exact: David $15.00, Bob $10.00, Alice $5.00, Carol $0.00). Summary: this month **2 expenses, $50.00** (Food $30.00 + Transportation $20.00, settlements excluded); last month **2 expenses, $60.00**. Alice: receive $15.00, pay $11.00. |
| **Delete Me** (Alice) | Deleting a group removes its expenses and settlements | Test expense $12.00 and Bob's $6.00 settlement. |

**Settled-expense policy** (the checklist asks you to explain it): SplitSmart uses the accepted
*"allow changes"* policy. Expenses stay editable by their creator even after a settlement, because
balances are always recalculated from every expense and settlement. If an edit makes a settlement
larger than the new debt, the balance flips: for example, Bob settles his $10.00 with Alice, Alice
then edits the expense so Bob's share is $6.00, and Alice now owes Bob $4.00. Nothing is locked, so
deleting a settlement simply restores the debt it paid off. New settlements are still limited to
what is currently owed.

Demo script. You can combine rows, and doing the required features before the extras is safest:

| # | Requirement | What to show |
|---|---|---|
| 1 | FR 1.1–1.2 | Register Alice. Show errors for a non-`@usc.edu` email, a 9-digit USC ID, and re-using an email / USC ID that is already registered. |
| 2 | FR 1.3 | Log out, log back in (and a wrong password error). Profile: change the name; USC email and USC ID are read-only. |
| 3 | FR 1.4–1.6 | Create "Roommates" (description optional). Members tab: add Bob and Carol by USC email; show the errors for an unregistered email and an existing member. Show Bob sees the group after refresh. |
| 4 | FR 1.7 | As Bob, the Edit/Delete group menu items are missing. As Alice, edit the name. (Delete the group at the end of the demo.) |
| 5 | FR 2.1–2.6, 3.1–3.2, 3.6 | Add "Groceries" $10.00, payer Alice, equal split with Bob and Carol → preview shows $3.34 / $3.33 / $3.33. Show the date is automatic, Other is the default category, the payer can't be unchecked, and saving needs at least one other participant. |
| 6 | FR 2.3, 3.3 | Exact split: amounts that don't add up show "Amounts add up to $28.00, not $30.00" and Save is refused; "Fill evenly" helps. Show an amount with 3 decimals is rejected. |
| 7 | FR 3.4–3.5 | Percentage split $10.01 at 50/50 → $5.01 and $5.00. Percentages that don't total 100% are refused. |
| 8 | FR 4.1–4.4 | Balances tab: total to pay and total to receive shown separately, one line per member ("Bob owes you …", "You owe …", "settled up"). Show a chain (Alice→Bob→Carol) is **not** simplified. |
| 9 | FR 5.1–5.2 | Settlements from **either side**. As Alice in Road Trip: *Settle up → I paid → Bob*; $0 and more than $11.00 are refused; $5.00 leaves $6.00. As Alice in Dinner Club: Balances → *Record payment* next to Carol (*I was paid*). The details screen shows who recorded each settlement. |
| 10 | FR 5.4–5.6 | History: newest first, settlements labeled "Settlement"; keyword search on titles and notes ("cheese" finds an expense note, "cash" a settlement note); turn on **Match case**: "Pizza" finds only Pizza night, "pizza" only the "Venmo for pizza" settlement; filter by type, category, member; flip the sort order; open a transaction for full details. |
| 11 | FR 2.7–2.9, 5.3 | Edit an expense (balances/history/summary update) and delete one with the confirmation dialog. As another member, Edit/Delete are hidden. Same for a settlement. |
| 12 | FR 5.7 | Summary tab: pick a month; number of expenses, total, and per-category totals that add up; settlements excluded. |
| 13 | NFR 1, 3 | Close and reopen the app: data is still there. Changes made on device A appear on device B after pull-to-refresh. |
| 14 | Live updates | With both devices on the same group, add an expense on device A: device B's History and Balances update **without** refreshing. Add someone to a group: it appears on their home screen immediately. |
| 15 | Member management | As the owner, Members tab → ⋮ next to Carol → *Remove from group* while she still owes money → refused with the amount. As Bob (settled up), menu → *Leave group*. As the owner, ⋮ → *Make owner* on another member, then show the old owner now sees *Leave group* instead of Edit/Delete. Bob's old expenses still show his name. |
| 16 | Extras | Home screen totals per group, "you lent / you borrowed" in history, Settle-up and Record-payment shortcuts on balances, Match case search, Share balances, month picker, dark mode. |

## 5. How the code is organized

`app/src/main/java/com/team04/splitsmart/` follows the 2.2 design's six components:

| Component | Package | Main classes |
|---|---|---|
| C1 UI Views | `ui` | `LoginActivity`, `RegisterActivity`, `GroupListActivity`, `ProfileActivity`, `GroupFormActivity`, `GroupDetailActivity` (+ `HistoryFragment`, `BalanceFragment`, `SummaryFragment`, `MembersFragment`), `ExpenseFormActivity`, `SettlementFormActivity`, `TransactionDetailActivity`, `ConfirmDialog`, adapters and helpers |
| C2 ViewModels | `viewmodel` | `BaseViewModel` and one ViewModel per screen, `ViewModelFactory`, `AppContainer` |
| C3 Business logic & model | `logic.validation`, `logic.split`, `logic.balance`, `logic.history`, `model` | `Money`, `InputValidator`, `DateProvider`, `SplitCalculator`, `BalanceCalculator`, `HistoryFilter`, `SummaryCalculator`; `User`, `Group`, `Expense`, `Settlement`, `Share`, enums |
| C4 Repositories | `data` | `AuthRepository`, `UserRepository`, `GroupRepository`, `TransactionRepository`, `DataCallback`, `FirestoreMapper` |
| C5 / C6 | Firebase SDK | Firebase Authentication, Cloud Firestore (+ `firestore.rules`) |

Rules of thumb that keep the design intact:

* **Views only talk to ViewModels**; ViewModels call the engines (C3) and repositories (C4);
  **only `data/` touches Firebase.**
* **Money is always `long` cents** (`Money.parseDollars` / `Money.format`); never `double`.
* **Balances are computed on demand** from the transactions (`BalanceCalculator`), never stored.
* Every write is checked three times: the UI hides the action, the ViewModel re-checks the
  owner/creator, and `firestore.rules` enforces it on the server.

Firestore layout (see `FirestoreMapper`):

```
users/{uid}                         name, uscEmail, uscId, createdAt
uscIds/{uscId}                      uid                       (reserves the USC ID, FR 1.2)
groups/{groupId}                    name, description, ownerId, memberIds[], createdAt
groups/{groupId}/transactions/{id}  type, creatorId, date (YYYY-MM-DD), createdAt, updatedAt, note
    EXPENSE:    title, totalCents, category, payerId, splitMethod, shares[], participantIds[]
    SETTLEMENT: payerId, receiverId, amountCents
```

## 6. Ideas for improving it further

* Notifications when you are added to a group or an expense involves you (R12 in the
  implementation document analyses the design impact).
* UI tests with Espresso, and ViewModel tests with fake repositories.
* A charts library for the monthly summary (charts are optional and not graded).

## 7. Troubleshooting

* **"Firebase setup needed" screen** — `app/google-services.json` is missing; see section 2.
* **"You don't have permission to do that."** — the rules in `firestore.rules` were not
  published, or the Firestore database was not created.
* **Gradle JDK errors** — *Settings → Build Tools → Gradle → Gradle JDK*: choose the bundled
  JetBrains Runtime (JDK 17 or 21).
* **SDK 36 not found** — *Tools → SDK Manager* → install Android 16 (API 36).

## 8. How this was verified

The app follows our 2.1 requirements and 2.2 design. It has been verified as follows:

* full Gradle build (`./gradlew assembleDebug`) with the real libraries;
* 74 JUnit tests for the business logic, all passing;
* `firestore.rules` tested against the real Firestore emulator (29 tests, all passing);
* an end-to-end run on an Android 15 emulator against the local Firebase emulators:
  registration and login (with error cases), profile, groups and members, equal and percentage
  splits, balances, settlements and their limits, history search, the monthly summary, group
  deletion, live updates and member management;
* a full pass through the TA's *Project 2.3 Demo Checklist* on an Android 15 emulator with the
  demo data from section 4, item by item:
  * **Feature 1:** each registration rejection (non-USC email, 9-digit ID, 10 characters with
    a letter, taken email, taken USC ID); rename, logout and a wrong password; a blank group
    name; adding members, including both error messages; a non-owner seeing members but no
    add/edit/delete; renaming; and deleting a group, after which its expense and settlement
    were gone from the database.
  * **Feature 2:** $0, negative and 3-decimal amounts refused; an expense recorded for others
    by a non-participant; editing payer, participants and amount; Cancel and Delete on the
    confirmation.
  * **Features 3 and 4:** every split example and rejection, including $0.00 and 0% shares;
    every balance example in section 4; balances updating after creating, editing and deleting
    expenses and settlements.
  * **Feature 5:** settlements from both sides; type, category and member filters; both sort
    orders; Match case; a non-participant viewing an expense; editing last month's expense,
    which kept its date and moved only that month's total; an empty month showing
    0 expenses and $0.00.
  * **Extras:** Share balances and dark mode.
  * No SplitSmart crash was logged.

**Whenever a pull request changes `firestore.rules`, whoever owns the Firebase project must
re-publish it** (console → Firestore → Rules, or `firebase deploy --only firestore:rules`).
Otherwise the new behavior fails with "You don't have permission to do that." For example,
recording a payment you received needs the current rules.
