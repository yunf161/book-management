/** 前端只做提前提示；格式、正文、数量和资源限制最终由服务器解析校验。 */
export function validateKnowledgeFile(file) {
  const extension = file?.name?.split(".").pop()?.toLowerCase();
  if (!["md", "txt", "csv", "json", "pdf"].includes(extension))
    throw new Error("仅支持MD、TXT、CSV、JSON和文本型PDF");
  const limit = extension === "pdf" ? 4 * 1024 * 1024 : 600000;
  if (!file.size || file.size > limit)
    throw new Error("文件不能为空；PDF最大4MiB，其他格式最大600000字节");
  return extension;
}
