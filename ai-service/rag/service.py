"""知识库更新与检索的公共服务入口，供CLI及未来HTTP适配层复用。"""


class KnowledgeService:
    def __init__(self, store):
        self.store = store

    def get_status(self):
        """返回当前快照generation，作为下一次更新的预期版本。"""
        return self.store.status()

    def update_knowledge(self, manifest_path, *, expected_generation=None):
        """发布完整知识清单；相同内容幂等，移除的资料在新快照中不可检索。

        expected_generation=None表示不检查版本；空字符串表示必须为首次导入。
        线上适配层应要求传入版本并校验管理员权限，不能开放任意文件路径上传。
        """
        if expected_generation is not None and not isinstance(expected_generation, str):
            raise ValueError("预期知识库版本必须是字符串")
        return self.store.ingest(manifest_path, expected_generation=expected_generation)

    def search(self, question, **filters):
        return self.store.query(question, **filters)
