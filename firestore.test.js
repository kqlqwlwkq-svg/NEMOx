const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_nemox_1";
const BOB_UID = "bob_nemox_2";

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

test("Unauthenticated users cannot read or write any NEMOx collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("library_games").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("missions").doc("m1").set({
      userId: ALICE_UID,
      gameName: "Minecraft",
      title: "Mine diamonds",
      category: "DAILY",
      currentProgress: 0,
      targetProgress: 5,
      completed: false,
      expiresAtMillis: Date.now() + 86400000,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("UserProfile: owner create & partial update succeed; cross-user & ghost fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "AliceGamer",
      email: "alice@example.com",
      preferredLanguage: "ar",
      darkMode: true,
      notificationsEnabled: true,
      notifyOnUpdates: true,
      notifyOnSeasons: true,
      notifyOnMissions: true,
      notifyOnUpcoming: true,
      notifyOnNews: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Partial update of language & theme
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredLanguage: "en",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject cross-user read or write
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("LibraryGames: create, list, favorite toggle, and immutable guards", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  // Empty collection list succeeds for owner
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("library_games").get()
  );

  // Create game (NO @DocumentId `id` key)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("library_games").doc("steam_578080").set({
      userId: ALICE_UID,
      externalId: "steam_578080",
      title: "PUBG: BATTLEGROUNDS",
      coverUrl: "https://cdn.akamai.steamstatic.com/steam/apps/578080/header.jpg",
      sourceType: "STEAM",
      steamAppId: 578080,
      platforms: ["PC"],
      genres: ["Action", "Shooter"],
      favorite: false,
      needsUpdate: false,
      playHours: 120,
      playerLevel: 45,
      achievementsUnlocked: 12,
      achievementsTotal: 37,
      notifyUpdates: true,
      notifySeasons: true,
      notifyMissions: true,
      notifyNews: true,
      lastUpdatedAtMillis: Date.now() - 5000,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Partial update favorite & needsUpdate
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("library_games").doc("steam_578080").update({
      favorite: true,
      needsUpdate: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject @DocumentId "id" injection on create
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("library_games").doc("steam_730").set({
      id: "steam_730",
      userId: ALICE_UID,
      externalId: "steam_730",
      title: "CS2",
      sourceType: "STEAM",
      favorite: false,
      needsUpdate: false,
      playHours: 0,
      playerLevel: 1,
      achievementsUnlocked: 0,
      achievementsTotal: 1,
      notifyUpdates: true,
      notifySeasons: true,
      notifyMissions: true,
      notifyNews: true,
      lastUpdatedAtMillis: Date.now(),
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject cross-user list
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("library_games").get()
  );
});

test("GameAccounts, Missions, Waitlist, Notifications: CRUD & validation bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 2000);

  // Linked Account (No password field permitted!)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("game_accounts").doc("acc_pubgm").set({
      userId: ALICE_UID,
      gameKey: "pubg_mobile",
      gameName: "PUBG Mobile",
      playerIdentifier: "5123456789",
      platform: "Mobile",
      serverRegion: "Middle East",
      level: 68,
      rankTier: "Ace Master",
      apiMode: "MANUAL_NO_PUBLIC_API",
      apiAvailabilityNote: "No official public API for PUBG Mobile stats; Player ID tracked locally.",
      lastSyncedAtMillis: Date.now(),
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject password injection on GameAccount
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("game_accounts").doc("acc_bad").set({
      userId: ALICE_UID,
      gameKey: "pubg_mobile",
      gameName: "PUBG Mobile",
      playerIdentifier: "5123456789",
      password: "secret",
      platform: "Mobile",
      level: 68,
      apiMode: "MANUAL_NO_PUBLIC_API",
      lastSyncedAtMillis: Date.now(),
      createdAt: now,
      updatedAt: now,
    })
  );

  // Mission create & progress update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("missions").doc("mis_1").set({
      userId: ALICE_UID,
      gameDocId: "steam_578080",
      gameName: "PUBG: BATTLEGROUNDS",
      title: "Survive 30 minutes in Erangel",
      category: "DAILY",
      currentProgress: 15,
      targetProgress: 30,
      completed: false,
      expiresAtMillis: Date.now() + 3600000,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("missions").doc("mis_1").update({
      currentProgress: 30,
      completed: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Waitlist create & status update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("waitlist").doc("wait_1").set({
      userId: ALICE_UID,
      externalId: "steam_123456",
      title: "Upcoming RPG",
      releaseDateText: "Q4 2026",
      releaseTimestampMillis: Date.now() + 864000000,
      platforms: ["PC", "PS5"],
      status: "UPCOMING",
      notifyBeforeRelease: true,
      notifyOnRelease: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Notification create & mark read
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("notif_1").set({
      userId: ALICE_UID,
      type: "SEASON_ENDING",
      title: "Season Ending Soon",
      body: "Current ranked season ends in 24 hours.",
      gameName: "Rocket League",
      read: false,
      createdAtMillis: Date.now(),
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("notif_1").update({
      read: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject tampered future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("notif_future").set({
      userId: ALICE_UID,
      type: "SEASON_ENDING",
      title: "Future",
      body: "Tampered",
      read: false,
      createdAtMillis: Date.now(),
      createdAt: new Date(Date.now() + 120000),
      updatedAt: new Date(Date.now() + 120000),
    })
  );
});
