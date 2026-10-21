import { reactive } from "vue";
import http from "../api/http";
import { session } from "./session";

/** 规则确认先于权限和业务查询；旧异步响应不能覆盖另一个登录账号。 */
export const rules = reactive({
  ready: false,
  value: null,
  loading: false,
  error: "",
});
let pending;
let generation = 0;
export function clearRules() {
  generation++;
  pending = undefined;
  rules.ready = false;
  rules.value = null;
  rules.error = "";
}
export async function refreshRules() {
  if (!session.token) {
    rules.ready = false;
    rules.value = null;
    return;
  }
  if (session.claims.memberType === "AI_LIBRARIAN") {
    rules.ready = true;
    return;
  }
  if (pending) return pending;
  const member = session.claims.memberId;
  const current = generation;
  rules.loading = true;
  const request = http
    .get("/me/borrowing-rules", { silent: true })
    .then((value) => {
      if (
        current !== generation ||
        String(member) !== String(session.claims.memberId) ||
        !session.token
      )
        return;
      rules.value = value;
      rules.error = "";
      rules.ready = true;
    })
    .catch(() => {
      if (current !== generation) return;
      rules.ready = false;
      rules.error = "须知加载失败，请重试";
    })
    .finally(() => {
      if (pending === request) pending = undefined;
      if (current === generation) rules.loading = false;
    });
  pending = request;
  return pending;
}
export async function decideRules(decision) {
  const result = await http.post("/me/borrowing-rules/decision", {
    decision,
    ruleVersion: rules.value.ruleVersion,
    expectedMemberVersion: rules.value.memberVersion,
  });
  if (decision === "DECLINE") session.clear();
  else {
    rules.value = result;
    rules.ready = true;
  }
  return result;
}
