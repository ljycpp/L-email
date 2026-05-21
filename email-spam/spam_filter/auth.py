import hashlib
import hmac
import json
import os
import secrets
import threading
import time
from copy import deepcopy


DEFAULT_ADMIN_USERNAME = "admin"
DEFAULT_ADMIN_PASSWORD = "admin123"
DEFAULT_ADMIN_DISPLAY_NAME = "系统管理员"
PASSWORD_ITERATIONS = 120000
SESSION_TTL_SECONDS = 12 * 60 * 60


def _now_timestamp():
    return int(time.time())


def _make_password_hash(password, salt=None, iterations=PASSWORD_ITERATIONS):
    if not password or len(password) < 6:
        raise ValueError("密码长度至少需要 6 位。")

    raw_salt = salt or os.urandom(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), raw_salt, iterations)
    return {
        "algorithm": "pbkdf2_sha256",
        "iterations": int(iterations),
        "salt": raw_salt.hex(),
        "hash": digest.hex(),
    }


def _verify_password(password, password_data):
    raw_salt = bytes.fromhex(password_data["salt"])
    digest = hashlib.pbkdf2_hmac(
        "sha256",
        password.encode("utf-8"),
        raw_salt,
        int(password_data["iterations"]),
    )
    return hmac.compare_digest(digest.hex(), password_data["hash"])


def _safe_user(user):
    safe = deepcopy(user)
    safe.pop("password", None)
    return safe


class UserStore(object):
    def __init__(self, path):
        self.path = path
        self.lock = threading.RLock()
        self.users = []
        self.bootstrap_created = self.ensure_initialized()

    def ensure_initialized(self):
        with self.lock:
            if os.path.exists(self.path):
                self._load()
                return False

            directory = os.path.dirname(self.path)
            if directory:
                os.makedirs(directory, exist_ok=True)

            now = _now_timestamp()
            default_admin = {
                "id": secrets.token_hex(8),
                "username": DEFAULT_ADMIN_USERNAME,
                "display_name": DEFAULT_ADMIN_DISPLAY_NAME,
                "role": "admin",
                "enabled": True,
                "created_at": now,
                "updated_at": now,
                "password": _make_password_hash(DEFAULT_ADMIN_PASSWORD),
            }
            self.users = [default_admin]
            self._save()
            return True

    def _load(self):
        with open(self.path, "r", encoding="utf-8") as file_obj:
            payload = json.load(file_obj)
        self.users = payload.get("users", [])
        if not self.users:
            raise ValueError("用户文件为空，至少需要一个账号。")

    def _save(self):
        payload = {"users": self.users}
        with open(self.path, "w", encoding="utf-8") as file_obj:
            json.dump(payload, file_obj, ensure_ascii=False, indent=2)

    def list_users(self):
        with self.lock:
            return [_safe_user(user) for user in self.users]

    def find_by_username(self, username):
        normalized = (username or "").strip().lower()
        with self.lock:
            for user in self.users:
                if user["username"].lower() == normalized:
                    return deepcopy(user)
        return None

    def find_by_id(self, user_id):
        with self.lock:
            for user in self.users:
                if user["id"] == user_id:
                    return deepcopy(user)
        return None

    def authenticate(self, username, password):
        user = self.find_by_username(username)
        if not user:
            raise ValueError("用户名或密码错误。")
        if not user.get("enabled", True):
            raise ValueError("当前账号已被停用。")
        if not _verify_password(password, user["password"]):
            raise ValueError("用户名或密码错误。")
        return _safe_user(user)

    def create_user(self, username, password, display_name="", role="analyst"):
        normalized = (username or "").strip()
        if not normalized:
            raise ValueError("用户名不能为空。")
        if role not in ("admin", "analyst"):
            raise ValueError("角色只能是 admin 或 analyst。")

        with self.lock:
            if self.find_by_username(normalized):
                raise ValueError("用户名已存在，请换一个。")

            now = _now_timestamp()
            user = {
                "id": secrets.token_hex(8),
                "username": normalized,
                "display_name": (display_name or "").strip() or normalized,
                "role": role,
                "enabled": True,
                "created_at": now,
                "updated_at": now,
                "password": _make_password_hash(password),
            }
            self.users.append(user)
            self._save()
            return _safe_user(user)

    def update_user(self, user_id, display_name=None, role=None, enabled=None, acting_user_id=None):
        with self.lock:
            index = self._find_index(user_id)
            user = self.users[index]

            if display_name is not None:
                display = display_name.strip()
                if not display:
                    raise ValueError("显示名称不能为空。")
                user["display_name"] = display

            if role is not None:
                if role not in ("admin", "analyst"):
                    raise ValueError("角色只能是 admin 或 analyst。")
                if user["id"] == acting_user_id and role != "admin":
                    raise ValueError("当前登录管理员不能把自己降级。")
                if user["role"] == "admin" and role != "admin" and self._count_enabled_admins() <= 1:
                    raise ValueError("系统至少需要保留一个启用中的管理员。")
                user["role"] = role

            if enabled is not None:
                enabled_flag = bool(enabled)
                if user["id"] == acting_user_id and not enabled_flag:
                    raise ValueError("不能停用当前登录账号。")
                if user["role"] == "admin" and not enabled_flag and self._count_enabled_admins() <= 1:
                    raise ValueError("系统至少需要保留一个启用中的管理员。")
                user["enabled"] = enabled_flag

            user["updated_at"] = _now_timestamp()
            self._save()
            return _safe_user(user)

    def reset_password(self, user_id, new_password):
        with self.lock:
            index = self._find_index(user_id)
            self.users[index]["password"] = _make_password_hash(new_password)
            self.users[index]["updated_at"] = _now_timestamp()
            self._save()
            return _safe_user(self.users[index])

    def _find_index(self, user_id):
        for index, user in enumerate(self.users):
            if user["id"] == user_id:
                return index
        raise ValueError("用户不存在。")

    def _count_enabled_admins(self):
        return sum(1 for user in self.users if user.get("enabled", True) and user.get("role") == "admin")


class SessionStore(object):
    def __init__(self, ttl_seconds=SESSION_TTL_SECONDS):
        self.ttl_seconds = int(ttl_seconds)
        self.lock = threading.RLock()
        self.sessions = {}

    def create_session(self, user):
        with self.lock:
            self._purge_expired()
            token = secrets.token_urlsafe(32)
            now = _now_timestamp()
            self.sessions[token] = {
                "user_id": user["id"],
                "created_at": now,
                "expires_at": now + self.ttl_seconds,
            }
            return token

    def get_session(self, token):
        if not token:
            return None
        with self.lock:
            self._purge_expired()
            session = self.sessions.get(token)
            if not session:
                return None
            if session["expires_at"] <= _now_timestamp():
                self.sessions.pop(token, None)
                return None
            return deepcopy(session)

    def delete_session(self, token):
        if not token:
            return
        with self.lock:
            self.sessions.pop(token, None)

    def _purge_expired(self):
        now = _now_timestamp()
        expired = [token for token, session in self.sessions.items() if session["expires_at"] <= now]
        for token in expired:
            self.sessions.pop(token, None)
