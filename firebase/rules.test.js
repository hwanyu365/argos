import { after, afterEach, before, describe, test } from "node:test";
import { readFileSync } from "node:fs";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";

// 스펙 §4.3 보안 규칙 계약 검증. TC 번호는 docs/specification.md §7 과 대응한다.
let env;
const FID = "familyA";
const CODE = "ABCDEFGH23";
const MIN = 60_000;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-argos",
    database: { rules: readFileSync(new URL("../database.rules.json", import.meta.url), "utf8") },
  });
});

afterEach(() => env.clearDatabase());
after(() => env.cleanup());

const db = (uid) => env.authenticatedContext(uid).database();
const live = (extra = {}) => ({ pkg: "com.google.android.youtube", label: "YouTube", since: 1, screenOn: true, updatedAt: { ".sv": "timestamp" }, perms: { usage: true, a11y: false, notif: false }, ...extra });
const member = (role, extra = {}) => ({ role, name: role === "parent" ? "엄마" : "첫째", joinedAt: { ".sv": "timestamp" }, ...extra });

// 규칙을 거치지 않고 초기 상태를 만든다.
async function seed(data) {
  await env.withSecurityRulesDisabled((ctx) => ctx.database().ref().set(data));
}

const parentFamily = (extra = {}) => ({
  families: { [FID]: { members: { parent1: { role: "parent", name: "엄마", joinedAt: 1 } }, ...extra } },
});

describe("TC#4 가족 생성", () => {
  test("빈 가족에 첫 부모로 자신을 등록할 수 있다", async () => {
    await assertSucceeds(db("parent1").ref(`families/${FID}/members/parent1`).set(member("parent")));
  });

  test("빈 가족이라도 자녀로 첫 등록은 할 수 없다", async () => {
    await assertFails(db("kid1").ref(`families/${FID}/members/kid1`).set(member("child")));
  });

  test("멤버가 모두 떠나 데이터만 남은 가족은 첫 부모로 차지할 수 없다", async () => {
    await seed({ families: { [FID]: { children: { kidX: { live: { pkg: "a" } } } } } });
    await assertFails(db("intruder").ref(`families/${FID}/members/intruder`).set(member("parent")));
  });

  test("이미 멤버가 있는 가족에는 코드 없이 부모로 들어갈 수 없다", async () => {
    await seed(parentFamily());
    await assertFails(db("intruder").ref(`families/${FID}/members/intruder`).set(member("parent")));
  });

  test("다른 사람의 uid 로 멤버를 만들 수 없다", async () => {
    await assertFails(db("parent1").ref(`families/${FID}/members/someone`).set(member("parent")));
  });
});

describe("TC#6 초대 코드로 참여", () => {
  test("유효한 코드면 자녀로 참여할 수 있다", async () => {
    await seed({ ...parentFamily(), pairing: { [CODE]: { familyId: FID, role: "child", expiresAt: Date.now() + 5 * MIN } } });
    await assertSucceeds(db("kid1").ref(`families/${FID}/members/kid1`).set(member("child", { code: CODE })));
  });

  test("유효한 코드면 두 번째 부모로 참여할 수 있다 (US#7)", async () => {
    await seed({ ...parentFamily(), pairing: { [CODE]: { familyId: FID, role: "parent", expiresAt: Date.now() + 5 * MIN } } });
    await assertSucceeds(db("parent2").ref(`families/${FID}/members/parent2`).set(member("parent", { code: CODE })));
  });

  test("만료된 코드로는 참여할 수 없다", async () => {
    await seed({ ...parentFamily(), pairing: { [CODE]: { familyId: FID, role: "child", expiresAt: Date.now() - 1 } } });
    await assertFails(db("kid1").ref(`families/${FID}/members/kid1`).set(member("child", { code: CODE })));
  });

  test("자녀용 코드로 부모 역할을 등록할 수 없다 (역할 승격 방지)", async () => {
    await seed({ ...parentFamily(), pairing: { [CODE]: { familyId: FID, role: "child", expiresAt: Date.now() + 5 * MIN } } });
    await assertFails(db("kid1").ref(`families/${FID}/members/kid1`).set(member("parent", { code: CODE })));
  });

  test("이름은 1~40자, 참여 시각은 서버 시각만 허용한다", async () => {
    await seed({ ...parentFamily(), pairing: { [CODE]: { familyId: FID, role: "child", expiresAt: Date.now() + 5 * MIN } } });
    const ref = db("kid1").ref(`families/${FID}/members/kid1`);
    await assertFails(ref.set(member("child", { code: CODE, name: "" })));
    await assertFails(ref.set(member("child", { code: CODE, name: "가".repeat(41) })));
    await assertFails(ref.set(member("child", { code: CODE, joinedAt: 1 })));
  });

  test("이미 등록된 멤버 레코드는 수정할 수 없다", async () => {
    // 유효한 코드가 있어도 생성이 아니라 수정이면 거부되어야 한다.
    await seed({
      ...parentFamily({ members: { parent1: { role: "parent", name: "엄마", joinedAt: 1 }, kid1: { role: "child", name: "첫째", joinedAt: 1 } } }),
      pairing: { [CODE]: { familyId: FID, role: "child", expiresAt: Date.now() + 5 * MIN } },
    });
    await assertFails(db("kid1").ref(`families/${FID}/members/kid1`).set(member("child", { code: CODE, name: "바뀐이름" })));
    await assertFails(db("kid1").ref(`families/${FID}/members/kid1/role`).set("parent"));
    await assertFails(db("kid1").ref(`families/${FID}/members/kid1`).set(member("parent")));
  });

  test("다른 가족의 코드로는 참여할 수 없다", async () => {
    await seed({ ...parentFamily(), pairing: { [CODE]: { familyId: "familyB", role: "child", expiresAt: Date.now() + 5 * MIN } } });
    await assertFails(db("kid1").ref(`families/${FID}/members/kid1`).set(member("child", { code: CODE })));
  });
});

