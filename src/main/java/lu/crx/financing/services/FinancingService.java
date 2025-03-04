package lu.crx.financing.services;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lu.crx.financing.entities.*;
import lu.crx.financing.repository.FinancingAgreementRepository;
import lu.crx.financing.repository.InvoiceRepository;
import lu.crx.financing.repository.PurchaserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
@Service
public class FinancingService {

    private static final int DAYS_IN_YEAR = 360;
    private final InvoiceRepository invoiceRepository;
    private final PurchaserRepository purchaserRepository;
    private final FinancingAgreementRepository financingAgreementRepository;

    @Transactional
    public void finance() {
        log.info("Financing started");

        List<Invoice> unfinancedInvoices = invoiceRepository.findAllByFinanced(false);
        Set<Long> unfinancedCreditorsId = unfinancedInvoices.stream()
                .map(invoice -> invoice.getCreditor().getId())
                .collect(Collectors.toSet());

        /*
        Fetching all data at once to avoid multiple DB operations,
        depends on application profiling if there will be issue with RAM or performance it can be better to process it in some batch
        */
        List<Purchaser> purchasersForInvoicesCreditor = purchaserRepository.findPurchasersByCreditorsId(unfinancedCreditorsId);

        List<FinancingCalculationResult> financingCalculationResults = unfinancedInvoices.stream()
                .map(invoice -> processInvoice(invoice, purchasersForInvoicesCreditor))
                .filter(fcr -> fcr.invoiceId != 0L)
                .toList();

        log.info("Found financing for {} invoices", financingCalculationResults.size());

        financingCalculationResults
                .forEach(fcr -> invoiceRepository.updateInvoice(fcr.invoiceId(), true, fcr.earlyPaymentAmountInCents(), fcr.discountAmount()));


        List<FinancingAgreement> financingAgreements = financingCalculationResults.stream()
                .map(fcr -> FinancingAgreement.builder()
                        .invoice(unfinancedInvoices.stream()
                                .filter(invoice -> invoice.getId() == fcr.invoiceId)
                                .findFirst()
                                .get())
                        .purchaser(purchasersForInvoicesCreditor.stream()
                                .filter(purchaser -> purchaser.getId() == fcr.purchaserId)
                                .findFirst()
                                .get())
                        .build())
                .toList();
        List<FinancingAgreement> savedFinancingAgreements = financingAgreementRepository.saveAll(financingAgreements);
        log.info("Saved {} financingAgreement", savedFinancingAgreements.size());

        log.info("Financing completed");
    }

    private FinancingCalculationResult processInvoice(Invoice invoice, List<Purchaser> purchasers) {
        log.info("Processing Invoice: {}", invoice.getId());
        Creditor creditor = invoice.getCreditor();
        long daysForFinancing = ChronoUnit.DAYS.between(LocalDate.now(), invoice.getMaturityDate());

        List<PurchaserFinancingSettings> pfsForGivenInvoice = purchasers.stream()
                .filter(purchaser -> purchaser.getMinimumFinancingTermInDays() < daysForFinancing)
                .flatMap(purchaser -> purchaser.getPurchaserFinancingSettings().stream())
                .filter(pfs -> pfs.getCreditor().getId() == creditor.getId())
                .filter(pfs -> isFinancingRateAllowed(pfs, daysForFinancing))
                .toList();

        Optional<PurchaserFinancingSettings> bestPurchaserFinancingSettings = pfsForGivenInvoice.stream()
                .min(Comparator.comparing(PurchaserFinancingSettings::getAnnualRateInBps));

        if (bestPurchaserFinancingSettings.isPresent()) {
            PurchaserFinancingSettings bpfs = bestPurchaserFinancingSettings.get();
            long pfsId = bpfs.getId();
            long purchaserId = purchasers.stream()
                    .filter(p -> containPurchaserFinancingSettings(p, pfsId))
                    .findFirst()
                    .get()
                    .getId();
            long discountAmount = BigDecimal.valueOf(invoice.getValueInCents())
                    .multiply(BigDecimal.valueOf(calculateFinancingRate(bpfs, daysForFinancing)))
                    .divide(BigDecimal.valueOf(10_000L), 10, RoundingMode.HALF_UP)
                    .longValue();
            long earlyPaymentAmountInCents = invoice.getValueInCents() - discountAmount;
            return new FinancingCalculationResult(invoice.getId(), discountAmount, earlyPaymentAmountInCents, purchaserId);
        } else {
            log.warn("Couldn't find purchaser financing settings for invoice_id: {} ", invoice.getId());
            return new FinancingCalculationResult(0L, 0L, 0L, 0L);
        }
    }

    private boolean containPurchaserFinancingSettings(Purchaser purchaser, long pfsId) {
        return purchaser.getPurchaserFinancingSettings().stream()
                .anyMatch(pfs -> pfs.getId() == pfsId);
    }

    private boolean isFinancingRateAllowed(PurchaserFinancingSettings pfs, long daysForFinancing) {
        return calculateFinancingRate(pfs, daysForFinancing) <= pfs.getCreditor().getMaxFinancingRateInBps();
    }

    private long calculateFinancingRate(PurchaserFinancingSettings pfs, long daysForFinancing) {
        BigDecimal annualRate = BigDecimal.valueOf(pfs.getAnnualRateInBps());
        BigDecimal financingDays = BigDecimal.valueOf(daysForFinancing);
        return annualRate
                .multiply(financingDays)
                .divide(BigDecimal.valueOf(DAYS_IN_YEAR), 10, RoundingMode.HALF_UP)
                .longValue();
    }

    private record FinancingCalculationResult(long invoiceId, long discountAmount, long earlyPaymentAmountInCents,
                                              long purchaserId) {
    }
}
