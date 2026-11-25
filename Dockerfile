# syntax=docker/dockerfile:1
# 빌드: docker buildx build --platform linux/amd64,linux/arm64 -t <ecr>/indig-catalog:$(git rev-parse --short HEAD) --push .
FROM --platform=$BUILDPLATFORM gradle:8.10-jdk21 AS build
WORKDIR /app
COPY settings.gradle build.gradle gradle.properties* ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon || true
COPY src ./src
RUN gradle bootJar --no-daemon -x test

# 🔴 amd64 · arm64 둘 다 올립니다 (AWS 노드는 t4g · 온프레미스는 Intel PC) — 노드와 다른 아키텍처만 있으면 exec format error
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
USER app
EXPOSE 8082 8092
# MaxMetaspaceSize 160m — JPA 가 Gateway 보다 클래스를 많이 씁니다
# ✏️ 11/25 k6 S6 3,000명 — SerialGC(GC 스레드 1개) 멈춤 최대 721ms → ParallelGC: CPU 한도만큼 스레드로 나눠 짧게 (Gateway · concert 와 같음)
#   CPU 한도 1코어 이하(AWS 800m)면 SerialGC 와 같습니다. GC 는 JAVA_TOOL_OPTIONS 로 못 바꿉니다 — 여기 플래그가 뒤에 와서 두 GC 가 함께 켜져 기동 실패
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=70", \
  "-XX:+UseParallelGC", \
  "-XX:MaxMetaspaceSize=160m", \
  "-Duser.timezone=Asia/Seoul", \
  "-Dfile.encoding=UTF-8", \
  "-jar", "app.jar"]
