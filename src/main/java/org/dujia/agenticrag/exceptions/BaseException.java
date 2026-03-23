package org.dujia.agenticrag.exceptions;

import lombok.Getter;
import org.dujia.agenticrag.enums.ErrorCode;

@Getter
public class BaseException extends RuntimeException {

    private int code;

    public BaseException(String message) {
        super(message);
    }

    public BaseException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }
}
