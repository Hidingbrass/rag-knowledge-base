"""项目日志配置。

这个文件负责统一配置 Python logging。

为什么要单独放在 core 里：
- 日志属于项目基础设施，不属于某一个 API 或 service。
- 所有模块都可以通过 get_logger(__name__) 拿到自己的 logger。
- 后续如果要改日志格式、日志级别、写入文件或接入日志平台，只需要优先改这里。

常见使用方式：
    from app.core.logging import get_logger

    logger = get_logger(__name__)
    logger.info("文档入库完成")
"""

import logging
import sys

from app.core.config import settings


def setup_logging() -> None:
    """初始化全项目日志配置。

    输入：无。
    输出：无显式返回。

    关键变量：
    - root_logger：Python logging 的根 logger，所有子 logger 默认会继承它的配置。
    - handler：日志输出目标。当前输出到控制台，方便本地学习和 uvicorn 查看。
    - formatter：日志格式，包含时间、级别、模块名和日志内容。

    为什么要判断 root_logger.handlers：
    FastAPI 开发模式、测试脚本或重复导入时，setup_logging() 可能被调用多次。
    如果不判断，会重复添加 handler，导致同一条日志打印多遍。
    """
    root_logger = logging.getLogger()

    if root_logger.handlers:
        root_logger.setLevel(settings.log_level)
        return

    handler = logging.StreamHandler(sys.stdout)
    formatter = logging.Formatter(
        "%(asctime)s | %(levelname)s | %(name)s | %(message)s"
    )
    handler.setFormatter(formatter)

    root_logger.addHandler(handler)
    root_logger.setLevel(settings.log_level)


def get_logger(name: str) -> logging.Logger:
    """获取指定模块使用的 logger。

    参数：
    - name：通常传入 __name__，这样日志里能看到是谁打印的。

    返回：
    - logging.Logger：模块级 logger。
    """
    return logging.getLogger(name)
