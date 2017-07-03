package pm.resilience4j;

class ExampleClass {

    void consumeAString(String string) {
        System.out.println(string);
    }

    void consumeAStringThrowingException(String message) throws ExampleException {
        throw new ExampleException("Exception thrown when consuming \"" + message + "\"");
    }

    static final class ExampleException extends Exception {
        ExampleException(String message) {
            super(message);
        }
    }
}
