package com.restcomm.identity;

public class InstanceManagerException extends Exception {
    private Integer statusCode;

    public InstanceManagerException(Integer statusCode) {
        super();
        this.statusCode = statusCode;
    }

    public InstanceManagerException() {
        // TODO Auto-generated constructor stub
    }

    public InstanceManagerException(String message) {
        super(message);
        // TODO Auto-generated constructor stub
    }

    public InstanceManagerException(Throwable cause) {
        super(cause);
        // TODO Auto-generated constructor stub
    }

    public InstanceManagerException(String message, Throwable cause) {
        super(message, cause);
        // TODO Auto-generated constructor stub
    }

    public InstanceManagerException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        // TODO Auto-generated constructor stub
    }

    public Integer getStatusCode() {
        return statusCode;
    }

}
