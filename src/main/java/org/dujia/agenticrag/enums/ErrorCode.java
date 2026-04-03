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
    DOCUMENT_NOT_FOUND(4001, Common.DOCUMENT_NOT_FOUND),
    FAIL_TO_REGISTER(1003, Common.FAIL_TO_REGISTER),
    USER_NOT_FOUND(1004, Common.USER_NOT_FOUND),
    INVALID_USERNAME_OR_PASSWORD(1005, Common.INVALID_USERNAME_OR_PASSWORD),
    INVALID_SESSION_ID(1006, Common.INVALID_SESSION_ID),
    UNAUTHORIZED_ACCESS(1007, Common.UNAUTHORIZED_ACCESS),
    UNABLE_TO_ACCESS_THIS_ASSISTANT(1008, Common.UNABLE_TO_ACCESS_THIS_ASSISTANT),
    ASSISTANT_NOT_EXIST(1009, Common.ASSISTANT_NOT_EXIST);


    private final int code;
    private final String message;
}
