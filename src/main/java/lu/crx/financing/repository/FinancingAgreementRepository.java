package lu.crx.financing.repository;

import lu.crx.financing.entities.FinancingAgreement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FinancingAgreementRepository extends JpaRepository<FinancingAgreement, Long> {
}
