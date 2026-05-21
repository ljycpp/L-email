import argparse

from spam_filter.training import format_percentage, train_and_evaluate


def build_argument_parser():
    parser = argparse.ArgumentParser(description="训练垃圾邮件识别模型")
    parser.add_argument("--data", default="data/sample_emails.csv", help="训练数据 CSV 路径")
    parser.add_argument("--model", default="models/spam_model.json", help="输出模型文件路径")
    parser.add_argument("--test-ratio", type=float, default=0.2, help="测试集比例，默认 0.2")
    parser.add_argument("--seed", type=int, default=42, help="随机种子")
    parser.add_argument("--alpha", type=float, default=1.0, help="拉普拉斯平滑系数")
    return parser


def main():
    args = build_argument_parser().parse_args()
    report = train_and_evaluate(
        data_path=args.data,
        model_path=args.model,
        test_ratio=args.test_ratio,
        seed=args.seed,
        alpha=args.alpha,
    )

    print("加载样本数: %d" % report["sample_count"])
    print("训练集样本数: %d" % report["train_count"])
    print("测试集样本数: %d" % report["test_count"])

    metrics = report["metrics"]
    if metrics:
        print("\n评估结果:")
        print("Accuracy : %s" % format_percentage(metrics["accuracy"]))
        print("Precision: %s" % format_percentage(metrics["precision"]))
        print("Recall   : %s" % format_percentage(metrics["recall"]))
        print("F1 Score : %s" % format_percentage(metrics["f1"]))
        print("TP=%d TN=%d FP=%d FN=%d" % (metrics["tp"], metrics["tn"], metrics["fp"], metrics["fn"]))

    print("\n模型已保存到: %s" % report["model_path"])
    print("最能代表 spam 类别的特征:")
    for token_data in report["top_spam_tokens"]:
        print("  %.3f  %s" % (token_data["score"], token_data["token"]))


if __name__ == "__main__":
    main()
