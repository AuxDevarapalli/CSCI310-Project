// Security-rule tests for firestore.rules (design section 5.3, NFR 4).
// Documents are shaped exactly like the Android app writes them (see FirestoreMapper.java).
// Run from this folder:  npm install && npm test   (starts the Firestore emulator)
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, test } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  arrayUnion,
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  runTransaction,
  setDoc,
  updateDoc,
  where,
  writeBatch,
} from 'firebase/firestore';

const ALICE = 'alice';
const BOB = 'bob';
const CAROL = 'carol';
const GROUP = 'group1';

let env;

const as = (uid) => env.authenticatedContext(uid, { email: `${uid}@usc.edu` }).firestore();
const anonymous = () => env.unauthenticatedContext().firestore();
const seed = (write) => env.withSecurityRulesDisabled((context) => write(context.firestore()));

const user = (uid, extra = {}) => ({
  name: uid[0].toUpperCase() + uid.slice(1),
  uscEmail: `${uid}@usc.edu`,
  uscId: '1234567890',
  createdAt: 1,
  ...extra,
});
const group = (owner, members = [owner]) => ({
  name: 'Roommates',
  description: 'Apartment 4B',
  ownerId: owner,
  memberIds: members,
  createdAt: 1,
});
const share = (uid, cents, percentBasisPoints = 0) => ({
  participantId: uid,
  amountCents: cents,
  percentBasisPoints,
});
const expense = (creator, payer, shares, extra = {}) => ({
  type: 'EXPENSE',
  creatorId: creator,
  date: '2026-10-29',
  createdAt: 1,
  updatedAt: 1,
  note: '',
  title: 'Groceries',
  totalCents: shares.reduce((sum, s) => sum + s.amountCents, 0),
  category: 'FOOD',
  payerId: payer,
  splitMethod: 'EQUAL',
  shares,
  participantIds: shares.map((s) => s.participantId),
  ...extra,
});
const settlement = (payer, receiver, cents, extra = {}) => ({
  type: 'SETTLEMENT',
  creatorId: payer,
  date: '2026-10-29',
  createdAt: 2,
  updatedAt: 2,
  note: 'Venmo',
  payerId: payer,
  receiverId: receiver,
  amountCents: cents,
  ...extra,
});

const txDoc = (db, id) => doc(db, 'groups', GROUP, 'transactions', id);

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-splitsmart',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});

after(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
});

describe('users/{uid} (FR 1.1 - 1.3)', () => {
  test('a user can create their own valid profile', async () => {
    await assertSucceeds(setDoc(doc(as(ALICE), 'users', ALICE), user(ALICE)));
  });

  test('profiles need a USC email that matches the login, a 10-digit USC ID and no extra fields', async () => {
    const db = as(ALICE);
    await assertFails(setDoc(doc(db, 'users', ALICE), user(ALICE, { uscEmail: 'alice@gmail.com' })));
    await assertFails(setDoc(doc(db, 'users', ALICE), user(ALICE, { uscEmail: 'bob@usc.edu' })));
    await assertFails(setDoc(doc(db, 'users', ALICE), user(ALICE, { uscId: '123456789' })));
    await assertFails(setDoc(doc(db, 'users', ALICE), user(ALICE, { isAdmin: true })));
  });

  test("nobody can create someone else's profile", async () => {
    await assertFails(setDoc(doc(as(BOB), 'users', ALICE), user(ALICE)));
  });

  test('only the name can be updated; USC email and USC ID are locked', async () => {
    await seed((db) => setDoc(doc(db, 'users', ALICE), user(ALICE)));
    const db = as(ALICE);
    await assertSucceeds(updateDoc(doc(db, 'users', ALICE), { name: 'Alice Trojan' }));
    await assertFails(updateDoc(doc(db, 'users', ALICE), { uscEmail: 'other@usc.edu' }));
    await assertFails(updateDoc(doc(db, 'users', ALICE), { uscId: '0987654321' }));
    await assertFails(updateDoc(doc(as(BOB), 'users', ALICE), { name: 'Hacked' }));
  });

  test('signed-in users can read profiles (to show names and add members); guests cannot', async () => {
    await seed((db) => setDoc(doc(db, 'users', ALICE), user(ALICE)));
    await assertSucceeds(getDoc(doc(as(BOB), 'users', ALICE)));
    await assertSucceeds(getDocs(query(collection(as(BOB), 'users'),
      where('uscEmail', '==', 'alice@usc.edu'))));
    await assertFails(getDoc(doc(anonymous(), 'users', ALICE)));
  });
});

