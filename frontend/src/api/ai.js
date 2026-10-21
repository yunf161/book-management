import http from "./http";
import { session } from "../stores/session";
import { readAiEvents } from "../modules/aiStream";

export const aiApi = {
  preview: (body) => http.post("/ai/actions/preview", body),
  conversation: (id) => http.get(`/ai/conversations/${encodeURIComponent(id)}`),
  clear: (id) => http.delete(`/ai/conversations/${encodeURIComponent(id)}`),
  skills: () => http.get("/ai/skills"),
  actions: (params) => http.get("/ai/actions", { params }),
  confirm: (action) =>
    http.post(
      `/ai/actions/${encodeURIComponent(action.actionId)}/confirm`,
      { requestHash: action.requestHash },
      { timeout: 30000 },
    ),
  cancel: (action) =>
    http.post(`/ai/actions/${encodeURIComponent(action.actionId)}/cancel`),
};

/** fetch用于读取响应流；与普通HTTP客户端采用相同的登录态、令牌轮换和规则失效处理。 */
export async function streamAiChat(body, onEvent, signal) {
  const member = session.claims.memberId;
  const identity = session.user;
  const role = session.claims.roleId;
  const token = session.token;
  const controller = new AbortController();
  const cancel = () => controller.abort();
  signal?.addEventListener("abort", cancel, { once: true });
  if (signal?.aborted) cancel();
  const timer = setTimeout(cancel, 125000);
  try {
    const response = await fetch("/api/ai/chat", {
      method: "POST",
      signal: controller.signal,
      headers: {
        "Content-Type": "application/json",
        Accept: "text/event-stream, application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify(body),
    });
    const sameMember = () =>
      session.token &&
      session.user === identity &&
      String(session.claims.memberId) === String(member) &&
      String(session.claims.roleId) === String(role);
    if (!sameMember()) throw new Error("登录身份已变化，本轮回答已丢弃");
    const refreshed = response.headers.get("x-refreshed-token");
    if (refreshed) session.saveToken(refreshed);
    if (response.status === 401) session.clear();
    if (response.headers.get("x-error-code") === "RULE_CONSENT_REQUIRED")
      window.dispatchEvent(new Event("library-rules-updated"));
    if (!response.ok) {
      const result = await response.json().catch(() => ({}));
      throw new Error(result.message || result.msg || "AI服务暂不可用");
    }
    await readAiEvents(response, (event, value) => {
      if (!sameMember()) throw new Error("登录身份已变化，本轮回答已丢弃");
      onEvent(event, value);
    });
  } catch (failure) {
    if (failure.status === 401 && session.user === identity) session.clear();
    if (controller.signal.aborted && !signal?.aborted)
      throw new Error("AI回答超时，本轮内容未确认，可以稍后重试");
    throw failure;
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener("abort", cancel);
  }
}
