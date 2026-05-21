import re


WORD_RE = re.compile(r"[a-z0-9][a-z0-9@._%+-]{1,39}", re.IGNORECASE)
PHRASE_RE = re.compile(r"[\u4e00-\u9fff]{2,20}")
URL_RE = re.compile(
    r"(https?://\S+|www\.\S+|\b[a-z0-9.-]+\.(?:com|cn|net|org|top|vip|xyz|cc|info|shop|link)\b)",
    re.IGNORECASE,
)
PHONE_RE = re.compile(r"(?:\+?86[- ]?)?1[3-9]\d{9}")
CURRENCY_RE = re.compile(r"(?:￥|\$|¥|\d+\s*(?:元|块|万元|usd|rmb))", re.IGNORECASE)
CODE_RE = re.compile(r"(验证码|校验码|动态码|提货码|\bcode\b)", re.IGNORECASE)

SPAM_HINTS = (
    "中奖",
    "红包",
    "返利",
    "兼职",
    "刷单",
    "领取",
    "提现",
    "投资",
    "外汇",
    "盈利",
    "稳赚",
    "低息",
    "贷款",
    "办证",
    "提额",
    "回收",
    "定金",
    "补贴",
    "清零",
    "点击",
    "链接",
    "押金",
    "返还",
    "返现",
    "代购",
    "招募",
    "理财",
    "跟单",
    "日赚",
    "优惠",
    "促销",
    "秒杀",
    "退税",
    "理赔",
    "空投",
    "offer",
    "winner",
    "claim",
    "bonus",
    "urgent",
    "free",
)

SAFE_HINTS = (
    "如非本人请忽略",
    "联系管理员",
    "共享目录",
    "共享盘",
    "会议纪要",
    "报销",
    "审批",
    "评审",
    "培训",
    "周会",
)


def normalize_text(text):
    cleaned = (text or "").strip().lower()
    cleaned = re.sub(r"\s+", " ", cleaned)
    return cleaned


def _is_cjk(char):
    return u"\u4e00" <= char <= u"\u9fff"


def _clean_for_ngrams(text):
    compact = []
    for char in text:
        if char.isalnum() or _is_cjk(char):
            compact.append(char)
    return "".join(compact)[:400]


def _word_tokens(text, prefix):
    tokens = []
    for token in WORD_RE.findall(text):
        if len(token) > 1:
            tokens.append("%s:w:%s" % (prefix, token))
    return tokens


def _phrase_tokens(text, prefix):
    tokens = []
    for token in PHRASE_RE.findall(text):
        if len(token) > 1:
            tokens.append("%s:p:%s" % (prefix, token))
    return tokens


def _char_ngrams(text, prefix):
    tokens = []
    compact = _clean_for_ngrams(text)
    for size in (2, 3):
        if len(compact) < size:
            continue
        for index in range(len(compact) - size + 1):
            gram = compact[index : index + size]
            if gram.isdigit():
                continue
            tokens.append("%s:g:%s" % (prefix, gram))
    return tokens


def _meta_tokens(full_text):
    tokens = []
    url_hits = len(URL_RE.findall(full_text))
    phone_hits = len(PHONE_RE.findall(full_text))
    money_hits = len(CURRENCY_RE.findall(full_text))
    code_hits = len(CODE_RE.findall(full_text))
    digit_count = sum(1 for char in full_text if char.isdigit())

    tokens.extend(["meta:has_url"] * min(url_hits, 3))
    tokens.extend(["meta:has_phone"] * min(phone_hits, 2))
    tokens.extend(["meta:has_money"] * min(money_hits, 3))
    tokens.extend(["meta:has_code"] * min(code_hits, 2))

    if digit_count >= 4:
        tokens.append("meta:many_digits")
    if "http" in full_text or "www." in full_text:
        tokens.append("meta:contains_link_text")
    if any(mark in full_text for mark in ("立即", "马上", "立刻", "速抢", "限时")):
        tokens.append("meta:urgent_call")

    for hint in SPAM_HINTS:
        count = full_text.count(hint)
        tokens.extend(["hint:%s" % hint] * min(count, 2))

    for hint in SAFE_HINTS:
        count = full_text.count(hint)
        tokens.extend(["safe:%s" % hint] * min(count, 2))

    return tokens


def extract_tokens(subject, body):
    subject_text = normalize_text(subject)
    body_text = normalize_text(body)
    combined = (subject_text + " " + body_text).strip()

    tokens = []
    subject_tokens = (
        _word_tokens(subject_text, "subject")
        + _phrase_tokens(subject_text, "subject")
        + _char_ngrams(subject_text, "subject")
    )
    body_tokens = (
        _word_tokens(body_text, "body")
        + _phrase_tokens(body_text, "body")
        + _char_ngrams(body_text, "body")
    )

    # 主题通常更能表征邮件意图，因此做一次加权。
    tokens.extend(subject_tokens)
    tokens.extend(subject_tokens)
    tokens.extend(body_tokens)
    tokens.extend(_meta_tokens(combined))

    if not tokens:
        tokens.append("meta:empty")
    return tokens
