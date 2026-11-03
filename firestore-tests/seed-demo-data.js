// Prepares the data for the Project 2.3 demo checklist: four accounts (Alice, Bob, Carol and
// David), five groups with different owners, and expenses and settlements across two months that
// reproduce the checklist's example numbers. README section 4 lists what each group shows.
//
//   npm run emulators       # terminal 1: local Auth + Firestore emulators
//   npm run seed:emulator   # terminal 2: fill the emulators
//   npm run seed            # or: fill the real splitsmart-team04 project
//
// It signs in as each demo user and writes through the normal security rules, using the same
// fields as FirestoreMapper, so the app reads it like data it created itself. Running it again
// first deletes every group the demo accounts own, so the demo always starts from this state.
import { readFileSync } from 'node:fs';
import { initializeApp } from 'firebase/app';
import {
  connectAuthEmulator, createUserWithEmailAndPassword, getAuth, signInWithEmailAndPassword,
} from 'firebase/auth';
import {
  addDoc, collection, connectFirestoreEmulator, doc, getDoc, getDocs, getFirestore, query,
  runTransaction, updateDoc, where, writeBatch,
} from 'firebase/firestore';

const mode = process.argv[2];
if (mode !== '--emulator' && mode !== '--live') {
  console.error('Usage: node seed-demo-data.js --emulator | --live');
  process.exit(1);
}

const config = JSON.parse(readFileSync(new URL('../app/google-services.json', import.meta.url)));
const projectId = config.project_info.project_id;
const apiKey = process.env.FIREBASE_API_KEY ?? config.client[0].api_key[0].current_key;
const app = initializeApp({ apiKey, projectId, authDomain: `${projectId}.firebaseapp.com` });
const auth = getAuth(app);
const db = getFirestore(app);
if (mode === '--emulator') {
  connectAuthEmulator(auth, 'http://127.0.0.1:9099', { disableWarnings: true });
  connectFirestoreEmulator(db, '127.0.0.1', 8080);
}

// Test accounts for the demo only. Every account uses the same password.
const PASSWORD = 'splitsmart-demo';
const USERS = {
  alice: { name: 'Alice', email: 'alice.demo@usc.edu', uscId: '9990000001' },
  bob: { name: 'Bob', email: 'bob.demo@usc.edu', uscId: '9990000002' },
  carol: { name: 'Carol', email: 'carol.demo@usc.edu', uscId: '9990000003' },
  david: { name: 'David', email: 'david.demo@usc.edu', uscId: '9990000004' },
};
const uid = {};

// Dates use this computer's local time zone, like the app's automatic YYYY-MM-DD date.
const pad = (n) => String(n).padStart(2, '0');
const ymd = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const now = new Date();
const lastMonth = new Date(now.getFullYear(), now.getMonth() - 1, 15, 12, 0, 0);
let tick = 0;
// One second apart, in the order written, so "newest first" is predictable.
const today = () => ({ date: ymd(now), createdAt: now.getTime() - 600_000 + 1000 * tick++ });
const inLastMonth = () => ({ date: ymd(lastMonth), createdAt: lastMonth.getTime() + 1000 * tick++ });

async function signInAs(key) {
  const user = USERS[key];
  try {
    return (await signInWithEmailAndPassword(auth, user.email, PASSWORD)).user.uid;
  } catch (e) {
    if (!['auth/user-not-found', 'auth/invalid-credential', 'auth/invalid-login-credentials']
      .includes(e.code)) {
      throw e;
    }
  }
  try {
    return (await createUserWithEmailAndPassword(auth, user.email, PASSWORD)).user.uid;
  } catch (e) {
    if (e.code === 'auth/email-already-in-use') {
      throw new Error(`${user.email} already exists with a different password. Delete it in the `
        + 'Firebase console (Authentication -> Users) and run this again.');
    }
    throw e;
  }
}

