package pm.resilience4j;

class ExampleConsumer {

    void consumeAString(String string) {
        System.out.println(string);
    }

    void consumeAStringThrowingException(String message) throws ExampleException {
        throw new ExampleException("Exception thrown when consuming \"" + message + "\"");
    }
}
