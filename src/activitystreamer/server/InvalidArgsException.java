package activitystreamer.server;

public class InvalidArgsException extends Exception {
    
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public InvalidArgsException() {
        super();
    }
	
	public InvalidArgsException(String message) {
		super(message);
	}
    
    public InvalidArgsException(Throwable cause) {
        super(cause);
    }
    
    public InvalidArgsException(String message, Throwable cause) {
        super(message, cause);
    }

    public String getMessage() {
        return "invalid arguments"; // TODO
    }

}
