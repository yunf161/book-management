<script setup>
import AppShell from "../components/AppShell.vue";
import PageHeader from "../components/PageHeader.vue";
import ResourceDesk from "../components/ResourceDesk.vue";
import { api } from "../api/library";

// 与后端聚合DTO一一对应，不提供修改、删除或原始敏感数据下载入口。
const queryFields = [
  { key: "id", label: "记录编号", type: "number" },
  { key: "actorMemberId", label: "操作者编号", type: "number" },
  { key: "actorRoleId", label: "操作者角色编号", type: "number" },
  {
    key: "actorKind",
    label: "执行者类型",
    options: ["MEMBER", "SYSTEM", "ANONYMOUS"],
  },
  {
    key: "targetType",
    label: "对象类型",
    options: [
      "MEMBER",
      "DEPARTMENT",
      "PERMISSION",
      "BOOK_CATEGORY",
      "BOOK",
      "BOOK_COPY",
      "BORROW_APPLICATION",
      "BORROW_RECORD",
      "MEMBER_CHANGE",
      "POLICY",
      "READING_PLAN_MEMBER",
      "HTTP_OPERATION",
      "AI_CONTROL",
      "AI_HANDOFF",
    ],
  },
  { key: "targetId", label: "对象编号", type: "number" },
  { key: "actionCode", label: "操作名称（精确）" },
  { key: "resultStatus", label: "结果", options: ["SUCCESS", "FAILURE"] },
  { key: "createdFrom", label: "开始时间", type: "datetime" },
  { key: "createdTo", label: "结束时间", type: "datetime" },
];
const columns = [
  { key: "id", label: "记录编号" },
  { key: "createdAt", label: "操作时间" },
  { key: "actorKind", label: "执行者类型" },
  { key: "actorMemberId", label: "操作者" },
  { key: "actorMemberType", label: "成员类型快照" },
  { key: "actorRoleId", label: "角色快照" },
  { key: "targetType", label: "对象类型" },
  { key: "targetId", label: "对象编号" },
  { key: "actionCode", label: "操作" },
  { key: "resultStatus", label: "结果" },
  { key: "errorCode", label: "错误类型" },
  { key: "reason", label: "操作来源" },
  { key: "beforeData", label: "修改前（脱敏）", overflow: true },
  { key: "afterData", label: "修改后（脱敏）", overflow: true },
];
</script>

<template>
  <AppShell>
    <PageHeader
      eyebrow="安全追溯"
      title="操作审计"
      description="查询人员、馆藏、借阅、计划、政策及权限操作。快照已脱敏，历史记录只读。"
    />
    <ResourceDesk
      title="操作记录"
      :query-fields="queryFields"
      :columns="columns"
      :list="api.operationLogs"
      :json-fields="['beforeData', 'afterData']"
    />
  </AppShell>
</template>
