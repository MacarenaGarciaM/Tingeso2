package com.example.kardexservice.repositories;

import com.example.kardexservice.entities.KardexEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface KardexRepository extends JpaRepository<KardexEntity, Long> {

    @Query("""
      select k
      from KardexEntity k
      where (:toolId is null or k.toolId = :toolId)
        and (:rutUser is null or k.rutUser = :rutUser)
        and (:typeLower = '' or lower(k.type) = :typeLower)
        and (:hasFrom = false or k.movementDate >= :fromDate)
        and (:hasTo   = false or k.movementDate <= :toDate)
        and (:namePat = '' or lower(k.toolNameSnapshot) like :namePat)
        and (:categoryPat = '' or lower(k.toolCategorySnapshot) like :categoryPat)
    """)
    Page<KardexEntity> search(
            @Param("toolId") Long toolId,
            @Param("rutUser") String rutUser,
            @Param("typeLower") String typeLower,
            @Param("hasFrom") boolean hasFrom,
            @Param("fromDate") LocalDate fromDate,
            @Param("hasTo") boolean hasTo,
            @Param("toDate") LocalDate toDate,
            @Param("namePat") String namePat,
            @Param("categoryPat") String categoryPat,
            Pageable pageable
    );
}
