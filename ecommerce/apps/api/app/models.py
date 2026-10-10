from sqlalchemy import String, Boolean, DateTime, ForeignKey, Text, UniqueConstraint, Index
from sqlalchemy.orm import Mapped, mapped_column, relationship
from .db import Base, new_id, now_utc

class TimestampMixin:
    created_at: Mapped[object] = mapped_column(DateTime(timezone=True), default=now_utc, nullable=False)
    updated_at: Mapped[object] = mapped_column(DateTime(timezone=True), default=now_utc, onupdate=now_utc, nullable=False)

class User(Base, TimestampMixin):
    __tablename__="users"
    id: Mapped[str]=mapped_column(String(36),primary_key=True,default=new_id)
    email: Mapped[str|None]=mapped_column(String(254),unique=True)
    phone: Mapped[str|None]=mapped_column(String(32),unique=True)
    password_hash: Mapped[str]=mapped_column(String(255),nullable=False)
    display_name: Mapped[str]=mapped_column(String(120),default="")
    status: Mapped[str]=mapped_column(String(20),default="ACTIVE",index=True)

class AdminUser(Base, TimestampMixin):
    __tablename__="admin_users"
    id: Mapped[str]=mapped_column(String(36),primary_key=True,default=new_id)
    username: Mapped[str]=mapped_column(String(80),unique=True)
    email: Mapped[str|None]=mapped_column(String(254),unique=True)
    password_hash: Mapped[str]=mapped_column(String(255),nullable=False)
    display_name: Mapped[str]=mapped_column(String(120),default="")
    status: Mapped[str]=mapped_column(String(20),default="ACTIVE",index=True)
    is_superuser: Mapped[bool]=mapped_column(Boolean,default=False)

class Merchant(Base, TimestampMixin):
    __tablename__="merchants"
    id: Mapped[str]=mapped_column(String(36),primary_key=True,default=new_id)
    name: Mapped[str]=mapped_column(String(160),nullable=False)
    type: Mapped[str]=mapped_column(String(32),nullable=False,index=True)
    status: Mapped[str]=mapped_column(String(24),default="ACTIVE",index=True)
    review_status: Mapped[str]=mapped_column(String(24),default="NOT_REQUIRED")

class Role(Base, TimestampMixin):
    __tablename__="roles"
    __table_args__=(UniqueConstraint("code","merchant_id",name="uq_role_code_merchant"),)
    id: Mapped[str]=mapped_column(String(36),primary_key=True,default=new_id)
    code: Mapped[str]=mapped_column(String(80),index=True)
    name: Mapped[str]=mapped_column(String(120),nullable=False)
    scope: Mapped[str]=mapped_column(String(24),nullable=False,index=True)
    merchant_id: Mapped[str|None]=mapped_column(ForeignKey("merchants.id",ondelete="CASCADE"),index=True)
    description: Mapped[str]=mapped_column(String(255),default="")

class Permission(Base, TimestampMixin):
    __tablename__="permissions"
    id: Mapped[str]=mapped_column(String(36),primary_key=True,default=new_id)
    code: Mapped[str]=mapped_column(String(120),unique=True)
    name: Mapped[str]=mapped_column(String(160),nullable=False)
    resource: Mapped[str]=mapped_column(String(80),nullable=False)
    action: Mapped[str]=mapped_column(String(40),nullable=False)
    kind: Mapped[str]=mapped_column(String(24),default="API")

class RolePermission(Base):
    __tablename__="role_permissions"
    role_id: Mapped[str]=mapped_column(ForeignKey("roles.id",ondelete="CASCADE"),primary_key=True)
    permission_id: Mapped[str]=mapped_column(ForeignKey("permissions.id",ondelete="CASCADE"),primary_key=True)