/** Same as AuthRepository: reserve the USC ID and write the profile in one transaction. */
async function ensureProfile(key) {
  const user = USERS[key];
  const profileRef = doc(db, 'users', uid[key]);
  if ((await getDoc(profileRef)).exists()) {
    return;
  }
  const idRef = doc(db, 'uscIds', user.uscId);
  await runTransaction(db, async (tx) => {
    if ((await tx.get(idRef)).exists()) {
      throw new Error(`USC ID ${user.uscId} already belongs to another account.`);
    }
    tx.set(idRef, { uid: uid[key] });
    tx.set(profileRef, { name: user.name, uscEmail: user.email, uscId: user.uscId, createdAt: Date.now() });
  });
}

/** Deletes every group this demo user owns, with its expenses and settlements. */
async function deleteOwnedGroups(key) {
  const groups = await getDocs(query(collection(db, 'groups'),
    where('memberIds', 'array-contains', uid[key])));
  let deleted = 0;
  for (const group of groups.docs) {
    if (group.data().ownerId !== uid[key]) {
      continue;
    }
    const transactions = await getDocs(collection(db, 'groups', group.id, 'transactions'));
    const batch = writeBatch(db);
    transactions.docs.forEach((t) => batch.delete(t.ref));
    batch.delete(group.ref);
    await batch.commit();
    deleted++;
  }
  return deleted;
}

async function createGroup(ownerKey, name, description, memberKeys) {
  await signInAs(ownerKey);
  const ref = await addDoc(collection(db, 'groups'), {
    name, description, ownerId: uid[ownerKey], memberIds: [uid[ownerKey]], createdAt: today().createdAt,
  });
  // Like the app, the owner adds members one step after creating the group (FR 1.5).
  await updateDoc(ref, { memberIds: [ownerKey, ...memberKeys].map((k) => uid[k]) });
  return ref.id;
}

/** shares: [[member, cents, percentBasisPoints?], ...]; they must add up to the total (FR 3.6). */
function expense(title, totalCents, category, payer, splitMethod, shares, when, extra = {}) {
  const sum = shares.reduce((s, [, cents]) => s + cents, 0);
  if (sum !== totalCents || !shares.some(([k]) => k === payer)) {
    throw new Error(`Bad demo expense "${title}"`);
  }
  return {
    creator: extra.creator ?? payer,
    data: {
      type: 'EXPENSE', creatorId: uid[extra.creator ?? payer], ...when, updatedAt: when.createdAt,
      note: extra.note ?? '', title, totalCents, category, payerId: uid[payer], splitMethod,
      shares: shares.map(([k, cents, bp = 0]) => ({ participantId: uid[k], amountCents: cents, percentBasisPoints: bp })),
      participantIds: shares.map(([k]) => uid[k]),
    },
  };
}

function settlement(payer, receiver, amountCents, when, extra = {}) {
  return {
    creator: extra.creator ?? payer,
    data: {
      type: 'SETTLEMENT', creatorId: uid[extra.creator ?? payer], ...when, updatedAt: when.createdAt,
      note: extra.note ?? '', payerId: uid[payer], receiverId: uid[receiver], amountCents,
    },
  };
}

async function addTransactions(groupId, transactions) {
  for (const t of transactions) {
    await signInAs(t.creator);
    await addDoc(collection(db, 'groups', groupId, 'transactions'), t.data);
  }
}

