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
const ADMIN_UID = "admin_789";
const CHARLIE_UID = "charlie_999";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

test("Unauthenticated user: cannot read or write user profile", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("users").doc("test").set({ displayName: "Hacker" }));
});

test("Authenticated user: can create own profile with Free tier and unverified status", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc("B600001").set({
      userId: ALICE_UID,
      profileCode: "B600001",
      displayName: "Alice Khan",
      gender: "Female",
      age: 26,
      maritalStatus: "Never Married",
      religion: "Islam",
      profession: "Software Engineer",
      locationDistrict: "Dhaka",
      packageTier: "Free",
      isVerified: false,
      verificationStatus: "UNSUBMITTED",
      createdAt: new Date(),
    })
  );
});

test("Authenticated user: cannot self-grant isVerified or Paid Package Tier", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc("B600001").set({
      userId: ALICE_UID,
      profileCode: "B600001",
      displayName: "Alice Khan",
      gender: "Female",
      age: 26,
      maritalStatus: "Never Married",
      religion: "Islam",
      profession: "Software Engineer",
      locationDistrict: "Dhaka",
      packageTier: "Gold",
      isVerified: true,
      createdAt: new Date(),
    })
  );
});

test("Authenticated user: cannot create profile for another user ID", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob Ahmed",
      gender: "Male",
      age: 29,
      maritalStatus: "Never Married",
      religion: "Islam",
      profession: "Doctor",
      locationDistrict: "Sylhet",
      packageTier: "Free",
      createdAt: new Date(),
    })
  );
});

test("Authenticated user cannot increase quotas or extend a package expiry", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc("B600001");
  await assertSucceeds(
    profileRef.set({
      userId: ALICE_UID,
      profileCode: "B600001",
      packageTier: "Free",
      packageActivatedAt: 0,
      packageExpiresAt: 0,
      interestsRemaining: 2,
      contactsRemaining: 0,
      isVerified: false,
    })
  );
  await assertFails(
    profileRef.update({
      packageExpiresAt: Date.now() + 30 * 24 * 60 * 60 * 1000,
      interestsRemaining: 100,
      contactsRemaining: 50,
    })
  );
  await assertSucceeds(profileRef.update({ interestsRemaining: 1 }));
});

test("Public profile mirror exposes biodata but rejects contact and entitlement fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("publicProfiles").doc("B600001").set({
      userId: ALICE_UID,
      profileCode: "B600001",
      displayName: "Alice Khan",
      age: 26,
      locationDistrict: "Dhaka",
      packageTier: "Free",
      isVerified: false,
      isAdmin: false,
      role: "USER",
    })
  );
  await assertFails(
    aliceDb.collection("publicProfiles").doc("B600001").update({
      contactPhone: "01700000000",
    })
  );
  await assertFails(
    aliceDb.collection("publicProfiles").doc("B600001").update({
      packageTier: "Gold",
      isVerified: true,
    })
  );
});

test("Authenticated user: can submit PENDING verificationRequest and paymentRequest, but cannot self-approve", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("verificationRequests").doc("B600001").set({
      requestId: "B600001",
      userId: ALICE_UID,
      profileCode: "B600001",
      displayName: "Alice Khan",
      gender: "Female",
      docType: "Smart NID Card",
      idNumber: "1998123456789",
      docUrl: "https://res.cloudinary.com/dfff99g1f/image/upload/sample_nid.jpg",
      status: "PENDING",
      createdAt: new Date(),
    })
  );

  // Cannot self-approve verificationRequest
  await assertFails(
    aliceDb.collection("verificationRequests").doc("B600001").update({
      status: "APPROVED",
    })
  );

  await assertSucceeds(
    aliceDb.collection("paymentRequests").doc("pay_B600001_1").set({
      requestId: "pay_B600001_1",
      userId: ALICE_UID,
      profileCode: "B600001",
      displayName: "Alice Khan",
      packageTier: "Gold",
      amountBdt: 2499,
      paymentMethod: "bKash",
      senderPhone: "01711223344",
      transactionId: "BKA998877",
      status: "PENDING",
      createdAt: new Date(),
    })
  );

  // Cannot self-approve paymentRequest
  await assertFails(
    aliceDb.collection("paymentRequests").doc("pay_B600001_1").update({
      status: "APPROVED",
    })
  );
});

