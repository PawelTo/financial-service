package lu.crx.financing.services;

import lu.crx.financing.entities.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestEntityManager;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@AutoConfigureTestEntityManager
@TestPropertySource(locations = "classpath:application-test.properties")
class FinancingServiceIT {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TransactionTemplate template;

    @Autowired
    private FinancingService financingService;

    /**
     * The test case based on example from README.md
     */
    @Test
    void testFinancing() {
        Creditor creditor = Creditor.builder()
                .maxFinancingRateInBps(3)
                .name("Creditor1")
                .build();

        Debtor debtor = Debtor.builder()
                .name("Debtor1")
                .build();

        Purchaser purchaser1 = Purchaser.builder()
                .name("Purchaser1")
                .minimumFinancingTermInDays(20)
                .purchaserFinancingSetting(PurchaserFinancingSettings.builder()
                        .annualRateInBps(50)
                        .creditor(creditor)
                        .build())
                .build();

        Purchaser purchaser2 = Purchaser.builder()
                .name("Purchaser1")
                .minimumFinancingTermInDays(20)
                .purchaserFinancingSetting(PurchaserFinancingSettings.builder()
                        .annualRateInBps(40)
                        .creditor(creditor)
                        .build())
                .build();

        Invoice invoice = Invoice.builder()
                .creditor(creditor)
                .debtor(debtor)
                .valueInCents(10_000_00L)
                .maturityDate(LocalDate.now().plusDays(30))
                .build();

        template.executeWithoutResult(transactionStatus -> {
            entityManager.persist(creditor);
            entityManager.persist(debtor);
            entityManager.persist(invoice);
            entityManager.persist(purchaser1);
            entityManager.persist(purchaser2);
        });

        financingService.finance();

        template.executeWithoutResult(transactionStatus -> {
            Invoice updatedInvoice = entityManager.find(Invoice.class, invoice.getId());

            assertEquals(9_997_00, updatedInvoice.getEarlyPaymentAmountInCents());
            assertEquals(300, updatedInvoice.getDiscountedAmountInCents());
            assertEquals(10_000_00, updatedInvoice.getEarlyPaymentAmountInCents() + updatedInvoice.getDiscountedAmountInCents());
        });
    }

    @Test
    void testFinancingWithoutEligiblePurchaser() {
        // given
        Creditor creditor = Creditor.builder()
                .maxFinancingRateInBps(3)
                .name("Creditor1")
                .build();

        Debtor debtor = Debtor.builder()
                .name("Debtor1")
                .build();

        Purchaser purchaser = Purchaser.builder()
                .name("Purchaser1")
                .minimumFinancingTermInDays(40)
                .purchaserFinancingSetting(PurchaserFinancingSettings.builder()
                        .annualRateInBps(50)
                        .creditor(creditor)
                        .build())
                .build();

        Invoice invoice = Invoice.builder()
                .creditor(creditor)
                .debtor(debtor)
                .valueInCents(10_000_00L)
                .maturityDate(LocalDate.now().plusDays(30))
                .build();

        template.executeWithoutResult(status -> {
            entityManager.persist(creditor);
            entityManager.persist(debtor);
            entityManager.persist(invoice);
            entityManager.persist(purchaser);
        });

        // when
        financingService.finance();

        // then
        template.executeWithoutResult(status -> {
            Invoice updatedInvoice = entityManager.find(Invoice.class, invoice.getId());

            assertEquals(null, updatedInvoice.getEarlyPaymentAmountInCents());
            assertEquals(null, updatedInvoice.getDiscountedAmountInCents());
        });
    }

    @Test
    void testFinancing_whenMultipleChoosesBestPurchaser() {
        // given
        Creditor creditor = Creditor.builder()
                .maxFinancingRateInBps(60)
                .name("Creditor1")
                .build();

        Debtor debtor = Debtor.builder()
                .name("Debtor1")
                .build();

        Purchaser expensivePurchaser = Purchaser.builder()
                .name("ExpensivePurchaser")
                .minimumFinancingTermInDays(10)
                .purchaserFinancingSetting(PurchaserFinancingSettings.builder()
                        .annualRateInBps(100)
                        .creditor(creditor)
                        .build())
                .build();

        Purchaser cheapPurchaser = Purchaser.builder()
                .name("CheapPurchaser")
                .minimumFinancingTermInDays(10)
                .purchaserFinancingSetting(PurchaserFinancingSettings.builder()
                        .annualRateInBps(30)
                        .creditor(creditor)
                        .build())
                .build();

        Invoice invoice = Invoice.builder()
                .creditor(creditor)
                .debtor(debtor)
                .valueInCents(20_000_00L)
                .maturityDate(LocalDate.now().plusDays(20))
                .build();

        template.executeWithoutResult(status -> {
            entityManager.persist(creditor);
            entityManager.persist(debtor);
            entityManager.persist(invoice);
            entityManager.persist(expensivePurchaser);
            entityManager.persist(cheapPurchaser);
        });

        // when
        financingService.finance();

        // then
        template.executeWithoutResult(status -> {
            Invoice updatedInvoice = entityManager.find(Invoice.class, invoice.getId());

            long expectedDiscount = BigDecimal.valueOf(20_000_00L)
                    .multiply(BigDecimal.valueOf(30*20/360))
                    .divide(BigDecimal.valueOf(10_000L), 10, RoundingMode.HALF_UP)
                    .longValue();
            long expectedEarlyPayment = 20_000_00L - expectedDiscount;

            assertEquals(expectedEarlyPayment, updatedInvoice.getEarlyPaymentAmountInCents());
            assertEquals(expectedDiscount, updatedInvoice.getDiscountedAmountInCents());
        });
    }

