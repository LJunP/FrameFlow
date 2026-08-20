package com.frameflow.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 团队角色（CLIENT 是项目级角色，M02 起，不在团队角色枚举内）。 */
@Schema(description = "团队角色（CLIENT 是项目级角色，M02 起，不在团队角色枚举内）")
public enum TeamRole {
    OWNER,
    OPERATOR,
    REVIEWER,
    VIEWER
}
