"""PostGIS 连接与查询执行。

用 psycopg3 的同步连接池：调度侧的空间查询是「一次派单一次调用」的短查询，
异步收益远小于引入的复杂度，且本服务不是在线热路径（Java 侧有降级）。
"""

from __future__ import annotations

from collections.abc import Iterable, Sequence
from typing import Any

from psycopg_pool import ConnectionPool

from .config import DbConfig

_pool: ConnectionPool | None = None


def init_pool(cfg: DbConfig, *, min_size: int = 1, max_size: int = 8) -> ConnectionPool:
    global _pool
    if _pool is not None:
        return _pool
    _pool = ConnectionPool(conninfo=cfg.dsn(), min_size=min_size, max_size=max_size, open=True)
    return _pool


def close_pool() -> None:
    global _pool
    if _pool is not None:
        _pool.close()
        _pool = None


def get_pool() -> ConnectionPool:
    if _pool is None:
        init_pool(DbConfig.from_env())
    assert _pool is not None
    return _pool


def query(sql: str, params: Sequence[Any] = ()) -> list[tuple[Any, ...]]:
    with get_pool().connection() as conn:
        with conn.cursor() as cur:
            cur.execute(sql, params)
            return list(cur.fetchall())


def query_one(sql: str, params: Sequence[Any] = ()) -> tuple[Any, ...] | None:
    rows = query(sql, params)
    return rows[0] if rows else None


def execute_many(sql: str, rows: Iterable[Sequence[Any]]) -> int:
    values = list(rows)
    if not values:
        return 0
    with get_pool().connection() as conn:
        with conn.cursor() as cur:
            cur.executemany(sql, values)
        conn.commit()
    return len(values)


def run_ddl(script: str) -> None:
    with get_pool().connection() as conn:
        conn.execute(script)
        conn.commit()
