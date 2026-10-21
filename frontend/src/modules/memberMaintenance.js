/** 与后端直接维护白名单对应；最终授权和状态转换仍由后端校验。 */
export function maintenanceFields(access, type, row) {
  if (
    !["READER", "EMPLOYEE"].includes(type) ||
    !access.has("member:query", "ALL")
  )
    return [];
  if (row && String(row.id) === String(access.memberId)) return [];
  if (
    type === "EMPLOYEE" &&
    !access.has("employee-profile-change:approve", "ALL")
  )
    return [];
  const fields = [];
  if (access.has("borrow-quota-change:approve", "ALL"))
    fields.push("permanentBorrowLimit");
  if (access.has("member-account-change:approve", "ALL"))
    fields.push("accountStatus");
  if (
    access.has("reader-qualification:approve", "ALL") &&
    (!row || ["APPROVED", "SUSPENDED"].includes(row.qualificationStatus))
  )
    fields.push("qualificationStatus");
  return fields;
}
