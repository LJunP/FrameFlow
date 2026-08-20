# FrameFlow Select 角色与权限

Team roles:

```text
OWNER / OPERATOR / REVIEWER / VIEWER
```

| Operation | OWNER | OPERATOR | REVIEWER | VIEWER |
|---|---:|---:|---:|---:|
| Manage team and roles | yes | no | no | no |
| Create project | yes | yes | no | no |
| Create profile draft | yes | yes | no | no |
| Publish profile | yes | no | no | no |
| Create batch and upload | yes | yes | no | no |
| Start/cancel/rerun analysis | yes | yes | no | no |
| View results | yes | yes | yes | yes |
| Confirm/reject finding | yes | no | yes | no |
| Keep/reject candidate | yes | no | yes | no |
| Lock selection set | yes | no | yes | no |
| Change auto-reject policy | yes | no | no | no |

Legacy mapping:

```text
OWNER    → OWNER
PRODUCER → OPERATOR
EDITOR   → REVIEWER
VIEWER   → VIEWER
CLIENT   → VIEWER only if encountered outside team role
```

Resource existence is hidden with 404 for cross-team access; explicit operation permission failures use 403. The final active OWNER cannot be removed or downgraded.
