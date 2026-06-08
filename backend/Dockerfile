# syntax=docker/dockerfile:1

# ── 1단계: 빌드 (JDK 21로 부트 JAR 생성) ──────────────────────────────
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 의존성 레이어 캐싱: 빌드 스크립트 먼저 복사 → 소스만 바뀌면 의존성 재다운로드 안 함
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

# 소스 복사 후 실행 가능 JAR 빌드 (테스트는 CI에서 별도로 돌림)
COPY src ./src
RUN ./gradlew clean bootJar --no-daemon -x test

# ── 2단계: 실행 (JRE만 담아 이미지 경량화) ────────────────────────────
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# 루트 대신 비특권 유저로 실행 (보안)
RUN useradd -r -u 1001 appuser
USER appuser

COPY --from=build /app/build/libs/*.jar app.jar

# 컨테이너는 PORT(플랫폼 주입) 또는 8080으로 listen
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
