FROM maven:3.9.6-eclipse-temurin-21-alpine AS common-builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Install vào local repo của image này
RUN mvn clean install -DskipTests

# Quan trọng: Giữ lại Stage này làm image nền
FROM alpine:latest
# Copy toàn bộ kho thư viện .m2 sang image cuối
COPY --from=common-builder /root/.m2 /root/.m2