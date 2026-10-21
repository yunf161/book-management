/** 乐观锁属于请求协议；用户填写的是业务内容，不是数据库版本。 */
const lockKeys = new Set([
  "version",
  "versionNumber",
  "expectedVersion",
  "expectedMemberVersion",
  "expectedProfileVersion",
  "expectedPlanVersion",
  "employeeProfileVersion",
]);

export const isLockVersion = (key) => lockKeys.has(key);
export const visibleFields = (fields = []) =>
  fields.filter((field) => !isLockVersion(field.key));

/** 只从查询记录取得版本；保留0，不用随机数、时间戳或默认0伪造版本。 */
export function recordVersion(row) {
  if (!Number.isSafeInteger(row?.version) || row.version < 0)
    throw new Error("记录信息不完整，请刷新后重试");
  return row.version;
}

/** 详情中的嵌套业务快照也隐藏技术版本，政策/须知/内容版本不受影响。 */
export function withoutLockVersions(value) {
  if (Array.isArray(value)) return value.map(withoutLockVersions);
  if (value && typeof value === "object")
    return Object.fromEntries(
      Object.entries(value)
        .filter(([key]) => !isLockVersion(key))
        .map(([key, nested]) => [key, withoutLockVersions(nested)]),
    );
  return value;
}
