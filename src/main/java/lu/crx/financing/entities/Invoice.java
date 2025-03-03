package lu.crx.financing.entities;

import java.io.Serializable;
import java.time.LocalDate;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * An invoice issued by the {@link Creditor} to the {@link Debtor} for shipped goods.
 */
@Entity
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Invoice implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    /**
     * Creditor is the entity that issued the invoice.
     * Setting fetch = FetchType.LAZY to avoid potentially N+1 hibernate query problem
     */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "creditor_id")
    private Creditor creditor;

    /**
     * Debtor is the entity obliged to pay according to the invoice.
     * Setting fetch = FetchType.LAZY to avoid potentially N+1 hibernate query problem
     */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "debtor_id")
    private Debtor debtor;

    /**
     * Maturity date is the date on which the {@link #debtor} is to pay for the invoice.
     * In case the invoice was financed, the money will be paid in full on this date to the purchaser of the invoice.
     */
    @Basic(optional = false)
    private LocalDate maturityDate;

    /**
     * The value is the amount to be paid for the shipment by the Debtor.
     */
    @Basic(optional = false)
    private long valueInCents;

    /**
     * The early payment amount of the invoice. Should be not null if invoice has been financed.
     */
    @Basic
    private Long earlyPaymentAmountInCents;

    /**
     * The early discounted amount of the invoice. Should be not null if invoice has been financed.
     *  The sum earlyPaymentAmountInCents + discountedAmountInCents should be equal to valueInCents.
     */
    @Basic
    private Long discountedAmountInCents;

    /**
     * Field added for potential performance improvement.
     * Allows creating an index in the database to enable more efficient retrieval of only unprocessed invoices
     * (e.g., where financed = false).
     */
    @Column
    private boolean financed;
}
