package pm.resilience4j;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerOpenException;
import io.vavr.CheckedConsumer;
import org.junit.Test;

import java.time.Duration;

public class CircuitBreakerTest {

    private static final Integer SMALL_FAILURE_RATE_THRESHOLD = 5;
    private static final Integer SMALL_WAIT_DURATION_IN_OPEN_STATE_MILLIS = 1000;
    private static final Integer SMALL_HALF_OPEN_RING_BUFFER_SIZE = 1;
    private static final Integer SMALL_CLOSED_RING_BUFFER_SIZE = 10;
    private static final Float SMALL_FAILURE_RATE_THRESHOLD_PERCENT = new Float(SMALL_FAILURE_RATE_THRESHOLD) * 100 / SMALL_CLOSED_RING_BUFFER_SIZE;

    private final ExampleClass exampleClass = new ExampleClass();

    @Test(expected = ExampleClass.ExampleException.class)
    public void defaultCircuitIsNotBrokenWhenOneExceptionThrown() throws Throwable {
        CircuitBreaker circuitBreaker = getDefaultCircuitBreaker();

        CheckedConsumer<String> checkedConsumerThrowingException = CircuitBreaker.decorateCheckedConsumer(circuitBreaker, exampleClass::consumeAStringThrowingException);
        CheckedConsumer<String> checkedConsumerNotThrowingException = CircuitBreaker.decorateCheckedConsumer(circuitBreaker, exampleClass::consumeAString);

        checkedConsumerNotThrowingException.accept("No Exception thrown");
        checkedConsumerThrowingException.accept("Exception thrown");
    }

    @Test(expected = ExampleClass.ExampleException.class)
    public void customCircuitIsNotBrokenWhenOneExceptionThrown() throws Throwable {
        CircuitBreaker circuitBreaker = getCustomCircuitBreakerWithSmallValues();

        CheckedConsumer<String> checkedConsumerThrowingException = CircuitBreaker.decorateCheckedConsumer(circuitBreaker, exampleClass::consumeAStringThrowingException);
        CheckedConsumer<String> checkedConsumerNotThrowingException = CircuitBreaker.decorateCheckedConsumer(circuitBreaker, exampleClass::consumeAString);

        checkedConsumerNotThrowingException.accept("No Exception thrown");
        checkedConsumerThrowingException.accept("Exception thrown");
    }

    @Test
    public void circuitIsNotBrokenWhenEnoughExceptionsThrownAndRingBufferNotFull() throws Throwable {
        CircuitBreaker circuitBreaker = getCustomCircuitBreakerWithSmallValues();

        CheckedConsumer<String> checkedConsumerThrowingException = CircuitBreaker.decorateCheckedConsumer(circuitBreaker, exampleClass::consumeAStringThrowingException);
        CheckedConsumer<String> checkedConsumerNotThrowingException = CircuitBreaker.decorateCheckedConsumer(circuitBreaker, exampleClass::consumeAString);

        int failureCount = SMALL_FAILURE_RATE_THRESHOLD + 1;
        for (int i = 1; i <= failureCount; i++) {
            callAndHandleExampleException(checkedConsumerThrowingException, String.format("Throwing Exception %02d", i));
        }

        for (int i = 1; i < SMALL_CLOSED_RING_BUFFER_SIZE - failureCount; i++) {
            checkedConsumerNotThrowingException.accept(String.format("Not throwing exception %02d", i));
        }
    }

