package com.frameflow.learning.identity.repo;

/** idempotency_records 的读取结果。 */
public class IdempotencyRecordRow {

    private int responseStatus;
    private String responseBody;

    public int getResponseStatus() { return responseStatus; }
    public void setResponseStatus(int responseStatus) { this.responseStatus = responseStatus; }
    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
}
