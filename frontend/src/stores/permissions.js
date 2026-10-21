import { reactive } from "vue";
import { api } from "../api/library";
import { session } from "./session";

/** 授权摘要只控制界面可见性；每项业务操作仍由后端按当前授权重新判断。 */
export const access = reactive({
  grants: [],
  ready: false,
  memberId: null,
  has(code, minimum) {
    if (!this.ready || !session.token) return false;
    const scope = this.grants.find((grant) => grant.code === code)?.dataScope;
    if (!scope) return false;
    if (!minimum || minimum === "NONE") return true;
    return scope === "ALL" || scope === minimum;
  },
  forMember(code, memberId) {
    return (
      this.has(code, "ALL") ||
      (this.has(code, "SELF") && String(memberId) === String(this.memberId))
    );
  },
});
let generation = 0;
let pending;

export function clearPermissions() {
  generation++;
  pending = undefined;
  access.grants = [];
  access.ready = false;
  access.memberId = null;
}

export async function refreshPermissions() {
  if (!session.token) {
    clearPermissions();
    return;
  }
  if (pending) return pending;
  const current = generation;
  const member = session.claims.memberId;
  const role = session.claims.roleId;
  const request = Promise.resolve().then(async () => {
    try {
      const result = await api.currentPermissions();
      if (
        current !== generation ||
        !session.token ||
        String(session.claims.memberId) !== String(member) ||
        String(session.claims.roleId) !== String(role)
      )
        return;
      access.grants = result.permissions || [];
      access.memberId = result.memberId;
      access.ready = true;
    } catch {
      // 获取失败时关闭界面操作，避免依赖以前账号或以前角色的授权快照。
      if (current === generation) {
        access.grants = [];
        access.ready = false;
      }
    } finally {
      if (pending === request) pending = undefined;
    }
  });
  pending = request;
  return request;
}

/** 路由只显示已实现且当前角色可查询的模块。成员类型仍保留业务身份限制。 */
export function canEnter(path) {
  if (["/home", "/catalog", "/login"].includes(path)) return true;
  const requirements = {
    "/ai": [["ai:chat:self", "SELF"]],
    "/borrow": [
      ["borrow-application:query", "SELF"],
      ["borrow-record:query", "SELF"],
    ],
    "/reading-plan": [["reading-plan:query:self", "SELF"]],
    "/messages": [["message:query:self"]],
    "/profile": [["member:query", "SELF"]],
    "/member-changes": [["member-change:query:self"]],
    "/admin/books": [["book-copy:query"]],
    "/admin/borrow": [
      ["borrow-application:query", "ALL"],
      ["borrow-record:query", "ALL"],
    ],
    "/admin/readers": [
      ["member:query", "ALL"],
      ["member:query", "DEPARTMENT"],
    ],
    "/admin/staff": [
      ["member:query", "ALL"],
      ["member:query", "DEPARTMENT"],
    ],
    "/admin/departments": [["department:query"]],
    "/admin/permissions": [["permission:query", "ALL"]],
    "/admin/operation-logs": [["operation-log:query", "ALL"]],
    "/admin/knowledge": [["knowledge:query", "ALL"]],
    "/admin/operations": [["operations:query", "ALL"]],
    "/ai/tasks": [["ai-task:query", "SELF"]],
    "/ai/handoffs": [["ai-handoff:query", "SELF"]],
    "/admin/ai-controls": [["ai-control:manage", "ALL"]],
    "/admin/policies": [["policy:query", "ALL"]],
    "/admin/approvals": [
      ["member-change:query:reader", "ALL"],
      ["member-change:query:employee", "ALL"],
    ],
    "/admin/messages": [["message:query:self"]],
    "/admin/plans": [["reading-plan:query", "SELF"]],
  };
  if (path === "/admin/messages" && session.claims.memberType !== "EMPLOYEE")
    return false;
  if (path === "/admin/dashboard")
    return Object.keys(requirements).some(
      (key) => key.startsWith("/admin") && canEnter(key),
    );
  return (requirements[path] || []).some(([code, scope]) =>
    access.has(code, scope),
  );
}
