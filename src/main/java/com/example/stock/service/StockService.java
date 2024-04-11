package com.example.stock.service;

import com.example.stock.domain.Stock;
import com.example.stock.repository.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {
    private final StockRepository stockRepository;

    public StockService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    /* NOTE
    *   synchronized 를 사용하더라도 @Transactional 어노테이션으로 인해 실패
    *   ------------------
    *   -- proxy method --
    *   start traction
    *   call decrease
    *   end transaction
    *   --    end       --
    *   ------------------
    *   decrease 작업이 끝나고 end transaction 전에(commit X) 새로운 thread 가 작업하는 상황 발생
    *   */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void decrease(Long id, Long quantity) {
        //1단계
        //Stock 조회
        //재고 감소
        //갱신된 값을 저장
        Stock stock = stockRepository.findById(id).orElseThrow();
        stock.decrease(quantity);
        stockRepository.saveAndFlush(stock);
    }
}