describe("TC#7 초대 코드 발급·조회", () => {
  test("부모 멤버는 10분 이내 만료 코드를 발급할 수 있다", async () => {
    await seed(parentFamily());
    await assertSucceeds(db("parent1").ref(`pairing/${CODE}`).set({ familyId: FID, role: "child", expiresAt: Date.now() + 10 * MIN - 1000 }));
  });

  test("코드에는 참여 역할(parent|child)이 있어야 한다", async () => {
    await seed(parentFamily());
    await assertFails(db("parent1").ref(`pairing/${CODE}`).set({ familyId: FID, expiresAt: Date.now() + MIN }));
    await assertFails(db("parent1").ref(`pairing/${CODE}`).set({ familyId: FID, role: "admin", expiresAt: Date.now() + MIN }));
  });

  test("다른 가족의 부모는 기존 코드를 덮어쓸 수 없다", async () => {
    await seed({
      families: {
        [FID]: { members: { parent1: { role: "parent", name: "엄마", joinedAt: 1 } } },
        familyB: { members: { parentB: { role: "parent", name: "아빠", joinedAt: 1 } } },
      },
      pairing: { [CODE]: { familyId: FID, role: "child", expiresAt: Date.now() + 5 * MIN } },
    });
    await assertFails(db("parentB").ref(`pairing/${CODE}`).set({ familyId: "familyB", role: "child", expiresAt: Date.now() + MIN }));
  });

  test("기기 시계가 서버보다 30초 빨라도 10분 코드는 발급된다", async () => {
    await seed(parentFamily());
    await assertSucceeds(db("parent1").ref(`pairing/${CODE}`).set({ familyId: FID, role: "child", expiresAt: Date.now() + 10 * MIN + 30_000 }));
  });

  test("만료가 허용 상한(10분 + 시계 여유 1분)을 넘는 코드는 발급할 수 없다", async () => {
    await seed(parentFamily());
    await assertFails(db("parent1").ref(`pairing/${CODE}`).set({ familyId: FID, role: "child", expiresAt: Date.now() + 12 * MIN }));
  });

  test("형식이 아닌 코드(Crockford Base32 10자리 아님)는 발급할 수 없다", async () => {
    await seed(parentFamily());
    await assertFails(db("parent1").ref("pairing/abcdefgh23").set({ familyId: FID, role: "child", expiresAt: Date.now() + MIN }));
    await assertFails(db("parent1").ref("pairing/ABCDEFGHIL").set({ familyId: FID, role: "child", expiresAt: Date.now() + MIN }));
  });

  test("부모가 아닌 사용자는 코드를 발급할 수 없다", async () => {
    await seed(parentFamily({ members: { parent1: { role: "parent", name: "엄마", joinedAt: 1 }, kid1: { role: "child", name: "첫째", joinedAt: 1 } } }));
    await assertFails(db("kid1").ref(`pairing/${CODE}`).set({ familyId: FID, role: "child", expiresAt: Date.now() + MIN }));
    await assertFails(db("stranger").ref(`pairing/${CODE}`).set({ familyId: FID, role: "child", expiresAt: Date.now() + MIN }));
  });

  test("코드를 아는 인증 사용자는 그 코드만 읽을 수 있고 목록은 읽을 수 없다", async () => {
    await seed({ ...parentFamily(), pairing: { [CODE]: { familyId: FID, role: "child", expiresAt: Date.now() + MIN } } });
    await assertSucceeds(db("kid1").ref(`pairing/${CODE}`).get());
    await assertFails(db("kid1").ref("pairing").get());
    await assertFails(env.unauthenticatedContext().database().ref(`pairing/${CODE}`).get());
  });
});

describe("TC#8 가족 간 격리", () => {
  test("다른 가족의 데이터는 읽거나 쓸 수 없다", async () => {
    await seed(parentFamily());
    const other = db("parentB");
    await assertFails(other.ref(`families/${FID}`).get());
    await assertFails(other.ref(`families/${FID}/children/x/live`).set({ pkg: "a" }));
  });

  test("멤버는 자기 가족 데이터를 읽을 수 있다", async () => {
    await seed(parentFamily());
    await assertSucceeds(db("parent1").ref(`families/${FID}`).get());
  });
});

