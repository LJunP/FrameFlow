package com.frameflow.learning.identity.service;

import com.frameflow.learning.identity.domain.Role;
import com.frameflow.learning.identity.repo.MemberMapper;
import com.frameflow.learning.identity.repo.MemberRow;
import com.frameflow.learning.shared.error.ApiException;
import com.frameflow.learning.shared.error.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * 团队访问控制：F1 在 AuthService 里内联过一次（teamMembers），F2 起多个
 * 模块都需要同样的判定，抽出成独立组件——同一规则只在一个地方定义。
 *
 * product 模块通过本类做权限判定，【不直接触碰】identity 的表，
 * 这是 docs/02 §3 模块边界（跨模块只走公开接口）的落点。
 */
@Service
public class TeamAccessService {

    private final MemberMapper members;

    public TeamAccessService(MemberMapper members) {
        this.members = members;
    }

    /** 成员校验：不是该团队成员 → 404（防枚举，语义与 F1 完全一致）。 */
    public MemberRow requireMember(long userId, long teamId) {
        MemberRow member = members.findByUserAndTeam(userId, teamId);
        if (member == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return member;
    }

    // ★ 核心：角色判定的标准入口——永远先 requireMember(404) 再比角色(403)。
    // 顺序反了就会向试探者泄露"团队存在"；这条规则从 F1 的 teamMembers
    // 原样迁移至此，成为全项目唯一的权限判定路径。
    public MemberRow requireRole(long userId, long teamId, Role... allowedRoles) {
        MemberRow member = requireMember(userId, teamId);
        for (Role allowed : allowedRoles) {
            if (allowed.name().equals(member.getRole())) {
                return member;
            }
        }
        throw new ApiException(ErrorCode.FORBIDDEN);
    }
}
