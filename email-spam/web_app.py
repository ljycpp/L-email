import argparse
import base64
import json
import os
import threading
from http.cookies import SimpleCookie
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse

from spam_filter.analytics import build_analysis_report
from spam_filter.auth import (
    DEFAULT_ADMIN_PASSWORD,
    DEFAULT_ADMIN_USERNAME,
    SESSION_TTL_SECONDS,
    SessionStore,
    UserStore,
)
from spam_filter.classifier import NaiveBayesSpamClassifier
from spam_filter.email_parser import parse_eml_bytes
from spam_filter.training import format_percentage, train_and_evaluate


BASE_DIR = Path(__file__).resolve().parent
STATIC_DIR = BASE_DIR / "webui"
USERS_FILE = BASE_DIR / "data" / "users.json"
SESSION_COOKIE_NAME = "mail_radar_session"


class SpamFilterWebService(object):
    def __init__(self, model_path, data_path, users_path, alpha=1.0, test_ratio=0.2, seed=42):
        self.model_path = model_path
        self.data_path = data_path
        self.alpha = float(alpha)
        self.test_ratio = float(test_ratio)
        self.seed = int(seed)
        self.lock = threading.RLock()
        self.model = None
        self.last_training_report = None
        self.analytics_cache = None
        self.analytics_cache_key = None
        self.user_store = UserStore(users_path)
        self.session_store = SessionStore(ttl_seconds=SESSION_TTL_SECONDS)

    def ensure_model_ready(self):
        with self.lock:
            if self.model is not None:
                return

            if not os.path.exists(self.model_path):
                self.last_training_report = train_and_evaluate(
                    data_path=self.data_path,
                    model_path=self.model_path,
                    test_ratio=self.test_ratio,
                    seed=self.seed,
                    alpha=self.alpha,
                )

            self.model = NaiveBayesSpamClassifier.load(self.model_path)

    def login(self, username, password):
        user = self.user_store.authenticate(username, password)
        session_token = self.session_store.create_session(user)
        return session_token, user

    def logout(self, session_token):
        self.session_store.delete_session(session_token)

    def get_current_user(self, session_token):
        session = self.session_store.get_session(session_token)
        if not session:
            return None

        user = self.user_store.find_by_id(session["user_id"])
        if not user or not user.get("enabled", True):
            self.session_store.delete_session(session_token)
            return None
        return self._present_user(user)

    def session_payload(self, user):
        return {
            "user": self._present_user(user),
            "permissions": {
                "manage_users": user.get("role") == "admin",
                "retrain_model": user.get("role") == "admin",
                "view_analytics": user.get("role") == "admin",
            },
        }

    def predict_text(self, subject, body):
        with self.lock:
            self.ensure_model_ready()
            predicted_label, probabilities = self.model.predict(subject, body)

        spam_score = probabilities.get("spam", 0.0)
        ham_score = probabilities.get("ham", 0.0)
        confidence = max(spam_score, ham_score)
        return {
            "label": predicted_label,
            "label_text": "垃圾邮件" if predicted_label == "spam" else "正常邮件",
            "subject": subject,
            "body_preview": (body or "").strip()[:240],
            "scores": {"spam": spam_score, "ham": ham_score},
            "confidence": confidence,
            "confidence_text": format_percentage(confidence),
        }

    def predict_eml(self, raw_bytes, filename=""):
        subject, body = parse_eml_bytes(raw_bytes)
        result = self.predict_text(subject, body)
        result["filename"] = filename
        return result

    def retrain(self, data_path=None):
        with self.lock:
            target_data_path = data_path or self.data_path
            self.last_training_report = train_and_evaluate(
                data_path=target_data_path,
                model_path=self.model_path,
                test_ratio=self.test_ratio,
                seed=self.seed,
                alpha=self.alpha,
            )
            self.model = NaiveBayesSpamClassifier.load(self.model_path)
            self.data_path = target_data_path
            self.analytics_cache = None
            self.analytics_cache_key = None
            return self.status()

    def status(self):
        with self.lock:
            self.ensure_model_ready()
            top_tokens = []
            for score, token in self.model.top_indicative_tokens("spam", limit=6):
                top_tokens.append({"token": token, "score": score})

            return {
                "model_path": self.model_path,
                "data_path": self.data_path,
                "alpha": self.alpha,
                "test_ratio": self.test_ratio,
                "seed": self.seed,
                "model_exists": os.path.exists(self.model_path),
                "top_tokens": top_tokens,
                "last_training_report": self.last_training_report,
            }

    def list_users(self):
        return {
            "users": [self._present_user(user) for user in self.user_store.list_users()],
        }

    def create_user(self, username, password, display_name="", role="analyst"):
        created_user = self.user_store.create_user(
            username=username,
            password=password,
            display_name=display_name,
            role=role,
        )
        return {
            "user": self._present_user(created_user),
            "users": [self._present_user(user) for user in self.user_store.list_users()],
        }

    def update_user(self, user_id, display_name=None, role=None, enabled=None, acting_user_id=None):
        updated_user = self.user_store.update_user(
            user_id=user_id,
            display_name=display_name,
            role=role,
            enabled=enabled,
            acting_user_id=acting_user_id,
        )
        return {
            "user": self._present_user(updated_user),
            "users": [self._present_user(user) for user in self.user_store.list_users()],
        }

    def reset_password(self, user_id, new_password):
        updated_user = self.user_store.reset_password(user_id, new_password)
        return {
            "user": self._present_user(updated_user),
            "users": [self._present_user(user) for user in self.user_store.list_users()],
        }

    def analytics_report(self):
        with self.lock:
            self.ensure_model_ready()
            cache_key = (
                self.data_path,
                self.alpha,
                self.test_ratio,
                self.seed,
                os.path.getmtime(self.data_path),
            )
            if self.analytics_cache_key == cache_key and self.analytics_cache is not None:
                return self.analytics_cache

            report = build_analysis_report(
                data_path=self.data_path,
                alpha=self.alpha,
                test_ratio=self.test_ratio,
                seed=self.seed,
            )
            self.analytics_cache = report
            self.analytics_cache_key = cache_key
            return report

    def _present_user(self, user):
        return {
            "id": user["id"],
            "username": user["username"],
            "display_name": user.get("display_name") or user["username"],
            "role": user["role"],
            "role_text": "管理员" if user["role"] == "admin" else "分析员",
            "enabled": bool(user.get("enabled", True)),
            "enabled_text": "启用" if user.get("enabled", True) else "停用",
            "created_at": user.get("created_at"),
            "updated_at": user.get("updated_at"),
        }


