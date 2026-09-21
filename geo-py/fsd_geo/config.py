"""运行配置：全部走环境变量，不落盘任何凭证。"""

from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class DbConfig:
    host: str
    port: int
    dbname: str
    user: str
    password: str

    @classmethod
    def from_env(cls) -> DbConfig:
        return cls(
            host=os.getenv("GEO_PG_HOST", "127.0.0.1"),
            port=int(os.getenv("GEO_PG_PORT", "5433")),
            dbname=os.getenv("GEO_PG_DB", "fsd_geo"),
            user=os.getenv("GEO_PG_USER", "postgres"),
            password=os.getenv("GEO_PG_PASSWORD", ""),
        )

    def dsn(self) -> str:
        return (
            f"host={self.host} port={self.port} dbname={self.dbname} "
            f"user={self.user} password={self.password}"
        )
