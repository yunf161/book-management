"""只读Java HTTP适配器：固定路径、拒绝重定向、令牌轮换、响应最小化。"""

import json
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import HTTPRedirectHandler, Request, build_opener


class ToolError(RuntimeError):
    def __init__(self, message, status=None):
        super().__init__(message)
        self.status = status


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # 防止Authorization随着服务重定向泄漏到其他地址。
        return None


class LibraryReadClient:
    PATHS = frozenset(("/books", "/policies/current", "/borrow-records", "/me/permissions", "/members",
                       "/ai/skills", "/ai/actions", "/reading-plans", "/reading-plans/items", "/borrow-applications",
                       "/admin/borrow-applications", "/admin/borrow-records", "/book-categories", "/admin/book-copies"))

    def __init__(self, settings, token=""):
        self.settings = settings
        self._token = self._clean_token(token)
        self._opener = build_opener(NoRedirect())

    @staticmethod
    def _clean_token(token):
        if not isinstance(token, str) or any(c in token for c in "\r\n"):
            raise ValueError("会话令牌格式错误")
        return token.strip()

    def _refresh(self, headers):
        value = headers.get("X-Refreshed-Token")
        if value:
            self._token = self._clean_token(value)

    @property
    def current_token(self):
        """仅供可信HTTP适配层传递轮换令牌，不可加入模型上下文或来源。"""
        return self._token

    def _get(self, path, params=None):
        if path not in self.PATHS:
            raise ToolError("未开放该HTTP查询路径")
        url = self.settings.base_url.rstrip("/") + path
        if params:
            url += "?" + urlencode({k: str(v).lower() if isinstance(v, bool) else v for k,v in params.items() if v is not None})
        headers = {"Accept": "application/json"}
        if self._token:
            headers["Authorization"] = "Bearer " + self._token
        try:
            with self._opener.open(Request(url, headers=headers, method="GET"), timeout=self.settings.timeout) as response:
                self._refresh(response.headers)
                body = response.read(2_000_001)
            if len(body) > 2_000_000:
                raise ToolError("Java响应超过工具容量上限")
            result = json.loads(body)
            if not isinstance(result, dict) or result.get("code") != 200:
                raise ToolError("Java返回了非成功业务响应")
            return result.get("data")
        except HTTPError as exc:
            self._refresh(exc.headers)
            exc.close()
            messages = {401:"登录已失效，请在业务系统重新登录",403:"Java拒绝当前查询：权限不足或尚未接受有效规则",404:"未找到可访问的记录",409:"当前会话或业务状态冲突，请重试",503:"Java依赖服务暂不可用"}
            raise ToolError(messages.get(exc.code, f"Java查询失败（HTTP {exc.code}）"),exc.code) from exc
        except (URLError, TimeoutError, OSError) as exc:
            raise ToolError("无法连接Java服务或查询超时") from exc
        except (ValueError, UnicodeError) as exc:
            raise ToolError("Java响应不是有效JSON") from exc

    @staticmethod
    def _page(data, fields):
        if not isinstance(data,dict) or not isinstance(data.get("rows"),list):
            raise ToolError("Java分页响应格式错误")
        return {"total":data.get("total"),"rows":[{key:row.get(key) for key in fields if key in row} for row in data["rows"]]}

    def search_books(self, params):
        # 第一版工具只查询启用书目，库存来自既有Service，不能由模型计算或猜测。
        query = {"page":1,"pageSize":5,**params,"status":"ACTIVE"}
        data = self._get("/books",query)
        fields=("id","title","subtitle","author","isbn","publisher","publishedDate","edition","language","category","totalCopies","availableCopies","status","tags")
        result=self._page(data,fields)
        for row in result["rows"]:
            if "tags" in row:
                row["tags"]=[{key:tag.get(key) for key in ("id","name")} for tag in row["tags"] or []]
        return {**result,"page":query["page"],"pageSize":query["pageSize"]}

    def current_policy(self, policy_type):
        data=self._get("/policies/current",{"type":policy_type})
        if not isinstance(data,dict) or data.get("status") != "ACTIVE":
            raise ToolError("Java没有返回当前已发布政策")
        result={key:data.get(key) for key in ("id","type","title","content","parameters","policyVersion","ruleVersion","publishedAt")}
        content=result.get("content") or ""
        result["contentTruncated"]=len(content)>6000
        result["content"]=content[:6000]
        return result

    def my_records(self, params):
        # 仅使用读者本人接口，Service强制覆盖memberId；绝不调用admin端。
        query={"page":1,"pageSize":5,**params}
        fields=("id","bookId","title","author","isbn","borrowedAt","dueAt","returnedAt","status","overdueDays","currentOverdueDays","renewCount","lastRenewedAt")
        return {**self._page(self._get("/borrow-records",query),fields),"page":query["page"],"pageSize":query["pageSize"]}

    def _my_member(self):
        # 身份必须取自Java会话摘要，不解码或信任模型传入的JWT/成员ID。
        current=self._get("/me/permissions")
        member_id=current.get("memberId") if isinstance(current,dict) else None
        if not isinstance(member_id,int) or isinstance(member_id,bool) or member_id<=0:
            raise ToolError("Java没有返回有效的当前成员身份")
        row=None
        for member_type in ("READER","EMPLOYEE"):
            try:
                data=self._get("/members",{"id":member_id,"memberType":member_type,"page":1,"pageSize":1})
            except ToolError as exc:
                # 聚合ID查询在类型不匹配时返回404；只换扩展表类型，ID始终是本人。
                # 401/403不能重试其他类型，避免把权限拒绝当作类型不匹配。
                if exc.status == 404:
                    continue
                raise
            rows=data.get("rows") if isinstance(data,dict) else None
            if not isinstance(rows,list):
                raise ToolError("Java成员响应格式错误")
            if rows:
                row=rows[0]
                break
        if row is None or row.get("id") != member_id:
            raise ToolError("未能查询到当前成员本人资料")
        if row.get("memberType") not in ("READER","EMPLOYEE"):
            raise ToolError("当前只开放人类成员的本人借阅咨询")
        return row

    def my_quota(self):
        row = self._my_member()
        fields=("accountStatus","qualificationStatus","borrowedBookCount","permanentBorrowLimit","temporaryBorrowLimit","temporaryQuotaExpiresAt","effectiveBorrowLimit")
        result={key:row.get(key) for key in fields}
        count,limit=result["borrowedBookCount"],result["effectiveBorrowLimit"]
        if any(isinstance(v,bool) or not isinstance(v,int) or v<0 for v in (count,limit)):
            raise ToolError("Java返回的借阅额度数据不完整")
        result["remainingBorrowCount"]=max(0,limit-count)
        result["overLimit"]=count>limit
        result["note"]="剩余数量仅代表额度，是否允许借阅仍由Java检查资格、规则接受情况和逾期等业务条件。"
        return result

    def business_skills(self):
        return self._get("/ai/skills")

    def business_query(self, name, params):
        """路径由代码绑定，模型不能传URL或成员ID；管理查询仍由Java逐项鉴权。"""
        if name == "get_my_profile":
            row = self._my_member()
            # 只给申请规划提供当前版本及可审批字段，不暴露密码、手机号或邮箱。
            fields = ("version", "profileVersion", "acceptedRuleVersion", "accountStatus", "qualificationStatus",
                      "departmentId", "positionName", "employmentStatus", "permanentBorrowLimit")
            return {key: row.get(key) for key in fields if key in row}
        routes = {
            "get_my_reading_plan": "/reading-plans",
            "get_my_plan_items": "/reading-plans/items",
            "get_my_borrow_applications": "/borrow-applications",
            "get_admin_borrow_applications": "/admin/borrow-applications",
            "get_admin_borrow_records": "/admin/borrow-records",
            "get_book_categories": "/book-categories",
            "get_book_copies": "/admin/book-copies",
            "get_my_ai_actions": "/ai/actions",
        }
        if name not in routes:
            raise ToolError("未开放该业务只读查询")
        query = {"page": 1, "pageSize": 5, **params} if name != "get_my_reading_plan" else {}
        try:
            data = self._get(routes[name], query)
        except ToolError as error:
            # 本人尚未创建计划是正常业务事实；401/403和其他资源错误绝不能降级绕过。
            if error.status == 404 and name in ("get_my_reading_plan", "get_my_plan_items"):
                return {"exists": False, "rows": [], "total": 0}
            raise
        if name == "get_my_reading_plan":
            return {key: data.get(key) for key in ("id", "name", "description", "version", "status", "targetBooks", "startDate", "endDate")} if isinstance(data, dict) else None
        # 只传递规划所需字段，去掉成员姓名、邮箱等无关个人资料。
        fields = ("id", "bookId", "bookName", "title", "bookTitle", "applicationType", "borrowRecordId", "version", "status",
                  "requestedDays", "sequenceNo", "code", "name", "parentId", "bookId", "barcode", "location", "state", "actionId", "skill")
        return self._page(data, fields)