test("Admin Web App user: can approve verificationRequest, paymentRequest, and grant Verified Badge & Package Tier", async () => {
  const adminEmail = "admin@example.com";
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("admins").doc(adminEmail).set({
      email: adminEmail,
      role: "SUPER_ADMIN",
      isActive: true,
    });
    await context.firestore().collection("users").doc("B600001").set({
      userId: ALICE_UID,
      profileCode: "B600001",
      displayName: "Alice Khan",
      gender: "Female",
      age: 26,
      maritalStatus: "Never Married",
      religion: "Islam",
      profession: "Software Engineer",
      locationDistrict: "Dhaka",
      packageTier: "Free",
      isVerified: false,
      verificationStatus: "PENDING",
      createdAt: new Date(),
    });
    await context.firestore().collection("verificationRequests").doc("B600001").set({
      requestId: "B600001",
      userId: ALICE_UID,
      profileCode: "B600001",
      docType: "Smart NID Card",
      idNumber: "1998123456789",
      docUrl: "https://res.cloudinary.com/dfff99g1f/image/upload/sample_nid.jpg",
      status: "PENDING",
      createdAt: new Date(),
    });
  });

  // Spoofed admin with email_verified: false must fail
  const spoofAdminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: adminEmail,
    email_verified: false,
  }).firestore();
  await assertFails(
    spoofAdminDb.collection("users").doc("B600001").update({
      isVerified: true,
      verificationStatus: "VERIFIED",
    })
  );

  // Verified admin in /admins collection succeeds
  const realAdminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: adminEmail,
    email_verified: true,
  }).firestore();
  await assertSucceeds(
    realAdminDb.collection("verificationRequests").doc("B600001").update({
      status: "APPROVED",
    })
  );
  await assertSucceeds(
    realAdminDb.collection("users").doc("B600001").update({
      isVerified: true,
      verificationStatus: "VERIFIED",
      packageTier: "Gold",
      interestsRemaining: 50,
      contactsRemaining: 25,
    })
  );
});

test("Notifications & Broadcasts: only Admin can dispatch; signed-in members can read; unauthenticated blocked", async () => {
  const adminEmail = "admin@example.com";
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("admins").doc(adminEmail).set({
      email: adminEmail,
      role: "SUPER_ADMIN",
      isActive: true,
    });
  });

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const realAdminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: adminEmail,
    email_verified: true,
  }).firestore();

  // Regular member cannot spoof a broadcast or notification
  await assertFails(
    aliceDb.collection("notifications").doc("notif_spoof").set({
      type: "PACKAGE_APPROVED",
      targetUserId: ALICE_UID,
      title: "Fake Approval",
      createdAt: new Date(),
    })
  );
  await assertFails(
    aliceDb.collection("broadcasts").doc("bcast_spoof").set({
      type: "BROADCAST",
      targetSegment: "FREE_TIER",
      titleBn: "গোল্ডেন নিকাহ ও ডায়মন্ড প্যাকেজে বিশেষ ২৫% ছাড়!",
      createdAt: new Date(),
    })
  );

  // Verified Admin can create broadcast & targeted user notifications
  await assertSucceeds(
    realAdminDb.collection("broadcasts").doc("bcast_festive_25").set({
      broadcastId: "bcast_festive_25",
      type: "BROADCAST",
      targetSegment: "Free-Tier Members (Discount & Upgrade campaign)",
      title: "25% Festive Discount on Golden Nikah & Diamond Packages!",
      body: "Unlock direct mobile numbers and guardian details now with bKash or Nagad.",
      titleBn: "গোল্ডেন নিকাহ ও ডায়মন্ড প্যাকেজে বিশেষ ২৫% ছাড়!",
      bodyBn: "সরাসরি মোবাইল নম্বর ও অভিভাবকের তথ্য আনলক করুন এখনই। চেকআউটে বিকাশ বা নগদে বিশেষ ছাড় উপভোগ করুন।",
      actionRoute: "PACKAGES",
      showBanner: true,
      pushNotification: true,
      createdAt: new Date(),
    })
  );

  await assertSucceeds(
    realAdminDb.collection("notifications").doc("notif_kyc_B600001").set({
      notificationId: "notif_kyc_B600001",
      type: "ACCOUNT_APPROVED",
      targetUserId: ALICE_UID,
      targetProfileCode: "B600001",
      title: "Your Ghotok Profile is Now Verified!",
      body: "Admin has verified your Smart NID Card. Your Verified Badge is now active.",
      titleBn: "আপনার ঘটক প্রোফাইল ভেরিফাইড হয়েছে!",
      bodyBn: "অ্যাডমিন আপনার এনআইডি যাচাই সম্পন্ন করেছেন। আপনার ভেরিফাইড ব্যাজ এখন সক্রিয়।",
      actionRoute: "PROFILE",
      showBanner: true,
      pushNotification: true,
      createdAt: new Date(),
    })
  );

  // Signed-in member can read notifications and broadcasts, unauthenticated cannot
  await assertSucceeds(aliceDb.collection("notifications").get());
  await assertSucceeds(aliceDb.collection("broadcasts").get());
  await assertFails(unauthDb.collection("notifications").get());
  await assertFails(unauthDb.collection("broadcasts").get());
});

