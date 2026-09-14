-- =====================================================================
-- 邀请令牌改为只存 SHA-256 哈希（V8，只前向；不修改 V7）。
--
-- 背景：V7 把 256bit 随机令牌以明文存进 team_invitations.token。
-- 令牌本身就是入团凭证（持有即可接受邀请），明文存储意味着数据库一旦
-- 泄露，攻击者可直接用未过期的邀请入团——不需要破解任何东西。
-- 同一套系统里 refresh token 是存 SHA-256 的，两处强度必须一致。
--
-- 改后：列改名为 token_hash，只写哈希；明文仅在"创建邀请"的响应里
-- 返回一次，之后任何人（包括 Owner 拉列表）都拿不到。
-- =====================================================================

ALTER TABLE team_invitations RENAME COLUMN token TO token_hash;

-- 约束名不会跟随列重命名，显式改掉以免后续维护时误读
ALTER TABLE team_invitations RENAME CONSTRAINT uq_team_invitations_token
    TO uq_team_invitations_token_hash;

-- 已存在的待接受邀请一律作废：旧值是明文，无法就地转成哈希
-- （哈希不可逆，且我们本就不该继续持有明文）。受邀人需要 Owner 重新发起。
-- 这是 fail-closed 的取舍：宁可让几条在途邀请失效，也不保留一批明文凭证。
UPDATE team_invitations SET accepted_at = now() WHERE accepted_at IS NULL;
