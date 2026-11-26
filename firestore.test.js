const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_player_123";
const BOB_UID = "bob_player_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProfilePayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    displayName: "Sweet Sweeper",
    bestScore: 0,
    highestStage: 1,
    totalCandiesCleared: 0,
    bestCombo: 0,
    gamesPlayed: 0,
    hapticsEnabled: true,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validScorePayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    mode: "CLEANUP",
    score: 4850,
    stage: 1,
    stars: 3,
    candiesCleared: 64,
    maxCombo: 4,
    movesUsed: 14,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: denied all reads and writes on users and scores", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("scores").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("scores")
      .doc("run_1")
      .set(validScorePayload(ALICE_UID))
  );
});

test("Authenticated owner: profile create, stats update, and settings update succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  // Create initial profile (matches Kotlin repository create payload, no @DocumentId `id`)
  await assertSucceeds(profileRef.set(validProfilePayload(ALICE_UID)));

  // Read own profile succeeds; listing users collection fails
  await assertSucceeds(profileRef.get());
  await assertFails(aliceDb.collection("users").get());

  // Action 1: Update game progression stats
  await assertSucceeds(
    profileRef.update({
      bestScore: 5400,
      highestStage: 2,
      totalCandiesCleared: 72,
      bestCombo: 5,
      gamesPlayed: 1,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Action 2: Update display name and haptics preference
  await assertSucceeds(
    profileRef.update({
      displayName: "Candy Queen",
      hapticsEnabled: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Profile security guards: rejects shadow fields, immutable changes, future timestamps, and value poisoning", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);
  await assertSucceeds(profileRef.set(validProfilePayload(ALICE_UID)));

  // Shadow Update Test: ghost field rejected on create and update
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .update({
        displayName: "Hacker",
        isAdmin: true,
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Reject `@DocumentId` `id` field inside body
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb
      .collection("users")
      .doc(BOB_UID)
      .set(validProfilePayload(BOB_UID, { id: BOB_UID }))
  );

  // Immutable createdAt cannot be modified
  await assertFails(
    profileRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Future timestamp rejected
  await assertFails(
    profileRef.update({
      displayName: "Time Traveler",
      hapticsEnabled: true,
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Value Poisoning: oversized displayName and negative score rejected
  await assertFails(
    profileRef.update({
      displayName: "A".repeat(41),
      hapticsEnabled: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    profileRef.update({
      bestScore: -10,
      highestStage: 1,
      totalCandiesCleared: 0,
      bestCombo: 0,
      gamesPlayed: 1,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Mixed action update (stats + displayName together) blocked by action-based allowlist
  await assertFails(
    profileRef.update({
      displayName: "Sneaky",
      bestScore: 9999,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("ScoreRecord subcollection: Kotlin create, list query, delete succeed; updates and invalid enums fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const scoresCol = aliceDb.collection("users").doc(ALICE_UID).collection("scores");

  // Empty subcollection list succeeds (matches Kotlin observeScores query)
  await assertSucceeds(scoresCol.orderBy("score", "desc").limit(50).get());

  // Create CLEANUP and SCORE_RUSH runs
  await assertSucceeds(scoresCol.doc("run_1").set(validScorePayload(ALICE_UID)));
  await assertSucceeds(
    scoresCol.doc("run_2").set(
      validScorePayload(ALICE_UID, {
        mode: "SCORE_RUSH",
        score: 9200,
        stage: 1,
        stars: 3,
      })
    )
  );

  // List query succeeds
  await assertSucceeds(scoresCol.orderBy("score", "desc").limit(50).get());

  // Immutable score updates are strictly rejected
  await assertFails(scoresCol.doc("run_1").update({ score: 999999 }));

  // Invalid mode enum rejected
  await assertFails(
    scoresCol.doc("run_bad_mode").set(validScorePayload(ALICE_UID, { mode: "INFINITE" }))
  );

  // Stars out of bounds rejected
  await assertFails(
    scoresCol.doc("run_bad_stars").set(validScorePayload(ALICE_UID, { stars: 5 }))
  );

  // Spoofed userId inside body rejected
  await assertFails(
    scoresCol.doc("run_spoof").set(validScorePayload(BOB_UID))
  );

  // Owner can delete own history run
  await assertSucceeds(scoresCol.doc("run_1").delete());
});

test("Cross-user isolation: Alice cannot read, list, create, update, or delete Bob's data", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    await adminDb.collection("users").doc(BOB_UID).set(validProfilePayload(BOB_UID));
    await adminDb
      .collection("users")
      .doc(BOB_UID)
      .collection("scores")
      .doc("bob_run_1")
      .set(validScorePayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .update({ displayName: "Hijacked", hapticsEnabled: false, updatedAt: new Date(Date.now() - 500) })
  );
  await assertFails(aliceDb.collection("users").doc(BOB_UID).delete());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("scores").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("scores").doc("bob_run_1").get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("scores").doc("bob_run_1").delete()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("scores")
      .doc("injected")
      .set(validScorePayload(ALICE_UID))
  );
});