describe('uscIds/{uscId} (FR 1.2)', () => {
  test('registration reserves the USC ID and writes the profile in one transaction', async () => {
    const db = as(ALICE);
    await assertSucceeds(runTransaction(db, async (transaction) => {
      const reserved = await transaction.get(doc(db, 'uscIds', '1234567890'));
      if (reserved.exists()) throw new Error('taken');
      transaction.set(doc(db, 'uscIds', '1234567890'), { uid: ALICE });
      transaction.set(doc(db, 'users', ALICE), user(ALICE));
    }));
  });

  test('a USC ID that is already reserved cannot be claimed again', async () => {
    await seed((db) => setDoc(doc(db, 'uscIds', '1234567890'), { uid: ALICE }));
    await assertFails(setDoc(doc(as(BOB), 'uscIds', '1234567890'), { uid: BOB }));
  });

  test('a reservation must point at the caller and the id must be 10 digits', async () => {
    await assertFails(setDoc(doc(as(BOB), 'uscIds', '1111111111'), { uid: ALICE }));
    await assertFails(setDoc(doc(as(BOB), 'uscIds', '12345'), { uid: BOB }));
  });

  test('reservations cannot be listed', async () => {
    await assertFails(getDocs(collection(as(ALICE), 'uscIds')));
  });
});

describe('groups/{groupId} (FR 1.4 - 1.7)', () => {
  test('the creator becomes the owner and only member', async () => {
    await assertSucceeds(setDoc(doc(as(ALICE), 'groups', GROUP), group(ALICE)));
    await assertFails(setDoc(doc(as(ALICE), 'groups', 'g2'), group(BOB, [BOB])));
    await assertFails(setDoc(doc(as(ALICE), 'groups', 'g3'), group(ALICE, [ALICE, BOB])));
  });

  test('only members can read a group (NFR 4)', async () => {
    await seed((db) => setDoc(doc(db, 'groups', GROUP), group(ALICE, [ALICE, BOB])));
    await assertSucceeds(getDoc(doc(as(BOB), 'groups', GROUP)));
    await assertFails(getDoc(doc(as(CAROL), 'groups', GROUP)));
  });

  test("a member's group list query is allowed; listing every group is not", async () => {
    await seed((db) => setDoc(doc(db, 'groups', GROUP), group(ALICE, [ALICE, BOB])));
    const db = as(BOB);
    await assertSucceeds(getDocs(query(collection(db, 'groups'),
      where('memberIds', 'array-contains', BOB))));
    await assertFails(getDocs(collection(db, 'groups')));
  });

  test('reading a deleted group is allowed so the app can say it was deleted', async () => {
    await assertSucceeds(getDoc(doc(as(BOB), 'groups', 'gone')));
  });

  test('only the owner can edit the group or add members', async () => {
    await seed((db) => setDoc(doc(db, 'groups', GROUP), group(ALICE, [ALICE, BOB])));
    await assertSucceeds(updateDoc(doc(as(ALICE), 'groups', GROUP),
      { name: 'New name', description: 'New description' }));
    await assertSucceeds(updateDoc(doc(as(ALICE), 'groups', GROUP),
      { memberIds: arrayUnion(CAROL) }));
    await assertFails(updateDoc(doc(as(BOB), 'groups', GROUP), { name: 'Not mine' }));
    await assertFails(updateDoc(doc(as(BOB), 'groups', GROUP), { memberIds: arrayUnion('dave') }));
  });

  test('ownership cannot be changed and the owner cannot be removed', async () => {
    await seed((db) => setDoc(doc(db, 'groups', GROUP), group(ALICE, [ALICE, BOB])));
    await assertFails(updateDoc(doc(as(ALICE), 'groups', GROUP), { ownerId: BOB }));
    await assertFails(updateDoc(doc(as(ALICE), 'groups', GROUP), { memberIds: [BOB] }));
  });

  test('only the owner can delete the group, together with all its transactions', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'groups', GROUP), group(ALICE, [ALICE, BOB]));
      await setDoc(txDoc(db, 'e1'), expense(BOB, BOB, [share(ALICE, 500), share(BOB, 500)]));
      await setDoc(txDoc(db, 's1'), settlement(ALICE, BOB, 500));
    });
    await assertFails(deleteDoc(doc(as(BOB), 'groups', GROUP)));

    const db = as(ALICE);
    const batch = writeBatch(db);
    batch.delete(txDoc(db, 'e1'));
    batch.delete(txDoc(db, 's1'));
    batch.delete(doc(db, 'groups', GROUP));
    await assertSucceeds(batch.commit());
  });
});

