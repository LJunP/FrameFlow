# syntax=docker/dockerfile:1.7

ARG PYTHON_IMAGE=python:3.11.13-slim-bookworm

FROM ${PYTHON_IMAGE} AS build
WORKDIR /build
COPY pyproject.toml ./
COPY requirements.lock ./
COPY src ./src
RUN --mount=type=cache,target=/root/.cache/pip \
    python -m pip install --upgrade "pip==25.1.1" "build==1.2.2.post1" && \
    python -m build --wheel --outdir /wheels

FROM ${PYTHON_IMAGE} AS runtime
ARG VERSION=0.1.0
ARG VCS_REF=unknown
ARG SOURCE_URL=https://example.invalid/frameflow-select
LABEL org.opencontainers.image.title="FrameFlow Select AI worker" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.source="${SOURCE_URL}"

RUN apt-get update && \
    apt-get install -y --no-install-recommends ca-certificates ffmpeg libgomp1 && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd --gid 10001 frameflow && \
    useradd --uid 10001 --gid frameflow --create-home --shell /usr/sbin/nologin frameflow
COPY --from=build /wheels/*.whl /tmp/wheels/
COPY requirements.lock /tmp/requirements.lock
RUN --mount=type=cache,target=/root/.cache/pip \
    python -m pip install --requirement /tmp/requirements.lock && \
    python -m pip install --no-deps /tmp/wheels/*.whl && \
    rm -rf /tmp/wheels /tmp/requirements.lock

WORKDIR /work
RUN chown frameflow:frameflow /work

# ★ 核心：worker 处理不可信媒体，因此固定非 root UID，临时媒体只写 /work。
# 若给 root 或挂 Docker socket，畸形媒体触发的解析器漏洞会直接扩大为宿主机风险。
USER 10001:10001
EXPOSE 9108
STOPSIGNAL SIGTERM
ENTRYPOINT ["frameflow-worker"]
