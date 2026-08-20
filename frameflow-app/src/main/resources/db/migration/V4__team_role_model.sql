-- =============================================================================
-- V4: FrameFlow Select team role model (S0 FF-PIV-003)
-- Renames legacy team roles to the authoritative product role set and
-- tightens the column guard. Forward-only; V1-V3 are NOT modified.
--
-- Legacy mapping (docs/contracts/ROLE-PERMISSION-MATRIX.md):
--   OWNER    -> OWNER
--   PRODUCER -> OPERATOR
--   EDITOR   -> REVIEWER
--   VIEWER   -> VIEWER
--   CLIENT   -> VIEWER only if encountered outside team role (not allowed here)
--
-- Data is remapped so stored values match the new enumeration, and the CHECK
-- constraint is replaced to admit only OWNER / OPERATOR / REVIEWER / VIEWER.
-- =============================================================================

UPDATE team_members SET role = 'OPERATOR' WHERE role = 'PRODUCER';
UPDATE team_members SET role = 'REVIEWER'  WHERE role = 'EDITOR';

ALTER TABLE team_members DROP CONSTRAINT ck_team_members_role;
ALTER TABLE team_members ADD CONSTRAINT ck_team_members_role
    CHECK (role IN ('OWNER', 'OPERATOR', 'REVIEWER', 'VIEWER'));