describe('groups/{groupId}/transactions (FR 2.x, FR 5.x)', () => {
  beforeEach(async () => {
    await seed((db) => setDoc(doc(db, 'groups', GROUP), group(ALICE, [ALICE, BOB])));
  });

  test('any member can add a valid expense (FR 2.1)', async () => {
    const db = as(BOB);
    await assertSucceeds(setDoc(txDoc(db, 'e1'),
      expense(BOB, ALICE, [share(ALICE, 1000), share(BOB, 1000)])));
  });

  test('non-members cannot add or read transactions (NFR 4)', async () => {
    await seed((db) => setDoc(txDoc(db, 'e1'), expense(ALICE, ALICE, [share(ALICE, 1), share(BOB, 1)])));
    const db = as(CAROL);
    await assertFails(setDoc(txDoc(db, 'e2'), expense(CAROL, CAROL, [share(CAROL, 1), share(ALICE, 1)])));
    await assertFails(getDoc(txDoc(db, 'e1')));
    await assertFails(getDocs(collection(db, 'groups', GROUP, 'transactions')));
    await assertSucceeds(getDocs(collection(as(BOB), 'groups', GROUP, 'transactions')));
  });

  test('invalid expenses are rejected (FR 2.2, 2.3, 2.6)', async () => {
    const db = as(ALICE);
    // Creator must be the caller.
    await assertFails(setDoc(txDoc(db, 'a'), expense(BOB, ALICE, [share(ALICE, 1), share(BOB, 1)])));
    // Participants must be members.
    await assertFails(setDoc(txDoc(db, 'b'), expense(ALICE, ALICE, [share(ALICE, 1), share(CAROL, 1)])));
    // At least two participants.
    await assertFails(setDoc(txDoc(db, 'c'), expense(ALICE, ALICE, [share(ALICE, 100)])));
    // The payer must participate.
    await assertFails(setDoc(txDoc(db, 'd'), expense(ALICE, ALICE, [share(BOB, 50), share(CAROL, 50)])));
    // Total must be greater than zero.
    await assertFails(setDoc(txDoc(db, 'e'), expense(ALICE, ALICE, [share(ALICE, 0), share(BOB, 0)])));
  });

  test('the member who owes records a settlement to another member (FR 5.1, 5.2)', async () => {
    await assertSucceeds(setDoc(txDoc(as(ALICE), 's1'), settlement(ALICE, BOB, 500)));
    // Cannot record a payment on behalf of someone else, to a non-member, to yourself, or of $0.
    await assertFails(setDoc(txDoc(as(ALICE), 's2'), settlement(BOB, ALICE, 500, { creatorId: ALICE })));
    await assertFails(setDoc(txDoc(as(ALICE), 's3'), settlement(ALICE, CAROL, 500)));
    await assertFails(setDoc(txDoc(as(ALICE), 's4'), settlement(ALICE, ALICE, 500)));
    await assertFails(setDoc(txDoc(as(ALICE), 's5'), settlement(ALICE, BOB, 0)));
  });

  test('only the creator can edit a transaction (FR 2.7, FR 5.3)', async () => {
    await seed((db) => setDoc(txDoc(db, 'e1'), expense(ALICE, ALICE, [share(ALICE, 500), share(BOB, 500)])));
    const edited = expense(ALICE, ALICE, [share(ALICE, 300), share(BOB, 700)], { title: 'Edited' });
    await assertSucceeds(updateDoc(txDoc(as(ALICE), 'e1'), edited));
    await assertFails(updateDoc(txDoc(as(BOB), 'e1'), { ...edited, title: 'Bob edit' }));
    await assertFails(updateDoc(txDoc(as(ALICE), 'e1'), { creatorId: BOB }));
    await assertFails(updateDoc(txDoc(as(ALICE), 'e1'), settlement(ALICE, BOB, 100)));
  });

  test('only the creator can delete a transaction; other members cannot (FR 2.7, FR 5.3)', async () => {
    await seed(async (db) => {
      await setDoc(txDoc(db, 'bobExpense'), expense(BOB, BOB, [share(ALICE, 500), share(BOB, 500)]));
      await setDoc(txDoc(db, 'bobSettlement'), settlement(BOB, ALICE, 100));
      await setDoc(txDoc(db, 'aliceExpense'), expense(ALICE, ALICE, [share(ALICE, 1), share(BOB, 1)]));
    });
    await assertSucceeds(deleteDoc(txDoc(as(BOB), 'bobSettlement')));
    await assertSucceeds(deleteDoc(txDoc(as(BOB), 'bobExpense')));
    // Bob is a member, but neither the creator nor the owner of this one.
    await assertFails(deleteDoc(txDoc(as(BOB), 'aliceExpense')));
  });
});
