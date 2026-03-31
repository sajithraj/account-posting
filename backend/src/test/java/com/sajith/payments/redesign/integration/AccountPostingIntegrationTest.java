package com.sajith.payments.redesign.integration;

import com.sajith.payments.redesign.dto.accountposting.AccountPostingCreateResponseV2;
import com.sajith.payments.redesign.dto.accountposting.AccountPostingFullResponseV2;
import com.sajith.payments.redesign.dto.accountposting.Amount;
import com.sajith.payments.redesign.dto.accountposting.IncomingPostingRequest;
import com.sajith.payments.redesign.dto.accountpostingleg.LegCreateResponseV2;
import com.sajith.payments.redesign.dto.accountpostingleg.LegResponseV2;
import com.sajith.payments.redesign.dto.retry.RetryRequestV2;
import com.sajith.payments.redesign.dto.retry.RetryResponseV2;
import com.sajith.payments.redesign.dto.search.PostingSearchRequestV2;
import com.sajith.payments.redesign.dto.search.PostingSearchResponseV2;
import com.sajith.payments.redesign.dto.search.SearchCondition;
import com.sajith.payments.redesign.dto.search.SearchOrderBy;
import com.sajith.payments.redesign.dto.search.SearchPagination;
import com.sajith.payments.redesign.entity.AccountPostingEntity;
import com.sajith.payments.redesign.entity.AccountPostingLegEntity;
import com.sajith.payments.redesign.entity.PostingConfig;
import com.sajith.payments.redesign.entity.enums.LegMode;
import com.sajith.payments.redesign.entity.enums.LegStatus;
import com.sajith.payments.redesign.entity.enums.PostingStatus;
import com.sajith.payments.redesign.exception.BusinessException;
import com.sajith.payments.redesign.exception.ResourceNotFoundException;
import com.sajith.payments.redesign.repository.AccountPostingLegRepository;
import com.sajith.payments.redesign.repository.AccountPostingRepository;
import com.sajith.payments.redesign.repository.PostingConfigRepository;
import com.sajith.payments.redesign.service.accountposting.AccountPostingServiceV2;
import com.sajith.payments.redesign.service.accountposting.strategy.PostingStrategy;
import com.sajith.payments.redesign.service.accountposting.strategy.PostingStrategyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

