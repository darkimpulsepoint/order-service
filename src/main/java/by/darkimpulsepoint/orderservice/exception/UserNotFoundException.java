package by.darkimpulsepoint.orderservice.exception;

public class UserNotFoundException extends ResourceNotFoundException {

    public UserNotFoundException(String message) {
        super(message);
    }
}