class SpamFilterHTTPServer(ThreadingHTTPServer):
    def __init__(self, server_address, request_handler_class, app_service, static_dir):
        ThreadingHTTPServer.__init__(self, server_address, request_handler_class)
        self.app_service = app_service
        self.static_dir = Path(static_dir)


class SpamFilterRequestHandler(BaseHTTPRequestHandler):
    server_version = "SpamFilterWeb/2.0"

    def do_GET(self):
        parsed = urlparse(self.path)
        current_user = self._get_current_user()

        if parsed.path == "/":
            if not current_user:
                self._redirect("/login")
                return
            self._serve_static_file("index.html", "text/html; charset=utf-8")
            return

        if parsed.path == "/login":
            if current_user:
                self._redirect("/")
                return
            self._serve_static_file("login.html", "text/html; charset=utf-8")
            return

        if parsed.path == "/admin/analytics":
            user = self._require_user(api_mode=False)
            if not user:
                return
            if user.get("role") != "admin":
                self._redirect("/")
                return
            self._serve_static_file("analytics.html", "text/html; charset=utf-8")
            return

        if parsed.path == "/assets/app.css":
            self._serve_static_file("app.css", "text/css; charset=utf-8")
            return
        if parsed.path == "/assets/app.js":
            self._serve_static_file("app.js", "application/javascript; charset=utf-8")
            return
        if parsed.path == "/assets/analytics.js":
            self._serve_static_file("analytics.js", "application/javascript; charset=utf-8")
            return
        if parsed.path == "/assets/login.js":
            self._serve_static_file("login.js", "application/javascript; charset=utf-8")
            return

        if parsed.path == "/api/session":
            user = self._require_user(api_mode=True)
            if not user:
                return
            self._send_json(200, self.server.app_service.session_payload(user))
            return

        if parsed.path == "/api/status":
            user = self._require_user(api_mode=True)
            if not user:
                return
            self._send_json(200, self.server.app_service.status())
            return

        if parsed.path == "/api/users":
            user = self._require_user(api_mode=True)
            if not user:
                return
            if not self._require_admin(user):
                return
            self._send_json(200, self.server.app_service.list_users())
            return

        if parsed.path == "/api/analytics":
            user = self._require_user(api_mode=True)
            if not user:
                return
            if not self._require_admin(user):
                return
            self._send_json(200, self.server.app_service.analytics_report())
            return

        self._send_json(404, {"error": "未找到请求资源。"})

    def do_POST(self):
        parsed = urlparse(self.path)
        try:
            if parsed.path == "/api/login":
                payload = self._read_json_body()
                session_token, user = self.server.app_service.login(
                    payload.get("username", ""),
                    payload.get("password", ""),
                )
                self._send_json(
                    200,
                    self.server.app_service.session_payload(user),
                    extra_headers=[("Set-Cookie", self._build_session_cookie(session_token))],
                )
                return

            if parsed.path == "/api/logout":
                current_token = self._get_session_token()
                self.server.app_service.logout(current_token)
                self._send_json(
                    200,
                    {"ok": True},
                    extra_headers=[("Set-Cookie", self._build_expired_cookie())],
                )
                return

            user = self._require_user(api_mode=True)
            if not user:
                return

            if parsed.path == "/api/predict-text":
                payload = self._read_json_body()
                result = self.server.app_service.predict_text(
                    payload.get("subject", ""),
                    payload.get("body", ""),
                )
                self._send_json(200, result)
                return

            if parsed.path == "/api/predict-eml":
                payload = self._read_json_body()
                encoded = payload.get("content_base64", "")
                if not encoded:
                    raise ValueError("请上传 .eml 文件。")
                raw_bytes = base64.b64decode(encoded.encode("ascii"))
                result = self.server.app_service.predict_eml(
                    raw_bytes=raw_bytes,
                    filename=payload.get("filename", ""),
                )
                self._send_json(200, result)
                return

            if parsed.path == "/api/retrain":
                if not self._require_admin(user):
                    return
                payload = self._read_json_body()
                result = self.server.app_service.retrain(payload.get("data_path") or None)
                self._send_json(200, result)
                return

            if parsed.path == "/api/users":
                if not self._require_admin(user):
                    return
                payload = self._read_json_body()
                result = self.server.app_service.create_user(
                    username=payload.get("username", ""),
                    password=payload.get("password", ""),
                    display_name=payload.get("display_name", ""),
                    role=payload.get("role", "analyst"),
                )
                self._send_json(200, result)
                return

            if parsed.path == "/api/users/update":
                if not self._require_admin(user):
                    return
                payload = self._read_json_body()
                result = self.server.app_service.update_user(
                    user_id=payload.get("user_id", ""),
                    display_name=payload.get("display_name"),
                    role=payload.get("role"),
                    enabled=payload.get("enabled"),
                    acting_user_id=user["id"],
                )
                self._send_json(200, result)
                return

            if parsed.path == "/api/users/reset-password":
                if not self._require_admin(user):
                    return
                payload = self._read_json_body()
                result = self.server.app_service.reset_password(
                    user_id=payload.get("user_id", ""),
                    new_password=payload.get("password", ""),
                )
                self._send_json(200, result)
                return

            self._send_json(404, {"error": "未找到请求资源。"})
        except ValueError as error:
            self._send_json(400, {"error": str(error)})
        except Exception as error:
            self._send_json(500, {"error": "服务内部错误: %s" % error})

    def log_message(self, format_string, *args):
        print("%s - - [%s] %s" % (self.address_string(), self.log_date_time_string(), format_string % args))

    def _read_json_body(self):
        content_length = int(self.headers.get("Content-Length", "0"))
        raw_body = self.rfile.read(content_length) if content_length else b"{}"
        try:
            return json.loads(raw_body.decode("utf-8"))
        except ValueError:
            raise ValueError("请求体不是合法的 JSON。")

    def _get_session_token(self):
        cookie_header = self.headers.get("Cookie", "")
        if not cookie_header:
            return None
        cookie = SimpleCookie()
        cookie.load(cookie_header)
        morsel = cookie.get(SESSION_COOKIE_NAME)
        return morsel.value if morsel else None

    def _get_current_user(self):
        session_token = self._get_session_token()
        return self.server.app_service.get_current_user(session_token)

    def _require_user(self, api_mode=False):
        user = self._get_current_user()
        if user:
            return user
        if api_mode:
            self._send_json(401, {"error": "请先登录。", "code": "AUTH_REQUIRED"})
        else:
            self._redirect("/login")
        return None

    def _require_admin(self, user):
        if user.get("role") == "admin":
            return True
        self._send_json(403, {"error": "只有管理员可以执行这个操作。", "code": "ADMIN_REQUIRED"})
        return False

    def _serve_static_file(self, file_name, content_type):
        file_path = self.server.static_dir / file_name
        if not file_path.exists():
            self._send_json(404, {"error": "静态资源不存在。"})
            return
        body = file_path.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _redirect(self, location):
        body = b""
        self.send_response(302)
        self.send_header("Location", location)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _build_session_cookie(self, session_token):
        return (
            "%s=%s; Path=/; HttpOnly; SameSite=Lax; Max-Age=%d"
            % (SESSION_COOKIE_NAME, session_token, SESSION_TTL_SECONDS)
        )

    def _build_expired_cookie(self):
        return "%s=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0" % SESSION_COOKIE_NAME

    def _send_json(self, status_code, payload, extra_headers=None):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        for header_name, header_value in extra_headers or []:
            self.send_header(header_name, header_value)
        self.end_headers()
        self.wfile.write(body)


