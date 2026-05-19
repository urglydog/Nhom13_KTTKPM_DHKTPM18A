package iuh.fit.driverservice.service;

import iuh.fit.common.exception.AppException;
import iuh.fit.common.exception.ErrorCode;
import iuh.fit.driverservice.dto.event.DriverAcceptedEvent;
import iuh.fit.driverservice.dto.event.DriverLocationPayload;
import iuh.fit.driverservice.dto.event.DriverRejectedEvent;
import iuh.fit.driverservice.dto.event.RideAssignedEvent;
import iuh.fit.driverservice.dto.request.HandleDriverAssignmentRequest;
import iuh.fit.driverservice.dto.response.DriverCurrentRideResponse;
import iuh.fit.driverservice.entity.DriverAssignmentAction;
import iuh.fit.driverservice.entity.DriverAvailabilityStatus;
import iuh.fit.driverservice.entity.DriverProfile;
import iuh.fit.driverservice.entity.DriverRideStatus;
import iuh.fit.driverservice.entity.DriverVerificationStatus;
import iuh.fit.driverservice.repository.DriverProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriverRideCommandService {

    private static final String RIDE_ACCEPTED_TOPIC = "ride.accepted";
    private static final String RIDE_REJECTED_TOPIC = "ride.rejected";
    private static final String PENDING_RIDE_KEY_PREFIX = "driver:";
    private static final String PENDING_RIDE_KEY_SUFFIX = ":pending-ride";
    private static final String ASSIGNMENT_TIMEOUT_REASON = "ASSIGNMENT_TIMEOUT";
    private static final Duration ASSIGNMENT_TTL = Duration.ofSeconds(30);

    private final DriverProfileRepository driverProfileRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final DriverStatusService driverStatusService;
    private final StringRedisTemplate stringRedisTemplate;

    @Transactional
    public void handleRideAssigned(RideAssignedEvent event) {
        String rideId = event.aggregateId();
        String driverId = normalize(event.getDriverId());
        if (rideId == null || rideId.isBlank() || driverId == null || driverId.isBlank()) {
            log.warn("Skip ride.assigned with missing rideId/driverId | rideId={} | driverId={}", rideId, driverId);
            return;
        }

        DriverProfile profile = driverProfileRepository.findByExternalUserId(driverId).orElse(null);
        if (profile == null) {
            log.warn("Assigned driver profile not found, rejecting assignment | rideId={} | driverId={}", rideId, driverId);
            publishDriverRejected(rideId, driverId, "Assigned driver profile not found");
            return;
        }

        if (isSamePendingAssignment(profile, rideId)) {
            writePendingRide(profile, event);
            log.info("Duplicate ride.assigned skipped | rideId={} | driverId={}", rideId, driverId);
            return;
        }

        if (!canReceiveAssignment(profile)) {
            log.warn("Driver cannot receive assignment, rejecting | rideId={} | driverId={} | availability={} | currentRideId={} | currentRideStatus={}",
                    rideId, driverId, profile.getAvailabilityStatus(), profile.getCurrentRideId(), profile.getCurrentRideStatus());
            publishDriverRejected(rideId, driverId, "Driver unavailable for assignment");
            return;
        }

        profile.setCurrentRideId(rideId);
        profile.setCurrentRideStatus(DriverRideStatus.ASSIGNED);
        profile.setCurrentRidePickup(event.getPickupAddress());
        profile.setCurrentRideDestination(event.getDropoffAddress());
        profile.setCurrentRideRequestedAt(LocalDateTime.now());
        profile.setAvailabilityStatus(DriverAvailabilityStatus.ONLINE);
        profile.setLastOnlineAt(LocalDateTime.now());

        DriverProfile savedProfile = driverProfileRepository.save(profile);
        writePendingRide(savedProfile, event);
        driverStatusService.writeDriverStatus(savedProfile);
        log.info("Stored pending ride assignment | rideId={} | driverId={}", rideId, driverId);
    }

    @Transactional
    public DriverCurrentRideResponse handleAssignment(String externalUserId, HandleDriverAssignmentRequest request) {
        DriverAssignmentAction action = DriverAssignmentAction.valueOf(request.getAction().trim().toUpperCase());
        return action == DriverAssignmentAction.REJECT
                ? rejectRide(externalUserId, request.getRideId())
                : acceptRide(externalUserId, request.getRideId());
    }

    @Transactional(noRollbackFor = AppException.class)
    public DriverCurrentRideResponse acceptRide(String externalUserId, String rideId) {
        DriverProfile profile = getRequiredProfile(externalUserId);
        ensureDriverCanTakeCommand(profile);
        ensurePendingAssignment(profile, rideId);
        ensurePendingRideNotExpired(profile);

        profile.setCurrentRideStatus(DriverRideStatus.ACCEPTED);
        profile.setAvailabilityStatus(DriverAvailabilityStatus.ON_TRIP);
        profile.setLastOnlineAt(LocalDateTime.now());

        DriverProfile savedProfile = driverProfileRepository.save(profile);
        clearPendingRide(savedProfile.getExternalUserId());
        driverStatusService.writeDriverStatus(savedProfile);
        publishDriverAccepted(savedProfile.getCurrentRideId(), savedProfile.getExternalUserId());
        return toCurrentRideResponse(savedProfile);
    }

    @Transactional
    public DriverCurrentRideResponse rejectRide(String externalUserId, String rideId) {
        DriverProfile profile = getRequiredProfile(externalUserId);
        ensurePendingAssignment(profile, rideId);

        clearCurrentRide(profile, DriverAvailabilityStatus.ONLINE);
        DriverProfile savedProfile = driverProfileRepository.save(profile);
        clearPendingRide(savedProfile.getExternalUserId());
        driverStatusService.writeDriverStatus(savedProfile);
        publishDriverRejected(rideId, savedProfile.getExternalUserId(), "Driver rejected assignment");
        return toCurrentRideResponse(savedProfile);
    }

    @Transactional
    public void cleanupRide(String rideId, String driverId, String sourceTopic) {
        if (rideId == null || rideId.isBlank()) {
            log.warn("Skip driver cleanup without rideId | source={}", sourceTopic);
            return;
        }

        DriverProfile profile = resolveProfileForCleanup(rideId, driverId);
        if (profile == null) {
            log.info("No driver current ride to cleanup | rideId={} | source={}", rideId, sourceTopic);
            return;
        }

        clearCurrentRide(profile, DriverAvailabilityStatus.ONLINE);
        DriverProfile savedProfile = driverProfileRepository.save(profile);
        clearPendingRide(savedProfile.getExternalUserId());
        driverStatusService.writeDriverStatus(savedProfile);
        log.info("Driver ride state cleaned | rideId={} | driverId={} | source={}",
                rideId, savedProfile.getExternalUserId(), sourceTopic);
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void cleanupExpiredAssignments() {
        LocalDateTime cutoff = LocalDateTime.now().minus(ASSIGNMENT_TTL);
        for (DriverProfile profile : driverProfileRepository
                .findByCurrentRideStatusAndCurrentRideRequestedAtBefore(DriverRideStatus.ASSIGNED, cutoff)) {
            String rideId = profile.getCurrentRideId();
            String driverId = profile.getExternalUserId();
            log.warn("Assignment timeout detected | rideId={} | driverId={} | publishing ride.rejected | reason={}",
                    rideId, driverId, ASSIGNMENT_TIMEOUT_REASON);

            clearCurrentRide(profile, DriverAvailabilityStatus.ONLINE);
            DriverProfile savedProfile = driverProfileRepository.save(profile);
            clearPendingRide(savedProfile.getExternalUserId());
            driverStatusService.writeDriverStatus(savedProfile);
            publishDriverRejected(rideId, savedProfile.getExternalUserId(), ASSIGNMENT_TIMEOUT_REASON);
            log.info("Driver assignment expired, driver returned to AVAILABLE | rideId={} | driverId={}",
                    rideId, savedProfile.getExternalUserId());
        }
    }

    private DriverProfile getRequiredProfile(String externalUserId) {
        return driverProfileRepository.findByExternalUserId(externalUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));
    }

    private void ensurePendingAssignment(DriverProfile profile, String rideId) {
        if (rideId == null || rideId.isBlank()
                || profile.getCurrentRideId() == null
                || !profile.getCurrentRideId().equals(rideId)
                || profile.getCurrentRideStatus() != DriverRideStatus.ASSIGNED) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private void ensureDriverCanTakeCommand(DriverProfile profile) {
        if (profile.getVerificationStatus() != DriverVerificationStatus.APPROVED) {
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (profile.getAvailabilityStatus() == DriverAvailabilityStatus.OFFLINE) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        if (profile.getCurrentRideStatus() != DriverRideStatus.ASSIGNED
                && profile.getAvailabilityStatus() == DriverAvailabilityStatus.ON_TRIP) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private void clearCurrentRide(DriverProfile profile, DriverAvailabilityStatus nextAvailabilityStatus) {
        profile.setCurrentRideId(null);
        profile.setCurrentRideStatus(null);
        profile.setCurrentRidePickup(null);
        profile.setCurrentRideDestination(null);
        profile.setCurrentRideRequestedAt(null);
        profile.setAvailabilityStatus(nextAvailabilityStatus);
    }

    private boolean canReceiveAssignment(DriverProfile profile) {
        return profile.getVerificationStatus() == DriverVerificationStatus.APPROVED
                && profile.getAvailabilityStatus() == DriverAvailabilityStatus.ONLINE
                && profile.getCurrentRideId() == null
                && profile.getCurrentRideStatus() == null;
    }

    private boolean isSamePendingAssignment(DriverProfile profile, String rideId) {
        return rideId.equals(profile.getCurrentRideId())
                && profile.getCurrentRideStatus() == DriverRideStatus.ASSIGNED;
    }

    private void ensurePendingRideNotExpired(DriverProfile profile) {
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(pendingRideKey(profile.getExternalUserId())))) {
            clearCurrentRide(profile, DriverAvailabilityStatus.ONLINE);
            driverProfileRepository.save(profile);
            driverStatusService.writeDriverStatus(profile);
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private DriverProfile resolveProfileForCleanup(String rideId, String driverId) {
        String normalizedDriverId = normalize(driverId);
        if (normalizedDriverId != null && !normalizedDriverId.isBlank()) {
            return driverProfileRepository.findByExternalUserId(normalizedDriverId)
                    .filter(profile -> rideId.equals(profile.getCurrentRideId()))
                    .orElse(null);
        }
        return driverProfileRepository.findByCurrentRideId(rideId).orElse(null);
    }

    private void writePendingRide(DriverProfile profile, RideAssignedEvent event) {
        String rideId = event.aggregateId();
        String key = pendingRideKey(profile.getExternalUserId());
        Instant assignedAt = Instant.now();
        Instant expiredAt = assignedAt.plus(ASSIGNMENT_TTL);

        Map<String, String> pendingRide = new HashMap<>();
        pendingRide.put("rideId", rideId);
        pendingRide.put("bookingId", event.getBookingId() == null ? rideId : event.getBookingId());
        pendingRide.put("driverId", profile.getExternalUserId());
        pendingRide.put("status", DriverRideStatus.ASSIGNED.name());
        pendingRide.put("assignedAt", assignedAt.toString());
        pendingRide.put("expiredAt", expiredAt.toString());
        if (event.getPickupAddress() != null) {
            pendingRide.put("pickupLocation", event.getPickupAddress());
        }
        if (event.getDropoffAddress() != null) {
            pendingRide.put("dropoffLocation", event.getDropoffAddress());
        }
        if (event.getEstimatedFare() != null) {
            pendingRide.put("fare", event.getEstimatedFare().toPlainString());
        }

        stringRedisTemplate.opsForHash().putAll(key, pendingRide);
        stringRedisTemplate.expire(key, ASSIGNMENT_TTL.toSeconds(), TimeUnit.SECONDS);
    }

    private void clearPendingRide(String driverId) {
        stringRedisTemplate.delete(pendingRideKey(driverId));
    }

    private String pendingRideKey(String driverId) {
        return PENDING_RIDE_KEY_PREFIX + driverId + PENDING_RIDE_KEY_SUFFIX;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private DriverCurrentRideResponse toCurrentRideResponse(DriverProfile profile) {
        return DriverCurrentRideResponse.builder()
                .rideId(profile.getCurrentRideId())
                .rideStatus(profile.getCurrentRideStatus() == null ? null : profile.getCurrentRideStatus().name())
                .pickupAddress(profile.getCurrentRidePickup())
                .destinationAddress(profile.getCurrentRideDestination())
                .requestedAt(profile.getCurrentRideRequestedAt())
                .driverAvailabilityStatus(profile.getAvailabilityStatus().name())
                .currentLocation(DriverLocationPayload.builder()
                        .lat(profile.getCurrentLatitude())
                        .lng(profile.getCurrentLongitude())
                        .build())
                .build();
    }

    private void publishDriverAccepted(String rideId, String driverId) {
        kafkaTemplate.send(
                RIDE_ACCEPTED_TOPIC,
                rideId,
                DriverAcceptedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType(DriverAcceptedEvent.EVENT_TYPE)
                        .rideId(rideId)
                        .bookingId(rideId)
                        .driverId(driverId)
                        .timestamp(Instant.now().toString())
                        .build());
    }

    private void publishDriverRejected(String rideId, String driverId, String reason) {
        kafkaTemplate.send(
                RIDE_REJECTED_TOPIC,
                rideId,
                DriverRejectedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType(DriverRejectedEvent.EVENT_TYPE)
                        .rideId(rideId)
                        .bookingId(rideId)
                        .driverId(driverId)
                        .reason(reason)
                        .timestamp(Instant.now().toString())
                        .build());
    }

}
