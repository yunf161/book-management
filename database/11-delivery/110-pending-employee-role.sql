-- 与既有停职/离职审批逻辑保持一致：未在职员工不持有馆员或管理员岗位角色。
-- 成员类型、员工档案与借阅数据保持不变；复职仍由管理员审批并恢复岗位角色。
UPDATE member m
JOIN employee_profile ep ON ep.member_id = m.id
JOIN `role` current_role ON current_role.id = m.role_id
JOIN `role` fallback_role ON fallback_role.code = 'READER' AND fallback_role.status = 'ACTIVE'
SET m.role_id = fallback_role.id,
    m.version = m.version + 1,
    m.updated_at = CURRENT_TIMESTAMP(3)
WHERE m.member_type = 'EMPLOYEE'
  AND ep.employment_status IN ('SUSPENDED', 'LEFT')
  AND current_role.code IN ('LIBRARIAN', 'ADMIN');