/**
 * Integration tests for the Account Posting service.
 * <p>
 * Uses a real PostgreSQL database via Testcontainers. All tests persist data to the DB
 * and verify both the service response and the DB state directly.
 * <p>
 * Scenarios covered:
 * - All request types with all legs succeeding
 * - Partial failure (first leg fails, second succeeds; vice-versa)
 * - All legs fail (single, dual, triple-leg postings)
 * - Business rule violations (duplicate E2E ref, no config, invalid enums)
 * - Retry: single, bulk, specific IDs, still-failing, locked, no-eligible
 * - Search: by status, sourceName, requestType, combined criteria, paginated
 * - findById: full response with legs and timestamps
 * - Bulk data load: creates all request types + mixed outcomes for DB visibility
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration-test")
@Testcontainers
class AccountPostingIntegrationTest {

    // ══════════════════════════════════════════════════════════════════
    // Container + property source
    // ══════════════════════════════════════════════════════════════════

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("account_posting_test")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // ══════════════════════════════════════════════════════════════════
    // Injected beans
    // ══════════════════════════════════════════════════════════════════

    @Autowired
    private AccountPostingServiceV2 service;

    @Autowired
    private AccountPostingRepository postingRepo;

    @Autowired
    private AccountPostingLegRepository legRepo;

    @Autowired
    private PostingConfigRepository configRepo;

    /**
     * Spy on the factory so individual tests can make specific target-system strategies fail
     * by returning a custom PostingStrategy from resolve().
     * When unstubbed (after reset), calls delegate to the real factory (stubs always return SUCCESS).
     */
    @MockitoSpyBean
    private PostingStrategyFactory strategyFactory;

    // Per-test instance counter — each test class instance starts at 1000
    private final AtomicLong seq = new AtomicLong(1_000);

    // ══════════════════════════════════════════════════════════════════
    // Setup / teardown
    // ══════════════════════════════════════════════════════════════════

    @BeforeEach
    void setUp() {
        // Delete in FK order: legs → postings → configs
        legRepo.deleteAll();
        postingRepo.deleteAll();
        configRepo.deleteAll();
        seedConfigs();
        reset(strategyFactory);   // ensure real resolve() for each test
    }

    // ══════════════════════════════════════════════════════════════════
    // Config seeding
    // ══════════════════════════════════════════════════════════════════

    /**
     * Seeds one row per (requestType, orderSeq) pair.
     * Maps every RequestType enum value to its target systems in correct order.
     */
    private void seedConfigs() {
        configRepo.saveAll(List.of(
                // IMX_CBS_GL  → CBS (1) → GL (2)
                cfg("IMX", "IMX_CBS_GL", "CBS", 1),
                cfg("IMX", "IMX_CBS_GL", "GL", 2),
                // IMX_OBPM    → OBPM (1)
                cfg("IMX", "IMX_OBPM", "OBPM", 1),
                // FED_RETURN  → CBS (1) → GL (2)
                cfg("RMS", "FED_RETURN", "CBS", 1),
                cfg("RMS", "FED_RETURN", "GL", 2),
                // GL_RETURN   → GL (1)
                cfg("IMX", "GL_RETURN", "GL", 1),
                // MCA_RETURN  → CBS (1)
                cfg("RMS", "MCA_RETURN", "CBS", 1),
                // BUY_CUSTOMER_POSTING → OBPM (1) → GL (2)
                cfg("STABLECOIN", "BUY_CUSTOMER_POSTING", "OBPM", 1),
                cfg("STABLECOIN", "BUY_CUSTOMER_POSTING", "GL", 2),
                // ADD_ACCOUNT_HOLD → CBS (1)
                cfg("IMX", "ADD_ACCOUNT_HOLD", "CBS", 1),
                // CUSTOMER_POSTING → CBS (1) → OBPM (2) → GL (3)
                cfg("IMX", "CUSTOMER_POSTING", "CBS", 1),
                cfg("IMX", "CUSTOMER_POSTING", "OBPM", 2),
                cfg("IMX", "CUSTOMER_POSTING", "GL", 3)
        ));
    }

    private PostingConfig cfg(String sourceName, String requestType,
                              String targetSystem, int orderSeq) {
        PostingConfig c = new PostingConfig();
        c.setSourceName(sourceName);
        c.setRequestType(requestType);
        c.setTargetSystem(targetSystem);
        c.setOperation("POSTING");
        c.setOrderSeq(orderSeq);
        c.setCreatedBy("SYSTEM");
        c.setUpdatedBy("SYSTEM");
        return c;
    }

    // ══════════════════════════════════════════════════════════════════
    // Request builder
    // ══════════════════════════════════════════════════════════════════

    /**
     * Builds a valid IncomingPostingRequest with a unique E2E ref per call.
     */
    private IncomingPostingRequest req(String sourceName, String requestType) {
        long n = seq.getAndIncrement();
        IncomingPostingRequest r = new IncomingPostingRequest();
        r.setSourceRefId("SRC-IT-" + n);
        r.setEndToEndRefId("E2E-IT-" + n);
        r.setSourceName(sourceName);
        r.setRequestType(requestType);
        Amount amount = new Amount();
        amount.setValue(new java.math.BigDecimal("1000.00").add(java.math.BigDecimal.valueOf(n)).toPlainString());
        amount.setCurrency("USD");
        r.setAmount(amount);
        r.setCreditDebitIndicator("CREDIT");
        r.setDebtorAccount("DEBTOR-" + String.format("%010d", n));
        r.setCreditorAccount("CREDITOR-" + String.format("%010d", n));
        r.setRequestedExecutionDate(LocalDate.of(2026, 3, 21).toString());
        r.setRemittanceInformation("Integration test #" + n);
        return r;
    }

    // ══════════════════════════════════════════════════════════════════
    // Failure strategy helper
    // ══════════════════════════════════════════════════════════════════

    /**
     * Returns a PostingStrategy that:
     * 1. Updates the pre-inserted PENDING leg in DB to FAILED (so DB state is accurate)
     * 2. Returns a FAILED LegResponseV2
     * <p>
     * Used with: doReturn(failStrategy("reason")).when(strategyFactory).resolve("CBS_POSTING")
     */
    private PostingStrategy failStrategy(String reason) {
        AccountPostingLegRepository repo = legRepo;
        return new PostingStrategy() {
            @Override
            public String getPostingFlow() {
                return "FAIL_TEST";
            }

            @Override
            public LegResponseV2 process(Long postingId, int legOrder,
                                         IncomingPostingRequest request,
                                         boolean isRetry, Long existingLegId) {
                if (existingLegId != null) {
                    repo.findById(existingLegId).ifPresent(leg -> {
                        leg.setStatus(LegStatus.FAILED);
                        leg.setReason(reason);
                        leg.setPostedTime(Instant.now());
                        repo.save(leg);
                    });
                }
                LegResponseV2 lr = new LegResponseV2();
                lr.setStatus("FAILED");
                lr.setReason(reason);
                return lr;
            }
        };
    }

    // ══════════════════════════════════════════════════════════════════
    // DB helpers
    // ══════════════════════════════════════════════════════════════════

    private Long findPostingIdByE2e(String e2eRef) {
        return postingRepo.findAll().stream()
                .filter(p -> e2eRef.equals(p.getEndToEndReferenceId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No posting found for E2E: " + e2eRef))
                .getPostingId();
    }

    private AccountPostingEntity getPosting(Long postingId) {
        return postingRepo.findById(postingId).orElseThrow();
    }

    private List<AccountPostingLegEntity> getLegs(Long postingId) {
        return legRepo.findByPostingIdOrderByTransactionOrder(postingId);
    }

    // ══════════════════════════════════════════════════════════════════
    // ── 1. SINGLE-LEG SUCCESS SCENARIOS ──────────────────────────────
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Single-leg request types — all SUCCESS")
    class SingleLegSuccess {

        @Test
        @DisplayName("IMX + IMX_OBPM → 1 OBPM leg persisted as SUCCESS")
        void imx_obpm_singleObpmLeg_success() {
            IncomingPostingRequest request = req("IMX", "IMX_OBPM");
            AccountPostingCreateResponseV2 response = service.create(request);

            assertThat(response.getPostingStatus()).isEqualTo(PostingStatus.ACSP);
            assertThat(response.getSourceReferenceId()).isEqualTo(request.getSourceRefId());
            assertThat(response.getEndToEndReferenceId()).isEqualTo(request.getEndToEndRefId());
            assertThat(response.getResponses()).hasSize(1);
            assertThat(response.getResponses().get(0).getName()).isEqualTo("OBPM");
            assertThat(response.getResponses().get(0).getStatus()).isEqualTo("SUCCESS");
            assertThat(response.getResponses().get(0).getReferenceId()).isNotBlank();

            Long postingId = findPostingIdByE2e(request.getEndToEndRefId());
            AccountPostingEntity dbPosting = getPosting(postingId);
            assertThat(dbPosting.getStatus()).isEqualTo(PostingStatus.ACSP);
            assertThat(dbPosting.getTargetSystems()).isEqualTo("OBPM");
            assertThat(dbPosting.getReason()).isEqualTo("Request processed successfully");
            assertThat(dbPosting.getSourceName()).isEqualTo("IMX");
            assertThat(dbPosting.getRequestType()).isEqualTo("IMX_OBPM");
            assertThat(dbPosting.getRequestPayload()).isNotBlank();
            assertThat(dbPosting.getResponsePayload()).isNotBlank();

            List<AccountPostingLegEntity> legs = getLegs(postingId);
            assertThat(legs).hasSize(1);
            assertThat(legs.get(0).getTargetSystem()).isEqualTo("OBPM");
            assertThat(legs.get(0).getStatus()).isEqualTo(LegStatus.SUCCESS);
            assertThat(legs.get(0).getTransactionOrder()).isEqualTo(1);
            assertThat(legs.get(0).getMode()).isEqualTo(LegMode.NORM);
            assertThat(legs.get(0).getAttemptNumber()).isEqualTo(1);
            assertThat(legs.get(0).getPostedTime()).isNotNull();
            assertThat(legs.get(0).getReferenceId()).isNotBlank();
        }

        @Test
        @DisplayName("IMX + ADD_ACCOUNT_HOLD → 1 CBS leg persisted as SUCCESS")
        void imx_addAccountHold_cbsOnly_success() {
            IncomingPostingRequest request = req("IMX", "ADD_ACCOUNT_HOLD");
            AccountPostingCreateResponseV2 response = service.create(request);

            assertThat(response.getPostingStatus()).isEqualTo(PostingStatus.ACSP);
            assertThat(response.getResponses()).hasSize(1);

            Long postingId = findPostingIdByE2e(request.getEndToEndRefId());
            AccountPostingEntity dbPosting = getPosting(postingId);
            assertThat(dbPosting.getStatus()).isEqualTo(PostingStatus.ACSP);
            assertThat(dbPosting.getTargetSystems()).isEqualTo("CBS");

            List<AccountPostingLegEntity> legs = getLegs(postingId);
            assertThat(legs).hasSize(1);
            assertThat(legs.get(0).getTargetSystem()).isEqualTo("CBS");
            assertThat(legs.get(0).getStatus()).isEqualTo(LegStatus.SUCCESS);
        }

        @Test
        @DisplayName("RMS + MCA_RETURN → 1 CBS leg persisted as SUCCESS")
        void rms_mcaReturn_cbsOnly_success() {
            IncomingPostingRequest request = req("RMS", "MCA_RETURN");
            AccountPostingCreateResponseV2 response = service.create(request);

            assertThat(response.getPostingStatus()).isEqualTo(PostingStatus.ACSP);

            Long postingId = findPostingIdByE2e(request.getEndToEndRefId());
            AccountPostingEntity dbPosting = getPosting(postingId);
            assertThat(dbPosting.getStatus()).isEqualTo(PostingStatus.ACSP);
            assertThat(dbPosting.getSourceName()).isEqualTo("RMS");
            assertThat(dbPosting.getTargetSystems()).isEqualTo("CBS");

            List<AccountPostingLegEntity> legs = getLegs(postingId);
            assertThat(legs).hasSize(1);
            assertThat(legs.get(0).getTargetSystem()).isEqualTo("CBS");
            assertThat(legs.get(0).getStatus()).isEqualTo(LegStatus.SUCCESS);
        }

        @Test
        @DisplayName("IMX + GL_RETURN → 1 GL leg persisted as SUCCESS")
        void imx_glReturn_glOnly_success() {
            IncomingPostingRequest request = req("IMX", "GL_RETURN");
            AccountPostingCreateResponseV2 response = service.create(request);

            assertThat(response.getPostingStatus()).isEqualTo(PostingStatus.ACSP);

            Long postingId = findPostingIdByE2e(request.getEndToEndRefId());
            AccountPostingEntity dbPosting = getPosting(postingId);
            assertThat(dbPosting.getStatus()).isEqualTo(PostingStatus.ACSP);
            assertThat(dbPosting.getTargetSystems()).isEqualTo("GL");

            List<AccountPostingLegEntity> legs = getLegs(postingId);
            assertThat(legs).hasSize(1);
            assertThat(legs.get(0).getTargetSystem()).isEqualTo("GL");
            assertThat(legs.get(0).getStatus()).isEqualTo(LegStatus.SUCCESS);
            assertThat(legs.get(0).getReferenceId()).isNotBlank();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // ── 2. TWO-LEG SUCCESS SCENARIOS ─────────────────────────────────
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Two-leg request types — all SUCCESS")
    class TwoLegSuccess {

        @Test
        @DisplayName("IMX + IMX_CBS_GL → CBS(1) + GL(2), both SUCCESS in correct order")
        void imx_cbsGl_twoLegs_bothSuccess() {
            IncomingPostingRequest request = req("IMX", "IMX_CBS_GL");
            AccountPostingCreateResponseV2 response = service.create(request);

            assertThat(response.getPostingStatus()).isEqualTo(PostingStatus.ACSP);
            assertThat(response.getResponses()).hasSize(2);

            Long postingId = findPostingIdByE2e(request.getEndToEndRefId());
            AccountPostingEntity dbPosting = getPosting(postingId);
            assertThat(dbPosting.getStatus()).isEqualTo(PostingStatus.ACSP);
            assertThat(dbPosting.getTargetSystems()).isEqualTo("CBS_GL");
            assertThat(dbPosting.getAmount()).isEqualByComparingTo(request.getAmount().getValue());
            assertThat(dbPosting.getCurrency()).isEqualTo("USD");
            assertThat(dbPosting.getDebtorAccount()).isEqualTo(request.getDebtorAccount());
            assertThat(dbPosting.getCreditorAccount()).isEqualTo(request.getCreditorAccount());

            List<AccountPostingLegEntity> legs = getLegs(postingId);
            assertThat(legs).hasSize(2);

            AccountPostingLegEntity cbsLeg = legs.get(0);
            assertThat(cbsLeg.getTargetSystem()).isEqualTo("CBS");
            assertThat(cbsLeg.getStatus()).isEqualTo(LegStatus.SUCCESS);
            assertThat(cbsLeg.getTransactionOrder()).isEqualTo(1);
            assertThat(cbsLeg.getMode()).isEqualTo(LegMode.NORM);
            assertThat(cbsLeg.getAttemptNumber()).isEqualTo(1);
            assertThat(cbsLeg.getReferenceId()).isNotBlank();
            assertThat(cbsLeg.getPostedTime()).isNotNull();

            AccountPostingLegEntity glLeg = legs.get(1);
            assertThat(glLeg.getTargetSystem()).isEqualTo("GL");
            assertThat(glLeg.getStatus()).isEqualTo(LegStatus.SUCCESS);
            assertThat(glLeg.getTransactionOrder()).isEqualTo(2);
            assertThat(glLeg.getReferenceId()).isNotBlank();
        }

        @Test
        @DisplayName("RMS + FED_RETURN → CBS(1) + GL(2), both SUCCESS")
        void rms_fedReturn_twoLegs_bothSuccess() {
            IncomingPostingRequest request = req("RMS", "FED_RETURN");
            AccountPostingCreateResponseV2 response = service.create(request);

            assertThat(response.getPostingStatus()).isEqualTo(PostingStatus.ACSP);

            Long postingId = findPostingIdByE2e(request.getEndToEndRefId());
            AccountPostingEntity dbPosting = getPosting(postingId);
            assertThat(dbPosting.getStatus()).isEqualTo(PostingStatus.ACSP);
            assertThat(dbPosting.getSourceName()).isEqualTo("RMS");
            assertThat(dbPosting.getTargetSystems()).isEqualTo("CBS_GL");

            List<AccountPostingLegEntity> legs = getLegs(postingId);
            assertThat(legs).hasSize(2);
            assertThat(legs).extracting(AccountPostingLegEntity::getTargetSystem)
                    .containsExactly("CBS", "GL");
            assertThat(legs).extracting(AccountPostingLegEntity::getStatus)
                    .containsOnly(LegStatus.SUCCESS);
        }

        @Test
        @DisplayName("STABLECOIN + BUY_CUSTOMER_POSTING → OBPM(1) + GL(2), both SUCCESS")
        void stablecoin_buyCustomerPostng_twoLegs_bothSuccess() {
            IncomingPostingRequest request = req("STABLECOIN", "BUY_CUSTOMER_POSTING");
            AccountPostingCreateResponseV2 response = service.create(request);

            assertThat(response.getPostingStatus()).isEqualTo(PostingStatus.ACSP);

            Long postingId = findPostingIdByE2e(request.getEndToEndRefId());
            AccountPostingEntity dbPosting = getPosting(postingId);
            assertThat(dbPosting.getStatus()).isEqualTo(PostingStatus.ACSP);
            assertThat(dbPosting.getTargetSystems()).isEqualTo("OBPM_GL");

            List<AccountPostingLegEntity> legs = getLegs(postingId);
            assertThat(legs).hasSize(2);
            assertThat(legs.get(0).getTargetSystem()).isEqualTo("OBPM");
            assertThat(legs.get(0).getStatus()).isEqualTo(LegStatus.SUCCESS);
            assertThat(legs.get(1).getTargetSystem()).isEqualTo("GL");
            assertThat(legs.get(1).getStatus()).isEqualTo(LegStatus.SUCCESS);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // ── 3. THREE-LEG SUCCESS SCENARIO ────────────────────────────────
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Three-leg request type — all SUCCESS")
    class ThreeLegSuccess {

        @Test
        @DisplayName("IMX + CUSTOMER_POSTING → CBS(1) + OBPM(2) + GL(3), all 3 legs SUCCESS")
        void imx_customerPosting_threeLegs_allSuccess() {
            IncomingPostingRequest request = req("IMX", "CUSTOMER_POSTING");
            AccountPostingCreateResponseV2 response = service.create(request);

            assertThat(response.getPostingStatus()).isEqualTo(PostingStatus.ACSP);
            assertThat(response.getResponses()).hasSize(3);
            assertThat(response.getResponses())
                    .extracting(LegCreateResponseV2::getName)
                    .containsExactly("CBS", "OBPM", "GL");

            Long postingId = findPostingIdByE2e(request.getEndToEndRefId());
            AccountPostingEntity dbPosting = getPosting(postingId);
            assertThat(dbPosting.getStatus()).isEqualTo(PostingStatus.ACSP);
            assertThat(dbPosting.getTargetSystems()).isEqualTo("CBS_OBPM_GL");

            List<AccountPostingLegEntity> legs = getLegs(postingId);
            assertThat(legs).hasSize(3);
            assertThat(legs).extracting(AccountPostingLegEntity::getTargetSystem)
                    .containsExactly("CBS", "OBPM", "GL");
            assertThat(legs).extracting(AccountPostingLegEntity::getTransactionOrder)
                    .containsExactly(1, 2, 3);
            assertThat(legs).extracting(AccountPostingLegEntity::getStatus)
                    .containsOnly(LegStatus.SUCCESS);
            assertThat(legs).extracting(AccountPostingLegEntity::getMode)
                    .containsOnly(LegMode.NORM);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // ── 4. FAILURE SCENARIOS ─────────────────────────────────────────
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Leg failure scenarios — posting ends up PENDING")
    class FailureScenarios {

        @Test
        @DisplayName("IMX_CBS_GL: CBS(leg 1) FAILS, GL(leg 2) SUCCESS → posting PENDING")
        void cbsGl_firstLegFails_secondSucceeds_postingPending() {
            doReturn(failStrategy("CBS connection timeout")).when(strategyFactory).resolve("CBS_POSTING");

            IncomingPostingRequest request = req("IMX", "IMX_CBS_GL");
            AccountPostingCreateResponseV2 response = service.create(request);

            assertThat(response.getPostingStatus()).isEqualTo(PostingStatus.PNDG);
            assertThat(response.getResponses()).hasSize(2);

            Long postingId = findPostingIdByE2e(request.getEndToEndRefId());
            AccountPostingEntity dbPosting = getPosting(postingId);
            assertThat(dbPosting.getStatus()).isEqualTo(PostingStatus.PNDG);
            assertThat(dbPosting.getTargetSystems()).isEqualTo("CBS_GL");

            List<AccountPostingLegEntity> legs = getLegs(postingId);
            assertThat(legs).hasSize(2);
            assertThat(legs.get(0).getTargetSystem()).isEqualTo("CBS");
            assertThat(legs.get(0).getStatus()).isEqualTo(LegStatus.FAILED);
            assertThat(legs.get(0).getReason()).isEqualTo("CBS connection timeout");
            assertThat(legs.get(1).getTargetSystem()).isEqualTo("GL");
            assertThat(legs.get(1).getStatus()).isEqualTo(LegStatus.SUCCESS);
        }

        @Test
        @DisplayName("IMX_CBS_GL: CBS(leg 1) SUCCESS, GL(leg 2) FAILS → posting PENDING")
        void cbsGl_firstSucceeds_secondFails_postingPending() {
            doReturn(failStrategy("GL ledger unavailable")).when(strategyFactory).resolve("GL_POSTING");

            IncomingPostingRequest request = req("IMX", "IMX_CBS_GL");
            AccountPostingCreateResponseV2 response = service.create(request);

            assertThat(response.getPostingStatus()).isEqualTo(PostingStatus.PNDG);

            Long postingId = findPostingIdByE2e(request.getEndToEndRefId());
            List<AccountPostingLegEntity> legs = getLegs(postingId);
            assertThat(legs.get(0).getTargetSystem()).isEqualTo("CBS");
            assertThat(legs.get(0).getStatus()).isEqualTo(LegStatus.SUCCESS);
            assertThat(legs.get(1).getTargetSystem()).isEqualTo("GL");
            assertThat(legs.get(1).getStatus()).isEqualTo(LegStatus.FAILED);
            assertThat(legs.get(1).getReason()).isEqualTo("GL ledger unavailable");
        }

        @Test
        @DisplayName("IMX_CBS_GL: BOTH CBS and GL FAIL → posting PENDING, both legs FAILED")
        void cbsGl_bothLegsFail_postingPending() {
            doReturn(failStrategy("CBS timeout")).when(strategyFactory).resolve("CBS_POSTING");
            doReturn(failStrategy("GL timeout")).when(strategyFactory).resolve("GL_POSTING");

            IncomingPostingRequest request = req("IMX", "IMX_CBS_GL");
            AccountPostingCreateResponseV2 response = service.create(request);

            assertThat(response.getPostingStatus()).isEqualTo(PostingStatus.PNDG);

            Long postingId = findPostingIdByE2e(request.getEndToEndRefId());
            AccountPostingEntity dbPosting = getPosting(postingId);
            assertThat(dbPosting.getStatus()).isEqualTo(PostingStatus.PNDG);

            List<AccountPostingLegEntity> legs = getLegs(postingId);
            assertThat(legs).hasSize(2);
            assertThat(legs).extracting(AccountPostingLegEntity::getStatus)
                    .containsOnly(LegStatus.FAILED);
        }

        @Test
        @DisplayName("IMX_OBPM: single OBPM leg FAILS → posting PENDING")
        void imxObpm_singleLegFails_postingPending() {
            doReturn(failStrategy("OBPM timeout")).when(strategyFactory).resolve("OBPM_POSTING");

            IncomingPostingRequest request = req("IMX", "IMX_OBPM");
            AccountPostingCreateResponseV2 response = service.create(request);

            assertThat(response.getPostingStatus()).isEqualTo(PostingStatus.PNDG);

            Long postingId = findPostingIdByE2e(request.getEndToEndRefId());
            List<AccountPostingLegEntity> legs = getLegs(postingId);
            assertThat(legs).hasSize(1);
            assertThat(legs.get(0).getStatus()).isEqualTo(LegStatus.FAILED);
            assertThat(legs.get(0).getReason()).isEqualTo("OBPM timeout");
        }

        @Test
        @DisplayName("BUY_CUSTOMER_POSTING: OBPM(leg 1) FAILS, GL(leg 2) SUCCESS → posting PENDING")
        void stablecoin_obpmFails_glSucceeds_postingPending() {
            doReturn(failStrategy("OBPM service down")).when(strategyFactory).resolve("OBPM_POSTING");

            IncomingPostingRequest request = req("STABLECOIN", "BUY_CUSTOMER_POSTING");
            AccountPostingCreateResponseV2 response = service.create(request);

            assertThat(response.getPostingStatus()).isEqualTo(PostingStatus.PNDG);

            Long postingId = findPostingIdByE2e(request.getEndToEndRefId());
            List<AccountPostingLegEntity> legs = getLegs(postingId);
            assertThat(legs.get(0).getTargetSystem()).isEqualTo("OBPM");
            assertThat(legs.get(0).getStatus()).isEqualTo(LegStatus.FAILED);
            assertThat(legs.get(1).getTargetSystem()).isEqualTo("GL");
            assertThat(legs.get(1).getStatus()).isEqualTo(LegStatus.SUCCESS);
        }

        @Test
        @DisplayName("BUY_CUSTOMER_POSTING: OBPM(leg 1) SUCCESS, GL(leg 2) FAILS → posting PENDING")
        void stablecoin_obpmSucceeds_glFails_postingPending() {
            doReturn(failStrategy("GL quota exceeded")).when(strategyFactory).resolve("GL_POSTING");

            IncomingPostingRequest request = req("STABLECOIN", "BUY_CUSTOMER_POSTING");
            AccountPostingCreateResponseV2 response = service.create(request);

            assertThat(response.getPostingStatus()).isEqualTo(PostingStatus.PNDG);

            Long postingId = findPostingIdByE2e(request.getEndToEndRefId());
            List<AccountPostingLegEntity> legs = getLegs(postingId);
            assertThat(legs.get(0).getStatus()).isEqualTo(LegStatus.SUCCESS);
            assertThat(legs.get(1).getStatus()).isEqualTo(LegStatus.FAILED);
        }

        @Test
        @DisplayName("CUSTOMER_POSTING (3 legs): CBS(1) SUCCESS, OBPM(2) FAILS, GL(3) SUCCESS → PENDING")
        void customerPosting_middleLegFails_postingPending() {
            doReturn(failStrategy("OBPM unavailable")).when(strategyFactory).resolve("OBPM_POSTING");

            IncomingPostingRequest request = req("IMX", "CUSTOMER_POSTING");
            AccountPostingCreateResponseV2 response = service.create(request);

            assertThat(response.getPostingStatus()).isEqualTo(PostingStatus.PNDG);

            Long postingId = findPostingIdByE2e(request.getEndToEndRefId());
            List<AccountPostingLegEntity> legs = getLegs(postingId);
            assertThat(legs).hasSize(3);
            assertThat(legs.get(0).getTargetSystem()).isEqualTo("CBS");
            assertThat(legs.get(0).getStatus()).isEqualTo(LegStatus.SUCCESS);
            assertThat(legs.get(1).getTargetSystem()).isEqualTo("OBPM");
            assertThat(legs.get(1).getStatus()).isEqualTo(LegStatus.FAILED);
            assertThat(legs.get(2).getTargetSystem()).isEqualTo("GL");
            assertThat(legs.get(2).getStatus()).isEqualTo(LegStatus.SUCCESS);
        }

        @Test
        @DisplayName("CUSTOMER_POSTING (3 legs): CBS(1) FAILS, OBPM(2) SUCCESS, GL(3) SUCCESS → PENDING")
        void customerPosting_firstLegFails_postingPending() {
            doReturn(failStrategy("CBS down")).when(strategyFactory).resolve("CBS_POSTING");

            IncomingPostingRequest request = req("IMX", "CUSTOMER_POSTING");
            AccountPostingCreateResponseV2 response = service.create(request);

            assertThat(response.getPostingStatus()).isEqualTo(PostingStatus.PNDG);

            Long postingId = findPostingIdByE2e(request.getEndToEndRefId());
            List<AccountPostingLegEntity> legs = getLegs(postingId);
            assertThat(legs.get(0).getStatus()).isEqualTo(LegStatus.FAILED);   // CBS
            assertThat(legs.get(1).getStatus()).isEqualTo(LegStatus.SUCCESS);  // OBPM
            assertThat(legs.get(2).getStatus()).isEqualTo(LegStatus.SUCCESS);  // GL
        }

        @Test
        @DisplayName("CUSTOMER_POSTING (3 legs): all 3 legs FAIL → posting PENDING")
        void customerPosting_allThreeLegsFail_postingPending() {
            doReturn(failStrategy("CBS down")).when(strategyFactory).resolve("CBS_POSTING");
            doReturn(failStrategy("OBPM down")).when(strategyFactory).resolve("OBPM_POSTING");
            doReturn(failStrategy("GL down")).when(strategyFactory).resolve("GL_POSTING");

            IncomingPostingRequest request = req("IMX", "CUSTOMER_POSTING");
            AccountPostingCreateResponseV2 response = service.create(request);

            assertThat(response.getPostingStatus()).isEqualTo(PostingStatus.PNDG);

            Long postingId = findPostingIdByE2e(request.getEndToEndRefId());
            List<AccountPostingLegEntity> legs = getLegs(postingId);
            assertThat(legs).hasSize(3);
            assertThat(legs).extracting(AccountPostingLegEntity::getStatus)
                    .containsOnly(LegStatus.FAILED);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // ── 5. BUSINESS RULE VIOLATIONS ──────────────────────────────────
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Business rule violations")
    class BusinessRuleViolations {

        @Test
        @DisplayName("Duplicate endToEndReferenceId → BusinessException, no second posting created")
        void duplicateE2eRef_throwsBusinessException_noSecondPosting() {
            IncomingPostingRequest request = req("IMX", "IMX_CBS_GL");
            service.create(request);                          // first — succeeds
            long countAfterFirst = postingRepo.count();

            assertThatThrownBy(() -> service.create(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already exists");

            // No additional posting should have been saved
            assertThat(postingRepo.count()).isEqualTo(countAfterFirst);
        }

        @Test
        @DisplayName("No posting config for requestType → BusinessException, posting saved as FAILED")
        void noConfigFound_throwsBusinessException_postingPersistedAsFailed() {
            configRepo.deleteAll();   // remove all configs

            assertThatThrownBy(() -> service.create(req("IMX", "IMX_CBS_GL")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("No posting config found");

            assertThat(postingRepo.count()).isEqualTo(1);
            AccountPostingEntity posting = postingRepo.findAll().get(0);
            assertThat(posting.getStatus()).isEqualTo(PostingStatus.RJCT);
            assertThat(posting.getReason()).contains("No posting config found");
        }

        @Test
        @DisplayName("Invalid sourceName → BusinessException(INVALID_ENUM_VALUE), posting saved as FAILED")
        void invalidSourceName_throwsBusinessException_postingFailed() {
            IncomingPostingRequest request = req("IMX", "IMX_CBS_GL");
            request.setSourceName("UNKNOWN_SOURCE");

            assertThatThrownBy(() -> service.create(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("INVALID_ENUM_VALUE");

            assertThat(postingRepo.count()).isEqualTo(1);
            AccountPostingEntity posting = postingRepo.findAll().get(0);
            assertThat(posting.getStatus()).isEqualTo(PostingStatus.RJCT);
            assertThat(posting.getSourceName()).isEqualTo("UNKNOWN_SOURCE");
            assertThat(posting.getReason()).contains("sourceName");
        }

        @Test
        @DisplayName("Invalid requestType → BusinessException(INVALID_ENUM_VALUE), posting saved as FAILED")
        void invalidRequestType_throwsBusinessException_postingFailed() {
            IncomingPostingRequest request = req("IMX", "IMX_CBS_GL");
            request.setRequestType("NOT_A_REAL_TYPE");

            assertThatThrownBy(() -> service.create(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("INVALID_ENUM_VALUE");

            assertThat(postingRepo.count()).isEqualTo(1);
            AccountPostingEntity posting = postingRepo.findAll().get(0);
            assertThat(posting.getStatus()).isEqualTo(PostingStatus.RJCT);
            assertThat(posting.getReason()).contains("requestType");
        }

        @Test
        @DisplayName("Both sourceName and requestType invalid → single BusinessException, posting FAILED")
        void bothEnumsInvalid_throwsBusinessException_postingFailed() {
            IncomingPostingRequest request = req("IMX", "IMX_CBS_GL");
            request.setSourceName("BAD_SOURCE");
            request.setRequestType("BAD_TYPE");

            assertThatThrownBy(() -> service.create(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("INVALID_ENUM_VALUE");

            AccountPostingEntity posting = postingRepo.findAll().get(0);
            assertThat(posting.getStatus()).isEqualTo(PostingStatus.RJCT);
            // Reason contains both errors joined
            assertThat(posting.getReason()).contains("sourceName").contains("requestType");
        }

        @Test
        @DisplayName("Multiple postings: first succeeds, second is duplicate → only 1 posting in DB")
        void firstSucceeds_secondDuplicate_onlyOnePosting() {
            IncomingPostingRequest request = req("RMS", "FED_RETURN");
            AccountPostingCreateResponseV2 first = service.create(request);
            assertThat(first.getPostingStatus()).isEqualTo(PostingStatus.ACSP);

            assertThatThrownBy(() -> service.create(request))
                    .isInstanceOf(BusinessException.class);

            assertThat(postingRepo.count()).isEqualTo(1);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // ── 6. RETRY SCENARIOS ───────────────────────────────────────────
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Retry scenarios")
    class RetryScenarios {

        @Test
        @DisplayName("Retry: single PENDING posting with 1 failed CBS leg → recovers to SUCCESS")
        void retry_singlePendingPosting_cbsLegRecovers_becomesSuccess() {
            // Create with CBS failing
            doReturn(failStrategy("CBS timeout")).when(strategyFactory).resolve("CBS_POSTING");
            IncomingPostingRequest request = req("IMX", "IMX_CBS_GL");
            service.create(request);

            Long postingId = findPostingIdByE2e(request.getEndToEndRefId());
            assertThat(getPosting(postingId).getStatus()).isEqualTo(PostingStatus.PNDG);

            // CBS is now real (stubs always return SUCCESS)
            reset(strategyFactory);

            RetryResponseV2 retryResp = service.retry(new RetryRequestV2());

            // Only the failed CBS leg was retried — GL was already SUCCESS
            assertThat(retryResp.getTotalPostings()).isEqualTo(1);
            assertThat(retryResp.getSuccessCount()).isEqualTo(1);
            assertThat(retryResp.getFailedCount()).isEqualTo(0);

            // Posting is now SUCCESS
            assertThat(getPosting(postingId).getStatus()).isEqualTo(PostingStatus.ACSP);
            assertThat(getPosting(postingId).getReason()).isEqualTo("Request processed successfully");

            // CBS leg: mode=RETRY, attempt incremented to 2
            AccountPostingLegEntity cbsLeg = getLegs(postingId).stream()
                    .filter(l -> "CBS".equals(l.getTargetSystem())).findFirst().orElseThrow();
            assertThat(cbsLeg.getStatus()).isEqualTo(LegStatus.SUCCESS);
            assertThat(cbsLeg.getMode()).isEqualTo(LegMode.RETRY);
            assertThat(cbsLeg.getAttemptNumber()).isEqualTo(2);
        }

        @Test
        @DisplayName("Retry: single PENDING posting with both legs failed → both retry to SUCCESS")
        void retry_bothLegsFailed_retrySucceedsBoth() {
            doReturn(failStrategy("CBS down")).when(strategyFactory).resolve("CBS_POSTING");
            doReturn(failStrategy("GL down")).when(strategyFactory).resolve("GL_POSTING");
            IncomingPostingRequest request = req("IMX", "IMX_CBS_GL");
            service.create(request);

            Long postingId = findPostingIdByE2e(request.getEndToEndRefId());
            assertThat(getLegs(postingId)).extracting(AccountPostingLegEntity::getStatus)
                    .containsOnly(LegStatus.FAILED);

            reset(strategyFactory);

            RetryResponseV2 retryResp = service.retry(new RetryRequestV2());

            assertThat(retryResp.getTotalPostings()).isEqualTo(1);
            assertThat(retryResp.getSuccessCount()).isEqualTo(1);

            assertThat(getPosting(postingId).getStatus()).isEqualTo(PostingStatus.ACSP);
            assertThat(getLegs(postingId)).extracting(AccountPostingLegEntity::getStatus)
                    .containsOnly(LegStatus.SUCCESS);
            assertThat(getLegs(postingId)).extracting(AccountPostingLegEntity::getMode)
                    .containsOnly(LegMode.RETRY);
            assertThat(getLegs(postingId)).extracting(AccountPostingLegEntity::getAttemptNumber)
                    .containsOnly(2);
        }

        @Test
        @DisplayName("Retry bulk: 3 PENDING postings with CBS failures → all 3 recover to SUCCESS")
        void retry_bulkThreePendingPostings_allRecoverToSuccess() {
            doReturn(failStrategy("CBS failure")).when(strategyFactory).resolve("CBS_POSTING");

            IncomingPostingRequest req1 = req("IMX", "IMX_CBS_GL");
            IncomingPostingRequest req2 = req("IMX", "IMX_CBS_GL");
            IncomingPostingRequest req3 = req("RMS", "FED_RETURN");
            service.create(req1);
            service.create(req2);
            service.create(req3);

            assertThat(postingRepo.findAll()).hasSize(3)
                    .extracting(AccountPostingEntity::getStatus)
                    .containsOnly(PostingStatus.PNDG);

            reset(strategyFactory);

            RetryResponseV2 retryResp = service.retry(new RetryRequestV2());

            assertThat(retryResp.getTotalPostings()).isEqualTo(3);
            assertThat(retryResp.getSuccessCount()).isEqualTo(3);
            assertThat(retryResp.getFailedCount()).isEqualTo(0);

            assertThat(postingRepo.findAll())
                    .extracting(AccountPostingEntity::getStatus)
                    .containsOnly(PostingStatus.ACSP);
        }

        @Test
        @DisplayName("Retry: specific posting IDs — only those 2 retried, third stays PENDING")
        void retry_specificPostingIds_onlyThoseProcessed() {
            doReturn(failStrategy("CBS failure")).when(strategyFactory).resolve("CBS_POSTING");
            IncomingPostingRequest req1 = req("IMX", "IMX_CBS_GL");
            IncomingPostingRequest req2 = req("IMX", "IMX_CBS_GL");
            IncomingPostingRequest req3 = req("IMX", "IMX_CBS_GL");
            service.create(req1);
            service.create(req2);
            service.create(req3);

            Long id1 = findPostingIdByE2e(req1.getEndToEndRefId());
            Long id2 = findPostingIdByE2e(req2.getEndToEndRefId());
            Long id3 = findPostingIdByE2e(req3.getEndToEndRefId());

            reset(strategyFactory);

            RetryRequestV2 retryReq = new RetryRequestV2();
            retryReq.setPostingIds(List.of(id1, id2));
            RetryResponseV2 retryResp = service.retry(retryReq);

            assertThat(retryResp.getTotalPostings()).isEqualTo(2);

            assertThat(getPosting(id1).getStatus()).isEqualTo(PostingStatus.ACSP);
            assertThat(getPosting(id2).getStatus()).isEqualTo(PostingStatus.ACSP);
            assertThat(getPosting(id3).getStatus()).isEqualTo(PostingStatus.PNDG); // not retried
        }

        @Test
        @DisplayName("Retry: leg still fails after retry → posting remains PENDING")
        void retry_legStillFailsAfterRetry_postingRemainsAsPending() {
            // CBS fails both on create AND on retry (stub never reset)
            doReturn(failStrategy("CBS permanently down")).when(strategyFactory).resolve("CBS_POSTING");
            IncomingPostingRequest request = req("IMX", "IMX_CBS_GL");
            service.create(request);

            Long postingId = findPostingIdByE2e(request.getEndToEndRefId());

            RetryResponseV2 retryResp = service.retry(new RetryRequestV2());

            assertThat(retryResp.getFailedCount()).isEqualTo(1);
            assertThat(retryResp.getSuccessCount()).isEqualTo(0);
            assertThat(getPosting(postingId).getStatus()).isEqualTo(PostingStatus.PNDG);
        }

        @Test
        @DisplayName("Retry: 3-leg posting where OBPM was the only failure → retries only OBPM")
        void retry_threeLegPosting_onlyFailedLegRetried() {
            doReturn(failStrategy("OBPM crashed")).when(strategyFactory).resolve("OBPM_POSTING");
            IncomingPostingRequest request = req("IMX", "CUSTOMER_POSTING");
            service.create(request);

            Long postingId = findPostingIdByE2e(request.getEndToEndRefId());
            // Before retry: CBS=SUCCESS, OBPM=FAILED, GL=SUCCESS
            assertThat(getLegs(postingId).get(1).getStatus()).isEqualTo(LegStatus.FAILED);

            reset(strategyFactory);
            RetryResponseV2 retryResp = service.retry(new RetryRequestV2());

            assertThat(retryResp.getTotalPostings()).isEqualTo(1);
            assertThat(retryResp.getSuccessCount()).isEqualTo(1);

            assertThat(getPosting(postingId).getStatus()).isEqualTo(PostingStatus.ACSP);
            assertThat(getLegs(postingId)).extracting(AccountPostingLegEntity::getStatus)
                    .containsOnly(LegStatus.SUCCESS);
        }

        @Test
        @DisplayName("Retry: no eligible postings (all SUCCESS) → empty response, nothing retried")
        void retry_noEligiblePostings_returnsEmptyResponse() {
            service.create(req("IMX", "IMX_CBS_GL"));
            service.create(req("IMX", "IMX_OBPM"));
            service.create(req("STABLECOIN", "BUY_CUSTOMER_POSTING"));

            assertThat(postingRepo.findAll())
                    .extracting(AccountPostingEntity::getStatus)
                    .containsOnly(PostingStatus.ACSP);

            RetryResponseV2 retryResp = service.retry(new RetryRequestV2());

            assertThat(retryResp.getTotalPostings()).isEqualTo(0);
        }

        @Test
        @DisplayName("Retry: locked posting is skipped even when PENDING")
        void retry_lockedPosting_isSkipped() {
            doReturn(failStrategy("CBS down")).when(strategyFactory).resolve("CBS_POSTING");
            IncomingPostingRequest request = req("IMX", "IMX_CBS_GL");
            service.create(request);
            Long postingId = findPostingIdByE2e(request.getEndToEndRefId());

            // Lock the posting for 5 minutes
            AccountPostingEntity posting = getPosting(postingId);
            posting.setRetryLockedUntil(Instant.now().plusSeconds(300));
            postingRepo.save(posting);

            reset(strategyFactory);
            RetryResponseV2 retryResp = service.retry(new RetryRequestV2());

            // Posting was locked — skipped
            assertThat(retryResp.getTotalPostings()).isEqualTo(0);
            assertThat(getPosting(postingId).getStatus()).isEqualTo(PostingStatus.PNDG);
        }

        @Test
        @DisplayName("Retry: mixed — 2 PENDING + 1 SUCCESS + 1 locked → only unlocked PENDING retried")
        void retry_mixedPool_onlyUnlockedPendingRetried() {
            doReturn(failStrategy("CBS fail")).when(strategyFactory).resolve("CBS_POSTING");

            IncomingPostingRequest pending1 = req("IMX", "IMX_CBS_GL");
            IncomingPostingRequest pending2 = req("IMX", "IMX_CBS_GL");
            IncomingPostingRequest locked = req("IMX", "IMX_CBS_GL");
            service.create(pending1);
            service.create(pending2);
            service.create(locked);

            Long lockedId = findPostingIdByE2e(locked.getEndToEndRefId());
            AccountPostingEntity lockedPosting = getPosting(lockedId);
            lockedPosting.setRetryLockedUntil(Instant.now().plusSeconds(300));
            postingRepo.save(lockedPosting);

            reset(strategyFactory);
            service.create(req("IMX", "IMX_OBPM")); // SUCCESS, must not be retried

            RetryResponseV2 retryResp = service.retry(new RetryRequestV2());

            // Only pending1 and pending2 are unlocked PENDING → 2 retried
            assertThat(retryResp.getTotalPostings()).isEqualTo(2);
            assertThat(retryResp.getSuccessCount()).isEqualTo(2);

            Long id1 = findPostingIdByE2e(pending1.getEndToEndRefId());
            Long id2 = findPostingIdByE2e(pending2.getEndToEndRefId());
            assertThat(getPosting(id1).getStatus()).isEqualTo(PostingStatus.ACSP);
            assertThat(getPosting(id2).getStatus()).isEqualTo(PostingStatus.ACSP);
            assertThat(getPosting(lockedId).getStatus()).isEqualTo(PostingStatus.PNDG);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // ── 7. SEARCH SCENARIOS ──────────────────────────────────────────
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Search scenarios")
    class SearchScenarios {

        private PostingSearchRequestV2 byCondition(String property, String operator, String value) {
            PostingSearchRequestV2 req = new PostingSearchRequestV2();
            req.setConditions(List.of(new SearchCondition(property, operator, List.of(value))));
            return req;
        }

        private PostingSearchRequestV2 withPagination(int offset, int limit, String sortProp, String sortOrder) {
            PostingSearchRequestV2 req = new PostingSearchRequestV2();
            SearchPagination pg = new SearchPagination();
            pg.setOffset(offset);
            pg.setLimit(limit);
            req.setPagination(pg);
            SearchOrderBy ob = new SearchOrderBy();
            ob.setProperty(sortProp);
            ob.setSortOrder(sortOrder);
            req.setOrderBy(List.of(ob));
            return req;
        }

        @Test
        @DisplayName("Search by status=SUCCESS returns only SUCCESS postings")
        void search_byStatusSuccess_returnsOnlySuccessPostings() {
            service.create(req("IMX", "IMX_CBS_GL"));
            service.create(req("IMX", "IMX_OBPM"));

            doReturn(failStrategy("CBS fail")).when(strategyFactory).resolve("CBS_POSTING");
            service.create(req("IMX", "IMX_CBS_GL"));    // PENDING
            service.create(req("IMX", "ADD_ACCOUNT_HOLD")); // PENDING (CBS only)
            reset(strategyFactory);

            PostingSearchResponseV2 result = service.search(byCondition("status", "EQUALS", "ACSP"));

            assertThat(result.getTotalItems()).isEqualTo(2);
            assertThat(result.getItems())
                    .extracting(AccountPostingFullResponseV2::getPostingStatus)
                    .containsOnly(PostingStatus.ACSP);
        }

        @Test
        @DisplayName("Search by status=PENDING returns only PENDING postings")
        void search_byStatusPending_returnsOnlyPendingPostings() {
            service.create(req("IMX", "IMX_CBS_GL"));   // SUCCESS

            doReturn(failStrategy("CBS fail")).when(strategyFactory).resolve("CBS_POSTING");
            service.create(req("IMX", "IMX_CBS_GL"));   // PENDING
            service.create(req("RMS", "FED_RETURN"));   // PENDING
            reset(strategyFactory);

            PostingSearchResponseV2 result = service.search(byCondition("status", "EQUALS", "PNDG"));

            assertThat(result.getTotalItems()).isEqualTo(2);
            assertThat(result.getItems())
                    .extracting(AccountPostingFullResponseV2::getPostingStatus)
                    .containsOnly(PostingStatus.PNDG);
        }

        @Test
        @DisplayName("Search by sourceName=RMS returns only RMS postings")
        void search_bySourceName_returnsMatchingPostings() {
            service.create(req("IMX", "IMX_CBS_GL"));
            service.create(req("IMX", "IMX_OBPM"));
            service.create(req("RMS", "FED_RETURN"));
            service.create(req("RMS", "MCA_RETURN"));
            service.create(req("STABLECOIN", "BUY_CUSTOMER_POSTING"));

            PostingSearchResponseV2 result = service.search(byCondition("source_name", "EQUALS", "RMS"));

            assertThat(result.getTotalItems()).isEqualTo(2);
            assertThat(result.getItems())
                    .extracting(AccountPostingFullResponseV2::getSourceName)
                    .containsOnly("RMS");
        }

        @Test
        @DisplayName("Search by requestType=IMX_CBS_GL returns only those postings")
        void search_byRequestType_filtersCorrectly() {
            service.create(req("IMX", "IMX_CBS_GL"));
            service.create(req("IMX", "IMX_CBS_GL"));
            service.create(req("IMX", "IMX_CBS_GL"));
            service.create(req("IMX", "IMX_OBPM"));
            service.create(req("RMS", "FED_RETURN"));

            PostingSearchResponseV2 result = service.search(byCondition("request_type", "EQUALS", "IMX_CBS_GL"));

            assertThat(result.getTotalItems()).isEqualTo(3);
            assertThat(result.getItems())
                    .extracting(AccountPostingFullResponseV2::getRequestType)
                    .containsOnly("IMX_CBS_GL");
        }

        @Test
        @DisplayName("Search by targetSystem substring filters by target_systems column")
        void search_byTargetSystem_substring() {
            service.create(req("IMX", "IMX_CBS_GL"));          // CBS_GL
            service.create(req("IMX", "IMX_OBPM"));            // OBPM
            service.create(req("RMS", "MCA_RETURN"));          // CBS
            service.create(req("STABLECOIN", "BUY_CUSTOMER_POSTING")); // OBPM_GL

            PostingSearchResponseV2 result = service.search(byCondition("target_system", "CONTAINS", "CBS"));

            assertThat(result.getTotalItems()).isEqualTo(2);
            assertThat(result.getItems())
                    .extracting(AccountPostingFullResponseV2::getTargetSystems)
                    .allMatch(ts -> ts != null && ts.contains("CBS"));
        }

        @Test
        @DisplayName("Search by combined criteria: IMX + SUCCESS")
        void search_byCombinedCriteria_sourceNameAndStatus() {
            service.create(req("IMX", "IMX_CBS_GL"));   // IMX SUCCESS
            service.create(req("RMS", "FED_RETURN"));   // RMS SUCCESS

            doReturn(failStrategy("CBS fail")).when(strategyFactory).resolve("CBS_POSTING");
            service.create(req("IMX", "IMX_CBS_GL"));   // IMX PENDING
            reset(strategyFactory);

            PostingSearchRequestV2 searchReq = new PostingSearchRequestV2();
            searchReq.setConditions(List.of(
                    new SearchCondition("source_name", "EQUALS", List.of("IMX")),
                    new SearchCondition("status", "EQUALS", List.of("ACSP"))
            ));

            PostingSearchResponseV2 result = service.search(searchReq);

            assertThat(result.getTotalItems()).isEqualTo(1);
            assertThat(result.getItems().get(0).getSourceName()).isEqualTo("IMX");
            assertThat(result.getItems().get(0).getPostingStatus()).isEqualTo(PostingStatus.ACSP);
        }

        @Test
        @DisplayName("Search with no criteria returns all postings, paginated")
        void search_noCriteria_returnsAllPagedByPostingId() {
            service.create(req("IMX", "IMX_CBS_GL"));
            service.create(req("IMX", "IMX_OBPM"));
            service.create(req("RMS", "FED_RETURN"));
            service.create(req("STABLECOIN", "BUY_CUSTOMER_POSTING"));
            service.create(req("IMX", "CUSTOMER_POSTING"));

            PostingSearchResponseV2 resultPage1 = service.search(withPagination(1, 3, "posting_id", "ASC"));
            PostingSearchResponseV2 resultPage2 = service.search(withPagination(4, 3, "posting_id", "ASC"));

            assertThat(resultPage1.getTotalItems()).isEqualTo(5);
            assertThat(resultPage1.getItems()).hasSize(3);
            assertThat(resultPage2.getItems()).hasSize(2);
        }

        @Test
        @DisplayName("Search result includes nested leg responses")
        void search_responseIncludesLegsForEachPosting() {
            service.create(req("IMX", "IMX_CBS_GL"));        // 2 legs
            service.create(req("IMX", "CUSTOMER_POSTING"));  // 3 legs

            PostingSearchResponseV2 result = service.search(byCondition("source_name", "EQUALS", "IMX"));

            assertThat(result.getTotalItems()).isEqualTo(2);
            result.getItems().forEach(posting -> {
                assertThat(posting.getResponses()).isNotEmpty();
                posting.getResponses().forEach(leg ->
                        assertThat(leg.getStatus()).isEqualTo("SUCCESS")
                );
            });
        }

        @Test
        @DisplayName("Search by endToEndReferenceId returns exactly that posting")
        void search_byEndToEndRef_returnsExactPosting() {
            IncomingPostingRequest req1 = req("IMX", "IMX_CBS_GL");
            IncomingPostingRequest req2 = req("IMX", "IMX_OBPM");
            service.create(req1);
            service.create(req2);

            PostingSearchResponseV2 result = service.search(
                    byCondition("end_to_end_reference_id", "EQUALS", req1.getEndToEndRefId()));

            assertThat(result.getTotalItems()).isEqualTo(1);
            assertThat(result.getItems().get(0).getEndToEndReferenceId())
                    .isEqualTo(req1.getEndToEndRefId());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // ── 8. FIND BY ID ────────────────────────────────────────────────
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findById scenarios")
    class FindByIdScenarios {

        @Test
        @DisplayName("findById returns full posting with all fields and legs including timestamps")
        void findById_returnsFullPostingWithLegsAndTimestamps() {
            IncomingPostingRequest request = req("IMX", "IMX_CBS_GL");
            service.create(request);

            Long postingId = findPostingIdByE2e(request.getEndToEndRefId());
            AccountPostingFullResponseV2 resp = service.findById(postingId);

            assertThat(resp.getPostingId()).isEqualTo(postingId);
            assertThat(resp.getSourceReferenceId()).isEqualTo(request.getSourceRefId());
            assertThat(resp.getEndToEndReferenceId()).isEqualTo(request.getEndToEndRefId());
            assertThat(resp.getSourceName()).isEqualTo("IMX");
            assertThat(resp.getRequestType()).isEqualTo("IMX_CBS_GL");
            assertThat(resp.getAmount()).isEqualByComparingTo(request.getAmount().getValue());
            assertThat(resp.getCurrency()).isEqualTo("USD");
            assertThat(resp.getDebtorAccount()).isEqualTo(request.getDebtorAccount());
            assertThat(resp.getCreditorAccount()).isEqualTo(request.getCreditorAccount());
            assertThat(resp.getPostingStatus()).isEqualTo(PostingStatus.ACSP);
            assertThat(resp.getTargetSystems()).isEqualTo("CBS_GL");
            assertThat(resp.getCreatedAt()).isNotNull();
            assertThat(resp.getUpdatedAt()).isNotNull();

            assertThat(resp.getResponses()).hasSize(2);
            assertThat(resp.getResponses())
                    .extracting(LegResponseV2::getTransactionOrder)
                    .containsExactly(1, 2);
            assertThat(resp.getResponses())
                    .extracting(LegResponseV2::getName)
                    .containsExactly("CBS", "GL");
            assertThat(resp.getResponses())
                    .extracting(LegResponseV2::getStatus)
                    .containsOnly("SUCCESS");
            resp.getResponses().forEach(leg -> {
                assertThat(leg.getReferenceId()).isNotBlank();
                assertThat(leg.getPostedTime()).isNotNull();
            });
        }

        @Test
        @DisplayName("findById for a PENDING posting shows FAILED legs correctly")
        void findById_pendingPosting_showsFailedLegs() {
            doReturn(failStrategy("CBS down")).when(strategyFactory).resolve("CBS_POSTING");
            IncomingPostingRequest request = req("IMX", "IMX_CBS_GL");
            service.create(request);

            Long postingId = findPostingIdByE2e(request.getEndToEndRefId());
            AccountPostingFullResponseV2 resp = service.findById(postingId);

            assertThat(resp.getPostingStatus()).isEqualTo(PostingStatus.PNDG);
            assertThat(resp.getResponses()).hasSize(2);
            LegResponseV2 cbsLeg = resp.getResponses().stream()
                    .filter(l -> "CBS".equals(l.getName())).findFirst().orElseThrow();
            assertThat(cbsLeg.getStatus()).isEqualTo("FAILED");
            assertThat(cbsLeg.getReason()).isEqualTo("CBS down");
        }

        @Test
        @DisplayName("findById with non-existent ID throws ResourceNotFoundException")
        void findById_notFound_throwsResourceNotFoundException() {
            assertThatThrownBy(() -> service.findById(999_999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // ── 9. BULK DATA LOAD ────────────────────────────────────────────
    // ══════════════════════════════════════════════════════════════════

    /**
     * Creates a large, representative dataset across all request types and outcome combinations.
     * When tests run, this scenario loads the most data into the DB for visual inspection.
     */
    @Nested
    @DisplayName("Bulk data load — all request types + mixed outcomes")
    class BulkDataLoad {

        @Test
        @DisplayName("All request types with all legs succeeding — 13 postings, various leg counts")
        void allRequestTypes_allSuccess_multiplePostingsEach() {
            // Each valid requestType + sourceName combination, multiple runs each
            service.create(req("IMX", "IMX_CBS_GL"));          // 2 legs
            service.create(req("IMX", "IMX_CBS_GL"));          // 2 legs
            service.create(req("IMX", "IMX_CBS_GL"));          // 2 legs
            service.create(req("IMX", "IMX_OBPM"));            // 1 leg
            service.create(req("IMX", "IMX_OBPM"));            // 1 leg
            service.create(req("RMS", "FED_RETURN"));          // 2 legs
            service.create(req("RMS", "FED_RETURN"));          // 2 legs
            service.create(req("IMX", "GL_RETURN"));           // 1 leg
            service.create(req("RMS", "MCA_RETURN"));          // 1 leg
            service.create(req("STABLECOIN", "BUY_CUSTOMER_POSTING")); // 2 legs
            service.create(req("STABLECOIN", "BUY_CUSTOMER_POSTING")); // 2 legs
            service.create(req("IMX", "ADD_ACCOUNT_HOLD"));    // 1 leg
            service.create(req("IMX", "CUSTOMER_POSTING"));    // 3 legs

            assertThat(postingRepo.count()).isEqualTo(13);
            assertThat(postingRepo.findAll())
                    .extracting(AccountPostingEntity::getStatus)
                    .containsOnly(PostingStatus.ACSP);

            // Leg count: 3×2 + 2×1 + 2×2 + 1×1 + 1×1 + 2×2 + 1×1 + 1×3 = 6+2+4+1+1+4+1+3 = 22
            assertThat(legRepo.count()).isEqualTo(22);
            assertThat(legRepo.findAll())
                    .extracting(AccountPostingLegEntity::getStatus)
                    .containsOnly(LegStatus.SUCCESS);
        }

        @Test
        @DisplayName("Mixed outcomes: SUCCESS + PENDING (various leg failures) + FAILED (violations)")
        void mixedOutcomes_comprehensiveDataLoad() {
            // ── SUCCESS postings ───────────────────────────────────────────────
            service.create(req("IMX", "IMX_CBS_GL"));           // CBS+GL success
            service.create(req("IMX", "IMX_OBPM"));             // OBPM success
            service.create(req("RMS", "FED_RETURN"));           // CBS+GL success
            service.create(req("STABLECOIN", "BUY_CUSTOMER_POSTING"));  // OBPM+GL success
            service.create(req("IMX", "CUSTOMER_POSTING"));     // CBS+OBPM+GL success
            service.create(req("IMX", "GL_RETURN"));            // GL success
            service.create(req("RMS", "MCA_RETURN"));           // CBS success
            service.create(req("IMX", "ADD_ACCOUNT_HOLD"));     // CBS success

            // ── PENDING: CBS fails ─────────────────────────────────────────────
            doReturn(failStrategy("CBS timeout")).when(strategyFactory).resolve("CBS_POSTING");
            service.create(req("IMX", "IMX_CBS_GL"));         // CBS fail, GL success
            service.create(req("RMS", "FED_RETURN"));         // CBS fail, GL success
            service.create(req("IMX", "ADD_ACCOUNT_HOLD"));   // CBS only — fails → PENDING
            service.create(req("IMX", "CUSTOMER_POSTING"));   // CBS fail, OBPM+GL success
            reset(strategyFactory);

            // ── PENDING: GL fails ──────────────────────────────────────────────
            doReturn(failStrategy("GL timeout")).when(strategyFactory).resolve("GL_POSTING");
            service.create(req("IMX", "IMX_CBS_GL"));         // CBS success, GL fail
            service.create(req("RMS", "FED_RETURN"));         // CBS success, GL fail
            service.create(req("STABLECOIN", "BUY_CUSTOMER_POSTING")); // OBPM success, GL fail
            reset(strategyFactory);

            // ── PENDING: OBPM fails ────────────────────────────────────────────
            doReturn(failStrategy("OBPM timeout")).when(strategyFactory).resolve("OBPM_POSTING");
            service.create(req("IMX", "IMX_OBPM"));            // OBPM only → PENDING
            service.create(req("STABLECOIN", "BUY_CUSTOMER_POSTING")); // OBPM fail, GL success
            service.create(req("IMX", "CUSTOMER_POSTING"));    // CBS success, OBPM fail, GL success
            reset(strategyFactory);

            // ── PENDING: all legs fail ─────────────────────────────────────────
            doReturn(failStrategy("CBS down")).when(strategyFactory).resolve("CBS_POSTING");
            doReturn(failStrategy("GL down")).when(strategyFactory).resolve("GL_POSTING");
            service.create(req("IMX", "IMX_CBS_GL"));   // both CBS+GL fail
            service.create(req("RMS", "FED_RETURN"));   // both CBS+GL fail
            reset(strategyFactory);

            // ── FAILED: business violations ───────────────────────────────────
            configRepo.deleteAll();
            assertThatThrownBy(() -> service.create(req("IMX", "IMX_CBS_GL")))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> service.create(req("IMX", "IMX_OBPM")))
                    .isInstanceOf(BusinessException.class);
            seedConfigs();  // restore for remaining

            IncomingPostingRequest badSource = req("IMX", "IMX_CBS_GL");
            badSource.setSourceName("INVALID_SRC");
            assertThatThrownBy(() -> service.create(badSource))
                    .isInstanceOf(BusinessException.class);

            IncomingPostingRequest badType = req("IMX", "IMX_CBS_GL");
            badType.setRequestType("INVALID_TYPE");
            assertThatThrownBy(() -> service.create(badType))
                    .isInstanceOf(BusinessException.class);

            // ── Final DB state assertions ─────────────────────────────────────
            long success = postingRepo.findAll().stream()
                    .filter(p -> p.getStatus() == PostingStatus.ACSP).count();
            long pending = postingRepo.findAll().stream()
                    .filter(p -> p.getStatus() == PostingStatus.PNDG).count();
            long failed = postingRepo.findAll().stream()
                    .filter(p -> p.getStatus() == PostingStatus.RJCT).count();

            assertThat(success).isEqualTo(8);
            assertThat(pending).isEqualTo(12);
            assertThat(failed).isEqualTo(4);
            assertThat(postingRepo.count()).isEqualTo(24);
            assertThat(legRepo.count()).isGreaterThan(30L);
        }

        @Test
        @DisplayName("Retry after bulk failures: all PENDING postings recover to SUCCESS")
        void bulkRetry_afterMixedFailures_allRecover() {
            // Create 5 PENDING postings (CBS fails on create)
            doReturn(failStrategy("CBS fail")).when(strategyFactory).resolve("CBS_POSTING");
            for (int i = 0; i < 5; i++) {
                service.create(req("IMX", "IMX_CBS_GL"));
            }
            reset(strategyFactory);

            // Create 3 SUCCESS postings
            service.create(req("IMX", "IMX_OBPM"));
            service.create(req("RMS", "FED_RETURN"));
            service.create(req("STABLECOIN", "BUY_CUSTOMER_POSTING"));

            assertThat(postingRepo.findAll().stream()
                    .filter(p -> p.getStatus() == PostingStatus.PNDG).count()).isEqualTo(5);

            // Bulk retry — all 5 PENDING recover
            RetryResponseV2 retryResp = service.retry(new RetryRequestV2());

            assertThat(retryResp.getTotalPostings()).isEqualTo(5);
            assertThat(retryResp.getSuccessCount()).isEqualTo(5);
            assertThat(retryResp.getFailedCount()).isEqualTo(0);

            // All 8 postings now SUCCESS
            assertThat(postingRepo.findAll())
                    .extracting(AccountPostingEntity::getStatus)
                    .containsOnly(PostingStatus.ACSP);

            // Verify retry metadata on the retried legs
            legRepo.findAll().stream()
                    .filter(l -> l.getMode() == LegMode.RETRY)
                    .forEach(l -> {
                        assertThat(l.getStatus()).isEqualTo(LegStatus.SUCCESS);
                        assertThat(l.getAttemptNumber()).isEqualTo(2);
                    });
        }
    }
}
