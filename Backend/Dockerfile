# ============================================
# 心理健康助手后端 - 应用镜像
# 构建：mvn package 后 docker build -t mental-health-assistant .
# ============================================

# 基础镜像：带 JDK 17 运行时的精简镜像
FROM eclipse-temurin:17-jre

# 元信息
LABEL maintainer="yuankai" description="心理健康助手后端服务"

# 工作目录
WORKDIR /app

# 拷贝打包好的 jar（先确认 target 下 jar 名与 pom 的 artifactId/version 一致）
COPY target/ai-springboot-0.0.1-SNAPSHOT.jar app.jar

# 容器内文件上传落盘目录
RUN mkdir -p /app/upload

# 暴露端口（与 application.yml 的 server.port 一致）
EXPOSE 8080

# 启动命令（数据库、Redis、JWT 密钥等通过环境变量注入）
ENTRYPOINT ["java", "-jar", "app.jar"]
