package com.frameflow.identity.web.dto;

/** M01 成员 API 只返回 ACTIVE 关系；REMOVED 是数据库软删除态，INVITED 待未来邀请流程引入。 */
public enum MemberStatus {
    ACTIVE
}
