# CAB Booking System - API Payload Documentation

Tài liệu này cung cấp các mẫu Payload (JSON) để các thành viên trong nhóm thực hiện test API thông qua **API Gateway (Cổng 8080)**.

---

## 1. Review Service (Quản lý Đánh giá)

### 1.1. Tạo Đánh giá mới
*   **URL:** `POST http://localhost:8080/api/reviews`
*   **Payload:**
```json
{
  "rideId": "RIDE_001",
  "userId": "USER_123",
  "driverId": "DRIVER_456",
  "rating": 5,
  "comment": "Tài xế rất thân thiện, xe sạch sẽ!"
}
```

---

## 2. Notification Service (Dịch vụ Thông báo)

### 2.1. Test tạo thông báo (Simulate)
*   **URL:** `POST http://localhost:8080/api/notifications/test`
*   **Ví dụ:** `http://localhost:8080/api/notifications/test?userId=USER_123&message=Tai xe dang den don ban`

---

## 3. Hướng dẫn cài đặt nhanh cho Team (Quick Start)

Để chạy và test được, các thành viên cần:

1.  **Bật Docker:** Chạy `docker-compose up -d`.
2.  **Cấu hình Hosts:** Thêm dòng này vào file `hosts` của Windows:
    `127.0.0.1 mongodb kafka redis config-server eureka-server auth-db`
3.  **Thứ tự chạy trong IntelliJ:**
    `ConfigServer` -> `Eureka` -> `ReviewService` -> `NotificationService` -> `ApiGateway`.

---
*Chúc cả nhóm làm việc hiệu quả!*
