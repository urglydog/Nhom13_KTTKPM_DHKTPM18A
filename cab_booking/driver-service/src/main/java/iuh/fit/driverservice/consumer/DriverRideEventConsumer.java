package iuh.fit.driverservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.driverservice.dto.event.RideAssignedEvent;
import iuh.fit.driverservice.dto.event.RideLifecycleEvent;
import iuh.fit.driverservice.service.DriverRideCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DriverRideEventConsumer {

    private final DriverRideCommandService driverRideCommandService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "ride.assigned", groupId = "driver-service-group")
    public void handleRideAssigned(@Payload Map<String, Object> payload) {
        RideAssignedEvent event = objectMapper.convertValue(payload, RideAssignedEvent.class);
        log.info("[ride.assigned] rideId={} | driverId={}", event.aggregateId(), event.getDriverId());
        try {
            driverRideCommandService.handleRideAssigned(event);
        } catch (Exception ex) {
            log.error("Failed to handle ride.assigned | rideId={} | driverId={} | reason={}",
                    event.aggregateId(), event.getDriverId(), ex.getMessage(), ex);
        }
    }

    @KafkaListener(topics = "ride.completed", groupId = "driver-service-group")
    public void handleRideCompleted(@Payload Map<String, Object> payload) {
        RideLifecycleEvent event = objectMapper.convertValue(payload, RideLifecycleEvent.class);
        log.info("[ride.completed] rideId={} | driverId={}", event.aggregateId(), event.getDriverId());
        try {
            driverRideCommandService.cleanupRide(event.aggregateId(), event.getDriverId(), "ride.completed");
        } catch (Exception ex) {
            log.error("Failed to cleanup driver after ride.completed | rideId={} | reason={}",
                    event.aggregateId(), ex.getMessage(), ex);
        }
    }

    @KafkaListener(topics = "ride.cancelled", groupId = "driver-service-group")
    public void handleRideCancelled(@Payload Map<String, Object> payload) {
        RideLifecycleEvent event = objectMapper.convertValue(payload, RideLifecycleEvent.class);
        log.info("[ride.cancelled] rideId={} | driverId={}", event.aggregateId(), event.getDriverId());
        try {
            driverRideCommandService.cleanupRide(event.aggregateId(), event.getDriverId(), "ride.cancelled");
        } catch (Exception ex) {
            log.error("Failed to cleanup driver after ride.cancelled | rideId={} | reason={}",
                    event.aggregateId(), ex.getMessage(), ex);
        }
    }
}
