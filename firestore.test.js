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

test("Unauthenticated user: cannot read or write families, categories, or activities", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("families").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("categories").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("activities").get());

  const now = new Date(Date.now() - 1000);
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("categories").doc("cat_1").set({
      userId: ALICE_UID,
      name: "شادی",
      colorHex: "#0F5C4D",
      iconKey: "celebration",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Authenticated owner: valid FamilyProfile create, partial update, and list succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("families").doc("fam_11").set({
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

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("families").doc("fam_11").update({
      address: "کوٹ رمضان جدید",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("families").doc("fam_ghost").set({
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
});

test("Authenticated owner: valid ActivityCategory CRUD & validation rules", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("categories").doc("cat_wedding").set({
      userId: ALICE_UID,
      name: "شادی",
      colorHex: "#0F5C4D",
      iconKey: "celebration",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("categories").get()
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("categories").doc("cat_wedding").update({
      name: "شادی بیاہ",
      colorHex: "#B87314",
      iconKey: "favorite",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject invalid hex color
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("categories").doc("cat_bad_color").set({
      userId: ALICE_UID,
      name: "خراب رنگ",
      colorHex: "not_hex",
      iconKey: "event",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject ghost id field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("categories").doc("cat_ghost").set({
      id: "cat_ghost",
      userId: ALICE_UID,
      name: "گھوسٹ",
      colorHex: "#0F5C4D",
      iconKey: "event",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Allow delete by owner
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("categories").doc("cat_wedding").delete()
  );
});

test("Authenticated owner: valid ActivityRecord create, partial updates, and category rename sync succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("activities").doc("act_1").set({
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

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("activities").doc("act_1").update({
      eventType: "شادی بیاہ",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("activities").doc("act_1").update({
      calendarSynced: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Cross-user isolation: Alice cannot access or modify Bob's categories or activities", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const bobDb = context.firestore();
    const now = new Date(Date.now() - 5000);
    await bobDb.collection("users").doc(BOB_UID).collection("categories").doc("bob_cat").set({
      userId: BOB_UID,
      name: "Bob Category",
      colorHex: "#1982C4",
      iconKey: "work",
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("categories").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("categories").doc("bob_cat").update({
      name: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("categories").doc("bob_cat").delete()
  );
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("events").get());
});

test("Authenticated owner: valid EventEntry create, update, list, validation, and delete succeed", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("events").get());

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("events").doc("evt_1").set({
      userId: ALICE_UID,
      title: "Family Wedding Reception",
      description: "Dinner and gift exchange at Community Hall.",
      eventDateMillis: 1771027200000,
      category: "Wedding",
      location: "Lahore",
      familyDocId: "fam_11",
      familyName: "Rajab Ali",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("events").get()
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("events").doc("evt_1").update({
      title: "Updated Reception Event",
      description: "Moved to 8 PM.",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject empty title
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("events").doc("evt_bad").set({
      userId: ALICE_UID,
      title: "",
      description: "Missing title",
      eventDateMillis: 1771027200000,
      category: "Wedding",
      location: "",
      familyName: "",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject ghost id field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("events").doc("evt_ghost").set({
      id: "evt_ghost",
      userId: ALICE_UID,
      title: "Ghost ID",
      description: "",
      eventDateMillis: 1771027200000,
      category: "Wedding",
      location: "",
      familyName: "",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("events").doc("evt_future").set({
      userId: ALICE_UID,
      title: "Future timestamp",
      description: "",
      eventDateMillis: 1771027200000,
      category: "Wedding",
      location: "",
      familyName: "",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("events").doc("evt_1").delete()
  );
});
