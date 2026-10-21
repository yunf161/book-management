"""模型可见工具白名单：参数严格校验，只暴露业务GET和受限知识检索。"""

from copy import deepcopy
from datetime import date
import math
from tools.backend import ToolError


def tool(name, description, properties, required=()):
    return {"type":"function","function":{"name":name,"description":description,
            "parameters":{"type":"object","properties":properties,"required":list(required),"additionalProperties":False}}}


INTEGER={"type":"integer","minimum":1,"maximum":9223372036854775807}
TEXT={"type":"string","minLength":1,"maxLength":200}
PAGE={"type":"integer","minimum":1,"maximum":10000}
SIZE={"type":"integer","minimum":1,"maximum":10}
BOOLEAN={"type":"boolean"}
TOOLS=[
    tool("search_books","查询启用书目、分类和实时可借副本数量。书名用title，综合关键词用keyword，最多每页10本；不返回停用书目。",
         {"id":INTEGER,"keyword":TEXT,"title":TEXT,"author":TEXT,"isbn":TEXT,"publisher":TEXT,"categoryId":INTEGER,"availableOnly":BOOLEAN,"page":PAGE,"pageSize":SIZE}),
    tool("get_current_policy","从Java查询当前有效政策正文和参数，所有现行借阅规则以此为准；BORROWING借阅政策，ADMISSION准入政策。",
         {"type":{"type":"string","enum":["BORROWING","ADMISSION"]}}),
    tool("get_my_borrow_records","查询当前登录用户本人的借阅记录；不能指定成员或查询别人，不支持馆员管理范围。",
         {"id":INTEGER,"bookId":INTEGER,"bookTitle":TEXT,"isbn":TEXT,"copyBarcode":TEXT,
          "status":{"type":"string","enum":["BORROWING","OVERDUE","RETURNED","LOST"]},
          "currentOnly":BOOLEAN,"overdueOnly":BOOLEAN,"page":PAGE,"pageSize":SIZE}),
    tool("get_my_quota","查询当前登录用户本人的有效额度、已借数量、剩余额度及资格/账号状态；不能指定成员；额度充足不代表最终允许借阅。",{}),
    tool("search_knowledge","检索本地通用知识或图书主题介绍。现行政策必须调用get_current_policy，不用本地未同步政策回答现行规则。",
         {"question":{"type":"string","minLength":1,"maxLength":1000},
          "kind":{"type":"string","enum":["general","book_intro"]}},("question","kind")),
]


def validate_arguments(schema, arguments):
    if not isinstance(arguments,dict):
        raise ToolError("工具参数必须为JSON对象")
    properties=schema["properties"]
    if set(arguments)-set(properties):
        raise ToolError("工具包含未开放参数；不能传入成员、角色、令牌或URL")
    if set(schema.get("required",[]))-set(arguments):
        raise ToolError("工具缺少必填参数")
    for key,value in arguments.items():
        rule=properties[key]
        kind=rule["type"]
        if kind=="integer" and (isinstance(value,bool) or not isinstance(value,int) or not rule.get("minimum",1)<=value<=rule.get("maximum",2**63-1)):
            raise ToolError("工具整数参数超出范围："+key)
        if kind=="boolean" and not isinstance(value,bool):
            raise ToolError("工具布尔参数格式错误："+key)
        if kind=="number" and (type(value) not in (int,float) or not math.isfinite(value) or not rule.get("minimum",0)<=value<=rule.get("maximum",float("inf"))):
            raise ToolError("工具数字参数超出范围："+key)
        if kind=="string" and (not isinstance(value,str) or not value.strip() or len(value)>rule.get("maxLength",200)):
            raise ToolError("工具字符串参数格式错误："+key)
        if "enum" in rule and value not in rule["enum"]:
            raise ToolError("工具枚举参数格式错误："+key)
        if rule.get("format")=="date":
            try:
                if date.fromisoformat(value).isoformat()!=value:
                    raise ValueError()
            except (ValueError,TypeError):
                raise ToolError("工具日期格式错误："+key)


BUSINESS_READS = [
    tool("get_my_profile", "查询本人资料版本及资格/在职状态，用于本人变更申请；不接受成员ID。", {}),
    tool("get_my_reading_plan", "查询本人阅读计划及当前version；不存在时请先创建。", {}),
    tool("get_my_plan_items", "查询本人阅读条目与当前状态。", {"bookId": INTEGER, "page": PAGE, "pageSize": SIZE}),
    tool("get_my_borrow_applications", "查询本人借阅/续借申请ID、版本和状态。", {"id": INTEGER, "bookId": INTEGER, "page": PAGE, "pageSize": SIZE}),
    tool("get_admin_borrow_applications", "馆员查询待审批申请；无权限时不能通过其他接口绕过。", {"id": INTEGER, "bookId": INTEGER, "status": TEXT, "page": PAGE, "pageSize": SIZE}),
    tool("get_admin_borrow_records", "馆员查询借阅记录ID、版本和状态以办理归还。", {"id": INTEGER, "bookId": INTEGER, "status": TEXT, "page": PAGE, "pageSize": SIZE}),
    tool("get_book_categories", "查询分类ID、编码、名称，不能猜测categoryId。", {"id": INTEGER, "code": TEXT, "name": TEXT, "page": PAGE, "pageSize": SIZE}),
    tool("get_book_copies", "馆员查询具体副本ID、条码和状态。", {"id": INTEGER, "bookId": INTEGER, "barcode": TEXT, "page": PAGE, "pageSize": SIZE}),
    tool("get_my_ai_actions", "查询本人最近的AI操作实际执行状态；EXECUTING/UNKNOWN不能当成成功。", {"actionId": TEXT, "state": TEXT, "page": PAGE, "pageSize": SIZE}),
]


class ReadTools:
    def __init__(self, backend, knowledge=None, business=False):
        self.backend=backend
        self.knowledge=knowledge
        self.business=business

    @property
    def definitions(self):
        return deepcopy([t for t in TOOLS if self.knowledge is not None or t['function']['name']!='search_knowledge'] + (BUSINESS_READS if self.business else []))

    def execute(self,name,arguments):
        definition=next((t for t in self.definitions if t['function']['name']==name),None)
        if definition is None:
            raise ToolError("未授权或不存在的工具")
        validate_arguments(definition['function']['parameters'],arguments)
        if name=='search_books':
            return self.backend.search_books(arguments)
        if name=='get_current_policy':
            return self.backend.current_policy(arguments.get('type','BORROWING'))
        if name=='get_my_borrow_records':
            return self.backend.my_records(arguments)
        if name=='get_my_quota':
            return self.backend.my_quota()
        if name=='search_knowledge':
            hits=self.knowledge.search(arguments['question'],kind=arguments['kind'],demo=False)
            return {'hits':hits,'note':'仅通用知识，不能证明现行政策、库存或个人状态。'}
        if self.business and name in {t['function']['name'] for t in BUSINESS_READS}:
            return self.backend.business_query(name,arguments)
        raise ToolError('未开放工具')