def build_argument_parser():
    parser = argparse.ArgumentParser(description="启动垃圾邮件识别 Web 页面")
    parser.add_argument("--host", default="127.0.0.1", help="监听地址，默认 127.0.0.1")
    parser.add_argument("--port", type=int, default=8000, help="监听端口，默认 8000")
    parser.add_argument("--model", default="models/spam_model.json", help="模型文件路径")
    parser.add_argument("--data", default="data/sample_emails.csv", help="训练数据 CSV 路径")
    parser.add_argument("--users", default=str(USERS_FILE), help="用户数据文件路径")
    parser.add_argument("--alpha", type=float, default=1.0, help="拉普拉斯平滑系数")
    parser.add_argument("--test-ratio", type=float, default=0.2, help="测试集比例，默认 0.2")
    parser.add_argument("--seed", type=int, default=42, help="随机种子")
    return parser


def create_server(host, port, model_path, data_path, users_path, alpha=1.0, test_ratio=0.2, seed=42):
    service = SpamFilterWebService(
        model_path=model_path,
        data_path=data_path,
        users_path=users_path,
        alpha=alpha,
        test_ratio=test_ratio,
        seed=seed,
    )
    return SpamFilterHTTPServer(
        (host, port),
        SpamFilterRequestHandler,
        app_service=service,
        static_dir=STATIC_DIR,
    )


def main():
    args = build_argument_parser().parse_args()
    server = create_server(
        host=args.host,
        port=args.port,
        model_path=args.model,
        data_path=args.data,
        users_path=args.users,
        alpha=args.alpha,
        test_ratio=args.test_ratio,
        seed=args.seed,
    )

    print("页面服务已启动: http://%s:%d" % (args.host, args.port))
    print("模型路径: %s" % args.model)
    print("训练数据: %s" % args.data)
    print("用户文件: %s" % args.users)
    if server.app_service.user_store.bootstrap_created:
        print("已创建默认管理员账号: %s / %s" % (DEFAULT_ADMIN_USERNAME, DEFAULT_ADMIN_PASSWORD))
    print("按 Ctrl+C 停止服务。")

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n正在停止服务...")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
