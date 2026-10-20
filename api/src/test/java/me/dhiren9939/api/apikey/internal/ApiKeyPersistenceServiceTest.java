package me.dhiren9939.api.apikey.internal;

import me.dhiren9939.api.apikey.ApiKeyPermissions;
import me.dhiren9939.api.apikey.Permissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyPersistenceServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-02-15T12:00:00Z");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID API_KEY_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String API_KEY_NAME = "test-key";
    private static final String ACCESS_ID = "acc_test123";
    private static final String ACCESS_SECRET = "secret_raw";
    private static final String SECRET_HASH = "hashed_secret";
    private static final Permissions DEFAULT_PERMISSIONS = new Permissions(
            ApiKeyPermissions.Ingestion.NONE,
            ApiKeyPermissions.Tenant.NONE,
            ApiKeyPermissions.Channel.NONE
    );

    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private ApiKeyNextRepository apiKeyNextRepository;
    @Mock private ApiKeyEventEmitterService eventEmitterService;
    @Mock private OptimisticLockRetry optimisticLockRetry;
    @Mock private ApiKeyExpiry apiKeyExpiry;
    @Mock private PlatformTransactionManager transactionManager;

    @Captor private ArgumentCaptor<ApiKeyEntity> entityCaptor;
    @Captor private ArgumentCaptor<ApiKeyNextEntity> nextEntityCaptor;

    private ApiKeyPersistenceService service;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZoneId.of("UTC"));
        service = new ApiKeyPersistenceService(
                apiKeyRepository,
                apiKeyNextRepository,
                eventEmitterService,
                apiKeyExpiry,
                clock,
                optimisticLockRetry,
                transactionManager
        );
    }

    private ApiKeyTriplet triplet() {
        return new ApiKeyTriplet(ACCESS_ID, ACCESS_SECRET, SECRET_HASH);
    }

    /** Builds an entity that represents a fully applied (existing) API key with no pending changes. */
    private ApiKeyEntity existingEntity() {
        return ApiKeyEntity.builder()
                .apiKeyId(API_KEY_ID)
                .userId(USER_ID)
                .apiKeyName(API_KEY_NAME)
                .accessId(ACCESS_ID)
                .accessSecretHash(SECRET_HASH)
                .permissions(DEFAULT_PERMISSIONS)
                .createdAt(NOW)
                .expiresAt(EXPIRES_AT)
                .appliedVersion(1)
                .issuedVersion(1)
                .desiredVersion(1)
                .next(null)
                .build();
    }

    /** Builds a pending next entity with the given status. */
    private ApiKeyNextEntity pendingNext(ApiKeyUpdateStatus status) {
        return ApiKeyNextEntity.builder()
                .updateVersion(1)
                .updatedAt(NOW)
                .updateType(ApiKeyUpdateType.UPSERT)
                .updateStatus(status)
                .apiKeyName(API_KEY_NAME)
                .permissions(DEFAULT_PERMISSIONS)
                .expiresAt(EXPIRES_AT)
                .build();
    }

    /** Asserts all fields of an ApiKeyDto. */
    private void assertDtoMatches(ApiKeyDto actual, UUID expectedApiKeyId, UUID expectedUserId,
                                  String expectedAccessId, String expectedSecret, String expectedSecretHash,
                                  String expectedApiKeyName, Permissions expectedPermissions,
                                  Instant expectedCreatedAt, Instant expectedExpiresAt,
                                  ApiKeyUpdateStatus expectedUpdateStatus) {
        assertThat(actual).isNotNull();
        assertThat(actual.apiKeyId()).isEqualTo(expectedApiKeyId);
        assertThat(actual.userId()).isEqualTo(expectedUserId);
        assertThat(actual.accessId()).isEqualTo(expectedAccessId);
        assertThat(actual.accessSecret()).isEqualTo(expectedSecret);
        assertThat(actual.accessSecretHash()).isEqualTo(expectedSecretHash);
        assertThat(actual.apiKeyName()).isEqualTo(expectedApiKeyName);
        assertThat(actual.permissions()).isEqualTo(expectedPermissions);
        assertThat(actual.createdAt()).isEqualTo(expectedCreatedAt);
        assertThat(actual.expiresAt()).isEqualTo(expectedExpiresAt);
        assertThat(actual.updateStatus()).isEqualTo(expectedUpdateStatus);
    }

    /** Asserts all mutable and bookkeeping fields of an ApiKeyNextEntity. */
    private void assertNextEntityMatches(ApiKeyNextEntity actual, long expectedUpdateVersion,
                                         Instant expectedUpdatedAt, ApiKeyUpdateType expectedType,
                                         ApiKeyUpdateStatus expectedStatus, String expectedName,
                                         Permissions expectedPermissions, Instant expectedExpiresAt) {
        assertThat(actual).isNotNull();
        assertThat(actual.getUpdateVersion()).isEqualTo(expectedUpdateVersion);
        assertThat(actual.getUpdatedAt()).isEqualTo(expectedUpdatedAt);
        assertThat(actual.getUpdateType()).isEqualTo(expectedType);
        assertThat(actual.getUpdateStatus()).isEqualTo(expectedStatus);
        assertThat(actual.getApiKeyName()).isEqualTo(expectedName);
        assertThat(actual.getPermissions()).isEqualTo(expectedPermissions);
        assertThat(actual.getExpiresAt()).isEqualTo(expectedExpiresAt);
    }

    // ========================================================================================
    // create()
    // ========================================================================================

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("creates entity, next entity, emits event, and returns DTO with secret")
        void happyPath() {
            ApiKeyDto result = service.create(USER_ID, API_KEY_NAME, triplet(),
                    DEFAULT_PERMISSIONS, NOW, EXPIRES_AT);

            verify(apiKeyRepository).save(entityCaptor.capture());
            verify(apiKeyNextRepository).save(nextEntityCaptor.capture());

            ApiKeyEntity savedEntity = entityCaptor.getValue();
            assertThat(savedEntity.getUserId()).isEqualTo(USER_ID);
            assertThat(savedEntity.getApiKeyName()).isEqualTo(API_KEY_NAME);
            assertThat(savedEntity.getAccessId()).isEqualTo(ACCESS_ID);
            assertThat(savedEntity.getAccessSecretHash()).isEqualTo(SECRET_HASH);
            assertThat(savedEntity.getPermissions()).isEqualTo(DEFAULT_PERMISSIONS);
            assertThat(savedEntity.getCreatedAt()).isEqualTo(NOW);
            assertThat(savedEntity.getExpiresAt()).isEqualTo(EXPIRES_AT);
            assertThat(savedEntity.getAppliedVersion()).isEqualTo(ApiKeyEntity.NON_EXISTENT);
            assertThat(savedEntity.getIssuedVersion()).isEqualTo(1);
            assertThat(savedEntity.getDesiredVersion()).isEqualTo(1);
            assertThat(savedEntity.getNext()).isSameAs(nextEntityCaptor.getValue());

            ApiKeyNextEntity savedNext = nextEntityCaptor.getValue();
            assertNextEntityMatches(savedNext, 1, NOW, ApiKeyUpdateType.UPSERT,
                    ApiKeyUpdateStatus.PENDING, API_KEY_NAME, DEFAULT_PERMISSIONS, EXPIRES_AT);

            verify(eventEmitterService).emitCreated(savedEntity);

            assertDtoMatches(result, savedEntity.getApiKeyId(), USER_ID, ACCESS_ID,
                    ACCESS_SECRET, SECRET_HASH, API_KEY_NAME, DEFAULT_PERMISSIONS,
                    NOW, EXPIRES_AT, ApiKeyUpdateStatus.PENDING);
        }

        @Test
        @DisplayName("preserves null expiresAt for non-expiring keys")
        void nullExpiresAt() {
            ApiKeyDto result = service.create(USER_ID, API_KEY_NAME, triplet(), DEFAULT_PERMISSIONS, NOW, null);

            verify(apiKeyRepository).save(entityCaptor.capture());
            verify(apiKeyNextRepository).save(nextEntityCaptor.capture());

            ApiKeyEntity savedEntity = entityCaptor.getValue();
            assertThat(savedEntity.getExpiresAt()).isNull();
            assertThat(savedEntity.getUserId()).isEqualTo(USER_ID);
            assertThat(savedEntity.getApiKeyName()).isEqualTo(API_KEY_NAME);
            assertThat(savedEntity.getAccessId()).isEqualTo(ACCESS_ID);
            assertThat(savedEntity.getAccessSecretHash()).isEqualTo(SECRET_HASH);
            assertThat(savedEntity.getPermissions()).isEqualTo(DEFAULT_PERMISSIONS);
            assertThat(savedEntity.getCreatedAt()).isEqualTo(NOW);
            assertThat(savedEntity.getAppliedVersion()).isEqualTo(ApiKeyEntity.NON_EXISTENT);
            assertThat(savedEntity.getIssuedVersion()).isEqualTo(1);
            assertThat(savedEntity.getDesiredVersion()).isEqualTo(1);
            assertThat(savedEntity.getNext()).isSameAs(nextEntityCaptor.getValue());

            ApiKeyNextEntity savedNext = nextEntityCaptor.getValue();
            assertNextEntityMatches(savedNext, 1, NOW, ApiKeyUpdateType.UPSERT,
                    ApiKeyUpdateStatus.PENDING, API_KEY_NAME, DEFAULT_PERMISSIONS, null);

            verify(eventEmitterService).emitCreated(savedEntity);

            assertDtoMatches(result, savedEntity.getApiKeyId(), USER_ID, ACCESS_ID,
                    ACCESS_SECRET, SECRET_HASH, API_KEY_NAME, DEFAULT_PERMISSIONS,
                    NOW, null, ApiKeyUpdateStatus.PENDING);
        }
    }

    // ========================================================================================
    // get()
    // ========================================================================================

    @Nested
    @DisplayName("get()")
    class Get {

        @Test
        @DisplayName("returns DTO for owned key")
        void happyPath() {
            ApiKeyEntity entity = existingEntity();
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.of(entity));

            ApiKeyDto result = service.get(USER_ID, API_KEY_ID);

            assertDtoMatches(result, API_KEY_ID, USER_ID, ACCESS_ID, null, null,
                    API_KEY_NAME, DEFAULT_PERMISSIONS, NOW, EXPIRES_AT, null);
        }

        @Test
        @DisplayName("returns DTO with PENDING updateStatus when change is pending")
        void pendingChange() {
            ApiKeyEntity entity = existingEntity();
            entity.setNext(pendingNext(ApiKeyUpdateStatus.PENDING));
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.of(entity));

            ApiKeyDto result = service.get(USER_ID, API_KEY_ID);

            assertDtoMatches(result, API_KEY_ID, USER_ID, ACCESS_ID, null, null,
                    API_KEY_NAME, DEFAULT_PERMISSIONS, NOW, EXPIRES_AT, ApiKeyUpdateStatus.PENDING);
        }

        @Test
        @DisplayName("returns DTO with FAILED updateStatus when change is failed")
        void failedChange() {
            ApiKeyEntity entity = existingEntity();
            entity.setNext(pendingNext(ApiKeyUpdateStatus.FAILED));
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.of(entity));

            ApiKeyDto result = service.get(USER_ID, API_KEY_ID);

            assertDtoMatches(result, API_KEY_ID, USER_ID, ACCESS_ID, null, null,
                    API_KEY_NAME, DEFAULT_PERMISSIONS, NOW, EXPIRES_AT, ApiKeyUpdateStatus.FAILED);
        }

        @Test
        @DisplayName("throws ApiKeyNotFoundException when key does not exist")
        void notFound() {
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.get(USER_ID, API_KEY_ID))
                    .isInstanceOf(ApiKeyNotFoundException.class);
        }

        @Test
        @DisplayName("throws ApiKeyNotFoundException when key belongs to different user")
        void wrongUser() {
            ApiKeyEntity entity = existingEntity();
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.of(entity));
            UUID differentUser = UUID.fromString("00000000-0000-0000-0000-000000000099");

            assertThatThrownBy(() -> service.get(differentUser, API_KEY_ID))
                    .isInstanceOf(ApiKeyNotFoundException.class);
        }
    }

    // ========================================================================================
    // getForVerification()
    // ========================================================================================

    @Nested
    @DisplayName("getForVerification()")
    class GetForVerification {

        @Test
        @DisplayName("returns DTO with secret hash for existing key")
        void happyPath() {
            ApiKeyEntity entity = existingEntity();
            when(apiKeyRepository.findByAccessId(ACCESS_ID)).thenReturn(Optional.of(entity));

            ApiKeyDto result = service.getForVerification(ACCESS_ID);

            assertDtoMatches(result, API_KEY_ID, USER_ID, ACCESS_ID, null, SECRET_HASH,
                    API_KEY_NAME, DEFAULT_PERMISSIONS, NOW, EXPIRES_AT, null);
        }

        @Test
        @DisplayName("throws ApiKeyNotFoundException when accessId not found")
        void notFound() {
            when(apiKeyRepository.findByAccessId("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getForVerification("nonexistent"))
                    .isInstanceOf(ApiKeyNotFoundException.class);
        }

        @Test
        @DisplayName("throws ApiKeyNotFoundException when appliedVersion is NON_EXISTENT")
        void pendingCreation() {
            ApiKeyEntity entity = existingEntity();
            entity.setAppliedVersion(ApiKeyEntity.NON_EXISTENT);
            when(apiKeyRepository.findByAccessId(ACCESS_ID)).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.getForVerification(ACCESS_ID))
                    .isInstanceOf(ApiKeyNotFoundException.class);
        }
    }

    // ========================================================================================
    // update()
    // ========================================================================================

    @Nested
    @DisplayName("update()")
    class Update {

        @BeforeEach
        void stubRetryAndTransaction() {
            lenient().when(optimisticLockRetry.execute(any())).thenAnswer(invocation -> {
                var supplier = invocation.getArgument(0, java.util.function.Supplier.class);
                return supplier != null ? supplier.get() : null;
            });
        }

        @BeforeEach
        void stubTransactionManager() {
            lenient().when(transactionManager.getTransaction(any()))
                    .thenReturn(mock(org.springframework.transaction.TransactionStatus.class));
        }

        @Test
        @DisplayName("updates name only")
        void nameChangeOnly() {
            ApiKeyEntity entity = existingEntity();
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.of(entity));

            ApiKeyDto result = service.update(USER_ID, API_KEY_ID, "new-name", null, null);

            verify(apiKeyRepository).save(entityCaptor.capture());
            verify(apiKeyNextRepository).save(nextEntityCaptor.capture());

            ApiKeyEntity saved = entityCaptor.getValue();
            assertThat(saved.getIssuedVersion()).isEqualTo(2);
            assertThat(saved.getDesiredVersion()).isEqualTo(2);
            assertThat(saved.getNext()).isSameAs(nextEntityCaptor.getValue());

            ApiKeyNextEntity nextSaved = nextEntityCaptor.getValue();
            assertNextEntityMatches(nextSaved, 2, NOW, ApiKeyUpdateType.UPSERT,
                    ApiKeyUpdateStatus.PENDING, "new-name", DEFAULT_PERMISSIONS, EXPIRES_AT);

            verify(eventEmitterService).emitUpdated(saved);

            assertDtoMatches(result, API_KEY_ID, USER_ID, ACCESS_ID, null, null,
                    API_KEY_NAME, DEFAULT_PERMISSIONS, NOW, EXPIRES_AT, ApiKeyUpdateStatus.PENDING);
        }

        @Test
        @DisplayName("updates expiry when duration produces different value")
        void expiryChange() {
            ApiKeyEntity entity = existingEntity();
            Instant newExpiry = Instant.parse("2026-03-15T12:00:00Z");
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.of(entity));
            when(apiKeyExpiry.getExpiry(NOW, ApiKeyExpiry.Duration.ONE_MONTH)).thenReturn(newExpiry);

            ApiKeyDto result = service.update(USER_ID, API_KEY_ID, null, ApiKeyExpiry.Duration.ONE_MONTH, null);

            verify(apiKeyRepository).save(entityCaptor.capture());
            verify(apiKeyNextRepository).save(nextEntityCaptor.capture());

            ApiKeyEntity saved = entityCaptor.getValue();
            assertThat(saved.getIssuedVersion()).isEqualTo(2);
            assertThat(saved.getDesiredVersion()).isEqualTo(2);

            ApiKeyNextEntity nextSaved = nextEntityCaptor.getValue();
            assertNextEntityMatches(nextSaved, 2, NOW, ApiKeyUpdateType.UPSERT,
                    ApiKeyUpdateStatus.PENDING, API_KEY_NAME, DEFAULT_PERMISSIONS, newExpiry);

            verify(eventEmitterService).emitUpdated(saved);

            assertDtoMatches(result, API_KEY_ID, USER_ID, ACCESS_ID, null, null,
                    API_KEY_NAME, DEFAULT_PERMISSIONS, NOW, EXPIRES_AT, ApiKeyUpdateStatus.PENDING);
        }

        @Test
        @DisplayName("updates ingestion permission only")
        void ingestionPermissionChange() {
            ApiKeyEntity entity = existingEntity();
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.of(entity));
            Permissions permUpdate = new Permissions(ApiKeyPermissions.Ingestion.WRITE, null, null);
            Permissions expectedProposedPermissions = new Permissions(
                    ApiKeyPermissions.Ingestion.WRITE,
                    ApiKeyPermissions.Tenant.NONE,
                    ApiKeyPermissions.Channel.NONE
            );

            ApiKeyDto result = service.update(USER_ID, API_KEY_ID, null, null, permUpdate);

            verify(apiKeyRepository).save(entityCaptor.capture());
            verify(apiKeyNextRepository).save(nextEntityCaptor.capture());

            ApiKeyEntity saved = entityCaptor.getValue();
            assertThat(saved.getIssuedVersion()).isEqualTo(2);
            assertThat(saved.getDesiredVersion()).isEqualTo(2);

            ApiKeyNextEntity nextSaved = nextEntityCaptor.getValue();
            assertNextEntityMatches(nextSaved, 2, NOW, ApiKeyUpdateType.UPSERT,
                    ApiKeyUpdateStatus.PENDING, API_KEY_NAME, expectedProposedPermissions, EXPIRES_AT);

            verify(eventEmitterService).emitUpdated(saved);

            assertDtoMatches(result, API_KEY_ID, USER_ID, ACCESS_ID, null, null,
                    API_KEY_NAME, DEFAULT_PERMISSIONS, NOW, EXPIRES_AT, ApiKeyUpdateStatus.PENDING);
        }

        @Test
        @DisplayName("updates tenant permission only")
        void tenantPermissionChange() {
            ApiKeyEntity entity = existingEntity();
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.of(entity));
            Permissions permUpdate = new Permissions(null, ApiKeyPermissions.Tenant.READ_WRITE, null);
            Permissions expectedProposedPermissions = new Permissions(
                    ApiKeyPermissions.Ingestion.NONE,
                    ApiKeyPermissions.Tenant.READ_WRITE,
                    ApiKeyPermissions.Channel.NONE
            );

            ApiKeyDto result = service.update(USER_ID, API_KEY_ID, null, null, permUpdate);

            verify(apiKeyRepository).save(entityCaptor.capture());
            verify(apiKeyNextRepository).save(nextEntityCaptor.capture());

            ApiKeyEntity saved = entityCaptor.getValue();
            assertThat(saved.getIssuedVersion()).isEqualTo(2);
            assertThat(saved.getDesiredVersion()).isEqualTo(2);

            ApiKeyNextEntity nextSaved = nextEntityCaptor.getValue();
            assertNextEntityMatches(nextSaved, 2, NOW, ApiKeyUpdateType.UPSERT,
                    ApiKeyUpdateStatus.PENDING, API_KEY_NAME, expectedProposedPermissions, EXPIRES_AT);

            verify(eventEmitterService).emitUpdated(saved);

            assertDtoMatches(result, API_KEY_ID, USER_ID, ACCESS_ID, null, null,
                    API_KEY_NAME, DEFAULT_PERMISSIONS, NOW, EXPIRES_AT, ApiKeyUpdateStatus.PENDING);
        }

        @Test
        @DisplayName("updates channel permission only")
        void channelPermissionChange() {
            ApiKeyEntity entity = existingEntity();
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.of(entity));
            Permissions permUpdate = new Permissions(null, null, ApiKeyPermissions.Channel.READ_WRITE);
            Permissions expectedProposedPermissions = new Permissions(
                    ApiKeyPermissions.Ingestion.NONE,
                    ApiKeyPermissions.Tenant.NONE,
                    ApiKeyPermissions.Channel.READ_WRITE
            );

            ApiKeyDto result = service.update(USER_ID, API_KEY_ID, null, null, permUpdate);

            verify(apiKeyRepository).save(entityCaptor.capture());
            verify(apiKeyNextRepository).save(nextEntityCaptor.capture());

            ApiKeyEntity saved = entityCaptor.getValue();
            assertThat(saved.getIssuedVersion()).isEqualTo(2);
            assertThat(saved.getDesiredVersion()).isEqualTo(2);

            ApiKeyNextEntity nextSaved = nextEntityCaptor.getValue();
            assertNextEntityMatches(nextSaved, 2, NOW, ApiKeyUpdateType.UPSERT,
                    ApiKeyUpdateStatus.PENDING, API_KEY_NAME, expectedProposedPermissions, EXPIRES_AT);

            verify(eventEmitterService).emitUpdated(saved);

            assertDtoMatches(result, API_KEY_ID, USER_ID, ACCESS_ID, null, null,
                    API_KEY_NAME, DEFAULT_PERMISSIONS, NOW, EXPIRES_AT, ApiKeyUpdateStatus.PENDING);
        }

        @Test
        @DisplayName("updates all fields at once")
        void allFieldsChanged() {
            ApiKeyEntity entity = existingEntity();
            Instant newExpiry = Instant.parse("2026-04-15T12:00:00Z");
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.of(entity));
            when(apiKeyExpiry.getExpiry(NOW, ApiKeyExpiry.Duration.ONE_MONTH)).thenReturn(newExpiry);
            Permissions permUpdate = new Permissions(
                    ApiKeyPermissions.Ingestion.WRITE,
                    ApiKeyPermissions.Tenant.READ,
                    ApiKeyPermissions.Channel.WRITE
            );

            ApiKeyDto result = service.update(USER_ID, API_KEY_ID, "all-new-name", ApiKeyExpiry.Duration.ONE_MONTH, permUpdate);

            verify(apiKeyRepository).save(entityCaptor.capture());
            verify(apiKeyNextRepository).save(nextEntityCaptor.capture());

            ApiKeyEntity saved = entityCaptor.getValue();
            assertThat(saved.getIssuedVersion()).isEqualTo(2);
            assertThat(saved.getDesiredVersion()).isEqualTo(2);

            ApiKeyNextEntity next = nextEntityCaptor.getValue();
            assertNextEntityMatches(next, 2, NOW, ApiKeyUpdateType.UPSERT,
                    ApiKeyUpdateStatus.PENDING, "all-new-name", permUpdate, newExpiry);

            verify(eventEmitterService).emitUpdated(saved);

            assertDtoMatches(result, API_KEY_ID, USER_ID, ACCESS_ID, null, null,
                    API_KEY_NAME, DEFAULT_PERMISSIONS, NOW, EXPIRES_AT, ApiKeyUpdateStatus.PENDING);
        }

        @Test
        @DisplayName("returns existing DTO without side effects when nothing changed (all nulls)")
        void noOpAllNulls() {
            ApiKeyEntity entity = existingEntity();
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.of(entity));

            ApiKeyDto result = service.update(USER_ID, API_KEY_ID, null, null, null);

            verify(apiKeyRepository, never()).save(any());
            verify(apiKeyNextRepository, never()).save(any());
            verify(eventEmitterService, never()).emitUpdated(any());

            assertThat(entity.getIssuedVersion()).isEqualTo(1);
            assertThat(entity.getDesiredVersion()).isEqualTo(1);
            assertThat(entity.getNext()).isNull();

            assertDtoMatches(result, API_KEY_ID, USER_ID, ACCESS_ID, null, null,
                    API_KEY_NAME, DEFAULT_PERMISSIONS, NOW, EXPIRES_AT, null);
        }

        @Test
        @DisplayName("returns existing DTO when permission update has same values")
        void noOpSamePermissions() {
            ApiKeyEntity entity = existingEntity();
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.of(entity));
            Permissions samePerms = new Permissions(
                    ApiKeyPermissions.Ingestion.NONE,
                    ApiKeyPermissions.Tenant.NONE,
                    ApiKeyPermissions.Channel.NONE
            );

            ApiKeyDto result = service.update(USER_ID, API_KEY_ID, null, null, samePerms);

            verify(apiKeyRepository, never()).save(any());
            verify(apiKeyNextRepository, never()).save(any());
            verify(eventEmitterService, never()).emitUpdated(any());

            assertThat(entity.getIssuedVersion()).isEqualTo(1);
            assertThat(entity.getDesiredVersion()).isEqualTo(1);
            assertThat(entity.getNext()).isNull();

            assertDtoMatches(result, API_KEY_ID, USER_ID, ACCESS_ID, null, null,
                    API_KEY_NAME, DEFAULT_PERMISSIONS, NOW, EXPIRES_AT, null);
        }

        @Test
        @DisplayName("throws ApiKeyNotFoundException when entity has pending creation")
        void rejectPendingCreation() {
            ApiKeyEntity entity = existingEntity();
            entity.setAppliedVersion(ApiKeyEntity.NON_EXISTENT);
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.update(USER_ID, API_KEY_ID, "name", null, null))
                    .isInstanceOf(ApiKeyNotFoundException.class)
                    .hasMessage("Can't update an API key with pending creation");

            verify(apiKeyRepository, never()).save(any());
            verify(apiKeyNextRepository, never()).save(any());
            verify(eventEmitterService, never()).emitUpdated(any());
        }

        @Test
        @DisplayName("throws ApiKeyUpdateFailure when entity has pending changes")
        void rejectPendingChanges() {
            ApiKeyEntity entity = existingEntity();
            entity.setNext(pendingNext(ApiKeyUpdateStatus.PENDING));
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.update(USER_ID, API_KEY_ID, "name", null, null))
                    .isInstanceOf(ApiKeyUpdateFailure.class)
                    .hasMessage("Can't update an API key with pending changes");

            verify(apiKeyRepository, never()).save(any());
            verify(apiKeyNextRepository, never()).save(any());
            verify(eventEmitterService, never()).emitUpdated(any());
        }

        @Test
        @DisplayName("throws ApiKeyNotFoundException when entity not found")
        void notFound() {
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(USER_ID, API_KEY_ID, "name", null, null))
                    .isInstanceOf(ApiKeyNotFoundException.class);

            verify(apiKeyRepository, never()).save(any());
            verify(apiKeyNextRepository, never()).save(any());
            verify(eventEmitterService, never()).emitUpdated(any());
        }

        @Test
        @DisplayName("throws ApiKeyUpdateFailure when optimistic lock retries exhausted")
        void retriesExhausted() {
            doThrow(new ObjectOptimisticLockingFailureException("conflict", null))
                    .when(optimisticLockRetry).execute(any());

            assertThatThrownBy(() -> service.update(USER_ID, API_KEY_ID, "name", null, null))
                    .isInstanceOf(ApiKeyUpdateFailure.class);

            verify(apiKeyRepository, never()).save(any());
            verify(apiKeyNextRepository, never()).save(any());
            verify(eventEmitterService, never()).emitUpdated(any());
        }

        @Test
        @DisplayName("cleans up old next entity before applying new one")
        void cleansUpOldNext() {
            ApiKeyEntity entity = existingEntity();
            ApiKeyNextEntity oldNext = pendingNext(ApiKeyUpdateStatus.FAILED);
            entity.setNext(oldNext);
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.of(entity));

            ApiKeyDto result = service.update(USER_ID, API_KEY_ID, "updated-name", null, null);

            verify(apiKeyNextRepository).delete(oldNext);
            verify(apiKeyRepository).save(entityCaptor.capture());
            verify(apiKeyNextRepository).save(nextEntityCaptor.capture());

            ApiKeyEntity saved = entityCaptor.getValue();
            assertThat(saved.getIssuedVersion()).isEqualTo(2);
            assertThat(saved.getDesiredVersion()).isEqualTo(2);

            ApiKeyNextEntity nextSaved = nextEntityCaptor.getValue();
            assertNextEntityMatches(nextSaved, 2, NOW, ApiKeyUpdateType.UPSERT,
                    ApiKeyUpdateStatus.PENDING, "updated-name", DEFAULT_PERMISSIONS, EXPIRES_AT);

            verify(eventEmitterService).emitUpdated(saved);

            assertDtoMatches(result, API_KEY_ID, USER_ID, ACCESS_ID, null, null,
                    API_KEY_NAME, DEFAULT_PERMISSIONS, NOW, EXPIRES_AT, ApiKeyUpdateStatus.PENDING);
        }

        @Test
        @DisplayName("no-op when computed expiry equals current expiry")
        void noOpSameExpiry() {
            ApiKeyEntity entity = existingEntity();
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.of(entity));
            when(apiKeyExpiry.getExpiry(NOW, ApiKeyExpiry.Duration.ONE_MONTH)).thenReturn(EXPIRES_AT);

            ApiKeyDto result = service.update(USER_ID, API_KEY_ID, null, ApiKeyExpiry.Duration.ONE_MONTH, null);

            verify(apiKeyRepository, never()).save(any());
            verify(apiKeyNextRepository, never()).save(any());
            verify(eventEmitterService, never()).emitUpdated(any());

            assertThat(entity.getIssuedVersion()).isEqualTo(1);
            assertThat(entity.getDesiredVersion()).isEqualTo(1);
            assertThat(entity.getNext()).isNull();

            assertDtoMatches(result, API_KEY_ID, USER_ID, ACCESS_ID, null, null,
                    API_KEY_NAME, DEFAULT_PERMISSIONS, NOW, EXPIRES_AT, null);
        }

        @Test
        @DisplayName("null name in request does not count as a change")
        void nullNameSkipped() {
            ApiKeyEntity entity = existingEntity();
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.of(entity));

            ApiKeyDto result = service.update(USER_ID, API_KEY_ID, null, null, null);

            verify(apiKeyRepository, never()).save(any());
            verify(apiKeyNextRepository, never()).save(any());
            verify(eventEmitterService, never()).emitUpdated(any());

            assertThat(entity.getIssuedVersion()).isEqualTo(1);
            assertThat(entity.getDesiredVersion()).isEqualTo(1);
            assertThat(entity.getNext()).isNull();

            assertDtoMatches(result, API_KEY_ID, USER_ID, ACCESS_ID, null, null,
                    API_KEY_NAME, DEFAULT_PERMISSIONS, NOW, EXPIRES_AT, null);
        }
    }

    // ========================================================================================
    // acknowledgeFailure()
    // ========================================================================================

    @Nested
    @DisplayName("acknowledgeFailure()")
    class AcknowledgeFailure {

        @Test
        @DisplayName("clears failed next entity")
        void happyPath() {
            ApiKeyEntity entity = existingEntity();
            ApiKeyNextEntity failedNext = pendingNext(ApiKeyUpdateStatus.FAILED);
            entity.setNext(failedNext);
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.of(entity));

            service.acknowledgeFailure(USER_ID, API_KEY_ID);

            verify(apiKeyNextRepository).delete(failedNext);
            assertThat(entity.getNext()).isNull();
            verify(apiKeyRepository, never()).save(any());
            verify(eventEmitterService, never()).emitCreated(any());
            verify(eventEmitterService, never()).emitUpdated(any());
            verify(eventEmitterService, never()).emitDeleted(any());
        }

        @Test
        @DisplayName("throws ApiKeyNotFoundException when entity has pending creation")
        void pendingCreation() {
            ApiKeyEntity entity = existingEntity();
            entity.setAppliedVersion(ApiKeyEntity.NON_EXISTENT);
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.acknowledgeFailure(USER_ID, API_KEY_ID))
                    .isInstanceOf(ApiKeyNotFoundException.class);

            verify(apiKeyNextRepository, never()).delete(any());
        }

        @Test
        @DisplayName("throws ApiKeyUpdateFailure when next is null (no failure to acknowledge)")
        void noFailureNextNull() {
            ApiKeyEntity entity = existingEntity();
            entity.setNext(null);
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.acknowledgeFailure(USER_ID, API_KEY_ID))
                    .isInstanceOf(ApiKeyUpdateFailure.class)
                    .hasMessage("API key doesn't have a failure to acknowledge");

            verify(apiKeyNextRepository, never()).delete(any());
        }

        @Test
        @DisplayName("throws ApiKeyUpdateFailure when next has PENDING status")
        void noFailurePendingStatus() {
            ApiKeyEntity entity = existingEntity();
            entity.setNext(pendingNext(ApiKeyUpdateStatus.PENDING));
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.acknowledgeFailure(USER_ID, API_KEY_ID))
                    .isInstanceOf(ApiKeyUpdateFailure.class)
                    .hasMessage("API key doesn't have a failure to acknowledge");

            verify(apiKeyNextRepository, never()).delete(any());
        }

        @Test
        @DisplayName("throws ApiKeyNotFoundException when entity not found")
        void notFound() {
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.acknowledgeFailure(USER_ID, API_KEY_ID))
                    .isInstanceOf(ApiKeyNotFoundException.class);

            verify(apiKeyNextRepository, never()).delete(any());
        }
    }

    // ========================================================================================
    // delete()
    // ========================================================================================

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("marks entity for deletion with DELETE next entity")
        void happyPath() {
            ApiKeyEntity entity = existingEntity();
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.of(entity));

            ApiKeyDto result = service.delete(USER_ID, API_KEY_ID);

            assertThat(entity.getIssuedVersion()).isEqualTo(2);
            assertThat(entity.getDesiredVersion()).isEqualTo(2);

            ApiKeyNextEntity next = entity.getNext();
            assertThat(next).isNotNull();
            assertNextEntityMatches(next, 2, NOW, ApiKeyUpdateType.DELETE,
                    ApiKeyUpdateStatus.PENDING, API_KEY_NAME, DEFAULT_PERMISSIONS, EXPIRES_AT);

            verify(eventEmitterService).emitDeleted(entity);

            assertDtoMatches(result, API_KEY_ID, USER_ID, ACCESS_ID, null, null,
                    API_KEY_NAME, DEFAULT_PERMISSIONS, NOW, EXPIRES_AT, ApiKeyUpdateStatus.PENDING);
        }

        @Test
        @DisplayName("throws ApiKeyNotFoundException when entity has pending creation")
        void pendingCreation() {
            ApiKeyEntity entity = existingEntity();
            entity.setAppliedVersion(ApiKeyEntity.NON_EXISTENT);
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.delete(USER_ID, API_KEY_ID))
                    .isInstanceOf(ApiKeyNotFoundException.class)
                    .hasMessage("Can't delete an API key with pending creation");

            verify(eventEmitterService, never()).emitDeleted(any());
        }

        @Test
        @DisplayName("throws ApiKeyUpdateFailure when entity has pending changes")
        void pendingChanges() {
            ApiKeyEntity entity = existingEntity();
            entity.setNext(pendingNext(ApiKeyUpdateStatus.PENDING));
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.delete(USER_ID, API_KEY_ID))
                    .isInstanceOf(ApiKeyUpdateFailure.class)
                    .hasMessage("Can't delete an API key with pending changes");

            verify(eventEmitterService, never()).emitDeleted(any());
        }

        @Test
        @DisplayName("throws ApiKeyNotFoundException when entity not found")
        void notFound() {
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(USER_ID, API_KEY_ID))
                    .isInstanceOf(ApiKeyNotFoundException.class);

            verify(eventEmitterService, never()).emitDeleted(any());
        }

        @Test
        @DisplayName("cleans up old failed next entity before setting delete next")
        void cleansUpOldNext() {
            ApiKeyEntity entity = existingEntity();
            ApiKeyNextEntity failedNext = pendingNext(ApiKeyUpdateStatus.FAILED);
            entity.setNext(failedNext);
            when(apiKeyRepository.findById(API_KEY_ID)).thenReturn(Optional.of(entity));

            ApiKeyDto result = service.delete(USER_ID, API_KEY_ID);

            verify(apiKeyNextRepository).delete(failedNext);
            assertThat(entity.getNext()).isNotNull();
            assertNextEntityMatches(entity.getNext(), 2, NOW, ApiKeyUpdateType.DELETE,
                    ApiKeyUpdateStatus.PENDING, API_KEY_NAME, DEFAULT_PERMISSIONS, EXPIRES_AT);

            verify(eventEmitterService).emitDeleted(entity);

            assertDtoMatches(result, API_KEY_ID, USER_ID, ACCESS_ID, null, null,
                    API_KEY_NAME, DEFAULT_PERMISSIONS, NOW, EXPIRES_AT, ApiKeyUpdateStatus.PENDING);
        }
    }
}
