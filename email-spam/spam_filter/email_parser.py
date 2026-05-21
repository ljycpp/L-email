import re
from email import policy
from email.header import decode_header
from email.parser import BytesParser
from html import unescape


TAG_RE = re.compile(r"<[^>]+>")
SPACE_RE = re.compile(r"\s+")


def _decode_header_value(raw_value):
    if not raw_value:
        return ""

    parts = decode_header(raw_value)
    decoded_parts = []
    for value, charset in parts:
        if isinstance(value, bytes):
            try:
                decoded_parts.append(value.decode(charset or "utf-8", errors="ignore"))
            except LookupError:
                decoded_parts.append(value.decode("utf-8", errors="ignore"))
        else:
            decoded_parts.append(value)
    return "".join(decoded_parts)


def _decode_part(part):
    payload = part.get_payload(decode=True)
    if payload is None:
        raw_payload = part.get_payload()
        return raw_payload if isinstance(raw_payload, str) else ""

    charset = part.get_content_charset() or "utf-8"
    try:
        return payload.decode(charset, errors="ignore")
    except LookupError:
        return payload.decode("utf-8", errors="ignore")


def _html_to_text(html_content):
    text = TAG_RE.sub(" ", html_content or "")
    text = unescape(text)
    return SPACE_RE.sub(" ", text).strip()


def _extract_subject_and_body(message):
    subject = _decode_header_value(message.get("Subject", ""))
    body_parts = []

    if message.is_multipart():
        for part in message.walk():
            if part.get_content_disposition() == "attachment":
                continue

            content_type = part.get_content_type()
            if content_type not in ("text/plain", "text/html"):
                continue

            content = _decode_part(part)
            if content_type == "text/html":
                content = _html_to_text(content)
            body_parts.append(content)
    else:
        content = _decode_part(message)
        if message.get_content_type() == "text/html":
            content = _html_to_text(content)
        body_parts.append(content)

    body = "\n".join(part for part in body_parts if part).strip()
    return subject, body


def parse_eml_bytes(raw_bytes):
    message = BytesParser(policy=policy.default).parsebytes(raw_bytes)
    return _extract_subject_and_body(message)


def parse_eml_file(path):
    with open(path, "rb") as file_obj:
        raw_bytes = file_obj.read()
    return parse_eml_bytes(raw_bytes)
