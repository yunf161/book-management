/** 发布新版后，旧页面可能仍引用已被替换的路由分包。仅对此类加载错误恢复一次。 */
export function recoverNavigation(error, target, browser = window) {
  if (
    !/Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module|Unable to preload CSS/i.test(
      String(error?.message || error),
    )
  ) {
    return false;
  }
  try {
    const url = new URL(target, browser.location.origin);
    if (url.origin !== browser.location.origin) return false;
    const key = "library-navigation-recovery";
    const previous = JSON.parse(browser.sessionStorage.getItem(key) || "null");
    const now = Date.now();
    // 同一目标一分钟内只刷新一次，真正缺失的资源不能导致无限刷新。
    if (previous?.path === url.pathname && now - previous.time < 60000)
      return false;
    browser.sessionStorage.setItem(
      key,
      JSON.stringify({ path: url.pathname, time: now }),
    );
    browser.location.assign(url.pathname + url.search + url.hash);
    return true;
  } catch {
    // 隐私模式或存储不可用时保留页面，并由调用方给出明确提示。
    return false;
  }
}
