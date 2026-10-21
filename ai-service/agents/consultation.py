"""RAG咨询闭环：新鲜检索、来源校验与完整成功后提交会话。"""

from copy import deepcopy
import json
import re
import time
from pathlib import Path

from clients.ollama_client import OllamaError

PROMPT_PATH = Path(__file__).resolve().parents[1] / "prompts" / "consultation.md"
CITATION = re.compile(r"\[S(\d+)\]")
UNSUPPORTED_RESPONSE = "当前未连接业务查询或写操作接口，无法查询个人资料、实时库存或办理借阅等操作。请通过图书馆业务页面办理；不要在此提供成员ID、密码等个人凭证。"
INSUFFICIENT_RESPONSE = "本轮检索资料没有给出足够依据，无法确认这个问题。请补充具体问题或有效资料。"



def requires_business_interface(question):
    """识别明确的个人查询/代办请求，直接返回能力说明，不是权限鉴定。

    仅覆盖已列出的常见表达；其他表达仍受提示词与引用检查约束。
    后续接入Java工具时，权限与业务校验仍必须在Java实现。
    """
    patterns = (
        r"(?:帮我|为我|替我|给我).{0,12}(?:办理|提交|执行|续借|借书|归还|修改|审批|保存)",
        r"(?:查询|查看|查一下|查查|看看|计算).{0,15}(?:我|本人).{0,20}(?:额度|资格|记录|状态|已借)",
        r"(?:我|本人).{0,8}(?:目前|现在|当前|还剩|剩余).{0,12}(?:额度|借阅|资格)",
    )
    return any(re.search(pattern, question) for pattern in patterns)


