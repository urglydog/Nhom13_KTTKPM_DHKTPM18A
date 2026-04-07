# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy Maven config + source
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# --- BƯỚC 2: TẢI THƯ VIỆN & PLUGIN (QUAN TRỌNG) ---
# Lệnh này sẽ tải hết "phụ tùng" về và lưu vào 1 Layer riêng.
# Nếu pom.xml không đổi, bước này sẽ tốn 0 giây ở lần build sau.
RUN ./mvnw dependency:go-offline -B

# --- BƯỚC 3: MỚI COPY SOURCE CODE ---
COPY src ./src

# Build
RUN ./mvnw clean package -DskipTests

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy JAR từ build stage
COPY --from=build /app/target/*.jar app.jar

# Port mặc định của Gateway
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]