<script setup>
import { computed } from "vue";
import { useRoute } from "vue-router";
import AppShell from "../components/AppShell.vue";
import PageHeader from "../components/PageHeader.vue";
import ResourceDesk from "../components/ResourceDesk.vue";
import { dtoFields as fields } from "../modules/dtoFields";
import { api } from "../api/library";
import { session } from "../stores/session";
import { access, canEnter } from "../stores/permissions";
import { maintenanceFields } from "../modules/memberMaintenance";
const route = useRoute();
// 每个页面只使用对应后端DTO，部门编码及权限编码创建后不再提交修改。
const type = computed(
  () => ({ readers: "READER", staff: "EMPLOYEE" })[route.meta.kind],
);
const common = [
  "memberType",
  "roleId",
  "name",
  "email",
  "phone",
  "passwordHash",
  "accountStatus",
  "qualificationStatus",
  "emailVerifiedAt",
  "acceptedRuleVersion",
  "borrowedBookCount",
  "permanentBorrowLimit",
];
const special = {
  READER: ["interests", "personalizationEnabled"],
  EMPLOYEE: [
    "employeeNo",
    "departmentId",
    "positionName",
    "employmentStatus",
    "hiredOn",
    "leftOn",
  ],
};
const permissionModule = computed(() =>
  type.value
    ? "member"
    : route.meta.kind === "permissions"
      ? "permission"
      : "department",
);
const humanPage = computed(() => ["READER", "EMPLOYEE"].includes(type.value));
const permitted = (action) => {
  if (humanPage.value) {
    if (action === "update")
      return maintenanceFields(access, type.value).length > 0;
    if (action === "delete")
      return (
        access.has("member:delete", "ALL") &&
        maintenanceFields(access, type.value).includes("accountStatus")
      );
    return false; // 新账号仍从既有注册流程创建，不在本次字段维护中扩展登记。
  }
  return access.has(
    `${permissionModule.value}:${action}`,
    permissionModule.value === "department" ? undefined : "ALL",
  );
};
const canMaintainRow = (row) =>
  !humanPage.value ||
  (maintenanceFields(access, type.value, row).length > 0 &&
    !(session.isSystemAdmin && row.roleId === session.claims.roleId));
const canDisableRow = (row) =>
  !humanPage.value || (canMaintainRow(row) && row.accountStatus === "ACTIVE");
const config = computed(() => {
  if (type.value) {
    const allowed = [...common, ...special[type.value]];
    return {
      title: route.meta.title,
      query: fields.MemberQueryRequest.filter(
        (f) => !["passwordHash", "memberType"].includes(f.key),
      ),
      create: fields.MemberWriteRequest.filter(
        (f) => allowed.includes(f.key) && f.key !== "borrowedBookCount",
      ),
      edit: humanPage.value
        ? (row) => {
            const allowedFields = maintenanceFields(access, type.value, row);
            return fields.MemberWriteRequest.filter((f) =>
              [...allowedFields, "expectedVersion"].includes(f.key),
            ).map((f) => ({
              ...f,
              disabled: f.key === "expectedVersion",
              ...(f.key === "qualificationStatus"
                ? { options: ["APPROVED", "SUSPENDED"] }
                : {}),
            }));
          }
        : fields.MemberWriteRequest.filter(
            (f) =>
              [...allowed, "expectedVersion"].includes(f.key) &&
              f.key !== "borrowedBookCount",
          ),
      list: (q) => api.members({ ...q, memberType: type.value }),
      add: (b) => api.createMember({ ...b, memberType: type.value }),
      update: (r, b) =>
        api.updateMember(r.id, { ...b, memberType: r.memberType }),
      remove: (r) => api.disableMember(r.id, r.memberType),
      removeLabel: "停用",
      defaults: {
        memberType: type.value,
        accountStatus: "ACTIVE",
        qualificationStatus: "NOT_APPLIED",
        permanentBorrowLimit: 5,
      },
      columns: [
        { key: "id", label: "编号" },
        { key: "name", label: "姓名" },
        { key: "memberType", label: "类型" },
        { key: "accountStatus", label: "账号状态" },
        { key: "roleId", label: "角色" },
        { key: "permanentBorrowLimit", label: "长期额度" },
        { key: "temporaryBorrowLimit", label: "临时总额度" },
        { key: "temporaryQuotaExpiresAt", label: "临时到期时间" },
        { key: "effectiveBorrowLimit", label: "当前生效额度" },
        { key: "version", label: "版本" },
      ],
    };
  }
  if (route.meta.kind === "permissions")
    return {
      title: "权限管理",
      query: fields.PermissionFindParam,
      create: [
        { key: "code", label: "权限编码" },
        ...fields.PermissionUpdateRequest.filter(
          (f) => f.key !== "expectedVersion",
        ),
      ],
      edit: fields.PermissionUpdateRequest,
      list: api.permissions,
      add: api.createPermission,
      update: (r, b) => api.updatePermission(r.id, b),
      remove: (r) => api.deletePermission(r.id),
      columns: [
        { key: "id", label: "编号" },
        { key: "code", label: "权限编码" },
        { key: "name", label: "名称" },
        { key: "module", label: "模块" },
        { key: "version", label: "版本" },
      ],
    };
  return {
    title: "部门管理",
    defaults: { status: "ACTIVE", version: 0 },
    query: fields.DepartmentQueryRequest,
    create: [
      { key: "code", label: "部门编码" },
      { key: "name", label: "部门名称" },
      { key: "description", label: "说明", type: "textarea" },
      { key: "status", label: "状态", options: ["ACTIVE", "DISABLED"] },
      { key: "version", label: "初始版本", type: "number" },
    ],
    edit: fields.DepartmentUpdateRequest,
    list: api.departments,
    add: api.createDepartment,
    update: (r, b) => api.updateDepartment(r.id, b),
    remove: (r) => api.deleteDepartment(r.id),
    columns: [
      { key: "id", label: "编号" },
      { key: "code", label: "编码" },
      { key: "name", label: "部门名称" },
      { key: "status", label: "状态" },
      { key: "version", label: "版本" },
    ],
  };
});
</script>
<template>
  <AppShell
    ><PageHeader
      eyebrow="资料维护"
      :title="config.title"
      :description="
        type === 'READER' || type === 'EMPLOYEE'
          ? '普通资料只读；按专项权限维护额度、账号启停和已有资格的暂停/恢复。人事、部门及角色变更继续申请审批。'
          : '查询、维护记录；所有操作由后端校验权限与关联数据。'
      "
    />
    <p v-if="type === 'READER' || type === 'EMPLOYEE'" class="muted">
      初次借阅资格及员工人事变更请在审批中心处理；已授权的维护字段可直接操作。
      <router-link v-if="canEnter('/admin/approvals')" to="/admin/approvals"
        >进入审批中心</router-link
      >
    </p>
    <ResourceDesk
      :key="route.path"
      :title="config.title"
      :query-fields="config.query"
      :create-fields="config.create"
      :update-fields="config.edit"
      :columns="config.columns"
      :list="config.list"
      :create="permitted('create') ? config.add : undefined"
      :update="permitted('update') ? config.update : undefined"
      :allow-update="canMaintainRow"
      :allow-remove="canDisableRow"
      :remove="permitted('delete') ? config.remove : undefined"
      :remove-label="config.removeLabel || '删除'"
      :defaults="config.defaults"
    />
  </AppShell>
</template>
