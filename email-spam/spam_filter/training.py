import os

from spam_filter.classifier import NaiveBayesSpamClassifier
from spam_filter.dataset import compute_binary_metrics, load_samples, stratified_split


def format_percentage(value):
    return "{:.2%}".format(value)


def train_and_evaluate(data_path, model_path, test_ratio=0.2, seed=42, alpha=1.0):
    samples = load_samples(data_path)
    train_samples, test_samples = stratified_split(samples, test_ratio=test_ratio, seed=seed)

    evaluator = NaiveBayesSpamClassifier(alpha=alpha)
    evaluator.fit(train_samples)

    metrics = None
    if test_samples:
        y_true = []
        y_pred = []
        for sample in test_samples:
            predicted_label, _ = evaluator.predict(sample.subject, sample.body)
            y_true.append(sample.label)
            y_pred.append(predicted_label)
        metrics = compute_binary_metrics(y_true, y_pred, positive_label="spam")

    final_model = NaiveBayesSpamClassifier(alpha=alpha)
    final_model.fit(samples)

    model_dir = os.path.dirname(model_path)
    if model_dir:
        os.makedirs(model_dir, exist_ok=True)
    final_model.save(model_path)

    top_spam_tokens = []
    for score, token in final_model.top_indicative_tokens("spam", limit=8):
        top_spam_tokens.append({"token": token, "score": score})

    return {
        "data_path": data_path,
        "model_path": model_path,
        "alpha": alpha,
        "seed": seed,
        "test_ratio": test_ratio,
        "sample_count": len(samples),
        "train_count": len(train_samples),
        "test_count": len(test_samples),
        "metrics": metrics,
        "top_spam_tokens": top_spam_tokens,
    }
