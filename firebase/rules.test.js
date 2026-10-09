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
    await assertFails(ref().set(live({ pkg: "x".repeat(256) })));
    await assertFails(ref().set(live({ label: "x".repeat(101) })));
    await assertSucceeds(ref().set(live({ pkg: "x".repeat(255), label: "x".repeat(100), title: "x".repeat(300), url: "x".repeat(2048) })));
  });

  test("타입이 다르면 거부한다", async () => {
    await seed(withKids());
    await assertFails(ref().set(live({ screenOn: "true" })));
    await assertFails(ref().set(live({ since: "1" })));
    for (const k of ["usage", "a11y", "notif"]) await assertFails(ref().set(live({ perms: { usage: true, a11y: false, notif: false, [k]: "yes" } })));
  });

  test("PiP 앱은 pkg·label·since 만, 길이·타입이 맞을 때 쓸 수 있다", async () => {
    await seed(withKids());
    await assertSucceeds(ref().set(live({ pip: { pkg: "com.google.android.youtube", label: "YouTube", since: 1 } })));
    await assertFails(ref().set(live({ pip: { pkg: "x".repeat(256), label: "a", since: 1 } })));
    await assertFails(ref().set(live({ pip: { pkg: "a", label: "x".repeat(101), since: 1 } })));
    await assertFails(ref().set(live({ pip: { pkg: "a", label: "a", since: "1" } })));
    await assertFails(ref().set(live({ pip: { pkg: "a", label: "a", since: 1, title: "x" } })));
    await assertFails(ref().set(live({ pip: { label: "a", since: 1 } })));
    await assertFails(ref().set(live({ pip: { pkg: "a", label: "a" } })));
    await assertFails(ref().set(live({ pip: { pkg: 1, label: "a", since: 1 } })));
    await assertFails(ref().set(live({ pip: { pkg: "a", label: 1, since: 1 } })));
  });

  test("자녀 노드에는 정의된 하위 노드만 쓸 수 있다", async () => {
    await seed(withKids());
    await assertFails(db("kidX").ref(`families/${FID}/children/kidX/blob`).set("x".repeat(1000)));
  });
});

describe("TC#52 일별 사용 시간 형식 검증", () => {
  const base = () => db("kidX").ref(`families/${FID}/children/kidX`);

  test("날짜별 앱 사용 초와 하루 총합을 쓸 수 있다", async () => {
    await seed(withKids());
    await assertSucceeds(base().child("daily/2026-10-10").set({ "com,google,android,youtube": 3600, "com,kakao,talk": 600 }));
    await assertSucceeds(base().child("dailyTotal/2026-10-10").set(3900));
    await assertSucceeds(base().child("dailyShorts/2026-10-10").set(1200));
  });

  test("날짜 키가 yyyy-MM-dd 가 아니면 거부한다", async () => {
    await seed(withKids());
    await assertFails(base().child("daily/20261010").set({ a: 1 }));
    await assertFails(base().child("daily/2026-10-10").set(5));
    await assertFails(base().child("dailyTotal/2026-1-1").set(1));
  });

  test("앱 키는 패키지 키 형식(영숫자·밑줄·쉼표)만 허용한다", async () => {
    await seed(withKids());
    await assertFails(base().child("daily/2026-10-10").set({ "a b": 1 }));
    await assertFails(base().child("daily/2026-10-10").set({ ["x".repeat(256)]: 1 }));
  });

  test("값은 0~90000 초의 정수만 허용한다 (서머타임 25시간 날 포함)", async () => {
    await seed(withKids());
    await assertFails(base().child("daily/2026-10-10").set({ a: -1 }));
    await assertFails(base().child("daily/2026-10-10").set({ a: 90001 }));
    await assertSucceeds(base().child("daily/2026-10-10").set({ a: 90000 }));
    await assertFails(base().child("daily/2026-10-10").set({ a: "1" }));
    await assertFails(base().child("dailyTotal/2026-10-10").set(90001));
    await assertFails(base().child("dailyShorts/2026-10-10").set(90001));
    await assertFails(base().child("dailyShorts/2026-1-1").set(1));
    await assertFails(base().child("dailyShorts/2026-10-10").set(1.5));
    await assertFails(base().child("dailyShorts/2026-10-10").set(-1));
    await assertFails(base().child("dailyShorts/2026-10-10").set("1"));
    await assertFails(base().child("dailyTotal/2026-10-10").set(1.5));
  });

  test("자녀는 보관 기간이 지난 자기 일별 기록을 지울 수 있다", async () => {
    await seed(withKids());
    await assertSucceeds(base().child("daily/2026-10-10").set({ a: 1 }));
    await assertSucceeds(base().update({ "daily/2026-10-10": null, "dailyTotal/2026-10-10": null }));
    await assertFails(db("kidY").ref(`families/${FID}/children/kidX/daily/2026-10-10`).remove());
  });

  test("부모는 일별 기록을 읽을 수 있고 쓸 수 없다", async () => {
    await seed(withKids());
    await assertSucceeds(db("parent1").ref(`families/${FID}/children/kidX/daily`).get());
    await assertFails(db("parent1").ref(`families/${FID}/children/kidX/daily/2026-10-10`).set({ a: 1 }));
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

  test("자녀는 탈퇴하면서 자기 기록도 함께 지울 수 있다 (초기화)", async () => {
    await seed(withKids());
    await assertSucceeds(db("kidY").ref(`families/${FID}`).update({ "members/kidY": null, "children/kidY": null }));
  });

  test("자녀는 다른 자녀의 기록을 지울 수 없다", async () => {
    await seed(withKids());
    await assertFails(db("kidX").ref(`families/${FID}/children/kidY`).remove());
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
