package com.example.stock.facade;

import com.example.stock.service.StockService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedissonLockStockFacade {

    private final StockService stockService;
    private final RedissonClient redissonClient;

    public RedissonLockStockFacade(StockService stockService, RedissonClient redissonClient) {
        this.stockService = stockService;
        this.redissonClient = redissonClient;
    }

    public void decrease(Long id, Long quantity) {
        RLock lock = redissonClient.getLock(id.toString());

        try {
            //몇초동안 락 획득 실행할지, 점유할지
            boolean available = lock.tryLock(10, 1, TimeUnit.SECONDS);
            if(!available) {
                System.out.println("lock 획득 실패");
                return;
            }
            stockService.decrease(id, quantity);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }
}
