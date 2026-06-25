"""搜索和 Embedding 测试接口的数据结构。

当前 SearchRequest 只包含 question。
/search/demo 会用它做向量相似度演示；
/embedding/test 会用它测试单条文本 Embedding。
"""

from pydantic import BaseModel


class SearchRequest(BaseModel):
    """搜索类接口请求体。

    字段：
    - question：用户输入的查询文本。
    """

    question: str