test("Private profile data and conversations are limited to owners and participants", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob Ahmed",
      dateOfBirth: "1997-04-12",
      contactPhone: "01700000000",
    });
    await db.collection("publicProfiles").doc("B600002").set({
      userId: BOB_UID,
      profileCode: "B600002",
      displayName: "Bob Ahmed",
      age: 29,
      locationDistrict: "Dhaka",
    });
    await db.collection("profileContacts").doc(BOB_UID).set({
      userId: BOB_UID,
      contactPhone: "01700000000",
    });
    await db.collection("profileContacts").doc(ALICE_UID).set({
      userId: ALICE_UID,
      contactPhone: "01711111111",
    });
    await db.collection("chats").doc("chat_alice_bob").set({
      participantIds: [ALICE_UID, BOB_UID],
    });
    await db.collection("chats").doc("chat_alice_bob").collection("messages").doc("msg_1").set({
      senderId: BOB_UID,
      text: "Hello",
      participantIds: [ALICE_UID, BOB_UID],
    });
    await db.collection("reports").doc("report_alice").set({
      reporterId: ALICE_UID,
      details: "Private report",
    });
    await db.collection("callLogs").doc("call_alice_bob").set({
      callerId: ALICE_UID,
      calleeId: BOB_UID,
    });
    await db.collection("interests").doc("int_alice_bob").set({
      senderId: ALICE_UID,
      receiverId: BOB_UID,
      status: "pending",
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  await assertSucceeds(aliceDb.collection("publicProfiles").doc("B600002").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("profileContacts").doc(BOB_UID).get());
  await assertSucceeds(aliceDb.collection("profileContacts").doc(ALICE_UID).get());

  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_alice_bob").collection("messages").doc("msg_1").get()
  );
  await assertFails(
    charlieDb.collection("chats").doc("chat_alice_bob").collection("messages").doc("msg_1").get()
  );
  await assertSucceeds(aliceDb.collection("reports").doc("report_alice").get());
  await assertFails(charlieDb.collection("reports").doc("report_alice").get());
  await assertSucceeds(aliceDb.collection("callLogs").doc("call_alice_bob").get());
  await assertFails(charlieDb.collection("callLogs").doc("call_alice_bob").get());
  await assertFails(
    aliceDb.collection("interests").doc("int_alice_bob").update({ status: "accepted" })
  );
  await assertSucceeds(
    bobDb.collection("interests").doc("int_alice_bob").update({ status: "accepted" })
  );

  await assertFails(
    aliceDb.collection("publicProfiles").doc("B600003").set({
      userId: ALICE_UID,
      profileCode: "B600003",
      packageTier: "Free",
      isVerified: false,
      dateOfBirth: "1997-04-12",
      contactPhone: "01700000000",
    })
  );
  await assertFails(
    aliceDb.collection("publicProfiles").doc("B600003").set({
      userId: ALICE_UID,
      profileCode: "B600003",
      packageTier: "Gold",
      isVerified: true,
    })
  );
});
