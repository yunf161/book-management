import axios from "axios";
import { ElMessage } from "element-plus";
import { session } from "../stores/session";

const http = axios.create({ baseURL: "/api", timeout: 12000 });

function currentIdentity() {
  return {
    user: session.user,
    member: session.claims.memberId,
    role: session.claims.roleId,
    authenticated: !!session.token,
  };
}

function isCurrent(config) {
  const owner = config?._sessionIdentity;
  return (
    !!owner &&
    owner.user === session.user &&
    owner.authenticated === !!session.token &&
    String(owner.member) === String(session.claims.memberId) &&
    String(owner.role) === String(session.claims.roleId)
  );
}

function staleResponse() {
  const error = new Error("登录身份已变化，已丢弃旧请求结果");
  error.code = "STALE_SESSION";
  return error;
}

http.interceptors.request.use((config) => {
  // 绑定发起请求时的登录实例；同一账号退出后重新登录也视为新实例。
  config._sessionIdentity = currentIdentity();
  if (session.token) config.headers.Authorization = `Bearer ${session.token}`;
  return config;
});

http.interceptors.response.use(
  (response) => {
    // 旧账号的轮换令牌、资料和权限都不能写入新账号的页面状态。
    if (!isCurrent(response.config)) return Promise.reject(staleResponse());
    // 第一层拦截器可能滚动签发 JWT，前端必须立即替换旧令牌。
    const refreshed = response.headers["x-refreshed-token"];
    if (refreshed) session.saveToken(refreshed);
    const body = response.data;
    if (response.config?.responseType === "blob") return body;
    if (body && typeof body.code === "number" && body.code >= 400) {
      const message = body.message || body.msg || "请求失败";
      if (!response.config?.silent) ElMessage.error(message);
      return Promise.reject(new Error(message));
    }
    return body?.data;
  },
  (error) => {
    // 旧请求迟到的401不能注销新账号，也不能触发新账号的规则弹窗。
    if (!isCurrent(error.config)) return Promise.reject(staleResponse());
    if (error.response?.status === 401) session.clear();
    if (error.response?.headers?.["x-error-code"] === "RULE_CONSENT_REQUIRED")
      window.dispatchEvent(new Event("library-rules-updated"));
    const message =
      error.response?.data?.message ||
      error.response?.data?.msg ||
      error.message ||
      "网络异常";
    if (!error.config?.silent) ElMessage.error(message);
    return Promise.reject(error);
  },
);

export default http;
