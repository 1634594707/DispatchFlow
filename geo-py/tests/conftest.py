"""测试夹具：连不上 PostGIS 时整组跳过，而不是给出假绿。"""

from __future__ import annotations

import os
from pathlib import Path

import pytest

os.environ.setdefault("GEO_PG_HOST", "127.0.0.1")
os.environ.setdefault("GEO_PG_PORT", "5433")
os.environ.setdefault("GEO_PG_DB", "fsd_geo")
os.environ.setdefault("GEO_PG_USER", "postgres")

from fsd_geo import db  # noqa: E402
from fsd_geo.config import DbConfig  # noqa: E402


def _reachable() -> bool:
    try:
        import psycopg

        with psycopg.connect(DbConfig.from_env().dsn(), connect_timeout=3) as conn:
            conn.execute("SELECT 1")
        return True
    except Exception:
        return False


pytestmark = pytest.mark.skipif(
    not _reachable(),
    reason="PostGIS 不可达：先起容器 docker compose -f geo-py/docker-compose.yml up -d "
           "并跑 scripts/seed_from_migrations.py",
)


@pytest.fixture(scope="session", autouse=True)
def pool():
    db.init_pool(DbConfig.from_env())
    if db.query_one("SELECT count(*) FROM station")[0] == 0:  # type: ignore[index]
        pytest.skip("fsd_geo 库为空，未执行 seed_from_migrations.py")
    yield
    db.close_pool()


MIGRATIONS = Path(__file__).resolve().parents[2] / "back" / "sql" / "migrations"
