package sample.objects.exceptions;

/**
 * Unchecked timeout exception
 */
public class OperationTimeOutException extends RuntimeException {
    public OperationTimeOutException() {
    }

    public OperationTimeOutException(String message) {
        super(message);
    }

    public OperationTimeOutException(String message, Throwable cause) {
        super(message, cause);
    }

    public OperationTimeOutException(Throwable cause) {
        super(cause);
    }

    public OperationTimeOutException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
