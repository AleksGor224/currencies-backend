package com.exchange.repository;

import com.exchange.repository.entities.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

@Repository
public interface CurrencyRepository extends JpaRepository<Currency, String> {

    @Modifying
    int deleteByUpdatedAtBefore(long val);
}
