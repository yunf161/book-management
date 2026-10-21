/** 后端相对图片地址经过与业务请求相同的代理；不把对象key当成图片URL。 */
export function coverImageUrl(url) {
  if (!url) return "";
  if (url.startsWith("/books/")) return "/api" + url;
  return /^https?:\/\//.test(url) ? url : "";
}
