package pm.resilience4j;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.vavr.CheckedConsumer;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;

public class CircuitBreakerForConsumerTest {

    private static final Integer SMALL_FAILURE_RATE_THRESHOLD = 5;
    private static final Integer SMALL_WAIT_DURATION_IN_OPEN_STATE_MILLIS = 1000;
    private static final Integer SMALL_HALF_OPEN_RING_BUFFER_SIZE = 1;
    private static final Integer SMALL_CLOSED_RING_BUFFER_SIZE = 10;
    private static final Float SMALL_FAILURE_RATE_THRESHOLD_PERCENT = new Float(SMALL_FAILURE_RATE_THRESHOLD) * 100 / SMALL_CLOSED_RING_BUFFER_SIZE;

    private ExampleConsumer exampleConsumer;
    private CheckedConsumer<String> consumerThrowingExceptionWithCircuitBreaker;
    private CheckedConsumer<String> consumerNotThrowingExceptionWithCircuitBreaker;

    @Before
    public void setUp() {
        exampleConsumer = new ExampleConsumer();

        CircuitBreaker circuitBreaker = getCustomCircuitBreakerWithSmallValues();
        consumerThrowingExceptionWithCircuitBreaker = CircuitBreaker.decorateCheckedConsumer(circuitBreaker, exampleConsumer::consumeAStringThrowingException);
        consumerNotThrowingExceptionWithCircuitBreaker = CircuitBreaker.decorateCheckedConsumer(circuitBreaker, exampleConsumer::consumeAString);
    }

    @Test
    public void basicSimpleExampleOfUsingDefaultCircuitBreaker() throws Throwable {
        /*
        Defaults are:
        public static final int DEFAULT_MAX_FAILURE_THRESHOLD = 50; // Percentage
        public static final int DEFAULT_WAIT_DURATION_IN_OPEN_STATE = 60; // Seconds
        public static final int DEFAULT_RING_BUFFER_SIZE_IN_HALF_OPEN_STATE = 10;
        public static final int DEFAULT_RING_BUFFER_SIZE_IN_CLOSED_STATE = 100;
        */
        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("my-default-circuit-breaker");
        CheckedConsumer<String> decoratedCheckedConsumer = CircuitBreaker.decorateCheckedConsumer(circuitBreaker, exampleConsumer::consumeAString);
        decoratedCheckedConsumer.accept("A basic simple example of using the default circuit breaker");
    }

    @Test
    public void basicSimpleExampleOfUsingCustomCircuitBreaker() throws Throwable {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                                                                        .failureRateThreshold(50)
                                                                        .waitDurationInOpenState(Duration.ofMillis(1000))
                                                                        .ringBufferSizeInHalfOpenState(1)
                                                                        .ringBufferSizeInClosedState(10)
                                                                        .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of("custom-circuit-breaker", circuitBreakerConfig);
        CheckedConsumer<String> decoratedCheckedConsumer = CircuitBreaker.decorateCheckedConsumer(circuitBreaker, exampleConsumer::consumeAString);
        decoratedCheckedConsumer.accept("A basic simple example of using a custom circuit breaker");
    }

    @Test
    public void circuitIsNotBrokenWhenEnoughExceptionsThrownAndRingBufferNotFull() throws Throwable {

        int failureCount = SMALL_FAILURE_RATE_THRESHOLD;
        int successCount = SMALL_CLOSED_RING_BUFFER_SIZE - failureCount; // CircuitBreakerOpenException is only thrown on the call AFTER the ring buffer is full.
        consumeThrowingException(failureCount);
        consumeSuccessfully(successCount);
    }

    @Test(expected = CallNotPermittedException.class)
    public void circuitIsBrokenWhenEnoughExceptionsThrownAndRingBufferFull() throws Throwable {

        tripCircuitBreaker();
    }

    private void tripCircuitBreaker() throws Throwable {
        int failureCount = SMALL_FAILURE_RATE_THRESHOLD;
        int successCount = SMALL_CLOSED_RING_BUFFER_SIZE - failureCount + 1; // CircuitBreakerOpenException is only thrown on the call AFTER the ring buffer is full.
        consumeThrowingException(failureCount);
        consumeSuccessfully(successCount);
    }

    @Test
    public void circuitIsNotBrokenWhenNoExceptionsThrownAndRingBufferFull() throws Throwable {

        consumeSuccessfully(SMALL_CLOSED_RING_BUFFER_SIZE + 1);
    }

    @Test
    public void circuitIsNotBrokenWhenNotEnoughExceptionsThrownAndRingBufferFull() throws Throwable {

        int failureCount = SMALL_FAILURE_RATE_THRESHOLD - 1;
        int successCount = SMALL_CLOSED_RING_BUFFER_SIZE - failureCount + 1;
        consumeThrowingException(failureCount);
        consumeSuccessfully(successCount);
    }

    @Test(expected = CallNotPermittedException.class)
    public void inHalfOpenStateExceptionReTripsCircuitBreaker() throws Throwable {

        try {
            tripCircuitBreaker();
        }
        catch (CallNotPermittedException e) {
            System.out.println("Circuit breaker tripped");
        }
        Thread.sleep(SMALL_WAIT_DURATION_IN_OPEN_STATE_MILLIS + 100);

        consumeThrowingException(1);
        consumeSuccessfully(1); // Circuit breaker is re-tripped on the call AFTER the half-open ring buffer is full.
    }

    @Test
    public void inHalfOpenStateExceptionResetsCircuitBreaker() throws Throwable {

        try {
            tripCircuitBreaker();
        }
        catch (CallNotPermittedException e) {
            System.out.println("Circuit breaker tripped");
        }
        Thread.sleep(SMALL_WAIT_DURATION_IN_OPEN_STATE_MILLIS + 100);

        consumeSuccessfully(2);
        consumeThrowingException(1);
        consumeSuccessfully(1);
    }

    private void consumeSuccessfully(int numberOfTimes) throws Throwable {
        for (int i = 1; i <= numberOfTimes; i++) {
            consumerNotThrowingExceptionWithCircuitBreaker.accept(String.format("Not throwing exception %02d", i));
        }
    }

    private void consumeThrowingException(int numberOfTimes) throws Throwable {
        for (int i = 1; i <= numberOfTimes; i++) {
            try {
                consumerThrowingExceptionWithCircuitBreaker.accept(String.format("Throwing Exception %02d", i));
            }
            catch (ExampleException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    private CircuitBreaker getCustomCircuitBreakerWithSmallValues() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                                                                        .failureRateThreshold(SMALL_FAILURE_RATE_THRESHOLD_PERCENT)
                                                                        .waitDurationInOpenState(Duration.ofMillis(SMALL_WAIT_DURATION_IN_OPEN_STATE_MILLIS))
                                                                        .ringBufferSizeInHalfOpenState(SMALL_HALF_OPEN_RING_BUFFER_SIZE)
                                                                        .ringBufferSizeInClosedState(SMALL_CLOSED_RING_BUFFER_SIZE)
                                                                        .build();
        return CircuitBreaker.of("custom-circuit-breaker", circuitBreakerConfig);
    }
}