    @Test(expected = CircuitBreakerOpenException.class)
    public void circuitIsBrokenWhenEnoughExceptionsThrownAndRingBufferFull() throws Throwable {
        CircuitBreaker circuitBreaker = getCustomCircuitBreakerWithSmallValues();

        CheckedConsumer<String> checkedConsumerThrowingException = CircuitBreaker.decorateCheckedConsumer(circuitBreaker, exampleClass::consumeAStringThrowingException);
        CheckedConsumer<String> checkedConsumerNotThrowingException = CircuitBreaker.decorateCheckedConsumer(circuitBreaker, exampleClass::consumeAString);

        int failureCount = SMALL_FAILURE_RATE_THRESHOLD + 1;
        for (int i = 1; i <= failureCount; i++) {
            callAndHandleExampleException(checkedConsumerThrowingException, String.format("Throwing Exception %02d", i));
        }

        for (int i = 1; i <= (SMALL_CLOSED_RING_BUFFER_SIZE - failureCount) + 1; i++) {
            checkedConsumerNotThrowingException.accept(String.format("Not throwing exception %02d", i));
        }
    }

    @Test
    public void circuitIsNotBrokenWhenNoExceptionsThrownAndRingBufferFull() throws Throwable {
        CircuitBreaker circuitBreaker = getCustomCircuitBreakerWithSmallValues();

        CheckedConsumer<String> checkedConsumerNotThrowingException = CircuitBreaker.decorateCheckedConsumer(circuitBreaker, exampleClass::consumeAString);

        for (int i = 1; i <= SMALL_CLOSED_RING_BUFFER_SIZE + 1; i++) {
            checkedConsumerNotThrowingException.accept(String.format("Not throwing exception %02d", i));
        }
    }

    @Test
    public void circuitIsNotBrokenWhenNotEnoughExceptionsThrownAndRingBufferFull() throws Throwable {
        CircuitBreaker circuitBreaker = getCustomCircuitBreakerWithSmallValues();

        CheckedConsumer<String> checkedConsumerThrowingException = CircuitBreaker.decorateCheckedConsumer(circuitBreaker, exampleClass::consumeAStringThrowingException);
        CheckedConsumer<String> checkedConsumerNotThrowingException = CircuitBreaker.decorateCheckedConsumer(circuitBreaker, exampleClass::consumeAString);

        int failureCount = SMALL_FAILURE_RATE_THRESHOLD;
        for (int i = 1; i <= failureCount; i++) {
            callAndHandleExampleException(checkedConsumerThrowingException, String.format("Not throwing exception %02d", i));
        }

        for (int i = 1; i < SMALL_CLOSED_RING_BUFFER_SIZE - failureCount; i++) {
            checkedConsumerNotThrowingException.accept(String.format("Not throwing exception %02d", i));
        }
    }

    private void callAndHandleExampleException(CheckedConsumer<String> checkedConsumer, String stringToConsume) throws Throwable {
        try {
            checkedConsumer.accept(stringToConsume);
        }
        catch (ExampleClass.ExampleException e) {
            System.out.println(e.getMessage());
        }
    }

    private CircuitBreaker getDefaultCircuitBreaker() {
        /*
        Defaults are:
        public static final int DEFAULT_MAX_FAILURE_THRESHOLD = 50; // Percentage
        public static final int DEFAULT_WAIT_DURATION_IN_OPEN_STATE = 60; // Seconds
        public static final int DEFAULT_RING_BUFFER_SIZE_IN_HALF_OPEN_STATE = 10;
        public static final int DEFAULT_RING_BUFFER_SIZE_IN_CLOSED_STATE = 100;
        */
        return CircuitBreaker.ofDefaults("my-default-circuit-breaker");
    }

    private CircuitBreaker getCustomCircuitBreakerWithSmallValues() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                                                                        .failureRateThreshold(SMALL_FAILURE_RATE_THRESHOLD_PERCENT)
                                                                        .waitDurationInOpenState(Duration.ofMillis(SMALL_WAIT_DURATION_IN_OPEN_STATE_MILLIS))
                                                                        .ringBufferSizeInHalfOpenState(SMALL_HALF_OPEN_RING_BUFFER_SIZE)
                                                                        .ringBufferSizeInClosedState(SMALL_CLOSED_RING_BUFFER_SIZE)
                                                                        .build();
        return CircuitBreaker.of("test-circuit-breaker", circuitBreakerConfig);
    }
}
