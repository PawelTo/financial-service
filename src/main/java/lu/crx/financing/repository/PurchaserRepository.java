package lu.crx.financing.repository;

import lu.crx.financing.entities.Purchaser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface PurchaserRepository extends JpaRepository<Purchaser, Long> {

    @Query("SELECT p, pfs.creditor.id FROM Purchaser p join fetch p.purchaserFinancingSettings pfs where pfs.creditor.id IN (:creditorsId)")
    List<Purchaser> findPurchasersByCreditorsId(Set<Long> creditorsId);
}
