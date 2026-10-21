/** 展示文本只改变界面文案，提交时仍使用后端 DTO 的枚举值。 */
const labels = {
  SUCCESS: "成功",
  FAILURE: "失败",
  ANONYMOUS: "未认证",
  MEMBER: "成员",
  SYSTEM: "系统任务",
  DEPARTMENT: "部门",
  PERMISSION: "权限定义",
  BOOK_CATEGORY: "图书分类",
  BOOK: "图书书目",
  BOOK_COPY: "图书副本",
  BORROW_APPLICATION: "借阅申请",
  BORROW_RECORD: "借阅记录",
  MEMBER_CHANGE: "资料变更申请",
  POLICY: "政策版本",
  READING_PLAN_MEMBER: "成员阅读计划",
  HTTP_OPERATION: "请求操作",
  AI_CONTROL: "AI能力控制",
  AI_HANDOFF: "AI人工交接",
  QUEUED: "排队中",
  RUNNING: "执行中",
  SUCCEEDED: "已完成",
  FAILED: "失败",
  PARTIAL: "部分成功",
  WAITING: "等待接管",
  ACCEPTED: "已接管",
  CONSULTATION: "咨询",
  BUSINESS: "业务提案",
  IMPORT: "导入",
  EXPORT: "导出",
  ACTIVE: "启用",
  DISABLED: "停用",
  PAUSED: "暂停",
  READER: "读者",
  EMPLOYEE: "员工",
  AI_LIBRARIAN: "AI 馆员",
  NOT_APPLIED: "未申请",
  PENDING: "待审批",
  APPROVED: "已通过",
  REJECTED: "已拒绝",
  SUSPENDED: "暂停",
  NOT_APPLICABLE: "不适用",
  LEFT: "已离职",
  BORROW: "借阅",
  RENEW: "续借",
  CANCELLED: "已取消",
  EXPIRED: "已过期",
  BORROWING: "借阅中",
  OVERDUE: "已逾期",
  RETURNED: "已归还",
  NORMAL: "正常归还",
  DAMAGED: "损坏归还",
  LOST: "遗失",
  AVAILABLE: "可借",
  RESERVED: "已预留",
  BORROWED: "已借出",
  MAINTENANCE: "维护中",
  DISCARDED: "已下架",
  PLANNED: "待阅读",
  READING: "阅读中",
  COMPLETED: "已完成",
  ABANDONED: "已放弃",
  USER: "本人添加",
  LIBRARIAN: "馆员添加",
  AI: "AI 添加",
  READER_QUALIFICATION: "申请借阅资格",
  READER_QUALIFICATION_RESTORE: "恢复借阅资格",
  BORROW_QUOTA_CHANGE: "调整借阅额度",
  ACCOUNT_REACTIVATION: "重新激活账号",
  EMPLOYEE_PROFILE_CHANGE: "员工资料变更",
  EMPLOYEE_TRANSFER: "员工调岗",
  EMPLOYEE_SUSPENSION: "暂停任职",
  EMPLOYEE_RESIGNATION: "申请离职",
  EMPLOYEE_REINSTATEMENT: "恢复任职",
  EMPLOYEE_ROLE_CHANGE: "员工角色变更",
  TRANSFER: "调岗",
  SUSPEND: "暂停任职",
  RESIGN: "离职",
  REINSTATE: "恢复任职",
  REACTIVATE: "重新激活",
  OPEN: "开放",
  CLOSED: "已关闭",
};

export function displayValue(value) {
  if (value === null || value === undefined || value === "") return "—";
  if (typeof value === "boolean") return value ? "是" : "否";
  if (Array.isArray(value)) return value.map(displayValue).join("、");
  if (typeof value === "object") return JSON.stringify(value);
  return labels[value] || String(value);
}

export function optionLabel(option) {
  return option.label ?? displayValue(option.value ?? option);
}

// 仅状态字段使用颜色，避免书名、姓名恰好等于枚举文本时被误标记。
const statusFields = new Set([
  "status",
  "accountStatus",
  "qualificationStatus",
  "employmentStatus",
  "returnOutcome",
]);
const statusTones = {
  ACTIVE: "success",
  APPROVED: "success",
  AVAILABLE: "success",
  RETURNED: "success",
  COMPLETED: "success",
  SUCCEEDED: "success",
  NORMAL: "success",
  PENDING: "warning",
  QUEUED: "warning",
  WAITING: "warning",
  MAINTENANCE: "warning",
  RESERVED: "warning",
  PAUSED: "warning",
  OVERDUE: "danger",
  REJECTED: "danger",
  FAILED: "danger",
  LOST: "danger",
  DAMAGED: "danger",
  SUSPENDED: "danger",
  BORROWING: "primary",
  BORROWED: "primary",
  READING: "primary",
  RUNNING: "primary",
  ACCEPTED: "primary",
  DISABLED: "info",
  DISCARDED: "info",
  CANCELLED: "info",
  EXPIRED: "info",
  ABANDONED: "info",
  NOT_APPLIED: "info",
  NOT_APPLICABLE: "info",
  LEFT: "info",
  PLANNED: "info",
  CLOSED: "info",
};

export function statusTone(key, value) {
  return statusFields.has(key) && Object.hasOwn(statusTones, value)
    ? statusTones[value]
    : null;
}

// 保留接口异常值的原始显示，不将缺失值或越界值伪装成 0% / 100%。
export function progressPercentage(key, value) {
  return key === "progressPercent" &&
    typeof value === "number" &&
    Number.isFinite(value) &&
    value >= 0 &&
    value <= 100
    ? value
    : null;
}
