import test from "node:test";
import assert from "node:assert/strict";
import { coverImageUrl } from "../src/modules/bookCover.js";

test("封面走业务代理地址而不是对象key", () => {
  assert.equal(
    coverImageUrl("/books/21/cover/content"),
    "/api/books/21/cover/content",
  );
  assert.equal(coverImageUrl("library/covers/21/a.png"), "");
});

test("缺少封面与完整HTTPS地址展示", () => {
  assert.equal(coverImageUrl(null), "");
  assert.equal(
    coverImageUrl("https://example.com/a.png"),
    "https://example.com/a.png",
  );
});
