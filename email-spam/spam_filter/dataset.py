import csv
import random
from collections import defaultdict, namedtuple


EmailSample = namedtuple("EmailSample", ["label", "subject", "body"])


def load_samples(path):
    samples = []
    with open(path, "r", encoding="utf-8-sig", newline="") as file_obj:
        reader = csv.DictReader(file_obj)
        required_columns = {"label", "subject", "body"}
        if not reader.fieldnames or not required_columns.issubset(set(reader.fieldnames)):
            raise ValueError("CSV 需要包含表头: label, subject, body")

        for row in reader:
            label = (row.get("label") or "").strip().lower()
            subject = (row.get("subject") or "").strip()
            body = (row.get("body") or "").strip()
            if label not in ("spam", "ham"):
                raise ValueError("发现未知标签: %s" % label)
            samples.append(EmailSample(label=label, subject=subject, body=body))

    if len(samples) < 4:
        raise ValueError("训练数据太少，至少需要 4 条样本。")
    return samples


def stratified_split(samples, test_ratio=0.2, seed=42):
    if not 0 <= test_ratio < 1:
        raise ValueError("test_ratio 必须在 [0, 1) 范围内。")
    if test_ratio == 0:
        return list(samples), []

    randomizer = random.Random(seed)
    grouped = defaultdict(list)
    for sample in samples:
        grouped[sample.label].append(sample)

    train_samples = []
    test_samples = []
    for label_samples in grouped.values():
        label_samples = list(label_samples)
        randomizer.shuffle(label_samples)

        if len(label_samples) <= 1:
            train_samples.extend(label_samples)
            continue

        test_count = int(round(len(label_samples) * test_ratio))
        test_count = max(1, test_count)
        if test_count >= len(label_samples):
            test_count = len(label_samples) - 1

        test_samples.extend(label_samples[:test_count])
        train_samples.extend(label_samples[test_count:])

    randomizer.shuffle(train_samples)
    randomizer.shuffle(test_samples)
    return train_samples, test_samples


def compute_binary_metrics(y_true, y_pred, positive_label="spam"):
    if len(y_true) != len(y_pred):
        raise ValueError("y_true 和 y_pred 长度不一致。")

    true_positive = 0
    true_negative = 0
    false_positive = 0
    false_negative = 0

    for actual, predicted in zip(y_true, y_pred):
        if actual == positive_label and predicted == positive_label:
            true_positive += 1
        elif actual != positive_label and predicted != positive_label:
            true_negative += 1
        elif actual != positive_label and predicted == positive_label:
            false_positive += 1
        else:
            false_negative += 1

    total = float(max(len(y_true), 1))
    precision_den = true_positive + false_positive
    recall_den = true_positive + false_negative

    precision = true_positive / float(precision_den) if precision_den else 0.0
    recall = true_positive / float(recall_den) if recall_den else 0.0
    accuracy = (true_positive + true_negative) / total
    f1 = (2 * precision * recall / (precision + recall)) if (precision + recall) else 0.0

    return {
        "accuracy": accuracy,
        "precision": precision,
        "recall": recall,
        "f1": f1,
        "tp": true_positive,
        "tn": true_negative,
        "fp": false_positive,
        "fn": false_negative,
    }
