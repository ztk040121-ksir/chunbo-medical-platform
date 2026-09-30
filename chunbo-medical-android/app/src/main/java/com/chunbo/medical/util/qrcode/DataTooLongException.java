package com.chunbo.medical.util.qrcode;

public class DataTooLongException extends IllegalArgumentException {
    public DataTooLongException() {
        super();
    }
    public DataTooLongException(String message) {
        super(message);
    }
}