const withKids = () =>
  parentFamily({
    members: {
      parent1: { role: "parent", name: "엄마", joinedAt: 1 },
      kidX: { role: "child", name: "첫째", joinedAt: 1 },
      kidY: { role: "child", name: "둘째", joinedAt: 1 },
    },
    children: { kidX: { live: { pkg: "a" } }, kidY: { live: { pkg: "b" } } },
  });

describe("TC#9 자녀 노드 쓰기 권한", () => {
  test("자녀는 자기 노드에 쓸 수 있다", async () => {
    await seed(withKids());
    await assertSucceeds(db("kidX").ref(`families/${FID}/children/kidX/live`).set(live()));
  });

  test("자녀는 다른 자녀 노드에 쓸 수 없다", async () => {
    await seed(withKids());
    await assertFails(db("kidX").ref(`families/${FID}/children/kidY/live`).set(live()));
  });

  test("부모는 자녀 노드에 값을 쓸 수 없고 삭제만 할 수 있다", async () => {
    await seed(withKids());
    await assertFails(db("parent1").ref(`families/${FID}/children/kidX/live`).set(live()));
    await assertSucceeds(db("parent1").ref(`families/${FID}/children/kidX`).remove());
  });
});

describe("TC#46 live 형식 검증", () => {
  const ref = () => db("kidX").ref(`families/${FID}/children/kidX/live`);

  test("화면 꺼짐 상태는 앱 정보 없이 올릴 수 있다", async () => {
    await seed(withKids());
    await assertSucceeds(ref().set(live({ pkg: null, label: null, since: null, screenOn: false })));
  });

  test("필수 필드(screenOn, updatedAt, perms)가 없으면 거부한다", async () => {
    await seed(withKids());
    for (const k of ["screenOn", "updatedAt", "perms"]) await assertFails(ref().set(live({ [k]: null })));
  });

  test("updatedAt 은 서버 시각만, 정의되지 않은 필드는 거부한다", async () => {
    await seed(withKids());
    await assertFails(ref().set(live({ updatedAt: 1 })));
    await assertFails(ref().set(live({ extra: "x" })));
    await assertFails(ref().set(live({ perms: { usage: true, a11y: false, notif: false, extra: true } })));
  });

  test("문자열 길이 상한을 넘으면 거부한다 (무료 한도 소진 방지)", async () => {
    await seed(withKids());
    await assertFails(ref().set(live({ title: "x".repeat(301) })));
    await assertFails(ref().set(live({ url: "x".repeat(2049) })));
    await assertSucceeds(ref().set(live({ title: "x".repeat(300), url: "x".repeat(2048) })));
  });

  test("자녀 노드에는 정의된 하위 노드만 쓸 수 있다", async () => {
    await seed(withKids());
    await assertFails(db("kidX").ref(`families/${FID}/children/kidX/blob`).set("x".repeat(1000)));
  });
});

describe("TC#10 기기 제거", () => {
  test("부모가 자녀를 제거하면 이후 그 자녀는 쓰기·읽기가 거부된다", async () => {
    await seed(withKids());
    await assertSucceeds(db("parent1").ref(`families/${FID}`).update({ "members/kidX": null, "children/kidX": null }));
    await assertFails(db("kidX").ref(`families/${FID}/children/kidX/live`).set(live()));
    await assertFails(db("kidX").ref(`families/${FID}`).get());
  });

  test("멤버는 스스로 탈퇴할 수 있다", async () => {
    await seed(withKids());
    await assertSucceeds(db("kidY").ref(`families/${FID}/members/kidY`).remove());
  });

  test("자녀는 다른 멤버를 제거할 수 없다", async () => {
    await seed(withKids());
    await assertFails(db("kidX").ref(`families/${FID}/members/parent1`).remove());
    await assertFails(db("kidX").ref(`families/${FID}/members/kidY`).remove());
  });
});

describe("TC#45 앱 이름 공유", () => {
  test("자녀는 앱 이름을 쓸 수 있다", async () => {
    await seed(withKids());
    await assertSucceeds(db("kidX").ref(`families/${FID}/apps/com,google,android,youtube`).set({ label: "YouTube" }));
  });

  test("부모·외부인은 앱 이름을 쓸 수 없다", async () => {
    await seed(withKids());
    await assertFails(db("parent1").ref(`families/${FID}/apps/a,b`).set({ label: "x" }));
    await assertFails(db("stranger").ref(`families/${FID}/apps/a,b`).set({ label: "x" }));
  });

  test("label 은 1~100자 문자열만, 다른 필드는 쓸 수 없다", async () => {
    await seed(withKids());
    const ref = db("kidX").ref(`families/${FID}/apps/a,b`);
    await assertFails(ref.set({ label: "" }));
    await assertFails(ref.set({ label: "x".repeat(101) }));
    await assertFails(ref.set({ label: "x", extra: 1 }));
  });
});
