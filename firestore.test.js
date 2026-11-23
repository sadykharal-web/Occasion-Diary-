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

test("Unauthenticated user: cannot read or write families or activities", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("families").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("activities").get());

  const now = new Date(Date.now() - 1000);
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("families")
      .doc("fam_1")
      .set({
        userId: ALICE_UID,
        familyId: "11",
        name: "رجب علی",
        fatherName: "رمضان",
        address: "کوٹ رمضان",
        createdAt: now,
        updatedAt: now,
      })
  );
});

test("Authenticated owner: valid FamilyProfile create, partial update, and list succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Mirror Kotlin FamilyProfile create payload (no @DocumentId `id` field)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("families")
      .doc("fam_11")
      .set({
        userId: ALICE_UID,
        familyId: "11",
        name: "رجب علی",
        fatherName: "رمضان",
        address: "کوٹ رمضان",
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("families").get()
  );

  // Valid partial update
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("families")
      .doc("fam_11")
      .update({
        address: "کوٹ رمضان جدید",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Reject ghost field on create
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("families")
      .doc("fam_ghost")
      .set({
        id: "fam_ghost",
        userId: ALICE_UID,
        familyId: "12",
        name: "اکرم",
        fatherName: "اسلم",
        address: "لاہور",
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject immutable createdAt modification
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("families")
      .doc("fam_11")
      .update({
        createdAt: new Date(Date.now() - 500),
        updatedAt: new Date(Date.now() - 500),
      })
  );
});

test("Authenticated owner: valid ActivityRecord create, partial update, reminder toggle, and calendar sync succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("activities")
      .doc("act_1")
      .set({
        userId: ALICE_UID,
        familyDocId: "fam_11",
        familyId: "11",
        name: "رجب علی",
        fatherName: "رمضان",
        address: "کوٹ رمضان",
        eventType: "شادی",
        eventDetail: "علی رضا بن رجب علی",
        eventDateMillis: 1771027200000,
        direction: "OUT",
        homeItems: "نقد 20000 ہار 3000 ایک سوٹ زنانہ",
        neondraAmount: 5000,
        reminderEnabled: true,
        reminderAtMillis: 1770940800000,
        reminderMinutesBefore: 1440,
        calendarSynced: false,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Partial update: reminder toggle
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("activities")
      .doc("act_1")
      .update({
        reminderEnabled: false,
        reminderAtMillis: 0,
        reminderMinutesBefore: 0,
        updatedAt: new Date(Date.now() - 2000),
      })
  );

  // Partial update: calendarSynced toggle
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("activities")
      .doc("act_1")
      .update({
        calendarSynced: true,
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Reject invalid direction enum
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("activities")
      .doc("act_bad_dir")
      .set({
        userId: ALICE_UID,
        familyId: "11",
        name: "رجب علی",
        fatherName: "رمضان",
        address: "کوٹ رمضان",
        eventType: "شادی",
        eventDetail: "تفصیل",
        eventDateMillis: 1771027200000,
        direction: "INVALID",
        homeItems: "",
        neondraAmount: 5000,
        reminderEnabled: false,
        reminderAtMillis: 0,
        reminderMinutesBefore: 0,
        calendarSynced: false,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject tampered future timestamp
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("activities")
      .doc("act_future_ts")
      .set({
        userId: ALICE_UID,
        familyId: "11",
        name: "رجب علی",
        fatherName: "رمضان",
        address: "کوٹ رمضان",
        eventType: "شادی",
        eventDetail: "تفصیل",
        eventDateMillis: 1771027200000,
        direction: "IN",
        homeItems: "",
        neondraAmount: 1000,
        reminderEnabled: false,
        reminderAtMillis: 0,
        reminderMinutesBefore: 0,
        calendarSynced: false,
        createdAt: new Date(Date.now() + 60000),
        updatedAt: created,
      })
  );
});

test("Cross-user isolation: Alice cannot read, list, update, or delete Bob's data", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const bobDb = context.firestore();
    const now = new Date(Date.now() - 5000);
    await bobDb.collection("users").doc(BOB_UID).collection("families").doc("bob_fam").set({
      userId: BOB_UID,
      familyId: "99",
      name: "بابر",
      fatherName: "احمد",
      address: "ملتان",
      createdAt: now,
      updatedAt: now,
    });
    await bobDb.collection("users").doc(BOB_UID).collection("activities").doc("bob_act").set({
      userId: BOB_UID,
      familyId: "99",
      name: "بابر",
      fatherName: "احمد",
      address: "ملتان",
      eventType: "ولیمہ",
      eventDetail: "تفصیل",
      eventDateMillis: 1771027200000,
      direction: "IN",
      homeItems: "نقد 5000",
      neondraAmount: 2000,
      reminderEnabled: false,
      reminderAtMillis: 0,
      reminderMinutesBefore: 0,
      calendarSynced: false,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("families").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("families").doc("bob_fam").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("activities").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("activities").doc("bob_act").update({
      neondraAmount: 99999,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("activities").doc("bob_act").delete()
  );
});
