"""FSD 调度地理服务（PostGIS）。

把 DispatchFlow 里用 Java 手算的空间判断（点在围栏内、最近站点召回、球面距离）
搬到 PostGIS 上做，并提供与 Java 实现逐条对照的口径。
"""

__version__ = "0.1.0"
