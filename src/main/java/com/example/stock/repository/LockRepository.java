package com.example.stock.repository;

import com.example.stock.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

//실무에서는 Lock 용도와 일반 Entity 의 데이터 소스를 분리하는 것을 권장
public interface LockRepository extends JpaRepository<Stock, Long> {

    @Query(nativeQuery = true, value = "select get_lock(:key, 3000)")
    void getLock(String key);

    @Query(nativeQuery = true, value = "select release_lock(:key)")
    void release(String key);
}
