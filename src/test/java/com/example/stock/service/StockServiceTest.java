package com.example.stock.service;

import com.example.stock.domain.Stock;
import com.example.stock.repository.StockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class StockServiceTest {
    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockService stockService;

    @Autowired
    private PessimisticLockStockService pessimisticLockStockService;

    @BeforeEach
    public void before() {
        stockRepository.save(
                new Stock(1L, 100L)
        );
    }

    @AfterEach
    public void after() {
        stockRepository.deleteAll();
    }

    @Test
    void decrease() {
        stockService.decrease(1L, 1L);
        Stock stock = stockRepository.findById(1L).orElseThrow();
        assertEquals(99, stock.getQuantity());

    }

    @Test
    void 동시에_100개의_요청() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch countDownLatch = new CountDownLatch(threadCount);
        for(int i=0; i<threadCount; i++) {
            executorService.submit(() -> {
                try {
                    pessimisticLockStockService.decrease(1L, 1L);
                } finally {
                    countDownLatch.countDown();
                }
            });
        }

        //다른 thread 에서 수행중인 작업을 대기하도록 함
        countDownLatch.await();

        Stock stock = stockRepository.findById(1L).orElseThrow();
        assertEquals(0, stock.getQuantity());

    }
}