# HTTP 接口参考

基线：2026-10-21。此清单来自实际Spring注册路由，不来自早期原型。

共 **127 个 HTTP 方法＋路径注册项**、**62 个请求模型**；`/members` 等别名单独计数，不等于同等数量的独立业务功能。

可导入 Apifox/Postman 的结构文件：[openapi.json](openapi.json)。常见协议和业务约束见 [API约定](api-conventions.md)。

请求模型内属性不统一标为必填：不少规则在Service中按申请类型、成员身份、记录状态校验。开放Map模型和非泛型Result也未伪造完整契约。

## AiRuntimeController

### `POST /admin/ai-actions/reconcile`

处理方法：[AiRuntimeController.reconcile](../../src/main/java/com/yunfeil161/ailibrary/ai/runtime/AiRuntimeController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

无显式业务参数。

### `GET /admin/ai-controls`

处理方法：[AiRuntimeController.controls](../../src/main/java/com/yunfeil161/ailibrary/ai/runtime/AiRuntimeController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

无显式业务参数。

### `PUT /admin/ai-controls/{code}`

处理方法：[AiRuntimeController.update](../../src/main/java/com/yunfeil161/ailibrary/ai/runtime/AiRuntimeController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| code | path | `java.lang.String` |
| request | body | [ai_runtime_AiRuntimeController_ControlRequest](#ai_runtime_airuntimecontroller_controlrequest) |

## BookCategoryController

### `POST /admin/book-categories`

处理方法：[BookCategoryController.create](../../src/main/java/com/yunfeil161/ailibrary/book/category/controller/BookCategoryController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| request | body | [book_category_dto_BookCategoryCreateRequest](#book_category_dto_bookcategorycreaterequest) |

### `DELETE /admin/book-categories/{id}`

处理方法：[BookCategoryController.delete](../../src/main/java/com/yunfeil161/ailibrary/book/category/controller/BookCategoryController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.Long` |

### `PUT /admin/book-categories/{id}`

处理方法：[BookCategoryController.update](../../src/main/java/com/yunfeil161/ailibrary/book/category/controller/BookCategoryController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.Long` |
| request | body | [book_category_dto_BookCategoryUpdateRequest](#book_category_dto_bookcategoryupdaterequest) |

## BookCopyController

### `GET /admin/book-copies`

处理方法：[BookCopyController.query](../../src/main/java/com/yunfeil161/ailibrary/book/copy/controller/BookCopyController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| query | model | [book_copy_dto_BookCopyQueryRequest](#book_copy_dto_bookcopyqueryrequest) |

## BorrowRecordController

### `POST /admin/book-copies/{barcode}/return`

处理方法：[BorrowRecordController.returnBarcode](../../src/main/java/com/yunfeil161/ailibrary/book/borrow/controller/BorrowRecordController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| barcode | path | `java.lang.String` |
| r | body | [book_borrow_dto_BorrowReturnRequest](#book_borrow_dto_borrowreturnrequest) |

## BookCopyController

### `DELETE /admin/book-copies/{id}`

处理方法：[BookCopyController.delete](../../src/main/java/com/yunfeil161/ailibrary/book/copy/controller/BookCopyController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.Long` |

### `PUT /admin/book-copies/{id}`

处理方法：[BookCopyController.update](../../src/main/java/com/yunfeil161/ailibrary/book/copy/controller/BookCopyController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.Long` |
| request | body | [book_copy_dto_BookCopyUpdateRequest](#book_copy_dto_bookcopyupdaterequest) |

## BookController

### `POST /admin/books`

处理方法：[BookController.create](../../src/main/java/com/yunfeil161/ailibrary/book/catalog/controller/BookController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| request | body | [book_catalog_dto_BookCreateRequest](#book_catalog_dto_bookcreaterequest) |

## BookCopyController

### `POST /admin/books/{bookId}/copies`

处理方法：[BookCopyController.create](../../src/main/java/com/yunfeil161/ailibrary/book/copy/controller/BookCopyController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| bookId | path | `java.lang.Long` |
| request | body | [book_copy_dto_BookCopyCreateRequest](#book_copy_dto_bookcopycreaterequest) |

## BookCoverController

### `DELETE /admin/books/{bookId}/cover`

处理方法：[BookCoverController.delete](../../src/main/java/com/yunfeil161/ailibrary/book/cover/controller/BookCoverController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| bookId | path | `java.lang.Long` |
| expectedVersion | query | `java.lang.Long` |

### `POST /admin/books/{bookId}/cover`

处理方法：[BookCoverController.create](../../src/main/java/com/yunfeil161/ailibrary/book/cover/controller/BookCoverController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| bookId | path | `java.lang.Long` |
| expectedVersion | form-data | `java.lang.Long` |
| file | form-data | `org.springframework.web.multipart.MultipartFile` |

### `PUT /admin/books/{bookId}/cover`

处理方法：[BookCoverController.replace](../../src/main/java/com/yunfeil161/ailibrary/book/cover/controller/BookCoverController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| bookId | path | `java.lang.Long` |
| expectedVersion | form-data | `java.lang.Long` |
| file | form-data | `org.springframework.web.multipart.MultipartFile` |

## RecommendationController

### `PUT /admin/books/{bookId}/tags`

处理方法：[RecommendationController.bookTags](../../src/main/java/com/yunfeil161/ailibrary/recommendation/controller/RecommendationController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| bookId | path | `java.lang.Long` |
| request | body | [recommendation_dto_BookTagsRequest](#recommendation_dto_booktagsrequest) |

## BookController

### `DELETE /admin/books/{id}`

处理方法：[BookController.delete](../../src/main/java/com/yunfeil161/ailibrary/book/catalog/controller/BookController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.Long` |

### `PUT /admin/books/{id}`

处理方法：[BookController.update](../../src/main/java/com/yunfeil161/ailibrary/book/catalog/controller/BookController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.Long` |
| request | body | [book_catalog_dto_BookUpdateRequest](#book_catalog_dto_bookupdaterequest) |

## BorrowApplicationController

### `GET /admin/borrow-applications`

处理方法：[BorrowApplicationController.admin](../../src/main/java/com/yunfeil161/ailibrary/book/borrow/controller/BorrowApplicationController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| q | model | [book_borrow_dto_BorrowApplicationQueryRequest](#book_borrow_dto_borrowapplicationqueryrequest) |

### `POST /admin/borrow-applications/{id}/approve`

处理方法：[BorrowApplicationController.approve](../../src/main/java/com/yunfeil161/ailibrary/book/borrow/controller/BorrowApplicationController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.Long` |
| r | body | [book_borrow_dto_BorrowApplicationApproveRequest](#book_borrow_dto_borrowapplicationapproverequest) |

### `POST /admin/borrow-applications/{id}/reject`

处理方法：[BorrowApplicationController.reject](../../src/main/java/com/yunfeil161/ailibrary/book/borrow/controller/BorrowApplicationController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.Long` |
| r | body | [book_borrow_dto_BorrowApplicationRejectRequest](#book_borrow_dto_borrowapplicationrejectrequest) |

## BorrowRecordController

### `GET /admin/borrow-records`

处理方法：[BorrowRecordController.admin](../../src/main/java/com/yunfeil161/ailibrary/book/borrow/controller/BorrowRecordController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| q | model | [book_borrow_dto_BorrowRecordQueryRequest](#book_borrow_dto_borrowrecordqueryrequest) |

### `POST /admin/borrow-records/{id}/return`

处理方法：[BorrowRecordController.returnRecord](../../src/main/java/com/yunfeil161/ailibrary/book/borrow/controller/BorrowRecordController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.Long` |
| r | body | [book_borrow_dto_BorrowReturnRequest](#book_borrow_dto_borrowreturnrequest) |

## DeptController

### `GET /admin/departments`

处理方法：[DeptController.getDepartments](../../src/main/java/com/yunfeil161/ailibrary/identity/controller/DeptController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| query | model | [identity_dto_DepartmentQueryRequest](#identity_dto_departmentqueryrequest) |

### `POST /admin/departments`

处理方法：[DeptController.addDepartment](../../src/main/java/com/yunfeil161/ailibrary/identity/controller/DeptController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| dept | body | [identity_entity_Dept](#identity_entity_dept) |

### `DELETE /admin/departments/{id}`

处理方法：[DeptController.deleteDepartment](../../src/main/java/com/yunfeil161/ailibrary/identity/controller/DeptController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.Long` |

### `PUT /admin/departments/{id}`

处理方法：[DeptController.updateDepartment](../../src/main/java/com/yunfeil161/ailibrary/identity/controller/DeptController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.Long` |
| request | body | [identity_dto_DepartmentUpdateRequest](#identity_dto_departmentupdaterequest) |

## KnowledgeController

### `GET /admin/knowledge/documents`

处理方法：[KnowledgeController.documents](../../src/main/java/com/yunfeil161/ailibrary/ai/knowledge/KnowledgeController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| query | query | `java.util.Map<java.lang.String, java.lang.String>` |

### `POST /admin/knowledge/documents`

处理方法：[KnowledgeController.create](../../src/main/java/com/yunfeil161/ailibrary/ai/knowledge/KnowledgeController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| body | body | `java.util.Map<java.lang.String, java.lang.Object>` |

### `GET /admin/knowledge/documents/{id}`

处理方法：[KnowledgeController.document](../../src/main/java/com/yunfeil161/ailibrary/ai/knowledge/KnowledgeController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.String` |

### `PUT /admin/knowledge/documents/{id}`

处理方法：[KnowledgeController.update](../../src/main/java/com/yunfeil161/ailibrary/ai/knowledge/KnowledgeController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.String` |
| body | body | `java.util.Map<java.lang.String, java.lang.Object>` |

### `POST /admin/knowledge/import-preview`

处理方法：[KnowledgeController.preview](../../src/main/java/com/yunfeil161/ailibrary/ai/knowledge/KnowledgeController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| file | form-data | `org.springframework.web.multipart.MultipartFile` |

### `GET /admin/knowledge/jobs/{id}`

处理方法：[KnowledgeController.job](../../src/main/java/com/yunfeil161/ailibrary/ai/knowledge/KnowledgeController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.String` |

### `POST /admin/knowledge/rebuild`

处理方法：[KnowledgeController.rebuild](../../src/main/java/com/yunfeil161/ailibrary/ai/knowledge/KnowledgeController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| body | body | `java.util.Map<java.lang.String, java.lang.Object>` |

### `POST /admin/knowledge/search`

处理方法：[KnowledgeController.search](../../src/main/java/com/yunfeil161/ailibrary/ai/knowledge/KnowledgeController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| body | body | `java.util.Map<java.lang.String, java.lang.Object>` |

### `GET /admin/knowledge/status`

处理方法：[KnowledgeController.status](../../src/main/java/com/yunfeil161/ailibrary/ai/knowledge/KnowledgeController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

无显式业务参数。

## MemberChangeApplicationController

### `GET /admin/member-change-applications`

处理方法：[MemberChangeApplicationController.queryForReview](../../src/main/java/com/yunfeil161/ailibrary/identity/application/controller/MemberChangeApplicationController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| query | model | [identity_application_dto_MemberChangeQueryRequest](#identity_application_dto_memberchangequeryrequest) |

### `POST /admin/member-change-applications/{id}/decision`

处理方法：[MemberChangeApplicationController.decide](../../src/main/java/com/yunfeil161/ailibrary/identity/application/controller/MemberChangeApplicationController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.Long` |
| request | body | [identity_application_dto_MemberChangeDecisionRequest](#identity_application_dto_memberchangedecisionrequest) |

## MemberController

### `GET /admin/members`

处理方法：[MemberController.getMember](../../src/main/java/com/yunfeil161/ailibrary/identity/controller/MemberController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| query | model | [identity_dto_MemberQueryRequest](#identity_dto_memberqueryrequest) |

### `POST /admin/members`

处理方法：[MemberController.addMember](../../src/main/java/com/yunfeil161/ailibrary/identity/controller/MemberController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| request | body | [identity_dto_MemberWriteRequest](#identity_dto_memberwriterequest) |

### `DELETE /admin/members/{id}`

处理方法：[MemberController.deleteMember](../../src/main/java/com/yunfeil161/ailibrary/identity/controller/MemberController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.Long` |
| memberType | query | `com.yunfeil161.ailibrary.common.enums.MemberType` |

### `PUT /admin/members/{id}`

处理方法：[MemberController.updateMember](../../src/main/java/com/yunfeil161/ailibrary/identity/controller/MemberController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.Long` |
| request | body | [identity_dto_MemberWriteRequest](#identity_dto_memberwriterequest) |

## OperationLogController

### `GET /admin/operation-logs`

处理方法：[OperationLogController.query](../../src/main/java/com/yunfeil161/ailibrary/audit/OperationLogController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| query | model | [audit_OperationLogQuery](#audit_operationlogquery) |

## OperationsController

### `POST /admin/operations/exports`

处理方法：[OperationsController.export](../../src/main/java/com/yunfeil161/ailibrary/operations/OperationsController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| query | body | [book_catalog_dto_BookQueryRequest](#book_catalog_dto_bookqueryrequest) |

### `POST /admin/operations/imports`

处理方法：[OperationsController.upload](../../src/main/java/com/yunfeil161/ailibrary/operations/OperationsController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| file | part | `org.springframework.web.multipart.MultipartFile` |

### `GET /admin/operations/jobs`

处理方法：[OperationsController.query](../../src/main/java/com/yunfeil161/ailibrary/operations/OperationsController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| query | model | [operations_OperationJobQuery](#operations_operationjobquery) |

### `GET /admin/operations/jobs/{id}/file`

处理方法：[OperationsController.download](../../src/main/java/com/yunfeil161/ailibrary/operations/OperationsController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.String` |

### `GET /admin/operations/overview`

处理方法：[OperationsController.overview](../../src/main/java/com/yunfeil161/ailibrary/operations/OperationsController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| from | query | `java.time.LocalDate` |
| to | query | `java.time.LocalDate` |

### `GET /admin/operations/template`

处理方法：[OperationsController.template](../../src/main/java/com/yunfeil161/ailibrary/operations/OperationsController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

无显式业务参数。

## PermissionController

### `GET /admin/permissions`

处理方法：[PermissionController.getPermissions](../../src/main/java/com/yunfeil161/ailibrary/config/security/permission/controller/PermissionController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| pageQuery | model | [common_api_PageQuery](#common_api_pagequery) |
| findParam | model | [config_security_permission_dto_PermissionFindParam](#config_security_permission_dto_permissionfindparam) |

### `POST /admin/permissions`

处理方法：[PermissionController.addPermission](../../src/main/java/com/yunfeil161/ailibrary/config/security/permission/controller/PermissionController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| permission | body | [config_security_permission_entity_Permission](#config_security_permission_entity_permission) |

### `DELETE /admin/permissions/{id}`

处理方法：[PermissionController.deletePermission](../../src/main/java/com/yunfeil161/ailibrary/config/security/permission/controller/PermissionController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.Long` |

### `GET /admin/permissions/{id}`

处理方法：[PermissionController.getPermissionById](../../src/main/java/com/yunfeil161/ailibrary/config/security/permission/controller/PermissionController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.Long` |

### `PUT /admin/permissions/{id}`

处理方法：[PermissionController.updatePermission](../../src/main/java/com/yunfeil161/ailibrary/config/security/permission/controller/PermissionController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.Long` |
| request | body | [config_security_permission_dto_PermissionUpdateRequest](#config_security_permission_dto_permissionupdaterequest) |

## PolicyController

### `GET /admin/policies`

处理方法：[PolicyController.query](../../src/main/java/com/yunfeil161/ailibrary/policy/controller/PolicyController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| query | model | [policy_dto_PolicyQueryRequest](#policy_dto_policyqueryrequest) |

### `POST /admin/policies`

处理方法：[PolicyController.create](../../src/main/java/com/yunfeil161/ailibrary/policy/controller/PolicyController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| request | body | [policy_dto_PolicyWriteRequest](#policy_dto_policywriterequest) |

### `PUT /admin/policies/{id}`

处理方法：[PolicyController.update](../../src/main/java/com/yunfeil161/ailibrary/policy/controller/PolicyController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.Long` |
| request | body | [policy_dto_PolicyWriteRequest](#policy_dto_policywriterequest) |

### `POST /admin/policies/{id}/publish`

处理方法：[PolicyController.publish](../../src/main/java/com/yunfeil161/ailibrary/policy/controller/PolicyController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.Long` |
| request | body | [policy_dto_PolicyPublishRequest](#policy_dto_policypublishrequest) |

## AdminReadingPlanController

### `GET /admin/reading-plans`

处理方法：[AdminReadingPlanController.query](../../src/main/java/com/yunfeil161/ailibrary/book/reading/controller/AdminReadingPlanController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| q | model | [book_reading_dto_ReadingPlanQueryRequest](#book_reading_dto_readingplanqueryrequest) |

### `POST /admin/reading-plans`

处理方法：[AdminReadingPlanController.create](../../src/main/java/com/yunfeil161/ailibrary/book/reading/controller/AdminReadingPlanController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| r | body | [book_reading_dto_ReadingPlanCreateRequest](#book_reading_dto_readingplancreaterequest) |

### `GET /admin/reading-plans/items`

处理方法：[AdminReadingPlanController.items](../../src/main/java/com/yunfeil161/ailibrary/book/reading/controller/AdminReadingPlanController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| q | model | [book_reading_dto_ReadingItemQueryRequest](#book_reading_dto_readingitemqueryrequest) |

### `DELETE /admin/reading-plans/{memberId}`

处理方法：[AdminReadingPlanController.delete](../../src/main/java/com/yunfeil161/ailibrary/book/reading/controller/AdminReadingPlanController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| memberId | path | `java.lang.Long` |
| expectedVersion | query | `java.lang.Long` |

### `PUT /admin/reading-plans/{memberId}`

处理方法：[AdminReadingPlanController.update](../../src/main/java/com/yunfeil161/ailibrary/book/reading/controller/AdminReadingPlanController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| memberId | path | `java.lang.Long` |
| r | body | [book_reading_dto_ReadingPlanUpdateRequest](#book_reading_dto_readingplanupdaterequest) |

### `POST /admin/reading-plans/{memberId}/items`

处理方法：[AdminReadingPlanController.addItem](../../src/main/java/com/yunfeil161/ailibrary/book/reading/controller/AdminReadingPlanController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| memberId | path | `java.lang.Long` |
| r | body | [book_reading_dto_ReadingItemCreateRequest](#book_reading_dto_readingitemcreaterequest) |

### `DELETE /admin/reading-plans/{memberId}/items/{bookId}`

处理方法：[AdminReadingPlanController.deleteItem](../../src/main/java/com/yunfeil161/ailibrary/book/reading/controller/AdminReadingPlanController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| memberId | path | `java.lang.Long` |
| bookId | path | `java.lang.Long` |
| expectedVersion | query | `java.lang.Long` |
| expectedPlanVersion | query | `java.lang.Long` |

### `PUT /admin/reading-plans/{memberId}/items/{bookId}`

处理方法：[AdminReadingPlanController.updateItem](../../src/main/java/com/yunfeil161/ailibrary/book/reading/controller/AdminReadingPlanController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| memberId | path | `java.lang.Long` |
| bookId | path | `java.lang.Long` |
| r | body | [book_reading_dto_ReadingItemUpdateRequest](#book_reading_dto_readingitemupdaterequest) |

## BusinessActionController

### `GET /ai/actions`

处理方法：[BusinessActionController.query](../../src/main/java/com/yunfeil161/ailibrary/ai/business/BusinessActionController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| actionId | query | `java.lang.String` |
| state | query | `java.lang.String` |
| page | model | [common_api_PageQuery](#common_api_pagequery) |

### `POST /ai/actions/preview`

处理方法：[BusinessActionController.preview](../../src/main/java/com/yunfeil161/ailibrary/ai/business/BusinessActionController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| request | body | [ai_business_BusinessActionService_Preview](#ai_business_businessactionservice_preview) |

### `POST /ai/actions/{actionId}/cancel`

处理方法：[BusinessActionController.cancel](../../src/main/java/com/yunfeil161/ailibrary/ai/business/BusinessActionController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| actionId | path | `java.lang.String` |

### `POST /ai/actions/{actionId}/confirm`

处理方法：[BusinessActionController.confirm](../../src/main/java/com/yunfeil161/ailibrary/ai/business/BusinessActionController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| actionId | path | `java.lang.String` |
| confirmation | body | [ai_business_BusinessActionService_Confirmation](#ai_business_businessactionservice_confirmation) |

## AiChatController

### `POST /ai/chat`

处理方法：[AiChatController.chat](../../src/main/java/com/yunfeil161/ailibrary/ai/controller/AiChatController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| input | body | [ai_dto_AiChatRequest](#ai_dto_aichatrequest) |

返回SSE，不能当作普通Result JSON解析。

### `DELETE /ai/conversations/{id}`

处理方法：[AiChatController.clear](../../src/main/java/com/yunfeil161/ailibrary/ai/controller/AiChatController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.String` |

### `GET /ai/conversations/{id}`

处理方法：[AiChatController.conversation](../../src/main/java/com/yunfeil161/ailibrary/ai/controller/AiChatController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.String` |

## AiHandoffController

### `GET /ai/handoffs`

处理方法：[AiHandoffController.query](../../src/main/java/com/yunfeil161/ailibrary/ai/handoff/AiHandoffController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| q | model | [ai_handoff_AiHandoffQuery](#ai_handoff_aihandoffquery) |

### `POST /ai/handoffs`

处理方法：[AiHandoffController.create](../../src/main/java/com/yunfeil161/ailibrary/ai/handoff/AiHandoffController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| q | body | [ai_handoff_AiHandoffService_Request](#ai_handoff_aihandoffservice_request) |

### `POST /ai/handoffs/{id}/decision`

处理方法：[AiHandoffController.decide](../../src/main/java/com/yunfeil161/ailibrary/ai/handoff/AiHandoffController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.Long` |
| q | body | [ai_handoff_AiHandoffController_Decision](#ai_handoff_aihandoffcontroller_decision) |

## BusinessActionController

### `GET /ai/skills`

处理方法：[BusinessActionController.skills](../../src/main/java/com/yunfeil161/ailibrary/ai/business/BusinessActionController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

无显式业务参数。

## AiRuntimeController

### `GET /ai/tasks`

处理方法：[AiRuntimeController.query](../../src/main/java/com/yunfeil161/ailibrary/ai/runtime/AiRuntimeController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| query | model | [ai_runtime_AiTaskQuery](#ai_runtime_aitaskquery) |

### `POST /ai/tasks`

处理方法：[AiRuntimeController.submit](../../src/main/java/com/yunfeil161/ailibrary/ai/runtime/AiRuntimeController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| request | body | [ai_runtime_AiTaskService_Request](#ai_runtime_aitaskservice_request) |

### `POST /ai/tasks/{id}/cancel`

处理方法：[AiRuntimeController.cancel](../../src/main/java/com/yunfeil161/ailibrary/ai/runtime/AiRuntimeController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.String` |

### `POST /ai/tasks/{id}/retry`

处理方法：[AiRuntimeController.retry](../../src/main/java/com/yunfeil161/ailibrary/ai/runtime/AiRuntimeController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.String` |

### `GET /ai/usage`

处理方法：[AiRuntimeController.usage](../../src/main/java/com/yunfeil161/ailibrary/ai/runtime/AiRuntimeController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| from | query | `java.time.LocalDate` |
| to | query | `java.time.LocalDate` |
| memberId | query | `java.lang.Long` |

## BookCategoryController

### `GET /book-categories`

处理方法：[BookCategoryController.query](../../src/main/java/com/yunfeil161/ailibrary/book/category/controller/BookCategoryController.java)。公开能力；规则与限流校验仍有效。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| query | model | [book_category_dto_BookCategoryQueryRequest](#book_category_dto_bookcategoryqueryrequest) |

## BookController

### `GET /books`

处理方法：[BookController.query](../../src/main/java/com/yunfeil161/ailibrary/book/catalog/controller/BookController.java)。公开能力；规则与限流校验仍有效。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| query | model | [book_catalog_dto_BookQueryRequest](#book_catalog_dto_bookqueryrequest) |

## BookCoverController

### `GET /books/{bookId}/cover`

处理方法：[BookCoverController.query](../../src/main/java/com/yunfeil161/ailibrary/book/cover/controller/BookCoverController.java)。公开能力；规则与限流校验仍有效。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| bookId | path | `java.lang.Long` |

### `GET /books/{bookId}/cover/content`

处理方法：[BookCoverController.content](../../src/main/java/com/yunfeil161/ailibrary/book/cover/controller/BookCoverController.java)。公开能力；规则与限流校验仍有效。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| bookId | path | `java.lang.Long` |

## BorrowApplicationController

### `GET /borrow-applications`

处理方法：[BorrowApplicationController.mine](../../src/main/java/com/yunfeil161/ailibrary/book/borrow/controller/BorrowApplicationController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| q | model | [book_borrow_dto_BorrowApplicationQueryRequest](#book_borrow_dto_borrowapplicationqueryrequest) |

### `POST /borrow-applications`

处理方法：[BorrowApplicationController.create](../../src/main/java/com/yunfeil161/ailibrary/book/borrow/controller/BorrowApplicationController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| r | body | [book_borrow_dto_BorrowApplicationCreateRequest](#book_borrow_dto_borrowapplicationcreaterequest) |

### `POST /borrow-applications/{id}/cancel`

处理方法：[BorrowApplicationController.cancel](../../src/main/java/com/yunfeil161/ailibrary/book/borrow/controller/BorrowApplicationController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.Long` |
| r | body | [book_borrow_dto_BorrowApplicationCancelRequest](#book_borrow_dto_borrowapplicationcancelrequest) |

## BorrowRecordController

### `GET /borrow-records`

处理方法：[BorrowRecordController.mine](../../src/main/java/com/yunfeil161/ailibrary/book/borrow/controller/BorrowRecordController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| q | model | [book_borrow_dto_BorrowRecordQueryRequest](#book_borrow_dto_borrowrecordqueryrequest) |

## ConversationController

### `GET /conversations`

处理方法：[ConversationController.query](../../src/main/java/com/yunfeil161/ailibrary/communication/conversation/controller/ConversationController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| q | model | [communication_conversation_dto_ConversationQueryRequest](#communication_conversation_dto_conversationqueryrequest) |

### `POST /conversations`

处理方法：[ConversationController.create](../../src/main/java/com/yunfeil161/ailibrary/communication/conversation/controller/ConversationController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| q | body | [communication_conversation_dto_ConversationCreateRequest](#communication_conversation_dto_conversationcreaterequest) |

### `PUT /conversations/{id}`

处理方法：[ConversationController.update](../../src/main/java/com/yunfeil161/ailibrary/communication/conversation/controller/ConversationController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.Long` |
| q | body | [communication_conversation_dto_ConversationUpdateRequest](#communication_conversation_dto_conversationupdaterequest) |

## RecommendationController

### `GET /interest-tags`

处理方法：[RecommendationController.tags](../../src/main/java/com/yunfeil161/ailibrary/recommendation/controller/RecommendationController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | query | `java.lang.Long` |
| code | query | `java.lang.String` |
| name | query | `java.lang.String` |

## LoginController

### `POST /login`

处理方法：[LoginController.login](../../src/main/java/com/yunfeil161/ailibrary/auth/controller/LoginController.java)。公开能力；规则与限流校验仍有效。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| loginParam | body | [auth_dto_LoginParam](#auth_dto_loginparam) |

### `POST /login/code`

处理方法：[LoginController.loginCode](../../src/main/java/com/yunfeil161/ailibrary/auth/controller/LoginController.java)。公开能力；规则与限流校验仍有效。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| loginParam | body | [auth_dto_LoginParam](#auth_dto_loginparam) |

### `POST /login/register`

处理方法：[LoginController.confirmRegister](../../src/main/java/com/yunfeil161/ailibrary/auth/controller/LoginController.java)。公开能力；规则与限流校验仍有效。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| registerParam | body | [auth_dto_RegisterParam](#auth_dto_registerparam) |

### `POST /login/reset-password`

处理方法：[LoginController.resetPassword](../../src/main/java/com/yunfeil161/ailibrary/auth/controller/LoginController.java)。公开能力；规则与限流校验仍有效。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| registerParam | body | [auth_dto_RegisterParam](#auth_dto_registerparam) |

### `POST /logout`

处理方法：[LoginController.logout](../../src/main/java/com/yunfeil161/ailibrary/auth/controller/LoginController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

无显式业务参数。

## PolicyController

### `GET /me/borrowing-rules`

处理方法：[PolicyController.rules](../../src/main/java/com/yunfeil161/ailibrary/policy/controller/PolicyController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

无显式业务参数。

### `POST /me/borrowing-rules/decision`

处理方法：[PolicyController.decision](../../src/main/java/com/yunfeil161/ailibrary/policy/controller/PolicyController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| request | body | [policy_dto_RuleDecisionRequest](#policy_dto_ruledecisionrequest) |

## RecommendationController

### `GET /me/interests`

处理方法：[RecommendationController.interests](../../src/main/java/com/yunfeil161/ailibrary/recommendation/controller/RecommendationController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

无显式业务参数。

### `PUT /me/interests`

处理方法：[RecommendationController.interests](../../src/main/java/com/yunfeil161/ailibrary/recommendation/controller/RecommendationController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| request | body | [recommendation_dto_TagsRequest](#recommendation_dto_tagsrequest) |

## CurrentPermissionController

### `GET /me/permissions`

处理方法：[CurrentPermissionController.current](../../src/main/java/com/yunfeil161/ailibrary/config/security/permission/controller/CurrentPermissionController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

无显式业务参数。

## RecommendationController

### `GET /me/recommendations`

处理方法：[RecommendationController.current](../../src/main/java/com/yunfeil161/ailibrary/recommendation/controller/RecommendationController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

无显式业务参数。

### `POST /me/recommendations/refresh`

处理方法：[RecommendationController.refresh](../../src/main/java/com/yunfeil161/ailibrary/recommendation/controller/RecommendationController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| Idempotency-Key | header | `java.lang.String` |

### `POST /me/recommendations/{itemId}/feedback`

处理方法：[RecommendationController.feedback](../../src/main/java/com/yunfeil161/ailibrary/recommendation/controller/RecommendationController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| itemId | path | `java.lang.Long` |
| request | body | [recommendation_dto_FeedbackRequest](#recommendation_dto_feedbackrequest) |

## MemberChangeApplicationController

### `GET /member-change-applications`

处理方法：[MemberChangeApplicationController.queryMine](../../src/main/java/com/yunfeil161/ailibrary/identity/application/controller/MemberChangeApplicationController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| query | model | [identity_application_dto_MemberChangeQueryRequest](#identity_application_dto_memberchangequeryrequest) |

### `POST /member-change-applications`

处理方法：[MemberChangeApplicationController.create](../../src/main/java/com/yunfeil161/ailibrary/identity/application/controller/MemberChangeApplicationController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| request | body | [identity_application_dto_MemberChangeCreateRequest](#identity_application_dto_memberchangecreaterequest) |

### `POST /member-change-applications/{id}/cancel`

处理方法：[MemberChangeApplicationController.cancel](../../src/main/java/com/yunfeil161/ailibrary/identity/application/controller/MemberChangeApplicationController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.Long` |
| request | body | [identity_application_dto_MemberChangeCancelRequest](#identity_application_dto_memberchangecancelrequest) |

## MemberController

### `GET /members`

处理方法：[MemberController.getMember](../../src/main/java/com/yunfeil161/ailibrary/identity/controller/MemberController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| query | model | [identity_dto_MemberQueryRequest](#identity_dto_memberqueryrequest) |

### `POST /members`

处理方法：[MemberController.addMember](../../src/main/java/com/yunfeil161/ailibrary/identity/controller/MemberController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| request | body | [identity_dto_MemberWriteRequest](#identity_dto_memberwriterequest) |

### `DELETE /members/{id}`

处理方法：[MemberController.deleteMember](../../src/main/java/com/yunfeil161/ailibrary/identity/controller/MemberController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.Long` |
| memberType | query | `com.yunfeil161.ailibrary.common.enums.MemberType` |

### `PUT /members/{id}`

处理方法：[MemberController.updateMember](../../src/main/java/com/yunfeil161/ailibrary/identity/controller/MemberController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.Long` |
| request | body | [identity_dto_MemberWriteRequest](#identity_dto_memberwriterequest) |

## MessageController

### `GET /messages`

处理方法：[MessageController.query](../../src/main/java/com/yunfeil161/ailibrary/communication/message/controller/MessageController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| request | model | [communication_message_dto_MessageQueryRequest](#communication_message_dto_messagequeryrequest) |

### `POST /messages`

处理方法：[MessageController.send](../../src/main/java/com/yunfeil161/ailibrary/communication/message/controller/MessageController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| request | body | [communication_message_dto_MessageSendRequest](#communication_message_dto_messagesendrequest) |

### `POST /messages/{id}/read`

处理方法：[MessageController.read](../../src/main/java/com/yunfeil161/ailibrary/communication/message/controller/MessageController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| id | path | `java.lang.Long` |

## PolicyController

### `GET /policies/current`

处理方法：[PolicyController.current](../../src/main/java/com/yunfeil161/ailibrary/policy/controller/PolicyController.java)。公开能力；规则与限流校验仍有效。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| type | query | `java.lang.String` |

## ReadingPlanController

### `DELETE /reading-plans`

处理方法：[ReadingPlanController.delete](../../src/main/java/com/yunfeil161/ailibrary/book/reading/controller/ReadingPlanController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| expectedVersion | query | `java.lang.Long` |

### `GET /reading-plans`

处理方法：[ReadingPlanController.get](../../src/main/java/com/yunfeil161/ailibrary/book/reading/controller/ReadingPlanController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

无显式业务参数。

### `POST /reading-plans`

处理方法：[ReadingPlanController.create](../../src/main/java/com/yunfeil161/ailibrary/book/reading/controller/ReadingPlanController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| r | body | [book_reading_dto_ReadingPlanCreateRequest](#book_reading_dto_readingplancreaterequest) |

### `PUT /reading-plans`

处理方法：[ReadingPlanController.update](../../src/main/java/com/yunfeil161/ailibrary/book/reading/controller/ReadingPlanController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| r | body | [book_reading_dto_ReadingPlanUpdateRequest](#book_reading_dto_readingplanupdaterequest) |

### `GET /reading-plans/items`

处理方法：[ReadingPlanController.items](../../src/main/java/com/yunfeil161/ailibrary/book/reading/controller/ReadingPlanController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| q | model | [book_reading_dto_ReadingItemQueryRequest](#book_reading_dto_readingitemqueryrequest) |

### `POST /reading-plans/items`

处理方法：[ReadingPlanController.addItem](../../src/main/java/com/yunfeil161/ailibrary/book/reading/controller/ReadingPlanController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| r | body | [book_reading_dto_ReadingItemCreateRequest](#book_reading_dto_readingitemcreaterequest) |

### `DELETE /reading-plans/items/{bookId}`

处理方法：[ReadingPlanController.deleteItem](../../src/main/java/com/yunfeil161/ailibrary/book/reading/controller/ReadingPlanController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| bookId | path | `java.lang.Long` |
| expectedVersion | query | `java.lang.Long` |
| expectedPlanVersion | query | `java.lang.Long` |

### `PUT /reading-plans/items/{bookId}`

处理方法：[ReadingPlanController.updateItem](../../src/main/java/com/yunfeil161/ailibrary/book/reading/controller/ReadingPlanController.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

| 参数 | 绑定位置 | 类型 |
| --- | --- | --- |
| bookId | path | `java.lang.Long` |
| r | body | [book_reading_dto_ReadingItemUpdateRequest](#book_reading_dto_readingitemupdaterequest) |

## SocketConfiguration

### `POST /ws/tickets`

处理方法：[SocketConfiguration.ticket](../../src/main/java/com/yunfeil161/ailibrary/communication/websocket/SocketConfiguration.java)。需要登录；具体权限编码和数据范围在Controller或Service中校验。

无显式业务参数。

# 请求模型字段索引

下表包含继承字段；枚举弃用值不再作为可选项。`expectedVersion` 等内部版本仍由前端自动携带，不由用户手工输入。

## ai_business_BusinessActionService_Confirmation

`com.yunfeil161.ailibrary.ai.business.BusinessActionService$Confirmation`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| requestHash | `java.lang.String` | — |

## ai_business_BusinessActionService_Preview

`com.yunfeil161.ailibrary.ai.business.BusinessActionService$Preview`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| skill | `java.lang.String` | — |
| arguments | `java.util.Map<java.lang.String, java.lang.Object>` | — |

## ai_dto_AiChatRequest

`com.yunfeil161.ailibrary.ai.dto.AiChatRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| question | `java.lang.String` | — |
| conversationId | `java.lang.String` | — |
| task | `java.lang.String` | — |

## ai_handoff_AiHandoffController_Decision

`com.yunfeil161.ailibrary.ai.handoff.AiHandoffController$Decision`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| decision | `java.lang.String` | — |
| expectedVersion | `java.lang.Long` | — |

## ai_handoff_AiHandoffQuery

`com.yunfeil161.ailibrary.ai.handoff.AiHandoffQuery`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| id | `java.lang.Long` | — |
| memberId | `java.lang.Long` | — |
| staffMemberId | `java.lang.Long` | — |
| status | `java.lang.String` | — |
| page | `java.lang.Integer` | — |
| pageSize | `java.lang.Integer` | — |

## ai_handoff_AiHandoffService_Request

`com.yunfeil161.ailibrary.ai.handoff.AiHandoffService$Request`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| staffMemberId | `java.lang.Long` | — |
| aiConversationId | `java.lang.String` | — |
| summary | `java.lang.String` | — |

## ai_runtime_AiRuntimeController_ControlRequest

`com.yunfeil161.ailibrary.ai.runtime.AiRuntimeController$ControlRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| enabled | `java.lang.Boolean` | — |
| expectedVersion | `java.lang.Long` | — |

## ai_runtime_AiTaskQuery

`com.yunfeil161.ailibrary.ai.runtime.AiTaskQuery`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| taskId | `java.lang.String` | — |
| memberId | `java.lang.Long` | — |
| state | `java.lang.String` | — |
| type | `java.lang.String` | — |
| page | `java.lang.Integer` | — |
| pageSize | `java.lang.Integer` | — |

## ai_runtime_AiTaskService_Request

`com.yunfeil161.ailibrary.ai.runtime.AiTaskService$Request`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| type | `java.lang.String` | — |
| question | `java.lang.String` | — |

## audit_OperationLogQuery

`com.yunfeil161.ailibrary.audit.OperationLogQuery`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| id | `java.lang.Long` | — |
| actorMemberId | `java.lang.Long` | — |
| actorRoleId | `java.lang.Long` | — |
| actorKind | `java.lang.String` | — |
| targetType | `java.lang.String` | — |
| targetId | `java.lang.Long` | — |
| actionCode | `java.lang.String` | — |
| resultStatus | `java.lang.String` | — |
| createdFrom | `java.time.LocalDateTime` | — |
| createdTo | `java.time.LocalDateTime` | — |
| page | `java.lang.Integer` | — |
| pageSize | `java.lang.Integer` | — |

## auth_dto_LoginParam

`com.yunfeil161.ailibrary.auth.dto.LoginParam`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| email | `java.lang.String` | — |
| password | `java.lang.String` | — |

## auth_dto_RegisterParam

`com.yunfeil161.ailibrary.auth.dto.RegisterParam`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| email | `java.lang.String` | — |
| password | `java.lang.String` | — |
| code | `java.lang.String` | — |
| name | `java.lang.String` | — |
| memberType | `com.yunfeil161.ailibrary.common.enums.MemberType` | READER、EMPLOYEE |
| departmentId | `java.lang.Long` | — |
| positionName | `java.lang.String` | — |

## book_borrow_dto_BorrowApplicationApproveRequest

`com.yunfeil161.ailibrary.book.borrow.dto.BorrowApplicationApproveRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| copyId | `java.lang.Long` | — |
| approvedDays | `java.lang.Integer` | — |
| decisionRemark | `java.lang.String` | — |
| expectedVersion | `java.lang.Long` | — |

## book_borrow_dto_BorrowApplicationCancelRequest

`com.yunfeil161.ailibrary.book.borrow.dto.BorrowApplicationCancelRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| expectedVersion | `java.lang.Long` | — |

## book_borrow_dto_BorrowApplicationCreateRequest

`com.yunfeil161.ailibrary.book.borrow.dto.BorrowApplicationCreateRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| applicationType | `com.yunfeil161.ailibrary.book.borrow.enums.BorrowApplicationType` | BORROW、RENEW |
| bookId | `java.lang.Long` | — |
| borrowRecordId | `java.lang.Long` | — |
| requestedDays | `java.lang.Integer` | — |
| reason | `java.lang.String` | — |

## book_borrow_dto_BorrowApplicationQueryRequest

`com.yunfeil161.ailibrary.book.borrow.dto.BorrowApplicationQueryRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| id | `java.lang.Long` | — |
| applicationType | `com.yunfeil161.ailibrary.book.borrow.enums.BorrowApplicationType` | BORROW、RENEW |
| borrowRecordId | `java.lang.Long` | — |
| status | `com.yunfeil161.ailibrary.book.borrow.enums.BorrowApplicationStatus` | PENDING、APPROVED、REJECTED、CANCELLED、EXPIRED |
| memberId | `java.lang.Long` | — |
| memberName | `java.lang.String` | — |
| bookId | `java.lang.Long` | — |
| bookTitle | `java.lang.String` | — |
| isbn | `java.lang.String` | — |
| submittedFrom | `java.time.LocalDateTime` | — |
| submittedTo | `java.time.LocalDateTime` | — |
| processedBy | `java.lang.Long` | — |
| page | `java.lang.Integer` | — |
| pageSize | `java.lang.Integer` | — |

## book_borrow_dto_BorrowApplicationRejectRequest

`com.yunfeil161.ailibrary.book.borrow.dto.BorrowApplicationRejectRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| decisionRemark | `java.lang.String` | — |
| expectedVersion | `java.lang.Long` | — |

## book_borrow_dto_BorrowRecordQueryRequest

`com.yunfeil161.ailibrary.book.borrow.dto.BorrowRecordQueryRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| id | `java.lang.Long` | — |
| memberId | `java.lang.Long` | — |
| memberName | `java.lang.String` | — |
| bookId | `java.lang.Long` | — |
| bookTitle | `java.lang.String` | — |
| isbn | `java.lang.String` | — |
| copyBarcode | `java.lang.String` | — |
| status | `com.yunfeil161.ailibrary.book.borrow.enums.BorrowRecordStatus` | BORROWING、OVERDUE、RETURNED、LOST |
| returnOutcome | `com.yunfeil161.ailibrary.book.borrow.enums.BorrowReturnOutcome` | NORMAL、DAMAGED、LOST |
| currentOnly | `java.lang.Boolean` | — |
| overdueOnly | `java.lang.Boolean` | — |
| borrowedFrom | `java.time.LocalDateTime` | — |
| borrowedTo | `java.time.LocalDateTime` | — |
| dueFrom | `java.time.LocalDateTime` | — |
| dueTo | `java.time.LocalDateTime` | — |
| page | `java.lang.Integer` | — |
| pageSize | `java.lang.Integer` | — |

## book_borrow_dto_BorrowReturnRequest

`com.yunfeil161.ailibrary.book.borrow.dto.BorrowReturnRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| returnOutcome | `com.yunfeil161.ailibrary.book.borrow.enums.BorrowReturnOutcome` | NORMAL、DAMAGED、LOST |
| returnConditionNote | `java.lang.String` | — |
| expectedVersion | `java.lang.Long` | — |

## book_catalog_dto_BookCreateRequest

`com.yunfeil161.ailibrary.book.catalog.dto.BookCreateRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| tagIds | `java.util.List<java.lang.Long>` | — |
| categoryId | `java.lang.Long` | — |
| title | `java.lang.String` | — |
| subtitle | `java.lang.String` | — |
| author | `java.lang.String` | — |
| isbn | `java.lang.String` | — |
| publisher | `java.lang.String` | — |
| publishedDate | `java.time.LocalDate` | — |
| edition | `java.lang.String` | — |
| language | `java.lang.String` | — |
| description | `java.lang.String` | — |
| coverObjectKey | `java.lang.String` | — |

## book_catalog_dto_BookQueryRequest

`com.yunfeil161.ailibrary.book.catalog.dto.BookQueryRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| id | `java.lang.Long` | — |
| categoryId | `java.lang.Long` | — |
| keyword | `java.lang.String` | — |
| title | `java.lang.String` | — |
| subtitle | `java.lang.String` | — |
| author | `java.lang.String` | — |
| isbn | `java.lang.String` | — |
| publisher | `java.lang.String` | — |
| publishedDate | `java.time.LocalDate` | — |
| edition | `java.lang.String` | — |
| language | `java.lang.String` | — |
| description | `java.lang.String` | — |
| coverObjectKey | `java.lang.String` | — |
| status | `com.yunfeil161.ailibrary.book.common.enums.CatalogStatus` | ACTIVE、DISABLED |
| version | `java.lang.Long` | — |
| createdAt | `java.time.LocalDateTime` | — |
| updatedAt | `java.time.LocalDateTime` | — |
| availableOnly | `java.lang.Boolean` | — |
| page | `java.lang.Integer` | — |
| pageSize | `java.lang.Integer` | — |

## book_catalog_dto_BookUpdateRequest

`com.yunfeil161.ailibrary.book.catalog.dto.BookUpdateRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| tagIds | `java.util.List<java.lang.Long>` | — |
| categoryId | `java.lang.Long` | — |
| title | `java.lang.String` | — |
| subtitle | `java.lang.String` | — |
| author | `java.lang.String` | — |
| isbn | `java.lang.String` | — |
| publisher | `java.lang.String` | — |
| publishedDate | `java.time.LocalDate` | — |
| edition | `java.lang.String` | — |
| language | `java.lang.String` | — |
| description | `java.lang.String` | — |
| coverObjectKey | `java.lang.String` | — |
| status | `com.yunfeil161.ailibrary.book.common.enums.CatalogStatus` | ACTIVE、DISABLED |
| expectedVersion | `java.lang.Long` | — |
| subtitleProvided | `boolean` | — |
| isbnProvided | `boolean` | — |
| publisherProvided | `boolean` | — |
| publishedDateProvided | `boolean` | — |
| editionProvided | `boolean` | — |
| descriptionProvided | `boolean` | — |
| coverObjectKeyProvided | `boolean` | — |

## book_category_dto_BookCategoryCreateRequest

`com.yunfeil161.ailibrary.book.category.dto.BookCategoryCreateRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| code | `java.lang.String` | — |
| name | `java.lang.String` | — |
| parentId | `java.lang.Long` | — |
| description | `java.lang.String` | — |

## book_category_dto_BookCategoryQueryRequest

`com.yunfeil161.ailibrary.book.category.dto.BookCategoryQueryRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| id | `java.lang.Long` | — |
| code | `java.lang.String` | — |
| name | `java.lang.String` | — |
| parentId | `java.lang.Long` | — |
| description | `java.lang.String` | — |
| status | `com.yunfeil161.ailibrary.book.common.enums.CatalogStatus` | ACTIVE、DISABLED |
| version | `java.lang.Long` | — |
| createdAt | `java.time.LocalDateTime` | — |
| updatedAt | `java.time.LocalDateTime` | — |
| rootOnly | `java.lang.Boolean` | — |
| page | `java.lang.Integer` | — |
| pageSize | `java.lang.Integer` | — |

## book_category_dto_BookCategoryUpdateRequest

`com.yunfeil161.ailibrary.book.category.dto.BookCategoryUpdateRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| name | `java.lang.String` | — |
| parentId | `java.lang.Long` | — |
| parentIdProvided | `boolean` | — |
| description | `java.lang.String` | — |
| descriptionProvided | `boolean` | — |
| status | `com.yunfeil161.ailibrary.book.common.enums.CatalogStatus` | ACTIVE、DISABLED |
| expectedVersion | `java.lang.Long` | — |

## book_copy_dto_BookCopyCreateRequest

`com.yunfeil161.ailibrary.book.copy.dto.BookCopyCreateRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| barcode | `java.lang.String` | — |
| callNumber | `java.lang.String` | — |
| location | `java.lang.String` | — |
| acquiredAt | `java.time.LocalDate` | — |
| price | `java.math.BigDecimal` | — |
| conditionNote | `java.lang.String` | — |

## book_copy_dto_BookCopyQueryRequest

`com.yunfeil161.ailibrary.book.copy.dto.BookCopyQueryRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| id | `java.lang.Long` | — |
| bookId | `java.lang.Long` | — |
| barcode | `java.lang.String` | — |
| callNumber | `java.lang.String` | — |
| location | `java.lang.String` | — |
| status | `com.yunfeil161.ailibrary.book.copy.enums.BookCopyStatus` | AVAILABLE、BORROWED、RESERVED、MAINTENANCE、LOST、DISCARDED |
| acquiredAt | `java.time.LocalDate` | — |
| price | `java.math.BigDecimal` | — |
| conditionNote | `java.lang.String` | — |
| version | `java.lang.Long` | — |
| createdAt | `java.time.LocalDateTime` | — |
| updatedAt | `java.time.LocalDateTime` | — |
| page | `java.lang.Integer` | — |
| pageSize | `java.lang.Integer` | — |

## book_copy_dto_BookCopyUpdateRequest

`com.yunfeil161.ailibrary.book.copy.dto.BookCopyUpdateRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| barcode | `java.lang.String` | — |
| callNumber | `java.lang.String` | — |
| location | `java.lang.String` | — |
| status | `com.yunfeil161.ailibrary.book.copy.enums.BookCopyStatus` | AVAILABLE、BORROWED、RESERVED、MAINTENANCE、LOST、DISCARDED |
| acquiredAt | `java.time.LocalDate` | — |
| price | `java.math.BigDecimal` | — |
| conditionNote | `java.lang.String` | — |
| expectedVersion | `java.lang.Long` | — |
| callNumberProvided | `boolean` | — |
| acquiredAtProvided | `boolean` | — |
| priceProvided | `boolean` | — |
| conditionNoteProvided | `boolean` | — |

## book_reading_dto_ReadingItemCreateRequest

`com.yunfeil161.ailibrary.book.reading.dto.ReadingItemCreateRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| bookId | `java.lang.Long` | — |
| expectedPlanVersion | `java.lang.Long` | — |
| sequenceNo | `java.lang.Integer` | — |
| priority | `java.lang.Integer` | — |
| status | `com.yunfeil161.ailibrary.book.reading.enums.ReadingItemStatus` | PLANNED、READING、COMPLETED、ABANDONED |
| source | `com.yunfeil161.ailibrary.book.reading.enums.ReadingItemSource` | USER、LIBRARIAN、AI |
| plannedStartDate | `java.time.LocalDate` | — |
| plannedFinishDate | `java.time.LocalDate` | — |
| note | `java.lang.String` | — |

## book_reading_dto_ReadingItemQueryRequest

`com.yunfeil161.ailibrary.book.reading.dto.ReadingItemQueryRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| pageNum | `java.lang.Integer` | — |
| pageSize | `java.lang.Integer` | — |
| memberId | `java.lang.Long` | — |
| bookId | `java.lang.Long` | — |
| memberKeyword | `java.lang.String` | — |
| bookKeyword | `java.lang.String` | — |
| status | `com.yunfeil161.ailibrary.book.reading.enums.ReadingItemStatus` | PLANNED、READING、COMPLETED、ABANDONED |
| source | `com.yunfeil161.ailibrary.book.reading.enums.ReadingItemSource` | USER、LIBRARIAN、AI |
| orderBy | `java.lang.String` | — |
| orderDirection | `java.lang.String` | — |
| sortColumn | `java.lang.String` | — |
| sortDirection | `java.lang.String` | — |

## book_reading_dto_ReadingItemUpdateRequest

`com.yunfeil161.ailibrary.book.reading.dto.ReadingItemUpdateRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| sequenceNo | `java.lang.Integer` | — |
| priority | `java.lang.Integer` | — |
| progressPercent | `java.lang.Integer` | — |
| status | `com.yunfeil161.ailibrary.book.reading.enums.ReadingItemStatus` | PLANNED、READING、COMPLETED、ABANDONED |
| plannedStartDate | `java.time.LocalDate` | — |
| plannedFinishDate | `java.time.LocalDate` | — |
| note | `java.lang.String` | — |
| version | `java.lang.Long` | — |
| expectedPlanVersion | `java.lang.Long` | — |

## book_reading_dto_ReadingPlanCreateRequest

`com.yunfeil161.ailibrary.book.reading.dto.ReadingPlanCreateRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| memberId | `java.lang.Long` | — |
| name | `java.lang.String` | — |
| description | `java.lang.String` | — |
| targetBooks | `java.lang.Integer` | — |
| startDate | `java.time.LocalDate` | — |
| endDate | `java.time.LocalDate` | — |
| status | `com.yunfeil161.ailibrary.book.reading.enums.ReadingPlanStatus` | DRAFT、ACTIVE、PAUSED、COMPLETED |

## book_reading_dto_ReadingPlanQueryRequest

`com.yunfeil161.ailibrary.book.reading.dto.ReadingPlanQueryRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| pageNum | `java.lang.Integer` | — |
| pageSize | `java.lang.Integer` | — |
| memberId | `java.lang.Long` | — |
| memberKeyword | `java.lang.String` | — |
| planName | `java.lang.String` | — |
| status | `com.yunfeil161.ailibrary.book.reading.enums.ReadingPlanStatus` | DRAFT、ACTIVE、PAUSED、COMPLETED |
| startDateFrom | `java.time.LocalDate` | — |
| startDateTo | `java.time.LocalDate` | — |

## book_reading_dto_ReadingPlanUpdateRequest

`com.yunfeil161.ailibrary.book.reading.dto.ReadingPlanUpdateRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| name | `java.lang.String` | — |
| description | `java.lang.String` | — |
| targetBooks | `java.lang.Integer` | — |
| startDate | `java.time.LocalDate` | — |
| endDate | `java.time.LocalDate` | — |
| status | `com.yunfeil161.ailibrary.book.reading.enums.ReadingPlanStatus` | DRAFT、ACTIVE、PAUSED、COMPLETED |
| version | `java.lang.Long` | — |

## common_api_PageQuery

`com.yunfeil161.ailibrary.common.api.PageQuery`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| page | `java.lang.Integer` | — |
| pageSize | `java.lang.Integer` | — |

## communication_conversation_dto_ConversationCreateRequest

`com.yunfeil161.ailibrary.communication.conversation.dto.ConversationCreateRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| targetMemberId | `java.lang.Long` | — |
| subject | `java.lang.String` | — |

## communication_conversation_dto_ConversationQueryRequest

`com.yunfeil161.ailibrary.communication.conversation.dto.ConversationQueryRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| id | `java.lang.Long` | — |
| targetMemberId | `java.lang.Long` | — |
| subject | `java.lang.String` | — |
| status | `java.lang.String` | — |
| unreadOnly | `java.lang.Boolean` | — |
| createdFrom | `java.time.LocalDateTime` | — |
| createdTo | `java.time.LocalDateTime` | — |
| page | `java.lang.Integer` | — |
| pageSize | `java.lang.Integer` | — |

## communication_conversation_dto_ConversationUpdateRequest

`com.yunfeil161.ailibrary.communication.conversation.dto.ConversationUpdateRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| subject | `java.lang.String` | — |
| status | `java.lang.String` | — |
| expectedVersion | `java.lang.Long` | — |

## communication_message_dto_MessageQueryRequest

`com.yunfeil161.ailibrary.communication.message.dto.MessageQueryRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| messageType | `java.lang.String` | — |
| businessType | `java.lang.String` | — |
| businessId | `java.lang.Long` | — |
| id | `java.lang.Long` | — |
| conversationId | `java.lang.Long` | — |
| box | `java.lang.String` | — |
| senderMemberId | `java.lang.Long` | — |
| recipientMemberId | `java.lang.Long` | — |
| sendType | `java.lang.String` | — |
| subject | `java.lang.String` | — |
| read | `java.lang.Boolean` | — |
| createdFrom | `java.time.LocalDateTime` | — |
| createdTo | `java.time.LocalDateTime` | — |
| afterSequence | `java.lang.Long` | — |
| page | `java.lang.Integer` | — |
| pageSize | `java.lang.Integer` | — |

## communication_message_dto_MessageSendRequest

`com.yunfeil161.ailibrary.communication.message.dto.MessageSendRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| conversationId | `java.lang.Long` | — |
| recipientIds | `java.util.List<java.lang.Long>` | — |
| subject | `java.lang.String` | — |
| body | `java.lang.String` | — |
| clientMessageId | `java.lang.String` | — |

## config_security_permission_dto_PermissionFindParam

`com.yunfeil161.ailibrary.config.security.permission.dto.PermissionFindParam`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| id | `java.lang.Integer` | — |
| code | `java.lang.String` | — |
| name | `java.lang.String` | — |
| module | `java.lang.String` | — |

## config_security_permission_dto_PermissionUpdateRequest

`com.yunfeil161.ailibrary.config.security.permission.dto.PermissionUpdateRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| name | `java.lang.String` | — |
| module | `java.lang.String` | — |
| description | `java.lang.String` | — |
| expectedVersion | `java.lang.Long` | — |

## config_security_permission_entity_Permission

`com.yunfeil161.ailibrary.config.security.permission.entity.Permission`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| id | `java.lang.Integer` | — |
| code | `java.lang.String` | — |
| name | `java.lang.String` | — |
| module | `java.lang.String` | — |
| description | `java.lang.String` | — |
| version | `java.lang.Long` | — |
| createdAt | `java.time.LocalDateTime` | — |
| updatedAt | `java.time.LocalDateTime` | — |

## identity_application_dto_MemberChangeCancelRequest

`com.yunfeil161.ailibrary.identity.application.dto.MemberChangeCancelRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| expectedVersion | `java.lang.Long` | — |

## identity_application_dto_MemberChangeCreateRequest

`com.yunfeil161.ailibrary.identity.application.dto.MemberChangeCreateRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| applicationType | `com.yunfeil161.ailibrary.identity.application.enums.MemberChangeType` | READER_QUALIFICATION、READER_QUALIFICATION_RESTORE、BORROW_QUOTA_CHANGE、ACCOUNT_REACTIVATION、EMPLOYEE_PROFILE_CHANGE、EMPLOYEE_TRANSFER、EMPLOYEE_SUSPENSION、EMPLOYEE_RESIGNATION、EMPLOYEE_REINSTATEMENT、EMPLOYEE_ROLE_CHANGE |
| reason | `java.lang.String` | — |
| expectedMemberVersion | `java.lang.Long` | — |
| expectedProfileVersion | `java.lang.Long` | — |
| acceptedRuleVersion | `java.lang.String` | — |
| requestedMaxBorrowCount | `java.lang.Integer` | — |
| quotaDurationDays | `java.lang.Integer` | — |
| requestedDepartmentId | `java.lang.Long` | — |
| requestedPositionName | `java.lang.String` | — |
| requestedEmploymentAction | `com.yunfeil161.ailibrary.identity.application.enums.EmploymentChangeAction` | TRANSFER、SUSPEND、RESIGN、REINSTATE |
| requestedRoleId | `java.lang.Long` | — |
| requestedAccountAction | `com.yunfeil161.ailibrary.identity.application.enums.AccountChangeAction` | REACTIVATE |

## identity_application_dto_MemberChangeDecisionRequest

`com.yunfeil161.ailibrary.identity.application.dto.MemberChangeDecisionRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| decision | `com.yunfeil161.ailibrary.identity.application.enums.MemberChangeDecision` | APPROVE、REJECT |
| reason | `java.lang.String` | — |
| expectedVersion | `java.lang.Long` | — |

## identity_application_dto_MemberChangeQueryRequest

`com.yunfeil161.ailibrary.identity.application.dto.MemberChangeQueryRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| id | `java.lang.Long` | — |
| applicationNo | `java.lang.String` | — |
| applicantMemberId | `java.lang.Long` | — |
| targetMemberId | `java.lang.Long` | — |
| applicantMemberType | `com.yunfeil161.ailibrary.common.enums.MemberType` | READER、EMPLOYEE |
| applicationType | `com.yunfeil161.ailibrary.identity.application.enums.MemberChangeType` | READER_QUALIFICATION、READER_QUALIFICATION_RESTORE、BORROW_QUOTA_CHANGE、ACCOUNT_REACTIVATION、EMPLOYEE_PROFILE_CHANGE、EMPLOYEE_TRANSFER、EMPLOYEE_SUSPENSION、EMPLOYEE_RESIGNATION、EMPLOYEE_REINSTATEMENT、EMPLOYEE_ROLE_CHANGE |
| status | `com.yunfeil161.ailibrary.identity.application.enums.MemberChangeStatus` | PENDING、APPROVED、REJECTED、CANCELLED、EXPIRED |
| reviewerMemberId | `java.lang.Long` | — |
| createdFrom | `java.time.LocalDateTime` | — |
| createdTo | `java.time.LocalDateTime` | — |
| page | `java.lang.Integer` | — |
| pageSize | `java.lang.Integer` | — |

## identity_dto_DepartmentQueryRequest

`com.yunfeil161.ailibrary.identity.dto.DepartmentQueryRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| id | `java.lang.Long` | — |
| name | `java.lang.String` | — |
| page | `java.lang.Integer` | — |
| pageSize | `java.lang.Integer` | — |

## identity_dto_DepartmentUpdateRequest

`com.yunfeil161.ailibrary.identity.dto.DepartmentUpdateRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| name | `java.lang.String` | — |
| description | `java.lang.String` | — |
| descriptionProvided | `boolean` | — |
| status | `com.yunfeil161.ailibrary.common.enums.DepartmentStatus` | ACTIVE、DISABLED |
| expectedVersion | `java.lang.Integer` | — |

## identity_dto_MemberQueryRequest

`com.yunfeil161.ailibrary.identity.dto.MemberQueryRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| scene | `java.lang.String` | — |
| id | `java.lang.Long` | — |
| memberType | `com.yunfeil161.ailibrary.common.enums.MemberType` | READER、EMPLOYEE |
| roleId | `java.lang.Long` | — |
| name | `java.lang.String` | — |
| email | `java.lang.String` | — |
| phone | `java.lang.String` | — |
| passwordHash | `java.lang.String` | — |
| accountStatus | `com.yunfeil161.ailibrary.common.enums.AccountStatus` | ACTIVE、DISABLED |
| qualificationStatus | `com.yunfeil161.ailibrary.common.enums.QualificationStatus` | NOT_APPLIED、PENDING、APPROVED、REJECTED、SUSPENDED、NOT_APPLICABLE |
| emailVerifiedAt | `java.time.LocalDateTime` | — |
| acceptedRuleVersion | `java.lang.String` | — |
| borrowedBookCount | `java.lang.Integer` | — |
| permanentBorrowLimit | `java.lang.Integer` | — |
| temporaryBorrowLimit | `java.lang.Integer` | — |
| temporaryQuotaExpiresAt | `java.time.LocalDateTime` | — |
| version | `java.lang.Long` | — |
| createdAt | `java.time.LocalDateTime` | — |
| updatedAt | `java.time.LocalDateTime` | — |
| readerInterestId | `java.lang.Long` | — |
| interestName | `java.lang.String` | — |
| interests | `java.lang.String` | — |
| readerSettingsId | `java.lang.Long` | — |
| personalizationEnabled | `java.lang.Boolean` | — |
| recommendationNotificationsEnabled | `java.lang.Boolean` | — |
| employeeProfileId | `java.lang.Long` | — |
| employeeNo | `java.lang.String` | — |
| departmentId | `java.lang.Long` | — |
| positionName | `java.lang.String` | — |
| employmentStatus | `com.yunfeil161.ailibrary.common.enums.EmploymentStatus` | ACTIVE、SUSPENDED、LEFT |
| hiredOn | `java.time.LocalDate` | — |
| leftOn | `java.time.LocalDate` | — |
| page | `java.lang.Integer` | — |
| pageSize | `java.lang.Integer` | — |

## identity_dto_MemberWriteRequest

`com.yunfeil161.ailibrary.identity.dto.MemberWriteRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| memberType | `com.yunfeil161.ailibrary.common.enums.MemberType` | READER、EMPLOYEE |
| roleId | `java.lang.Long` | — |
| name | `java.lang.String` | — |
| email | `java.lang.String` | — |
| phone | `java.lang.String` | — |
| passwordHash | `java.lang.String` | — |
| accountStatus | `com.yunfeil161.ailibrary.common.enums.AccountStatus` | ACTIVE、DISABLED |
| qualificationStatus | `com.yunfeil161.ailibrary.common.enums.QualificationStatus` | NOT_APPLIED、PENDING、APPROVED、REJECTED、SUSPENDED、NOT_APPLICABLE |
| emailVerifiedAt | `java.time.LocalDateTime` | — |
| acceptedRuleVersion | `java.lang.String` | — |
| borrowedBookCount | `java.lang.Integer` | — |
| permanentBorrowLimit | `java.lang.Integer` | — |
| temporaryBorrowLimit | `java.lang.Integer` | — |
| temporaryQuotaExpiresAt | `java.time.LocalDateTime` | — |
| interests | `java.util.List<java.lang.String>` | — |
| personalizationEnabled | `java.lang.Boolean` | — |
| recommendationNotificationsEnabled | `java.lang.Boolean` | — |
| employeeNo | `java.lang.String` | — |
| departmentId | `java.lang.Long` | — |
| positionName | `java.lang.String` | — |
| employmentStatus | `com.yunfeil161.ailibrary.common.enums.EmploymentStatus` | ACTIVE、SUSPENDED、LEFT |
| hiredOn | `java.time.LocalDate` | — |
| leftOn | `java.time.LocalDate` | — |
| expectedVersion | `java.lang.Long` | — |

## identity_entity_Dept

`com.yunfeil161.ailibrary.identity.entity.Dept`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| id | `java.lang.Integer` | — |
| code | `java.lang.String` | — |
| name | `java.lang.String` | — |
| status | `com.yunfeil161.ailibrary.common.enums.DepartmentStatus` | ACTIVE、DISABLED |
| description | `java.lang.String` | — |
| version | `java.lang.Integer` | — |
| createdAt | `java.time.LocalDateTime` | — |
| updatedAt | `java.time.LocalDateTime` | — |

## operations_OperationJobQuery

`com.yunfeil161.ailibrary.operations.OperationJobQuery`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| jobId | `java.lang.String` | — |
| type | `java.lang.String` | — |
| state | `java.lang.String` | — |
| page | `java.lang.Integer` | — |
| pageSize | `java.lang.Integer` | — |

## policy_dto_AdmissionPolicyParameters

`com.yunfeil161.ailibrary.policy.dto.AdmissionPolicyParameters`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| requireVerifiedEmail | `java.lang.Boolean` | — |
| requiredFields | `java.util.List<com.yunfeil161.ailibrary.policy.dto.AdmissionPolicyParameters$RequiredField>` | — |
| defaultMaxBorrowCount | `java.lang.Integer` | — |

## policy_dto_BorrowingPolicyParameters

`com.yunfeil161.ailibrary.policy.dto.BorrowingPolicyParameters`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| defaultBorrowDays | `java.lang.Integer` | — |
| maxBorrowDays | `java.lang.Integer` | — |
| maxRenewCount | `java.lang.Integer` | — |
| defaultRenewDays | `java.lang.Integer` | — |
| maxRenewDays | `java.lang.Integer` | — |
| blockBorrowWhenOverdue | `java.lang.Boolean` | — |
| applicationValidityDays | `java.lang.Integer` | — |

## policy_dto_PolicyPublishRequest

`com.yunfeil161.ailibrary.policy.dto.PolicyPublishRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| expectedVersion | `java.lang.Long` | — |
| expectedCurrentPolicyId | `java.lang.Long` | — |
| currentPolicyIdProvided | `boolean` | — |

## policy_dto_PolicyQueryRequest

`com.yunfeil161.ailibrary.policy.dto.PolicyQueryRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| id | `java.lang.Long` | — |
| type | `java.lang.String` | — |
| status | `java.lang.String` | — |
| title | `java.lang.String` | — |
| policyVersion | `java.lang.Long` | — |
| page | `java.lang.Integer` | — |
| pageSize | `java.lang.Integer` | — |

## policy_dto_PolicyWriteRequest

`com.yunfeil161.ailibrary.policy.dto.PolicyWriteRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| type | `java.lang.String` | — |
| title | `java.lang.String` | — |
| content | `java.lang.String` | — |
| admissionParameters | `com.yunfeil161.ailibrary.policy.dto.AdmissionPolicyParameters` | — |
| borrowingParameters | `com.yunfeil161.ailibrary.policy.dto.BorrowingPolicyParameters` | — |
| expectedVersion | `java.lang.Long` | — |

## policy_dto_RuleDecisionRequest

`com.yunfeil161.ailibrary.policy.dto.RuleDecisionRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| ruleVersion | `java.lang.String` | — |
| decision | `java.lang.String` | — |
| expectedMemberVersion | `java.lang.Long` | — |

## recommendation_dto_BookTagsRequest

`com.yunfeil161.ailibrary.recommendation.dto.BookTagsRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| expectedVersion | `java.lang.Long` | — |
| tagIds | `java.util.List<java.lang.Long>` | — |

## recommendation_dto_FeedbackRequest

`com.yunfeil161.ailibrary.recommendation.dto.FeedbackRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| kind | `java.lang.String` | — |

## recommendation_dto_TagsRequest

`com.yunfeil161.ailibrary.recommendation.dto.TagsRequest`

| 字段 | Java类型 | 可选枚举 |
| --- | --- | --- |
| tagIds | `java.util.List<java.lang.Long>` | — |

