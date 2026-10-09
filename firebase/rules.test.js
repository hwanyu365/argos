import { after, before, test } from "node:test";
import { readFileSync } from "node:fs";
import { assertFails, initializeTestEnvironment } from "@firebase/rules-unit-testing";

// 스펙 §4.3 보안 규칙 계약 검증. TC 번호는 docs/specification.md §7 과 대응한다.
let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-argos",
    database: { rules: readFileSync(new URL("../database.rules.json", import.meta.url), "utf8") },
  });
});

after(() => env.cleanup());

test("TC#8 다른 가족의 데이터는 읽거나 쓸 수 없다", async () => {
  const db = env.authenticatedContext("parentA").database();
  await assertFails(db.ref("families/familyB").get());
  await assertFails(db.ref("families/familyB/children/x/live").set({ pkg: "a" }));
});
