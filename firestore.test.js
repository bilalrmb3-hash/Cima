const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user: cannot read or write library or progress", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("library").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("progress").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("library")
      .doc("tt0133093")
      .set({
        userId: ALICE_UID,
        mediaType: "movie",
        title: "The Matrix",
        status: "WATCHLIST",
        totalEpisodes: 0,
        watchedCount: 0,
        createdAt: new Date(Date.now() - 1000),
        updatedAt: new Date(Date.now() - 1000),
      })
  );
});

test("Authenticated owner: valid library create, partial update, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const libraryRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("library")
    .doc("tt0944947");

  await assertSucceeds(
    libraryRef.set({
      userId: ALICE_UID,
      mediaType: "series",
      title: "Game of Thrones",
      poster: "https://images.metahub.space/poster/medium/tt0944947/img",
      releaseInfo: "2011-2019",
      imdbRating: "9.2",
      status: "WATCHING",
      totalEpisodes: 73,
      watchedCount: 1,
      lastWatchedEpisodeId: "tt0944947:1:1",
      lastWatchedLabel: "S1 E1",
      createdAt: created,
      updatedAt: created,
    })
  );

  // List subcollection (empty or populated) succeeds for owner
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("library").get()
  );

  // Partial update of status & progress succeeds
  await assertSucceeds(
    libraryRef.update({
      status: "COMPLETED",
      watchedCount: 73,
      lastWatchedEpisodeId: "tt0944947:8:6",
      lastWatchedLabel: "S8 E6",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Delete succeeds for owner
  await assertSucceeds(libraryRef.delete());
});

test("Authenticated owner: valid series progress create and update succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const progressRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("progress")
    .doc("tt0944947");

  await assertSucceeds(
    progressRef.set({
      userId: ALICE_UID,
      mediaType: "series",
      watchedEpisodeIds: ["tt0944947:1:1"],
      lastEpisodeId: "tt0944947:1:1",
      lastSeason: 1,
      lastEpisode: 1,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    progressRef.update({
      watchedEpisodeIds: ["tt0944947:1:1", "tt0944947:1:2"],
      lastEpisodeId: "tt0944947:1:2",
      lastSeason: 1,
      lastEpisode: 2,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Cross-user isolation: Bob cannot read, list, write, or delete Alice's data", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const created = new Date(Date.now() - 10000);
    await context
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("library")
      .doc("tt0133093")
      .set({
        userId: ALICE_UID,
        mediaType: "movie",
        title: "The Matrix",
        status: "WATCHLIST",
        totalEpisodes: 0,
        watchedCount: 0,
        createdAt: created,
        updatedAt: created,
      });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("library").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("library")
      .doc("tt0133093")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("library")
      .doc("tt0133093")
      .update({ status: "COMPLETED", updatedAt: new Date(Date.now() - 1000) })
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("library")
      .doc("tt0133093")
      .delete()
  );
});

test("Schema & Red-Team hardening: reject ghost fields, @DocumentId 'id', future timestamps, and immutable mutations", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Reject ghost field on create
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("library")
      .doc("tt0133093")
      .set({
        userId: ALICE_UID,
        mediaType: "movie",
        title: "The Matrix",
        status: "WATCHLIST",
        totalEpisodes: 0,
        watchedCount: 0,
        isAdmin: true,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject @DocumentId 'id' inside payload
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("library")
      .doc("tt0133093")
      .set({
        id: "tt0133093",
        userId: ALICE_UID,
        mediaType: "movie",
        title: "The Matrix",
        status: "WATCHLIST",
        totalEpisodes: 0,
        watchedCount: 0,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("library")
      .doc("tt0133093")
      .set({
        userId: ALICE_UID,
        mediaType: "movie",
        title: "The Matrix",
        status: "WATCHLIST",
        totalEpisodes: 0,
        watchedCount: 0,
        createdAt: new Date(Date.now() + 60000),
        updatedAt: new Date(Date.now() + 60000),
      })
  );

  // Create valid doc then test immutable field violation
  const validRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("library")
    .doc("tt0133093");
  await assertSucceeds(
    validRef.set({
      userId: ALICE_UID,
      mediaType: "movie",
      title: "The Matrix",
      status: "WATCHLIST",
      totalEpisodes: 0,
      watchedCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertFails(
    validRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(
    validRef.update({
      mediaType: "series",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
