# syntax=docker/dockerfile:1.7

ARG MAVEN_IMAGE=maven:3.9.11-eclipse-temurin-17
ARG JRE_IMAGE=eclipse-temurin:17.0.16_8-jre-jammy

FROM ${MAVEN_IMAGE} AS build
WORKDIR /workspace

# Maven Wrapper 的 only-script 模式在系统没有 unzip 时会把 .zip URL
# 改为 .tar.gz，但仍用 ZIP 的固定 SHA-256 校验，因而必然拒绝构建。
# 只在 build stage 补 unzip，保留仓库 Wrapper 版本和校验，不把它带入运行镜像。
RUN apt-get update && \
    apt-get install -y --no-install-recommends unzip && \
    rm -rf /var/lib/apt/lists/*

# 先复制构建描述，再复制业务源码。BuildKit 的 Maven cache mount
# 跨次构建复用已校验依赖，不单独调用 dependency:go-offline：该 goal
# 会无条件解析测试与报告链，全新容器会在产物构建前下载大量无关依赖。
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY frameflow-app/pom.xml frameflow-app/pom.xml
COPY frameflow-app/src frameflow-app/src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -pl frameflow-app -am -Dmaven.test.skip=true package && \
    jar_path="$(find frameflow-app/target -maxdepth 1 -type f -name '*.jar' ! -name '*.jar.original' -print -quit)" && \
    test -n "$jar_path" && cp "$jar_path" /workspace/application.jar

FROM ${JRE_IMAGE} AS runtime
ARG VERSION=0.1.0-SNAPSHOT
ARG VCS_REF=unknown
ARG SOURCE_URL=https://example.invalid/frameflow-select
LABEL org.opencontainers.image.title="FrameFlow Select app" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.source="${SOURCE_URL}"

RUN apt-get update && \
    apt-get install -y --no-install-recommends ca-certificates curl && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd --gid 10001 frameflow && \
    useradd --uid 10001 --gid frameflow --no-create-home --shell /usr/sbin/nologin frameflow

WORKDIR /app
COPY --from=build --chown=frameflow:frameflow /workspace/application.jar /app/application.jar

# ★ 核心：运行阶段只保留 JRE、只读应用 JAR，并降权到固定非 root UID。
# 若改回 root 或把 Maven/源码带进运行镜像，容器逃逸面与供应链审计噪声都会扩大。
USER 10001:10001
EXPOSE 18080
HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=5 \
  CMD curl --fail --silent --show-error http://127.0.0.1:18080/actuator/health >/dev/null || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/application.jar"]