async function main() {
  console.log(mode === '--live'
    ? `Seeding the REAL Firebase project ${projectId}.`
    : `Seeding the local emulators for ${projectId}.`);

  for (const key of Object.keys(USERS)) {
    uid[key] = await signInAs(key);
    await ensureProfile(key);
    const deleted = await deleteOwnedGroups(key);
    console.log(`  ${USERS[key].name.padEnd(5)} ${USERS[key].email}${deleted ? ` (removed ${deleted} old group(s))` : ''}`);
  }

  // Feature 4: separate totals. Bob owes Alice $10, Carol owes Alice $5, Alice owes David $4,
  // so Alice's page shows $15.00 to receive and $4.00 to pay.
  const apartment = await createGroup('alice', 'Apartment 4B', 'Shared apartment costs',
    ['bob', 'carol', 'david']);
  await addTransactions(apartment, [
    expense('Groceries', 2000, 'FOOD', 'alice', 'EQUAL', [['alice', 1000], ['bob', 1000]], today()),
    expense('Cleaning supplies', 1000, 'HOUSING', 'alice', 'EQUAL', [['alice', 500], ['carol', 500]], today()),
    expense('Gas for the car', 800, 'TRANSPORTATION', 'david', 'EQUAL', [['david', 400], ['alice', 400]], today()),
  ]);

  // Feature 4: debts add up and offset. Alice owes Bob $10 + $5 and Bob owes Alice $4, so Alice
  // owes Bob $11.00. Owned by Bob; Carol is a member who is settled up with everyone.
  const roadTrip = await createGroup('bob', 'Road Trip', 'Weekend in San Diego', ['alice', 'carol']);
  await addTransactions(roadTrip, [
    expense('Gas', 2000, 'TRANSPORTATION', 'bob', 'EQUAL', [['bob', 1000], ['alice', 1000]], today()),
    expense('Snacks', 1000, 'FOOD', 'bob', 'EQUAL', [['bob', 500], ['alice', 500]], today()),
    expense('Parking', 800, 'TRANSPORTATION', 'alice', 'EQUAL', [['alice', 400], ['bob', 400]], today()),
  ]);

  // Feature 4: no simplifying across pairs. Alice owes Bob $10 and Bob owes Carol $10; there is
  // no Alice-Carol debt. Alice recorded "Printing" without being part of it (Feature 2).
  const study = await createGroup('carol', 'Study Group', 'CSCI 310 midterm prep', ['alice', 'bob']);
  await addTransactions(study, [
    expense('Textbook', 2000, 'OTHER', 'bob', 'EQUAL', [['bob', 1000], ['alice', 1000]], today()),
    expense('Printing', 2000, 'OTHER', 'carol', 'EQUAL', [['carol', 1000], ['bob', 1000]], today(),
      { creator: 'alice', note: 'Alice recorded this for Carol' }),
  ]);

  // Feature 5: monthly summary, search, filters, and settlements from both sides.
  // This month: 2 expenses, $50.00 (Food $30.00 + Transportation $20.00); settlements excluded.
  // Last month: 2 expenses, $60.00 (Entertainment $30.00 + Food $30.00).
  const dinner = await createGroup('david', 'Dinner Club', 'Fridays after class',
    ['alice', 'bob', 'carol']);
  await addTransactions(dinner, [
    expense('Concert tickets', 3000, 'ENTERTAINMENT', 'david', 'PERCENTAGE',
      [['david', 1500, 5000], ['alice', 600, 2000], ['bob', 900, 3000], ['carol', 0, 0]], inLastMonth(),
      { note: '20% / 30% / 50%, Carol 0%' }),
    expense('Farmers market', 3000, 'FOOD', 'david', 'EXACT',
      [['david', 1500], ['alice', 500], ['bob', 1000], ['carol', 0]], inLastMonth(),
      { note: '$5 + $10 + $15, Carol $0' }),
    expense('Pizza night', 3000, 'FOOD', 'alice', 'EQUAL',
      [['alice', 1000], ['bob', 1000], ['carol', 1000]], today(), { note: "Domino's, extra cheese" }),
    expense('Uber to the concert', 2000, 'TRANSPORTATION', 'bob', 'EQUAL',
      [['bob', 1000], ['carol', 1000]], today()),
    settlement('carol', 'alice', 500, today(), { note: 'Venmo for pizza' }),
    settlement('carol', 'bob', 400, today(), { creator: 'bob', note: 'Cash' }),
  ]);

  // Feature 1: a group to delete, with an expense and a settlement that disappear with it.
  const disposable = await createGroup('alice', 'Delete Me', 'For the group deletion demo', ['bob']);
  await addTransactions(disposable, [
    expense('Test expense', 1200, 'OTHER', 'alice', 'EQUAL', [['alice', 600], ['bob', 600]], today()),
    settlement('bob', 'alice', 600, today(), { note: 'Paid back' }),
  ]);

  console.log('Created Apartment 4B, Road Trip, Study Group, Dinner Club and Delete Me.');
  console.log('Log in with any demo email above; the shared password is in this script and README section 4.');
  process.exit(0);
}

main().catch((e) => {
  console.error(`Seeding failed: ${e.message}`);
  process.exit(1);
});
