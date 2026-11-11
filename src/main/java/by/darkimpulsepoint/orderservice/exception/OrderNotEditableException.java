package by.darkimpulsepoint.orderservice.exception;

public class OrderNotEditableException extends RuntimeException {

    public OrderNotEditableException(String message) {
        super(message);
    }

    public OrderNotEditableException(String message, Throwable cause) {
        super(message, cause);
    }
}
