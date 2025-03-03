package lu.crx.financing.repository;

import lu.crx.financing.entities.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findAllByFinanced(boolean financed);

    /**
     * For processing multiple invoices it will be better to use
     * namedParameterJdbcOperations.batchUpdate(sql, batch);
     */
    @Transactional
    @Modifying
    @Query("UPDATE Invoice " +
            "SET financed = :financed," +
            "earlyPaymentAmountInCents = :earlyPaymentAmountInCents," +
            "discountedAmountInCents = :discountedAmountInCents" +
            " WHERE id = :id")
    void updateInvoice(@Param("id") long id, @Param("financed") boolean financed, @Param("earlyPaymentAmountInCents") long earlyPaymentAmountInCents, @Param("discountedAmountInCents") long discountedAmountInCents);
}