class ConsultationSession:
    def __init__(self, client, knowledge, *, demo=False, max_evidence_chars=5000):
        if max_evidence_chars < 100:
            raise ValueError("证据预算不能小于100字符")
        self.client = client
        self.knowledge = knowledge
        self.demo = demo
        self.max_evidence_chars = max_evidence_chars
        self.system_prompt = PROMPT_PATH.read_text(encoding="utf-8").strip()
        if not self.system_prompt:
            raise ValueError("咨询提示词不能为空")
        self.system_prompt += (
            "\n\n## 边界输出协议\n"
            "要求查询个人数据、实时库存或办理实际业务时，只输出[UNSUPPORTED]，不加其他文字或引用。"
            "程序将它替换成不能查询/操作的能力说明。\n"
            "检索资料无法回答问题时，只输出[INSUFFICIENT]，不加其他文字或引用。"
            "正常有依据的回答仍按上文使用真实引用。"
        )
        self._history = []
        self._sources = []
        self._generation = None
        self._next_source = 1

    @property
    def history(self):
        return deepcopy(self._history)

    @property
    def sources(self):
        """仅返回上一个成功回答使用的证据副本，不表示当前政策版本。"""
        return deepcopy(self._sources)

    def clear(self):
        self._history.clear()
        self._sources.clear()
        self._generation = None
        self._next_source = 1

    def _retrieve(self, question, history):
        # 简短追问使用最近一个用户问题补全主题，不将助手结论用于向量检索。
        query = question
        if history and len(question) < 60 and re.search(r"那|它|这|刚才|上面|还能|几次|多久|多少|为什么|怎么", question):
            previous = next((m["content"] for m in reversed(history) if m["role"] == "user"), "")
            query = "上一问题：" + previous[:500] + "\n当前追问：" + question
        return self.knowledge.search(query, demo=self.demo)

    def stream(self, question):
        """流式文本可能已展示；失败、取消、假引用不会提交历史。"""
        if not isinstance(question, str) or not question.strip() or len(question) > 3000:
            raise ValueError("咨询问题需1～3000字符")
        question = question.strip()
        generation = self.knowledge.get_status()["generation"]
        # 知识已更新时保留原状态直到本轮成功，但生成时不带旧历史。
        history = self.history if generation == self._generation else []
        keep = self.client.settings.max_turns - 1
        history = history[-2 * keep:] if keep else []
        needs_backend = requires_business_interface(question)
        hits = [] if needs_backend else self._retrieve(question, history)
        if self.knowledge.get_status()["generation"] != generation:
            raise OllamaError("知识库在检索期间更新，请重新提问")
        sources, evidence, remaining = [], [], self.max_evidence_chars
        for hit in hits:
            text = hit["text"]
            # 不截断片段以免丢失否定句/限制条件；超过预算的片段直接跳过。
            if len(text) > remaining:
                continue
            label = "S" + str(self._next_source + len(sources))
            sources.append(dict(deepcopy(hit), citation=label))
            evidence.append({"citation": label, "text": text, "metadata": deepcopy(hit["metadata"])})
            remaining -= len(text)
        if needs_backend:
            answer = UNSUPPORTED_RESPONSE
            yield answer
        elif not sources:
            answer = "没有检索到足以支持回答的当前知识。请补充问题或导入有效资料；我不能据此猜测本馆规则、实时库存或个人信息。"
            yield answer
        else:
            payload = json.dumps({"question": question, "retrieved_evidence": evidence}, ensure_ascii=False)
            # 保守字符预算用于裁剪完整历史轮次，不冒充模型精确token计数。
            budget = (self.client.settings.context - self.client.settings.max_tokens) // 2
            base_size = len(self.system_prompt) + len(payload)
            while history and base_size + sum(len(m["content"]) for m in history) > budget:
                history = history[2:]
            if base_size > budget:
                raise ValueError("问题与证据超出当前上下文预算，请缩短问题或增加上下文")
            messages = [{"role": "system", "content": self.system_prompt}, *history,
                        {"role": "user", "content": payload}]
            boundary_tokens = {"[UNSUPPORTED]": UNSUPPORTED_RESPONSE,
                               "[INSUFFICIENT]": INSUFFICIENT_RESPONSE}
            parts, pending, mode = [], "", None
            for text in self.client.stream_chat(messages):
                parts.append(text)
                if mode == "normal":
                    yield text
                elif mode is None:
                    pending += text
                    prefix = pending.lstrip()
                    if any(token.startswith(prefix) for token in boundary_tokens):
                        continue
                    if any(prefix.startswith(token) for token in boundary_tokens):
                        mode = "boundary"
                    else:
                        mode = "normal"
                        yield pending
                        pending = ""
            answer = "".join(parts)
            if answer.strip() in boundary_tokens:
                answer = boundary_tokens[answer.strip()]
                yield answer
            elif mode == "boundary":
                raise OllamaError("模型边界回复格式不正确，本轮未保存")
            elif mode is None and pending:
                yield pending
            if not answer.strip():
                raise OllamaError("模型返回空回答，本轮未保存")
            cited = {"S" + number for number in CITATION.findall(answer)}
            allowed = {source["citation"] for source in sources}
            boundary_reply = answer.strip() in (UNSUPPORTED_RESPONSE, INSUFFICIENT_RESPONSE)
            if not boundary_reply and (not cited or not cited.issubset(allowed)):
                raise OllamaError("回答缺少有效来源或引用了不存在的来源，本轮未保存")
            if self.demo and not boundary_reply and not ("测试资料" in answer and ("非" in answer or "不是" in answer or "不代表" in answer)):
                raise OllamaError("回答未明确区分测试资料和正式政策，本轮未保存")
        # 模型生成期间更新政策/到期时，不将旧版本回答提交为成功会话。
        if self.knowledge.get_status()["generation"] != generation:
            raise OllamaError("知识库在生成期间更新，本轮未保存，请重新提问")
        now = time.time()
        if any(not s["metadata"]["effective_from"] <= now < s["metadata"]["effective_to"] for s in sources):
            raise OllamaError("本轮证据已超过生效区间，请重新提问")
        # 保存原始问题而不是检索JSON，避免后续历史不断积累旧证据。
        self._history = history + [{"role": "user", "content": question},
                                   {"role": "assistant", "content": answer}]
        self._sources = sources
        self._generation = generation
        self._next_source += len(sources)


def format_consultation_sources(sources):
    """只展示已提供给本轮模型的来源，不由模型生成出处。"""
    lines = []
    for source in sources:
        metadata = source["metadata"]
        line = (f"[{source['citation']}] {metadata['title']} | 版本 {metadata['version']} | "
                f"来源 {metadata['source']} | 字符 {metadata['start']}～{metadata['end']} | MD5 {metadata['content_md5']}")
        if metadata["demo"]:
            line += " | 测试资料，非正式政策"
        if len(json.loads(metadata["provenance_json"])) > 1:
            line += " | 全部来源 " + metadata["provenance_json"]
        lines.append(line)
    return "\n".join(lines)
