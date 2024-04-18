<aside>
💡 재고 시스템을 구현할 때 발생할 수 있는 동시성 이슈를 해결하는 방법을 학습
- 실행 환경 : SpringBoot + Java
- 해결 방식 : synchronized, DB Lock, Redis

</aside>

# 시나리오

```java
@Transactional
    public void decrease(Long id, Long quantity) {
        //Stock 조회
        //재고 감소
        //갱신된 값을 저장
        Stock stock = stockRepository.findById(id).orElseThrow();
        stock.decrease(quantity);
        stockRepository.saveAndFlush(stock);
    }
```

```java
// Stock entity의 재고 감소 로직
public void decrease(Long quantity) {
        if(this.quantity - quantity < 0 ) throw new RuntimeException("재고는 0개 미만이 될 수 없습니다.");
        this.quantity -= quantity;
    }
```

# 문제 정의

- 여러대의 프로세스 또는 스레드에서 실행시 동시성 이슈가 발생할 수 있다.
    - DB에서 stock 을 조회하고 값을 업데이트하는 시간의 텀에 다른 스레드에서 같은 작업을 하기 때문
    - 데이터 정합성을 유지하기 위한 조치가 필요

![Untitled](https://prod-files-secure.s3.us-west-2.amazonaws.com/7b00d932-dab0-415e-b74c-10e5d3b7557b/0cb04262-b605-4921-bd0c-b2a1e2d64065/Untitled.png)

---

# [해결1] Application 에서 해결 : synchronized

> 싱글 스레드로 비즈니스 로직 처리
> 

### 한계1 : transactional 과 함께 사용할 수 없음

- SpringBoot의 AOP 기능인 transactional 은 프록시 기반으로 동작
    - 기존 코드 앞 뒤로 횡단 관심 영역의 코드가 추가
- start transaction, end transaction 사이에 호출되는 decrease만 싱글스레드로 동작하게됨
- decrease 함수가 종료되고 end transaction 전에 새로운 스레드의 접근이 가능해져서 동시성 이슈 발생

```java
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
```

### 한계2 :  멀티 프로세스 환경에서 사용 불가능

- synchronized 는 스레드의 접근은 제어하지만 프로세스의 접근을 제어하지 못함

# [해결2] Lock 메커니즘 이용

> **자원이 사용되는 동안 데이터 접근을 막아서 동시성을 제어하는 방법**
> 

## 1. 비관적 락(Pessimistic Lock)

데이터를 갱신할 때 충돌이 발생할 것이라고 보고 미리 잠금하는 방식

데이터를 조회하는 시점부터 락 시작

### 사용

- LockModeType.PESSIMISTIC_WRITE(default)
    - exclusive lock 방식
    - 다른 스레드에서 자원에 대한 조회, 수정, 삭제 불가능
- LockModeType.PESSIMISTIC_READ
    - shared lock 방식
    - 다른 스레드에서 읽기는 가능하지만 수정은 불가능
- LockModeType.PESSINISTIC_FORCE_INCREMENT
    - Version 정보를 사용하는 비관적 락

### 장단점

- 충돌을 전제로해서 자원을 잠그기때문에 무결성을 유지하기 좋음
- 충돌이 빈번한 경우 비관적 락 방식을 사용하는 것이 적절함
- 그러나, 대량 데이터 처리시 성능 저하 또는 교착상태를 유발하게 될 수 있다

## 2. 낙관적 락(Optimistic Lock)

충돌이 거의 발생하지 않을 것이라고 가정하고, 충돌이 발생한 경우를 대비하는 방식 (락X)

데이터를 조회할 때는 락을 걸지 않고 업데이트할 때 version 이라는 컬럼을 이용해 충돌 확인

### 장단점

- 리소스 경쟁이 적고, 락으로 인한 성능 저하가 덜함
- 그러나, 충돌 발생에 대한 추가 로직이 필요함

![Untitled](https://prod-files-secure.s3.us-west-2.amazonaws.com/7b00d932-dab0-415e-b74c-10e5d3b7557b/18ed7f3c-22e5-4127-84fd-0b5df49ee58b/Untitled.png)

## 3. 네임드 락(Named Lock)

자원을 잠그는 것이 아니라 사용자가 지정한 이름으로 잠금을 생성하는 방식

아래 그림처럼 Stock이 아닌 사용자가 임의로 지정한 메타 데이터(1)로 잠금을 생성하고 제어한다

![Untitled](https://prod-files-secure.s3.us-west-2.amazonaws.com/7b00d932-dab0-415e-b74c-10e5d3b7557b/a58d1cd3-9ac2-41c5-ad9e-4ed585c309ed/Untitled.png)

### 사용

- 잠금의 획득, 잠금의 반납을 로직에 추가해서 사용
- 잠금을 획득한 이후 트랜젝션이 커밋/롤백되더라도 자동으로 잠금 해제되지 않기때문에 반납하는 로직이 필수

```java
@Transactional
void decrease(Long id, Long quantity) {
        try {
            lockRepository.getLock(id.toString());
            stockService.decrease(id, quantity);
        } finally {
            lockRepository.release(id.toString());
        }
    }
```

### 주의

**lock 획득, 반납 로직과 서비스 로직의 transaction 격리 필요**

- Lock을 해제하는 시점과, 비즈니스 로직의 커밋 시점의 차이로 동시성 이슈가 발생할 수 있음(synchronized와 transactional을 함께 사용할때의 원인과 동일)

**데이터 소스 분리**

- 트랜잭션을 분리해야 하기 때문에 커넥션을 2개씩 사용하게 됨
- 락 획득을 위한 작업이 비즈니스 로직 영향을 주게되는 상황을 방자히기위해 데이터소스를 분리해서 사용하는 것 권장

### 장단점

- 분산락 구현에 적합함
- 트랜잭션 종료 시 lock 해제, 세션 관리 등에 대한 오버헤드 발생

# [해결3] Redis를 이용한 분산락

|  | lettuce | redisson |
| --- | --- | --- |
| 구현 방식 | spin lock
- 점유 가능 여부를 주기적으로 확인
- 락 획득 성공할때까지 시도하기때문에 부하를 일으킬 수 있음 | pub sub
- lock이 해제되면 구독중인 스레드에게 알려줌
- spin lock 에 비해 redis 부하가 적은 편 |
| 분산락 | 구현 필요 | 라이브러리에서 분산락 형태로 구현하여 제공 |

---

# 출처

https://systemdata.tistory.com/51

[https://ksh-coding.tistory.com/125#3-2. MySQL(DB 단)의 Lock 사용-1](https://ksh-coding.tistory.com/125#3-2.%20MySQL(DB%20%EB%8B%A8)%EC%9D%98%20Lock%20%EC%82%AC%EC%9A%A9-1)

https://sungsan.oopy.io/5f46d024-dfea-4d10-992b-40cef9275999
