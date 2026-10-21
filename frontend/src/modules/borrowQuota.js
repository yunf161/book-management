/** 仅用于展示；真正准入由后端在提交、审批及SQL增加数量时重新校验。 */
export function effectiveBorrowLimit(member, now = Date.now()) {
  if (!member) return null;
  const expires = Date.parse(member.temporaryQuotaExpiresAt);
  return member.temporaryBorrowLimit != null &&
    Number.isFinite(expires) &&
    now < expires
    ? member.temporaryBorrowLimit
    : member.permanentBorrowLimit;
}
