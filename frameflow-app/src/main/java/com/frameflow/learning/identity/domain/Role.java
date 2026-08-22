package com.frameflow.learning.identity.domain;

/**
 * 团队内角色（docs/01 §4 角色模型）。
 * 与数据库 CHECK 约束的四个合法值一一对应——枚举在应用层挡住非法值，
 * CHECK 在数据库层兜底，两层防线缺一不可。
 */
public enum Role {
    OWNER, OPERATOR, REVIEWER, VIEWER
}
