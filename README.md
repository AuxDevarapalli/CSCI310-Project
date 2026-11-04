# SplitSmart — CSCI 310 Team 04 (Project A)

SplitSmart is a native Android app (Java) for USC students and alumni to record shared
expenses, split them, see who owes whom, and record payments made outside the app. It
implements the Project 2.1 requirements (FR 1.1 – FR 5.7, NFR 1 – NFR 4) using the
Project 2.2 design: MVVM over Firebase Authentication + Cloud Firestore.

**Team 04:** Zachary Chung · Aux Devarapalli · Edward Zhao · Swayam Parekh · Brian Shi

| Document | Where |
|---|---|
| Project 2.3 implementation document (PDF for Brightspace) | [`docs/Team04_Project2.3_Implementation_Document.pdf`](docs/Team04_Project2.3_Implementation_Document.pdf) |
| Same document, editable source | [`docs/implementation-document.md`](docs/implementation-document.md) |
| Revised diagrams (PlantUML sources + images) | [`docs/diagrams/`](docs/diagrams/) |
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

Runs 90 JUnit tests for the business logic (money parsing/formatting, every split method
and the FR 3.5 rounding rule, pairwise balances, validation, history filtering, monthly
summaries, member-management helpers) using the exact examples from the customer description.
They need no Firebase.

Security-rule tests (optional, needs Node.js 20+ and Java 21; starts the Firestore emulator):

```bash
cd firestore-tests && npm install && npm test
```

27 tests check NFR 4 on the server side: members-only access, owner-only group changes,
creator-only edits, locked USC email/ID, unique USC IDs, valid document contents, and the
leave / remove member / transfer ownership rules.

### Running the whole app against local Firebase emulators (optional)

Useful for testing without creating accounts in the shared Firebase project:

```bash
firebase emulators:start --only auth,firestore --project splitsmart-team04   # terminal 1
./gradlew installDebug -Psplitsmart.emulatorHost=10.0.2.2                     # terminal 2, emulator running
```

`10.0.2.2` is how the Android emulator reaches your computer. Builds without the flag always use
the real Firebase project.

## 4. Demo video script (what the TA grades)

Use **two devices** (two emulators, or an emulator and a phone) so one member's changes can
be shown on another member's screen. Accounts can also be switched with *Profile → Log out*.

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
| 9 | FR 5.1–5.2 | As Bob, Settle up → pay Alice. Show "more than owed" and "$0" are refused. Record a partial payment, then sign in as Alice and record another payment from Bob. Both entries should show Bob paid Alice. |
| 10 | FR 5.4–5.6 | History: newest first, settlements labeled "Settlement"; case-sensitive keyword search on titles/notes; filter by type, category, member; flip the sort order; open a transaction for full details. |
| 11 | FR 2.7–2.9, 5.3 | Edit an expense (balances/history/summary update) and delete one with the confirmation dialog. As another member, Edit/Delete are hidden. Same for a settlement. |
| 12 | FR 5.7 | Summary tab: pick a month; number of expenses, total, and per-category totals that add up; settlements excluded. |
| 13 | NFR 1, 3 | Close and reopen the app: data is still there. Changes made on device A appear on device B after pull-to-refresh. |
| 14 | Live updates | With both devices on the same group, add an expense on device A: device B's History and Balances update **without** refreshing. Add someone to a group: it appears on their home screen immediately. |
| 15 | Member management | As the owner, Members tab → ⋮ next to Carol → *Remove from group* while she still owes money → refused with the amount. As Bob (settled up), menu → *Leave group*. As the owner, ⋮ → *Make owner* on another member, then show the old owner now sees *Leave group* instead of Edit/Delete. Bob's old expenses still show his name. |
| 16 | Extras | Home screen totals per group, "you lent / you borrowed" in history, Settle-up shortcut on balances, Share balances, month picker, dark mode. |

**Settled-expense policy:** Creators can still edit or delete expenses after a settlement.
Payments stay in history, and the recalculated pair balance can reverse direction if a
payment now exceeds the underlying debt.

**Optional reporting features:** In History, choose a month alongside the existing filters.
The download button saves the matching rows, in the displayed order, as a CSV through Android’s
file picker. The CSV includes payment roles and expense shares. In Summary, tap a category to open
its expenses, compare what you paid with your assigned shares, or tap a bar in the six-month chart
to change the selected month. Expense totals and personal figures exclude settlements.
Group owners can add, replace, or remove a shared thumbnail from **Edit group**. The photo picker
previews the change, and **Save changes** applies it for all members.

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

* Notifications when you are added to a group or an expense involves you (RC-12 in the
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
* 90 JUnit tests for the business logic, all passing;
* `firestore.rules` tested against the real Firestore emulator (27 tests, all passing);
* an end-to-end run on an Android 15 emulator against the local Firebase emulators:
  registration and login (with error cases), profile, groups and members, equal and percentage
  splits, balances, settlements and their limits, history search, the monthly summary, group
  deletion, live updates and member management. Still to check by hand before recording the
  demo: editing and deleting a transaction, exact splits, history filters and sort, Share
  balances, and dark mode.

**After pulling these changes, whoever owns the Firebase project must re-publish
`firestore.rules`** (console → Firestore → Rules, or `firebase deploy --only firestore:rules`).
Otherwise leaving a group, removing a member and transferring ownership fail with "You don't
have permission to do that."
