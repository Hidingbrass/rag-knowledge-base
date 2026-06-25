"""评测测试集结构检查。

这个测试不评估模型效果，只检查 app/evaluation/cases.py 的数据结构是否稳定。
它可以防止后续新增题目时漏写字段、拒答题误填 expected_page，或者不小心删掉测试用例。
"""

from app.evaluation.cases import TEST_CASES


def test_evaluation_case_count_and_distribution():
    """当前测试集应包含 30 条：22 条普通题，8 条拒答题。"""
    normal_cases = [
        case
        for case in TEST_CASES
        if not case["should_reject"]
    ]
    rejection_cases = [
        case
        for case in TEST_CASES
        if case["should_reject"]
    ]

    assert len(TEST_CASES) == 30
    assert len(normal_cases) == 22
    assert len(rejection_cases) == 8


def test_evaluation_cases_have_valid_fields():
    """每条测试用例都应包含评测脚本需要的字段。"""
    for case in TEST_CASES:
        assert case["question"]
        assert isinstance(case["expected_keywords"], list)
        assert "expected_page" in case
        assert isinstance(case["should_reject"], bool)

        if case["should_reject"]:
            assert case["expected_keywords"] == []
            assert case["expected_page"] is None
        else:
            assert case["expected_keywords"]
            assert case["expected_page"] in {1, 2}
