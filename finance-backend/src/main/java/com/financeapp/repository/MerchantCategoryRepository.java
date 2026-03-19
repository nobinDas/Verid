package com.financeapp.repository;

import com.financeapp.model.MerchantCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MerchantCategoryRepository extends JpaRepository<MerchantCategory, Long> {

    Optional<MerchantCategory> findByMerchantKey(String merchantKey);

    List<MerchantCategory> findAllByMerchantKeyIn(List<String> merchantKeys);
}
