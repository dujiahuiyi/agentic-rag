package org.dujia.agenticrag.domain;

import lombok.Data;

@Data
public class Result<T> {
    private int code; // 0 - 正常  1 - 异常
    private String errmsg;
    private T data;

    public static <T> Result<T> success() {
        Result<T> result = new Result<>();
        result.setCode(0);
        return result;
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(0);
        result.setData(data);
        return result;
    }

    public static <T> Result<T> fail(int code, String errmsg) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setErrmsg(errmsg);
        return result;
    }

    public static <T> Result<T> fail(String errmsg) {
        Result<T> result = new Result<>();
        result.setErrmsg(errmsg);
        return result;
    }
}
