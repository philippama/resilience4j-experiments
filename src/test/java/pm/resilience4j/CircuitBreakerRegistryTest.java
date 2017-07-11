package pm.resilience4j;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerOpenException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.vavr.CheckedConsumer;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.fail;

public class CircuitBreakerRegistryTest {

    private static final int RING_BUFFER_SIZE_IN_CLOSED_STATE = 2;
    private static final CircuitBreakerConfig CONFIG_TO_TRIP_AFTER_ONE_FAILURE = CircuitBreakerConfig.custom()
                                                                                                     .failureRateThreshold(100F * 1 / RING_BUFFER_SIZE_IN_CLOSED_STATE)
                                                                                                     .ringBufferSizeInClosedState(RING_BUFFER_SIZE_IN_CLOSED_STATE)
                                                                                                     .build();
    private static final CircuitBreakerConfig CONFIG_TO_TRIP_AFTER_TWO_FAILURES = CircuitBreakerConfig.custom()
                                                                                                      .failureRateThreshold(100F * 2 / RING_BUFFER_SIZE_IN_CLOSED_STATE)
                                                                                                      .ringBufferSizeInClosedState(RING_BUFFER_SIZE_IN_CLOSED_STATE)
                                                                                                      .build();

    private ExampleConsumer exampleConsumer;
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Before
    public void setUp() {
        circuitBreakerRegistry = CircuitBreakerRegistry.of(CONFIG_TO_TRIP_AFTER_ONE_FAILURE);
        exampleConsumer = new ExampleConsumer();
    }

    @Test
    public void tryOutCircuitBreakerFromRegistryWithItsDefaultCircuit() throws Throwable {

        CircuitBreaker tripAfterOneFailure = circuitBreakerRegistry.circuitBreaker("trip-after-one-failure");
        fillClosedStateRingBuffer(tripAfterOneFailure, RING_BUFFER_SIZE_IN_CLOSED_STATE);

        CheckedConsumer<String> consumerThrowingExceptionWithCircuitBreaker = CircuitBreaker.decorateCheckedConsumer(tripAfterOneFailure, exampleConsumer::consumeAStringThrowingException);

        consumeCatchingExampleException(consumerThrowingExceptionWithCircuitBreaker);
        try {
            consumeCatchingExampleException(consumerThrowingExceptionWithCircuitBreaker);
            fail("Should have thrown a CircuitBreakerOpenException");
        }
        catch(CircuitBreakerOpenException e) {
            // The circuit has tripped as expected.
        }
    }

    @Test
    public void tryOutCircuitBreakerFromRegistryWithCustomCircuit() throws Throwable {

        CircuitBreaker tripAfterTwoFailures = circuitBreakerRegistry.circuitBreaker("trip-after-two-failures", CONFIG_TO_TRIP_AFTER_TWO_FAILURES);
        fillClosedStateRingBuffer(tripAfterTwoFailures, RING_BUFFER_SIZE_IN_CLOSED_STATE);

        CheckedConsumer<String> consumerThrowingExceptionWithCircuitBreaker = CircuitBreaker.decorateCheckedConsumer(tripAfterTwoFailures, exampleConsumer::consumeAStringThrowingException);

        consumeCatchingExampleException(consumerThrowingExceptionWithCircuitBreaker);
        consumeCatchingExampleException(consumerThrowingExceptionWithCircuitBreaker);
        try {
            consumeCatchingExampleException(consumerThrowingExceptionWithCircuitBreaker);
            fail("Should have thrown a CircuitBreakerOpenException");
        }
        catch(CircuitBreakerOpenException e) {
            // The circuit has tripped as expected.
        }
    }

    private void fillClosedStateRingBuffer(CircuitBreaker circuitBreaker, int ringBufferSize) throws Throwable {
        CheckedConsumer<String> successfulConsumerWithCircuitBreaker = CircuitBreaker.decorateCheckedConsumer(circuitBreaker, exampleConsumer::consumeAString);
        for (int i = 1; i <= ringBufferSize; i++) {
            successfulConsumerWithCircuitBreaker.accept(String.format("Not throwing exception %02d", i));
        }
    }

    private void consumeCatchingExampleException(CheckedConsumer<String> consumer) throws Throwable {
        try {
            consumer.accept("Consuming");
        }
        catch (ExampleException e) {
            System.out.println(e.getMessage());
        }
    }

}