class UserRole(Base, TimestampMixin):
    __tablename__="user_roles"
    __table_args__=(UniqueConstraint("user_kind","user_id","role_id","merchant_id",name="uq_user_role_scope"),)
    id: Mapped[str]=mapped_column(String(36),primary_key=True,default=new_id)
    user_kind: Mapped[str]=mapped_column(String(16),nullable=False,index=True)
    user_id: Mapped[str]=mapped_column(String(36),nullable=False,index=True)
    role_id: Mapped[str]=mapped_column(ForeignKey("roles.id",ondelete="CASCADE"),nullable=False)
    merchant_id: Mapped[str|None]=mapped_column(ForeignKey("merchants.id",ondelete="CASCADE"),index=True)

class MerchantUser(Base, TimestampMixin):
    __tablename__="merchant_users"
    __table_args__=(UniqueConstraint("merchant_id","user_id",name="uq_merchant_user"),)
    id: Mapped[str]=mapped_column(String(36),primary_key=True,default=new_id)
    merchant_id: Mapped[str]=mapped_column(ForeignKey("merchants.id",ondelete="CASCADE"),nullable=False,index=True)
    user_id: Mapped[str]=mapped_column(ForeignKey("admin_users.id",ondelete="CASCADE"),nullable=False,index=True)
    status: Mapped[str]=mapped_column(String(20),default="ACTIVE",index=True)

class SessionToken(Base):
    __tablename__="sessions"
    id: Mapped[str]=mapped_column(String(36),primary_key=True,default=new_id)
    token_hash: Mapped[str]=mapped_column(String(64),unique=True,nullable=False)
    subject_id: Mapped[str]=mapped_column(String(36),nullable=False,index=True)
    subject_kind: Mapped[str]=mapped_column(String(16),nullable=False,index=True)
    merchant_id: Mapped[str|None]=mapped_column(ForeignKey("merchants.id",ondelete="CASCADE"),index=True)
    expires_at: Mapped[object]=mapped_column(DateTime(timezone=True),nullable=False,index=True)
    revoked_at: Mapped[object|None]=mapped_column(DateTime(timezone=True))
    created_at: Mapped[object]=mapped_column(DateTime(timezone=True),default=now_utc,nullable=False)

class AuditLog(Base):
    __tablename__="audit_logs"
    __table_args__=(Index("ix_audit_actor_time","actor_id","created_at"),)
    id: Mapped[str]=mapped_column(String(36),primary_key=True,default=new_id)
    actor_id: Mapped[str|None]=mapped_column(String(36),index=True)
    actor_kind: Mapped[str]=mapped_column(String(16),default="SYSTEM")
    merchant_id: Mapped[str|None]=mapped_column(String(36),index=True)
    action: Mapped[str]=mapped_column(String(120),nullable=False,index=True)
    target_type: Mapped[str|None]=mapped_column(String(80))
    target_id: Mapped[str|None]=mapped_column(String(36),index=True)
    request_id: Mapped[str]=mapped_column(String(64),nullable=False,index=True)
    details: Mapped[str]=mapped_column(Text,default="{}")
    created_at: Mapped[object]=mapped_column(DateTime(timezone=True),default=now_utc,nullable=False,index=True)

class LoginLog(Base):
    __tablename__="login_logs"
    id: Mapped[str]=mapped_column(String(36),primary_key=True,default=new_id)
    subject_id: Mapped[str|None]=mapped_column(String(36),index=True)
    subject_kind: Mapped[str]=mapped_column(String(16),nullable=False,index=True)
    login_hint_hash: Mapped[str]=mapped_column(String(64),nullable=False)
    success: Mapped[bool]=mapped_column(Boolean,nullable=False,index=True)
    request_id: Mapped[str]=mapped_column(String(64),nullable=False,index=True)
    created_at: Mapped[object]=mapped_column(DateTime(timezone=True),default=now_utc,nullable=False,index=True)

class SystemSetting(Base, TimestampMixin):
    __tablename__="system_settings"
    key: Mapped[str]=mapped_column(String(120),primary_key=True)
    value: Mapped[str]=mapped_column(Text,nullable=False)
    is_secret: Mapped[bool]=mapped_column(Boolean,default=False)
    updated_by: Mapped[str|None]=mapped_column(ForeignKey("admin_users.id",ondelete="SET NULL"))



