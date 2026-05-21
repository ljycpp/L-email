from collections import Counter

from spam_filter.classifier import NaiveBayesSpamClassifier
from spam_filter.dataset import compute_binary_metrics, load_samples, stratified_split
from spam_filter.features import extract_tokens


META_LABELS = {
    "meta:has_url": "链接",
    "meta:has_phone": "手机号",
    "meta:has_money": "金额",
    "meta:has_code": "验证码",
    "meta:many_digits": "大量数字",
    "meta:contains_link_text": "链接文本",
    "meta:urgent_call": "紧急催促",
    "meta:empty": "空内容",
}


def _humanize_token(token):
    if token in META_LABELS:
        return META_LABELS[token]

    if token.startswith("hint:") or token.startswith("safe:"):
        return token.split(":", 1)[1]

    parts = token.split(":", 2)
    if len(parts) == 3 and parts[1] in ("w", "p"):
        value = parts[2].strip()
        if value and len(value) <= 24:
            return value
    return None


def _build_word_cloud(samples, limit=28):
    counter = Counter()
    for sample in samples:
        seen = set()
        for token in extract_tokens(sample.subject, sample.body):
            display_token = _humanize_token(token)
            if not display_token or display_token in seen:
                continue
            seen.add(display_token)
            counter[display_token] += 1

    top_items = counter.most_common(limit)
    if not top_items:
        return []

    max_count = float(top_items[0][1])
    items = []
    for text, count in top_items:
        weight = 0.55 + (count / max_count) * 1.25
        items.append(
            {
                "text": text,
                "count": count,
                "weight": round(weight, 3),
            }
        )
    return items


def _build_indicative_terms(samples, alpha, limit=10):
    model = NaiveBayesSpamClassifier(alpha=alpha)
    model.fit(samples)

    result = {}
    for label in ("spam", "ham"):
        terms = []
        seen = set()
        for score, token in model.top_indicative_tokens(label, limit=limit * 4):
            display_token = _humanize_token(token)
            if not display_token or display_token in seen:
                continue
            seen.add(display_token)
            terms.append(
                {
                    "text": display_token,
                    "score": round(score, 3),
                }
            )
            if len(terms) >= limit:
                break
        result[label] = terms
    return result


def build_analysis_report(data_path, alpha=1.0, test_ratio=0.2, seed=42):
    samples = load_samples(data_path)
    train_samples, test_samples = stratified_split(samples, test_ratio=test_ratio, seed=seed)

    evaluator = NaiveBayesSpamClassifier(alpha=alpha)
    evaluator.fit(train_samples)

    y_true = []
    y_pred = []
    for sample in test_samples:
        predicted_label, _ = evaluator.predict(sample.subject, sample.body)
        y_true.append(sample.label)
        y_pred.append(predicted_label)

    metrics = compute_binary_metrics(y_true, y_pred, positive_label="spam") if test_samples else None

    spam_samples = [sample for sample in samples if sample.label == "spam"]
    ham_samples = [sample for sample in samples if sample.label == "ham"]
    sample_count = len(samples)
    spam_count = len(spam_samples)
    ham_count = len(ham_samples)

    return {
        "data_path": data_path,
        "sample_count": sample_count,
        "train_count": len(train_samples),
        "test_count": len(test_samples),
        "distribution": {
            "spam_count": spam_count,
            "ham_count": ham_count,
            "spam_ratio": (spam_count / float(sample_count)) if sample_count else 0.0,
            "ham_ratio": (ham_count / float(sample_count)) if sample_count else 0.0,
        },
        "metrics": metrics,
        "confusion_matrix": {
            "tp": metrics["tp"] if metrics else 0,
            "tn": metrics["tn"] if metrics else 0,
            "fp": metrics["fp"] if metrics else 0,
            "fn": metrics["fn"] if metrics else 0,
        },
        "word_clouds": {
            "spam": _build_word_cloud(spam_samples),
            "ham": _build_word_cloud(ham_samples),
        },
        "indicative_terms": _build_indicative_terms(samples, alpha=alpha),
    }
