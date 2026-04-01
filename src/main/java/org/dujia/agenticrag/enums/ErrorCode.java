package org.dujia.agenticrag.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.dujia.agenticrag.commons.Common;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    USER_ALREADY_EXISTS(1000, Common.USER_ALREADY_EXISTS),
    TOKEN_ERROR(1001, Common.TOKEN_ERROR),
    UNAUTHORIZED(1002, Common.UNAUTHORIZED),
    THE_FILE_CANNOT_BE_EMPTY(1002, Common.THE_FILE_CANNOT_BE_EMPTY),
    DOCUMENT_NOT_FOUND(40001, Common.DOCUMENT_NOT_FOUND),
    FAIL_TO_REGISTER(10003, Common.FAIL_TO_REGISTER),
    USER_NOT_FOUND(10004, Common.USER_NOT_FOUND),
    INVALID_USERNAME_OR_PASSWORD(10005, Common.INVALID_USERNAME_OR_PASSWORD);

    private final int code;
    private final String message;
}
