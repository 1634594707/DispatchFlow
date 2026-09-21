FROM python:3.12-slim

WORKDIR /srv/fsd-geo

COPY pyproject.toml ./
COPY fsd_geo ./fsd_geo

RUN pip install --no-cache-dir .

EXPOSE 8090
CMD ["uvicorn", "fsd_geo.app:app", "--host", "0.0.0.0", "--port", "8090"]
