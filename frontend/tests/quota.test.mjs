import test from "node:test";
import assert from "node:assert/strict";
import { effectiveBorrowLimit } from "../src/modules/borrowQuota.js";

test("无临时额度使用长期额度", () => {
  assert.equal(effectiveBorrowLimit({ permanentBorrowLimit: 5 }), 5);
  assert.equal(effectiveBorrowLimit(null), null);
});
test("临时额度是总额，不叠加长期值", () => {
  assert.equal(
    effectiveBorrowLimit(
      {
        permanentBorrowLimit: 5,
        temporaryBorrowLimit: 8,
        temporaryQuotaExpiresAt: "2030-01-01T00:00:00",
      },
      Date.parse("2029-12-31T23:59:59"),
    ),
    8,
  );
});
test("到期瞬间恢复最新长期值，即使页面还没有重新查询", () => {
  const member = {
    permanentBorrowLimit: 6,
    temporaryBorrowLimit: 8,
    temporaryQuotaExpiresAt: "2030-01-01T00:00:00",
  };
  assert.equal(
    effectiveBorrowLimit(member, Date.parse(member.temporaryQuotaExpiresAt)),
    6,
  );
});
test("无效或缺失到期时间不会让临时值永久生效", () => {
  assert.equal(
    effectiveBorrowLimit({ permanentBorrowLimit: 5, temporaryBorrowLimit: 8 }),
    5,
  );
  assert.equal(
    effectiveBorrowLimit({
      permanentBorrowLimit: 5,
      temporaryBorrowLimit: 8,
      temporaryQuotaExpiresAt: "invalid",
    }),
    5,
  );
});
