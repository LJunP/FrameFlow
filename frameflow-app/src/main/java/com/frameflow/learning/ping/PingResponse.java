package com.frameflow.learning.ping;

/**
 * ping 接口的响应体。
 *
 * Java 17 record：纯数据载体的最简写法——编译器自动生成构造器、访问器
 * (app()/version()/serverTime())、equals/hashCode/toString，且字段不可变。
 * Jackson 会自动把 record 序列化成 JSON 对象。
 */
public record PingResponse(String app, String version, String serverTime) {
}
