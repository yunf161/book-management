import { reactive } from "vue";

function parsePayload(token) {
  try {
    const value = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    return JSON.parse(decodeURIComponent(escape(atob(value))));
  } catch {
    return {};
  }
}

// 损坏或旧版本的浏览器存储不能让整个登录页面初始化失败。
function restore() {
  try {
    const token = localStorage.getItem("ai-library-token") || "";
    const user = JSON.parse(localStorage.getItem("ai-library-user") || "null");
    if (token && user && typeof user === "object" && !Array.isArray(user))
      return { token, user };
  } catch {
    // 不输出存储内容，避免在日志中泄漏令牌。
  }
  persist("", null);
  return { token: "", user: null };
}

function persist(token, user) {
  try {
    if (token) {
      localStorage.setItem("ai-library-token", token);
      localStorage.setItem("ai-library-user", JSON.stringify(user));
    } else {
      localStorage.removeItem("ai-library-token");
      localStorage.removeItem("ai-library-user");
    }
  } catch {
    // 浏览器禁用存储时仍允许本次内存会话使用；刷新页面后需要重新登录。
  }
}

export const session = reactive({
  ...restore(),
  get claims() {
    return this.token ? parsePayload(this.token) : {};
  },
  get isAdmin() {
    return ["LIBRARIAN", "ADMIN"].includes(this.claims.roleCode);
  },
  /** 系统管理员使用管理侧栏，普通馆员继续使用读者端顶部导航。 */
  get isSystemAdmin() {
    return this.claims.roleCode === "ADMIN";
  },
  get isLibrarian() {
    return this.claims.roleCode === "LIBRARIAN";
  },
  save(loginInfo) {
    this.token = loginInfo.token;
    this.user = loginInfo;
    persist(this.token, this.user);
  },
  saveToken(token) {
    this.token = token;
    persist(token, this.user);
  },
  clear() {
    this.token = "";
    this.user = null;
    persist("", null);
  },
});
