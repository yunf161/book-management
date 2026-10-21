"""日常交流不触碰业务工具，失败/取消不提交历史，业务查询仍要求本轮来源。"""

import unittest
from unittest.mock import Mock

from agents.business import BusinessAgent
from agents.dialogue import CHAT_NAME, is_dialogue
from agents.read_assistant import ReadAssistant
from clients.ollama_client import OllamaError
from config.settings import Settings
from tools.backend import ToolError
from tools.registry import ReadTools


def chat(arguments=None):
    return {"function": {"name": CHAT_NAME, "arguments": {} if arguments is None else arguments}}


class Model:
    settings = Settings()

    def __init__(self, answer="你好！我是阅知的AI馆员，也可以陪你聊聊日常和阅读。"):
        self.answer = answer
        self.plans = []
        self.messages = []

    def plan_tools(self, messages, tools):
        self.plans.append((messages, tools))
        return {"tool_calls": [chat()]}

    def stream_chat(self, messages):
        self.messages.append(messages)
        yield self.answer


class DialogueTests(unittest.TestCase):
    def test_greeting_with_quota_request_still_executes_real_read_tool(self):
        backend, model = Mock(), Model()
        backend.my_quota.return_value = {"remainingBorrowCount": 4}
        model.plan_tools = Mock(return_value={"tool_calls": [
            {"function": {"name": "get_my_quota", "arguments": {}}}
        ]})
        model.answer = "你好，你的剩余额度是4本[T1]。"
        agent = ReadAssistant(model, ReadTools(backend))
        self.assertIn("[T1]", "".join(agent.stream("你好，帮我查一下额度")))
        backend.my_quota.assert_called_once_with()
        self.assertEqual(agent.sources[0]["tool"], "get_my_quota")

    def test_greetings_and_general_topics_work_in_both_agents_without_backend(self):
        for agent_type in (ReadAssistant, BusinessAgent):
            for question in ("你好", "请介绍一下自己", "谢谢", "今天心情不太好，陪我聊聊", "什么是数据库", "帮我写一句读书寄语"):
                with self.subTest(agent=agent_type.__name__, question=question):
                    backend, model = Mock(), Model()
                    agent = agent_type(model, ReadTools(backend, business=agent_type is BusinessAgent))
                    self.assertIn("阅知", "".join(agent.stream(question)))
                    self.assertEqual(backend.mock_calls, [])
                    self.assertEqual(agent.sources, [])
                    self.assertEqual(len(agent.history), 2)
                    if isinstance(agent, BusinessAgent):
                        self.assertIsNone(agent.proposal)

    def test_followup_keeps_conversation_but_clears_old_sources_and_proposal(self):
        model = Model()
        agent = BusinessAgent(model, ReadTools(Mock(), business=True))
        agent.proposal = {"skill": "old"}
        agent._sources = [{"source_id": "T1", "data": {"secret": "old"}}]
        list(agent.stream("你好"))
        model.answer = "当然，你想聊些什么？"
        list(agent.stream("继续聊聊"))
        self.assertEqual(len(agent.history), 4)
        self.assertEqual(len(model.messages[-1]), 4)
        self.assertEqual(agent.sources, [])
        self.assertIsNone(agent.proposal)
        self.assertNotIn("old", str(model.messages))

    def test_model_can_reply_without_emitting_internal_chat_tool(self):
        for agent_type in (ReadAssistant, BusinessAgent):
            model, backend = Model(), Mock()
            model.plan_tools = Mock(return_value={"tool_calls": []})
            agent = agent_type(model, ReadTools(backend, business=agent_type is BusinessAgent))
            self.assertIn("阅知", "".join(agent.stream("你好，请介绍一下自己")))
            self.assertEqual(backend.mock_calls, [])

    def test_no_fake_citations_or_empty_answer_are_saved(self):
        for answer in ("", "库存有三本[T1]", "根据资料[S3]"):
            agent = ReadAssistant(Model(answer), ReadTools(Mock()))
            with self.assertRaises(OllamaError):
                list(agent.stream("你好"))
            self.assertEqual(agent.history, [])

    def test_cancel_does_not_commit(self):
        agent = ReadAssistant(Model(), ReadTools(Mock()))
        stream = agent.stream("你好")
        next(stream)
        stream.close()
        self.assertEqual(agent.history, [])

    def test_dialogue_signal_has_no_business_parameters_or_mixed_calls(self):
        self.assertTrue(is_dialogue([chat("{}")]))
        for calls in ([chat({"memberId": 1})], [chat("not-json")],
                      [chat(), {"function": {"name": "get_my_quota", "arguments": {}}}]):
            with self.assertRaises(ToolError):
                is_dialogue(calls)

    def test_failed_model_does_not_commit(self):
        model = Model()
        model.stream_chat = Mock(side_effect=OllamaError("model failed"))
        agent = BusinessAgent(model, ReadTools(Mock(), business=True))
        with self.assertRaises(OllamaError):
            list(agent.stream("你好"))
        self.assertEqual(agent.history, [])
        self.assertIsNone(agent.proposal)
