TEST_CASES = [
    {
        "question": "RAG 的英文全称是什么？",
        "expected_keywords": [
            "Retrieval-Augmented Generation",
        ],
        "expected_page": 1,
        "should_reject": False,
    },
    {
        "question": "星河科技的平台由哪些服务组成？",
        "expected_keywords": [
            "SpringBoot",
            "FastAPI",
        ],
        "expected_page": 1,
        "should_reject": False,
    },
    {
        "question": "星河科技的 CEO 是谁？",
        "expected_keywords": [],
        "expected_page": None,
        "should_reject": True,
    },
    {
        "question": "chunk overlap 有什么作用？",
        "expected_keywords": [
            "边界",
            "信息丢失",
        ],
        "expected_page": 1,
        "should_reject": False,
    },
    {
        "question": "top-k 设置太大会有什么问题？",
        "expected_keywords": [
            "无关片段",
            "token",
            "干扰",
        ],
        "expected_page": 1,
        "should_reject": False,
    },
    {
        "question": "RAG 的中文名称是什么？",
        "expected_keywords": [
            "检索增强生成",
        ],
        "expected_page": 1,
        "should_reject": False,
    }, {
        "question": "一个典型 RAG 系统如何处理文档并完成检索？",
        "expected_keywords": [
            "解析",
            "切分",
            "向量",
            "检索",
        ],
        "expected_page": 1,
        "should_reject": False,
    }, {
        "question": "RAG 能否彻底避免大模型幻觉？",
        "expected_keywords": [
            "降低",
            "不能完全消除",
        ],
        "expected_page": 1,
        "should_reject": False,
    }, {
        "question": "chunk overlap 比例过高有什么缺点？",
        "expected_keywords": [
            "冗余",
            "存储量",
            "噪声",
        ],
        "expected_page": 1,
        "should_reject": False,
    }, {
        "question": "top-k 太小可能造成什么后果？",
        "expected_keywords": [
            "遗漏",
            "缺少",
        ],
        "expected_page": 1,
        "should_reject": False,
    }, {
        "question": "检索候选较多时，Rerank 的作用是什么？",
        "expected_keywords": [
            "候选片段",
            "重新排序",
        ],
        "expected_page": 1,
        "should_reject": False,
    }, {
        "question": "SpringBoot 与 FastAPI 服务通过什么方式通信？",
        "expected_keywords": [
            "HTTP",
            "接口",
        ],
        "expected_page": 2,
        "should_reject": False,
    }, {
        "question": "普通员工能够访问哪些知识库？",
        "expected_keywords": [
            "所属部门",
        ],
        "expected_page": 2,
        "should_reject": False,
    }, {
        "question": "文档在什么条件下会变成“可用”状态？",
        "expected_keywords": [
            "解析",
            "切分",
            "向量化",
        ],
        "expected_page": 2,
        "should_reject": False,
    }, {
        "question": "MySQL 与向量数据库分别适合保存什么数据？",
        "expected_keywords": [
            "结构化",
            "文本片段",
            "向量",
        ],
        "expected_page": 2,
        "should_reject": False,
    }, {
        "question": "星河科技使用 Kafka 还是 RabbitMQ 处理异步任务？",
        "expected_keywords": [

        ],
        "expected_page": None,
        "should_reject": True,
    }, {
        "question": "星河科技使用了哪个 Rerank 模型？",
        "expected_keywords": [

        ],
        "expected_page": None,
        "should_reject": True,
    }, {
        "question": "普通员工每天最多可以提问多少次？",
        "expected_keywords": [

        ],
        "expected_page": None,
        "should_reject": True,
    }, {
        "question": "为什么长文档通常不能直接全部发送给大语言模型？",
        "expected_keywords": [
            "上下文窗口",
            "无关内容",
            "成本",
            "干扰",
        ],
        "expected_page": 1,
        "should_reject": False,
    }, {
        "question": "当 chunk_size 为 500、chunk_overlap 为 100 时，第二个片段从哪里开始？",
        "expected_keywords": [
            "400 ",
        ],
        "expected_page": 1,
        "should_reject": False,
    }, {
        "question": "Embedding 在 RAG 流程中的作用是什么？",
        "expected_keywords": [
            "文本",
            "向量",
        ],
        "expected_page": 1,
        "should_reject": False,
    }, {
        "question": "实际项目为什么通常需要返回引用来源？",
        "expected_keywords": [
            "幻觉",
            "引用来源",
            "评测",
        ],
        "expected_page": 1,
        "should_reject": False,
    }, {
        "question": "星河科技在 2024 年启动内部知识库项目的目标是什么？",
        "expected_keywords": [
            "2024",
            "员工",
            "查询制度",
            "项目资料",
        ],
        "expected_page": 1,
        "should_reject": False,
    }, {
        "question": "SpringBoot 服务在星河科技知识库平台中负责哪些内容？",
        "expected_keywords": [
            "用户登录",
            "权限控制",
            "知识库管理",
            "聊天记录",
            "文档状态",
        ],
        "expected_page": 2,
        "should_reject": False,
    }, {
        "question": "FastAPI 服务在星河科技知识库平台中负责哪些内容？",
        "expected_keywords": [
            "PDF 解析",
            "文本切分",
            "Embedding",
            "向量检索",
            "大语言模型",
        ],
        "expected_page": 2,
        "should_reject": False,
    }, {
        "question": "Redis 在项目中可以用于哪些场景？",
        "expected_keywords": [
            "缓存热点数据",
            "短期会话",
            "限流",
        ],
        "expected_page": 2,
        "should_reject": False,
    }, {
        "question": "星河科技的总部在哪个城市？",
        "expected_keywords": [

        ],
        "expected_page": None,
        "should_reject": True,
    }, {
        "question": "星河科技内部知识库项目的预算是多少？",
        "expected_keywords": [

        ],
        "expected_page": None,
        "should_reject": True,
    }, {
        "question": "管理员是否可以删除其他员工账号？",
        "expected_keywords": [

        ],
        "expected_page": None,
        "should_reject": True,
    }, {
        "question": "文档中指定使用哪一个 Embedding 模型了吗？",
        "expected_keywords": [

        ],
        "expected_page": None,
        "should_reject": True,
    },
]
