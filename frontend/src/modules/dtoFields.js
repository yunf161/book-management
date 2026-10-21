// 与后端DTO逐字段核对的表单和查询配置；排序内部字段不作为客户端输入。
export const dtoFields = {
  DepartmentQueryRequest: [
    {
      key: "id",
      label: "记录编号",
      type: "number",
    },
    {
      key: "name",
      label: "名称",
      type: "text",
    },
  ],
  DepartmentUpdateRequest: [
    {
      key: "name",
      label: "名称",
      type: "text",
    },
    {
      key: "description",
      label: "说明",
      type: "textarea",
    },
    {
      key: "status",
      label: "状态",
      type: "text",
      options: ["ACTIVE", "DISABLED"],
    },
    {
      key: "expectedVersion",
      label: "当前记录版本",
      type: "number",
    },
  ],
  MemberQueryRequest: [
    {
      key: "id",
      label: "记录编号",
      type: "number",
    },
    {
      key: "memberType",
      label: "成员类型",
      type: "text",
      options: ["READER", "EMPLOYEE"],
    },
    {
      key: "roleId",
      label: "角色编号",
      type: "number",
    },
    {
      key: "name",
      label: "名称",
      type: "text",
    },
    {
      key: "email",
      label: "邮箱",
      type: "text",
    },
    {
      key: "phone",
      label: "手机",
      type: "text",
    },
    {
      key: "passwordHash",
      label: "密码哈希",
      type: "text",
    },
    {
      key: "accountStatus",
      label: "账号状态",
      type: "text",
      options: ["ACTIVE", "DISABLED"],
    },
    {
      key: "qualificationStatus",
      label: "借阅资格",
      type: "text",
      options: [
        "NOT_APPLIED",
        "PENDING",
        "APPROVED",
        "REJECTED",
        "SUSPENDED",
        "NOT_APPLICABLE",
      ],
    },
    {
      key: "emailVerifiedAt",
      label: "邮箱验证时间",
      type: "datetime",
    },
    {
      key: "acceptedRuleVersion",
      label: "已接受规则版本",
      type: "text",
    },
    {
      key: "borrowedBookCount",
      label: "未归还数量",
      type: "number",
    },
    {
      key: "permanentBorrowLimit",
      label: "长期借阅额度",
      type: "number",
    },
    { key: "temporaryBorrowLimit", label: "临时借阅总额度", type: "number" },
    {
      key: "temporaryQuotaExpiresAt",
      label: "临时额度到期时间",
      type: "datetime",
    },
    {
      key: "version",
      label: "版本",
      type: "number",
    },
    {
      key: "createdAt",
      label: "创建时间",
      type: "datetime",
    },
    {
      key: "updatedAt",
      label: "更新时间",
      type: "datetime",
    },
    {
      key: "readerInterestId",
      label: "兴趣记录编号",
      type: "number",
    },
    {
      key: "interestName",
      label: "兴趣名称",
      type: "text",
    },
    {
      key: "interests",
      label: "阅读兴趣",
      type: "text",
    },
    {
      key: "readerSettingsId",
      label: "偏好记录编号",
      type: "number",
    },
    {
      key: "personalizationEnabled",
      label: "个性化推荐",
      type: "text",
      options: [
        {
          value: true,
          label: "是",
        },
        {
          value: false,
          label: "否",
        },
      ],
    },
    {
      key: "employeeProfileId",
      label: "员工档案编号",
      type: "number",
    },
    {
      key: "employeeNo",
      label: "工号",
      type: "text",
    },
    {
      key: "departmentId",
      label: "部门编号",
      type: "number",
    },
    {
      key: "positionName",
      label: "职务",
      type: "text",
    },
    {
      key: "employmentStatus",
      label: "任职状态",
      type: "text",
      options: ["ACTIVE", "SUSPENDED", "LEFT"],
    },
    {
      key: "hiredOn",
      label: "入职日期",
      type: "date",
    },
    {
      key: "leftOn",
      label: "离职日期",
      type: "date",
    },
  ],
  MemberWriteRequest: [
    {
      key: "memberType",
      label: "成员类型",
      type: "text",
      options: ["READER", "EMPLOYEE"],
    },
    {
      key: "roleId",
      label: "角色编号",
      type: "number",
    },
    {
      key: "name",
      label: "名称",
      type: "text",
    },
    {
      key: "email",
      label: "邮箱",
      type: "text",
    },
    {
      key: "phone",
      label: "手机",
      type: "text",
    },
    {
      key: "passwordHash",
      label: "密码哈希",
      type: "text",
    },
    {
      key: "accountStatus",
      label: "账号状态",
      type: "text",
      options: ["ACTIVE", "DISABLED"],
    },
    {
      key: "qualificationStatus",
      label: "借阅资格",
      type: "text",
      options: [
        "NOT_APPLIED",
        "PENDING",
        "APPROVED",
        "REJECTED",
        "SUSPENDED",
        "NOT_APPLICABLE",
      ],
    },
    {
      key: "emailVerifiedAt",
      label: "邮箱验证时间",
      type: "datetime",
    },
    {
      key: "acceptedRuleVersion",
      label: "已接受规则版本",
      type: "text",
    },
    {
      key: "borrowedBookCount",
      label: "未归还数量",
      type: "number",
    },
    {
      key: "permanentBorrowLimit",
      label: "长期借阅额度",
      type: "number",
    },
    {
      key: "interests",
      label: "阅读兴趣（逗号分隔）",
      type: "text",
      array: true,
    },
    {
      key: "personalizationEnabled",
      label: "个性化推荐",
      type: "text",
      options: [
        {
          value: true,
          label: "是",
        },
        {
          value: false,
          label: "否",
        },
      ],
    },
    {
      key: "employeeNo",
      label: "工号",
      type: "text",
    },
    {
      key: "departmentId",
      label: "部门编号",
      type: "number",
    },
    {
      key: "positionName",
      label: "职务",
      type: "text",
    },
    {
      key: "employmentStatus",
      label: "任职状态",
      type: "text",
      options: ["ACTIVE", "SUSPENDED", "LEFT"],
    },
    {
      key: "hiredOn",
      label: "入职日期",
      type: "date",
    },
    {
      key: "leftOn",
      label: "离职日期",
      type: "date",
    },
    {
      key: "expectedVersion",
      label: "当前记录版本",
      type: "number",
    },
  ],
  PermissionFindParam: [
    {
      key: "id",
      label: "记录编号",
      type: "number",
    },
    {
      key: "code",
      label: "编码",
      type: "text",
    },
    {
      key: "name",
      label: "名称",
      type: "text",
    },
    {
      key: "module",
      label: "模块",
      type: "text",
    },
  ],
  PermissionUpdateRequest: [
    {
      key: "name",
      label: "名称",
      type: "text",
    },
    {
      key: "module",
      label: "模块",
      type: "text",
    },
    {
      key: "description",
      label: "说明",
      type: "textarea",
    },
    {
      key: "expectedVersion",
      label: "当前记录版本",
      type: "number",
    },
  ],
  MemberChangeCreateRequest: [
    {
      key: "applicationType",
      label: "申请类型",
      type: "text",
      options: [
        "READER_QUALIFICATION",
        "READER_QUALIFICATION_RESTORE",
        "BORROW_QUOTA_CHANGE",
        "ACCOUNT_REACTIVATION",
        "EMPLOYEE_PROFILE_CHANGE",
        "EMPLOYEE_TRANSFER",
        "EMPLOYEE_SUSPENSION",
        "EMPLOYEE_RESIGNATION",
        "EMPLOYEE_REINSTATEMENT",
        "EMPLOYEE_ROLE_CHANGE",
      ],
    },
    {
      key: "reason",
      label: "申请原因",
      type: "textarea",
    },
    {
      key: "expectedMemberVersion",
      label: "当前成员版本",
      type: "number",
    },
    {
      key: "expectedProfileVersion",
      label: "当前员工档案版本",
      type: "number",
    },
    {
      key: "acceptedRuleVersion",
      label: "已接受规则版本",
      type: "text",
    },
    {
      key: "requestedMaxBorrowCount",
      label: "申请总额度（不是增加数量）",
      type: "number",
    },
    {
      key: "quotaDurationDays",
      label: "临时额度天数（留空为长期调整）",
      type: "number",
    },
    {
      key: "requestedDepartmentId",
      label: "申请部门编号",
      type: "number",
    },
    {
      key: "requestedPositionName",
      label: "申请职务",
      type: "text",
    },
    {
      key: "requestedEmploymentAction",
      label: "申请任职动作",
      type: "text",
      options: ["TRANSFER", "SUSPEND", "RESIGN", "REINSTATE"],
    },
    {
      key: "requestedRoleId",
      label: "申请角色编号",
      type: "number",
    },
    {
      key: "requestedAccountAction",
      label: "申请账号动作",
      type: "text",
      options: ["REACTIVATE"],
    },
  ],
  MemberChangeQueryRequest: [
    {
      key: "id",
      label: "记录编号",
      type: "number",
    },
    {
      key: "applicationNo",
      label: "申请单号",
      type: "text",
    },
    {
      key: "applicantMemberId",
      label: "申请人编号",
      type: "number",
    },
    {
      key: "targetMemberId",
      label: "目标成员编号",
      type: "number",
    },
    {
      key: "applicantMemberType",
      label: "申请人类型",
      type: "text",
      options: ["READER", "EMPLOYEE"],
    },
    {
      key: "applicationType",
      label: "申请类型",
      type: "text",
      options: [
        "READER_QUALIFICATION",
        "READER_QUALIFICATION_RESTORE",
        "BORROW_QUOTA_CHANGE",
        "ACCOUNT_REACTIVATION",
        "EMPLOYEE_PROFILE_CHANGE",
        "EMPLOYEE_TRANSFER",
        "EMPLOYEE_SUSPENSION",
        "EMPLOYEE_RESIGNATION",
        "EMPLOYEE_REINSTATEMENT",
        "EMPLOYEE_ROLE_CHANGE",
      ],
    },
    {
      key: "status",
      label: "状态",
      type: "text",
      options: ["PENDING", "APPROVED", "REJECTED", "CANCELLED", "EXPIRED"],
    },
    {
      key: "reviewerMemberId",
      label: "审批人编号",
      type: "number",
    },
    {
      key: "createdFrom",
      label: "创建时间下限",
      type: "datetime",
    },
    {
      key: "createdTo",
      label: "创建时间上限",
      type: "datetime",
    },
  ],
  ReadingPlanCreateRequest: [
    {
      key: "memberId",
      label: "成员编号",
      type: "number",
    },
    {
      key: "name",
      label: "名称",
      type: "text",
    },
    {
      key: "description",
      label: "说明",
      type: "textarea",
    },
    {
      key: "targetBooks",
      label: "目标完成书目数",
      type: "number",
    },
    {
      key: "startDate",
      label: "开始日期",
      type: "date",
    },
    {
      key: "endDate",
      label: "结束日期",
      type: "date",
    },
    {
      key: "status",
      label: "状态",
      type: "text",
      options: ["DRAFT", "ACTIVE", "PAUSED", "COMPLETED"],
    },
  ],
  ReadingPlanUpdateRequest: [
    {
      key: "name",
      label: "名称",
      type: "text",
    },
    {
      key: "description",
      label: "说明",
      type: "textarea",
    },
    {
      key: "targetBooks",
      label: "目标完成书目数",
      type: "number",
    },
    {
      key: "startDate",
      label: "开始日期",
      type: "date",
    },
    {
      key: "endDate",
      label: "结束日期",
      type: "date",
    },
    {
      key: "status",
      label: "状态",
      type: "text",
      options: ["DRAFT", "ACTIVE", "PAUSED", "COMPLETED"],
    },
    {
      key: "version",
      label: "版本",
      type: "number",
    },
  ],
  ReadingPlanQueryRequest: [
    {
      key: "pageNum",
      label: "pageNum",
      type: "number",
    },
    {
      key: "pageSize",
      label: "pageSize",
      type: "number",
    },
    {
      key: "memberId",
      label: "成员编号",
      type: "number",
    },
    {
      key: "memberKeyword",
      label: "成员关键词",
      type: "text",
    },
    {
      key: "planName",
      label: "计划名称",
      type: "text",
    },
    {
      key: "status",
      label: "状态",
      type: "text",
      options: ["DRAFT", "ACTIVE", "PAUSED", "COMPLETED"],
    },
    {
      key: "startDateFrom",
      label: "开始日期下限",
      type: "date",
    },
    {
      key: "startDateTo",
      label: "开始日期上限",
      type: "date",
    },
  ],
  ReadingItemCreateRequest: [
    {
      key: "bookId",
      label: "图书编号",
      type: "number",
    },
    {
      key: "expectedPlanVersion",
      label: "当前计划版本",
      type: "number",
    },
    {
      key: "sequenceNo",
      label: "阅读顺序",
      type: "number",
    },
    {
      key: "priority",
      label: "优先级",
      type: "number",
    },
    {
      key: "status",
      label: "状态",
      type: "text",
      options: ["PLANNED", "READING", "COMPLETED", "ABANDONED"],
    },
    {
      key: "source",
      label: "来源",
      type: "text",
      options: ["USER", "LIBRARIAN", "AI"],
    },
    {
      key: "plannedStartDate",
      label: "计划开始日期",
      type: "date",
    },
    {
      key: "plannedFinishDate",
      label: "计划完成日期",
      type: "date",
    },
    {
      key: "note",
      label: "备注",
      type: "textarea",
    },
  ],
  ReadingItemUpdateRequest: [
    {
      key: "sequenceNo",
      label: "阅读顺序",
      type: "number",
    },
    {
      key: "priority",
      label: "优先级",
      type: "number",
    },
    {
      key: "progressPercent",
      label: "阅读进度",
      type: "number",
    },
    {
      key: "status",
      label: "状态",
      type: "text",
      options: ["PLANNED", "READING", "COMPLETED", "ABANDONED"],
    },
    {
      key: "plannedStartDate",
      label: "计划开始日期",
      type: "date",
    },
    {
      key: "plannedFinishDate",
      label: "计划完成日期",
      type: "date",
    },
    {
      key: "note",
      label: "备注",
      type: "textarea",
    },
    {
      key: "version",
      label: "版本",
      type: "number",
    },
    {
      key: "expectedPlanVersion",
      label: "当前计划版本",
      type: "number",
    },
  ],
  ReadingItemQueryRequest: [
    {
      key: "pageNum",
      label: "pageNum",
      type: "number",
    },
    {
      key: "pageSize",
      label: "pageSize",
      type: "number",
    },
    {
      key: "memberId",
      label: "成员编号",
      type: "number",
    },
    {
      key: "bookId",
      label: "图书编号",
      type: "number",
    },
    {
      key: "memberKeyword",
      label: "成员关键词",
      type: "text",
    },
    {
      key: "bookKeyword",
      label: "图书关键词",
      type: "text",
    },
    {
      key: "status",
      label: "状态",
      type: "text",
      options: ["PLANNED", "READING", "COMPLETED", "ABANDONED"],
    },
    {
      key: "source",
      label: "来源",
      type: "text",
      options: ["USER", "LIBRARIAN", "AI"],
    },
    {
      key: "orderBy",
      label: "排序字段",
      type: "text",
    },
    {
      key: "orderDirection",
      label: "排序方向",
      type: "text",
    },
  ],
  BorrowApplicationQueryRequest: [
    {
      key: "id",
      label: "记录编号",
      type: "number",
    },
    {
      key: "applicationType",
      label: "申请类型",
      type: "text",
      options: ["BORROW", "RENEW"],
    },
    {
      key: "borrowRecordId",
      label: "借阅记录编号",
      type: "number",
    },
    {
      key: "status",
      label: "状态",
      type: "text",
      options: ["PENDING", "APPROVED", "REJECTED", "CANCELLED", "EXPIRED"],
    },
    {
      key: "memberId",
      label: "成员编号",
      type: "number",
    },
    {
      key: "memberName",
      label: "成员姓名",
      type: "text",
    },
    {
      key: "bookId",
      label: "图书编号",
      type: "number",
    },
    {
      key: "bookTitle",
      label: "书名",
      type: "text",
    },
    {
      key: "isbn",
      label: "ISBN",
      type: "text",
    },
    {
      key: "submittedFrom",
      label: "提交时间下限",
      type: "datetime",
    },
    {
      key: "submittedTo",
      label: "提交时间上限",
      type: "datetime",
    },
    {
      key: "processedBy",
      label: "处理人编号",
      type: "number",
    },
  ],
  BorrowRecordQueryRequest: [
    {
      key: "id",
      label: "记录编号",
      type: "number",
    },
    {
      key: "memberId",
      label: "成员编号",
      type: "number",
    },
    {
      key: "memberName",
      label: "成员姓名",
      type: "text",
    },
    {
      key: "bookId",
      label: "图书编号",
      type: "number",
    },
    {
      key: "bookTitle",
      label: "书名",
      type: "text",
    },
    {
      key: "isbn",
      label: "ISBN",
      type: "text",
    },
    {
      key: "copyBarcode",
      label: "副本条码",
      type: "text",
    },
    {
      key: "status",
      label: "状态",
      type: "text",
      options: ["BORROWING", "OVERDUE", "RETURNED", "LOST"],
    },
    {
      key: "returnOutcome",
      label: "归还/结案结果",
      options: ["NORMAL", "DAMAGED", "LOST"],
    },
    {
      key: "currentOnly",
      label: "当前未归还",
      type: "text",
      options: [
        {
          value: true,
          label: "是",
        },
        {
          value: false,
          label: "否",
        },
      ],
    },
    {
      key: "overdueOnly",
      label: "当前逾期",
      type: "text",
      options: [
        {
          value: true,
          label: "是",
        },
        {
          value: false,
          label: "否",
        },
      ],
    },
    {
      key: "borrowedFrom",
      label: "借出时间下限",
      type: "datetime",
    },
    {
      key: "borrowedTo",
      label: "借出时间上限",
      type: "datetime",
    },
    {
      key: "dueFrom",
      label: "应还时间下限",
      type: "datetime",
    },
    {
      key: "dueTo",
      label: "应还时间上限",
      type: "datetime",
    },
  ],
};
