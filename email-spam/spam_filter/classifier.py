import json
import math
from collections import Counter, defaultdict

from spam_filter.features import extract_tokens


class NaiveBayesSpamClassifier(object):
    def __init__(self, alpha=1.0):
        self.alpha = float(alpha)
        self.class_counts = Counter()
        self.token_totals = Counter()
        self.token_counts = defaultdict(Counter)
        self.vocabulary = set()

    def fit(self, samples):
        self.class_counts = Counter()
        self.token_totals = Counter()
        self.token_counts = defaultdict(Counter)
        self.vocabulary = set()

        for sample in samples:
            label = sample.label
            tokens = extract_tokens(sample.subject, sample.body)

            self.class_counts[label] += 1
            self.token_totals[label] += len(tokens)
            self.token_counts[label].update(tokens)
            self.vocabulary.update(tokens)

        if len(self.class_counts) < 2:
            raise ValueError("训练数据至少需要包含两个类别。")

        return self

    def _log_probability(self, label, tokens):
        total_docs = float(sum(self.class_counts.values()))
        vocab_size = float(max(len(self.vocabulary), 1))
        denominator = self.token_totals[label] + self.alpha * vocab_size
        log_prob = math.log(self.class_counts[label] / total_docs)
        class_token_counter = self.token_counts[label]

        for token in tokens:
            token_count = class_token_counter.get(token, 0)
            log_prob += math.log((token_count + self.alpha) / denominator)
        return log_prob

    def predict_proba(self, subject, body):
        if not self.class_counts:
            raise ValueError("模型还没有训练或加载。")

        tokens = extract_tokens(subject, body)
        scores = {}
        for label in self.class_counts:
            scores[label] = self._log_probability(label, tokens)

        max_score = max(scores.values())
        exp_scores = {}
        normalizer = 0.0
        for label, score in scores.items():
            exp_value = math.exp(score - max_score)
            exp_scores[label] = exp_value
            normalizer += exp_value

        probabilities = {}
        for label, exp_value in exp_scores.items():
            probabilities[label] = exp_value / normalizer if normalizer else 0.0
        return probabilities

    def predict(self, subject, body):
        probabilities = self.predict_proba(subject, body)
        predicted_label = max(probabilities, key=probabilities.get)
        return predicted_label, probabilities

    def top_indicative_tokens(self, label, limit=10):
        if label not in self.class_counts:
            raise ValueError("未知类别: %s" % label)

        other_total = 0
        other_counter = Counter()
        for other_label, counter in self.token_counts.items():
            if other_label == label:
                continue
            other_counter.update(counter)
            other_total += self.token_totals[other_label]

        vocab_size = float(max(len(self.vocabulary), 1))
        denominator_target = self.token_totals[label] + self.alpha * vocab_size
        denominator_other = other_total + self.alpha * vocab_size

        scored = []
        for token in self.vocabulary:
            p_target = (self.token_counts[label].get(token, 0) + self.alpha) / denominator_target
            p_other = (other_counter.get(token, 0) + self.alpha) / denominator_other
            scored.append((math.log(p_target / p_other), token))

        scored.sort(reverse=True)
        return scored[:limit]

    def save(self, path):
        data = {
            "alpha": self.alpha,
            "class_counts": dict(self.class_counts),
            "token_totals": dict(self.token_totals),
            "token_counts": {label: dict(counter) for label, counter in self.token_counts.items()},
            "vocabulary": sorted(self.vocabulary),
        }
        with open(path, "w", encoding="utf-8") as file_obj:
            json.dump(data, file_obj, ensure_ascii=False, indent=2)

    @classmethod
    def load(cls, path):
        with open(path, "r", encoding="utf-8") as file_obj:
            data = json.load(file_obj)

        model = cls(alpha=data["alpha"])
        model.class_counts = Counter(data["class_counts"])
        model.token_totals = Counter(data["token_totals"])
        model.token_counts = defaultdict(Counter)
        for label, counter in data["token_counts"].items():
            model.token_counts[label] = Counter(counter)
        model.vocabulary = set(data["vocabulary"])
        return model
