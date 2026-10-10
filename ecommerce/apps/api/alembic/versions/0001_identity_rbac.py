"""Explicit Phase 2 identity, RBAC and audit schema."""
from alembic import op
import sqlalchemy as sa

revision = "0001_identity_rbac"
down_revision = None
branch_labels = None
depends_on = None


def upgrade():
    op.create_table("users",
        sa.Column("id",sa.String(36),primary_key=True), sa.Column("email",sa.String(254),unique=True),
        sa.Column("phone",sa.String(32),unique=True), sa.Column("password_hash",sa.String(255),nullable=False),
        sa.Column("display_name",sa.String(120),nullable=False), sa.Column("status",sa.String(20),nullable=False),
        sa.Column("created_at",sa.DateTime(timezone=True),nullable=False), sa.Column("updated_at",sa.DateTime(timezone=True),nullable=False))
    op.create_index("ix_users_status","users",["status"])
    op.create_table("admin_users",
        sa.Column("id",sa.String(36),primary_key=True), sa.Column("username",sa.String(80),nullable=False,unique=True),
        sa.Column("email",sa.String(254),unique=True), sa.Column("password_hash",sa.String(255),nullable=False),
        sa.Column("display_name",sa.String(120),nullable=False), sa.Column("status",sa.String(20),nullable=False),
        sa.Column("is_superuser",sa.Boolean(),nullable=False), sa.Column("created_at",sa.DateTime(timezone=True),nullable=False),
        sa.Column("updated_at",sa.DateTime(timezone=True),nullable=False))
    op.create_index("ix_admin_users_status","admin_users",["status"])
    op.create_table("merchants",
        sa.Column("id",sa.String(36),primary_key=True), sa.Column("name",sa.String(160),nullable=False),
        sa.Column("type",sa.String(32),nullable=False), sa.Column("status",sa.String(24),nullable=False),
        sa.Column("review_status",sa.String(24),nullable=False), sa.Column("created_at",sa.DateTime(timezone=True),nullable=False),
        sa.Column("updated_at",sa.DateTime(timezone=True),nullable=False))
    op.create_index("ix_merchants_type","merchants",["type"]); op.create_index("ix_merchants_status","merchants",["status"])
    op.create_table("roles",
        sa.Column("id",sa.String(36),primary_key=True), sa.Column("code",sa.String(80),nullable=False),
        sa.Column("name",sa.String(120),nullable=False), sa.Column("scope",sa.String(24),nullable=False),
        sa.Column("merchant_id",sa.String(36),sa.ForeignKey("merchants.id",ondelete="CASCADE")),
        sa.Column("description",sa.String(255),nullable=False), sa.Column("created_at",sa.DateTime(timezone=True),nullable=False),
        sa.Column("updated_at",sa.DateTime(timezone=True),nullable=False), sa.UniqueConstraint("code","merchant_id",name="uq_role_code_merchant"))
    op.create_index("ix_roles_code","roles",["code"]);op.create_index("ix_roles_scope","roles",["scope"]);op.create_index("ix_roles_merchant_id","roles",["merchant_id"])
    op.create_table("permissions",
        sa.Column("id",sa.String(36),primary_key=True),sa.Column("code",sa.String(120),nullable=False,unique=True),
        sa.Column("name",sa.String(160),nullable=False),sa.Column("resource",sa.String(80),nullable=False),
        sa.Column("action",sa.String(40),nullable=False),sa.Column("kind",sa.String(24),nullable=False),
        sa.Column("created_at",sa.DateTime(timezone=True),nullable=False),sa.Column("updated_at",sa.DateTime(timezone=True),nullable=False))
    op.create_table("role_permissions",
        sa.Column("role_id",sa.String(36),sa.ForeignKey("roles.id",ondelete="CASCADE"),primary_key=True),
        sa.Column("permission_id",sa.String(36),sa.ForeignKey("permissions.id",ondelete="CASCADE"),primary_key=True))
    op.create_table("user_roles",
        sa.Column("id",sa.String(36),primary_key=True),sa.Column("user_kind",sa.String(16),nullable=False),
        sa.Column("user_id",sa.String(36),nullable=False),sa.Column("role_id",sa.String(36),sa.ForeignKey("roles.id",ondelete="CASCADE"),nullable=False),
        sa.Column("merchant_id",sa.String(36),sa.ForeignKey("merchants.id",ondelete="CASCADE")),
        sa.Column("created_at",sa.DateTime(timezone=True),nullable=False),sa.Column("updated_at",sa.DateTime(timezone=True),nullable=False),
        sa.UniqueConstraint("user_kind","user_id","role_id","merchant_id",name="uq_user_role_scope"))
    for col in ("user_kind","user_id","merchant_id"):op.create_index(f"ix_user_roles_{col}","user_roles",[col])
    op.create_table("merchant_users",
        sa.Column("id",sa.String(36),primary_key=True),sa.Column("merchant_id",sa.String(36),sa.ForeignKey("merchants.id",ondelete="CASCADE"),nullable=False),
        sa.Column("user_id",sa.String(36),sa.ForeignKey("admin_users.id",ondelete="CASCADE"),nullable=False),
        sa.Column("status",sa.String(20),nullable=False),sa.Column("created_at",sa.DateTime(timezone=True),nullable=False),
        sa.Column("updated_at",sa.DateTime(timezone=True),nullable=False),sa.UniqueConstraint("merchant_id","user_id",name="uq_merchant_user"))
    op.create_index("ix_merchant_users_merchant_id","merchant_users",["merchant_id"]);op.create_index("ix_merchant_users_user_id","merchant_users",["user_id"]);op.create_index("ix_merchant_users_status","merchant_users",["status"])
    op.create_table("sessions",
        sa.Column("id",sa.String(36),primary_key=True),sa.Column("token_hash",sa.String(64),nullable=False,unique=True),
        sa.Column("subject_id",sa.String(36),nullable=False),sa.Column("subject_kind",sa.String(16),nullable=False),
        sa.Column("merchant_id",sa.String(36),sa.ForeignKey("merchants.id",ondelete="CASCADE")),
        sa.Column("expires_at",sa.DateTime(timezone=True),nullable=False),sa.Column("revoked_at",sa.DateTime(timezone=True)),
        sa.Column("created_at",sa.DateTime(timezone=True),nullable=False))
    for col in ("subject_id","subject_kind","merchant_id","expires_at"):op.create_index(f"ix_sessions_{col}","sessions",[col])
    op.create_table("audit_logs",
        sa.Column("id",sa.String(36),primary_key=True),sa.Column("actor_id",sa.String(36)),sa.Column("actor_kind",sa.String(16),nullable=False),
        sa.Column("merchant_id",sa.String(36)),sa.Column("action",sa.String(120),nullable=False),sa.Column("target_type",sa.String(80)),
        sa.Column("target_id",sa.String(36)),sa.Column("request_id",sa.String(64),nullable=False),sa.Column("details",sa.Text(),nullable=False),
        sa.Column("created_at",sa.DateTime(timezone=True),nullable=False))
    for col in ("actor_id","merchant_id","action","target_id","request_id","created_at"):op.create_index(f"ix_audit_logs_{col}","audit_logs",[col])
    op.create_index("ix_audit_actor_time","audit_logs",["actor_id","created_at"])
    op.create_table("login_logs",
        sa.Column("id",sa.String(36),primary_key=True),sa.Column("subject_id",sa.String(36)),sa.Column("subject_kind",sa.String(16),nullable=False),
        sa.Column("login_hint_hash",sa.String(64),nullable=False),sa.Column("success",sa.Boolean(),nullable=False),
        sa.Column("request_id",sa.String(64),nullable=False),sa.Column("created_at",sa.DateTime(timezone=True),nullable=False))
    for col in ("subject_id","subject_kind","success","request_id","created_at"):op.create_index(f"ix_login_logs_{col}","login_logs",[col])
    op.create_table("system_settings",
        sa.Column("key",sa.String(120),primary_key=True),sa.Column("value",sa.Text(),nullable=False),sa.Column("is_secret",sa.Boolean(),nullable=False),
        sa.Column("updated_by",sa.String(36),sa.ForeignKey("admin_users.id",ondelete="SET NULL")),
        sa.Column("created_at",sa.DateTime(timezone=True),nullable=False),sa.Column("updated_at",sa.DateTime(timezone=True),nullable=False))


def downgrade():
    op.drop_table("system_settings");op.drop_table("login_logs");op.drop_table("audit_logs");op.drop_table("sessions")
    op.drop_table("merchant_users");op.drop_table("user_roles");op.drop_table("role_permissions");op.drop_table("permissions")
    op.drop_table("roles");op.drop_table("merchants");op.drop_table("admin_users");op.drop_table("users")
