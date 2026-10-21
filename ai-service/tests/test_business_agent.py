"""业务智能体只生成一个待确认Skill，不执行写请求、不猜身份、不绕过查询拒绝。"""

import json
import unittest
from config.settings import Settings
from agents.business import BusinessAgent
from tools.backend import ToolError
from tools.registry import ReadTools, validate_arguments


def skill():
    return {"code": "plan_create", "title": "新建本人阅读计划", "tool": {
        "type": "function", "function": {"name": "plan_create", "description": "仅生成草稿",
            "parameters": {"type": "object", "properties": {"name": {"type": "string", "maxLength": 80}},
                           "required": ["name"], "additionalProperties": False}}}}


def call(name, arguments):
    return {"function": {"name": name, "arguments": arguments}}


class Backend:
    def __init__(self):
        self.reads = []
        self.error = None

    def business_skills(self):
        self.reads.append("skills")
        return [skill()]

    def search_books(self, arguments):
        self.reads.append(arguments)
        if self.error:
            raise self.error
        return {"rows": [{"id": 1, "title": "Java"}], "total": 1}

    def business_query(self, name, arguments):
        self.reads.append((name, arguments))
        return {"version": 3, "name": "本人计划"}


class Model:
    settings = Settings()

    def __init__(self, reads=None, proposals=None):
        self.plans = [reads or [], proposals if proposals is not None else [call("plan_create", {"name": "Java学习计划"})]]
        self.requests = []

    def plan_tools(self, messages, tools):
        self.requests.append((messages, tools))
        return {"tool_calls": self.plans.pop(0)}


class BusinessAgentTests(unittest.TestCase):
    def agent(self, model=None):
        self.backend = Backend()
        self.model = model or Model()
        return BusinessAgent(self.model, ReadTools(self.backend, business=True))

    def test_creates_only_proposal_without_executing(self):
        agent = self.agent()
        answer = "".join(agent.stream("新建一个Java学习计划"))
        self.assertEqual(agent.proposal, {"skill": "plan_create", "arguments": {"name": "Java学习计划"}})
        self.assertIn("尚未执行", answer)
        self.assertEqual(self.backend.reads, ["skills"])
        self.assertEqual(len(agent.history), 2)

    def test_read_first_then_propose_with_sources(self):
        agent = self.agent(Model([call("search_books", {"title": "Java"})]))
        list(agent.stream("查询Java图书并为我建计划"))
        self.assertEqual(agent.sources[0]["tool"], "search_books")
        self.assertEqual(agent.sources[0]["source_id"], "T1")
        self.assertIn('"title": "Java"', self.model.requests[1][0][1]["content"])

    def test_unknown_or_multiple_actions_are_rejected(self):
        for proposals in [[call("delete_member", {})], [call("plan_create", {"name": "A"})] * 2]:
            agent = self.agent(Model(proposals=proposals))
            with self.assertRaises(ToolError):
                list(agent.stream("执行"))
            self.assertEqual(agent.history, [])
            self.assertIsNone(agent.proposal)

    def test_forged_identity_and_missing_fields_rejected(self):
        for arguments in [{"name": "A", "memberId": 2}, {"name": "A", "url": "http://evil"}, {}, {"name": "x" * 81}]:
            agent = self.agent(Model(proposals=[call("plan_create", arguments)]))
            with self.assertRaises(ToolError):
                list(agent.stream("办理"))

    def test_read_forbidden_aborts_before_planning_action(self):
        agent = self.agent(Model([call("search_books", {"title": "Java"})]))
        self.backend.error = ToolError("forbidden", 403)
        with self.assertRaises(ToolError):
            list(agent.stream("查询并办理"))
        self.assertEqual(len(self.model.requests), 1)
        self.assertEqual(agent.history, [])

    def test_missing_decision_asks_for_details_without_faking_success(self):
        agent = self.agent(Model(proposals=[]))
        answer = "".join(agent.stream("帮我办理"))
        self.assertIsNone(agent.proposal)
        self.assertIn("未形成可执行草稿", answer)

    def test_business_queries_are_not_in_consultation_tool_list(self):
        names = {item["function"]["name"] for item in ReadTools(Backend()).definitions}
        self.assertNotIn("get_admin_borrow_applications", names)
        business = {item["function"]["name"] for item in ReadTools(Backend(), business=True).definitions}
        self.assertIn("get_my_reading_plan", business)
        self.assertNotIn("confirm_action", business)

    def test_date_price_and_integer_types_are_checked(self):
        properties = {"date": {"type": "string", "maxLength": 10, "format": "date"},
                      "price": {"type": "number", "minimum": 0, "maximum": 100},
                      "version": {"type": "integer", "minimum": 0}}
        schema = {"properties": properties}
        for arguments in [{"date": "2026-02-30"}, {"price": -1}, {"price": float("nan")}, {"version": True}]:
            with self.assertRaises(ToolError):
                validate_arguments(schema, arguments)
        validate_arguments(schema, {"date": "2026-10-18", "price": 1.2, "version": 0})

    def test_stringified_json_action_is_validated(self):
        agent = self.agent(Model(proposals=[call("plan_create", json.dumps({"name": "计划"}))]))
        list(agent.stream("新建计划"))
        self.assertEqual(agent.proposal["arguments"]["name"], "计划")

    def test_read_plan_identity_is_not_accepted(self):
        agent = self.agent(Model([call("get_my_reading_plan", {"memberId": 999})]))
        with self.assertRaises(ToolError):
            list(agent.stream("查他人计划"))
        self.assertEqual(self.backend.reads, ["skills"])


if __name__ == "__main__":
    unittest.main()


class MissingOwnPlanTests(unittest.TestCase):
    def test_missing_plan_is_a_fact_but_forbidden_is_not_hidden(self):
        from unittest.mock import patch
        from config.backend_settings import BackendSettings
        from tools.backend import LibraryReadClient
        backend = LibraryReadClient(BackendSettings(), "test-token")
        with patch.object(backend, "_get", side_effect=ToolError("missing", 404)):
            self.assertFalse(backend.business_query("get_my_reading_plan", {})["exists"])
        with patch.object(backend, "_get", side_effect=ToolError("forbidden", 403)):
            with self.assertRaises(ToolError):
                backend.business_query("get_my_reading_plan", {})


class OwnProfileToolTests(unittest.TestCase):
    def test_profile_is_minimized_and_never_accepts_another_member(self):
        from unittest.mock import patch
        from config.backend_settings import BackendSettings
        from tools.backend import LibraryReadClient
        backend = LibraryReadClient(BackendSettings(), "test-token")
        with patch.object(backend, "_my_member", return_value={"version": 7, "profileVersion": 2, "email": "private", "passwordHash": "secret", "id": 999}):
            result = ReadTools(backend, business=True).execute("get_my_profile", {})
        self.assertEqual(result, {"version": 7, "profileVersion": 2})
        with self.assertRaises(ToolError):
            ReadTools(backend, business=True).execute("get_my_profile", {"memberId": 999})
