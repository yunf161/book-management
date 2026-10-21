"""一个业务智能体，按Java授权的Skill规划；绝不在Python中发起业务写请求。"""

from copy import deepcopy
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path

from agents.read_assistant import ReadAssistant
from tools.backend import ToolError
from tools.registry import validate_arguments
from agents.dialogue import CHAT_TOOL, should_chat, stream_dialogue


class BusinessAgent(ReadAssistant):
    def __init__(self, client, tools):
        super().__init__(client, tools)
        self.prompt = (Path(__file__).resolve().parents[1] / "prompts/business.md").read_text(encoding="utf-8")
        self.proposal = None

    def stream(self, question):
        if not isinstance(question, str) or not question.strip() or len(question) > 2000:
            raise ValueError("业务请求须为1到2000字符")
        self.proposal = None
        keep = max(0, self.client.settings.max_turns - 1)
        history = self.history[-2 * keep:] if keep else []
        today = datetime.now(timezone(timedelta(hours=8))).date().isoformat()
        base = [{"role": "system", "content": self.prompt},
                {"role": "user", "content": json.dumps({"question": question, "today": today,
                    "recent_user_questions": [m["content"] for m in history if m["role"] == "user"],
                    "previous_sources": self.sources}, ensure_ascii=False)}]
        # 第一阶段只有只读查询。任何401/403直接终止，不改查其他接口绕过。
        plan = self.client.plan_tools(base, [*self.tools.definitions, CHAT_TOOL])
        calls = plan.get("tool_calls", [])
        if not isinstance(calls, list) or len(calls) > 4:
            raise ToolError("本轮只读工具过多")
        # 日常聊天不读取Skill，不查询业务对象，更不能沿用上轮的待确认草稿。
        if should_chat(calls, question):
            parts = []
            for text in stream_dialogue(self.client, self.prompt, history, question):
                parts.append(text)
                yield text
            answer = "".join(parts)
            self._history = history + [{"role": "user", "content": question},
                                       {"role": "assistant", "content": answer}]
            self._sources = []
            return
        # Skill和权限来自Java，不接受模型/浏览器伪造Skill列表。
        skills = self.tools.backend.business_skills()
        if not isinstance(skills, list) or len(skills) > 30:
            raise ToolError("Java返回的Skill列表错误")
        definitions = [row["tool"] for row in skills]
        sources = []
        validated = []
        for call in calls:
            function = call.get("function") if isinstance(call, dict) else None
            if not isinstance(function, dict):
                raise ToolError("只读工具格式错误")
            definition = next((d for d in self.tools.definitions if d["function"]["name"] == function.get("name")), None)
            if definition is None:
                raise ToolError("未开放的只读工具")
            arguments = function.get("arguments", {})
            if isinstance(arguments, str):
                arguments = json.loads(arguments)
            validate_arguments(definition["function"]["parameters"], arguments)
            validated.append((function["name"], arguments))
        for name, arguments in validated:
            data = self.tools.execute(name, arguments)
            source = {"source_id": "T" + str(self._next_source + len(sources)), "tool": name, "data": data}
            if len(json.dumps(source, ensure_ascii=False)) > 10000:
                raise ToolError("业务查询结果过大，请缩小范围")
            sources.append(source)
        context = {"question": question, "today": today, "sources": sources,
                   "recent_user_questions": [m["content"] for m in history if m["role"] == "user"]}
        if len(json.dumps(context, ensure_ascii=False)) > 22000:
            raise ToolError("业务上下文过大，请缩小范围")
        if definitions:
            decision = self.client.plan_tools([
                {"role": "system", "content": self.prompt + "\n现在仅选择一个待确认Skill，不需要的可不调用。"},
                {"role": "user", "content": json.dumps(context, ensure_ascii=False)}], definitions)
            proposals = decision.get("tool_calls", [])
        else:
            proposals = []
        if not isinstance(proposals, list) or len(proposals) > 1:
            raise ToolError("每轮只能生成一个待确认操作")
        if proposals:
            function = proposals[0].get("function", {})
            chosen = next((s for s in skills if s["code"] == function.get("name")), None)
            if chosen is None:
                raise ToolError("模型选择了未授权Skill")
            arguments = function.get("arguments", {})
            if isinstance(arguments, str):
                arguments = json.loads(arguments)
            validate_arguments(chosen["tool"]["function"]["parameters"], arguments)
            self.proposal = {"skill": chosen["code"], "arguments": arguments}
            answer = "已生成“" + chosen["title"] + "”操作草稿。请核对下方参数后确认执行，当前尚未执行。"
        else:
            answer = "本轮未形成可执行草稿。请明确要办理的操作、对象和必要参数；我不会猜测ID或版本，也不会直接执行。"
        yield answer
        self._history = history + [{"role": "user", "content": question}, {"role": "assistant", "content": answer}]
        self._sources = deepcopy(sources)
        self._next_source += len(sources)