    @Test
    void testFinancingWithTooShortTerm() {
        // given
        Creditor creditor = Creditor.builder()
                .maxFinancingRateInBps(60)
                .name("Creditor1")
                .build();

        Debtor debtor = Debtor.builder()
                .name("Debtor1")
                .build();

        Purchaser purchaser = Purchaser.builder()
                .name("Purchaser1")
                .minimumFinancingTermInDays(15)
                .purchaserFinancingSetting(PurchaserFinancingSettings.builder()
                        .annualRateInBps(40)
                        .creditor(creditor)
                        .build())
                .build();

        Invoice invoice = Invoice.builder()
                .creditor(creditor)
                .debtor(debtor)
                .valueInCents(15_000_00L)
                .maturityDate(LocalDate.now().plusDays(10))
                .build();

        template.executeWithoutResult(status -> {
            entityManager.persist(creditor);
            entityManager.persist(debtor);
            entityManager.persist(invoice);
            entityManager.persist(purchaser);
        });

        // when
        financingService.finance();

        // then
        template.executeWithoutResult(status -> {
            Invoice updatedInvoice = entityManager.find(Invoice.class, invoice.getId());

            assertEquals(null, updatedInvoice.getEarlyPaymentAmountInCents());
            assertEquals(null, updatedInvoice.getDiscountedAmountInCents());
        });
    }

    @Test
    void testFinancingMultipleInvoices() {
        // given
        Creditor creditor = Creditor.builder()
                .maxFinancingRateInBps(60)
                .name("Creditor1")
                .build();

        Debtor debtor = Debtor.builder()
                .name("Debtor1")
                .build();

        Purchaser purchaser = Purchaser.builder()
                .name("Purchaser1")
                .minimumFinancingTermInDays(10)
                .purchaserFinancingSetting(PurchaserFinancingSettings.builder()
                        .annualRateInBps(40)
                        .creditor(creditor)
                        .build())
                .build();

        Invoice invoice1 = Invoice.builder()
                .creditor(creditor)
                .debtor(debtor)
                .valueInCents(10_000_00L)
                .maturityDate(LocalDate.now().plusDays(20))
                .build();

        Invoice invoice2 = Invoice.builder()
                .creditor(creditor)
                .debtor(debtor)
                .valueInCents(20_000_00L)
                .maturityDate(LocalDate.now().plusDays(30))
                .build();

        Invoice invoice3 = Invoice.builder()
                .creditor(creditor)
                .debtor(debtor)
                .valueInCents(30_000_00L)
                .maturityDate(LocalDate.now().plusDays(5))
                .build();

        template.executeWithoutResult(status -> {
            entityManager.persist(creditor);
            entityManager.persist(debtor);
            entityManager.persist(invoice1);
            entityManager.persist(invoice2);
            entityManager.persist(invoice3);
            entityManager.persist(purchaser);
        });

        // when
        financingService.finance();

        template.executeWithoutResult(status -> {
            Invoice updatedInvoice1 = entityManager.find(Invoice.class, invoice1.getId());
            Invoice updatedInvoice2 = entityManager.find(Invoice.class, invoice2.getId());
            Invoice updatedInvoice3 = entityManager.find(Invoice.class, invoice3.getId());
            FinancingAgreement financingAgreement1 = entityManager.find(FinancingAgreement.class, 1L);
            FinancingAgreement financingAgreement2 = entityManager.find(FinancingAgreement.class, 2L);

            long expectedDiscount1 = BigDecimal.valueOf(10_000_00L)
                    .multiply(BigDecimal.valueOf(40*20/360))
                    .divide(BigDecimal.valueOf(10_000L), 10, RoundingMode.HALF_UP)
                    .longValue();
            long expectedEarlyPayment1 = 10_000_00L - expectedDiscount1;
            assertEquals(expectedEarlyPayment1, updatedInvoice1.getEarlyPaymentAmountInCents());
            assertEquals(expectedDiscount1, updatedInvoice1.getDiscountedAmountInCents());
            assertEquals(updatedInvoice1, financingAgreement1.getInvoice());
            assertEquals(1L, financingAgreement1.getPurchaser().getId());
            assertEquals(true, updatedInvoice1.isFinanced());

            long expectedDiscount2 = BigDecimal.valueOf(20_000_00L)
                    .multiply(BigDecimal.valueOf(40*30/360))
                    .divide(BigDecimal.valueOf(10_000L), 10, RoundingMode.HALF_UP)
                    .longValue();
            long expectedEarlyPayment2 = 20_000_00L - expectedDiscount2;
            assertEquals(expectedEarlyPayment2, updatedInvoice2.getEarlyPaymentAmountInCents());
            assertEquals(expectedDiscount2, updatedInvoice2.getDiscountedAmountInCents());
            assertEquals(true, updatedInvoice2.isFinanced());
            assertEquals(updatedInvoice2, financingAgreement2.getInvoice());
            assertEquals(1L, financingAgreement2.getPurchaser().getId());

            assertEquals(null, updatedInvoice3.getEarlyPaymentAmountInCents());
            assertEquals(null, updatedInvoice3.getDiscountedAmountInCents());
            assertEquals(false, updatedInvoice3.isFinanced());
        });
    }

}
