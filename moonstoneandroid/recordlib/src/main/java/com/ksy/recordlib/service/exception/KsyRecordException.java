package com.ksy.recordlib.service.exception;

/**
 * Created by eflakemac on 15/6/17.
 */
public class KsyRecordException extends Exception {
    private static final long serialVersionUID = -2503345001841814995L;

    public KsyRecordException(String message, Throwable t) {
        super(message, t);
    }

    public KsyRecordException(String message) {
        super(message);
    }

    public KsyRecordException(Throwable t) {
        super(t);
    }
}
